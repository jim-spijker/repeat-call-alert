package io.github.jimspijker.repeatcallalert.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberNormalizerTest {
    private fun n(s: String?, cc: String? = "31") = PhoneNumberNormalizer.normalize(s, cc)

    @Test fun differentNotationsOfSameNumberMatch() {
        val expected = "+31612345678"
        assertEquals(expected, n("+31612345678"))
        assertEquals(expected, n("+31 6 12345678"))
        assertEquals(expected, n("0031612345678"))
        assertEquals(expected, n("06-12345678"))
        assertEquals(expected, n("(06) 123 456 78"))
    }

    @Test fun postDialDigitsAreIgnored() {
        assertEquals("+31201234567", n("020-1234567,,123"))
    }

    @Test fun foreignNumberKeepsCountryCode() {
        assertEquals("+4915112345678", n("+49 151 12345678"))
        assertEquals("+14155550123", n("+1 (415) 555-0123", cc = "44"))
    }

    @Test fun unknownCountryDoesNotGuess() {
        assertEquals("nat:0612345678", n("0612345678", cc = null))
    }

    @Test fun unusableNumbersAreRejected() {
        listOf(null, "", "   ", "anonymous", "user@sip.example", "12", "+", "31+612", "+1234567890123456")
            .forEach { assertNull("expected null for '$it'", n(it)) }
    }

    @Test fun maskShowsOnlyLastDigits() {
        assertEquals("…678", PhoneNumberNormalizer.mask("+31612345678"))
        assertEquals("…", PhoneNumberNormalizer.mask(null))
    }
}
