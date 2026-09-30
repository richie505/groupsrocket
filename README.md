# Rocket Sheets MCQ — APPSC practice app (Android)

Offline Android app for practising MCQs generated **only from the ROCKET Sheets notes**
(no PYQs). **15,855 MCQs covering all 37,158 facts** of the 721 sheets. Every `ROCKET SHEET #N` of every subject gets its own MCQ set, mapped onto the
**APPSC combined syllabus tracker** (Group 1 Prelims + Group 2), and scheduled in a
**90-day plan starting 29 Sep 2026**.

## What's inside

| Path | What it is |
|---|---|
| `data/sheets/<subject>.json` | Notes text split per ROCKET SHEET (14 subjects, 721 sheets) |
| `data/syllabus.json` | Combined syllabus-tracker units (G1 A–F, G2 screening + mains) and each subject's candidate units |
| `data/mcqs/<subject>.json` | Generated MCQs per sheet, with the sheet's tracker units |
| `data/index.json` | App manifest: subjects, tracker → sheets, 90-day plan |
| `prompts/` | MCQ format guide + blueprint keyword angles used in the prompt |
| `scripts/` | Extraction, OpenAI generation and index build |
| `android/` | Kotlin + Jetpack Compose app (bundles `data/` as assets) |

Subjects: AP History, AP Geography, Indian History, Indian Geography, World Geography,
General Geography, Indian Polity, Indian Economy, Indian Society, Environment,
Disaster Management, Physics, Chemistry, Biology.

## App features

- **90-Day Plan** — Day 1 = 29 Sep 2026, same structure as the "APPSC Restructured 90-Day Plan": one subject per day with consecutive sheets (Polity & Society → History → Geography → Economy → Science & Environment, ~140 MCQs/day), a weekly review test every 7th day, revision Days 71–83, full mocks + repair days 84–90.
- **Subjects** → sheets → practice a sheet, or read that sheet's notes.
- **Syllabus Tracker** — Group 1 / Group 2 tabs, each unit shows its mapped sheets, MCQ count and progress.
- **Quiz** — instant right/wrong, explanation, MCQ format + blueprint keyword tags, bookmarks, score summary, "retry wrong".
- **Review** — all wrong answers, bookmarks, random 25-question mixed test, overall accuracy.

## MCQ generation (OpenAI)

The prompt (`scripts/prompt.py`) is the APPSC tutor MCQ module: 8 official formats
(Direct Recall, Negative, Assertion-Reason, Statement A/B, Multi-Statement, Chronological,
List-Matching, Count-Based), close distractors, explanations grounded strictly in the notes,
blueprint keyword tags. It uses structured JSON output; the script validates answers,
balances answer positions and picks the tracker units for each sheet.

```bash
pip install -r requirements.txt
export OPENAI_API_KEY=sk-...            # optional: OPENAI_MODEL=<model>
python scripts/generate_mcqs.py                       # all sheets (skips done ones)
python scripts/generate_mcqs.py --subject "Indian Polity" --sheets 3 4 --force
```

MCQ count scales with sheet length (5–25 per sheet); override with `--per-sheet N`.
Or run the **Generate MCQs (OpenAI)** GitHub Action (needs the `OPENAI_API_KEY` repo secret).

### Updating notes

Download the Drive `rocket sheets` folder (`<Subject> - ROCKET Sheets.pdf`), then:

```bash
python scripts/extract_sheets.py path/to/rocket-sheets
python scripts/generate_mcqs.py
```

### Fact coverage (every fact gets an MCQ)

```bash
python scripts/cover_facts.py --workers 32     # resumable; finished sheets are skipped
```

For each sheet it extracts every atomic fact (`data/facts/`), links each MCQ to the facts it
tests, writes new MCQs for untested facts, and re-checks the gaps. The app shows
"facts covered" per sheet and a Facts checklist.

## Building the APK

The **Build Android APK** GitHub Action builds `app-release.apk` on every push that touches
`android/` or `data/` — download it from the run's artifacts and install it on your phone
(allow "install unknown apps"). Locally:

```bash
cd android
./gradlew assembleRelease     # needs Android SDK (local.properties: sdk.dir=...)
# -> app/build/outputs/apk/release/app-release.apk
```
