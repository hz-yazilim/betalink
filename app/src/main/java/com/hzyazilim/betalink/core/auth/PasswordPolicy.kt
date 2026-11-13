package com.hzyazilim.betalink.core.auth

/**
 * Shared password policy for registration and password updates.
 * The symbol allow-list matches Supabase Auth's supported password symbols.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 12
    const val ERROR_MESSAGE =
        "Şifre en az 12 karakter olmalı; büyük/küçük harf, rakam ve desteklenen özel karakter içermeli."

    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()_+-=[]{};':\"|<>?,./~"

    fun isValid(password: String): Boolean =
        password.length >= MIN_LENGTH &&
            password.any { it in LOWERCASE } &&
            password.any { it in UPPERCASE } &&
            password.any { it in DIGITS } &&
            password.any { it in SYMBOLS || it == '\u0060' }
}
