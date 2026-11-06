package com.budgetsms.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Saudi Riyal currency symbol, drawn as a local vector (works offline, follows the icon tint). */
val RiyalSymbol: ImageVector by lazy {
    ImageVector.Builder(name = "RiyalSymbol", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 1000f, viewportHeight = 1000f)
        .path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            // Left stroke, curving at the bottom into the lower-left bar
            moveTo(387f, 148f); curveTo(412f, 116f, 443f, 90f, 477f, 72f)
            lineTo(477f, 680f); curveTo(477f, 693f, 473f, 705f, 466f, 715f)
            lineTo(425f, 775f); curveTo(412f, 793f, 393f, 805f, 372f, 809f)
            lineTo(115f, 865f); curveTo(118f, 830f, 128f, 797f, 143f, 767f)
            lineTo(387f, 715f); close()
            // Upper slanted bar crossing both strokes
            moveTo(185f, 548f); lineTo(883f, 400f); curveTo(880f, 435f, 870f, 468f, 857f, 498f)
            lineTo(158f, 647f); curveTo(161f, 612f, 171f, 579f, 185f, 548f); close()
            // Right stroke
            moveTo(567f, 192f); curveTo(592f, 160f, 623f, 135f, 657f, 117f)
            lineTo(657f, 700f); lineTo(567f, 745f); close()
            // Middle slanted bar (right side)
            moveTo(567f, 652f); lineTo(883f, 585f); curveTo(880f, 620f, 870f, 653f, 857f, 683f)
            lineTo(567f, 745f); close()
            // Lower short bar
            moveTo(593f, 832f); lineTo(883f, 770f); curveTo(880f, 805f, 870f, 838f, 857f, 868f)
            lineTo(567f, 930f); curveTo(570f, 895f, 580f, 862f, 593f, 832f); close()
        }
        .build()
}
