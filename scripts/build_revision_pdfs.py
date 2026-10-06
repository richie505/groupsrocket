#!/usr/bin/env python3
"""Rocket Revision's 90-day schedule as PDFs: one PDF per plan day with that day's revision points.

Adapted from APPSC Prep's tools/build_revision_pdfs.py (richie505/Group-app).
The day's sheets are the ones the MCQ schedule gives that day (scripts/build_mcq_pdfs.py): on study days the
sheets the plan brings in for the first time, on weekly reviews and revision days the sheets it revisits.
Each sheet prints its revision points from rev{n}.json (scripts/build_revision.py): the facts its MCQs test,
answers in bold. Mock days (no sheets) get no PDF.

Usage: python3 scripts/build_revision_pdfs.py android/app/src/main/assets android/app/src/revise/assets <out_dir> [--dark]
"""
import json
import sys
from concurrent.futures import ProcessPoolExecutor
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_mcq_pdfs as M  # noqa: E402
from reportlab.lib.colors import HexColor  # noqa: E402
from reportlab.lib.pagesizes import A4  # noqa: E402
from reportlab.lib.styles import ParagraphStyle  # noqa: E402
from reportlab.lib.units import mm  # noqa: E402
from reportlab.platypus import KeepTogether, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle  # noqa: E402


def markup(x):
    """Runs [[text, flags]] (or plain text) as Paragraph markup, bold kept."""
    if isinstance(x, str):
        return M.esc(x)
    return "".join(f"<b>{M.esc(t)}</b>" if f & 1 else M.esc(t) for t, f in x if not f & 8)


def styles():
    t = M.T
    return {
        "fact": ParagraphStyle("fact", fontName="Sans", fontSize=10, leading=14.2, textColor=HexColor(t["body"]),
                               leftIndent=12, firstLineIndent=-9, spaceAfter=3.5),
        "angle": ParagraphStyle("angle", fontName="Sans", fontSize=9.6, leading=13.5, textColor=HexColor(t["pri"]["MED"])),
        "cell": ParagraphStyle("cell", fontName="Sans", fontSize=8.8, leading=11.8, textColor=HexColor(t["body"])),
        "head": ParagraphStyle("head", fontName="Sans-Bold", fontSize=8.8, leading=11.8, textColor=HexColor(t["ink"])),
        "para": ParagraphStyle("para", fontName="Sans-Bold", fontSize=9.6, leading=13.5, textColor=HexColor(t["ink"]), spaceBefore=3),
    }


def block_flow(b, st):
    """One revision block as PDF flowables."""
    k = b["k"]
    if k == "x":
        text = markup(b["x"])
        if text.startswith("Exam angle:"):
            text = text[len("Exam angle:"):].strip()
        box = Table([[Paragraph(f"<b>EXAM ANGLE</b>&nbsp;&nbsp;{text}", st["angle"])]], colWidths=[A4[0] - 32 * mm - 6])
        box.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, -1), HexColor("#FFF4E8" if not M.T["bg"] else "#2A1A0A")),
            ("LINEBEFORE", (0, 0), (0, -1), 2.2, HexColor(M.T["pri"]["MED"])),
            ("TOPPADDING", (0, 0), (-1, -1), 4), ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ("LEFTPADDING", (0, 0), (-1, -1), 7), ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ]))
        return [box, Spacer(1, 4)]
    if k == "t":
        rows = [[Paragraph(markup(c), st["head"]) for c in b["h"]]] + [[Paragraph(markup(c), st["cell"]) for c in r] for r in b["r"]]
        n = max(len(r) for r in rows)
        rows = [r + [""] * (n - len(r)) for r in rows]
        width = A4[0] - 32 * mm - 6
        first = min(0.32, 1.0 / n) if n > 1 else 1.0
        cols = [width * first] + [width * (1 - first) / (n - 1)] * (n - 1) if n > 1 else [width]
        t = Table(rows, colWidths=cols, repeatRows=1)
        t.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), HexColor(M.T["zebra"])),
            ("GRID", (0, 0), (-1, -1), 0.4, HexColor(M.T["line"])),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("TOPPADDING", (0, 0), (-1, -1), 3), ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ]))
        return [t, Spacer(1, 5)]
    if k == "n":  # the source line: shown in the sheet's meta line instead
        return []
    if k == "p":
        return [Paragraph(markup(b["x"]), st["para"])]
    return [Paragraph(f"•&nbsp;&nbsp;{markup(b['x'])}", st["fact"])]


def build_day(job):
    M.apply_theme(job["theme"])
    st = styles()
    S, T = M.S, M.T
    day, out = job["day"], Path(job["out"])
    index, rev = job["index"], job["rev"]
    n_day = day["n"]
    when = date.fromisoformat(day["date"])
    datestr = f"{day['dow']}, {when.day} {when.strftime('%b %Y')}"
    sections = [(b, ri) for b, ri, _ in job["rows"]]
    nfacts = sum(1 for b, ri in sections for s in rev[b][ri]["secs"] for x in s["b"] if x["k"] != "n")
    kind = "Revision of" if all(k == "rev" for *_, k in job["rows"]) else ""
    story = M.header_block(day, f"Day {n_day} of 90 · {M.day_title(day)}",
                           f"{datestr} · {M.title_case(day['phase'])} · {kind + ' ' if kind else ''}{len(sections)} sections · {nfacts} revision points", [])
    story[0] = Paragraph("ROCKET REVISION · DAILY REVISION POINTS", S["topic_k"])
    for i, (b, ri) in enumerate(sections, 1):
        story.append(Paragraph(f"{i}.&nbsp;&nbsp;{M.esc(index[b - 1]['rows'][ri]['title'])}", S["toc"]))
    story.append(Spacer(1, 10))

    last_unit = None
    for i, (b, ri) in enumerate(sections, 1):
        info = index[b - 1]
        row = info["rows"][ri]
        unit = (b, row["u"])
        if unit != last_unit:
            u = info["units"][row["u"]]
            story += [M.rule(), Spacer(1, 6),
                      Paragraph(f"TOPIC {row['u'] + 1} · {M.esc(info['short'].upper())} &nbsp;<font color='{T['muted']}'>{M.esc(u['code'])}</font>", S["topic_k"]),
                      Paragraph(M.esc(u["title"]), S["topic"]), Spacer(1, 8)]
            last_unit = unit
        pri = job["pri"].get((b, ri))
        meta = [f"<font color='{T['pri'][pri]}'><b>{pri}</b></font>"] if pri in T["pri"] else []
        meta += [M.esc(" · ".join(row["codes"]))] if row["codes"] else []
        meta += [f"Source: {M.esc(M.u_title(info, row))} · ROCKET SHEET {M.esc(row['title'].split(' · ')[0])} · pp {row['p1']}-{row['p2']}"]
        story.append(KeepTogether([Paragraph(f"{i}. {M.esc(row['title'])}", S["section"]),
                                   Paragraph("&nbsp;&nbsp;·&nbsp;&nbsp;".join(meta), S["meta"]), Spacer(1, 6)]))
        secs = rev[b][ri]["secs"]
        for si, sec in enumerate(secs):
            if not sec["b"]:
                continue
            flows = []
            for blk in sec["b"]:
                flows += block_flow(blk, st)
            if len(secs) > 1:  # Rocket sheets have one page: no subsection heading
                head = [Paragraph(f"{i}.{si + 1}&nbsp;&nbsp;{M.esc(sec['t'])}", S["subsec"]), Spacer(1, 3)]
                story.append(KeepTogether(head + flows[:2]))  # a heading never ends a page
                flows = flows[2:]
            story += flows
            story.append(Spacer(1, 6))
        story.append(Spacer(1, 4))

    footer = M.page_decor(f"Rocket Revision · Day {n_day} of 90 · {datestr}")
    out.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(str(out), pagesize=A4, leftMargin=16 * mm, rightMargin=16 * mm,
                            topMargin=14 * mm, bottomMargin=16 * mm,
                            title=f"Rocket Revision - Day {n_day}", author="Rocket Prep")
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    return out.name, nfacts


def main():
    assets, rev_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    theme = "dark" if "--dark" in sys.argv[4:] else "light"
    index, plan, _, mcq = M.load(assets)
    days, _ = M.schedule(index, plan, mcq)
    pri = {}
    for d in plan["days"]:
        for r in d["rows"]:
            if r.get("ref"):
                pri.setdefault(tuple(r["ref"]), r.get("pri"))
    rev = {}
    for b in (bk["id"] for bk in index):
        book = json.loads((rev_dir / f"rev{b}.json").read_text(encoding="utf-8"))
        rev[b] = [r for u in book["units"] for r in u["rows"]]
    jobs = [{"day": d, "rows": days[d["n"]], "index": index, "rev": rev, "pri": pri, "theme": theme,
             "out": str(out_dir / M.file_name(d))} for d in plan["days"] if days[d["n"]]]
    with ProcessPoolExecutor() as ex:
        for name, n in ex.map(build_day, jobs):
            print(f"{name}: {n} revision points")


if __name__ == "__main__":
    main()
