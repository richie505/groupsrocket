"""Make sure EVERY fact in every ROCKET SHEET is tested by at least one MCQ.

Per sheet:
  1. extract   – list every atomic fact in the notes  -> data/facts/<subject>.json
  2. map       – link each existing MCQ to the fact ids it tests
  3. fill      – write new MCQs for the facts nobody tests yet (repeats until all covered)
  4. re-check  – facts still untested get a targeted check against every question;
                 only facts no question tests are filled again (repeats up to MAX_ROUNDS)

Resumable: finished steps are saved and skipped on the next run.

    python scripts/cover_facts.py --workers 24
    python scripts/cover_facts.py --subject Chemistry --sheets 5
"""

import argparse
import json
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import RLock

from build_index import build_index
from common import DATA_DIR, MCQS_DIR, SHEETS_DIR, load_json, load_syllabus, save_json, slugify
from generate_mcqs import DEFAULT_MODEL, call_openai, clean_mcqs
from prompt import (
    CHECK_SCHEMA,
    CHECK_USER,
    FACTS_SCHEMA,
    FACTS_SYSTEM,
    FACTS_USER,
    FILL_USER,
    MAP_SCHEMA,
    MAP_SYSTEM,
    MAP_USER,
    build_system_prompt,
    fill_schema,
)

FACTS_DIR = DATA_DIR / "facts"
FILL_CHUNK = 25  # untested facts per fill request
MAP_CHUNK = 40  # questions per mapping request
CHECK_CHUNK = 60  # questions per targeted re-check request
MAX_ROUNDS = 5


def fmt_facts(facts: list[dict]) -> str:
    return "\n".join(f"{f['id']}: {f['text']}" for f in facts)


def fmt_question(n: int, q: dict) -> str:
    return (
        f"Q{n}. {q['question']}\n"
        + "\n".join(f"   {'ABCD'[i]}) {o}" for i, o in enumerate(q["options"]))
        + f"\n   Correct: {'ABCD'[q['answer']]}. {q['explanation']}"
    )


class Sheet:
    """One sheet's notes, facts and MCQs, with saving back to the shared per-subject files."""

    def __init__(self, ctx, subj, slug, sheet):
        self.ctx, self.subj, self.slug, self.sheet = ctx, subj, slug, sheet
        self.id = sheet["id"]

    @property
    def record(self) -> dict:
        return self.ctx.mcqs[self.slug]["sheets"][self.id]

    def facts(self) -> list[dict] | None:
        return self.ctx.facts[self.slug]["sheets"].get(self.id)

    @property
    def lock(self):
        return self.ctx.locks[self.slug]

    def save(self):
        with self.lock:
            save_json(MCQS_DIR / f"{self.slug}.json", self.ctx.mcqs[self.slug])
            save_json(FACTS_DIR / f"{self.slug}.json", self.ctx.facts[self.slug])

    def call(self, system, user, schema):
        return call_openai(self.ctx.client, self.ctx.model, system, user, schema)

    # 1. extract
    def extract(self):
        if self.facts() is not None:
            return
        out = self.call(FACTS_SYSTEM, FACTS_USER.format(subject=self.subj["name"], sheet_id=self.id, text=self.sheet["text"]), FACTS_SCHEMA)
        texts = [t.strip() for t in out["facts"] if t.strip()]
        with self.lock:
            self.ctx.facts[self.slug]["sheets"][self.id] = [{"id": f"F{i}", "text": t} for i, t in enumerate(texts, 1)]
            self.save()

    # 2. map
    def map_questions(self, questions: list[dict]):
        facts = self.facts()
        valid = {f["id"] for f in facts}
        for start in range(0, len(questions), MAP_CHUNK):
            chunk = questions[start : start + MAP_CHUNK]
            user = MAP_USER.format(
                facts=fmt_facts(facts),
                questions="\n\n".join(fmt_question(i, q) for i, q in enumerate(chunk, 1)),
            )
            out = self.call(MAP_SYSTEM, user, MAP_SCHEMA)
            links = [[] for _ in chunk]
            for link in out["links"]:
                if 1 <= link["q"] <= len(chunk):
                    links[link["q"] - 1] = [f for f in dict.fromkeys(link["fact_ids"]) if f in valid]
            with self.lock:
                for q, ids in zip(chunk, links):
                    q["facts"] = ids
                self.save()

    def untested(self) -> list[dict]:
        tested = {f for q in self.record["mcqs"] for f in q.get("facts", [])}
        return [f for f in self.facts() if f["id"] not in tested]

    # 3. fill
    def fill(self, missing: list[dict]) -> int:
        record, added = self.record, 0
        valid = {f["id"] for f in self.facts()}
        for start in range(0, len(missing), FILL_CHUNK):
            chunk = missing[start : start + FILL_CHUNK]
            user = FILL_USER.format(
                subject=self.subj["name"],
                sheet_id=self.id,
                untested=fmt_facts(chunk),
                asked="\n".join(f"- {q['question'][:140]}".replace("\n", " / ") for q in record["mcqs"]) or "- (none)",
                text=self.sheet["text"],
            )
            out = self.call(self.ctx.system[self.slug], user, fill_schema(self.subj["g1"], self.subj["g2"]))
            new = clean_mcqs(out["mcqs"], self.slug, self.id, existing=record["mcqs"], fact_ids=valid)
            with self.lock:
                record["mcqs"].extend(new)
                self.save()
            added += len(new)
        return added

    # 4. targeted re-check
    def recheck(self, missing: list[dict]) -> list[dict]:
        """Ask, fact by fact, whether any existing question tests it; link the ones that do."""
        questions = self.record["mcqs"]
        found: dict[str, set[int]] = {}
        wanted = {f["id"] for f in missing}
        for start in range(0, len(questions), CHECK_CHUNK):
            chunk = questions[start : start + CHECK_CHUNK]
            user = CHECK_USER.format(
                facts=fmt_facts(missing),
                questions="\n\n".join(fmt_question(i, q) for i, q in enumerate(chunk, 1)),
            )
            out = self.call(MAP_SYSTEM, user, CHECK_SCHEMA)
            for c in out["checks"]:
                if c["fact_id"] in wanted:
                    found.setdefault(c["fact_id"], set()).update(start + n - 1 for n in c["questions"] if 1 <= n <= len(chunk))
        with self.lock:
            for fid, idxs in found.items():
                for i in idxs:
                    q = questions[i]
                    q["facts"] = list(dict.fromkeys([*q.get("facts", []), fid]))
            self.save()
        return self.untested()

    def run(self) -> str:
        self.extract()
        record = self.record
        if record.get("coverage", {}).get("audited"):
            return f"{self.subj['name']} #{self.id}: already complete"
        before = len(record["mcqs"])
        unmapped = [q for q in record["mcqs"] if "facts" not in q]
        if unmapped:
            self.map_questions(unmapped)
        missing = self.untested()
        for _ in range(MAX_ROUNDS):
            if missing:
                missing = self.recheck(missing)
            if not missing or not self.fill(missing):
                break
            missing = self.untested()
        total = len(self.facts())
        with self.lock:
            record["coverage"] = {"facts": total, "covered": total - len(missing), "audited": not missing}
            self.save()
        return (
            f"{self.subj['name']} #{self.id}: {total - len(missing)}/{total} facts covered, "
            f"MCQs {before} -> {len(record['mcqs'])}" + ("" if not missing else f"  (UNCOVERED: {', '.join(f['id'] for f in missing)})")
        )


class Ctx:
    pass


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--subject", action="append", help="subject name (repeatable); default: all")
    ap.add_argument("--sheets", nargs="*", help="only these sheet ids")
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--recheck", action="store_true", help="re-audit sheets already marked complete")
    args = ap.parse_args()
    if not os.environ.get("OPENAI_API_KEY"):
        sys.exit("Set OPENAI_API_KEY first.")
    from openai import OpenAI

    ctx = Ctx()
    ctx.client, ctx.model = OpenAI(), args.model
    ctx.mcqs, ctx.facts, ctx.locks, ctx.system = {}, {}, {}, {}
    syllabus = load_syllabus()
    subjects = [s for s in syllabus["subjects"] if not args.subject or s["name"] in args.subject]

    jobs = []
    for subj in subjects:
        slug = slugify(subj["name"])
        notes = load_json(SHEETS_DIR / f"{slug}.json")
        mcqs = load_json(MCQS_DIR / f"{slug}.json")
        if not notes or not mcqs:
            print(f"! {subj['name']}: run extract_sheets.py and generate_mcqs.py first")
            continue
        ctx.mcqs[slug] = mcqs
        ctx.facts[slug] = load_json(FACTS_DIR / f"{slug}.json", {"subject": subj["name"], "sheets": {}})
        ctx.locks[slug] = RLock()
        ctx.system[slug] = build_system_prompt(subj["blueprint"])
        for sheet in notes["sheets"]:
            if args.sheets and sheet["id"] not in args.sheets:
                continue
            rec = mcqs["sheets"].get(sheet["id"])
            if rec is None:
                continue
            if args.recheck:
                rec.pop("coverage", None)
            jobs.append(Sheet(ctx, subj, slug, sheet))
    # Longest sheets first so the slowest jobs start early.
    jobs.sort(key=lambda j: -len(j.sheet["text"]))
    print(f"{len(jobs)} sheets, model {args.model}")

    failed = 0
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(j.run): j for j in jobs}
        for i, fut in enumerate(as_completed(futures), 1):
            j = futures[fut]
            try:
                print(f"[{i}/{len(jobs)}] {fut.result()}", flush=True)
            except Exception as e:  # noqa: BLE001
                failed += 1
                print(f"[{i}/{len(jobs)}] FAILED {j.subj['name']} #{j.id}: {e}", flush=True)

    build_index()
    report()
    if failed:
        sys.exit(f"{failed} sheet(s) failed — re-run to resume.")


def report():
    facts = covered = sheets = complete = 0
    for path in MCQS_DIR.glob("*.json"):
        for rec in json.loads(path.read_text(encoding="utf-8"))["sheets"].values():
            cov = rec.get("coverage")
            sheets += 1
            if cov:
                facts += cov["facts"]
                covered += cov["covered"]
                complete += cov["audited"]
    print(f"coverage: {covered}/{facts} facts tested; {complete}/{sheets} sheets fully covered and audited")


if __name__ == "__main__":
    main()
