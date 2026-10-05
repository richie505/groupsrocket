#!/usr/bin/env python3
"""Rocket Prep 90-day MCQ schedule as PDFs: one PDF per plan day, MCQs only, each followed by its answer.

Adapted from APPSC Prep's tools/build_mcq_pdfs.py (richie505/Group-app) for the ROCKET Sheets app.
Headings follow the app: Day -> Topic (subject) -> Section (ROCKET SHEET #N) -> MCQs.

  * Study and Sunday days: every MCQ of each section the plan brings in for the first time that day.
    Sections the plan never lists go on the day of the nearest listed section of the same book, so
    all MCQs in the app appear exactly once across these days.
  * Days that only revisit earlier sections (weekly reviews, revision days): a revision set from those
    sections (HIGH 15, MED 8, LIGHT 5 questions per sheet, spread over the sheet).
  * Mock days (84-90) have no sections in the plan, so they get no PDF.

Each day also gets an answer key ("<out_dir>/Answer Keys/"): question numbers and answers only, in a grid
under the section headings, numbered exactly as in that day's MCQ PDF.

Usage: python3 scripts/build_mcq_pdfs.py android/app/src/main/assets <out_dir> [--dark]
  --dark   reverse print: white text on a black page
"""
import json
import re
import sys
from collections import defaultdict
from concurrent.futures import ProcessPoolExecutor
from datetime import date
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import KeepTogether, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
pdfmetrics.registerFont(TTFont("Sans", str(FONT_DIR / "DejaVuSans.ttf")))
pdfmetrics.registerFont(TTFont("Sans-Bold", str(FONT_DIR / "DejaVuSans-Bold.ttf")))
pdfmetrics.registerFontFamily("Sans", normal="Sans", bold="Sans-Bold", italic="Sans", boldItalic="Sans-Bold")

# Colours. "light": the app's colours (ui/theme/Theme.kt). "dark": reverse print, white text on a black page.
THEMES = {
    "light": {"bg": None, "zebra": "#F7F8FA", "title": "#0F2557", "ink": "#12203A", "body": "#1F2937",
              "muted": "#5A6B88", "faint": "#8A96AD", "accent": "#3949AB", "green": "#15803D", "line": "#E5E8EF",
              "pri": {"HIGH": "#C62828", "MED": "#B45309", "LOW": "#4B5563", "LIGHT": "#4B5563"}},
    "dark": {"bg": "#000000", "zebra": "#161616", "title": "#FFFFFF", "ink": "#FFFFFF", "body": "#FFFFFF",
             "muted": "#C8C8C8", "faint": "#A0A0A0", "accent": "#A5B4FC", "green": "#6EE7A0", "line": "#3A3A3A",
             "pri": {"HIGH": "#FF8A80", "MED": "#FFC46B", "LOW": "#D0D0D0", "LIGHT": "#D0D0D0"}},
}


def make_styles(t):
    c = {k: HexColor(v) for k, v in t.items() if isinstance(v, str)}
    return {
        "title": ParagraphStyle("title", fontName="Sans-Bold", fontSize=20, leading=25, textColor=c["title"]),
        "sub": ParagraphStyle("sub", fontName="Sans", fontSize=10.5, leading=15, textColor=c["muted"]),
        "brief": ParagraphStyle("brief", fontName="Sans", fontSize=9, leading=13, textColor=c["muted"]),
        "toc": ParagraphStyle("toc", fontName="Sans", fontSize=9, leading=13, textColor=c["body"], leftIndent=10),
        "topic_k": ParagraphStyle("topic_k", fontName="Sans-Bold", fontSize=8.5, leading=12, textColor=c["accent"]),
        "topic": ParagraphStyle("topic", fontName="Sans-Bold", fontSize=13, leading=17, textColor=c["ink"]),
        "section": ParagraphStyle("section", fontName="Sans-Bold", fontSize=11.5, leading=15.5, textColor=c["ink"]),
        "meta": ParagraphStyle("meta", fontName="Sans", fontSize=8.5, leading=12, textColor=c["muted"]),
        "subsec": ParagraphStyle("subsec", fontName="Sans-Bold", fontSize=10, leading=14, textColor=c["accent"]),
        "key_h": ParagraphStyle("key_h", fontName="Sans-Bold", fontSize=9.5, leading=13, textColor=c["ink"]),
        "key": ParagraphStyle("key", fontName="Sans", fontSize=8.6, leading=11, textColor=c["ink"]),
        "q": ParagraphStyle("q", fontName="Sans", fontSize=10, leading=14, textColor=c["body"], alignment=TA_LEFT),
        "opt": ParagraphStyle("opt", fontName="Sans", fontSize=9.6, leading=13.2, textColor=c["body"], leftIndent=26, firstLineIndent=-18),
        "ans": ParagraphStyle("ans", fontName="Sans-Bold", fontSize=9.6, leading=13.2, textColor=c["green"], leftIndent=8),
    }


T = THEMES["light"]
S = make_styles(T)


def apply_theme(name):
    global T, S
    T = THEMES[name]
    S = make_styles(T)


def page_decor(label):
    """onPage callback: page background (dark theme) and the footer."""
    def draw(canvas, doc):
        canvas.saveState()
        if T["bg"]:
            canvas.setFillColor(HexColor(T["bg"]))
            canvas.rect(0, 0, A4[0], A4[1], stroke=0, fill=1)
        canvas.setFont("Sans", 7.5)
        canvas.setFillColor(HexColor(T["muted"]))
        canvas.drawString(16 * mm, 9 * mm, label)
        canvas.drawRightString(A4[0] - 16 * mm, 9 * mm, f"Page {doc.page}")
        canvas.restoreState()
    return draw


def esc(text):
    return escape(text).replace("\n", "<br/>")


def title_case(focus):
    small = {"and", "of", "&", "the", "in", "+"}
    words = focus.lower().split()
    out = " ".join(w if w in small or not w[0].isalpha() else w[0].upper() + w[1:] for w in words)
    return out.replace("Ir", "IR")


def day_title(day):
    """The day's heading as the app shows it (DayScreens.kt): Sundays have no focus in the plan."""
    if not day["focus"] and day["type"] == "sunday":
        return "Weekly Review + Test"
    if not day["focus"] and day["type"] == "revision":
        return "All-Subject Revision"
    return title_case(day["focus"])


def load(assets):
    index = json.loads((assets / "index.json").read_text())["books"]
    plan = json.loads((assets / "plan.json").read_text())
    subs_titles, mcq = {}, {}
    for b in (bk["id"] for bk in index):
        book = json.loads((assets / f"book{b}.json").read_text())
        rows = [r for u in book["units"] for r in u["rows"]]
        subs_titles[b] = [[s["t"] for s in r["secs"]] for r in rows]
        m = json.loads((assets / f"mcq{b}.json").read_text())
        mcq[b] = {int(r): list(zip(m["subs"][r], qs)) for r, qs in m["rows"].items()}
    return index, plan, subs_titles, mcq


def schedule(index, plan, mcq):
    """day n -> list of (book, row, kind) where kind is 'new' or 'rev'."""
    first = {}
    for d in plan["days"]:
        for r in d["rows"]:
            if r.get("ref"):
                first.setdefault(tuple(r["ref"]), d["n"])
    # sections the plan never lists: same day as the nearest listed section of the book, placed after it
    extra = defaultdict(list)  # anchor (book,row) -> [rows]
    for b in index:
        bid, n = b["id"], len(b["rows"])
        for ri in range(n):
            if (bid, ri) in first or not mcq[bid].get(ri):
                continue
            anchor = next(((bid, j) for j in range(ri - 1, -1, -1) if (bid, j) in first), None) \
                or next(((bid, j) for j in range(ri + 1, n) if (bid, j) in first), None)
            extra[anchor].append((bid, ri))
    days = {}
    for d in plan["days"]:
        refs = list(dict.fromkeys(tuple(r["ref"]) for r in d["rows"] if r.get("ref")))
        new = [x for x in refs if first[x] == d["n"]]
        if new:
            out = []
            for x in new:
                out.append((*x, "new"))
                out += [(*e, "new") for e in extra.get(x, [])]
            days[d["n"]] = out
        else:
            days[d["n"]] = [(*x, "rev") for x in refs]
    return days, first


def pick_revision(qs, k):
    """k questions spread evenly over a section's list (it is in subsection order)."""
    if len(qs) <= k:
        return qs
    step = len(qs) / k
    return [qs[int(i * step)] for i in range(k)]


def question_block(n, q):
    flow = [Paragraph(f"<b>Q{n}.</b> {esc(q['s'])}", S["q"])]
    for i, o in enumerate(q["o"]):
        flow.append(Paragraph(f"({i + 1})&nbsp;&nbsp;{esc(o)}", S["opt"]))
    flow.append(Paragraph(f"Answer: ({q['a'] + 1}) {esc(q['o'][q['a']])}", S["ans"]))
    flow.append(Spacer(1, 7))
    return KeepTogether(flow)


def header_block(day, headline, sub, lines):
    flow = [
        Paragraph("ROCKET PREP · MCQ SCHEDULE", S["topic_k"]),
        Spacer(1, 3),
        Paragraph(esc(headline), S["title"]),
        Spacer(1, 2),
        Paragraph(esc(sub), S["sub"]),
        Spacer(1, 6),
    ]
    flow += [Paragraph(esc(t), S["brief"]) for t in lines]
    flow.append(Spacer(1, 8))
    return flow


def rule():
    t = Table([[""]], colWidths=["100%"], rowHeights=[0.6])
    t.setStyle(TableStyle([("LINEABOVE", (0, 0), (-1, -1), 0.6, HexColor(T["line"]))]))
    return t


def select(job):
    """The day's sections and their MCQs, in print order: [(book, row, [(sub, q)])], plus a summary line."""
    rows, mcq = job["rows"], job["data"]["mcq"]
    rev = all(k == "rev" for *_, k in rows)
    sel = []  # (book, row, [(sub, q)])
    sweep = len(rows) > 300  # all-subject revision day: one MCQ from every sheet
    for b, ri, kind in rows:
        # subsection order (the generator interleaves them a little); section-level ones last
        qs = sorted(mcq[b].get(ri, []), key=lambda x: (x[0] < 0, x[0]))
        if kind == "rev":
            pri = job["pri"].get((b, ri), "MED")
            qs = pick_revision(qs, 1 if sweep else {"HIGH": 15, "MED": 8, "LOW": 5, "LIGHT": 5}.get(pri, 8))
        if qs:
            sel.append((b, ri, qs))
    total = sum(len(qs) for *_, qs in sel)
    kind_line = (f"Revision set · {total} MCQs from today's {len(sel)} sections (first given on Days "
                 f"{min(job['first'][(b, r)] for b, r, _ in sel)}–{max(job['first'][(b, r)] for b, r, _ in sel)})"
                 if rev else f"{total} MCQs · {len(sel)} sections")
    return sel, kind_line


def build_day(job):
    apply_theme(job["theme"])
    day, rows, data, out = job["day"], job["rows"], job["data"], Path(job["out"])
    index, subs_titles, mcq = data["index"], data["subs"], data["mcq"]
    n_day = day["n"]
    when = date.fromisoformat(day["date"])
    datestr = f"{day['dow']}, {when.day} {when.strftime('%b %Y')}"
    story = []
    qn = 0

    sel, kind_line = select(job)
    story += header_block(day, f"Day {n_day} of 90 · {day_title(day)}",
                          f"{datestr} · {title_case(day['phase'])} · {kind_line}", [])
    # contents: sections with question ranges
    start = 1
    toc = []
    for i, (b, ri, qs) in enumerate(sel, 1):
        row = index[b - 1]["rows"][ri]
        toc.append(Paragraph(f"{i}.&nbsp;&nbsp;{esc(row['title'])} <font color='{T['faint']}'>· Q{start}–{start + len(qs) - 1}</font>", S["toc"]))
        start += len(qs)
    story += toc + [Spacer(1, 10)]

    last_unit = None
    for i, (b, ri, qs) in enumerate(sel, 1):
        info = index[b - 1]
        row = info["rows"][ri]
        unit = (b, row["u"])
        if unit != last_unit:
            u = info["units"][row["u"]]
            story.append(rule())
            story.append(Spacer(1, 6))
            story.append(Paragraph(f"TOPIC {row['u'] + 1} · {esc(info['short'].upper())} &nbsp;<font color='{T['muted']}'>{esc(u['code'])}</font>", S["topic_k"]))
            story.append(Paragraph(esc(u["title"]), S["topic"]))
            story.append(Spacer(1, 8))
            last_unit = unit
        pri = job["pri"].get((b, ri))
        meta = [f"<font color='{T['pri'][pri]}'><b>{pri}</b></font>"] if pri in T["pri"] else []
        meta += [esc(" · ".join(row["codes"]))] if row["codes"] else []
        meta += [f"Source: {esc(u_title(info, row))} · ROCKET SHEET {esc(row['title'].split(' · ')[0])} · pp {row['p1']}-{row['p2']}",
                 f"{len(qs)} MCQs"]
        story.append(KeepTogether([
            Paragraph(f"{i}. {esc(row['title'])}", S["section"]),
            Paragraph("&nbsp;&nbsp;·&nbsp;&nbsp;".join(meta), S["meta"]),
            Spacer(1, 6),
        ]))
        last_sub = 0 if all(si == 0 for si, _ in qs) else None  # one subsection: no extra heading
        for si, q in qs:
            if si != last_sub:
                name = subs_titles[b][ri][si] if si >= 0 else "Other MCQs of this section"
                num = f"{i}.{si + 1}" if si >= 0 else f"{i}.–"
                story.append(Paragraph(f"{num}&nbsp;&nbsp;{esc(name)}", S["subsec"]))
                story.append(Spacer(1, 4))
                last_sub = si
            qn += 1
            story.append(question_block(qn, q))
        story.append(Spacer(1, 6))

    footer = page_decor(f"Rocket Prep · Day {n_day} of 90 · {datestr}")

    out.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(str(out), pagesize=A4, leftMargin=16 * mm, rightMargin=16 * mm,
                            topMargin=14 * mm, bottomMargin=16 * mm,
                            title=f"Rocket Prep MCQ Schedule - Day {n_day}", author="Rocket Prep")
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    return n_day, qn, out.name


def build_key(job):
    """Answer key: question number and answer only, in a grid under each section's heading."""
    apply_theme(job["theme"])
    day, out = job["day"], Path(job["key_out"])
    index = job["data"]["index"]
    n_day = day["n"]
    when = date.fromisoformat(day["date"])
    datestr = f"{day['dow']}, {when.day} {when.strftime('%b %Y')}"
    sel, kind_line = select(job)
    story = header_block(day, f"Day {n_day} of 90 · Answer Key",
                         f"{day_title(day)} · {datestr} · {kind_line}",
                         ["Answers are option numbers (1)–(4), as printed in the day's MCQ PDF."])
    cols = 10
    width = (A4[0] - 32 * mm) / cols
    qn = 0
    for i, (b, ri, qs) in enumerate(sel, 1):
        row = index[b - 1]["rows"][ri]
        head = Paragraph(f"{i}. {esc(row['title'])} <font color='{T['faint']}'>· Q{qn + 1}–{qn + len(qs)}</font>", S["key_h"])
        cells = []
        for _, q in qs:
            qn += 1
            cells.append(Paragraph(f"<font color='{T['muted']}'>{qn}.</font>&nbsp;<b>({q['a'] + 1})</b>", S["key"]))
        grid = [cells[k:k + cols] for k in range(0, len(cells), cols)]
        grid[-1] += [""] * (cols - len(grid[-1]))
        t = Table(grid, colWidths=[width] * cols)
        t.setStyle(TableStyle([
            ("ROWBACKGROUNDS", (0, 0), (-1, -1), [HexColor(T["bg"] or "#FFFFFF"), HexColor(T["zebra"])]),
            ("LINEBELOW", (0, 0), (-1, -1), 0.3, HexColor(T["line"])),
            ("TOPPADDING", (0, 0), (-1, -1), 2), ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
            ("LEFTPADDING", (0, 0), (-1, -1), 3), ("RIGHTPADDING", (0, 0), (-1, -1), 2),
        ]))
        # short sections stay on one page; long grids may break across pages
        story += [KeepTogether([head, Spacer(1, 3), t])] if len(grid) <= 8 else [head, Spacer(1, 3), t]
        story.append(Spacer(1, 8))

    footer = page_decor(f"Rocket Prep · Day {n_day} of 90 · Answer Key")

    out.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(str(out), pagesize=A4, leftMargin=16 * mm, rightMargin=16 * mm,
                            topMargin=14 * mm, bottomMargin=16 * mm,
                            title=f"Rocket Prep MCQ Schedule - Day {n_day} Answer Key", author="Rocket Prep")
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    return n_day, qn, out.name


def u_title(info, row):
    return info["units"][row["u"]]["title"]


def file_name(day):
    focus = re.sub(r"\((\d)/(\d)\)", r"(\1 of \2)", day_title(day))
    focus = re.sub(r'[\\:*?"<>|]', "", focus)
    return f"Day {day['n']:02d} - {focus}.pdf"


def main():
    assets, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    theme = "dark" if "--dark" in sys.argv[3:] else "light"
    index, plan, subs_titles, mcq = load(assets)
    days, first = schedule(index, plan, mcq)
    pri = {}
    for d in plan["days"]:
        for r in d["rows"]:
            if r.get("ref"):
                pri.setdefault(tuple(r["ref"]), r.get("pri"))

    data = {"index": index, "subs": subs_titles, "mcq": mcq}
    jobs = []
    for d in plan["days"]:
        if not days[d["n"]]:  # mock week: no sections
            continue
        jobs.append({"day": d, "rows": days[d["n"]], "data": data, "pri": pri, "first": first, "theme": theme,
                     "out": str(out_dir / file_name(d)),
                     "key_out": str(out_dir / "Answer Keys" / file_name(d).replace(".pdf", " - Answer Key.pdf"))})
    once = sum(len(mcq[b].get(r, [])) for rows in days.values() for b, r, k in rows if k == "new")
    print("MCQs given once on study days:", once, "of", sum(len(v) for b in mcq for v in mcq[b].values()))
    with ProcessPoolExecutor() as ex:
        for n, qn, name in ex.map(build_day, jobs):
            print(f"{name}: {qn} MCQs")
        for n, qn, name in ex.map(build_key, jobs):
            print(f"{name}: {qn} answers")


if __name__ == "__main__":
    main()
