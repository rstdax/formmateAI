package com.rst.heyrst.engine

import com.rst.heyrst.model.FieldKind
import com.rst.heyrst.model.FormField
import com.rst.heyrst.model.OptionItem
import com.rst.heyrst.model.SemanticType
import com.rst.heyrst.model.VoiceCommand
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-fidelity Kotlin port of Formmate AI Core Domain Logic (formmate-core.js).
 * Deterministic parsing, multi-tier option matching, negation-first boolean parsing,
 * spoken date/email/phone normalization, and natural bilingual human dialogue generation.
 */
object FormmateEngine {

    // --- Normalization Helpers ---

    fun normalizeText(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val lower = value.lowercase(Locale.ROOT)
        // NFD decomposition to separate diacritics
        val nfd = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val stripped = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val nfc = Normalizer.normalize(stripped, Normalizer.Form.NFC)
        return nfc
            .replace(Regex("['’]"), "") // e.g. don't -> dont
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}@.+/-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun cleanPunctuation(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value
            .replace(Regex("^[\\s,.;:!?\"'()\\[\\]{}]+"), "")
            .replace(Regex("[\\s,.;:!?\"'()\\[\\]{}]+$"), "")
            .trim()
    }

    // --- Word & Number Mappings ---

    private val SMALL_NUMBERS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    private val ORDINALS = mapOf(
        "first" to 1, "1st" to 1,
        "second" to 2, "2nd" to 2,
        "third" to 3, "3rd" to 3,
        "fourth" to 4, "4th" to 4,
        "fifth" to 5, "5th" to 5,
        "sixth" to 6, "6th" to 6,
        "seventh" to 7, "7th" to 7,
        "eighth" to 8, "8th" to 8,
        "ninth" to 9, "9th" to 9,
        "tenth" to 10, "10th" to 10,
        "eleventh" to 11, "11th" to 11,
        "twelfth" to 12, "12th" to 12,
        "thirteenth" to 13, "13th" to 13,
        "fourteenth" to 14, "14th" to 14,
        "fifteenth" to 15, "15th" to 15,
        "sixteenth" to 16, "16th" to 16,
        "seventeenth" to 17, "17th" to 17,
        "eighteenth" to 18, "18th" to 18,
        "nineteenth" to 19, "19th" to 19,
        "twentieth" to 20, "20th" to 20,
        "twenty first" to 21, "twenty-first" to 21, "21st" to 21,
        "twenty second" to 22, "twenty-second" to 22, "22nd" to 22,
        "twenty third" to 23, "twenty-third" to 23, "23rd" to 23,
        "twenty fourth" to 24, "twenty-fourth" to 24, "24th" to 24,
        "twenty fifth" to 25, "twenty-fifth" to 25, "25th" to 25,
        "twenty sixth" to 26, "twenty-sixth" to 26, "26th" to 26,
        "twenty seventh" to 27, "twenty-seventh" to 27, "27th" to 27,
        "twenty eighth" to 28, "twenty-eighth" to 28, "28th" to 28,
        "twenty ninth" to 29, "twenty-ninth" to 29, "29th" to 29,
        "thirtieth" to 30, "30th" to 30,
        "thirty first" to 31, "thirty-first" to 31, "31st" to 31,
        // Hindi ordinals
        "पहला" to 1, "pehla" to 1, "pahla" to 1,
        "दूसरा" to 2, "doosra" to 2, "dusra" to 2,
        "तीसरा" to 3, "teesra" to 3, "tisra" to 3,
        "चौथा" to 4, "chautha" to 4,
        "पांचवा" to 5, "paanchwa" to 5, "panchwa" to 5
    )

    private val MONTHS = mapOf(
        "january" to 1, "jan" to 1,
        "february" to 2, "feb" to 2,
        "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5,
        "june" to 6, "jun" to 6,
        "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12
    )

    private val STATE_ALIASES = mapOf(
        "as" to "assam",
        "ml" to "meghalaya",
        "mn" to "manipur",
        "tr" to "tripura",
        "ap" to "arunachal pradesh",
        "ar" to "arunachal pradesh",
        "wb" to "west bengal",
        "bengal" to "west bengal",
        "assam" to "assam",
        "meghalaya" to "meghalaya",
        "manipur" to "manipur",
        "tripura" to "tripura",
        "arunachal" to "arunachal pradesh"
    )

    // --- Conversational Framing Stripper ---

    fun stripConversationalFraming(text: String, semanticType: SemanticType = SemanticType.GENERAL): String {
        var cleaned = normalizeText(text)

        // Strip polite openers and closers
        cleaned = cleaned
            .replace(Regex("^(?:please|kindly|could you please|can you|would you|i want to|let us|lets)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:please|kindly|thank you|thanks|as well)$", RegexOption.IGNORE_CASE), "")
            .trim()

        val prefixes = listOf(
            Regex("^(?:my\\s+full\\s+name\\s+is|my\\s+name\\s+is|full\\s+name\\s+is|name\\s+is|this\\s+is|i\\s+am\\s+a|i\\s+am|myself|it\\s+is)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+state\\s+is|the\\s+state\\s+is|state\\s+is|my\\s+state\\s+of\\s+residence\\s+is|i\\s+live\\s+in|i\\s+reside\\s+in|i\\s+belong\\s+to|i\\s+am\\s+from|from|state)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+district\\s+is|district\\s+is|my\\s+city\\s+is|city\\s+is)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+address\\s+is|address\\s+is|residential\\s+address\\s+is)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+gender\\s+is|gender\\s+is|i\\s+am\\s+a\\s+candidate|i\\s+am\\s+a|i\\s+am|im\\s+a|im)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+date\\s+of\\s+birth\\s+is|date\\s+of\\s+birth\\s+is|my\\s+dob\\s+is|dob\\s+is|born\\s+on|born)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+email\\s+is|my\\s+email\\s+address\\s+is|email\\s+is|email\\s+address\\s+is)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+phone\\s+number\\s+is|phone\\s+is|my\\s+mobile\\s+number\\s+is|mobile\\s+is|contact\\s+is|number\\s+is)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+pin\\s+code\\s+is|pin\\s+code\\s+is|pin\\s+is|postal\\s+code\\s+is|zip\\s+is)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:my\\s+preferred\\s+course\\s+is|preferred\\s+course\\s+is|course\\s+is|i\\s+want\\s+to\\s+study|i\\s+choose|choose|select|i\\s+prefer)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:i\\s+choose|choose|select|option|pick|go\\s+with|set\\s+it\\s+to|make\\s+it)\\s+", RegexOption.IGNORE_CASE)
        )

        for (prefix in prefixes) {
            cleaned = cleaned.replace(prefix, "").trim()
        }

        // Strip standalone leading articles
        cleaned = cleaned.replace(Regex("^(?:a|an|the)\\s+", RegexOption.IGNORE_CASE), "").trim()
        // Strip common trailing words
        cleaned = cleaned.replace(Regex("\\s+(?:state|option|please|candidate)$", RegexOption.IGNORE_CASE), "").trim()

        return cleanPunctuation(cleaned)
    }

    // --- Ordinal Selection Parser ---

    data class OrdinalResult(val index: Int = 0, val isLast: Boolean = false)

    fun parseOrdinal(rawInput: String): OrdinalResult? {
        val text = normalizeText(rawInput)
        if (text.isEmpty()) return null

        if (Regex("^(?:the\\s+)?last(?:\\s+option|\\s+one)?$", RegexOption.IGNORE_CASE).matches(text)) {
            return OrdinalResult(isLast = true)
        }

        ORDINALS[text]?.let { return OrdinalResult(index = it) }

        val patternMatch = Regex("^(?:option|choice|number|item)\\s+([a-z0-9-]+)$", RegexOption.IGNORE_CASE).find(text)
        if (patternMatch != null) {
            val token = patternMatch.groupValues[1]
            token.toIntOrNull()?.let { if (it > 0) return OrdinalResult(index = it) }
            SMALL_NUMBERS[token]?.let { return OrdinalResult(index = it) }
            ORDINALS[token]?.let { return OrdinalResult(index = it) }
        }

        val ordinalPhrase = Regex("\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th)\\b", RegexOption.IGNORE_CASE).find(text)
        if (ordinalPhrase != null && (text.contains("option") || text.contains("choice") || text.contains("one") || text.contains("select"))) {
            val ord = ordinalPhrase.groupValues[1].lowercase(Locale.ROOT)
            ORDINALS[ord]?.let { return OrdinalResult(index = it) }
        }

        return null
    }

    // --- 6-Tier Option Matcher ---

    data class OptionMatchResult(
        val matched: OptionItem? = null,
        val tier: Int = 0,
        val ambiguous: Boolean = false,
        val candidates: List<OptionItem> = emptyList(),
        val error: String? = null
    )

    fun matchOption(options: List<OptionItem>, rawInput: String, semanticType: SemanticType = SemanticType.GENERAL): OptionMatchResult {
        if (options.isEmpty()) {
            return OptionMatchResult(error = "No options are available for this field.")
        }
        val raw = rawInput.trim()
        if (raw.isEmpty()) {
            return OptionMatchResult(error = "The provided answer is empty.")
        }

        val normInput = normalizeText(raw)
        val strippedInput = stripConversationalFraming(raw, semanticType)

        // Filter out non-selectable placeholders
        val selectable = options.filter { o ->
            val v = o.value.trim()
            val l = o.label.trim().lowercase(Locale.ROOT)
            if (o.disabled) return@filter false
            if (v.isEmpty() && (l.startsWith("choose") || l.startsWith("select") || l.startsWith("--") || l.isEmpty())) {
                return@filter false
            }
            true
        }
        val activeList = if (selectable.isNotEmpty()) selectable else options

        data class Prep(val original: OptionItem, val normLabel: String, val normValue: String)
        val prepared = activeList.map { Prep(it, normalizeText(it.label), normalizeText(it.value)) }

        // Tier 1: Exact normalized label
        prepared.firstOrNull { it.normLabel == normInput }?.let {
            return OptionMatchResult(matched = it.original, tier = 1)
        }

        // Tier 2: Exact normalized value
        prepared.firstOrNull { it.normValue == normInput && it.normValue.isNotEmpty() }?.let {
            return OptionMatchResult(matched = it.original, tier = 2)
        }

        // Tier 3: Match after stripping conversational framing
        if (strippedInput.isNotEmpty() && strippedInput != normInput) {
            prepared.firstOrNull { it.normLabel == strippedInput }?.let {
                return OptionMatchResult(matched = it.original, tier = 3)
            }
            prepared.firstOrNull { it.normValue == strippedInput && it.normValue.isNotEmpty() }?.let {
                return OptionMatchResult(matched = it.original, tier = 3)
            }
        }

        // Tier 4: Unambiguous aliases
        val targetAlias = STATE_ALIASES[strippedInput] ?: STATE_ALIASES[normInput]
        if (targetAlias != null) {
            prepared.firstOrNull { it.normLabel == targetAlias || it.normValue == targetAlias }?.let {
                return OptionMatchResult(matched = it.original, tier = 4)
            }
        }

        // Tier 5: Explicit ordinal selection
        val ordinal = parseOrdinal(normInput)
        if (ordinal != null) {
            if (ordinal.isLast) {
                return OptionMatchResult(matched = activeList.last(), tier = 5)
            }
            val targetIndex = ordinal.index - 1
            if (targetIndex in activeList.indices) {
                return OptionMatchResult(matched = activeList[targetIndex], tier = 5)
            }
            return OptionMatchResult(error = "Option ${ordinal.index} is not available. There are ${activeList.size} selectable options.")
        }

        // Tier 6: Substring / keyword match
        val query = if (strippedInput.isNotEmpty()) strippedInput else normInput
        if (query.length >= 3) {
            val candidates = prepared.filter { p ->
                val labelWords = p.normLabel.split(Regex("\\s+"))
                val queryWords = query.split(Regex("\\s+"))
                val wordMatch = queryWords.any { qw -> labelWords.contains(qw) && qw.length >= 3 }
                val includes = p.normLabel.contains(query) || query.contains(p.normLabel)
                wordMatch || includes
            }

            if (candidates.size == 1) {
                return OptionMatchResult(matched = candidates[0].original, tier = 6)
            }
            if (candidates.size > 1) {
                return OptionMatchResult(
                    ambiguous = true,
                    candidates = candidates.map { it.original },
                    error = "Multiple options match \"$raw\": ${candidates.joinToString { it.original.label }}."
                )
            }
        }

        val displayList = activeList.take(6).joinToString { it.label }
        return OptionMatchResult(error = "I couldn't match \"$raw\" to an available option. Options include: $displayList.")
    }

    // --- Checkbox Negation-First Boolean Parser ---

    fun parseBooleanAnswer(rawInput: String?): Boolean? {
        val text = normalizeText(rawInput)
        if (text.isEmpty()) return null

        // 1. Explicit Negation Patterns (Priority)
        val negativePattern = Pattern.compile(
            "(?:\\b(no|nope|not|do\\s+not\\s+agree|dont\\s+agree|disagree|disagreed|reject|declined?|refuse|never|nah|unchecked|uncheck|nahin|nahi|na|mat)\\b|(?:नहीं|ना|असहमत|अस्वीकार|सहमति\\s+नहीं))",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
        if (negativePattern.matcher(text).find()) {
            return false
        }

        // 2. Affirmative Patterns
        val affirmativePattern = Pattern.compile(
            "(?:\\b(yes|yeah|yep|agree|agreed|accept|accepted|checked|check|confirm|confirmed|true|haan|correct|sure|proceed|ok|okay|fine|theek|thik|sahi)\\b|(?:हाँ|हाँजी|हाँ\\s*जी|सहमत|स्वीकार|सही|ठीक|अवश्य))",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
        if (affirmativePattern.matcher(text).find()) {
            return true
        }

        return null
    }

    // --- Spoken Date Normalizer -> ISO YYYY-MM-DD ---

    fun parseSpokenDate(rawInput: String?): String? {
        val raw = rawInput?.trim() ?: return null
        if (raw.isEmpty()) return null

        // Standard ISO already: YYYY-MM-DD
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw)) {
            val parts = raw.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3 && parts[1] in 1..12 && parts[2] in 1..31) return raw
        }

        // Explicit DD/MM/YYYY or DD-MM-YYYY
        val dmy = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(raw)
        if (dmy != null) {
            val d = dmy.groupValues[1].toInt()
            val m = dmy.groupValues[2].toInt()
            val y = dmy.groupValues[3].toInt()
            if (d in 1..31 && m in 1..12 && y in 1900..2100) {
                return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d)
            }
        }

        var text = normalizeText(raw)
        text = text.replace(Regex("^(?:born\\s+on|born|my\\s+date\\s+of\\s+birth\\s+is|date\\s+of\\s+birth\\s+is|dob\\s+is|date\\s+is)\\s+", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("^(?:the\\s+)?", RegexOption.IGNORE_CASE), "").replace(Regex("\\s+(?:please)$", RegexOption.IGNORE_CASE), "")

        // Year extraction
        var year: Int? = null
        val yearMatch = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(text)
        if (yearMatch != null) {
            year = yearMatch.groupValues[1].toInt()
            text = text.replace(yearMatch.value, " ").trim()
        } else if (text.contains("two thousand and") || text.contains("two thousand")) {
            val sub = text.replace("two thousand and", "two thousand").substringAfter("two thousand").trim()
            if (sub.isNotEmpty() && SMALL_NUMBERS.containsKey(sub)) {
                year = 2000 + SMALL_NUMBERS.getValue(sub)
            } else if (sub.isEmpty()) {
                year = 2000
            }
            text = text.replace(Regex("two\\s+thousand(?:\\s+and)?(?:\\s+[a-z]+)?"), " ").trim()
        } else {
            val compoundYear = Regex("\\b(nineteen|twenty)\\s+([a-z]+(?:\\s+[a-z]+)?)\\b").find(text)
            if (compoundYear != null) {
                val century = if (compoundYear.groupValues[1] == "nineteen") 1900 else 2000
                val words = compoundYear.groupValues[2].split(Regex("\\s+"))
                var rem = 0
                for (w in words) {
                    SMALL_NUMBERS[w]?.let { rem += it }
                }
                if (rem in 1..99) {
                    year = century + rem
                    text = text.replace(compoundYear.value, " ").trim()
                }
            }
        }

        // Month extraction
        var month: Int? = null
        for ((mName, mNum) in MONTHS) {
            if (Regex("\\b$mName\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                month = mNum
                text = text.replace(Regex("\\b$mName\\b", RegexOption.IGNORE_CASE), " ").trim()
                break
            }
        }

        // Day extraction
        var day: Int? = null
        val digitDay = Regex("\\b([0-3]?\\d)(?:st|nd|rd|th)?\\b").find(text)
        if (digitDay != null) {
            val v = digitDay.groupValues[1].toInt()
            if (v in 1..31) day = v
        }

        if (day == null) {
            for ((ordName, ordNum) in ORDINALS) {
                if (Regex("\\b$ordName\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                    day = ordNum
                    break
                }
            }
        }

        if (day == null) {
            val compound = Regex("\\b(twenty|thirty)\\s+([a-z]+)\\b").find(text)
            if (compound != null) {
                val part1 = SMALL_NUMBERS[compound.groupValues[1]]
                val part2 = SMALL_NUMBERS[compound.groupValues[2]]
                if (part1 != null && part2 != null) {
                    val sum = part1 + part2
                    if (sum in 1..31) day = sum
                }
            }
        }

        if (day == null) {
            for ((numName, numVal) in SMALL_NUMBERS) {
                if (numVal in 1..31 && Regex("\\b$numName\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                    day = numVal
                    break
                }
            }
        }

        if (day != null && month != null && year != null) {
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
        }

        return null
    }

    // --- Spoken Email Normalizer ---

    fun parseSpokenEmail(rawInput: String?): String? {
        val text = rawInput?.trim() ?: return null
        if (text.isEmpty()) return null

        val converted = text.lowercase(Locale.ROOT)
            .replace(Regex("\\s+at\\s+"), "@")
            .replace(Regex("\\s+dot\\s+"), ".")
            .replace(Regex("\\s+underscore\\s+"), "_")
            .replace(Regex("\\s+dash\\s+"), "-")
            .replace(Regex("\\s+"), "")

        val match = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}").find(converted)
        return match?.value?.replace(Regex("[.,;]+$"), "")
    }

    // --- Spoken Phone Normalizer ---

    fun parseSpokenPhone(rawInput: String?): String? {
        var text = normalizeText(rawInput)
        if (text.isEmpty()) return null

        val digitWords = mapOf(
            "zero" to "0", "oh" to "0", "one" to "1", "two" to "2", "three" to "3",
            "four" to "4", "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9"
        )

        // Handle "double 9" -> "9 9", "double nine" -> "nine nine", "triple 5" -> "5 5 5"
        text = text.replace(Regex("\\bdouble\\s+([a-z0-9]+)", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]} ${it.groupValues[1]}" }
        text = text.replace(Regex("\\btriple\\s+([a-z0-9]+)", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]} ${it.groupValues[1]} ${it.groupValues[1]}" }

        val tokens = text.split(Regex("\\s+"))
        val assembled = StringBuilder()
        for (t in tokens) {
            digitWords[t]?.let { assembled.append(it) } ?: run {
                if (Regex("^\\d+$").matches(t)) assembled.append(t)
            }
        }

        if (assembled.length in 7..15) {
            return assembled.toString()
        }

        val digitsOnly = rawInput?.replace(Regex("\\D"), "") ?: ""
        if (digitsOnly.length in 7..15) {
            return digitsOnly
        }

        return null
    }

    // --- Conversational Command Recognition ---

    private val COMMAND_DICTIONARY = mapOf(
        VoiceCommand.REPEAT to listOf(
            "repeat", "repeat the question", "repeat question", "say again", "what was the question",
            "what is the question", "say that again", "pardon", "दोहराएं", "फिर से बोलें", "दोहराओ", "dohrayen", "phir se"
        ),
        VoiceCommand.GO_BACK to listOf(
            "go back", "previous", "previous question", "back", "backtrack", "return to previous",
            "वापस जाएं", "पीछे जाएं", "पिछला प्रश्न", "पीछे", "wapas", "pichhe"
        ),
        VoiceCommand.SKIP to listOf(
            "skip", "skip this", "skip this field", "skip this question", "leave blank", "leave empty", "pass", "skip it",
            "छोड़ें", "छोड़ दो", "आगे बढ़ें", "खाली रखें", "chhoden", "aage badho"
        ),
        VoiceCommand.START_AGAIN to listOf(
            "start again", "restart", "start over", "begin again", "reset conversation",
            "फिर से शुरू करें", "शुरू से", "दोबारा शुरू करें", "shuru se"
        ),
        VoiceCommand.CHANGE_ANSWER to listOf(
            "change my answer", "change answer", "edit this", "edit answer", "re answer",
            "उत्तर बदलें", "बदलो", "संपादित करें", "badlen"
        ),
        VoiceCommand.STOP_LISTENING to listOf(
            "stop listening", "stop", "pause", "cancel", "halt", "stop assistant", "close", "dismiss",
            "रुकें", "रोकें", "बंद करें", "ruko", "pause karo", "rok do"
        ),
        VoiceCommand.READ_ANSWERS to listOf(
            "read my answers", "review my answers", "review", "show summary", "check answers",
            "समीक्षा करें", "उत्तर देखें", "समीक्षा", "review karo"
        ),
        VoiceCommand.NEXT_QUESTION to listOf(
            "next question", "next", "continue", "move on", "proceed",
            "अगला प्रश्न", "आगे", "आगे चलें", "agla"
        )
    )

    fun recognizeCommand(rawInput: String?): VoiceCommand? {
        val text = normalizeText(rawInput)
        if (text.isEmpty()) return null

        for ((cmd, phrases) in COMMAND_DICTIONARY) {
            for (p in phrases) {
                val normP = normalizeText(p)
                if (text == normP || text == "assistant $normP" || text == "please $normP") {
                    return cmd
                }
            }
        }
        return null
    }

    // --- Natural Human Dialogue Generation ---

    fun generateHumanPrompt(field: FormField, lang: String = "en"): String {
        val isHindi = lang.startsWith("hi", ignoreCase = true)
        val label = field.label.ifBlank { if (isHindi) "यह फ़ील्ड" else "this field" }
        val reqNote = if (field.required) {
            if (isHindi) " (यह फ़ील्ड अनिवार्य है)" else " (This field is required)"
        } else ""

        if (isHindi) {
            return when (field.semanticType) {
                SemanticType.FULL_NAME -> "आपका पूरा नाम क्या है?$reqNote"
                SemanticType.FIRST_NAME -> "आपका पहला नाम क्या है?$reqNote"
                SemanticType.LAST_NAME -> "आपका उपनाम या पारिवारिक नाम क्या है?$reqNote"
                SemanticType.EMAIL -> "आपका ईमेल पता क्या है?$reqNote"
                SemanticType.PHONE -> "आपका 10 अंकों का मोबाइल नंबर क्या है?$reqNote"
                SemanticType.DATE_OF_BIRTH -> "आपकी जन्म तिथि क्या है? आप बोल सकते हैं, जैसे 4 अक्टूबर 2003।$reqNote"
                SemanticType.DATE -> "“$label” के लिए कौन सी तारीख दर्ज की जानी चाहिए?$reqNote"
                SemanticType.DISTRICT, SemanticType.CITY -> "आप किस जिले या शहर में रहते हैं?$reqNote"
                SemanticType.STATE -> {
                    if (field.options.isNotEmpty()) {
                        val list = field.options.take(6).joinToString { it.label }
                        "आप किस राज्य में रहते हैं? विकल्प हैं: $list।$reqNote"
                    } else "आप किस राज्य में रहते हैं?$reqNote"
                }
                SemanticType.GENDER -> {
                    if (field.options.isNotEmpty()) {
                        val list = field.options.joinToString { it.label }
                        "लिंग के लिए, आप $list कह सकते हैं।$reqNote"
                    } else "आपका लिंग क्या है?$reqNote"
                }
                SemanticType.PINCODE -> "आपका 6 अंकों का पिन कोड क्या है?$reqNote"
                SemanticType.ADDRESS -> "कृपया अपना आवासीय पता या इलाका बताएं।$reqNote"
                SemanticType.CONSENT -> "घोषणा: “$label” के लिए, कृपया “हाँ, मैं सहमत हूँ” या “नहीं” कहें।$reqNote"
                else -> {
                    if (field.kind == FieldKind.SELECT || field.kind == FieldKind.RADIO) {
                        val opts = field.options.take(5).joinToString { it.label }
                        "“$label” के लिए विकल्प हैं: $opts।$reqNote"
                    } else {
                        "कृपया “$label” के लिए उत्तर दें।$reqNote"
                    }
                }
            }
        }

        // English (India)
        return when (field.semanticType) {
            SemanticType.FULL_NAME -> "What is your full name?$reqNote"
            SemanticType.FIRST_NAME -> "What is your first name?$reqNote"
            SemanticType.LAST_NAME -> "What is your surname or family name?$reqNote"
            SemanticType.EMAIL -> "What is your email address?$reqNote"
            SemanticType.PHONE -> "What is your mobile or phone number?$reqNote"
            SemanticType.DATE_OF_BIRTH -> "What is your date of birth? You can speak it naturally, such as 4th of October 2003.$reqNote"
            SemanticType.DATE -> "What date should be entered for “$label”?$reqNote"
            SemanticType.DISTRICT, SemanticType.CITY -> "Which district or city do you live in?$reqNote"
            SemanticType.STATE -> {
                if (field.options.isNotEmpty()) {
                    val list = field.options.take(6).joinToString { it.label }
                    "Which state do you live in? Options include $list.$reqNote"
                } else "Which state do you live in?$reqNote"
            }
            SemanticType.GENDER -> {
                if (field.options.isNotEmpty()) {
                    val list = field.options.joinToString { it.label }
                    "For gender, you can say $list.$reqNote"
                } else "What is your gender?$reqNote"
            }
            SemanticType.PINCODE -> "What is your 6-digit PIN code?$reqNote"
            SemanticType.ADDRESS -> "Please tell me your residential address or locality.$reqNote"
            SemanticType.CONSENT -> "For the declaration: “$label”, please say “Yes, I agree” or “No, I do not agree”.$reqNote"
            else -> {
                if (field.kind == FieldKind.SELECT || field.kind == FieldKind.RADIO) {
                    val opts = field.options.take(5).joinToString { it.label }
                    "For “$label”, available options are: $opts.$reqNote"
                } else {
                    "Please provide the answer for “$label”.$reqNote"
                }
            }
        }
    }

    fun generateConfirmationMessage(field: FormField, value: String, nextField: FormField? = null, lang: String = "en"): String {
        val isHindi = lang.startsWith("hi", ignoreCase = true)
        val label = field.label.ifBlank { if (isHindi) "फ़ील्ड" else "the field" }

        if (isHindi) {
            val base = if (field.kind == FieldKind.CHECKBOX) {
                if (value.contains("Yes") || value.contains("agree") || value.contains("हाँ") || value.contains("सहमत")) {
                    "मैंने सहमति बॉक्स पर टिक कर दिया है।"
                } else {
                    "मैंने आपका उत्तर दर्ज कर लिया है और बॉक्स खाली रखा है।"
                }
            } else {
                "बहुत बढ़िया, मैंने “$label” को “$value” दर्ज कर दिया है।"
            }
            return if (nextField != null) "$base आगे, ${nextField.label} भरते हैं।" else base
        }

        val base = if (field.kind == FieldKind.CHECKBOX) {
            if (value.contains("Yes") || value.contains("agree") || value.contains("Checked")) {
                "I have checked the consent box for you."
            } else {
                "I've recorded your answer and left the box unchecked."
            }
        } else {
            "Perfect, I've updated “$label” to “$value”."
        }
        return if (nextField != null) "$base Next, what's your ${nextField.label.lowercase(Locale.ROOT)}?" else base
    }

    // --- Semantic Type Inferrer ---

    fun inferSemanticType(label: String, hint: String = "", id: String = "", className: String = ""): SemanticType {
        val combined = normalizeText("$label $hint $id $className")

        if (Regex("\\b(e mail|email|mail address)\\b").containsMatchIn(combined)) return SemanticType.EMAIL
        if (Regex("\\b(phone|mobile|telephone|contact number|whatsapp)\\b").containsMatchIn(combined)) return SemanticType.PHONE
        if (Regex("\\b(full name|applicant name|your name|student name)\\b").containsMatchIn(combined)) return SemanticType.FULL_NAME
        if (Regex("\\b(first name|given name|forename)\\b").containsMatchIn(combined)) return SemanticType.FIRST_NAME
        if (Regex("\\b(last name|surname|family name)\\b").containsMatchIn(combined)) return SemanticType.LAST_NAME
        if (Regex("\\b(date of birth|birth date|dob|born)\\b").containsMatchIn(combined)) return SemanticType.DATE_OF_BIRTH
        if (Regex("\\b(date)\\b").containsMatchIn(combined)) return SemanticType.DATE
        if (Regex("\\b(age|years old)\\b").containsMatchIn(combined)) return SemanticType.AGE
        if (Regex("\\b(pin ?code|postal code|postcode|zip code|pincode)\\b").containsMatchIn(combined)) return SemanticType.PINCODE
        if (Regex("\\b(address|street|locality|village|house number|residential address)\\b").containsMatchIn(combined)) return SemanticType.ADDRESS
        if (Regex("\\b(city|town|place of residence)\\b").containsMatchIn(combined)) return SemanticType.CITY
        if (Regex("\\b(district)\\b").containsMatchIn(combined)) return SemanticType.DISTRICT
        if (Regex("\\b(state|province)\\b").containsMatchIn(combined)) return SemanticType.STATE
        if (Regex("\\b(country|nation)\\b").containsMatchIn(combined)) return SemanticType.COUNTRY
        if (Regex("\\b(gender|sex)\\b").containsMatchIn(combined)) return SemanticType.GENDER
        if (Regex("\\b(course|preferred course|major|stream|branch)\\b").containsMatchIn(combined)) return SemanticType.COURSE
        if (Regex("\\b(education|qualification|degree)\\b").containsMatchIn(combined)) return SemanticType.EDUCATION
        if (Regex("\\b(college|school|institution)\\b").containsMatchIn(combined)) return SemanticType.INSTITUTION
        if (Regex("\\b(guardian|father|mother|parent)\\b").containsMatchIn(combined)) return SemanticType.GUARDIAN_NAME
        if (Regex("\\b(agree|consent|terms|declaration|accept)\\b").containsMatchIn(combined) || combined.contains("checkbox")) return SemanticType.CONSENT

        return SemanticType.GENERAL
    }
}
