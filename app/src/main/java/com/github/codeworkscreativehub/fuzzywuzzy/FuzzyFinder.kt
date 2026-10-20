package com.github.codeworkscreativehub.fuzzywuzzy

import android.content.Context
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.ContactListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.emptyString
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale

private const val SCORE_MATCH = 100
private const val SCORE_CONSECUTIVE = 90
private const val SCORE_WORD_START = 80
private const val SCORE_START_IDX_BONUS = 15

private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
private val PUNCT_SPACE_REGEX = Regex("[-_+,. ]")
private val PUNCT_REGEX = Regex("[-_+,.]")

object FuzzyFinder {

    private class TargetData(
        val normalized: String,
        val compact: String,
        val lower: String
    )

    private val targetCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, TargetData>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, TargetData>?): Boolean = size > 512
        }
    )

    private val searchCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, String>?): Boolean = size > 256
        }
    )

    private val maxScoreCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Int>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Int>?): Boolean = size > 512
        }
    )

    fun clearCache() {
        targetCache.clear()
        searchCache.clear()
        maxScoreCache.clear()
    }

    private fun getTargetData(input: String): TargetData =
        targetCache.getOrPut(input) {
            val normalized = normalizeDiacritics(input.uppercase(Locale.getDefault()))
                .replace(PUNCT_REGEX, " ")
            val compact = normalized.replace(" ", emptyString())
            val lower = input.lowercase(Locale.getDefault())
            TargetData(normalized, compact, lower)
        }

    private fun isWordBoundary(c: Char): Boolean =
        c == ' ' || c == '.' || c == '_' || c == '-' || c == ','

    private fun hasWordStartingWith(target: String, query: String): Boolean {
        if (query.isEmpty()) return true
        if (target.startsWith(query)) return true
        var i = 0
        val len = target.length
        while (i < len) {
            if (target[i].isWhitespace()) {
                while (i < len && target[i].isWhitespace()) {
                    i++
                }
                if (i < len && target.startsWith(query, i)) {
                    return true
                }
            } else {
                i++
            }
        }
        return false
    }

    /**
     * Scores an AppListItem based on its activity label.
     */
    fun scoreApp(context: Context, app: AppListItem, searchChars: String, topScore: Int): Int {
        val prefs = Prefs(context)
        val appLabel = prefs.getAppAlias(app.settingsKey)
            .takeIf { it.isNotBlank() }
            ?: app.activityLabel
        val normalizedAppLabel = normalizeTarget(appLabel)
        val normalizedSearchChars = normalizeSearch(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedAppLabel, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    /**
     * Scores a ContactListItem based on its display name.
     */
    fun scoreContact(contact: ContactListItem, searchChars: String, topScore: Int): Int {
        val contactLabel = contact.displayName
        val normalizedContactLabel = normalizeTarget(contactLabel)
        val normalizedSearchChars = normalizeSearch(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedContactLabel, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    /**
     * Scores a raw string against a search query.
     */
    fun scoreString(target: String, searchChars: String, topScore: Int): Int {
        val normalizedTarget = normalizeTarget(target)
        val normalizedSearchChars = normalizeSearch(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedTarget, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    /**
     * Advanced Fuzzy Matching Score.
     * Returns a float between 0.0 and 1.0.
     * 1.0 represents a perfect prefix match.
     */
    internal fun calculateFuzzyScore(haystack: String, needle: String): Float {
        if (needle.isEmpty() || haystack.isEmpty()) return 0f

        val n = needle.length
        val m = haystack.length
        if (n > m) return 0f

        var currentScore = 0
        var needleIdx = 0
        var prevHaystackIdx = -1

        // 1. Actual Score Loop
        for (i in 0 until m) {
            if (needleIdx >= n) break
            if (m - i < n - needleIdx) break

            if (haystack[i] == needle[needleIdx]) {
                var charScore = SCORE_MATCH
                if (prevHaystackIdx != -1 && i == prevHaystackIdx + 1) charScore += SCORE_CONSECUTIVE

                val isWordStart = i == 0 || isWordBoundary(haystack[i - 1])
                if (isWordStart) charScore += SCORE_WORD_START
                if (i < 3) charScore += SCORE_START_IDX_BONUS

                currentScore += charScore
                prevHaystackIdx = i
                needleIdx++
            }
        }

        if (needleIdx < n) return 0f

        // 2. Corrected Perfect Max Score
        // We only calculate the potential score for characters that ARE NOT spaces.
        val perfectMaxScore = maxScoreCache.getOrPut(haystack) {
            var maxScore = 0
            for (i in 0 until m) {
                if (haystack[i] == ' ') continue

                var potential = SCORE_MATCH
                val isWordStart = i == 0 || isWordBoundary(haystack[i - 1])

                potential += if (isWordStart) SCORE_WORD_START else SCORE_CONSECUTIVE
                if (i < 3) potential += SCORE_START_IDX_BONUS

                maxScore += potential
            }
            maxScore
        }

        return if (perfectMaxScore == 0) 0f else currentScore.toFloat() / perfectMaxScore
    }

    /**
     * Simple boolean match for legacy support or low-power filtering.
     */
    fun isMatch(target: String, searchChars: String): Boolean {
        val normalizedTarget = normalizeString(target)
        val normalizedSearch = normalizeString(searchChars)
        return normalizedTarget.contains(normalizedSearch)
    }

    // --- Normalization Helpers ---
    private fun normalizeString(input: String): String = normalizeSearch(input)

    private fun normalizeSearch(input: String): String =
        searchCache.getOrPut(input) {
            normalizeDiacritics(input.uppercase(Locale.getDefault()))
                .replace(PUNCT_SPACE_REGEX, emptyString())
        }

    private fun normalizeTarget(input: String): String = getTargetData(input).normalized

    private fun normalizeDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, emptyString())
    }

    /**
     * A generic helper to filter lists based on fuzzy scoring or simple matching.
     * T is the type of item (AppListItem or ContactListItem)
     */
    private data class RankedItem<T>(
        val item: T,
        val score: Int,
        val startIndex: Int,
        val startsWith: Boolean,
        val wordStartsWith: Boolean,
        val originalIndex: Int
    )

    fun <T> filterItems(
        itemsList: List<T>,
        query: String,
        prefs: Prefs,
        scoreProvider: (T, String) -> Int,
        labelProvider: (T) -> String,
        loggerTag: String
    ): MutableList<T> {
        if (query.isEmpty()) return itemsList.toMutableList()

        val normalizedQuery = normalizeSearch(query)

        // Keep original index as a final tie-breaker to preserve deterministic ordering.
        val rankedMatches = itemsList.mapIndexedNotNull { index, item ->
            val label = labelProvider(item)
            val targetData = getTargetData(label)
            val normalizedTarget = targetData.normalized
            val normalizedTargetCompact = targetData.compact
            val startIndex = normalizedTargetCompact.indexOf(normalizedQuery)
            val startsWith = normalizedTargetCompact.startsWith(normalizedQuery)
            val wordStartsWith = hasWordStartingWith(normalizedTarget, normalizedQuery)

            if (prefs.enableFilterStrength) {
                val score = scoreProvider(item, query)
                val isPrefixMatch = startsWith || wordStartsWith
                val blockedByStartSetting = prefs.searchFromStart && !startsWith
                val droppedByFilterStrength = !isPrefixMatch && score <= prefs.filterStrength
                if (blockedByStartSetting || droppedByFilterStrength) {
                    null
                } else {
                    RankedItem(item, score, startIndex, startsWith, wordStartsWith, index)
                }
            } else {
                val targetLower = targetData.lower
                val matched = if (prefs.searchFromStart) {
                    targetLower.startsWith(query)
                } else {
                    isMatch(targetLower, query)
                }
                if (!matched) {
                    null
                } else {
                    // Keep non-fuzzy mode deterministic but still prioritize stronger matches.
                    RankedItem(item, 0, startIndex, startsWith, wordStartsWith, index)
                }
            }
        }

        return rankedMatches
            .sortedWith(
                compareByDescending<RankedItem<T>> { it.startsWith }
                    .thenByDescending { it.wordStartsWith }
                    .thenByDescending { it.score }
                    .thenBy { if (it.startIndex >= 0) it.startIndex else Int.MAX_VALUE }
                    .thenBy { it.originalIndex }
            )
            .map { it.item }
            .toMutableList()
    }
}