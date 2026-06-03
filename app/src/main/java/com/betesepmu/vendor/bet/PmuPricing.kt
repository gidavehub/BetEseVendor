package com.betesepmu.vendor.bet

import com.betesepmu.vendor.model.BetTypeOption

/** Pricing table for one bet type. Ported verbatim from `betesepmu/constants.ts` BET_PRICING. */
data class BetPricing(
    val minHorses: Int,
    val perHorsePrice: Double? = null,
    val basePrice: Double? = null,
    val priceMap: Map<Int, Double> = emptyMap(),
    val xPriceMap: Map<Int, Map<Int, Double>> = emptyMap(),
)

/**
 * The PMU price tables and the bet-cost formula, ported from the betesepmu web app so the
 * native terminal charges identical stakes. Cost math mirrors `App.tsx` `updateBetSlip`
 * (lines 916-922); the winnings/dividend engine is intentionally not ported here.
 */
object PmuPricing {

    val BET_PRICING: Map<BetTypeOption, BetPricing> = mapOf(
        BetTypeOption.SimpleGagnant to BetPricing(minHorses = 1, perHorsePrice = 30.0),
        BetTypeOption.SimplePlace to BetPricing(minHorses = 1, perHorsePrice = 30.0),
        BetTypeOption.CoupleGagnant to BetPricing(
            minHorses = 2, basePrice = 30.0,
            priceMap = mapOf(2 to 30.0, 3 to 90.0, 4 to 180.0, 5 to 300.0, 6 to 450.0, 7 to 630.0, 8 to 840.0, 9 to 1080.0, 10 to 1350.0, 11 to 1650.0, 12 to 1980.0, 13 to 2340.0, 14 to 2730.0, 15 to 3150.0, 16 to 3600.0),
            xPriceMap = mapOf(1 to mapOf(1 to 450.0)),
        ),
        BetTypeOption.CouplePlace to BetPricing(
            minHorses = 2, basePrice = 30.0,
            priceMap = mapOf(2 to 30.0, 3 to 90.0, 4 to 180.0, 5 to 300.0, 6 to 450.0, 7 to 630.0, 8 to 840.0, 9 to 1080.0, 10 to 1350.0, 11 to 1650.0, 12 to 1980.0, 13 to 2340.0, 14 to 2730.0, 15 to 3150.0, 16 to 3600.0),
            xPriceMap = mapOf(1 to mapOf(1 to 450.0)),
        ),
        BetTypeOption.Tierce to BetPricing(
            minHorses = 3, basePrice = 25.0,
            priceMap = mapOf(3 to 25.0, 4 to 100.0, 5 to 250.0, 6 to 500.0, 7 to 875.0, 8 to 1400.0, 9 to 2100.0, 10 to 3000.0, 11 to 4125.0, 12 to 5500.0, 13 to 7150.0, 14 to 9100.0, 15 to 11375.0, 16 to 14000.0),
            xPriceMap = mapOf(1 to mapOf(2 to 350.0), 2 to mapOf(1 to 5250.0)),
        ),
        BetTypeOption.Quarte to BetPricing(
            minHorses = 4, basePrice = 25.0,
            priceMap = mapOf(4 to 25.0, 5 to 125.0, 6 to 375.0, 7 to 875.0, 8 to 1750.0, 9 to 3150.0, 10 to 5250.0, 11 to 8250.0, 12 to 12375.0, 13 to 17875.0, 14 to 25025.0, 15 to 34125.0, 16 to 45500.0),
            xPriceMap = mapOf(1 to mapOf(3 to 325.0), 2 to mapOf(2 to 4550.0), 3 to mapOf(1 to 68250.0)),
        ),
        BetTypeOption.Quinte to BetPricing(
            minHorses = 5, basePrice = 30.0,
            priceMap = mapOf(5 to 30.0, 6 to 180.0, 7 to 630.0, 8 to 1680.0, 9 to 3780.0, 10 to 7560.0, 11 to 13860.0, 12 to 23760.0, 13 to 38610.0, 14 to 60060.0, 15 to 90090.0, 16 to 131040.0),
            xPriceMap = mapOf(1 to mapOf(4 to 360.0), 2 to mapOf(3 to 4705.0), 3 to mapOf(2 to 65520.0), 4 to mapOf(1 to 982800.0)),
        ),
        BetTypeOption.Multi4 to BetPricing(
            minHorses = 4, basePrice = 40.0,
            priceMap = mapOf(4 to 40.0, 5 to 200.0, 6 to 600.0, 7 to 1400.0, 8 to 2800.0, 9 to 5040.0, 10 to 8400.0, 11 to 13200.0, 12 to 19800.0, 13 to 28600.0, 14 to 40040.0, 15 to 54600.0, 16 to 72800.0),
            xPriceMap = mapOf(1 to mapOf(3 to 440.0), 2 to mapOf(2 to 2640.0), 3 to mapOf(1 to 11440.0)),
        ),
        BetTypeOption.Multi5 to BetPricing(
            minHorses = 5, basePrice = 40.0,
            priceMap = mapOf(5 to 40.0, 6 to 240.0, 7 to 840.0, 8 to 2240.0, 9 to 5040.0, 10 to 10080.0, 11 to 18480.0, 12 to 31680.0, 13 to 51480.0, 14 to 80080.0, 15 to 120120.0, 16 to 174720.0),
            xPriceMap = mapOf(1 to mapOf(4 to 400.0), 2 to mapOf(3 to 2200.0), 3 to mapOf(2 to 8800.0), 4 to mapOf(1 to 28600.0)),
        ),
        BetTypeOption.Multi6 to BetPricing(
            minHorses = 6, basePrice = 40.0,
            priceMap = mapOf(6 to 40.0, 7 to 280.0, 8 to 1120.0, 9 to 3360.0, 10 to 8400.0, 11 to 18480.0, 12 to 36960.0, 13 to 68640.0, 14 to 120120.0, 15 to 200200.0, 16 to 320320.0),
            xPriceMap = mapOf(1 to mapOf(5 to 360.0), 2 to mapOf(4 to 1800.0), 3 to mapOf(3 to 6600.0), 4 to mapOf(2 to 19800.0), 5 to mapOf(1 to 51480.0)),
        ),
        BetTypeOption.Multi7 to BetPricing(
            minHorses = 7, basePrice = 40.0,
            priceMap = mapOf(7 to 40.0, 8 to 320.0, 9 to 1440.0, 10 to 4800.0, 11 to 13200.0, 12 to 31680.0, 13 to 68640.0, 14 to 137280.0, 15 to 257400.0, 16 to 457600.0),
            xPriceMap = mapOf(1 to mapOf(6 to 320.0), 2 to mapOf(5 to 1440.0), 3 to mapOf(4 to 4800.0), 4 to mapOf(3 to 13200.0), 5 to mapOf(2 to 31680.0), 6 to mapOf(1 to 68640.0)),
        ),
    )

    fun pricing(betType: BetTypeOption): BetPricing = BET_PRICING.getValue(betType)

    fun minHorses(betType: BetTypeOption): Int = pricing(betType).minHorses

    /**
     * Cost for one unit (multiplier = 1) of a selection. Mirrors `App.tsx` updateBetSlip:
     *   perHorsePrice → (numbers + X) × perHorsePrice
     *   else if X > 0 → xPriceMap[X][numbers]
     *   else          → priceMap[numbers]
     */
    fun costFor(betType: BetTypeOption, numbersCount: Int, xCount: Int): Double {
        val p = pricing(betType)
        return when {
            p.perHorsePrice != null -> (numbersCount + xCount) * p.perHorsePrice
            xCount > 0 -> p.xPriceMap[xCount]?.get(numbersCount) ?: 0.0
            else -> p.priceMap[numbersCount] ?: 0.0
        }
    }
}
