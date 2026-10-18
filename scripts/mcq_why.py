"""Explanation pass, as APPSC Prep did (tools/mcq_why_guide.md in richie505/Group-app): every kept MCQ gets
an APPSC-style explanation (WHY) and its own technique line (the hint), written topic by topic from the facts
it tests.

A good WHY is 1-3 sentences that stand alone (never "the notes say"): the correct answer and the fact that makes
it right; for statement / match / assertion-reason / "NOT" questions, which item is false or what the trap is
and why (for A-R: whether R explains A); where it helps, why the tempting wrong option is wrong.
The technique line says how to get there: "Discriminate between ...", "Recall the year ...", "Check each pair ...".

    python scripts/mcq_why.py export indian-polity 5      # the topic's kept MCQs: stem, options, key, WHY, facts
    python scripts/mcq_why.py save indian-polity 5 < w.txt
    python scripts/mcq_why.py check indian-polity [5]     # every kept MCQ written, lengths and wording sane
    python scripts/mcq_why.py stats

w.txt, one block per MCQ (labels as in the export; "a N" only when the key itself was wrong, 0-3 = A-D;
"s ..." / "o A | B | C | D" rewrite an ambiguous or broken question so exactly one option is right, with "a N"):
    @45-3
    x Lothal is known as Mini Harappa, not the City of the Dead. Mohenjodaro means Mound of the Dead ...
    t Find the wrongly matched local name among closely related Harappan terms.
Written to data/mcq_why/<subject>/NN.jsonl: {"q": "45-3", "x": "...", "t": "...", ["a": 2]}; build_prep_assets.py
uses them in place of the sheet MCQs' own explanation and "format · angle" line.
"""

import json
import re
import sys

import mcq_trim
from common import DATA_DIR, load_json

WHY_DIR = DATA_DIR / "mcq_why"
AMBIG = re.compile(r"doubtful|pairs (items|them) in order|both fit|two options fit|no option (is|fits)", re.I)
BANNED = re.compile(r"\b(the|these|your) notes?\b|\bas (stated|listed|given) in\b|\bthe sheet\b", re.I)


def kept(slug: str, ti: int) -> list:
    _, _, items = mcq_trim.topic_mcqs(slug, ti)
    k, _, _ = mcq_trim.apply(slug, ti, items, mcq_trim.load_decisions(slug, ti))
    return k


def load(slug: str, ti: int) -> dict:
    p = WHY_DIR / slug / f"{ti:02d}.jsonl"
    if not p.exists():
        return {}
    return {d["q"]: d for d in (json.loads(l) for l in p.read_text(encoding="utf-8").splitlines() if l.strip())}


def export(slug: str, ti: int) -> None:
    topic = load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"][ti]
    items = kept(slug, ti)
    print(f"=== TOPIC {slug}:{ti} | {topic['title']} | {len(items)} MCQs")
    for lab, _, q, _, fs in items:
        s = re.sub(r"\s+", " ", q["question"]).strip()
        if q.get("format") in ("Assertion-Reason", "Statement-Based (A/B)"):  # the usual four options: the key only
            opts = f"KEY: {q['options'][q['answer']]}"
        else:
            opts = " ".join(f"{'ABCD'[i]}){'*' if i == q['answer'] else ''} {re.sub(chr(10), ' ', o)}"
                            for i, o in enumerate(q["options"]))
        print(f"@{lab} [{q.get('format', '')}] {s}\n  {opts}\n  WHY: {q.get('explanation', '')}\n  FACTS: {' ; '.join(fs)[:400]}")


def audit(slug: str, ti: int) -> None:
    """The topic as the app shows it now (rewrites, keys and new WHY applied), all four options always listed."""
    topic = load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"][ti]
    items, why = kept(slug, ti), load(slug, ti)
    print(f"=== AUDIT {slug}:{ti} | {topic['title']} | {len(items)} MCQs")
    for lab, _, q, _, fs in items:
        w = why.get(lab, {})
        stem = re.sub(r"\s+", " ", w.get("s", q["question"])).strip()
        o, a = w.get("o", q["options"]), w.get("a", q["answer"])
        opts = " ".join(f"{'ABCD'[i]}){'*' if i == a else ''} {re.sub(chr(10), ' ', x)}" for i, x in enumerate(o))
        print(f"@{lab} [{q.get('format', '')}] {stem}\n  {opts}\n  WHY: {w.get('x', '')}\n  FACTS: {' ; '.join(fs)[:300]}")


def parse(text: str) -> list[dict]:
    out, cur = [], None
    for line in text.splitlines():
        line = line.rstrip()
        if line.startswith("@"):
            cur = {"q": line[1:].split()[0]}
            out.append(cur)
        elif cur is not None and line[:2] in ("x ", "t "):
            cur[line[0]] = line[2:].strip()
        elif cur is not None and line.startswith("a "):
            cur["a"] = int(line[2:].strip())
        elif cur is not None and line.startswith("s "):  # rewritten question; "\\n" marks a line break
            cur["s"] = line[2:].strip().replace("\\n", "\n")
        elif cur is not None and line.startswith("o "):  # rewritten options, separated by " | "
            cur["o"] = [o.strip() for o in line[2:].split(" | ")]
        elif cur is not None and line.strip():
            k = "t" if "t" in cur else "x"  # a wrapped line continues the field above
            cur[k] = cur.get(k, "") + " " + line.strip()
    return out


def check(slug: str, ti: int, verbose: bool = True) -> bool:
    items = {lab: q for lab, _, q, _, _ in kept(slug, ti)}
    got = load(slug, ti)
    probs = []
    for lab in items:
        d = got.get(lab)
        if not d:
            probs.append(f"{lab}: not written")
            continue
        x, t = d.get("x", ""), d.get("t", "")
        n = len(x.split())
        if not 6 <= n <= 90:
            probs.append(f"{lab}: WHY has {n} words")
        if not 3 <= len(t.split()) <= 25:
            probs.append(f"{lab}: technique line has {len(t.split())} words")
        if BANNED.search(x):
            probs.append(f"{lab}: WHY should stand alone: {BANNED.search(x).group(0)!r}")
        if AMBIG.search(x):
            probs.append(f"{lab}: ambiguous item left as is ({AMBIG.search(x).group(0)!r}): rewrite it with s/o/a")
        opts = d.get("o", items[lab]["options"])
        if "o" in d and (len(d["o"]) != len(items[lab]["options"]) or len(set(d["o"])) != len(d["o"])):
            probs.append(f"{lab}: rewritten options must be {len(items[lab]['options'])} distinct options")
        if "a" in d and not 0 <= d["a"] < len(opts):
            probs.append(f"{lab}: key out of range")
        if ("s" in d or "o" in d) and "a" not in d:
            probs.append(f"{lab}: a rewritten question needs its key (a N)")
    for lab in got:
        if lab not in items:
            probs.append(f"{lab}: not a kept MCQ of this topic")
    if verbose:
        print(f"{slug}:{ti}: {len(items)} MCQs, {sum(1 for l in items if l in got)} written, {len(probs)} problems")
        for p in probs[:20]:
            print("  PROBLEM", p)
    return not probs


def main() -> None:
    cmd = sys.argv[1]
    if cmd == "export":
        export(sys.argv[2], int(sys.argv[3]))
    elif cmd == "audit":
        audit(sys.argv[2], int(sys.argv[3]))
    elif cmd == "save":
        slug, ti = sys.argv[2], int(sys.argv[3])
        new = parse(sys.stdin.read())
        old = load(slug, ti)
        for d in new:
            old[d["q"]] = {**old.get(d["q"], {}), **d}
        p = WHY_DIR / slug / f"{ti:02d}.jsonl"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("".join(json.dumps(d, ensure_ascii=False) + "\n" for d in old.values()), encoding="utf-8")
        check(slug, ti)
    elif cmd == "check":
        slug = sys.argv[2]
        tis = [int(sys.argv[3])] if len(sys.argv) > 3 else range(len(load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"]))
        bad = [ti for ti in tis if not check(slug, ti)]
        print("all pass" if not bad else f"failing: {bad}")
    elif cmd == "stats":
        tot = done = 0
        for p in sorted((DATA_DIR / "topics").glob("*.json")):
            a = b = 0
            for ti, _ in enumerate(load_json(p)["topics"]):
                labs = {lab for lab, *_ in kept(p.stem, ti)}
                a += len(labs)
                b += len(labs & set(load(p.stem, ti)))
            print(f"{p.stem}: {b}/{a}")
            tot, done = tot + a, done + b
        print(f"total: {done}/{tot}")


if __name__ == "__main__":
    main()
