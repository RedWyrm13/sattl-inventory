package org.sattl.inventory.util

/**
 * Source of "now" as UTC epoch milliseconds. All dates come from the tablet's clock
 * (spec section 2). Code takes a Clock instead of calling System.currentTimeMillis()
 * directly so tests can control time.
 */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
