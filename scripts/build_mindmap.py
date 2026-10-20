#!/usr/bin/env python3
"""Mind maps for the revision app: every notes page as a tree of its own facts, nothing reworded.

    python3 scripts/build_mindmap.py android/app/src/main/assets android/app/src/revise/assets [book:rows ...]
(from APPSC Prep's tools/build_mindmap.py; the ROCKET notes cite their sheets in a grey "Source:" note, which goes)
Each page's tree goes into rev<book>.json as the page's "m" (the app draws it in place of the text), and the
same tree as text as the page's "b" (read aloud and searched):
  * the page title is the centre; each heading of the page is a branch (text before the first heading too);
  * a bullet "Label: details; more" is a node "Label" with the details as leaves, split at ";";
    a bullet without a label is one node; its sub-bullets are its children;
  * a table is a node naming its columns ("Act → Key provisions"); each row hangs from it as a node (its first
    cell) with a leaf per other cell ("Column: value" when the table has more than two columns);
  * exam angles are kept whole as highlighted nodes; source names ([GK], (CDI), TH, SES ...) and gap lines
    go - any date or fact next to a source name in a bracket stays.
Node: {"x": runs, "k": "b" | "x" (exam angle) | "t" (table row) | "g" (a table's columns), "c": [children]}
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
# source codes of the notes' citations; a bracket made only of these (with a date or page) is dropped
SOURCES = "ROCKET SHEET|ROCKET Sheets"  # the notes' facts carry no source tags; only sheet citations go
MONTH = r"(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\.?"
CITE = re.compile(r"\s*\(([^()]*)\)|\s*\[([^\[\]]*)\]")


def clean(rs):
    """Source names out of brackets ("(TH 17 Jul 2026)" -> "(17 Jul 2026)", "[GK]" and "(CDI)" go whole);
    everything else in a bracket - exam references, the other version where sources differ, data - stays."""
    out = []
    for t, f in rs:
        def drop(m):
            inner = m.group(1) if m.group(1) is not None else m.group(2)
            if not re.search(rf"\b(?:{SOURCES})\b", inner):
                return m.group()
            # source names (and their page numbers) out; any date or fact in the same bracket stays
            left = []
            for piece in re.split(r";|,(?=\s*[A-Za-z])", inner):
                x = re.sub(rf"\b(?:{SOURCES})\b|\b(?:notes?|key|data|table|box|summary|analysis)\b|\bp+\.?\s*\d+(?:-\d+)?", "", piece)
                x = re.sub(r"\s{2,}", " ", x).strip(" -–/:")
                if x:
                    left.append(x)
            if not left:
                return ""
            return (" (" if m.group().startswith(" ") else "(") + "; ".join(left) + ")" if m.group(1) is not None else "[" + "; ".join(left) + "]"
        t = CITE.sub(drop, t)
        if t:
            if out and out[-1][1] == f:
                out[-1][0] += t
            else:
                out.append([t, f])
    if out:
        out[0][0] = out[0][0].lstrip(" ;,")
        out[-1][0] = re.sub(r"[\s;,]+$", "", out[-1][0])
    for r in out:
        r[0] = re.sub(r"\s{2,}", " ", r[0])
    return [r for r in out if r[0]]


def rs(x):
    """runs as [[text, flags]] (a run may come without flags); muted asides out."""
    if isinstance(x, str):  # a plain-text cell
        x = [[x, 0]]
    out = [[r[0], r[1] if len(r) > 1 else 0] for r in x]
    return [r for r in out if not r[1] & 4]


def plain(r):
    return "".join(t for t, _ in r)


def cut(r, pos):
    """Split runs at a character offset."""
    a, b, n = [], [], 0
    for t, f in r:
        if n + len(t) <= pos:
            a.append([t, f])
        elif n >= pos:
            b.append([t, f])
        else:
            a.append([t[: pos - n], f])
            b.append([t[pos - n:], f])
        n += len(t)
    return a, b


def split_semis(r):
    """Split runs at '; ' outside brackets."""
    text = plain(r)
    depth, cuts = 0, []
    for i, ch in enumerate(text):
        depth += ch in "(["
        depth -= ch in ")]"
        if ch == ";" and depth == 0:
            cuts.append(i)
    parts, rest, off = [], r, 0
    for c in cuts:
        a, rest = cut(rest, c - off)
        off = c
        parts.append(a)
    parts.append(rest)
    out = []
    for p in parts:
        p = clean([list(x) for x in p])
        if p and plain(p).strip(" ;"):
            p[0][0] = p[0][0].lstrip(" ;")
            out.append(p)
    return out


def bullet_node(runs):
    r = clean(rs(runs))
    if not r or not plain(r).strip():
        return None
    text = plain(r)
    m = re.match(r"^([^:]{2,60}):\s+", text)
    if m and r[0][1] & 1 and len(r[0][0]) >= m.end(1):  # a bold "Label:" opens the bullet
        label, rest = cut(r, m.end(1))
        rest[0][0] = rest[0][0].lstrip(": ") if rest else ""
        if rest and rest[0][0] == "":
            rest = rest[1:]
        leaves = split_semis(rest)
        return {"x": clean(label), "k": "b", "c": [{"x": x, "k": "b", "c": []} for x in leaves]}
    return {"x": r, "k": "b", "c": []}


def table_nodes(b):
    head = [plain(clean(rs(c))).strip() for c in b["h"]]
    out = []
    for row in b["r"]:
        cells = [clean(rs(c)) for c in row]
        if not cells or not plain(cells[0]).strip():
            continue
        kids = []
        for i, c in enumerate(cells[1:], start=1):
            if not plain(c).strip():
                continue
            label = head[i] if i < len(head) else ""
            if len(cells) > 2 and label:
                kids.append({"x": [[label + ": ", 1]] + c, "k": "b", "c": []})
            else:
                kids += [{"x": x, "k": "b", "c": []} for x in split_semis(c)]
        title = [[t, f | 1] for t, f in cells[0]]
        out.append({"x": title, "k": "t", "c": kids})
    if any(head):
        # the columns as a group node: "Act → Key provisions" (headers can carry facts: "2024-25 (2nd AE)")
        cols = [x for x in head[1:] if x]
        label = head[0] + (" → " + " · ".join(cols) if cols else "") if head[0] else " · ".join(cols)
        return [{"x": [[label, 1]], "k": "g", "c": out}]
    return out


def prune(nodes):
    """Gap markers inside cells and bullets ("Year: Not in your sources") are not facts: out."""
    out = []
    for n in nodes:
        t = re.sub(r"^[^:]{0,40}:\s*", "", plain(n["x"])) if "Not in your sources" in plain(n["x"]) else plain(n["x"])
        if t.lstrip().startswith("Not in your sources") or plain(n["x"]).lstrip().startswith("Not in your sources"):
            continue
        n["c"] = prune(n["c"])
        out.append(n)
    return out


def page_map(blocks):
    branches, cur, last = [], None, None

    def branch(title):
        nonlocal cur, last
        cur = {"x": title, "k": "h", "c": []}
        branches.append(cur)
        last = None

    for b in blocks:
        k = b["k"]
        if k in ("n", "a"):  # the grey "Source: ROCKET SHEET #.." note and see-also links are not facts
            continue
        if k == "h":
            t = clean(rs(b["x"]))
            t[0][0] = re.sub(r"^\s*\d+\.\s*", "", t[0][0]) if t else ""
            branch([[plain(t).strip(), 1]])
            continue
        if cur is None:
            branch([["Key facts", 1]])
        if k == "t":
            cur["c"] += table_nodes(b)
            last = None
        elif k == "x":
            r = clean(rs(b["x"]))
            if r:
                cur["c"].append({"x": r, "k": "x", "c": []})
        elif k == "s" and last is not None:
            n = bullet_node(b["x"])
            if n:
                last["c"].append(n)
        else:
            if plain(rs(b.get("x", []))).lstrip().startswith("Not in your sources"):
                continue
            n = bullet_node(b.get("x", []))
            if n:
                cur["c"].append(n)
                last = n
    branches = [dict(b, c=prune(b["c"])) for b in branches]
    return [x for x in branches if x["c"]]


def map_blocks(branches):
    """The map as page text (read aloud, searched, counted): branch -> heading, node -> bullet, a node's leaves
    joined after it ("Label: a; b"), deeper nodes -> sub-bullets, exam angles kept."""
    out = []

    def line(n):
        r = [list(x) for x in n["x"]]
        leaves = [c for c in n["c"] if not c["c"]]
        if leaves and len(leaves) == len(n["c"]):
            r = r + [[": ", 0]]
            for i, c in enumerate(leaves):
                r += ([["; ", 0]] if i else []) + [list(x) for x in c["x"]]
            return r, []
        return r, n["c"]

    def walk(nodes, depth):
        for n in nodes:
            if n["k"] == "x":
                out.append({"k": "x", "x": n["x"]})
                continue
            r, kids = line(n)
            out.append({"k": "b" if depth == 1 else "s", "x": r})
            walk(kids, depth + 1)

    for b in branches:
        out.append({"k": "h", "x": b["x"]})
        walk(b["c"], 1)
    return out


def rows_arg(a):
    book, _, spec = a.partition(":")
    rows = set()
    for part in spec.split(","):
        x, _, y = part.partition("-")
        rows |= set(range(int(x), int(y or x) + 1))
    return int(book), rows


def main():
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    picks = [rows_arg(a) for a in sys.argv[3:]] or [(b, None) for b in range(1, 6)]
    for book, only in picks:
        notes = json.loads((src / f"book{book}.json").read_text())
        rev = json.loads((dst / f"rev{book}.json").read_text())
        nrows = [r for u in notes["units"] for r in u["rows"]]
        rrows = [r for u in rev["units"] for r in u["rows"]]
        pages = nodes = 0
        for ri, (n, r) in enumerate(zip(nrows, rrows)):
            if only is not None and ri not in only:
                continue
            for s_n, s_r in zip(n["secs"], r["secs"]):
                assert s_r["t"].split(" · Revision points")[0] == s_n["t"], (book, ri, s_n["t"])
                s_r["t"] = s_n["t"]  # the page is the whole map now, not a count of revision points
                s_r["m"] = page_map(s_n["b"])
                s_r["b"] = map_blocks(s_r["m"])
                pages += 1

                def count(xs):
                    return sum(1 + count(x["c"]) for x in xs)
                nodes += count(s_r["m"])
        (dst / f"rev{book}.json").write_text(json.dumps(rev, ensure_ascii=False, separators=(",", ":")))
        print(f"book {book}: {pages} pages mapped, {nodes} nodes")


if __name__ == "__main__":
    main()
