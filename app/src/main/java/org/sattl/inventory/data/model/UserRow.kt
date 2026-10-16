package org.sattl.inventory.data.model

import androidx.room.Embedded
import org.sattl.inventory.data.entity.User

/** A user plus how many items they hold right now, for Manage users (spec 5.9). */
data class UserRow(
    @Embedded val user: User,
    val openCheckouts: Int,
)
