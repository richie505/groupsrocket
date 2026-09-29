"""Generate MCQs for every ROCKET SHEET with the OpenAI API.

Reads data/sheets/<subject>.json, writes data/mcqs/<subject>.json, then
rebuilds data/index.json. Already-generated sheets are skipped, so the
script can be stopped and re-run at any time.

    export OPENAI_API_KEY=sk-...
    python scripts/generate_mcqs.py                      # all subjects (tops sheets up to target)
    python scripts/generate_mcqs.py --subject "Indian Polity" --sheets 1 2 3
    python scripts/generate_mcqs.py --subject Chemistry --force
"""

import argparse
import hashlib
import json
import os
import random
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from threading import Lock

from build_index import build_index
from common import MCQS_DIR, SHEETS_DIR, load_json, load_syllabus, save_json, slugify
from prompt import (
    AB_OPTIONS,
    AR_OPTIONS,
    FIXED_OPTION_FORMATS,
    build_system_prompt,
    build_user_prompt,
    response_schema,
)

DEFAULT_MODEL = os.environ.get("OPENAI_MODEL", "gpt-4.1-mini")
MAX_UNITS = 3  # tracker units kept per exam, most relevant first


BATCH = 20  # new MCQs requested per API call
MAX_BATCHES = 6  # per sheet per run


def question_count(text: str, per_sheet: int | None) -> int:
    """Target MCQs for a sheet: ~1 per 100 characters of notes, between 20 and 80."""
    if per_sheet:
        return per_sheet
    return max(20, min(80, round(len(text) / 100)))


def call_openai(client, model: str, system: str, user: str, schema: dict, retries: int = 5) -> dict:
    for attempt in range(retries):
        try:
            resp = client.chat.completions.create(
                model=model,
                messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
                response_format={
                    "type": "json_schema",
                    "json_schema": {"name": "rocket_sheet_mcqs", "strict": True, "schema": schema},
                },
            )
            msg = resp.choices[0].message
            if getattr(msg, "refusal", None):
                raise RuntimeError(f"model refused: {msg.refusal}")
            return json.loads(msg.content)
        except Exception as e:  # noqa: BLE001 - retry on any API/parse error
            if attempt == retries - 1:
                raise
            wait = 2 ** (attempt + 1)
            print(f"    retry in {wait}s ({type(e).__name__}: {e})", file=sys.stderr)
            time.sleep(wait)


NOTES_RE = re.compile(r"\b(the|these) (notes|sheet)\b", re.IGNORECASE)


def mentions_notes(q: dict) -> bool:
    return any(NOTES_RE.search(t) for t in [q["question"], *q["options"]])


def norm(s: str) -> str:
    return "".join(ch for ch in s.lower() if ch.isalnum())


def clean_mcqs(raw: list[dict], subject_slug: str, sheet_id: str, existing: list[dict] = ()) -> list[dict]:
    """Validate model output, drop repeats of existing questions, shuffle option order."""
    out, seen = [], {norm(q["question"]) for q in existing}
    for q in raw:
        opts = [o.strip() for o in q["options"]]
        fmt = q["format"]
        fixed = {"Assertion-Reason": AR_OPTIONS, "Statement-Based (A/B)": AB_OPTIONS}.get(fmt)
        if fixed:
            # The answer index is only trustworthy if the model kept the canonical order.
            if len(opts) != 4 or any(norm(a)[:18] != norm(b)[:18] for a, b in zip(opts, fixed)):
                continue
            opts = list(fixed)
        if len(opts) != 4 or len(set(opts)) != 4 or not 0 <= q["answer_index"] < 4:
            continue
        key = norm(q["question"])
        if key in seen:
            continue
        seen.add(key)
        answer = opts[q["answer_index"]]
        if fmt not in FIXED_OPTION_FORMATS:
            seed = int(hashlib.sha1(q["question"].encode()).hexdigest()[:8], 16)
            random.Random(seed).shuffle(opts)
        out.append(
            {
                "id": f"{subject_slug}-{sheet_id}-{len(existing) + len(out) + 1}",
                "format": fmt,
                "keyword": q["keyword"].strip(),
                "question": q["question"].strip(),
                "options": opts,
                "answer": opts.index(answer),
                "explanation": q["explanation"].strip(),
            }
        )
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--subject", action="append", help="subject name (repeatable); default: all")
    ap.add_argument("--sheets", nargs="*", help="only these sheet ids, e.g. 1 2 23-2")
    ap.add_argument("--per-sheet", type=int, help="fixed MCQ count per sheet (default: scales with sheet length)")
    ap.add_argument("--model", default=DEFAULT_MODEL, help=f"OpenAI model (default: $OPENAI_MODEL or {DEFAULT_MODEL})")
    ap.add_argument("--workers", type=int, default=4, help="parallel requests")
    ap.add_argument("--force", action="store_true", help="discard existing MCQs and regenerate from scratch")
    ap.add_argument("--limit", type=int, help="stop after this many sheets (for a trial run)")
    ap.add_argument(
        "--redo-notes-mentions",
        action="store_true",
        help='regenerate only sheets whose questions say "the notes" in the stem/options',
    )
    args = ap.parse_args()

    if not os.environ.get("OPENAI_API_KEY"):
        sys.exit("Set OPENAI_API_KEY first.")
    from openai import OpenAI

    client = OpenAI()
    syllabus = load_syllabus()
    units_by_id = {u["id"]: u for u in syllabus["units"]}
    subjects = [s for s in syllabus["subjects"] if not args.subject or s["name"] in args.subject]
    if args.subject and len(subjects) != len(args.subject):
        sys.exit(f"Unknown subject. Choose from: {', '.join(s['name'] for s in syllabus['subjects'])}")

    jobs, outputs, locks = [], {}, {}
    for subj in subjects:
        slug = slugify(subj["name"])
        sheets_file = load_json(SHEETS_DIR / f"{slug}.json")
        if not sheets_file:
            print(f"! no notes for {subj['name']} (run extract_sheets.py first)")
            continue
        out_path = MCQS_DIR / f"{slug}.json"
        existing = load_json(out_path, {"subject": subj["name"], "sheets": {}})
        outputs[slug] = (out_path, existing)
        locks[slug] = Lock()
        system = build_system_prompt(subj["blueprint"])
        for sheet in sheets_file["sheets"]:
            if args.sheets and sheet["id"] not in args.sheets:
                continue
            done = existing["sheets"].get(sheet["id"])
            redo = args.force
            if args.redo_notes_mentions:
                if not done or not any(mentions_notes(q) for q in done["mcqs"]):
                    continue
                redo = True
            elif done and not args.force and len(done["mcqs"]) >= question_count(sheet["text"], args.per_sheet) * 0.9:
                continue  # already at target
            jobs.append((subj, slug, system, sheet, redo))
    if args.limit:
        jobs = jobs[: args.limit]
    print(f"{len(jobs)} sheets to generate with {args.model}")

    def run(job):
        """Top a sheet up to its target count in batches, never repeating an existing question."""
        subj, slug, system, sheet, redo = job
        target = question_count(sheet["text"], args.per_sheet)
        out_path, data = outputs[slug]
        with locks[slug]:
            prev = None if redo else data["sheets"].get(sheet["id"])
        record = dict(prev) if prev else {"number": sheet["number"], "title": "", "tracker": {"g1": [], "g2": []}}
        mcqs = list(prev["mcqs"]) if prev else []
        start = len(mcqs)
        for _ in range(MAX_BATCHES):
            need = min(BATCH, target - len(mcqs))
            if need <= 0:
                break
            user = build_user_prompt(subj["name"], sheet, need, units_by_id, subj["g1"], subj["g2"], asked=mcqs)
            result = call_openai(client, args.model, system, user, response_schema(subj["g1"], subj["g2"]))
            new = clean_mcqs(result["mcqs"], slug, sheet["id"], existing=mcqs)
            mcqs += new
            if not record["title"]:
                record["title"] = result["title"].strip() or sheet["heading"]
                record["tracker"] = {
                    "g1": [u for u in dict.fromkeys(result["tracker_g1"]) if u in units_by_id][:MAX_UNITS],
                    "g2": [u for u in dict.fromkeys(result["tracker_g2"]) if u in units_by_id][:MAX_UNITS],
                }
            record.update(model=args.model, generated_at=datetime.now(timezone.utc).isoformat(timespec="seconds"), mcqs=mcqs)
            with locks[slug]:
                data["sheets"][sheet["id"]] = dict(record)
                data["sheets"] = dict(sorted(data["sheets"].items(), key=lambda kv: (kv[1]["number"], kv[0])))
                save_json(out_path, data)
            if len(new) < min(3, need):
                break  # the notes are exhausted
        return f"{subj['name']} #{sheet['id']}: {start} -> {len(mcqs)}/{target} MCQs  [{', '.join(record['tracker']['g1'] + record['tracker']['g2'])}]"

    failed = 0
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(run, j): j for j in jobs}
        for i, fut in enumerate(as_completed(futures), 1):
            subj, _, _, sheet, _ = futures[fut]
            try:
                print(f"[{i}/{len(jobs)}] {fut.result()}")
            except Exception as e:  # noqa: BLE001
                failed += 1
                print(f"[{i}/{len(jobs)}] FAILED {subj['name']} #{sheet['id']}: {e}", file=sys.stderr)

    build_index()
    if failed:
        sys.exit(f"{failed} sheet(s) failed — re-run the same command to retry them.")


if __name__ == "__main__":
    main()
