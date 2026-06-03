package com.betesepmu.vendor.bet

import com.betesepmu.vendor.model.RaceResult

/**
 * The bet-type groups and payout rows of an official rapport, in the exact order the
 * betesepmu web app prints them (`components/RapportPrintout.tsx`). Both the on-screen
 * Results view and the printed rapport iterate this so they stay in sync.
 */
object Rapport {

    data class Row(val label: String, val key: String)
    data class Group(val title: String, val rows: List<Row>)

    val GROUPS: List<Group> = listOf(
        Group("Quinté+", listOf(
            Row("Ordre", "quinteOrdre"),
            Row("Désordre", "quinteDesordre"),
            Row("Bonus 4", "quinteBonus4"),
            Row("Bonus 3", "quinteBonus3"),
        )),
        Group("Quarté+", listOf(
            Row("Ordre", "quarteOrdre"),
            Row("Désordre", "quarteDesordre"),
            Row("Bonus 3", "quarteBonus3"),
        )),
        Group("Tiercé", listOf(
            Row("Ordre", "tierceOrdre"),
            Row("Désordre", "tierceDesordre"),
        )),
        Group("Couplé", listOf(
            Row("Gagnant", "ordreGagnant"),
            Row("Placé A", "coupleA"),
            Row("Placé B", "coupleB"),
            Row("Placé C", "coupleC"),
        )),
        Group("Simple", listOf(
            Row("Gagnant", "simpleGagnant"),
            Row("Placé A", "simplePlaceA"),
            Row("Placé B", "simplePlaceB"),
            Row("Placé C", "simplePlaceC"),
        )),
        Group("Multi", listOf(
            Row("Multi 4", "multi4"),
            Row("Multi 5", "multi5"),
            Row("Multi 6", "multi6"),
            Row("Multi 7", "multi7"),
        )),
    )

    /** Dividend for a row; Couplé Gagnant falls back from `ordreGagnant` to `desordreGagnant`. */
    fun value(payouts: Map<String, Double>, key: String): Double? {
        val v = payouts[key]
        if (v != null && v > 0.0) return v
        if (key == "ordreGagnant") return payouts["desordreGagnant"]?.takeIf { it > 0.0 }
        return null
    }

    /** Winning numbers joined with '-' (mirrors `formatWinningNumbersForDisplay`). */
    fun formatNumbers(numbers: List<Int>): String =
        if (numbers.isEmpty()) "N/A" else numbers.joinToString("-")

    /** Groups (with their visible rows) that actually have a payout for this result section. */
    fun visibleGroups(payouts: Map<String, Double>): List<Pair<Group, List<Row>>> =
        GROUPS.mapNotNull { group ->
            val rows = group.rows.filter { value(payouts, it.key) != null }
            if (rows.isEmpty()) null else group to rows
        }

    /** The result sections of a race (primary + up to two bracket reports). */
    fun sections(result: RaceResult): List<Triple<String, List<Int>, Map<String, Double>>> = buildList {
        add(Triple("OFFICIAL REPORT", result.winningNumbers, result.payouts))
        if (result.bracketWinningNumbers != null && result.bracketPayouts != null) {
            add(Triple("BRACKET REPORT (1)", result.bracketWinningNumbers, result.bracketPayouts))
        }
        if (result.bracket2WinningNumbers != null && result.bracket2Payouts != null) {
            add(Triple("BRACKET REPORT (2)", result.bracket2WinningNumbers, result.bracket2Payouts))
        }
    }
}
