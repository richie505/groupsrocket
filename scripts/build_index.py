"""Build data/index.json (what the practice app loads first).

    python scripts/build_index.py
"""

from common import DATA_DIR, MCQS_DIR, SHEETS_DIR, load_json, load_syllabus, save_json, slugify

PLAN_DAYS = 90
PLAN_START = "2026-09-29"  # Day 1 of the 90-day plan


def build_plan(subjects: list[dict]) -> list[list[str]]:
    """Spread every subject's sheets evenly over PLAN_DAYS days, in sheet order."""
    slots = []
    for subj in subjects:
        n = len(subj["sheets"])
        for k, sheet in enumerate(subj["sheets"]):
            slots.append(((k + 0.5) / n, subj["slug"], sheet["id"]))
    slots.sort()
    days = [[] for _ in range(PLAN_DAYS)]
    for i, (_, slug, sheet_id) in enumerate(slots):
        days[i * PLAN_DAYS // len(slots)].append(f"{slug}/{sheet_id}")
    return days


def build_index() -> dict:
    syllabus = load_syllabus()
    subjects, by_unit = [], {u["id"]: [] for u in syllabus["units"]}
    for subj in syllabus["subjects"]:
        slug = slugify(subj["name"])
        notes = load_json(SHEETS_DIR / f"{slug}.json")
        if not notes:
            continue
        generated = load_json(MCQS_DIR / f"{slug}.json", {"sheets": {}})["sheets"]
        sheets = []
        for s in notes["sheets"]:
            g = generated.get(s["id"])
            tracker = g["tracker"] if g else {"g1": [], "g2": []}
            sheets.append(
                {
                    "id": s["id"],
                    "number": s["number"],
                    "title": g["title"] if g else s["heading"][:60],
                    "count": len(g["mcqs"]) if g else 0,
                    "facts": g.get("coverage", {}).get("facts", 0) if g else 0,
                    "covered": g.get("coverage", {}).get("covered", 0) if g else 0,
                    "tracker": tracker,
                }
            )
            for unit in tracker["g1"] + tracker["g2"]:
                by_unit[unit].append(f"{slug}/{s['id']}")
        subjects.append(
            {
                "name": subj["name"],
                "slug": slug,
                "sheets": sheets,
                "total": sum(s["count"] for s in sheets),
            }
        )

    index = {
        "tracker": syllabus["tracker"],
        "subjects": subjects,
        "units": [{**u, "sheets": by_unit[u["id"]]} for u in syllabus["units"]],
        "plan_start": PLAN_START,
        "plan": build_plan(subjects),
    }
    save_json(DATA_DIR / "index.json", index)
    total = sum(s["total"] for s in subjects)
    done = sum(1 for s in subjects for sh in s["sheets"] if sh["count"])
    facts = sum(sh["facts"] for s in subjects for sh in s["sheets"])
    covered = sum(sh["covered"] for s in subjects for sh in s["sheets"])
    print(
        f"index.json: {len(subjects)} subjects, {done}/{sum(len(s['sheets']) for s in subjects)} sheets generated, "
        f"{total} MCQs, {covered}/{facts} facts covered"
    )
    return index


if __name__ == "__main__":
    build_index()
