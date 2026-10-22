package com.alirezaiyan.passki.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24-unit grid, drawn as strokes so they tint like text. Same shapes as passki-web. */
object PassKiIcons {
    private fun stroke(name: String, width: Float = 2f, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Key = stroke("Key", 2f, "M12.5 8a4.5 4.5 0 1 1-9 0a4.5 4.5 0 1 1 9 0", "M11.3 11.3l8.2 8.2", "M16.5 16.5l2-2", "M14.3 14.3l1.6-1.6")
    val Check = stroke("Check", 2.6f, "M5 12.5L10 17l9-10")
    val Cross = stroke("Cross", 2.6f, "M7 7l10 10", "M17 7L7 17")
    val Warn = stroke("Warn", 2.6f, "M12 6v7", "M12 17.5v0.5")
    val Skip = stroke("Skip", 2.6f, "M7 12h10")
    val Shield = stroke("Shield", 2f, "M12 3L5 6v6c0 4.4 3 7.7 7 9c4-1.3 7-4.6 7-9V6z", "M9 12l2 2l4-4")
    val Flask = stroke("Flask", 2f, "M9 3h6", "M10 3v6L4.5 18.5A1.7 1.7 0 0 0 6 21h12a1.7 1.7 0 0 0 1.5-2.5L14 9V3", "M7 15h10")
    val User = stroke("User", 2f, "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    val Server = stroke("Server", 2f, "M6 4h12a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M6 13h12a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z", "M8 7.5h0.01", "M8 16.5h0.01")
    val Chevron = stroke("Chevron", 2f, "M9 6l6 6l-6 6")
    val Refresh = stroke("Refresh", 2f, "M20 11a8 8 0 0 0-14.9-3.9L4 8.5", "M4 4v4.5h4.5", "M4 13a8 8 0 0 0 14.9 3.9L20 15.5", "M20 20v-4.5h-4.5")
    val Logout = stroke("Logout", 2f, "M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3", "M10 17l-5-5l5-5", "M5 12h11")
    val Phone = stroke("Phone", 2f, "M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M11 18h2")
}
