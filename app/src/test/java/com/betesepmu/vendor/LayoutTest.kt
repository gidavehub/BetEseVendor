package com.betesepmu.vendor

import com.betesepmu.vendor.document.Column
import com.betesepmu.vendor.document.Layout
import com.betesepmu.vendor.escpos.Align
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutTest {

    @Test fun pad_left_right_center() {
        assertEquals("ab   ", Layout.pad("ab", 5, Align.LEFT))
        assertEquals("   ab", Layout.pad("ab", 5, Align.RIGHT))
        assertEquals(" ab  ", Layout.pad("ab", 5, Align.CENTER))
    }

    @Test fun pad_truncates_when_too_long() {
        assertEquals("abcde", Layout.pad("abcdefgh", 5, Align.LEFT))
    }

    @Test fun divider_fills_width() {
        assertEquals("====", Layout.divider(4, '='))
    }

    @Test fun columns_distribute_by_weight_and_fill_line() {
        val line = Layout.columns(
            listOf(
                Column("ITEM", weight = 3, align = Align.LEFT),
                Column("TOTAL", weight = 1, align = Align.RIGHT),
            ),
            totalChars = 32,
        )
        assertEquals(32, line.length)
        assertEquals("ITEM", line.trim().take(4))
        assertEquals("TOTAL", line.trimEnd().takeLast(5))
    }

    @Test fun wrap_breaks_on_spaces_and_respects_width() {
        val lines = Layout.wrap("the quick brown fox", 9)
        assertEquals(listOf("the quick", "brown fox"), lines)
    }

    @Test fun wrap_hard_splits_overlong_token() {
        val lines = Layout.wrap("abcdefghijk", 4)
        assertEquals(listOf("abcd", "efgh", "ijk"), lines)
    }
}
