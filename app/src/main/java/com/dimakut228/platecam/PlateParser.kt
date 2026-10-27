package com.dimakut228.platecam

enum class Country(val label: String) {
    RU("РФ"),
    BY("РБ"),
    UNKNOWN("РФ/РБ")
}

data class PlateRead(
    val canonical: String,
    val display: String,
    val country: Country,
    val kind: String,
    val confidence: Float,
    val raw: String
)

data class PlateHint(
    val country: Country = Country.UNKNOWN,
    val yellow: Boolean = false,
    val text: String = ""
)

object PlateParser {

    private data class Pattern(
        val regex: Regex,
        val country: Country,
        val kind: String,
        val priority: Int,
        val format: (MatchResult) -> String
    )

    private data class Variant(val text: String, val edits: Int)

    private data class Candidate(val read: PlateRead, val score: Float)

    private val patterns = listOf(
        Pattern(
            Regex("([ABEKMHOPCTYX])(\\d{3})([ABEKMHOPCTYX]{2})(\\d{2,3})"),
            Country.RU,
            "легковой",
            80
        ) { m -> cyr(m.groupValues[1] + m.groupValues[2] + m.groupValues[3]) + " " + m.groupValues[4] },
        Pattern(
            Regex("([ABEKMHOPCTYX]{2})(\\d{3})(\\d{2,3})"),
            Country.RU,
            "такси",
            70
        ) { m -> cyr(m.groupValues[1] + m.groupValues[2]) + " " + m.groupValues[3] },
        Pattern(
            Regex("([ABEKMHOPCTYX]{2})(\\d{4})(\\d{2,3})"),
            Country.RU,
            "прицеп",
            74
        ) { m -> cyr(m.groupValues[1]) + " " + m.groupValues[2] + " " + m.groupValues[3] },
        Pattern(
            Regex("(\\d{4})([ABEKMHOPCTYX]{2})(\\d{2})"),
            Country.RU,
            "мото",
            60
        ) { m -> m.groupValues[1] + " " + cyr(m.groupValues[2]) + " " + m.groupValues[3] },
        Pattern(
            Regex("([ABEKMHOPCTYX])(\\d{4})(\\d{2,3})"),
            Country.RU,
            "полиция",
            65
        ) { m -> cyr(m.groupValues[1]) + " " + m.groupValues[2] + " " + m.groupValues[3] },
        Pattern(
            Regex("(\\d{4})([ABEKMHOPCTYX]{2})"),
            Country.RU,
            "военный",
            55
        ) { m -> m.groupValues[1] + " " + cyr(m.groupValues[2]) },
        Pattern(
            Regex("(CD|CC|D|T)(\\d{3})(\\d{2,3})"),
            Country.RU,
            "дипломатический",
            75
        ) { m -> m.groupValues[1] + " " + m.groupValues[2] + " " + m.groupValues[3] },
        Pattern(
            Regex("(T)(\\d{3})([ABEKMHOPCTYX]{2})(\\d{2,3})"),
            Country.RU,
            "транзит",
            68
        ) { m -> "Т " + m.groupValues[2] + " " + cyr(m.groupValues[3]) + " " + m.groupValues[4] },
        Pattern(
            Regex("([KC])(\\d{3})([ABEKMHOPCTYX]{2})(\\d{2,3})"),
            Country.RU,
            "ретро/спорт",
            62
        ) { m -> cyr(m.groupValues[1]) + " " + m.groupValues[2] + " " + cyr(m.groupValues[3]) + " " + m.groupValues[4] },
        Pattern(
            Regex("(T)(\\d{4})(\\d{2,3})"),
            Country.RU,
            "временный",
            66
        ) { m -> "Т " + m.groupValues[2] + " " + m.groupValues[3] },
        Pattern(
            Regex("(\\d{4})([ABEIKMHOPCTX]{2})([0-8])"),
            Country.BY,
            "легковой",
            80
        ) { m -> m.groupValues[1] + " " + cyr(m.groupValues[2]) + "-" + m.groupValues[3] },
        Pattern(
            Regex("(E)(\\d{3})([ABEIKMHOPCTX]{2})([0-8])"),
            Country.BY,
            "электро",
            82
        ) { m -> cyr("E" + m.groupValues[2]) + " " + cyr(m.groupValues[3]) + "-" + m.groupValues[4] },
        Pattern(
            Regex("([ABEIKMHOPCTX]{2})(\\d{4})([0-8])"),
            Country.BY,
            "грузовой",
            72
        ) { m -> cyr(m.groupValues[1]) + " " + m.groupValues[2] + "-" + m.groupValues[3] },
        Pattern(
            Regex("([ABEIKMHOPCTX]{2})(E)(\\d{3})([0-8])"),
            Country.BY,
            "электрогрузовой",
            74
        ) { m -> cyr(m.groupValues[1]) + " " + cyr("E" + m.groupValues[3]) + "-" + m.groupValues[4] },
        Pattern(
            Regex("([ABEIKMHOPCTX])(\\d{4})([ABEIKMHOPCTX])([0-8])"),
            Country.BY,
            "прицеп",
            74
        ) { m -> cyr(m.groupValues[1] + m.groupValues[2] + m.groupValues[3]) + "-" + m.groupValues[4] },
        Pattern(
            Regex("(CD|CC)(\\d{4})([0-8])"),
            Country.BY,
            "дипломатический",
            76
        ) { m -> m.groupValues[1] + " " + m.groupValues[2] + "-" + m.groupValues[3] },
        Pattern(
            Regex("(\\d)(TAX)(\\d{4})"),
            Country.BY,
            "такси",
            78
        ) { m -> m.groupValues[1] + " " + m.groupValues[2] + " " + m.groupValues[3] },
        Pattern(
            Regex("(\\d)([ABEIKMHOPCTX]{2})(T)(\\d{4})"),
            Country.BY,
            "временный",
            70
        ) { m -> m.groupValues[1] + cyr(m.groupValues[2]) + " " + cyr(m.groupValues[3]) + " " + m.groupValues[4] },
        Pattern(
            Regex("([ABEIKMHOPCTX]{2})(\\d)(\\d{4})"),
            Country.BY,
            "трактор",
            55
        ) { m -> cyr(m.groupValues[1]) + "-" + m.groupValues[2] + "/" + m.groupValues[3] },
        Pattern(
            Regex("(\\d{4})([ABEIKMHOPCTX]{2})"),
            Country.BY,
            "военный",
            50
        ) { m -> m.groupValues[1] + " " + cyr(m.groupValues[2]) },
        Pattern(
            Regex("(M)(\\d{4})"),
            Country.BY,
            "иностранный",
            45
        ) { m -> "М " + m.groupValues[2] }
    )

    fun parse(
        raw: String,
        confidence: Float,
        hint: PlateHint = PlateHint(),
        maxVariants: Int = 700
    ): PlateRead? {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null
        val upperRaw = raw.uppercase()
        val textCountry = when {
            hint.country != Country.UNKNOWN -> hint.country
            upperRaw.contains("BY") -> Country.BY
            upperRaw.contains("RUS") -> Country.RU
            else -> Country.UNKNOWN
        }
        val active = if (textCountry == Country.UNKNOWN) patterns else patterns.filter { it.country == textCountry }
        val variants = variants(normalized, maxVariants)
        val candidates = ArrayList<Candidate>(32)

        for (variant in variants) {
            for (pattern in active) {
                for (match in pattern.regex.findAll(variant.text)) {
                    val matchLength = match.value.length
                    if (matchLength < 6) continue
                    val coverage = matchLength.toFloat() / variant.text.length.coerceAtLeast(1)
                    val adjusted = (confidence - variant.edits * 0.07f).coerceIn(0f, 1f)
                    var score = adjusted * 0.55f + coverage * 0.22f + pattern.priority * 0.0015f - variant.edits * 0.02f
                    if (matchLength == variant.text.length) score += 0.05f
                    if (textCountry == pattern.country) score += 0.06f
                    if (pattern.country == Country.RU && hint.yellow && pattern.kind == "такси") score += 0.08f
                    if (pattern.country == Country.BY && !hint.yellow) score += 0.02f
                    if (pattern.country == Country.RU && !hint.yellow) score += 0.01f
                    val read = PlateRead(
                        canonical = match.value,
                        display = pattern.format(match),
                        country = pattern.country,
                        kind = pattern.kind,
                        confidence = adjusted,
                        raw = raw
                    )
                    candidates.add(Candidate(read, score))
                }
            }
        }

        val sorted = candidates.sortedByDescending { it.score }
        val best = sorted.firstOrNull()
        if (best != null) {
            var country = best.read.country
            var kind = best.read.kind
            if (textCountry == Country.UNKNOWN && !hint.yellow) {
                val second = sorted.firstOrNull { it.read.country != best.read.country }
                if (second != null && best.score - second.score < 0.035f) {
                    country = Country.UNKNOWN
                    kind = best.read.kind
                }
            }
            val transitAmbiguous = best.read.kind == "легковой" && best.read.canonical.startsWith("T") &&
                sorted.any { it.read.canonical == best.read.canonical && it.read.kind == "транзит" }
            if (transitAmbiguous) kind = "легковой/транзит"
            return best.read.copy(country = country, kind = kind)
        }

        if (normalized.length !in 6..10 || confidence < 0.30f) return null
        val digits = normalized.count { it in '0'..'9' }
        val letters = normalized.count { it in 'A'..'Z' }
        if (digits < 2 || letters < 1) return null
        val country = when {
            textCountry != Country.UNKNOWN -> textCountry
            hint.yellow -> Country.RU
            else -> Country.UNKNOWN
        }
        val display = if (normalized.length >= 7) {
            normalized.dropLast(2) + " " + normalized.takeLast(2)
        } else {
            normalized
        }
        return PlateRead(
            canonical = normalized,
            display = display,
            country = country,
            kind = "нестандартный",
            confidence = confidence,
            raw = raw
        )
    }

    private fun normalize(raw: String): String {
        val out = StringBuilder(raw.length)
        for (ch in raw.uppercase()) {
            val c = when (ch) {
                'А' -> 'A'
                'В' -> 'B'
                'Е' -> 'E'
                'К' -> 'K'
                'М' -> 'M'
                'Н' -> 'H'
                'О' -> 'O'
                'Р' -> 'P'
                'С' -> 'C'
                'Т' -> 'T'
                'У' -> 'Y'
                'Х' -> 'X'
                'І' -> 'I'
                'Ї' -> 'I'
                else -> ch
            }
            if (c in 'A'..'Z' || c in '0'..'9') out.append(c)
        }
        return out.toString()
    }

    private fun variants(source: String, maxVariants: Int): List<Variant> {
        val limit = maxVariants.coerceIn(1, 700)
        var current = listOf(Variant("", 0))
        for (ch in source) {
            val alternatives = alternatives(ch)
            val next = ArrayList<Variant>(current.size * alternatives.length)
            for (variant in current) {
                for (alternative in alternatives) {
                    val edits = variant.edits + if (alternative != ch) 1 else 0
                    if (edits > 2) continue
                    next.add(Variant(variant.text + alternative, edits))
                }
            }
            current = if (next.isEmpty()) {
                listOf(Variant(source, 0))
            } else {
                next.sortedBy { it.edits }.take(limit)
            }
        }
        return current
    }

    private fun alternatives(c: Char): String = when (c) {
        'A' -> "A4"
        '4' -> "4A"
        'B' -> "B8"
        '8' -> "8B"
        'C' -> "C6"
        '6' -> "6C"
        'D' -> "D0"
        'E' -> "E3"
        '3' -> "3E"
        'I' -> "I1"
        '1' -> "1I"
        'O' -> "O0"
        '0' -> "0OD"
        'P' -> "P9"
        '9' -> "9P"
        'T' -> "T7"
        '7' -> "7T"
        '2' -> "2Z"
        'Z' -> "Z2"
        '5' -> "5S"
        'S' -> "S5"
        else -> c.toString()
    }

    private fun cyr(value: String): String {
        val out = StringBuilder(value.length)
        for (c in value) {
            out.append(
                when (c) {
                    'A' -> 'А'
                    'B' -> 'В'
                    'E' -> 'Е'
                    'K' -> 'К'
                    'M' -> 'М'
                    'H' -> 'Н'
                    'O' -> 'О'
                    'P' -> 'Р'
                    'C' -> 'С'
                    'T' -> 'Т'
                    'Y' -> 'У'
                    'X' -> 'Х'
                    'I' -> 'І'
                    else -> c
                }
            )
        }
        return out.toString()
    }
}
