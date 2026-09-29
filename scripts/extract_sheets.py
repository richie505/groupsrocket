"""Split ROCKET Sheets notes into one record per "ROCKET SHEET #N".

Input: a folder of "<Subject> - ROCKET Sheets.pdf" files (download the
"rocket sheets" folder from Google Drive), or .txt/.md text exports of them.
Output: data/sheets/<subject-slug>.json

    python scripts/extract_sheets.py path/to/rocket-sheets
"""

import argparse
import json
import re
import sys
from pathlib import Path

from common import DATA_DIR, SHEETS_DIR, load_syllabus, slugify

HEADER_RE = re.compile(r"ROCKET\s+SHEET\s*\\?#\s*(\d+)", re.IGNORECASE)

# Watermark / footer lines repeated on every page of the PDFs.
BOILERPLATE_RE = re.compile(
    r"^(SUBSCRIBE to our YouTube Channel.*|APPSC TGPSC Exams Personal Mentorship.*"
    r"|.*- ROCKET Sheets\.pdf\s*)$",
    re.IGNORECASE | re.MULTILINE,
)


def read_text(path: Path) -> str:
    if path.suffix.lower() == ".pdf":
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


def subject_from_filename(path: Path) -> str:
    return re.split(r"\s+-\s+ROCKET", path.stem, flags=re.IGNORECASE)[0].strip()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", type=Path, help="folder with ROCKET Sheets PDFs or text exports")
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
        out = SHEETS_DIR / f"{slugify(subject)}.json"
        out.write_text(
            json.dumps({"subject": subject, "source": path.name, "sheets": sheets}, ensure_ascii=False, indent=1),
            encoding="utf-8",
        )
        print(f"{subject:22} {len(sheets):3} sheets -> {out.relative_to(DATA_DIR.parent)}")


if __name__ == "__main__":
    main()
