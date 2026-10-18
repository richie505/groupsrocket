"""Write the Rocket Prep app's assets (APPSC Prep format) from the ROCKET data.

Syllabus mapping — the ROCKET sheets are the app's own syllabus:
    Book     = subject block in 90-day-plan order (ids follow APPSC Prep's subject numbering,
               which drives the MCQ technique hints: 1 History, 2 Polity, 3 Economy, 4 Geography, 5 Science)
    Topic    = subject (e.g. Indian Polity, Indian Society)
    Section  = one ROCKET SHEET #N
    Subsections = "Key facts" (every fact of the sheet) and "Sheet text" (the notes as in the PDF)
MCQ practice after each section = the sheet's MCQs; each question lists the facts it tests.
MCQs trimmed in data/mcq_trim/ (repeats, trivia, off-notes dropped; some moved or merged) are applied here,
and the explanations and technique lines rewritten in data/mcq_why/ (scripts/mcq_why.py).

    python scripts/build_prep_assets.py            # -> android/app/src/main/assets
"""

import datetime as dt
import json
import re
from pathlib import Path

from build_index import PLAN_DAYS, PLAN_START, build_plan
import mcq_trim
import mcq_why
import structured_md
import topic_check
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
TOPICS_DIR = DATA_DIR / "topics"
STRUCT_DIR = DATA_DIR / "structured"


def priority(facts: int) -> str:
    """A topic's weight in the plan by its facts."""
    return "HIGH" if facts >= 200 else "MED" if facts >= 90 else "LIGHT"


def tracker_lines(tracker: dict, units: dict) -> tuple[list[str], list[str], str]:
    codes = tracker["g1"] + tracker["g2"]
    sub = [f"{'Group-I' if c.startswith('G1') else 'Group-II'} {c[3:]}: {units[c]['title']}" for c in codes if c in units]
    tag = " + ".join(t for t, k in (("GROUP-I", "g1"), ("GROUP-II", "g2")) if tracker[k]) or "ROCKET"
    return codes, sub, tag


def paragraphs(text: str) -> list[dict]:
    parts = [re.sub(r"\s*\n\s*", " ", p).strip() for p in re.split(r"\n\s*\n", text)]
    return [{"k": "p", "x": [[p, 0]]} for p in parts if p]


def topic_pages(slug: str, ti: int, name: str, sheets: list) -> tuple[list[dict], dict, dict]:
    """A topic's pages, and where each fact and sheet landed: (secs, "sid:F3" -> page, sid -> first page).
    Rewritten topics (data/structured/, checked by topic_check.py) give structured pages; the others one
    page per sheet with its facts (until they are rewritten)."""
    md_path = STRUCT_DIR / slug / f"{ti:02d}.md"
    pages_of = {sid: sh.get("pages", [0, 0]) for sid, sh, _, _ in sheets}
    if md_path.exists():
        ok, placed = topic_check.check(slug, ti, verbose=False)
        if not ok:
            raise SystemExit(f"{slug}:{ti} does not pass topic_check.py - fix it first")
        _, pages = topic_check.parse(md_path.read_text(encoding="utf-8"))
        secs, first = [], {}
        for i, (title, frm, body) in enumerate(pages):
            for sid in frm:
                first.setdefault(sid, i)
            src = ", ".join(f"#{x} (pp {pages_of[x][0]}-{pages_of[x][1]})" for x in frm)
            secs.append({"t": title, "badges": ["ROCKET"], "p": pages_of[frm[0]][0],
                         "b": [{"k": "n", "x": [["Source: ", 1], [f"{name} – ROCKET Sheets PDF, ROCKET SHEET {src}", 0]]}]
                         + structured_md.to_blocks("\n".join(body), [])})
        return secs, placed, first
    secs, placed, first = [], {}, {}
    for sid, sh, title, fl in sheets:
        p1, p2 = pages_of[sid]
        first[sid] = len(secs)
        for f in fl:
            placed[f"{sid}:{f['id']}"] = len(secs)
        secs.append({"t": f"#{sid} · {title}", "badges": ["ROCKET"], "p": p1,
                     "b": [{"k": "n", "x": [["Source: ", 1], [f"{name} – ROCKET Sheets PDF, ROCKET SHEET #{sid}, pages {p1}-{p2}", 0]]}]
                     + [{"k": "b", "x": [[f["text"], 0]]} for f in fl]})
    return secs, placed, first


def main() -> None:
    syllabus = load_syllabus()
    units = {u["id"]: u for u in syllabus["units"]}
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        # kept from APPSC Prep: read-aloud short forms, checked full forms, dictionary (with dict/*.tsv);
        # keyterms.json is rebuilt by build_key_terms.py
        if old.name not in ("abbr.json", "acronyms.json", "india.json"):
            old.unlink()

    index_books, row_ref, row_meta, topics_of = [], {}, {}, {}
    moves = {}  # read marks: old sheet page "book:sheet row:0|1" -> new "book:topic row:page"
    for book_id, title, short, subjects in sorted(BOOKS):
        book_units, mcq_rows, mcq_subs, idx_rows, pages = [], {}, {}, [], 0
        old_row = 0
        for ui, name in enumerate(subjects):
            slug = slugify(name)
            notes = load_json(SHEETS_DIR / f"{slug}.json")
            by_id = {sh["id"]: sh for sh in notes["sheets"]}
            old_index = {sh["id"]: old_row + i for i, sh in enumerate(notes["sheets"])}
            old_row += len(notes["sheets"])
            recs = load_json(MCQS_DIR / f"{slug}.json")["sheets"]
            facts = load_json(FACTS_DIR / f"{slug}.json", {"sheets": {}})["sheets"]
            pages += notes.get("pdf_pages", 0)
            rows, topics_of[name] = [], []
            for ti, topic in enumerate(load_json(TOPICS_DIR / f"{slug}.json")["topics"]):
                sheets = [(sid, by_id[sid], recs.get(sid, {}).get("title") or by_id[sid]["heading"], facts.get(sid, []))
                          for sid in topic["sheets"]]
                secs, placed, first = topic_pages(slug, ti, name, sheets)
                codes, sub, tags = [], [], []
                for sid, *_ in sheets:
                    c, sb, tg = tracker_lines(recs.get(sid, {}).get("tracker", {"g1": [], "g2": []}), units)
                    codes += [x for x in c if x not in codes]
                    sub += [x for x in sb if x not in sub]
                    tags.append(tg)
                tag = " + ".join(t for t in ("GROUP-I", "GROUP-II") if any(t in x for x in tags)) or "ROCKET"
                p1 = min(sh.get("pages", [0, 0])[0] for _, sh, _, _ in sheets)
                p2 = max(sh.get("pages", [0, 0])[1] for _, sh, _, _ in sheets)
                row_index = len(idx_rows) + len(rows)
                src = ", ".join(f"#{sid} (pp {sh['pages'][0]}-{sh['pages'][1]})" for sid, sh, _, _ in sheets)
                rows.append({
                    "codes": codes, "tag": tag, "title": topic["title"], "sub": sub, "pyq": "", "p1": p1, "p2": p2,
                    "secs": secs, "src": [f"Source: {name} – ROCKET Sheets.pdf, ROCKET SHEET {src}"],
                })
                qs, subs, notes_of = [], [], {}
                decisions = mcq_trim.load_decisions(slug, ti)
                drop = {d["q"] for d in decisions if "d" in d} | {x for d in decisions for x in d.get("merge", [])}
                move = {d["q"]: d["move"] for d in decisions if "move" in d}
                why = mcq_why.load(slug, ti)  # APPSC-style explanation and technique line, where written
                for sid, sh, _, fl in sheets:
                    fact_text = {f["id"]: f["text"] for f in fl}
                    sp1, sp2 = sh.get("pages", [0, 0])
                    for q in recs.get(sid, {}).get("mcqs", []):
                        lab = mcq_trim.label(q)
                        notes_of[lab] = [f"{fact_text[f]} [{name} · ROCKET SHEET #{sid} · pp {sp1}-{sp2}]"
                                         for f in q.get("facts", []) if f in fact_text]
                        if lab in drop:
                            continue
                        at = [placed[f"{sid}:{f}"] for f in q.get("facts", []) if f"{sid}:{f}" in placed]
                        subs.append(move.get(lab, max(set(at), key=at.count) if at else first[sid]))
                        qs.append({
                            "id": "n" + q["id"], "s": why.get(lab, {}).get("s", q["question"]),
                            "o": why.get(lab, {}).get("o", q["options"]),
                            "a": why.get(lab, {}).get("a", q["answer"]),
                            "x": why.get(lab, {}).get("x") or q["explanation"],
                            "tq": why.get(lab, {}).get("t") or (f"{q['format']} · tests the “{q['keyword']}” angle"
                                                                 if q.get("keyword") else q["format"]),
                            **({"n": notes_of[lab]} if notes_of[lab] else {}),
                        })
                for d in decisions:  # one MCQ in place of several closely linked ones
                    if "merge" in d:
                        n = [x for lab in d["merge"] for x in notes_of.get(lab, [])]
                        subs.append(d.get("page", 0))
                        qs.append({"id": f"n{slug}-m{ti}-{'-'.join(d['merge'])}", "s": d["s"], "o": d["o"], "a": d["a"],
                                   "x": d["x"], "tq": d.get("tq", "Merged"), **({"n": n} if n else {})})
                for sid, *_ in sheets:
                    for old_sec in (0, 1):
                        moves[f"{book_id}:{old_index[sid]}:{old_sec}"] = f"{book_id}:{row_index}:{first[sid]}"
                if qs:
                    mcq_rows[str(row_index)] = qs
                    mcq_subs[str(row_index)] = subs
                ref = f"{slug}/T{ti}"
                row_ref[ref] = (book_id, row_index)
                nf = sum(len(fl) for *_, fl in sheets)
                row_meta[ref] = {"codes": codes, "topic": f"{name} · {topic['title']}", "p": f"{p1}-{p2}",
                                 "pri": priority(nf), "facts": nf, "q": len(qs), "sheets": len(sheets)}
                topics_of[name].append({"ref": ref, "title": topic["title"], "count": len(qs)})
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
        print(f"book{book_id} {short:22} {len(idx_rows):3} topics, {sum(len(v) for v in mcq_rows.values())} MCQs")
    save_json(OUT / "index.json", {"books": index_books})
    save_json(OUT / "plan.json", build_prep_plan(row_ref, row_meta, topics_of))
    # read marks and bookmarks follow their sheet to its topic page (once, ProgressStore.migrate)
    save_json(OUT / "moved.json", {"v": "rocket-topics-1", "m": moves, "g": []})


def build_prep_plan(row_ref: dict, row_meta: dict, topics_of: dict) -> dict:
    index = load_json(DATA_DIR / "index.json")
    plan = build_plan(index["subjects"], topics_of)
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
            brief = [f"Read: {d['title']} ({len(refs)} topics, {sum(row_meta[r]['sheets'] for r in refs)} ROCKET sheets, {f} facts).",
                     f"Practise: {q} MCQs – the MCQ practice after each topic."]
            tasks = [
                {"time": "07:00-09:30", "block": "Topics 1", "task": "First half of today's topics: read each page, mark it read."},
                {"time": "10:00-12:30", "block": "MCQs 1", "task": "MCQ practice after each of those topics. Every miss: open the facts it tests."},
                {"time": "14:00-16:30", "block": "Topics 2", "task": "Second half of today's topics, same routine."},
                {"time": "17:00-19:00", "block": "MCQs 2", "task": "MCQ practice for the second half, then Retry wrong answers."},
                {"time": "20:00-20:30", "block": "Recall", "task": "Close the app: recall each topic's facts aloud; reopen the ones you missed."},
            ]
        elif kind == "review":
            typ, phase, focus = "sunday", "WEEKLY REVIEW", ""
            brief = [f"This week's topics: {len(refs)} topics, {q} MCQs.", "Weekly test: 50 random MCQs from them."]
            tasks = [
                {"time": "08:00-09:00", "block": "Weekly test", "task": "Practice on this week's topics: 50 random questions, net score with 1/3 negative marking."},
                {"time": "09:30-12:30", "block": "Repair", "task": "Re-read the page behind every wrong answer."},
                {"time": "14:00-16:00", "block": "Saved", "task": "Go through Saved questions and retry wrong answers."},
            ]
        elif kind == "revision":
            typ, phase, focus = "revision", "PHASE 2 R2 REVISION", d.get("block", "").upper()
            brief = [f"{d['title']}: {len(refs)} topics, {q} MCQs.", "Revise the pages; practise the weak topics."]
            tasks = [
                {"time": "07:00-12:30", "block": "Revise", "task": "Pages of each topic below; spend longest on topics with low MCQ scores."},
                {"time": "14:00-17:00", "block": "MCQs", "task": "Practise 100 random MCQs from these topics; retry wrong answers."},
            ]
        else:  # mock / repair
            typ, phase, focus = "mock", "PHASE 3 MOCKS", ""
            brief = [d["title"]]
            tasks = [
                {"time": "09:30-12:00", "block": "Mock" if kind == "mock" else "Repair",
                 "task": "Full mixed test from all 721 sheets, exam timing, net score with 1/3 negative marking."
                 if kind == "mock" else "Retry every wrong answer; re-read the facts behind each miss."},
                {"time": "13:00-15:00", "block": "Analyse", "task": "Sort misses: didn't know / confused / careless. Re-read those pages."},
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
        {"dates": f"{end.day} Dec", "work": "Polity & Society, History: topics with the lowest MCQ scores first"},
        {"dates": f"{end.day + 1}-{end.day + 2} Dec", "work": "Geography, Economy: HIGH topics"},
        {"dates": f"{end.day + 3} Dec – 2 Jan", "work": "Science & Environment; then Saved questions and wrong answers"},
    ]
    return {"start": PLAN_START, "exam": EXAM.isoformat(), "examLabel": EXAM_LABEL, "days": days, "buffer": buffer}


if __name__ == "__main__":
    main()
