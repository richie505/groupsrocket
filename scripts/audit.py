"""Per-subject audit: compare each full ROCKET Sheets PDF with the app data.

For every subject it reports the PDF's sheets vs the app's sheets, how much of the PDF's
notes text made it into data/sheets (word coverage), MCQs, and fact coverage.

    python scripts/audit.py path/to/full-pdfs
"""

import re
import sys
from collections import Counter
from pathlib import Path

from common import MCQS_DIR, SHEETS_DIR, load_json, load_syllabus, slugify
from extract_sheets import clean, read_text, split_sheets, subject_from_filename

WORD_RE = re.compile(r"[A-Za-z0-9]+")


def words(text: str) -> Counter:
    return Counter(w.lower() for w in WORD_RE.findall(text))


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    pdfs = {subject_from_filename(p): p for p in Path(sys.argv[1]).glob("*.pdf")}
    rows, problems = [], []
    for subj in load_syllabus()["subjects"]:
        name, slug = subj["name"], slugify(subj["name"])
        app_sheets = load_json(SHEETS_DIR / f"{slug}.json", {"sheets": []})["sheets"]
        records = load_json(MCQS_DIR / f"{slug}.json", {"sheets": {}})["sheets"]
        pdf = pdfs.get(name)
        pdf_sheets = split_sheets(name, read_text(pdf)) if pdf else []

        missing = sorted({s["id"] for s in pdf_sheets} - {s["id"] for s in app_sheets}, key=lambda i: (len(i), i))
        # Word coverage: share of the PDF notes' words (with multiplicity) found in the app notes.
        pdf_words = words(clean("\n".join(s["text"] for s in pdf_sheets)))
        app_words = words("\n".join(s["text"] for s in app_sheets))
        found = sum(min(c, app_words[w]) for w, c in pdf_words.items())
        text_pct = 100 * found / max(1, sum(pdf_words.values()))

        no_mcq = [s["id"] for s in app_sheets if not records.get(s["id"], {}).get("mcqs")]
        mcqs = sum(len(r["mcqs"]) for r in records.values())
        facts = sum(r.get("coverage", {}).get("facts", 0) for r in records.values())
        covered = sum(r.get("coverage", {}).get("covered", 0) for r in records.values())
        unchecked = [s["id"] for s in app_sheets if not records.get(s["id"], {}).get("coverage", {}).get("audited")]
        rows.append((name, len(pdf_sheets), len(app_sheets), text_pct, mcqs, covered, facts, len(unchecked)))
        for label, ids in (("missing from app", missing), ("no MCQs", no_mcq), ("fact coverage not complete", unchecked)):
            if ids:
                problems.append(f"{name}: {len(ids)} sheet(s) {label}: {', '.join(ids[:15])}{' …' if len(ids) > 15 else ''}")

    print(f"{'Subject':20} {'PDF':>4} {'App':>4} {'Text':>6} {'MCQs':>6} {'Facts covered':>15} {'Unchecked':>9}")
    for n, p, a, t, m, c, f, u in rows:
        print(f"{n:20} {p:4} {a:4} {t:5.1f}% {m:6} {c:7}/{f:<7} {u:9}")
    tot = [sum(r[i] for r in rows) for i in (1, 2, 4, 5, 6, 7)]
    print(f"{'TOTAL':20} {tot[0]:4} {tot[1]:4} {'':6} {tot[2]:6} {tot[3]:7}/{tot[4]:<7} {tot[5]:9}")
    print("\n".join(["", "Problems:"] + problems) if problems else "\nNo problems found.")


if __name__ == "__main__":
    main()
