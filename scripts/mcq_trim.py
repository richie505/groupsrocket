"""MCQ trim: one strong MCQ per fact in each topic, repeats dropped, closely linked facts merged
(as APPSC Prep did with tools/mcq_trim_*.py and tools/mcq_cut_*.py in richie505/Group-app).

Merging the ROCKET sheets into topics put sheets that repeat each other side by side, and so their MCQs:
the same fact asked two or three times. For each topic, every MCQ is shown under the page it is filed on
(the page that holds its facts), with the facts it tests; MCQs that look alike are listed next to each other.
The decisions go in data/mcq_trim/<subject>/NN.jsonl, one JSON object per line; an MCQ not named is kept:

    {"q":"45-3","d":"dup","of":"45-1"}     drop: tests the same fact as 45-1 (kept, or part of a merge)
    {"q":"45-9","d":"trivia"}              drop: a detail the exam does not ask
    {"q":"45-12","d":"off"}                drop: wrong key, or not what the notes say
    {"q":"46-2","move":3}                  keep it, filed under page 3 of the topic
    {"merge":["45-6","46-7"],"page":1,"s":"Consider the following ...","o":["..","..","..",".."],"a":2,
     "x":"Statement 1 is correct: ... ","tq":"Statement-based"}   one MCQ in place of these

    python scripts/mcq_trim.py export indian-polity 5     # the topic's MCQs by page, to decide
    python scripts/mcq_trim.py check indian-polity 5      # decisions valid, and every fact still tested
    python scripts/mcq_trim.py save indian-polity 5 < d.txt   # short decisions: "A B" (A repeats B), "A t", "A off", "A >3"
    python scripts/mcq_trim.py list indian-polity         # topics: MCQs, kept, done marks
    python scripts/mcq_trim.py stats                      # totals over all subjects
"""

import json
import re
import sys
from collections import Counter

import topic_check
from common import DATA_DIR, MCQS_DIR, load_json

TRIM_DIR = DATA_DIR / "mcq_trim"
FACTS = "--facts" in sys.argv  # export: also print the facts each MCQ tests
FULL = "--full" in sys.argv  # export: print every MCQ in full (else MCQs alone on their facts are shown short)
STOP = topic_check.STOP


def label(q: dict) -> str:
    """indian-polity-45-3 -> 45-3 (sheet-number)."""
    m = re.search(r"-(\d+[a-z]?(?:-\d+)?)-(\d+)$", q["id"])
    return f"{m.group(1)}-{m.group(2)}" if m else q["id"]


def topic_mcqs(slug: str, ti: int):
    """(topic, page titles, [(label, sid, mcq, page, [fact texts])]) in topic order."""
    topic = load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"][ti]
    recs = load_json(MCQS_DIR / f"{slug}.json")["sheets"]
    facts = load_json(DATA_DIR / "facts" / f"{slug}.json")["sheets"]
    md = (DATA_DIR / "structured" / slug / f"{ti:02d}.md").read_text(encoding="utf-8")
    _, pages = topic_check.parse(md)
    ok, placed = topic_check.check(slug, ti, verbose=False)
    first = {}
    for i, (_, frm, _) in enumerate(pages):
        for sid in frm:
            first.setdefault(sid, i)
    out = []
    for sid in topic["sheets"]:
        ftext = {f["id"]: f["text"] for f in facts.get(sid, [])}
        for q in recs.get(sid, {}).get("mcqs", []):
            at = [placed[f"{sid}:{f}"] for f in q.get("facts", []) if f"{sid}:{f}" in placed]
            page = max(set(at), key=at.count) if at else first.get(sid, 0)
            out.append((label(q), sid, q, page, [ftext[f] for f in q.get("facts", []) if f in ftext]))
    return topic, [t for t, _, _ in pages], out


def words(text: str) -> set[str]:
    """Stemmed content words and numbers (Forty-Second = 42nd, Rs 1,000 = 1000), as topic_check.py reads them."""
    return (topic_check.words(text) | set(re.findall(r"\d+(?:st|nd|rd|th)?", topic_check.norm(text)))
            | topic_check.numbers(text))


def norm(text: str) -> str:
    return " ".join(sorted(words(text)))


def chain(items: list) -> list:
    """Orders MCQs so that ones testing alike facts sit next to each other (greedy nearest neighbour)."""
    sig = [words(" ".join(fs) + " " + q["options"][q["answer"]]) for _, _, q, _, fs in items]
    left = list(range(len(items)))
    order = []
    while left:
        cur = left.pop(0)
        order.append(cur)
        while left:
            best = max(left, key=lambda j: len(sig[cur] & sig[j]) / (len(sig[cur] | sig[j]) or 1))
            if len(sig[cur] & sig[best]) / (len(sig[cur] | sig[best]) or 1) < 0.25:
                break
            left.remove(best)
            order.append(best)
            cur = best
    return [items[i] for i in order]


def export(slug: str, ti: int) -> None:
    topic, titles, items = topic_mcqs(slug, ti)
    print(f"=== TOPIC {slug}:{ti} | {topic['title']} | {len(items)} MCQs")
    print("(⊆ X: every fact it tests is also tested by X; R: every fact it tests is tested by other MCQs; "
          "no mark: it alone tests a fact)")
    # facts said alike in two sheets (same numbers, most words shared) count as one fact
    canon, reps = {}, []
    for _, _, _, _, fs in items:
        for f in fs:
            k = norm(f)
            if k in canon:
                continue
            w = set(k.split())
            n = {x for x in w if x[:1].isdigit()}
            hit = next((i for i, (rw, rn) in enumerate(reps) if rn == n and len(w & rw) >= 0.75 * max(len(w), len(rw))), None)
            if hit is None:
                reps.append((w, n))
                hit = len(reps) - 1
            canon[k] = hit
    fsets = {lab: {canon[norm(f)] for f in fs} for lab, _, _, _, fs in items}
    count = Counter(k for v in fsets.values() for k in v)
    hint = {}
    for lab, v in fsets.items():
        sup = [o for o, w in fsets.items() if o != lab and v and v <= w]
        if sup:
            hint[lab] = "⊆ " + min(sup, key=lambda o: len(fsets[o]))
        elif v and all(count[k] > 1 for k in v):
            hint[lab] = "R"
    # MCQs that share a fact with a flagged one: shown in full, as the ones to keep in its place
    flagged = {k for lab in hint for k in fsets[lab]}
    related = {lab for lab, v in fsets.items() if v & flagged}
    for p, title in enumerate(titles):
        here = [x for x in items if x[3] == p]
        print(f"\n## PAGE {p} | {title} | {len(here)} MCQs")
        last = None
        for lab, sid, q, _, fs in chain(here):
            s = re.sub(r"\s+", " ", q["question"]).strip()
            opts = " ".join(f"{'abcd'[i]}){'*' if i == q['answer'] else ''} {o}" for i, o in enumerate(q["options"]))
            key = norm(" ".join(fs))
            last = key
            h = f" ({hint[lab]})" if lab in hint else ""
            if lab not in hint and lab not in related and not FULL:
                print(f"[{lab}] {s[:110]}{'…' if len(s) > 110 else ''}")  # alone tests a fact: shown short
                continue
            print(f"[{lab}]{h} {s} | {opts}")
            if FACTS:
                print(f"   = {' ; '.join(fs)}")


def load_decisions(slug: str, ti: int) -> list[dict]:
    p = TRIM_DIR / slug / f"{ti:02d}.jsonl"
    if not p.exists():
        return []
    return [json.loads(line) for line in p.read_text(encoding="utf-8").splitlines() if line.strip()]


def apply(slug: str, ti: int, items: list, decisions: list[dict]):
    """(kept [(label, sid, mcq, page, facts)], merged [(dec, [from items])], problems)."""
    by = {x[0]: x for x in items}
    probs, used = [], Counter()
    drop, move, merges = {}, {}, []
    for d in decisions:
        if "merge" in d:
            src = d["merge"]
            for q in src:
                used[q] += 1
            if len(src) < 2:
                probs.append(f"merge of fewer than 2: {src}")
            if len(d.get("o", [])) != 4 or len(set(o.strip().lower() for o in d.get("o", []))) != 4:
                probs.append(f"merge {src}: needs 4 distinct options")
            if d.get("a") not in (0, 1, 2, 3):
                probs.append(f"merge {src}: answer must be 0-3")
            if not d.get("s") or not d.get("x"):
                probs.append(f"merge {src}: needs s (question) and x (explanation)")
            merges.append(d)
        elif "move" in d:
            used[d["q"]] += 1
            move[d["q"]] = d["move"]
        else:
            used[d["q"]] += 1
            drop[d["q"]] = d
    for q, n in used.items():
        if q not in by:
            probs.append(f"unknown label {q}")
        if n > 1:
            probs.append(f"{q} decided {n} times")
    in_merge = {q for d in merges for q in d["merge"]}
    for q, d in drop.items():
        if d.get("d") not in ("dup", "trivia", "off"):
            probs.append(f"{q}: d must be dup, trivia or off")
        if d.get("d") == "dup":
            of = d.get("of")
            if of not in by:
                probs.append(f"{q}: dup of unknown {of}")
            elif of in drop:
                probs.append(f"{q}: dup of {of}, which is dropped too")
    kept = [(lab, sid, q, move.get(lab, page), fs) for lab, sid, q, page, fs in items if lab not in drop and lab not in in_merge]
    merged = [(d, [by[q] for q in d["merge"] if q in by]) for d in merges]
    return kept, merged, probs


def check(slug: str, ti: int, verbose: bool = True) -> bool:
    topic, titles, items = topic_mcqs(slug, ti)
    decisions = load_decisions(slug, ti)
    kept, merged, probs = apply(slug, ti, items, decisions)
    for d, _ in merged:
        if not 0 <= d.get("page", 0) < len(titles):
            probs.append(f"merge {d['merge']}: page out of range")
    # every fact the topic's MCQs tested is still tested (a fact repeated word for word in another sheet counts once)
    tested_before = {norm(f) for *_, fs in items for f in fs}
    tested_after = {norm(f) for *_, fs in kept for f in fs} | {norm(f) for _, src in merged for *_, fs in src for f in fs}
    lost = sorted(tested_before - tested_after)
    # near-equal wording (same content words but one or two) counts as tested
    after_sets = [set(k.split()) for k in tested_after]
    by = {x[0]: x for x in items}
    drops = {d["q"]: d for d in decisions if "q" in d and "move" not in d}

    def mcq_words(lab):
        _, _, q, _, fs = by[lab]
        return words(" ".join([q["question"], *q["options"], q.get("explanation", ""), *fs]))

    really, trivia = [], []
    for k in lost:
        w = set(k.split())
        if any(len(w ^ a) <= 2 and len(w & a) >= 0.8 * len(w) for a in after_sets):
            continue
        srcs = [lab for lab, _, _, _, fs in items if any(norm(f) == k for f in fs)]
        # dropped as a repeat: the MCQ kept in its place asks the same content (its numbers, most of its words)
        nums = {x for x in w if x.isdigit()}
        if any(drops.get(l, {}).get("d") == "dup" and drops[l].get("of") in by and
               nums <= mcq_words(drops[l]["of"]) and len(w & mcq_words(drops[l]["of"])) >= 0.55 * len(w) for l in srcs):
            continue
        if srcs and all(drops.get(l, {}).get("d") in ("trivia", "off") for l in srcs):
            trivia.append(k)
            continue
        really.append(k)
    texts = {norm(f): f for *_, fs in items for f in fs}
    n_after = len(kept) + len(merged)
    if verbose:
        print(f"{slug}:{ti} {topic['title']}: {len(items)} -> {n_after} MCQs ({100 * n_after // max(1, len(items))}%), "
              f"{len(decisions)} decisions, {len(probs)} problems, {len(really)} facts no longer tested"
              + (f" ({len(trivia)} left untested on purpose: trivia/off)" if trivia else ""))
        for p in probs:
            print("  PROBLEM", p)
        for k in really:
            src = [lab for lab, _, _, _, fs in items if any(norm(f) == k for f in fs)]
            print(f"  UNTESTED {texts[k]}  [was {', '.join(src)}]")
    return not probs and not really


def main() -> None:
    cmd = sys.argv[1]
    if cmd == "export":
        export(sys.argv[2], int(sys.argv[3]))
    elif cmd == "save":
        # short decisions on stdin, one per line: "A B" A repeats B; "A t" trivia; "A off"; "A >3" move to page 3
        slug, ti = sys.argv[2], int(sys.argv[3])
        out = []
        for line in sys.stdin.read().splitlines():
            parts = line.split()
            if not parts:
                continue
            q, what = parts[0], parts[1]
            if what == "t":
                out.append({"q": q, "d": "trivia"})
            elif what == "off":
                out.append({"q": q, "d": "off"})
            elif what.startswith(">"):
                out.append({"q": q, "move": int(what[1:])})
            else:
                out.append({"q": q, "d": "dup", "of": what})
        p = TRIM_DIR / slug / f"{ti:02d}.jsonl"
        p.parent.mkdir(parents=True, exist_ok=True)
        keep = [d for d in load_decisions(slug, ti) if "merge" in d] if "--add" not in sys.argv else load_decisions(slug, ti)
        p.write_text("".join(json.dumps(d, ensure_ascii=False) + "\n" for d in keep + out), encoding="utf-8")
        check(slug, ti)
    elif cmd == "check":
        slug = sys.argv[2]
        tis = [int(sys.argv[3])] if len(sys.argv) > 3 else range(len(load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"]))
        bad = [ti for ti in tis if not check(slug, ti)]
        print("all pass" if not bad else f"failing: {bad}")
    elif cmd in ("list", "stats"):
        slugs = [sys.argv[2]] if cmd == "list" else sorted(p.stem for p in (DATA_DIR / "topics").glob("*.json"))
        tb = ta = 0
        for slug in slugs:
            sb = sa = 0
            for ti, t in enumerate(load_json(DATA_DIR / "topics" / f"{slug}.json")["topics"]):
                _, _, items = topic_mcqs(slug, ti)
                kept, merged, _ = apply(slug, ti, items, load_decisions(slug, ti))
                done = (TRIM_DIR / slug / f"{ti:02d}.jsonl").exists()
                sb += len(items)
                sa += len(kept) + len(merged)
                if cmd == "list":
                    print(f"{ti:3} {len(items):4} -> {len(kept) + len(merged):4}  {'done' if done else '    '}  {t['title']}")
            print(f"{slug}: {sb} -> {sa}")
            tb += sb
            ta += sa
        print(f"total: {tb} -> {ta}")


if __name__ == "__main__":
    main()
