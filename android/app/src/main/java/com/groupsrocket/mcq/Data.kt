package com.groupsrocket.mcq

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** "subject-slug/sheet-id", e.g. "indian-polity/12". */
typealias SheetRef = String

data class Tracker(val g1: List<String>, val g2: List<String>) {
    val all get() = g1 + g2
}

data class SheetInfo(
    val subjectSlug: String,
    val subjectName: String,
    val id: String,
    val number: Int,
    val title: String,
    val count: Int,
    val facts: Int,
    val covered: Int,
    val tracker: Tracker,
) {
    val ref: SheetRef get() = "$subjectSlug/$id"
}

data class Subject(val name: String, val slug: String, val sheets: List<SheetInfo>, val total: Int)

data class SyllabusUnit(
    val id: String,
    val exam: String,
    val section: String,
    val title: String,
    val sheets: List<SheetRef>,
)

data class Fact(val id: String, val text: String)

data class Mcq(
    val id: String,
    val sheet: SheetRef,
    val format: String,
    val keyword: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    val facts: List<String>,
)

class Index(
    val tracker: String,
    val planStart: String,
    val subjects: List<Subject>,
    val units: List<SyllabusUnit>,
    val plan: List<List<SheetRef>>,
) {
    private val sheetsByRef = subjects.flatMap { it.sheets }.associateBy { it.ref }
    val unitsById = units.associateBy { it.id }
    fun sheet(ref: SheetRef): SheetInfo? = sheetsByRef[ref]
    val totalMcqs get() = subjects.sumOf { it.total }
}

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> = List(length()) { map(getJSONObject(it)) }

/** Reads the bundled data/ folder: index.json, mcqs/<slug>.json, sheets/<slug>.json. */
class Repository(private val context: Context) {
    val index: Index by lazy { loadIndex() }
    private val mcqCache = HashMap<String, Map<String, List<Mcq>>>()
    private val notesCache = HashMap<String, Map<String, String>>()

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: java.io.IOException) {
        null
    }

    private fun loadIndex(): Index {
        val root = JSONObject(readAsset("index.json") ?: "{}")
        val subjects = root.optJSONArray("subjects")?.objects { s ->
            val slug = s.getString("slug")
            val name = s.getString("name")
            Subject(
                name = name,
                slug = slug,
                total = s.optInt("total"),
                sheets = s.getJSONArray("sheets").objects { sh ->
                    val t = sh.getJSONObject("tracker")
                    SheetInfo(
                        subjectSlug = slug,
                        subjectName = name,
                        id = sh.getString("id"),
                        number = sh.getInt("number"),
                        title = sh.getString("title"),
                        count = sh.optInt("count"),
                        facts = sh.optInt("facts"),
                        covered = sh.optInt("covered"),
                        tracker = Tracker(t.getJSONArray("g1").strings(), t.getJSONArray("g2").strings()),
                    )
                },
            )
        } ?: emptyList()
        val units = root.optJSONArray("units")?.objects { u ->
            SyllabusUnit(u.getString("id"), u.getString("exam"), u.getString("section"), u.getString("title"), u.getJSONArray("sheets").strings())
        } ?: emptyList()
        val plan = root.optJSONArray("plan")?.let { p -> List(p.length()) { p.getJSONArray(it).strings() } } ?: emptyList()
        return Index(root.optString("tracker"), root.optString("plan_start", "2026-09-29"), subjects, units, plan)
    }

    @Synchronized
    private fun subjectMcqs(slug: String): Map<String, List<Mcq>> = mcqCache.getOrPut(slug) {
        val text = readAsset("mcqs/$slug.json") ?: return@getOrPut emptyMap()
        val sheets = JSONObject(text).getJSONObject("sheets")
        sheets.keys().asSequence().associateWith { id ->
            sheets.getJSONObject(id).getJSONArray("mcqs").objects { q ->
                Mcq(
                    id = q.getString("id"),
                    sheet = "$slug/$id",
                    format = q.getString("format"),
                    keyword = q.optString("keyword"),
                    question = q.getString("question"),
                    options = q.getJSONArray("options").strings(),
                    answer = q.getInt("answer"),
                    explanation = q.optString("explanation"),
                    facts = q.optJSONArray("facts")?.strings().orEmpty(),
                )
            }
        }
    }

    fun mcqs(ref: SheetRef): List<Mcq> {
        val (slug, id) = ref.split("/", limit = 2)
        return subjectMcqs(slug)[id].orEmpty()
    }

    fun mcqs(refs: Collection<SheetRef>): List<Mcq> = refs.flatMap { mcqs(it) }

    private val factsCache = HashMap<String, JSONObject>()

    /** Every atomic fact extracted from the sheet (data/facts/<slug>.json). */
    @Synchronized
    fun facts(ref: SheetRef): List<Fact> {
        val (slug, id) = ref.split("/", limit = 2)
        val sheets = factsCache.getOrPut(slug) {
            JSONObject(readAsset("facts/$slug.json") ?: "{}").optJSONObject("sheets") ?: JSONObject()
        }
        return sheets.optJSONArray(id)?.objects { Fact(it.getString("id"), it.getString("text")) }.orEmpty()
    }

    @Synchronized
    fun notes(ref: SheetRef): String {
        val (slug, id) = ref.split("/", limit = 2)
        val sheets = notesCache.getOrPut(slug) {
            val text = readAsset("sheets/$slug.json") ?: return@getOrPut emptyMap()
            JSONObject(text).getJSONArray("sheets").objects { it.getString("id") to it.getString("text") }.toMap()
        }
        return sheets[id].orEmpty()
    }
}
