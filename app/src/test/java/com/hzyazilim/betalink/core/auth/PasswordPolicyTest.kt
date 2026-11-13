package com.hzyazilim.betalink.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test fun acceptsPasswordMeetingAllRequirements() {
        assertTrue(PasswordPolicy.isValid("BetaLink!2026Secure"))
    }

    @Test fun rejectsPasswordShorterThanTwelveCharacters() {
        assertFalse(PasswordPolicy.isValid("Aa1!short"))
    }

    @Test fun requiresUppercaseAndLowercaseLetters() {
        assertFalse(PasswordPolicy.isValid("betalink!2026secure"))
        assertFalse(PasswordPolicy.isValid("BETALINK!2026SECURE"))
    }

    @Test fun requiresAtLeastOneDigitAndSupportedSymbol() {
        assertFalse(PasswordPolicy.isValid("BetaLink!SecurePass"))
        assertFalse(PasswordPolicy.isValid("BetaLink2026Secure"))
    }

    @Test fun rejectsUnsupportedUnicodeSymbolsWithoutSupabaseSymbol() {
        assertFalse(PasswordPolicy.isValid("BetaLink🙂2026Secure"))
    }
}
