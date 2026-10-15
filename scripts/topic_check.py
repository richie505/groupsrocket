"""Check a rewritten topic (data/structured/<subject>/NN.md) against its sheets' facts.

Every fact must be on one page of the topic: all its numbers, and most of its content words (rare words count
more). Lists the facts it cannot find, so they can be added back; also checks the page format.

    python scripts/topic_check.py indian-polity 5        # one topic
    python scripts/topic_check.py indian-polity          # every rewritten topic of the subject
Also used by build_prep_assets.py to file each fact (and so each MCQ) under the page that holds it.
"""

import math
import re
import sys
from functools import lru_cache

from common import DATA_DIR, load_json

STRUCT_DIR = DATA_DIR / "structured"

STOP = set("""a an the of in on at to for from by with and or but is are was were be been being it its this that these
those as into than then there their they he she his her them which who whom whose what when where how also only not no
any all each both more most other such same so very can could may might will would shall should must has have had do does
did under over after before between about against during up down out off again further once here why own per via known
called also used one first mean means meant seek seeks secure secures refer refers referred include includes
including identify identifies identified describe describes described concern concerns note notable important
enacted enact became become covered associated provided introduced added act acts effective modeled modelled
approximately about held took won contained contains contain complete completed work provide provides providing decided decide specifies specify need versus""".split())
ALIASES = [("united states of america", "usa"), ("united states", "usa"), ("u.s.a.", "usa"), ("united kingdom", "uk"),
           ("great britain", "britain")]
NUM_WORDS = {w: str(i) for i, w in enumerate(
    "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen "
    "eighteen nineteen twenty".split())}
NUM_WORDS.update({"first": "1st", "second": "2nd", "third": "3rd", "fourth": "4th", "fifth": "5th", "sixth": "6th",
                  "seventh": "7th", "eighth": "8th", "ninth": "9th", "tenth": "10th", "eleventh": "11th", "twelfth": "12th"})


UNITS = {w: i for i, w in enumerate("zero one two three four five six seven eight nine".split())}
UNITS_ORD = {"first": 1, "second": 2, "third": 3, "fourth": 4, "fifth": 5, "sixth": 6, "seventh": 7, "eighth": 8, "ninth": 9}
TEENS = {w: 10 + i for i, w in enumerate("ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split())}
TEENS_ORD = {"tenth": 10, "eleventh": 11, "twelfth": 12, "thirteenth": 13, "fourteenth": 14, "fifteenth": 15,
             "sixteenth": 16, "seventeenth": 17, "eighteenth": 18, "nineteenth": 19}
TENS = {w: 20 + 10 * i for i, w in enumerate("twenty thirty forty fifty sixty seventy eighty ninety".split())}
TENS_ORD = {w[:-1] + "ieth": n for w, n in TENS.items()}
SPELLED = re.compile(
    r"\b(?:(one|two|three) hundred(?: and)? )?(?:(" + "|".join(TENS) + r")[- ]("
    + "|".join(list(UNITS_ORD) + list(UNITS)[1:]) + r")|(" + "|".join(list(TENS_ORD) + list(TEENS_ORD) + list(TENS) + list(TEENS))
    + r")|(" + "|".join(list(UNITS_ORD) + list(UNITS)[1:]) + r"))\b")
SUFFIX = lambda n: "th" if 10 <= n % 100 <= 20 else {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")  # noqa: E731


def _spelled(m: re.Match) -> str:
    hundreds = UNITS.get(m.group(1) or "zero", 0) * 100
    ordinal = False
    if m.group(2):
        last = m.group(3)
        ordinal = last in UNITS_ORD
        n = TENS[m.group(2)] + (UNITS_ORD.get(last) or UNITS.get(last, 0))
    elif m.group(4):
        w = m.group(4)
        ordinal = w in TENS_ORD or w in TEENS_ORD
        n = TENS_ORD.get(w) or TEENS_ORD.get(w) or TENS.get(w) or TEENS.get(w)
    else:
        w = m.group(5)
        if not m.group(1):
            return w  # a lone "first", "two" ... stays a word (handled by NUM_WORDS)
        ordinal = w in UNITS_ORD
        n = UNITS_ORD.get(w) or UNITS.get(w, 0)
    n += hundreds
    return f"{n}{SUFFIX(n)}" if ordinal else str(n)


def norm(text: str) -> str:
    t = text.lower().replace("’", "'").replace("–", "-").replace("—", "-").replace("**", "")
    t = SPELLED.sub(_spelled, t)
    for a, b in ALIASES:
        t = t.replace(a, b)
    t = re.sub(r"(?<=\d),(?=\d{2,3}\b)", "", t)  # 1,000 -> 1000; 8,67,915 -> 867915
    return t


def numbers(text: str) -> set[str]:
    t = norm(text)
    out = set(re.findall(r"\d+(?:\.\d+)?", t))
    for w, d in NUM_WORDS.items():
        if re.search(rf"\b{w}\b", t):
            out.add(re.sub(r"\D", "", d) or d)
    return out


def stem(w: str) -> str:
    """First 6 letters: sovereignty ~ sovereign, secularism ~ secular, democracy ~ democratic."""
    for suf in ("ies", "es", "s"):
        if w.endswith(suf) and len(w) > len(suf) + 3:
            w = w[: -len(suf)] + ("y" if suf == "ies" else "")
            break
    return w[:6]


def words(text: str) -> set[str]:
    ws = (re.sub(r"'s$", "", w).strip("'") for w in re.findall(r"[a-z][a-z']*[a-z]|[a-z]", norm(text)))
    return {stem(w) for w in ws if w not in STOP and len(w) > 2}


def parse(md: str):
    """-> (topic header, [(page title, from ids, content lines)])"""
    lines = md.splitlines()
    head, pages = None, []
    for line in lines:
        if line.startswith("=== TOPIC "):
            head = line
        elif line.startswith("## "):
            m = re.match(r"## (.+?)\s*\|\s*from:\s*(.+)$", line)
            if not m:
                raise ValueError(f"bad page line: {line}")
            pages.append((m.group(1).strip(), [x.strip().lstrip("#") for x in m.group(2).split(",") if x.strip()], []))
        elif pages:
            pages[-1][2].append(line)
    return head, pages


FORMAT = re.compile(r"^(# \d+\. .+|- .+|  - .+|> .+|\|.*\||\*\*.+\*\*|\s*)$")


def format_problems(pages) -> list[str]:
    out = []
    for title, _, body in pages:
        for line in body:
            if not FORMAT.match(line):
                out.append(f"[{title}] odd line: {line[:90]}")
        rows = [l for l in body if l.startswith("|")]
        groups, cur = [], []
        for l in body + [""]:
            if l.startswith("|"):
                cur.append(l)
            elif cur:
                groups.append(cur)
                cur = []
        for g in groups:
            n = {len(r.strip().strip("|").split("|")) for r in g}
            if len(n) > 1:
                out.append(f"[{title}] table rows with different cell counts: {g[0][:60]}")
        del rows
    return out


@lru_cache(maxsize=None)
def idf(slug: str) -> dict:
    facts = load_json(DATA_DIR / "facts" / f"{slug}.json")["sheets"]
    docs = [words(f["text"]) for fl in facts.values() for f in fl]
    df = {}
    for d in docs:
        for w in d:
            df[w] = df.get(w, 0) + 1
    return {w: math.log(len(docs) / c) for w, c in df.items()}


def locate(slug: str, fact: str, page_words: list[set], page_nums: list[set]) -> tuple[int, float, set]:
    """Best page for a fact: (page index, share of the fact's weighted words found there, missing words)."""
    w, nums, weights = words(fact), numbers(fact), idf(slug)
    total = sum(weights.get(x, 1.0) for x in w) or 1.0
    best = (-1, 0.0, w)
    for i, (pw, pn) in enumerate(zip(page_words, page_nums)):
        if not nums <= pn:
            continue
        score = sum(weights.get(x, 1.0) for x in w & pw) / total
        if score > best[1]:
            best = (i, score, w - pw)
    return best


PASS = 0.6


def check(slug: str, ti: int, verbose: bool = True) -> tuple[bool, dict]:
    from topic_export import topic_sheets

    topic, sheets = topic_sheets(slug, ti)
    md = (STRUCT_DIR / slug / f"{ti:02d}.md").read_text(encoding="utf-8")
    head, pages = parse(md)
    problems = format_problems(pages)
    if not head or not head.startswith(f"=== TOPIC {slug}:{ti} |"):
        problems.append(f"header should start with '=== TOPIC {slug}:{ti} |'")
    ids = {sid for sid, *_ in sheets}
    used = {x for _, fr, _ in pages for x in fr}
    if ids - used:
        problems.append(f"sheets in no 'from:': {sorted(ids - used)}")
    if used - ids:
        problems.append(f"unknown sheets in 'from:': {sorted(used - ids)}")
    page_text = ["\n".join([t] + body) for t, _, body in pages]
    page_words = [words(t) for t in page_text]
    page_nums = [numbers(t) for t in page_text]
    placed, missing = {}, []
    for sid, _, _, fl in sheets:
        for f in fl:
            i, score, miss = locate(slug, f["text"], page_words, page_nums)
            if i < 0 or score < PASS:
                missing.append((sid, f, i, score, miss))
            else:
                placed[f"{sid}:{f['id']}"] = i
    ok = not missing and not problems
    if verbose:
        n = sum(len(fl) for *_, fl in sheets)
        print(f"{slug}:{ti} {topic['title']}: {len(pages)} pages, {n - len(missing)}/{n} facts found"
              + ("" if ok else f", {len(missing)} missing, {len(problems)} format problems"))
        for p in problems[:30]:
            print("  FORMAT", p)
        for sid, f, i, score, miss in missing:
            where = f"best page {i + 1} ({score:.0%}), lacks: {', '.join(sorted(miss)[:8])}" if i >= 0 else "a number is on no page"
            print(f"  MISSING #{sid} {f['id']}: {f['text']}  [{where}]")
    return ok, placed


def main() -> None:
    slug = sys.argv[1]
    if len(sys.argv) > 2:
        ok, _ = check(slug, int(sys.argv[2]))
        raise SystemExit(0 if ok else 1)
    bad = 0
    for p in sorted((STRUCT_DIR / slug).glob("*.md")):
        ok, _ = check(slug, int(p.stem), verbose=False)
        if not ok:
            bad += 1
            print(f"{slug}:{int(p.stem)} has problems")
    print(f"{slug}: {'all pass' if not bad else f'{bad} topics with problems'}")
    raise SystemExit(1 if bad else 0)


if __name__ == "__main__":
    main()
