#!/usr/bin/env python3
"""Key terms for every notes page (subsection): terms that occur in its text and have a meaning in the app -
the Indian glossary, the notes' own definitions (assets/india.json) or the short forms the notes define
(assets/abbr.json). The reader shows them as chips that open the Meaning card.

Up to 8 per page: glossary terms first, then terms the notes define, then short forms; longer phrases before
the words inside them; each term once per page.

From APPSC Prep (richie505/Group-app tools/build_key_terms.py); india.json and abbr.json are its assets.

Usage: python3 scripts/build_key_terms.py android/app/src/main/assets   (after build_prep_assets.py)
Writes <assets>/keyterms.json: {"book:row:sec": ["term as written on the page", ...]}
"""
import json
import re
import sys
from pathlib import Path

MAX = 6
# source codes used in citations: never key terms
CODES = {"cdi", "cdx", "app", "aphq", "iyb", "appca", "lens", "lensd", "cdca", "vis", "th", "upsc", "appsc", "pyq", "pyqs",
         "es", "ses", "apses", "bud", "pt365", "gk", "uppsc", "bpsc", "mppsc", "rpsc", "jpsc", "tnpsc", "ca", "ncert",
         "pdf", "rocket", "sheet", "sheets"}  # ROCKET source lines
CITATION = re.compile(r"\((?:[^()]*\b(?:CDI|CDX|APP|APHQ|IYB|APPCA|LENS|LENSD|CDCA|VIS|TH|UPSC|APPSC|PYQ|ES|SES|APSES|BUD|"
                      r"UPPSC|BPSC|MPPSC|RPSC|JPSC|GK)\b[^()]*)\)")



# glossary entries that are plain words in the ROCKET facts
NOT_TERMS = {"does not", "did not", "is not", "was not"}

# places are not terms to explain
PLACE_NAMES = {x.strip().lower() for x in """India, Andhra Pradesh, Telangana, Tamil Nadu, Karnataka, Kerala, Odisha, Maharashtra,
Gujarat, Rajasthan, Punjab, Haryana, Bihar, Jharkhand, Chhattisgarh, Madhya Pradesh, Uttar Pradesh, Uttarakhand, Himachal Pradesh,
West Bengal, Assam, Sikkim, Meghalaya, Manipur, Mizoram, Nagaland, Tripura, Arunachal Pradesh, Goa, Delhi, Ladakh, Puducherry,
Lakshadweep, Chandigarh, Jammu and Kashmir, Kashmir, Visakhapatnam, Vijayawada, Guntur, Nellore, Kurnool, Kadapa, Anantapur,
Ananthapuramu, Chittoor, Tirupati, Kakinada, Rajahmundry, Rajamahendravaram, Eluru, Ongole, Srikakulam, Vizianagaram,
Machilipatnam, Amaravati, Hyderabad, Chennai, Mumbai, Kolkata, Bengaluru, Krishna, Godavari, Penna, Pennar, Tungabhadra,
Andhra, Coastal Andhra, Rayalaseema, North Andhra, east coast, west coast""".split(",")}

# notes labels that are not terms: "Older notes", "Six states have Councils", "India's first", "in force"
LABEL_WORDS = {"have", "has", "had", "is", "are", "was", "were", "on", "to", "left", "older", "notes", "note", "order", "first",
               "last", "largest", "smallest", "biggest", "highest", "lowest", "work", "force", "area", "city", "assets",
               "liabilities", "distractors", "distractor", "chronological", "current", "latest", "new", "old", "other", "main",
               "key", "list", "type", "types", "states", "state", "district", "districts", "year", "years", "trap", "traps",
               "example", "examples", "answer", "question", "questions", "pyq", "exam", "angle", "source", "sources", "data",
               "status", "role", "contribution", "contributions", "significance", "features", "reason", "reasons", "result",
               "results", "date", "dates", "period", "place", "name", "names", "count", "number", "share", "rank", "ranking",
               "against", "india", "north", "south", "east", "west", "cross", "border", "in", "nodal"}


def dict_words(assets):
    words = set()
    for f in (assets / "dict").glob("*.tsv"):
        for line in f.read_text(encoding="utf-8").splitlines():
            words.add(line.split("\t", 1)[0].lower())
    return words


def is_everyday(word, words):
    """An ordinary English word or a form of one (women, income, renamed, embassies)."""
    w = word.lower()
    forms = {w, w.rstrip("s"), w[:-2] if w.endswith("es") else w, w[:-3] + "y" if w.endswith("ies") else w,
             w[:-2] if w.endswith("ed") else w, w[:-1] if w.endswith("ed") else w, w[:-3] if w.endswith("ing") else w,
             w[:-3] + "man" if w.endswith("men") else w}
    return any(f in words for f in forms)


def main():
    assets = Path(sys.argv[1])
    india = json.loads((assets / "india.json").read_text())
    abbr = json.loads((assets / "abbr.json").read_text())
    words = dict_words(assets)

    def useful(t, kind):
        k = t.lower()
        if k in CODES or k in PLACE_NAMES or len(k) < 3:
            return False
        if kind == "n" and " " not in k and (is_everyday(k, words) or len(k) < 5):
            return False
        if kind == "n" and (set(re.findall(r"[a-z]+", k)) & LABEL_WORDS or (" " not in k and k in {"against"}) or "'s" in k or "’s" in k or re.search(r"\d", k)):
            return False
        return True

    sources = [("g", {t for t in india["g"] if useful(t, "g")}), ("n", {t for t in india["n"] if useful(t, "n")}),
               ("a", {a for a in abbr if useful(a, "a")})]
    # one big pattern per source, longest first, so "fiscal deficit" wins over "deficit"
    patterns = []
    for kind, terms in sources:
        terms = sorted((t for t in terms if len(t) >= 3), key=len, reverse=True)
        flags = 0 if kind == "a" else re.IGNORECASE
        alt = "|".join(re.escape(t) for t in terms)
        patterns.append((kind, re.compile(rf"(?<![\w-])(?:{alt})(?![\w-])", flags)))
    out = {}
    covered = total = 0
    for b in (bk["id"] for bk in json.loads((assets / "index.json").read_text())["books"]):
        book = json.loads((assets / f"book{b}.json").read_text())
        rows = [r for u in book["units"] for r in u["rows"]]
        for ri, r in enumerate(rows):
            for si, s in enumerate(r["secs"]):
                parts = [s["t"]]
                for blk in s["b"]:
                    cells = [c for row in [blk["h"]] + blk["r"] for c in row] if blk["k"] == "t" else [blk["x"]]
                    for c in cells:
                        runs = c if isinstance(c, list) else [[c, 0]]
                        parts.append("".join(t for t, f in runs if not (f & 4)))  # not the grey notes
                text = CITATION.sub(" ", re.sub(r"\[[^\]]*\]", " ", "\n".join(parts)))
                found, seen = [], set()
                for kind, pat in patterns:
                    for m in pat.finditer(text):
                        k = m.group(0).lower()
                        if k in seen or k in NOT_TERMS or any(k in f.lower() for f in found):
                            continue
                        seen.add(k)
                        found.append(m.group(0))
                        if len(found) >= MAX:
                            break
                    if len(found) >= MAX:
                        break
                total += 1
                if found:
                    covered += 1
                    out[f"{b}:{ri}:{si}"] = found
    (assets / "keyterms.json").write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
    print(f"{covered} of {total} pages have key terms ({100 * covered / total:.1f}%)")


if __name__ == "__main__":
    main()
