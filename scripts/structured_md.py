"""(From APPSC Prep, richie505/Group-app tools/structured_md.py.) Structured notes: a small markdown form <-> the app's blocks.

    # Heading                 -> {"k": "h"}
    - **Label:** text         -> {"k": "b"}
      - text                  -> {"k": "s"}
    !! Not in your sources... -> the gap line (run flag 6), on a bullet or sub-bullet
    > text                    -> exam angle {"k": "x"}
    @@ text                   -> see-also {"k": "a"};   ~~ text -> grey note {"k": "n"};   plain line -> {"k": "p"}
    **x** bold, [GK] muted (flag 4)
Tables are kept as they are: "<<table N>>" stands for the page's N-th table block.
"""
import re

TOKEN = re.compile(r"(\*\*.+?\*\*|\[GK\])")


def unmark(text):
    """Drops "!!" left inside a line (italic bits of a gap line were exported with the marker)."""
    return re.sub(r" {2,}", " ", re.sub(r"\s*!!\s*", " ", text)).replace(" ;", ";").replace(" '", "'").replace(" )", ")").strip()


def runs(text):
    gap = text.startswith("!! ")
    if gap:
        return [[unmark(text[3:]), 6]]
    text = unmark(text) if "!!" in text else text
    out = []
    for part in TOKEN.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**"):
            out.append([part[2:-2], 1])
        elif part == "[GK]":
            out.append([part, 4])
        else:
            out.append([part, 0])
    return out


def cell_text(cell):
    """A table cell's runs as markdown (bold kept, [GK] kept, '|' made safe)."""
    x = cell if isinstance(cell, list) else [[cell, 0]]
    out = ""
    for t, f in x:
        if f & 8:
            continue
        t = t.replace("|", "/").replace("\n", " ")
        out += f"**{t}**" if f & 1 and not f & 4 and t.strip() else t
    return re.sub(r"\s+", " ", out).strip()


def table_md(b):
    head = "| " + " | ".join(cell_text(c) for c in b["h"]) + " |"
    sep = "|" + "---|" * len(b["h"])
    rows = ["| " + " | ".join(cell_text(c) for c in r) + " |" for r in b["r"]]
    return "\n".join([head, sep] + rows)


def parse_table(lines):
    cells = [[c.strip() for c in l.strip().strip("|").split("|")] for l in lines]
    cells = [r for r in cells if not all(re.fullmatch(r":?-{2,}:?", c or "--") for c in r)]
    head, rows = cells[0], cells[1:]
    n = len(head)
    rows = [(r + [""] * n)[:n] for r in rows]
    return {"k": "t", "h": [runs(c) for c in head], "r": [[runs(c) for c in r] for r in rows]}


def to_blocks(md, tables):
    blocks = []
    lines = md.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        i += 1
        if line.strip().startswith("|"):
            group = [line]
            while i < len(lines) and lines[i].strip().startswith("|"):
                group.append(lines[i])
                i += 1
            blocks.append(parse_table(group))
            continue
        if not line.strip():
            continue
        m = re.match(r"<<table (\d+)>>\s*$", line.strip())
        if m:
            blocks.append(tables[int(m.group(1))])
            continue
        if line.startswith("@@ "):
            blocks.append({"k": "a", "x": runs(line[3:].strip())})
        elif line.startswith("~~ "):
            blocks.append({"k": "n", "x": runs(line[3:].strip())})
        elif line.startswith("# "):
            blocks.append({"k": "h", "x": runs(line[2:].strip())})
        elif re.match(r"\s{2,}- ", line):
            blocks.append({"k": "s", "x": runs(line.strip()[2:])})
        elif line.startswith("- "):
            blocks.append({"k": "b", "x": runs(line[2:].strip())})
        elif line.startswith("> "):
            blocks.append({"k": "x", "x": runs(line[2:].strip())})
        else:
            blocks.append({"k": "p", "x": runs(line.strip())})
    return blocks


def from_blocks(blocks, inline_tables=False):
    """A page as the markdown form (input for rewriting): tables as placeholders, or written out as
    markdown tables when [inline_tables]."""
    lines, tables = [], []
    for b in blocks:
        if b["k"] == "t":
            if inline_tables:
                lines.append(table_md(b))
            else:
                lines.append(f"<<table {len(tables)}>>")
            tables.append(b)
            continue
        text = ""
        x = b["x"] if isinstance(b["x"], list) else [[b["x"], 0]]
        for t, f in x:
            if f & 6 == 6 or f == 6:
                text += "!! " + t
            elif f & 4 and t.strip() == "[GK]":
                text += t
            elif f & 1:
                text += f"**{t}**" if t.strip() else t
            elif f & 8:
                continue
            else:
                text += t
        prefix = {"h": "# ", "b": "- ", "s": "  - ", "x": "> ", "a": "@@ ", "n": "~~ "}.get(b["k"], "")
        lines.append(prefix + text.strip())
    return "\n".join(lines), tables
