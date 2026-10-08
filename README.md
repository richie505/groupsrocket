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

## App (Rocket Prep)

`android/` is **Rocket Prep**, built on the APPSC Prep app's code and pattern with the ROCKET
Sheets as its own syllabus (`scripts/build_prep_assets.py` writes its assets):

**Book** = subject block (Polity & Society · History · Geography · Economy · Science & Environment)
→ **Topic** = subject → **Section** = ROCKET SHEET #N → subsections **Key facts** (every fact of the
sheet) and **Sheet text** (as in the PDF). After each section: MCQ practice (Previous / Skip / Next,
"Stuck? Show a hint", explanation, what the question trains, "why it went wrong" technique note,
net score with 1/3 negative marking, retry wrong answers).
**Read aloud** (headphones button in the reader, from APPSC Prep v2.10): reads each Key fact in
turn, skips the source tags, says short forms in full, speed control, and keeps reading with the
screen locked (media notification).

Tabs: **Today** · **Plan** (90 days from 29 Sep 2026, Polity first, one subject per day; weekly
50-question tests; revision days; 120/150-question mocks; repair days) · **Notes** · **Progress** · **Saved**.

**Rocket Revision** (second app, same code): one page per ROCKET SHEET, "Revision points (N)", built from
the sheet's MCQs by `scripts/build_revision.py` (→ `android/app/src/revise/assets`): one fact per question, the
explanation's fact sentence when it reads on its own, else the sheet fact the MCQ tests; answer in bold; repeats
dropped. 12,572 points over the 721 sheets. Same 90-day plan, MCQ practice and read-aloud; its own progress
("revised"). Installs next to Rocket Prep (`com.groupsrocket.rocketprep.revise`, green icon).

```bash
python scripts/build_index.py && python scripts/build_prep_assets.py && python scripts/build_key_terms.py android/app/src/main/assets && python scripts/build_revision.py
cd android
./gradlew assembleNotesRelease     # Rocket Prep:     app/build/outputs/apk/notes/release/app-notes-release.apk
./gradlew assembleReviseRelease    # Rocket Revision: app/build/outputs/apk/revise/release/app-revise-release.apk
./gradlew testNotesDebugUnitTest testReviseDebugUnitTest :desktop:test
./gradlew recordRoborazziNotesDebug recordRoborazziReviseDebug   # screenshots -> app/screenshots/
```

**v1.3** brings in everything from APPSC Prep up to v2.33 (merged onto Rocket's own changes):
Unattempted / Incorrect / All MCQ sets with counts (Today and each sheet); after a wrong answer the
explanation sentence about the picked option is highlighted, and "From your notes" quotes the sheet's lines with
a link to the page; long-press any word → Meaning (offline dictionary + Indian context + what your sheets say);
Key terms on every page (`scripts/build_key_terms.py`); Explain simply / Google / Open in Chrome / Ask Gemini;
own notes; backup and restore (Progress tab); read-aloud with full forms.

### Windows app

`android/desktop` is the same screens for Windows (Compose for Desktop), using the whole window: lists in
columns, the sheet's pages listed on the left in the reader, Windows' own voices for read-aloud.

```bash
cd android
./gradlew :desktop:run                  # run on this computer
tools/windows/build_windows.sh          # Rocket-Prep-Setup.exe + Rocket-Prep-Windows.zip (needs mingw-w64, nsis)
```

Progress is kept in `%APPDATA%\Rocket Prep` (separate from APPSC Prep).

Signed with `android/keystore/rocket-prep.jks`, so new APKs install over old ones and keep progress.
Its app id (`com.groupsrocket.rocketprep`) differs from APPSC Prep, so both apps can be installed.

### MCQ schedule PDFs

`scripts/build_mcq_pdfs.py` prints the app's MCQs as one PDF per plan day (like APPSC Prep's
APPSC-MCQ-Schedule-90-Days): Day → subject → ROCKET SHEET #N (source pages) → questions, each
followed by its answer, plus an `Answer Keys/` grid per day. Study days print every MCQ of the
day's sheets (all 15,855 appear once); weekly reviews and revision days print a revision set
(HIGH 15 / MED 8 / LIGHT 5 per sheet; Day 83 one from every sheet); mock days have no PDF.

```bash
pip install reportlab
python scripts/build_mcq_pdfs.py android/app/src/main/assets out/Rocket-MCQ-Schedule-90-Days
python scripts/build_mcq_pdfs.py android/app/src/main/assets out/Rocket-MCQ-Schedule-90-Days-Dark --dark
# daily revision points PDFs (Rocket Revision's pages, same days and sheets as the MCQ PDFs)
python scripts/build_revision_pdfs.py android/app/src/main/assets android/app/src/revise/assets out/Rocket-Revision-90-Days
python scripts/build_revision_pdfs.py android/app/src/main/assets android/app/src/revise/assets out/Rocket-Revision-90-Days-Dark --dark
```

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

The **Build Android APK** GitHub Action builds both APKs (Rocket Prep and Rocket Revision) on every push that touches
`android/` or `data/` — download it from the run's artifacts and install it on your phone
(allow "install unknown apps"). Locally:

```bash
cd android
./gradlew assembleNotesRelease assembleReviseRelease   # needs Android SDK (local.properties: sdk.dir=...)
# -> app/build/outputs/apk/{notes,revise}/release/
```
