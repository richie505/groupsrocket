package com.appsc.prep.data

/**
 * MCQ solving techniques from "APPSC MCQ Techniques - Tested on PYQs" (the Drive guide).
 * Everything is worked out from the question text and options, so it covers every MCQ in the app:
 *  - [hints]: what to try when stuck, before answering (never uses the answer key);
 *  - [review]: after a wrong answer, which technique would have reached the key and which trap caught the pick.
 * Clue words are weighted per subject (Part B/C of the guide): a clue that did not work in a subject is not used there.
 */
object Techniques {

    enum class Kind { ASSERTION, MATCH, ORDER, STATEMENTS, NUMBERS, SENTENCES, SHORT }

    /** A word clue inside a statement. wrong = points to a FALSE statement; proof = the guide's APPSC figure. */
    private class Clue(val id: String, val label: String, val wrong: Boolean, val proof: String, val re: Regex)

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val CLUES = listOf(
        Clue("only", "'only'", true, "wrong 68 in 100, normal 46", r("""\bonly\b""")),
        Clue("absolute", "a 'no exceptions' word (all the / any / entirely / cannot / never / always)", true, "wrong 60 in 100, normal 44",
            r("""\b(all the|any|entirely|cannot|can not|never|always)\b""")),
        Clue("not", "'not / does not'", true, "wrong 51 in 100, normal 38", r("""\b(not|does not|do not|did not|is not|are not|no)\b|n't\b""")),
        Clue("launched", "'launched / developed / set up by'", true, "wrong 48–56 in 100, normal 36–38",
            r("""\b(launched|developed|set up|established|introduced|released|published|prepared|constituted) by\b""")),
        Clue("ministry", "a 'Ministry of / Department of' name", true, "wrong 58 in 100, normal 37", r("""\b(ministry|department) of\b""")),
        Clue("world", "a named world body (UN, WHO, World Bank, IMF…)", true, "wrong 58 in 100, normal 41",
            Regex("""\b(UN|UNO|UNESCO|UNEP|UNDP|UNICEF|UNFCCC|WHO|WTO|IMF|ILO|FAO|OECD|ADB|AIIB|IPCC|IUCN)\b|\b(United Nations|World Bank|World Economic Forum|International Monetary Fund|World Health Organi[sz]ation)\b""")),
        Clue("member", "'member of / consists of'", true, "wrong 54 in 100, normal 44",
            r("""\b(members? of|consists? of|comprises?|composed of|member countries|chairman of|chairperson of)\b""")),
        Clue("power", "a power given to a named authority (President, Governor, Speaker…)", true, "wrong 67 in 100, normal 44",
            r("""\b(president|governor|speaker|prime minister|chief minister|chief justice|vice-president|chairman of the rajya sabha)\b.{0,60}\b(can|may|has the power|is empowered|appoints?|removes?|dissolves?|summons?|nominates?|pardons?|issues?|declares?)\b""")),
        Clue("article", "'Article N / according to / the Constitution of India'", true, "wrong 47 in 100, normal 35",
            r("""\barticles? \d+|\baccording to\b|\bconstitution of india\b|\bthe constitution (says|defines|provides)""")),
        Clue("between", "'between X and Y'", true, "wrong 46–55 in 100, normal 40", r("""\bbetween\b.{1,60}\band\b""")),
        Clue("time", "a time or trend phrase ('in the last…', 'during the…', 'steadily')", true, "wrong 59 in 100, normal 37",
            r("""\bin the last\b|\bduring the\b|\bsteadily\b|\bcontinuously\b""")),
        Clue("reign", "'during the reign of / founded by / started by'", true, "wrong 75 in 100, normal 37",
            r("""\bduring the reign of\b|\b(founded|started|built|written|composed) by\b""")),
        Clue("size", "a size word ('in India', 'in the world', 'the state')", true, "wrong 45–48 in 100, normal 39–41",
            r("""\bin india\b|\bin the world\b|\bthe state\b|\blargest\b|\bhighest\b""")),
        Clue("located", "'is located in / lies in / headquarters'", true, "wrong 50–61 in 100, normal 39",
            r("""\b(is|are) located\b|\blies in\b|\bheadquarter""")),
        Clue("compare", "a comparison ('higher than', 'whereas', 'unlike', 'but')", true, "wrong 58 in 100, normal 41",
            r("""\b(higher|lower|more|less|greater|smaller|larger|bigger) than\b|\bwhereas\b|\bunlike\b|\bbut\b""")),
        Clue("percent", "a percentage", true, "wrong 52 in 100, normal 46", r("""%|\bper ?cent\b""")),
        Clue("long", "a very long sentence (25+ words)", true, "wrong 43–57 in 100, normal 37", Regex("""(\S+\s+){24,}\S+""")),
        Clue("some", "a careful word ('some' / 'one of the')", false, "wrong only 21 in 100, normal 38", r("""\bsome\b|\bone of the\b""")),
        Clue("can", "'can / can be'", false, "wrong 35 in 100, normal 38", r("""\bcan\b""")),
        Clue("does", "what something does ('is used for', 'helps in', 'leads to', 'aims to')", false, "wrong only 3–17 in 100, normal 33–38",
            r("""\b(is|are) used (for|to|in)\b|\bhelps? (in|to)\b|\bleads? to\b|\baims? (to|at)\b""")),
    )

    /** Clues that work (strong) or work a little (weak) in each book's subject, from Part C. Polity 'can' works the OPPOSITE way. */
    private val STRONG = mapOf(
        1 to setOf("reign"),
        2 to setOf("only", "absolute", "member", "power", "article", "time", "size", "compare", "percent", "long", "can"),
        3 to setOf("absolute", "not", "launched", "ministry", "world", "time", "long", "does"),
        4 to setOf("only", "absolute", "not", "between", "size", "located", "some", "can"),
        5 to setOf("only", "absolute", "not", "some", "can", "does", "size"),
        6 to setOf("launched", "ministry", "located"),
    )
    private val WEAK = mapOf(
        1 to setOf("absolute", "not", "world", "time", "compare", "some"),
        2 to setOf("not", "launched", "ministry", "between", "some"),
        3 to setOf("only", "between", "size", "compare", "percent", "some"),
        4 to setOf("compare", "percent", "does"),
        5 to setOf("launched", "compare", "world"),
        6 to setOf("world"),
    )
    private val SUBJECT = mapOf(1 to "History", 2 to "Polity", 3 to "Economy", 4 to "Geography", 5 to "Science", 6 to "Current Affairs")

    // ---------- question analysis ----------

    private val NEG = r("""\b(not|except|incorrect|false|wrong|odd one|does not|do not|is not|are not|cannot|never)\b|\bnot (correct|true|a)\b""")

    fun isNegative(stem: String): Boolean {
        val ask = stem.lines().filter { it.isNotBlank() }.let { ls -> (ls.take(1) + ls.takeLast(1)).joinToString(" ") }
        return Regex("""\b(NOT|EXCEPT|INCORRECT|FALSE)\b""").containsMatchIn(ask) ||
            r("""\b(is|are) not (correct|true|a|an|the)\b|\bexcept\b|\bincorrect\b|\bnot correctly\b|\bnot (true|correct)\b|\bodd one\b|\bwrong(ly)?\b|\bfalse\b|which (one )?.{0,40}\b(does not|do not|is not|are not|was not|were not|did not|has not|have not)\b""").containsMatchIn(ask)
    }

    private val AR = r("""assertion""")
    private val ORDER = r("""\b(arrange|chronological|sequence|correct order|ascending|descending|from (north|south|east|west) to)\b""")
    private val MATCH = r("""\bmatch\b|list\s*[-–]?\s*i\b|column\s*[-–]?\s*(a|i)\b""")
    private val PAIR = Regex("""\b([A-Ha-h]|I{1,3}|IV|VI{0,3}|IX|X)\s*[-–—:)]\s*\(?([0-9]+|[ivx]+|[A-Ha-h]|I{1,3}|IV|VI{0,3})\)?""")
    private val NUM = Regex("""^[₹$\s]*[-+]?[\d][\d,]*(\.\d+)?\s*(%|per ?cent|years?|yrs|km|kms|crores?|lakhs?|million|billion|days|months|times|tons|mm|cm|m|kg|°\s*[CF]?|degrees?|seats|members|th|st|nd|rd|AD|BC|CE)?\.?$""", RegexOption.IGNORE_CASE)

    private fun numVal(s: String): Double? {
        if (!NUM.matches(s.trim())) return null
        return Regex("""[\d][\d,]*(\.\d+)?""").find(s)?.value?.replace(",", "")?.toDoubleOrNull()
    }

    /** Statement labels found in the stem, in order, with their text. */
    fun statements(stem: String): List<Pair<String, String>> {
        val line = Regex("""^\s*\(?([1-9]|[ivx]{1,4}|[IVX]{1,4}|[A-Ea-e])[.)]\s*(.+)$""")
        val out = stem.lines().mapNotNull { l -> line.find(l)?.let { it.groupValues[1] to it.groupValues[2].trim() } }
            .filter { it.second.split(' ').size >= 3 }
        if (out.size >= 2) return out
        // statements run together on one line: "1. xxx 2. yyy 3. zzz"
        val inline = Regex("""(?:^|\s)([1-6])\.\s+""").findAll(stem).toList()
        if (inline.size >= 2 && inline.map { it.groupValues[1] } == (1..inline.size).map { "$it" }) {
            return inline.mapIndexed { i, m ->
                val end = if (i + 1 < inline.size) inline[i + 1].range.first else stem.length
                m.groupValues[1] to stem.substring(m.range.last + 1, end).trim()
            }
        }
        return emptyList()
    }

    /** Which statement labels an option accepts; null if the option does not talk about the labels. */
    fun optionSet(opt: String, labels: List<String>): Set<String>? {
        val o = opt.trim()
        if (r("""^(all of the above|all the above|all of these|all are correct|all)\b""").containsMatchIn(o) && !r("""\bexcept\b""").containsMatchIn(o)) return labels.toSet()
        if (r("""^(none|neither)\b""").containsMatchIn(o)) return emptySet()
        val found = labels.filter { lab ->
            val cs = lab.length == 1 && lab[0].isLetter()
            Regex("""(?<![A-Za-z0-9])${Regex.escape(lab)}(?![A-Za-z0-9])""", if (cs) setOf() else setOf(RegexOption.IGNORE_CASE)).containsMatchIn(o)
        }.toSet()
        return found.ifEmpty { null }
    }

    private fun pairs(opt: String): Set<String> = PAIR.findAll(opt).map { "${it.groupValues[1].uppercase()}-${it.groupValues[2].lowercase()}" }.toSet()

    private fun seq(opt: String): List<String> = Regex("""\(?([A-Ha-h]|[1-9]|[ivx]{1,4}|[IVX]{1,4})\)?""").findAll(opt)
        .map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()

    fun kind(q: Question): Kind {
        val o = q.options
        if (o.size < 2) return Kind.SHORT
        if (AR.containsMatchIn(q.stem) && r("""\breason\b""").containsMatchIn(q.stem) && o.any { r("""explanation|explain""").containsMatchIn(it) }) return Kind.ASSERTION
        if (MATCH.containsMatchIn(q.stem) && (o.all { pairs(it).size >= 2 } || o.all { seq(it).size >= 3 && it.length < 40 })) return Kind.MATCH
        if (ORDER.containsMatchIn(q.stem) && o.all { seq(it).size >= 3 && it.length < 40 }) return Kind.ORDER
        val st = statements(q.stem)
        if (st.size >= 2 && o.count { optionSet(it, st.map { s -> s.first }) != null } >= 3) return Kind.STATEMENTS
        if (o.all { numVal(it) != null }) return Kind.NUMBERS
        if (o.map { it.split(' ').size }.average() >= 3.5) return Kind.SENTENCES
        return Kind.SHORT
    }

    private fun cluesIn(text: String, book: Int): List<Pair<Clue, Boolean>> {
        val strong = STRONG[book] ?: emptySet()
        val weak = WEAK[book] ?: emptySet()
        return CLUES.filter { (it.id in strong || it.id in weak) && it.re.containsMatchIn(text) }
            .map { it to (it.id in strong) }
            // Polity: 'can' points to WRONG (setters swap powers)
            .map { (c, s) -> if (book == 2 && c.id == "can") Clue("can", "'can / may' about a power", true, "wrong 65 in 100, normal 47 in Polity", c.re) to s else c to s }
    }

    private fun letter(i: Int) = "(${i + 1})"

    private fun clearlyLongest(o: List<String>): Int? {
        val lens = o.map { it.length }
        val sorted = lens.sortedDescending()
        if (sorted.size < 2 || sorted[0] < 12 || sorted[0] < sorted[1] * 1.3) return null
        return lens.indexOf(sorted[0])
    }

    private fun middleNumbers(o: List<String>): Set<Int>? {
        val v = o.map { numVal(it) ?: return null }
        if (v.toSet().size != v.size || v.size != 4) return null
        val order = v.indices.sortedBy { v[it] }
        return setOf(order[1], order[2])
    }

    private fun familyOption(o: List<String>): Int? {
        val words = o.map { it.lowercase().split(Regex("""\W+""")).filter { w -> w.length > 2 }.toSet() }
        if (words.any { it.size < 3 }) return null
        val score = words.indices.map { i -> words.indices.filter { it != i }.sumOf { (words[i] intersect words[it]).size } }
        val best = score.max()
        return if (score.count { it == best } == 1) score.indexOf(best) else null
    }

    private fun allAbove(o: List<String>) = o.indexOfFirst { r("""^(all of the above|all the above|all of these|all the three|all three|all are correct|all of them)""").containsMatchIn(it.trim()) }
    private fun noneAbove(o: List<String>) = o.indexOfFirst { r("""^(none of the above|none of these|none of them|none)""").containsMatchIn(it.trim()) }
    private fun bothOption(o: List<String>) = o.indexOfFirst { r("""^both\b""").containsMatchIn(it.trim()) }

    // ---------- hints (before answering) ----------

    fun hints(q: Question): List<String> {
        if (q.kind == 'f' || q.options.size < 2) return emptyList()
        val neg = isNegative(q.stem)
        val o = q.options
        val out = mutableListOf<String>()
        if (neg) out += "This asks for the NOT correct / EXCEPT one. Every clue below flips: a statement that looks wrong is the one to pick."
        when (kind(q)) {
            Kind.ASSERTION -> {
                out += "Assertion–Reason: check A and R separately first. In APPSC, 'both true and R explains A' is the answer 43 in 100 times, then 'both true, R does not explain A'. Pick 'A true, R false' or 'A false, R true' only when you are sure one sentence is false."
                cluesInStatements(q, neg).take(2).forEach { out += it }
            }
            Kind.MATCH -> {
                out += "Match the following: start from the one pair you are 100% sure of and cross out every option that disagrees. Two sure pairs usually leave one option."
                val ps = o.map { pairs(it) }
                if (ps.all { it.isNotEmpty() }) {
                    val counts = ps.flatten().groupingBy { it }.eachCount()
                    counts.filter { it.value in 2..3 }.maxByOrNull { it.value }?.let { (p, n) ->
                        out += "No sure pair? '$p' appears in $n of the 4 options. A pair repeated like this is right about 82 in 100 times, so start from it."
                    }
                }
            }
            Kind.ORDER -> {
                out += "Putting things in order: don't order everything. Fix the first and the last item; usually only one option has both right."
                val firsts = o.map { seq(it).firstOrNull() }.filterNotNull().groupingBy { it }.eachCount()
                firsts.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.let {
                    out += "${it.value} options put '${it.key}' first. The item most options put first is usually the real first (84 in 100 across exams)."
                }
            }
            Kind.STATEMENTS -> {
                val st = statements(q.stem)
                val labels = st.map { it.first }
                val sets = o.map { optionSet(it, labels) }
                val common = labels.filter { l -> sets.count { it != null && l in it } >= 3 && sets.count { it != null && l in it } < q.options.size && cluesIn(st.toMap()[l] ?: "", q.book).none { c -> c.first.wrong && c.second } }
                if (common.isNotEmpty() && common.size < labels.size && !neg) {
                    val names = if (common.size == 1) "Statement ${common[0]} appears" else "Statements ${common.dropLast(1).joinToString(", ")} and ${common.last()} appear"
                    out += "$names in most of the options. Such statements are true about 79 in 100 times in APPSC: accept ${if (common.size > 1) "them" else "it"} and spend your time on the others."
                }
                out += cluesInStatements(q, neg)
                out += "Find ONE statement you are sure is wrong, then cross out every option that includes it. One sure fact often removes 2 or 3 options."
                out += "No clue at all on a statement? Lean TRUE: about 65 in 100 statements in these questions are true."
            }
            Kind.NUMBERS -> if (!neg) middleNumbers(o)?.let { m ->
                out += "Number options: sort them small to big. The answer is one of the two middle values, ${m.sorted().joinToString(" or ") { letter(it) }}, about 61 in 100 times in APPSC (luck 50)."
            }
            Kind.SENTENCES -> if (!neg) {
                clearlyLongest(o)?.let { out += "Option ${letter(it)} is clearly the longest. In APPSC the clearly longest option is right 37 in 100 times (luck 25): setters add words to make the right answer exact." }
                if (q.book in setOf(3, 4, 5, 6)) familyOption(o)?.let { out += "Tie-breaker: ${letter(it)} shares the most words with the other options. Wrong options are often small changes of the right one." }
                out += cluesInOptions(q, neg)
                if (neg) out += "Odd one out by meaning: three options belong to the same group or are true facts; the one that does not fit is the answer."
                else if (out.isEmpty()) out += "Split the options into two small choices (who / where / when) and answer each on its own. Ask 'Who would do this job?' to drop the ones that don't fit."
            }
            Kind.SHORT -> out += if (neg) "Odd one out by meaning: three options belong to the same group; the one that does not is the answer. Judge by meaning, not by look-alike words." else "This is a name or one-word answer. No trick replaces the fact here, but try 'Who would do this job?' and splitting the options into two small choices to cut the list down."
        }
        allAbove(o).takeIf { it >= 0 }?.let { if (!neg) out += "'All of the above' ${letter(it)} is a strong answer in APPSC (right 40 in 100, luck 25), especially in Science and Economy. Don't cut it without proof." }
        noneAbove(o).takeIf { it >= 0 }?.let { out += "Don't throw away 'None of the above' ${letter(it)}: in APPSC it is right 32 in 100 times, more than luck." }
        if (kind(q) != Kind.STATEMENTS && kind(q) != Kind.ASSERTION && bothOption(o) >= 0 && q.book in setOf(3, 5) && !neg)
            out += "A joined option ('Both…') ${letter(bothOption(o))} is right more often than luck in APPSC ${SUBJECT[q.book]}."
        return out.distinct()
    }

    private fun cluesInStatements(q: Question, neg: Boolean): List<String> {
        val st = if (kind(q) == Kind.ASSERTION) assertionParts(q.stem) else statements(q.stem)
        // a statement found in 3 or 4 options is usually true; if it also carries a WRONG clue, it is the one to check first
        val labels = st.map { it.first }
        val sets = q.options.map { optionSet(it, labels) }
        val common = if (kind(q) == Kind.STATEMENTS && !neg) labels.associateWith { l -> sets.count { it != null && l in it } }.filterValues { it >= 3 && it < q.options.size } else emptyMap()
        return st.mapNotNull { (lab, text) ->
            val cl = cluesIn(text, q.book)
            val wrong = cl.filter { it.first.wrong }.sortedByDescending { it.second }
            val right = cl.filter { !it.first.wrong }
            when {
                lab in common && wrong.any { it.second } -> "Statement $lab is in ${common[lab]} of the ${q.options.size} options (usually true), but it has ${wrong.first().first.label}. Check it first: if it is wrong, it removes ${common[lab]} options at once."
                lab in common -> null
                wrong.isNotEmpty() -> "Statement $lab has ${wrong.first().first.label}: in APPSC ${SUBJECT[q.book]} such statements are often ${if (neg) "the false one you are looking for" else "WRONG"} (${wrong.first().first.proof}${if (!wrong.first().second) ", a weak clue here" else ""})."
                right.isNotEmpty() -> "Statement $lab has ${right.first().first.label}: such statements are usually TRUE (${right.first().first.proof})."
                else -> null
            }
        }.take(4)
    }

    private fun cluesInOptions(q: Question, neg: Boolean): List<String> = q.options.mapIndexedNotNull { i, t ->
        val w = cluesIn(t, q.book).filter { it.first.wrong && it.second && it.first.id != "long" }
        if (w.isEmpty()) null else "Option ${letter(i)} has ${w.first().first.label}, a WRONG clue in ${SUBJECT[q.book]}${if (neg) " (so it may be the one you need)" else ""}."
    }.take(2)

    private fun assertionParts(stem: String): List<Pair<String, String>> {
        val a = Regex("""assertion\s*\(?a\)?\s*[:.-]?\s*(.+?)(?=reason|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(stem)?.groupValues?.get(1)
        val rr = Regex("""reason\s*\(?r\)?\s*[:.-]?\s*(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(stem)?.groupValues?.get(1)
        return listOfNotNull(a?.let { "A" to it.trim() }, rr?.let { "R" to it.trim() })
    }

    // ---------- review (after a wrong answer) ----------

    fun review(q: Question, picked: Int): List<String> {
        if (q.kind != 's' || q.answer < 0 || picked < 0 || picked == q.answer || q.options.size < 2) return emptyList()
        val o = q.options
        val neg = isNegative(q.stem)
        val key = q.answer
        val out = mutableListOf<String>()
        if (neg) out += "This was a NOT / EXCEPT question. The usual slip is marking the first TRUE fact you recognise: circle 'not / except' before reading the options."
        when (kind(q)) {
            Kind.ASSERTION -> {
                val kt = o[key].lowercase(); val pt = o[picked].lowercase()
                val keyBoth = kt.contains("both") || (kt.contains("true") && kt.contains("explanation") && !Regex("""\bfalse\b""").containsMatchIn(kt))
                val keyExplains = keyBoth && !kt.contains("not")
                when {
                    keyExplains -> out += "Assertion–Reason: the key is 'both true and R explains A', the most common APPSC answer (43 in 100). Drop it only when you can prove one sentence false; that did not hold here."
                    keyBoth -> out += "Both A and R were true, but R does not explain A. Ask 'does R answer WHY A happens?' The false-sentence options you might pick are under 1 in 5 in APPSC."
                    else -> out += "Here one sentence really was false, the rarer case in APPSC. Check A and R one at a time for a WRONG clue (only / not / wrong person / wrong date) before accepting 'both true'."
                }
                if (pt.contains("false") && keyBoth) out += "You marked a sentence false, but both were true. When a sentence has no clear WRONG clue, lean TRUE (65 in 100)."
                assertionParts(q.stem).forEach { (lab, t) ->
                    cluesIn(t, q.book).firstOrNull { it.first.wrong && it.second }?.let { if (!keyBoth) out += "Clue: $lab has ${it.first.label} (${it.first.proof})." }
                }
            }
            Kind.MATCH -> {
                val ps = o.map { pairs(it) }
                if (ps.all { it.isNotEmpty() }) {
                    val score = ps.indices.map { i -> ps[i].sumOf { p -> ps.indices.count { j -> j != i && p in ps[j] } } }
                    val best = score.max()
                    if (score[key] == best && score.count { it == best } == 1) out += "The key ${letter(key)} is the option that shares the most pairs with the others. With no sure pair, that option is right 37 in 100 in APPSC (luck 25)."
                    val keyPairs = ps[key] - ps[picked]
                    if (keyPairs.isNotEmpty()) out += "Your pick missed these correct pairs: ${keyPairs.joinToString(", ")}. Start from a pair you are sure of, cross out options that disagree, then use the next sure pair."
                } else out += "Match the following: fix your one sure pair first and cross out every option that disagrees; the rest usually falls out."
            }
            Kind.ORDER -> {
                val firsts = o.map { seq(it).firstOrNull() }
                val top = firsts.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }
                if (top != null && top.value >= 2 && firsts[key] == top.key) out += "Most options put '${top.key}' first, and the key does too. Accept the item most options put first, then decide with the LAST item."
                else out += "Order questions: find only the oldest/first and the newest/last item. Usually just one option has both in the right place."
            }
            Kind.STATEMENTS -> out += reviewStatements(q, picked, neg)
            Kind.NUMBERS -> middleNumbers(o)?.let { m ->
                if (!neg && key in m && picked !in m) out += "The key ${letter(key)} is one of the two middle values. Your pick was an extreme; when unsure, APPSC's answer is a middle number 61 in 100 times."
                else if (key !in m) out += "The key is an extreme value, so the middle-number trick fails here. This one needed the exact figure: note it for revision."
            }
            Kind.SENTENCES -> {
                if (!neg) clearlyLongest(o)?.let { if (it == key) out += "The key ${letter(key)} is the clearly longest option: setters add words to make the right answer exact (right 37 in 100 in APPSC)." }
                if (!neg && q.book in setOf(3, 4, 5, 6)) familyOption(o)?.let { if (it == key) out += "The key ${letter(key)} shares the most words with the other options; the wrong ones are small changes of it." }
                val w = cluesIn(o[picked], q.book).filter { it.first.wrong && it.second && it.first.id != "long" }
                if (!neg && w.isNotEmpty()) out += "Your pick has ${w.first().first.label}, a WRONG clue in ${SUBJECT[q.book]} (${w.first().first.proof})."
                val kw = cluesIn(o[key], q.book).filter { !it.first.wrong }
                if (!neg && kw.isNotEmpty()) out += "The key has ${kw.first().first.label}; such sentences are usually TRUE (${kw.first().first.proof})."
            }
            Kind.SHORT -> {}
        }
        val all = allAbove(o)
        if (all == key && !neg) out += "'All of the above' was the answer. In APPSC it wins 40 in 100 times (luck 25): keep it unless you can prove one option wrong."
        val none = noneAbove(o)
        if (none == key) out += "'None of the above' was the answer. In APPSC it is a real option (right 32 in 100), not a throwaway."
        if (none == picked && none != key && !neg) out += "'None of the above' is a real option in APPSC, but pick it only after checking each option."
        if (out.isEmpty() || (neg && out.size == 1)) out += when (kind(q)) {
            Kind.SHORT -> "This is a name or one-fact question: about half of APPSC questions are like this and no trick helps. It needs the fact; revise it from the notes for this section."
            else -> "No tested trick points to the key here: it needed the fact. Use 'find ONE wrong statement' and 'Who would do this job?' next time, and revise this point."
        }
        return out.distinct()
    }

    private fun reviewStatements(q: Question, picked: Int, neg: Boolean): List<String> {
        val st = statements(q.stem)
        val labels = st.map { it.first }
        val sets = q.options.map { optionSet(it, labels) }
        val ks = sets[q.answer] ?: return emptyList()
        val ps = sets[picked] ?: return emptyList()
        val out = mutableListOf<String>()
        // In a 'which are correct' question the key lists the TRUE statements; in a NOT question it lists the false ones.
        val trueSet = if (neg) labels.toSet() - ks else ks
        val pickTrue = if (neg) labels.toSet() - ps else ps
        val text = st.toMap()
        (pickTrue - trueSet).forEach { lab ->
            val w = cluesIn(text[lab] ?: "", q.book).filter { it.first.wrong }.sortedByDescending { it.second }.firstOrNull()
            out += if (w != null) "You treated statement $lab as true, but it was FALSE. It has ${w.first.label}, a WRONG clue (${w.first.proof})."
            else "You treated statement $lab as true, but it was FALSE. The mistake hides in a small detail (who, when, how much): read each part of the sentence on its own."
        }
        (trueSet - pickTrue).forEach { lab ->
            val n = sets.count { it != null && lab in it }
            val rc = cluesIn(text[lab] ?: "", q.book).firstOrNull { !it.first.wrong }
            out += when {
                n >= 3 && !neg -> "You rejected statement $lab, but it was TRUE. It appears in $n of the 4 options: statements like that are true 79 in 100 times in APPSC."
                rc != null -> "You rejected statement $lab, but it was TRUE. It has ${rc.first.label}; such statements are usually true (${rc.first.proof})."
                else -> "You rejected statement $lab, but it was TRUE. With no WRONG clue, lean TRUE: 65 in 100 statements are true."
            }
        }
        if (out.isNotEmpty()) out += "Method: find ONE statement you are sure of, then cross out every option that disagrees with it."
        return out
    }

    /** True if the guide has something concrete to say before answering (used to show the hint button). */
    fun hasHint(q: Question) = hints(q).isNotEmpty()
}
