package org.sattl.inventory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sattl.inventory.domain.ItemStatus

/**
 * Coloured status badge (spec section 10): Available green, Checked out amber,
 * Not checkoutable grey, Retired dark grey. The word is always shown, so colour is never
 * the only signal.
 */
@Composable
fun StatusBadge(status: ItemStatus, modifier: Modifier = Modifier) {
    val (background, text) = when (status) {
        ItemStatus.AVAILABLE -> Color(0xFFD3F0DC) to Color(0xFF0B5A26)
        ItemStatus.CHECKED_OUT -> Color(0xFFFFE08A) to Color(0xFF5C3B00)
        ItemStatus.NOT_CHECKOUTABLE -> Color(0xFFE0E0E0) to Color(0xFF3D3D3D)
        ItemStatus.RETIRED -> Color(0xFF4A4A4A) to Color.White
    }
    Text(
        text = status.label,
        color = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
