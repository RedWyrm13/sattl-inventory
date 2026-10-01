package org.sattl.inventory.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PIN / recovery-code hashing (spec section 8):
 * PBKDF2-HMAC-SHA256, random 16-byte salt per secret, at least 100,000 iterations.
 *
 * Hash and salt are stored as Base64 text. Never log or display the plain secret.
 *
 * Hashing is deliberately slow (a few hundred ms on the tablet). Call it from a background
 * dispatcher, never on the main thread.
 */
object PinHasher {
    /** Changing this invalidates every stored PIN; only increase it together with a migration. */
    const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()

    data class Hashed(val hash: String, val salt: String)

    /** Hashes [secret] with a fresh random salt. */
    fun hash(secret: String): Hashed {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return Hashed(hash = encode(derive(secret, salt)), salt = encode(salt))
    }

    /** True if [secret] matches the stored [hash] and [salt]. Uses a constant-time compare. */
    fun verify(secret: String, hash: String, salt: String): Boolean {
        val expected = Base64.getDecoder().decode(hash)
        val actual = derive(secret, Base64.getDecoder().decode(salt))
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
