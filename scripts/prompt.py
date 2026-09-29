"""MCQ-generation prompt, adapted from the appsc-group2-tutor skill's MCQ module
(secondary mode: ground strictly in the pasted reference text)."""

import re

from common import PROMPTS_DIR

FORMATS = [
    "Direct Recall",
    "Negative / Incorrect Statement",
    "Assertion-Reason",
    "Statement-Based (A/B)",
    "Multi-Statement Correctness",
    "Chronological Ordering",
    "List-Matching",
    "Count-Based",
]

# Formats whose options have a natural order that must not be shuffled.
FIXED_OPTION_FORMATS = {"Assertion-Reason", "Statement-Based (A/B)", "Count-Based"}

AR_OPTIONS = [
    "Both A and R are true, and R is the correct explanation of A",
    "Both A and R are true, but R is NOT the correct explanation of A",
    "A is true, but R is false",
    "A is false, but R is true",
]
AB_OPTIONS = [
    "Both Statement A and Statement B are true",
    "Both Statement A and Statement B are false",
    "Statement A is true, Statement B is false",
    "Statement A is false, Statement B is true",
]


def _blueprint_sections(names: list[str]) -> str:
    text = (PROMPTS_DIR / "blueprint-keywords.md").read_text(encoding="utf-8")
    sections = re.split(r"^## ", text, flags=re.MULTILINE)[1:]
    picked = []
    for sec in sections:
        title, _, body = sec.partition("\n")
        if title.strip() in names:
            picked.append(f"### {title.strip()}\n{body.strip()}")
    return "\n\n".join(picked)


SYSTEM_PROMPT = """You are an APPSC Group 1 / Group 2 exam MCQ setter.
You turn one ROCKET SHEET of revision notes into exam-aligned practice MCQs.

## Core directives
- Ground every question, option and explanation STRICTLY in the provided notes text. Do not mix in outside facts. If a fact is not in the notes, do not ask about it.
- Accuracy over invention: never invent dates, names, numbers or places to fill gaps. If the notes are too thin for the requested count, return fewer (high-quality) questions rather than padding.
- The notes were extracted from PDF tables, so rows may be run together on one line and the ligature "ti" is sometimes dropped (e.g. "Composi on" = "Composition", "Quan ty" = "Quantity"). Read tables row-by-row carefully and never build a question on an ambiguous row.
- No PYQs: write fresh questions from the notes only.

## The 8 official MCQ formats (use a MIX — do not default to Direct Recall)
{style_guide}

## Format mix
- Favour Assertion-Reason, List-Matching and Negative/Incorrect-Statement — they dominate the real paper over plain Direct Recall.
- Roughly: 25% Direct Recall, 15% Negative/Incorrect, 15% Assertion-Reason, 15% List-Matching, 10% Multi-Statement, 10% Statement-Based (A/B), 5% Chronological, 5% Count-Based.
- Use Chronological Ordering only if the notes give dates/sequence for every item you order. Otherwise use a different format.

## How to write each format in the JSON
- "question" holds the full stem, including numbered statements / lists on separate lines ("\\n").
- Assertion-Reason: question = "Assertion (A): ...\\nReason (R): ..." and options EXACTLY, in this order:
  {ar_options}
- Statement-Based (A/B): question = "Consider the following statements:\\nStatement A: ...\\nStatement B: ..." and options EXACTLY, in this order:
  {ab_options}
- Multi-Statement Correctness: numbered I, II, III (IV) statements; options are combinations like "I and II only".
- List-Matching: "List-I" items labelled a, b, c, d and "List-II" items labelled i, ii, iii, iv on separate lines; options are codes like "a-iii, b-iv, c-ii, d-i". Exactly one code must be fully correct.
- Chronological Ordering: items labelled A-D; options are orders like "B, A, D, C".
- Count-Based: "How many of the following ... ?" with a short numbered list; options like "Only one", "Only two", "Only three", "All four".
- Negative / Incorrect Statement: four statements as options; exactly one is false, made by altering exactly ONE detail (date, place, name, number) of a true fact from the notes.

## Distractor quality
Wrong options must be genuinely close: an adjacent year, a related-but-wrong person, a neighbouring district/state, an adjacent article number, a similarly-named committee/act — ideally other entries from the same notes table. Never obviously absurd options. Never "All of the above"/"None of the above".

## Other fields
- "keyword": the blueprint question angle this MCQ tests (pick from the keyword list below, e.g. "FIRST", "Founder/founded", "Articles – NUMBER GAME", "Dam/Reservoir → Location").
- "answer_index": 0-based index of the correct option.
- "explanation": 1-3 sentences grounded in the notes: why the answer is right; for Negative/Incorrect questions state exactly which detail was altered and its correct value; for List-Matching give the correct pairs.
- Every question must have exactly one defensible correct answer. Do not repeat the same fact across questions.
- Cover the whole sheet — spread questions across all its tables/sections, not just the first few rows.

## Syllabus-tracker mapping
Also map this sheet to the APPSC combined syllabus tracker: pick the unit ids (from the candidate lists given) whose syllabus text this sheet's content MAINLY belongs to, ordered most relevant first — usually 1, at most 2-3 per exam (only list a unit if a substantial part of the sheet is about it). Use an empty list only if nothing fits.

## Blueprint keyword angles for this subject
{keywords}
"""

USER_PROMPT = """Subject: {subject}
Sheet: ROCKET SHEET #{sheet_id}

Candidate Group-1 tracker units:
{g1_units}

Candidate Group-2 tracker units:
{g2_units}

Write {count} MCQs from the notes below, plus a short clean "title" (max 8 words) naming what this sheet covers.

=== NOTES: {subject} – ROCKET SHEET #{sheet_id} ===
{text}
=== END NOTES ===
"""


def build_system_prompt(blueprint_sections: list[str]) -> str:
    style_guide = (PROMPTS_DIR / "mcq-style-guide.md").read_text(encoding="utf-8")
    style_guide = style_guide.split("## 1.", 1)[1]
    style_guide = "## 1." + style_guide.split("## Notes for generating", 1)[0]
    style_guide = style_guide.replace("## ", "### ")
    return SYSTEM_PROMPT.format(
        style_guide=style_guide.strip(),
        ar_options=" | ".join(AR_OPTIONS),
        ab_options=" | ".join(AB_OPTIONS),
        keywords=_blueprint_sections(blueprint_sections),
    )


def build_user_prompt(subject: str, sheet: dict, count: int, units_by_id: dict, g1: list, g2: list) -> str:
    def fmt(ids):
        return "\n".join(f"- {i}: {units_by_id[i]['title']}" for i in ids) or "- (none)"

    return USER_PROMPT.format(
        subject=subject,
        sheet_id=sheet["id"],
        g1_units=fmt(g1),
        g2_units=fmt(g2),
        count=count,
        text=sheet["text"],
    )


def response_schema(g1: list, g2: list) -> dict:
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["title", "tracker_g1", "tracker_g2", "mcqs"],
        "properties": {
            "title": {"type": "string"},
            "tracker_g1": {"type": "array", "items": {"type": "string", "enum": g1 or ["NONE"]}},
            "tracker_g2": {"type": "array", "items": {"type": "string", "enum": g2 or ["NONE"]}},
            "mcqs": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["format", "keyword", "question", "options", "answer_index", "explanation"],
                    "properties": {
                        "format": {"type": "string", "enum": FORMATS},
                        "keyword": {"type": "string"},
                        "question": {"type": "string"},
                        "options": {"type": "array", "items": {"type": "string"}},
                        "answer_index": {"type": "integer"},
                        "explanation": {"type": "string"},
                    },
                },
            },
        },
    }
