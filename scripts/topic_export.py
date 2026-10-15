"""Print a topic's ROCKET SHEETs and their facts, to rewrite into structured pages (data/structured/).

    python scripts/topic_export.py indian-polity 5          # topic 5 of Indian Polity (0-based)
    python scripts/topic_export.py indian-polity --list     # topics with sizes and done marks
"""

import sys

from common import DATA_DIR, MCQS_DIR, SHEETS_DIR, load_json

STRUCT_DIR = DATA_DIR / "structured"


def topic_sheets(slug: str, ti: int):
    topic = load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"][ti]
    notes = {s["id"]: s for s in load_json(SHEETS_DIR / f"{slug}.json")["sheets"]}
    recs = load_json(MCQS_DIR / f"{slug}.json")["sheets"]
    facts = load_json(DATA_DIR / "facts" / f"{slug}.json")["sheets"]
    return topic, [(sid, notes[sid], recs.get(sid, {}).get("title") or notes[sid]["heading"], facts.get(sid, [])) for sid in topic["sheets"]]


def main() -> None:
    slug = sys.argv[1]
    if sys.argv[2] == "--list":
        for ti, t in enumerate(load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"]):
            _, sheets = topic_sheets(slug, ti)
            done = "done" if (STRUCT_DIR / slug / f"{ti:02d}.md").exists() else ""
            print(f"{ti:3} {sum(len(f) for *_, f in sheets):4} facts  {t['title']}  {done}")
        return
    ti = int(sys.argv[2])
    topic, sheets = topic_sheets(slug, ti)
    print(f"=== TOPIC {slug}:{ti} | {topic['title']}")
    for sid, sh, title, fl in sheets:
        p1, p2 = sh.get("pages", [0, 0])
        print(f"\n## SHEET #{sid} | {title} | pp {p1}-{p2}")
        for f in fl:
            print(f"{f['id']} {f['text']}")


if __name__ == "__main__":
    main()
