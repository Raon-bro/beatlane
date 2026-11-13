package com.beatlane.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveHistoryTest {

    @Test
    fun scoresRoundTrip() {
        val scores = listOf(3.8, 4.25, 5.0, 1.0)
        val decoded = DriveHistory.decodeScores(DriveHistory.encodeScores(scores))
        assertEquals(scores.size, decoded.size)
        scores.zip(decoded).forEach { (a, b) -> assertEquals(a, b, 1e-3) }
    }

    @Test
    fun keepsOnlyLatestMax() {
        val scores = (1..30).map { (it % 5).toDouble() }
        val decoded = DriveHistory.decodeScores(DriveHistory.encodeScores(scores))
        assertEquals(DriveHistory.MAX_SCORES, decoded.size)
        assertEquals(scores.takeLast(DriveHistory.MAX_SCORES), decoded)
    }

    @Test
    fun emptyOrBrokenInputIsSafe() {
        assertTrue(DriveHistory.decodeScores(null).isEmpty())
        assertTrue(DriveHistory.decodeScores("").isEmpty())
        // 깨진 값·범위 밖 값은 버린다
        assertEquals(listOf(4.0, 2.5), DriveHistory.decodeScores("4.0,abc,,9.9,-1,2.5"))
    }

    @Test
    fun encodingIgnoresLocaleDecimalComma() {
        // 독일어 등 소수점이 쉼표인 로케일에서도 구분자와 섞이지 않아야 한다
        val prev = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals(listOf(3.5, 4.0), DriveHistory.decodeScores(DriveHistory.encodeScores(listOf(3.5, 4.0))))
        } finally {
            java.util.Locale.setDefault(prev)
        }
    }
}
