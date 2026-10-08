package com.appsc.prep.data

/**
 * Turns a selection into what to look for in the notes, whichever way it is written:
 * s.144 / sec144 / Sec. 144 / Section 144, Art 21 / Article 21, 84th Amendment / Eighty-fourth Amendment,
 * 7th Schedule / Seventh Schedule; anything else as a whole word or phrase (spaces, hyphens either way).
 */
object NotesTerms {
    /** [display]: how the term is named on the card ("Section 144"); [regex]: finds it in the notes. */
    data class Term(val display: String, val regex: Regex)

    private val ORDINAL_WORDS = listOf(
        "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
        "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth",
        "nineteenth", "twentieth",
    )
    private val TENS = mapOf(
        2 to "twenty", 3 to "thirty", 4 to "forty", 5 to "fifty", 6 to "sixty", 7 to "seventy", 8 to "eighty", 9 to "ninety",
    )
    private val TENS_ORDINAL = mapOf(
        2 to "twentieth", 3 to "thirtieth", 4 to "fortieth", 5 to "fiftieth", 6 to "sixtieth", 7 to "seventieth",
        8 to "eightieth", 9 to "ninetieth",
    )

    /** 84 -> "eighty-fourth" (1-99; 100+ only as digits). */
    private fun ordinalWord(n: Int): String? = when {
        n in 1..20 -> ORDINAL_WORDS[n - 1]
        n in 21..99 && n % 10 == 0 -> TENS_ORDINAL[n / 10]
        n in 21..99 -> "${TENS[n / 10]}-${ORDINAL_WORDS[n % 10 - 1]}"
        else -> null
    }

    private fun wordToNumber(w: String): Int? {
        val x = w.lowercase().replace(' ', '-')
        for (n in 1..99) if (ordinalWord(n) == x) return n
        return null
    }

    private fun suffix(n: Int) = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    /** "84" or "84th" or "eighty-fourth" in the notes. */
    private fun ordinalRegex(n: Int): String {
        val word = ordinalWord(n)?.replace("-", "[\\s-]")
        return if (word != null) "(?:${n}(?:st|nd|rd|th)?|$word)" else "${n}(?:st|nd|rd|th)?"
    }

    fun parse(selection: String): Term? {
        val s = selection.trim().trim { !it.isLetterOrDigit() && it != '.' }.replace(Regex("""\s+"""), " ")
        if (s.length < 2 || s.length > 60) return null
        val opt = RegexOption.IGNORE_CASE

        // Section 144, s.144, sec144, u/s 144, Sec. 66A
        Regex("""^(?:u/s|s|ss|secs?|sections?)\.?\s*(\d+[A-Z]?(?:\(\d+\))?)$""", opt).matchEntire(s)?.let { m ->
            val num = m.groupValues[1].uppercase()
            val n = Regex.escape(num)
            return Term("Section $num", Regex("""(?<![A-Za-z])(?:u/s|ss?|secs?|sections?)\.?\s*$n(?![0-9A-Za-z])""", opt))
        }
        // Article 21, Art 21, Arts. 14
        Regex("""^(?:art|arts|article|articles)\.?\s*(\d+[A-Z]?(?:\(\d+\))?)$""", opt).matchEntire(s)?.let { m ->
            val num = m.groupValues[1].uppercase()
            val n = Regex.escape(num)
            return Term("Article $num", Regex("""(?<![A-Za-z])(?:art|arts|articles?)\.?\s*$n(?![0-9A-Za-z])""", opt))
        }
        // 84th Amendment, 84th Constitutional Amendment Act, Eighty-fourth Amendment, Amendment 84
        val amend = Regex("""^(\d{1,3})(?:st|nd|rd|th)?\s+(?:constitutional\s+|constitution\s+)?amend(?:ment)?(?:\s+act)?$""", opt).matchEntire(s)?.groupValues?.get(1)?.toInt()
            ?: Regex("""^([a-z]+(?:[\s-][a-z]+)?)\s+(?:constitutional\s+)?amend(?:ment)?(?:\s+act)?$""", opt).matchEntire(s)?.groupValues?.get(1)?.let(::wordToNumber)
            ?: Regex("""^amend(?:ment)?\s*(?:no\.?\s*)?(\d{1,3})$""", opt).matchEntire(s)?.groupValues?.get(1)?.toInt()
        if (amend != null) {
            return Term("$amend${suffix(amend)} Amendment", Regex("""(?<![0-9A-Za-z])${ordinalRegex(amend)}\s+(?:\(?Constitution(?:al)?\)?\s+)?Amendment""", opt))
        }
        // 7th Schedule, Seventh Schedule, 5th Five-Year Plan ... (ordinal + noun)
        val ord = Regex("""^(\d{1,3})(?:st|nd|rd|th)\s+(.+)$""", opt).matchEntire(s)?.let { it.groupValues[1].toInt() to it.groupValues[2] }
            ?: Regex("""^([a-z]+(?:-[a-z]+)?)\s+(.+)$""", opt).matchEntire(s)?.let { m -> wordToNumber(m.groupValues[1])?.let { it to m.groupValues[2] } }
        if (ord != null) {
            val (n, noun) = ord
            val rest = flexible(noun)
            return Term("$n${suffix(n)} ${noun.replaceFirstChar { it.uppercase() }}", Regex("""(?<![0-9A-Za-z])${ordinalRegex(n)}\s+$rest(?![A-Za-z])""", opt))
        }
        // anything else: the word or phrase, spaces/hyphens/dots either way
        return Term(s, Regex("""(?<![0-9A-Za-z])${flexible(s)}(?![0-9A-Za-z])""", opt))
    }

    /** "Lok Sabha" also finds "Lok-Sabha"; "e-governance" also "e governance"; "J&K" also "J and K". */
    private fun flexible(s: String): String = s.split(Regex("""[\s\-]+""")).filter { it.isNotEmpty() }
        .joinToString("""[\s\-]*""") { w ->
            w.map { c ->
                when {
                    c == '&' -> """(?:&|\s*and\s*)"""
                    c.isLetterOrDigit() -> c.toString()
                    else -> "\\$c"
                }
            }.joinToString("")
        }
}
