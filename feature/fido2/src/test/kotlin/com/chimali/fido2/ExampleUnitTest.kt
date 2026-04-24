package com.chimali.fido2

import kotlin.test.*
import kotlin.test.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        val two = TWO
        val four = FOUR
        assertEquals(four, two + two)
    }

    private companion object {
        private const val TWO = 2
        private const val FOUR = 4
    }
}
