package com.athena.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A six-column Doric temple, the same silhouette as the launcher emblem. */
val TempleSymbol: ImageVector
    get() {
        val cached = _templeSymbol
        if (cached != null) return cached
        val created = ImageVector.Builder(
            name = "Temple",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(24f, 6f)
                lineTo(44f, 18f)
                horizontalLineTo(4f)
                close()
                moveTo(6f, 20f)
                horizontalLineTo(42f)
                verticalLineTo(24f)
                horizontalLineTo(6f)
                close()
                moveTo(9f, 25f)
                horizontalLineTo(12f)
                verticalLineTo(38f)
                horizontalLineTo(9f)
                close()
                moveTo(16f, 25f)
                horizontalLineTo(19f)
                verticalLineTo(38f)
                horizontalLineTo(16f)
                close()
                moveTo(22.5f, 25f)
                horizontalLineTo(25.5f)
                verticalLineTo(38f)
                horizontalLineTo(22.5f)
                close()
                moveTo(29f, 25f)
                horizontalLineTo(32f)
                verticalLineTo(38f)
                horizontalLineTo(29f)
                close()
                moveTo(36f, 25f)
                horizontalLineTo(39f)
                verticalLineTo(38f)
                horizontalLineTo(36f)
                close()
                moveTo(4f, 38f)
                horizontalLineTo(44f)
                verticalLineTo(41f)
                horizontalLineTo(4f)
                close()
                moveTo(8f, 41f)
                horizontalLineTo(40f)
                verticalLineTo(43f)
                horizontalLineTo(8f)
                close()
            }
        }.build()
        _templeSymbol = created
        return created
    }

private var _templeSymbol: ImageVector? = null
