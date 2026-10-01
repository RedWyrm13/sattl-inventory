package org.sattl.inventory.security

import java.security.SecureRandom

/**
 * The admin recovery code (spec sections 5.1, 5.10, 8): 12 random characters, shown once,
 * stored only as a hash.
 *
 * Uses capital letters and digits but leaves out look-alikes (0/O, 1/I/L) so it is easy to
 * copy by hand. Shown in groups of four (ABCD-EFGH-JKMN); dashes, spaces and case are
 * ignored when it is typed back in.
 */
object RecoveryCode {
    const val LENGTH = 12
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    /** A new random code, without dashes. */
    fun generate(): String = buildString {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** "ABCDEFGHJKMN" -> "ABCD-EFGH-JKMN" for display. */
    fun format(code: String): String = normalize(code).chunked(4).joinToString("-")

    /** Removes dashes/spaces and upper-cases, so typed input matches the generated code. */
    fun normalize(input: String): String = input.filter { it.isLetterOrDigit() }.uppercase()
}
