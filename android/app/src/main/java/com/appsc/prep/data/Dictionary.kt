package com.appsc.prep.data

import java.io.InputStream

/**
 * Offline word meanings for "Meaning" on selected text, Indian context first:
 * the Indian exam glossary and the definitions the notes give (assets/india.json, tools/build_india_glossary.py),
 * the short forms the notes use (SpeechText.expand), then a general dictionary without US-only senses
 * (WordNet 3.1: assets/dict/<letter>.tsv, tools/build_dictionary.py).
 */
class Dictionary(private val open: (String) -> InputStream) {
    /** A definition from the notes and where it is ("Geography · Atmosphere ..."). */
    data class NoteDefinition(val text: String, val where: String)

    private val india: Pair<Map<String, String>, Map<String, List<NoteDefinition>>> by lazy {
        runCatching {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(open("india.json").bufferedReader().use { it.readText() })
                .let { it as kotlinx.serialization.json.JsonObject }
            val g = (root["g"] as kotlinx.serialization.json.JsonObject).mapValues { (it.value as kotlinx.serialization.json.JsonPrimitive).content }
            val n = (root["n"] as kotlinx.serialization.json.JsonObject).mapValues { (_, v) ->
                (v as kotlinx.serialization.json.JsonArray).map { d ->
                    val a = d as kotlinx.serialization.json.JsonArray
                    NoteDefinition((a[0] as kotlinx.serialization.json.JsonPrimitive).content, (a[1] as kotlinx.serialization.json.JsonPrimitive).content)
                }
            }
            g to n
        }.getOrDefault(emptyMap<String, String>() to emptyMap())
    }
    data class Sense(val pos: String, val definition: String, val example: String)

    /**
     * [word] is the form found ("government" for "governments"); [india] the Indian-context meaning,
     * [notes] the notes' own definitions, [senses] the general dictionary.
     */
    data class Entry(
        val word: String,
        val senses: List<Sense>,
        val shortForm: String? = null,
        val india: String? = null,
        val notes: List<NoteDefinition> = emptyList(),
    ) {
        val isEmpty get() = senses.isEmpty() && shortForm == null && india == null && notes.isEmpty()
    }

    // the last few letter files used: (sorted lower-case keys, lines)
    private val letters = object : LinkedHashMap<Char, Pair<List<String>, List<String>>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Char, Pair<List<String>, List<String>>>?) = size > 3
    }

    /** Meaning of a selected word or phrase, or null when neither the dictionary nor the notes know it. */
    @Synchronized
    fun lookup(selection: String): Entry? {
        val raw = selection.trim().trim { !it.isLetterOrDigit() }.replace(Regex("""\s+"""), " ")
        if (raw.isEmpty() || raw.length > 60) return null
        // a short form the notes use (SC, WTO, VCIC ...)
        val short = raw.takeIf { it.length in 2..10 && it.count(Char::isUpperCase) >= 2 }?.let { SpeechText.expand(it) }
        val forms = candidates(raw)
        val (glossary, notes) = india
        val indian = forms.firstNotNullOfOrNull { f -> glossary[f]?.let { f to it } }
        val fromNotes = forms.firstNotNullOfOrNull { f -> notes[f]?.let { f to it } }
        val general = forms.firstNotNullOfOrNull { f -> find(f)?.let { f to it } }
        val word = indian?.first ?: general?.first ?: fromNotes?.first ?: raw.lowercase()
        val entry = Entry(word, general?.second.orEmpty(), short, indian?.second, fromNotes?.second.orEmpty())
        return entry.takeUnless { it.isEmpty }
    }

    /** The selection and its likely dictionary forms: plural, past, -ing, -er/-est. */
    private fun candidates(raw: String): List<String> {
        val w = raw.lowercase().replace('’', '\'').removeSuffix("'s").removeSuffix("'")
        val out = linkedSetOf(w)
        if (' ' !in w) {
            fun add(s: String) { if (s.length >= 2) out += s }
            when {
                w.endsWith("ies") -> add(w.dropLast(3) + "y")
                w.endsWith("ves") -> { add(w.dropLast(3) + "f"); add(w.dropLast(3) + "fe") }
                w.endsWith("sses") || w.endsWith("shes") || w.endsWith("ches") || w.endsWith("xes") || w.endsWith("zes") -> add(w.dropLast(2))
            }
            if (w.endsWith("es")) { add(w.dropLast(1)); add(w.dropLast(2)) }
            if (w.endsWith("s") && !w.endsWith("ss")) add(w.dropLast(1))
            for (suffix in listOf("ed", "ing", "er", "est")) {
                if (!w.endsWith(suffix)) continue
                val stem = w.dropLast(suffix.length)
                if (stem.endsWith("i")) add(stem.dropLast(1) + "y")
                add(stem)
                add(stem + "e")
                if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
            }
            if (w.endsWith("ly")) { add(w.dropLast(2)); if (w.endsWith("ily")) add(w.dropLast(3) + "y") }
        } else {
            // hyphens and spaces are written either way
            out += w.replace('-', ' ')
            out += w.replace(' ', '-')
        }
        return out.toList()
    }

    private fun find(word: String): List<Sense>? {
        val (keys, lines) = file(word.first()) ?: return null
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = keys[mid].compareTo(word)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return lines[mid].split('\t').drop(1).mapNotNull { s ->
                    val p = s.split('|')
                    if (p.size < 2) null else Sense(POS[p[0]] ?: p[0], p[1], p.getOrElse(2) { "" })
                }
            }
        }
        return null
    }

    private fun file(first: Char): Pair<List<String>, List<String>>? {
        val c = first.lowercaseChar().let { if (it in 'a'..'z') it else '_' }
        letters[c]?.let { return it }
        val lines = runCatching { open("dict/$c.tsv").bufferedReader().use { it.readLines() } }.getOrNull() ?: return null
        val keys = lines.map { it.substringBefore('\t').lowercase() }
        return (keys to lines).also { letters[c] = it }
    }

    private companion object {
        val POS = mapOf("n" to "noun", "v" to "verb", "adj" to "adjective", "adv" to "adverb")
    }
}
