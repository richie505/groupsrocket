"""Build data/index.json (what the practice app loads first).

    python scripts/build_index.py
"""

from common import DATA_DIR, MCQS_DIR, SHEETS_DIR, load_json, load_syllabus, save_json, slugify

PLAN_DAYS = 90
PLAN_START = "2026-09-29"  # Day 1 of the 90-day plan


# Mirrors the "APPSC Restructured 90-Day Plan": subject blocks in book order, a review + weekly
# test every 7th day up to Day 70, revision Days 71-83, mocks and repair Days 84-90.
BLOCKS = [
    ("Polity & Society", ["Indian Polity", "Indian Society"]),
    ("History", ["Indian History", "AP History"]),
    ("Geography", ["General Geography", "Indian Geography", "World Geography", "AP Geography"]),
    ("Economy", ["Indian Economy"]),
    ("Science & Environment", ["Physics", "Chemistry", "Biology", "Environment", "Disaster Management"]),
]
STUDY_UNTIL = 70
REVISION = [  # (block, parts) in the original plan's order, Days 71-82
    ("Polity & Society", 3), ("History", 3), ("Geography", 2), ("Economy", 2), ("Science & Environment", 2),
]
FINAL_DAYS = [
    {"type": "revision", "title": "Revision: all subjects (mixed)", "all": True, "questions": 100},
    {"type": "mock", "title": "Full mock: G1 Prelims Paper-I style (120 Q)", "questions": 120},
    {"type": "mock", "title": "Full mock: G1 Prelims Paper-II style (120 Q)", "questions": 120},
    {"type": "mock", "title": "Full mock: G2 Screening style (150 Q)", "questions": 150},
    {"type": "repair", "title": "Repair day: re-practise every question you got wrong"},
    {"type": "mock", "title": "Exam-day simulation: two 120 Q papers", "questions": 240},
    {"type": "mock", "title": "Full mock: G2 Screening style (150 Q)", "questions": 150},
    {"type": "repair", "title": "Repair day: wrong answers + weakest sheets"},
]


def _split(items: list, parts: int, weight) -> list[list]:
    """Split items into `parts` contiguous groups of roughly equal total weight."""
    total = sum(weight(i) for i in items) or 1
    groups, cur, acc = [], [], 0.0
    for i, item in enumerate(items):
        cur.append(item)
        acc += weight(item)
        left_items, left_groups = len(items) - i - 1, parts - len(groups) - 1
        if left_groups and (acc >= total * (len(groups) + 1) / parts or left_items == left_groups):
            groups.append(cur)
            cur = []
    groups.append(cur)
    return [g for g in groups if g]


def build_plan(subjects: list[dict]) -> list[dict]:
    by_name = {s["name"]: s for s in subjects}
    ref = lambda s, sh: f"{s['slug']}/{sh['id']}"  # noqa: E731
    count = lambda item: max(1, item[1]["count"])  # noqa: E731

    study_days = [d for d in range(1, STUDY_UNTIL + 1) if d % 7]
    # Days per subject in proportion to its MCQ count (largest remainder, at least 1).
    order = [n for _, names in BLOCKS for n in names if n in by_name]
    load = {n: max(1, by_name[n]["total"]) for n in order}
    total = sum(load.values())
    quota = {n: len(study_days) * load[n] / total for n in order}
    alloc = {n: max(1, int(quota[n])) for n in order}
    for n in sorted(order, key=lambda n: quota[n] - int(quota[n]), reverse=True):
        if sum(alloc.values()) >= len(study_days):
            break
        alloc[n] += 1
    while sum(alloc.values()) > len(study_days):
        alloc[max(order, key=lambda n: alloc[n])] -= 1

    plan: dict[int, dict] = {}
    days = iter(study_days)
    block_of = {n: b for b, names in BLOCKS for n in names}
    for name in order:
        subj = by_name[name]
        items = [(subj, sh) for sh in subj["sheets"]]
        for group in _split(items, alloc[name], count):
            first, last = group[0][1]["id"], group[-1][1]["id"]
            plan[next(days)] = {
                "type": "study",
                "block": block_of[name],
                "title": f"{name} – Sheets #{first}" + (f" to #{last}" if first != last else ""),
                "sheets": [ref(s, sh) for s, sh in group],
            }
    for d in range(7, STUDY_UNTIL + 1, 7):
        week = [r for w in range(d - 6, d) for r in plan.get(w, {}).get("sheets", [])]
        plan[d] = {"type": "review", "title": f"Weekly review + test (Days {d - 6}–{d - 1})", "sheets": week, "questions": 50}

    day = STUDY_UNTIL + 1
    for block, parts in REVISION:
        names = dict(BLOCKS)[block]
        items = [(by_name[n], sh) for n in names if n in by_name for sh in by_name[n]["sheets"]]
        for k, group in enumerate(_split(items, parts, count), 1):
            plan[day] = {
                "type": "revision",
                "block": block,
                "title": f"Revision {k}/{parts}: {block}",
                "sheets": [ref(s, sh) for s, sh in group],
                "questions": 100,
            }
            day += 1
    everything = [ref(s, sh) for s in subjects for sh in s["sheets"]]
    for item in FINAL_DAYS:
        entry = {k: v for k, v in item.items() if k != "all"}
        entry["sheets"] = everything if item["type"] in ("mock", "revision") else []
        plan[day] = entry
        day += 1
    return [plan[d] for d in range(1, PLAN_DAYS + 1)]


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
