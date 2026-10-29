package dev.izumi.appopsnext.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.ui.theme.mainPageHeadingWeight

@Composable
fun MainPageSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = horizontalPadding, vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = mainPageHeadingWeight(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun MainPageEntryIcon(
    @DrawableRes iconRes: Int,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = RoundedCornerShape(9.dp),
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
fun MainPageChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_ph_caret_right),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Keeps lazy rows individually addressable while presenting one visual group. */
fun mainPageGroupShape(index: Int, count: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) 18.dp else 0.dp,
    topEnd = if (index == 0) 18.dp else 0.dp,
    bottomStart = if (index == count - 1) 18.dp else 0.dp,
    bottomEnd = if (index == count - 1) 18.dp else 0.dp,
)

@Composable
fun MainPageGroupDivider(startIndent: Dp = 60.dp) {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(start = startIndent, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}
