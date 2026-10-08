"""Write the Rocket Prep app's assets (APPSC Prep format) from the ROCKET data.

Syllabus mapping — the ROCKET sheets are the app's own syllabus:
    Book     = subject block in 90-day-plan order (ids follow APPSC Prep's subject numbering,
               which drives the MCQ technique hints: 1 History, 2 Polity, 3 Economy, 4 Geography, 5 Science)
    Topic    = subject (e.g. Indian Polity, Indian Society)
    Section  = one ROCKET SHEET #N
    Subsections = "Key facts" (every fact of the sheet) and "Sheet text" (the notes as in the PDF)
MCQ practice after each section = the sheet's MCQs; each question lists the facts it tests.

    python scripts/build_prep_assets.py            # -> android/app/src/main/assets
"""

import datetime as dt
import json
import re
from pathlib import Path

from build_index import PLAN_DAYS, PLAN_START, build_plan
from common import DATA_DIR, MCQS_DIR, ROOT, SHEETS_DIR, load_json, load_syllabus, save_json, slugify

OUT = ROOT / "android" / "app" / "src" / "main" / "assets"
EXAM = dt.date(2027, 1, 3)
EXAM_LABEL = "3 Jan 2027"

# (book id, title, short, subjects) — listed in plan order; ids match APPSC Prep's subject numbering.
BOOKS = [
    (2, "Polity & Society – ROCKET Sheets", "Polity & Society", ["Indian Polity", "Indian Society"]),
    (1, "History – ROCKET Sheets", "History", ["Indian History", "AP History"]),
    (4, "Geography – ROCKET Sheets", "Geography",
     ["General Geography", "Indian Geography", "World Geography", "AP Geography"]),
    (3, "Economy – ROCKET Sheets", "Economy", ["Indian Economy"]),
    (5, "Science & Environment – ROCKET Sheets", "Science & Environment",
     ["Physics", "Chemistry", "Biology", "Environment", "Disaster Management"]),
]
UNIT_CODES = {
    "Indian Polity": "POL", "Indian Society": "SOC", "Indian History": "IH", "AP History": "APH",
    "General Geography": "GG", "Indian Geography": "IG", "World Geography": "WG", "AP Geography": "APG",
    "Indian Economy": "ECO", "Physics": "PHY", "Chemistry": "CHE", "Biology": "BIO",
    "Environment": "ENV", "Disaster Management": "DM",
}
FACTS_DIR = DATA_DIR / "facts"


def priority(facts: int) -> str:
    return "HIGH" if facts >= 80 else "MED" if facts >= 35 else "LIGHT"


def tracker_lines(tracker: dict, units: dict) -> tuple[list[str], list[str], str]:
    codes = tracker["g1"] + tracker["g2"]
    sub = [f"{'Group-I' if c.startswith('G1') else 'Group-II'} {c[3:]}: {units[c]['title']}" for c in codes if c in units]
    tag = " + ".join(t for t, k in (("GROUP-I", "g1"), ("GROUP-II", "g2")) if tracker[k]) or "ROCKET"
    return codes, sub, tag


def paragraphs(text: str) -> list[dict]:
    parts = [re.sub(r"\s*\n\s*", " ", p).strip() for p in re.split(r"\n\s*\n", text)]
    return [{"k": "p", "x": [[p, 0]]} for p in parts if p]


def main() -> None:
    syllabus = load_syllabus()
    units = {u["id"]: u for u in syllabus["units"]}
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        # kept from APPSC Prep: read-aloud short forms, checked full forms, dictionary (with dict/*.tsv);
        # keyterms.json is rebuilt by build_key_terms.py
        if old.name not in ("abbr.json", "acronyms.json", "india.json"):
            old.unlink()

    index_books, row_ref = [], {}  # row_ref: "slug/sheet" -> (book id, row index)
    row_meta = {}
    for book_id, title, short, subjects in sorted(BOOKS):
        book_units, mcq_rows, mcq_subs, idx_rows, pages = [], {}, {}, [], 0
        for ui, name in enumerate(subjects):
            slug = slugify(name)
            notes = load_json(SHEETS_DIR / f"{slug}.json")
            recs = load_json(MCQS_DIR / f"{slug}.json")["sheets"]
            facts = load_json(FACTS_DIR / f"{slug}.json", {"sheets": {}})["sheets"]
            pages += notes.get("pdf_pages", 0)
            rows = []
            for sh in notes["sheets"]:
                rec = recs.get(sh["id"], {"title": sh["heading"], "tracker": {"g1": [], "g2": []}, "mcqs": []})
                fl = facts.get(sh["id"], [])
                fact_text = {f["id"]: f["text"] for f in fl}
                codes, sub, tag = tracker_lines(rec["tracker"], units)
                p1, p2 = sh.get("pages", [0, 0])
                source = f"{name} · ROCKET SHEET #{sh['id']} · pp {p1}-{p2}"
                secs = [
                    {"t": f"Key facts ({len(fl)})", "badges": ["ROCKET"], "p": p1,
                     "b": [{"k": "n", "x": [["Source: ", 1], [f"{name} – ROCKET Sheets PDF, ROCKET SHEET #{sh['id']}, pages {p1}-{p2}", 0]]}]
                     + [{"k": "b", "x": [[f["text"] + " ", 0], [f"[{source}]", 4]]} for f in fl]},
                    {"t": "Sheet text (PDF)", "badges": [], "p": p1, "b": paragraphs(sh["text"])},
                ]
                row_index = len(idx_rows) + len(rows)
                rows.append({
                    "codes": codes, "tag": tag,
                    "title": f"#{sh['id']} · {rec['title']}",
                    "sub": sub, "pyq": "", "p1": p1, "p2": p2, "secs": secs,
                    "src": [f"Source: {name} – ROCKET Sheets.pdf, ROCKET SHEET #{sh['id']} (pp {p1}-{p2})"],
                })
                qs = [{
                    "id": "n" + q["id"], "s": q["question"], "o": q["options"], "a": q["answer"],
                    "x": q["explanation"],
                    "tq": f"{q['format']} · tests the “{q['keyword']}” angle" if q.get("keyword") else q["format"],
                    **({"n": [f"{fact_text[f]} [{name} · ROCKET SHEET #{sh['id']} · pp {p1}-{p2}]"
                              for f in q.get("facts", []) if f in fact_text]} if q.get("facts") else {}),
                } for q in rec["mcqs"]]
                if qs:
                    mcq_rows[str(row_index)] = qs
                    mcq_subs[str(row_index)] = [0] * len(qs)
                ref = f"{slug}/{sh['id']}"
                row_ref[ref] = (book_id, row_index)
                row_meta[ref] = {"codes": codes, "topic": f"{name} #{sh['id']} · {rec['title']}", "p": f"{p1}-{p2}",
                                 "pri": priority(len(fl)), "facts": len(fl), "q": len(qs)}
            book_units.append({"code": UNIT_CODES[name], "title": name, "rows": rows})
            for r in rows:
                idx_rows.append({"u": ui, "codes": r["codes"], "title": r["title"], "tag": r["tag"], "pyq": "",
                                 "p1": r["p1"], "p2": r["p2"], "n": len(r["secs"]),
                                 "q": len(mcq_rows.get(str(len(idx_rows)), []))})
        save_json(OUT / f"book{book_id}.json",
                  {"id": book_id, "title": title, "short": short, "pages": pages, "units": book_units})
        save_json(OUT / f"mcq{book_id}.json", {"rows": mcq_rows, "units": {}, "subs": mcq_subs})
        index_books.append({"id": book_id, "title": title, "short": short, "pages": pages,
                            "units": [{"code": u["code"], "title": u["title"]} for u in book_units],
                            "rows": idx_rows, "uq": {}})
        print(f"book{book_id} {short:22} {len(idx_rows):3} sections, {sum(len(v) for v in mcq_rows.values())} MCQs")
    save_json(OUT / "index.json", {"books": index_books})
    save_json(OUT / "plan.json", build_prep_plan(row_ref, row_meta))


def build_prep_plan(row_ref: dict, row_meta: dict) -> dict:
    index = load_json(DATA_DIR / "index.json")
    plan = build_plan(index["subjects"])
    start = dt.date.fromisoformat(PLAN_START)
    days = []
    for n, d in enumerate(plan, 1):
        date = start + dt.timedelta(days=n - 1)
        refs = [r for r in d["sheets"] if r in row_ref]
        rows = [{"codes": row_meta[r]["codes"], "topic": row_meta[r]["topic"], "p": row_meta[r]["p"], "pyq": "",
                 "pri": row_meta[r]["pri"], "ref": list(row_ref[r])} for r in refs]
        q = sum(row_meta[r]["q"] for r in refs)
        f = sum(row_meta[r]["facts"] for r in refs)
        kind = d["type"]
        if kind == "study":
            typ, phase, focus = "study", "PHASE 1 FIRST PASS", d.get("block", "").upper()
            brief = [f"Read: {d['title']} ({len(refs)} sheets, {f} facts).",
                     f"Practise: {q} MCQs – the MCQ practice after each sheet."]
            tasks = [
                {"time": "07:00-09:30", "block": "Sheets 1", "task": "First half of today's sheets: read Key facts, then Sheet text. Mark each sheet done."},
                {"time": "10:00-12:30", "block": "MCQs 1", "task": "MCQ practice after each of those sheets. Every miss: open the facts it tests."},
                {"time": "14:00-16:30", "block": "Sheets 2", "task": "Second half of today's sheets, same routine."},
                {"time": "17:00-19:00", "block": "MCQs 2", "task": "MCQ practice for the second half, then Retry wrong answers."},
                {"time": "20:00-20:30", "block": "Recall", "task": "Close the app: recall each sheet's facts aloud; reopen the ones you missed."},
            ]
        elif kind == "review":
            typ, phase, focus = "sunday", "WEEKLY REVIEW", ""
            brief = [f"This week's sheets: {len(refs)} sheets, {q} MCQs.", "Weekly test: 50 random MCQs from them."]
            tasks = [
                {"time": "08:00-09:00", "block": "Weekly test", "task": "Topic practice on this week's sheets: 50 random questions, net score with 1/3 negative marking."},
                {"time": "09:30-12:30", "block": "Repair", "task": "Re-read the Key facts of every sheet behind a wrong answer."},
                {"time": "14:00-16:00", "block": "Saved", "task": "Go through Saved questions and retry wrong answers."},
            ]
        elif kind == "revision":
            typ, phase, focus = "revision", "PHASE 2 R2 REVISION", d.get("block", "").upper()
            brief = [f"{d['title']}: {len(refs)} sheets, {q} MCQs.", "Revise Key facts; practise the weak sheets."]
            tasks = [
                {"time": "07:00-12:30", "block": "Revise", "task": "Key facts of each sheet below; spend longest on sheets with low MCQ scores."},
                {"time": "14:00-17:00", "block": "MCQs", "task": "Practise 100 random MCQs from these sheets; retry wrong answers."},
            ]
        else:  # mock / repair
            typ, phase, focus = "mock", "PHASE 3 MOCKS", ""
            brief = [d["title"]]
            tasks = [
                {"time": "09:30-12:00", "block": "Mock" if kind == "mock" else "Repair",
                 "task": "Full mixed test from all 721 sheets, exam timing, net score with 1/3 negative marking."
                 if kind == "mock" else "Retry every wrong answer; re-read the facts behind each miss."},
                {"time": "13:00-15:00", "block": "Analyse", "task": "Sort misses: didn't know / confused / careless. Re-read those sheets."},
            ]
        days.append({
            "n": n, "date": date.isoformat(), "dow": date.strftime("%a"), "phase": phase, "focus": focus,
            "brief": brief, "left": f"{(EXAM - date).days} days to {EXAM_LABEL}", "type": typ,
            "rows": rows if typ != "mock" else [], "tasks": tasks,
            **({"quiz": d["questions"]} if d.get("questions") else {}),
            **({"repair": True} if kind == "repair" else {}),
        })
    end = start + dt.timedelta(days=PLAN_DAYS)
    buffer = [
        {"dates": f"{end.day} Dec", "work": "Polity & Society, History: sheets with the lowest MCQ scores first"},
        {"dates": f"{end.day + 1}-{end.day + 2} Dec", "work": "Geography, Economy: Key facts of HIGH sheets"},
        {"dates": f"{end.day + 3} Dec – 2 Jan", "work": "Science & Environment; then Saved questions and wrong answers"},
    ]
    return {"start": PLAN_START, "exam": EXAM.isoformat(), "examLabel": EXAM_LABEL, "days": days, "buffer": buffer}


if __name__ == "__main__":
    main()
