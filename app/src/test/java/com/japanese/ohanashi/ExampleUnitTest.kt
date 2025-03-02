package com.japanese.ohanashi

import org.junit.Test
import android.util.Log
import org.junit.Assert.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        val story = StoryCard("")
        val timeString = "01:08" //"00:01:08.83"
        val result0 = story.parseTimeString("08")
        val result1 = story.parseTimeString("08.00")
        val result2 = story.parseTimeString("00:08")
        val result3 = story.parseTimeString("00:08.00")
        val result4 = story.parseTimeString("00:00:08")
        val result5 = story.parseTimeString("00:00:08.00")
        System.out.println("RESULT: $result0, $result1, $result2, $result3, $result4, $result5")

        assertEquals(4, 2 + 2)
    }
}