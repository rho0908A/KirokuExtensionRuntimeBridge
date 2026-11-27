package io.kiroku.runtime.host.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.kiroku.runtime.ipc.BackendType

/** How a backend is presented as a manager card (badge, colour and human readable name). */
data class ManagerStyle(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val badge: Color,
)

/** The manager order used by the repository and extension lists. */
val BackendType.managerOrder: List<BackendType>
    get() = listOf(BackendType.ANIYOMI, BackendType.CLOUDSTREAM)

fun BackendType.managerStyle(): ManagerStyle = when (this) {
    BackendType.CLOUDSTREAM -> ManagerStyle(
        title = "CloudStream",
        icon = Icons.Rounded.Cloud,
        tint = Color.White,
        badge = Color(0xFF3B7BD8),
    )

    else -> ManagerStyle(
        title = "Aniyomi",
        icon = Icons.Rounded.SmartDisplay,
        tint = Color.White,
        badge = Color(0xFFE05A2B),
    )
}
