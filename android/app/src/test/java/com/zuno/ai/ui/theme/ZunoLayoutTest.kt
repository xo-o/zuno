package com.zuno.ai.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZunoLayoutTest {

    @Test
    fun `compact phones including Xiaomi 14T use 16dp gutters and aligned top bar`() {
        listOf(360, 375, 384, 402, 406, 412).forEach { widthDp ->
            val layout = zunoLayoutForWidth(widthDp)
            assertTrue("Expected compact for ${widthDp}dp", layout.isCompact)
            assertEquals(16.dp, layout.screenPaddingH)
            assertEquals(6.dp, layout.topBarPaddingH)
            // 44dp ZunoIconButton centers a 24dp icon with 10dp inset -> 6 + 10 == 16dp gutter
            assertEquals(layout.screenPaddingH, layout.topBarPaddingH + 10.dp)
            assertEquals(12.dp, layout.cardPaddingH)
            assertEquals(10.dp, layout.rowGap)
            assertEquals(12.dp, layout.chipPaddingH)
            assertEquals(6.dp, layout.chipGap)
        }
    }

    @Test
    fun `medium and tablet widths step up cleanly`() {
        val medium = zunoLayoutForWidth(480)
        assertFalse(medium.isCompact)
        assertEquals(20.dp, medium.screenPaddingH)
        assertEquals(medium.screenPaddingH, medium.topBarPaddingH + 10.dp)

        val tablet = zunoLayoutForWidth(640)
        assertFalse(tablet.isCompact)
        assertEquals(24.dp, tablet.screenPaddingH)
        assertEquals(12.dp, tablet.topBarPaddingH)
    }
}
