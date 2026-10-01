package org.sattl.inventory.security

/** PIN format rules (spec sections 5.2 and 8): 4 to 6 digits. */
object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** Rule from spec section 8: this many wrong PINs in a row locks the user. */
    const val MAX_FAILED_ATTEMPTS = 5

    /** Rule from spec section 8: lockout lasts 5 minutes. */
    const val LOCKOUT_MILLIS = 5 * 60 * 1000L

    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** Plain-language error for a badly formed PIN, or null if it is fine. */
    fun formatError(pin: String): String? =
        if (isValidFormat(pin)) null else "A PIN must be $MIN_LENGTH to $MAX_LENGTH digits."
}
