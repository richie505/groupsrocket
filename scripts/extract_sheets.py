"""Split ROCKET Sheets notes into one record per "ROCKET SHEET #N".

Input: a folder of "<Subject> - ROCKET Sheets.pdf" files (download the
"rocket sheets" folder from Google Drive), or .txt/.md text exports of them.
Output: data/sheets/<subject-slug>.json

    python scripts/extract_sheets.py path/to/rocket-sheets
    python scripts/extract_sheets.py path/to/rocket-sheets --merge   # keep existing sheets, add new ones

--merge keeps every existing sheet (so its MCQs and practice history stay valid), replaces a
sheet's text only when the PDF has noticeably more of it (a sheet that was cut off), and appends
sheets that are new. Replaced sheets lose their fact coverage so cover_facts.py re-checks them.
"""

import argparse
import json
import re
import sys
from pathlib import Path

from common import DATA_DIR, MCQS_DIR, SHEETS_DIR, load_json, load_syllabus, save_json, slugify

FACTS_DIR = DATA_DIR / "facts"

HEADER_RE = re.compile(r"ROCKET\s+SHEET\s*\\?#\s*(\d+)", re.IGNORECASE)

# Watermark / footer lines repeated on every page of the PDFs.
BOILERPLATE_RE = re.compile(
    r"^(SUBSCRIBE to our YouTube Channel.*|APPSC TGPSC Exams Personal Mentorship.*"
    r"|.*- ROCKET Sheets\.pdf\s*)$",
    re.IGNORECASE | re.MULTILINE,
)


def read_text(path: Path) -> str:
    if path.suffix.lower() == ".pdf":
        try:
            import pymupdf

            return "\n\n".join(page.get_text() for page in pymupdf.open(path))
        except ImportError:
            from pypdf import PdfReader

            return "\n\n".join(page.extract_text() or "" for page in PdfReader(path).pages)
    return path.read_text(encoding="utf-8")


def clean(text: str) -> str:
    text = text.replace("\\#", "#").replace("\\*", "*").replace("\\_", "_")
    text = BOILERPLATE_RE.sub("", text)
    # Drop the "<Subject> – " prefix that sits in front of each sheet header.
    text = re.sub(r"[ \t]*\n{3,}", "\n\n", text)
    return text.strip()


def split_sheets(subject: str, text: str) -> list[dict]:
    text = clean(text)
    matches = list(HEADER_RE.finditer(text))
    sheets, seen = [], {}
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        body = text[m.end():end]
        # Trim the "<Subject> –" prefix belonging to the next header.
        body = re.sub(r"\n[^\n]*[–-]\s*$", "", body.rstrip()).strip()
        number = m.group(1)
        seen[number] = seen.get(number, 0) + 1
        sheet_id = number if seen[number] == 1 else f"{number}-{seen[number]}"
        first_line = body.split("\n", 1)[0].strip()
        sheets.append(
            {
                "id": sheet_id,
                "number": int(number),
                "heading": first_line[:120],
                "text": body,
            }
        )
    return sheets


def merge(slug: str, subject: str, source: str, new_sheets: list[dict]) -> tuple[list[str], list[str]]:
    """Merge freshly extracted sheets into the existing file; returns (added ids, replaced ids)."""
    path = SHEETS_DIR / f"{slug}.json"
    existing = load_json(path, {"subject": subject, "source": source, "sheets": []})
    by_id = {s["id"]: s for s in existing["sheets"]}
    added, replaced = [], []
    for sheet in new_sheets:
        old = by_id.get(sheet["id"])
        if old is None:
            existing["sheets"].append(sheet)
            added.append(sheet["id"])
        elif len(sheet["text"]) > len(old["text"]) * 1.05:
            old.update(text=sheet["text"], heading=sheet["heading"])
            replaced.append(sheet["id"])
    existing["sheets"].sort(key=lambda s: (s["number"], s["id"]))
    save_json(path, existing)
    # Replaced sheets: keep their MCQs but drop fact links/coverage so cover_facts.py redoes them.
    mcqs, facts = load_json(MCQS_DIR / f"{slug}.json"), load_json(FACTS_DIR / f"{slug}.json")
    for sid in replaced:
        if mcqs and sid in mcqs["sheets"]:
            rec = mcqs["sheets"][sid]
            rec.pop("coverage", None)
            for q in rec["mcqs"]:
                q.pop("facts", None)
        if facts:
            facts["sheets"].pop(sid, None)
    if mcqs:
        save_json(MCQS_DIR / f"{slug}.json", mcqs)
    if facts:
        save_json(FACTS_DIR / f"{slug}.json", facts)
    return added, replaced


def subject_from_filename(path: Path) -> str:
    return re.split(r"\s+-\s+ROCKET", path.stem, flags=re.IGNORECASE)[0].strip()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", type=Path, help="folder with ROCKET Sheets PDFs or text exports")
    ap.add_argument("--merge", action="store_true", help="add new sheets, replace only cut-off ones")
    args = ap.parse_args()

    known = {s["name"]: s for s in load_syllabus()["subjects"]}
    files = sorted(p for p in args.source.iterdir() if p.suffix.lower() in {".pdf", ".txt", ".md"})
    if not files:
        sys.exit(f"No .pdf/.txt/.md files in {args.source}")

    SHEETS_DIR.mkdir(parents=True, exist_ok=True)
    for path in files:
        subject = subject_from_filename(path)
        if subject not in known:
            print(f"! {path.name}: subject '{subject}' is not in data/syllabus.json, skipped")
            continue
        sheets = split_sheets(subject, read_text(path))
        if args.merge:
            added, replaced = merge(slugify(subject), subject, path.name, sheets)
            print(f"{subject:22} {len(sheets):3} sheets in PDF; added {added or '-'}; replaced {replaced or '-'}")
            continue
        out = SHEETS_DIR / f"{slugify(subject)}.json"
        out.write_text(
            json.dumps({"subject": subject, "source": path.name, "sheets": sheets}, ensure_ascii=False, indent=1),
            encoding="utf-8",
        )
        print(f"{subject:22} {len(sheets):3} sheets -> {out.relative_to(DATA_DIR.parent)}")


if __name__ == "__main__":
    main()
