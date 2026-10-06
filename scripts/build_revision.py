"""Rocket Revision: revision sheets built from the app's MCQs - the facts the questions actually test.

Same method as APPSC Prep's APPSC Revision (tools/build_revision_mcq.py in richie505/Group-app).
Every MCQ's explanation states the fact behind its answer, and every MCQ lists the sheet facts it tests
(cover_facts.py). For each ROCKET SHEET the revision page is one fact per question, in the MCQs' order,
with the answer in bold:
  * the explanation's fact sentence when it reads on its own; when it talks about the question
    ("Both statements are true", "The incorrect detail is ...") the first sheet fact the MCQ tests;
  * filler is dropped: "Statements 1 and 2 are correct.", "Hence option (b).", option letters ("sequence B, D, A, C");
  * "Statement 3 is false because X" becomes "X";
  * a fact already given on the page (most of its rare words, all its numbers) is not repeated;
  * a sheet whose MCQs give no usable fact keeps its Key facts.
Sheets and their order are the notes app's, so the 90-day plan, MCQ practice and progress line up.
Each sheet has one page, "Revision points (N)", so the revision app ships its own index.json.

    python scripts/build_revision.py        # android/app/src/main/assets -> android/app/src/revise/assets
"""

import json
import math
import re
from pathlib import Path

from common import ROOT

SRC = ROOT / "android" / "app" / "src" / "main" / "assets"
OUT = ROOT / "android" / "app" / "src" / "revise" / "assets"

STOP = set("""a an the of in on at to for from by with and or but is are was were be been being it its this that these
those as into than then there their they he she his her them which who whom whose what when where how also only not no
any all each both more most other such same so very can could may might will would shall should must has have had
do does did under over after before between about against during up down out off again further once here why own
s same per via following correct incorrect given statements statement above below consider which true false regard
reference pairs pair matched match select code codes options option one two three four none answer asked""".split())

FILLER = re.compile(
    r"^(?:hence|thus|therefore|so)?,?\s*(?:the\s+)?(?:correct\s+)?(?:answer|option|choice)\b.*$"
    r"|^(?:only\s+)?statements?\s+[\d,\sand]+\s+(?:is|are)\s+(?:both\s+)?(?:correct|incorrect|true|false|wrong|right)\.?$"
    r"|^(?:all|none|both|neither)\b[^.]{0,40}\b(?:statements?|pairs?|options?)\b[^.]*(?:correct|incorrect|true|false)\.?$"
    r"|^(?:pairs?|options?)\s+[\d,\s(a-d)and]+\s+(?:is|are)\s+(?:correctly|incorrectly|wrongly)?\s*(?:matched|correct|incorrect)\.?$"
    r"|^the (?:other|remaining) (?:statements?|options?|pairs?) (?:is|are) (?:correct|incorrect|true|false|wrong)\.?$",
    re.I,
)
REASON = re.compile(
    r"^(?:statements?|options?|pairs?|assertion|reason|choices?)\s*\(?[\da-dIVXA-B]+\)?"
    r"(?:\s*(?:,|and)\s*\(?[\da-dIVX]+\)?)*\s+(?:is\s+|are\s+)?(?:both\s+)?(?:also\s+)?"
    r"(?:correctly matched|wrongly matched|incorrectly matched|not correct|incorrect|correct|true|false|wrong|right)"
    r"\s*(?:because|as|since|:|-|,)?\s*",
    re.I,
)
SENT = re.compile(r"(?<!\bArt)(?<!\bNo)(?<!\bvs)(?<!\bv)(?<!\bSec)(?<!\bDr)(?<!\bSt)(?<!\bc)(?<!\b[A-Z])\.\s+(?=[A-Z0-9\"“(])")
META = re.compile(
    r"\b(?:notes?|statements?|assertion|reason|pairs?|options?|sequence|chronolog\w*|incorrect|correct(?:ly)?|"
    r"the (?:former|latter)|both (?:are|were)|explains?|explanation|detail|listed|given|mentioned|stated|"
    r"question|answer|odd one|exception|neither|none of)\b", re.I)
SENTENCES = 1  # fact sentences kept per question: the first one states the fact, the rest elaborate
IDF: dict[str, float] = {}


def content(text: str) -> set[str]:
    """Content words and numbers of a piece of text (lower case, no stop words)."""
    words = re.findall(r"[a-z0-9][a-z0-9'-]*", text.lower().replace("’", "'"))
    return {w.removesuffix("'s").strip("'-") for w in words if w not in STOP and len(w) > 1} - {""}


def facts_of(q: dict) -> list[str]:
    """The fact sentences of one MCQ's explanation, filler out, reasons turned into facts."""
    out = []
    for s in SENT.split(q.get("x", "").replace("\n", " ").strip()):
        s = s.strip().rstrip(".").strip()
        if re.match(r"(?:statements?|options?|pairs?)\s+[\da-dIVX,\sand]+\s+(?:correctly|incorrectly|wrongly)\b", s, re.I):
            continue
        s = re.sub(r"[;,]\s*(?:but\s+|while\s+)?(?:statements?|options?|pairs?)\s+\(?[\da-dIVX]+\)?(?:\s*(?:,|and)\s*\(?[\da-dIVX]+\)?)*"
                   r"\s+(?:is|are)\s+(?:also\s+)?(?:not\s+)?(?:correctly matched|wrongly matched|incorrectly matched|incorrect|correct|true|false|wrong)"
                   r"\s*(?:because|as|since|:|-)?\s*", "; ", s, flags=re.I)
        s = re.sub(r"[;,]?\s*(?:and\s+)?(?:(?:therefore|hence|so|thus)\s*[;,]?\s*)+(?:and\s+)?(?:statements?|options?|pairs?)\s+[\da-dIVX,\sand]+\s+(?:is|are)\s+(?:not\s+)?\w+$", "", s, flags=re.I)
        s = re.sub(r",?\s*(?:giving|so|hence|i\.e\.|which gives)?\s*(?:the\s+)?(?:correct\s+)?(?:sequence|order|code|answer|arrangement)\s*(?:is\s*)?"
                   r"\(?[A-D1-4]\)?(?:\s*[,–-]\s*\(?[A-D1-4]\)?)+\s*$", "", s, flags=re.I)
        lead = re.match(r"^(?:the|all)\s+(?:listed|given|above|following|four|three|other)\s+[\w\s-]{0,30}?(?:is|are)\s+(?:all\s+)?"
                        r"(?:correct(?:ly matched)?|true|right)\s*[:;,-]\s*", s, re.I)
        if lead:
            s = s[lead.end():]
            s = s[:1].upper() + s[1:]
        m = REASON.match(s)
        if m:
            s = s[m.end():].strip()
            if not s:
                continue
            s = s[0].upper() + s[1:]
        if not s or FILLER.match(s + "."):
            continue
        if re.search(r"\b(?:listed|given (?:statements?|pairs?|options?|list)|the above|list[- ]?I{1,2}\b|option \(?[a-d]\)|\([a-d]\) and \([a-d]\))", s, re.I) \
                or re.search(r"\b[A-D]\s*[-–]\s*[1-4ivx]+\b(?:\s*,\s*[A-D]\s*[-–]\s*[1-4ivx]+\b)+", s) \
                or re.search(r"\b(?:Statement|Assertion|Reason) [AB12]\b|\b[a-d]-(?:i|ii|iii|iv)\b", s):
            continue
        if len(s.split()) < 4:
            continue
        out.append(s + ".")
        if len(out) == SENTENCES:
            break
    return out


def tested_facts(q: dict) -> list[str]:
    """The sheet facts the MCQ tests (cover_facts.py), source tag off."""
    return [re.sub(r"\s*\[[^\]]*ROCKET SHEET[^\]]*\]\s*$", "", f).strip() for f in q.get("n", [])]


def points_of(q: dict) -> list[str]:
    """One fact per question: its explanation's fact if it reads on its own, else the first fact it tests."""
    clean = [f for f in facts_of(q) if not META.search(f)]
    return clean + tested_facts(q)


def answer_of(q: dict) -> str:
    a, opts = q.get("a", -1), q.get("o", [])
    t = (opts[a] if 0 <= a < len(opts) else "").strip().rstrip(".")
    if re.fullmatch(r"[\divxIVX,\sand]+(?:only)?|only|both.*|neither.*|all.*|none.*|[a-d]\)?.*only|[A-D](?:,\s*[A-D])+|.*\b[a-d]-(?:i|ii|iii|iv)\b.*", t, re.I) \
            or len(t) < 3:
        return ""
    return t


def bold_answer(sentence: str, ans: str) -> list:
    """Runs of the sentence with the answer (if it is in there) in bold."""
    if ans:
        i = sentence.lower().find(ans.lower())
        if i >= 0:
            return [r for r in ([sentence[:i], 0], [sentence[i:i + len(ans)], 1], [sentence[i + len(ans):], 0]) if r[0]]
    return [[sentence, 0]]


class Said:
    """What a page has said: a fact whose distinctive words (weighted by rarity) were mostly said is a repeat."""

    def __init__(self):
        self.words: set[str] = set()

    def repeats(self, text: str) -> bool:
        w = content(text)
        if len(w) < 3:
            return False
        nums = {x for x in w if re.fullmatch(r"\d[\d.,-]*", x)}
        if not nums <= self.words:
            return False
        weight = sum(IDF.get(x, 1.0) for x in w)
        said = sum(IDF.get(x, 1.0) for x in w & self.words)
        return said >= 0.6 * weight

    def add(self, text: str) -> None:
        self.words |= content(text)


def load(name: str):
    return json.loads((SRC / name).read_text(encoding="utf-8"))


def write(name: str, obj) -> None:
    (OUT / name).write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    index = load("index.json")
    ids = [b["id"] for b in index["books"]]
    docs = [content(q.get("x", "")) for b in ids for qs in load(f"mcq{b}.json")["rows"].values() for q in qs]
    df: dict[str, int] = {}
    for d in docs:
        for w in d:
            df[w] = df.get(w, 0) + 1
    IDF.update({w: math.log(len(docs) / c) for w, c in df.items()})

    total_f = total_w = total_notes = fallback = 0
    for b in ids:
        book, mcq = load(f"book{b}.json"), load(f"mcq{b}.json")
        ri = -1
        for unit in book["units"]:
            for row in unit["rows"]:
                ri += 1
                key, text = row["secs"][0], row["secs"][1]
                total_notes += sum(len(x[0].split()) for blk in text["b"] for x in blk["x"])
                source = [blk for blk in key["b"] if blk["k"] == "n"]
                said, points, seen = Said(), [], set()
                for q in mcq["rows"].get(str(ri), []):
                    ans = answer_of(q)
                    for f in points_of(q):  # the first candidate not said yet
                        if said.repeats(f) or f.rstrip(".").lower() in seen:
                            continue
                        said.add(f)
                        seen.add(f.rstrip(".").lower())
                        points.append({"k": "b", "x": bold_answer(f if f.endswith((".", "?", "!", "”", '"', ")")) else f + ".", ans)})
                        break
                if not points:  # nothing usable from the questions: the sheet's key facts
                    points = [blk for blk in key["b"] if blk["k"] == "b"]
                    fallback += 1
                row["secs"] = [{"t": f"Revision points ({len(points)})", "badges": ["REVISION"], "p": key.get("p", row["p1"]),
                                "b": source + points}]
                total_f += len(points)
                total_w += sum(len(x[0].split()) for blk in points for x in blk["x"] if not x[1] & 4)
        write(f"rev{b}.json", book)
    for bk in index["books"]:
        for r in bk["rows"]:
            r["n"] = 1
    write("index.json", index)
    write("edition.json", {"edition": "revision", "name": "Rocket Revision"})
    print(f"{total_f:,} revision points, {total_w:,} words (sheet text {total_notes:,} words); "
          f"{fallback} sheets kept their key facts")


if __name__ == "__main__":
    main()
