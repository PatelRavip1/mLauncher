package com.github.codeworkscreativehub.mlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class WeekProgressTest {

    @Test
    fun testWeekDayIndexSundayStart() {
        val firstDay = Calendar.SUNDAY // 1
        assertEquals(1, (Calendar.SUNDAY - firstDay + 7) % 7 + 1)
        assertEquals(2, (Calendar.MONDAY - firstDay + 7) % 7 + 1)
        assertEquals(7, (Calendar.SATURDAY - firstDay + 7) % 7 + 1)
    }

    @Test
    fun testWeekDayIndexMondayStart() {
        val firstDay = Calendar.MONDAY // 2
        assertEquals(1, (Calendar.MONDAY - firstDay + 7) % 7 + 1)
        assertEquals(6, (Calendar.SATURDAY - firstDay + 7) % 7 + 1)
        assertEquals(7, (Calendar.SUNDAY - firstDay + 7) % 7 + 1)
    }
}
