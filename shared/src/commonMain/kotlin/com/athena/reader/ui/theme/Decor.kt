package com.athena.reader.ui.theme

import athena.shared.generated.resources.Res
import athena.shared.generated.resources.olive
import athena.shared.generated.resources.papyrus
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

/** Olive wood under a wash of honey so the grain reads as Greece, not a dark desk. */
@Composable
fun PapyrusBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val texture = painterResource(Res.drawable.olive)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        Image(
            painter = texture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.48f),
        )
        content()
    }
}

/** Reading surface: Greek papyrus, never wood. */
@Composable
fun ReaderPapyrusBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val texture = painterResource(Res.drawable.papyrus)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Papyrus))
        Image(
            painter = texture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.72f),
        )
        content()
    }
}

/** Running meander, the band that frames a temple. */
@Composable
fun GreekKey(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val stroke = size.height * 0.22f
        val unit = size.height * 2.4f
        val path = Path()
        val top = stroke
        val bot = size.height - stroke
        val mid = size.height * 0.55f
        var x = 0f
        while (x < size.width + unit) {
            path.moveTo(x, bot)
            path.lineTo(x + unit * 0.18f, bot)
            path.lineTo(x + unit * 0.18f, top)
            path.lineTo(x + unit * 0.58f, top)
            path.lineTo(x + unit * 0.58f, mid)
            path.lineTo(x + unit * 0.36f, mid)
            path.lineTo(x + unit * 0.36f, bot)
            path.lineTo(x + unit * 0.78f, bot)
            path.lineTo(x + unit * 0.78f, top)
            path.lineTo(x + unit, top)
            x += unit
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, join = StrokeJoin.Miter),
        )
    }
}

/** Section title set like an inscription, with a meander as the rule. */
@Composable
fun InscriptionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        GreekKey(Modifier.fillMaxWidth().height(8.dp))
    }
}

@Composable
fun athenaNavItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
