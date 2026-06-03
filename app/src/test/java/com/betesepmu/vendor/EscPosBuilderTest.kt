package com.betesepmu.vendor

import com.betesepmu.vendor.escpos.Align
import com.betesepmu.vendor.escpos.CodePage
import com.betesepmu.vendor.escpos.EscPos
import com.betesepmu.vendor.escpos.EscPosBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosBuilderTest {

    @Test fun initialize_emits_reset_then_codepage() {
        val bytes = EscPosBuilder(CodePage.WPC1252).initialize().build()
        // ESC @  then  ESC t 16
        assertArrayEquals(byteArrayOf(0x1B, '@'.code.toByte(), 0x1B, 't'.code.toByte(), 16), bytes)
    }

    @Test fun text_is_encoded_with_active_codepage() {
        val bytes = EscPosBuilder(CodePage.WPC1252).text("AB").build()
        assertArrayEquals(byteArrayOf('A'.code.toByte(), 'B'.code.toByte()), bytes)
    }

    @Test fun line_appends_line_feed() {
        val bytes = EscPosBuilder().text("").raw(ByteArray(0)).line("X").build()
        assertEquals('X'.code.toByte(), bytes[0])
        assertEquals(EscPos.LF, bytes[1])
    }

    @Test fun size_packs_width_and_height_nibbles() {
        // width x2, height x3  ->  upper nibble (2-1)=1, lower (3-1)=2  => 0x12
        val bytes = EscPos.size(2, 3)
        assertArrayEquals(byteArrayOf(0x1D, '!'.code.toByte(), 0x12), bytes)
    }

    @Test fun align_center_command() {
        assertArrayEquals(byteArrayOf(0x1B, 'a'.code.toByte(), 1), EscPos.align(Align.CENTER.value))
    }

    @Test fun cut_with_feed_uses_function_66() {
        val bytes = EscPosBuilder().cut(feedDots = 50).build()
        assertArrayEquals(byteArrayOf(0x1D, 'V'.code.toByte(), 66, 50), bytes)
    }

    @Test fun qr_command_contains_store_and_print_phases() {
        val bytes = EscPosBuilder().qr("HI").build()
        // crude structural check: should contain GS ( k store (0x50) and print (0x51)
        assertTrue(bytes.any { it == 0x50.toByte() })
        assertTrue(bytes.any { it == 0x51.toByte() })
    }
}
