package eu.kanade.presentation.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.yomi.YomiGutter
import tachiyomi.presentation.core.components.yomi.reveal
import tachiyomi.presentation.core.i18n.stringResource

/** The night-sky colour the moonlit mark is drawn on. */
private val MoonlitNight = Color(0xFF181526)

/** Yomi's moon-and-pages mark, cut into one of the expressive shapes. */
@Composable
fun YomiLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(MoonlitNight),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.yomi_moonlit_foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // The artwork keeps the launcher's safe-zone margin; inside a badge it can fill more.
            modifier = Modifier.fillMaxSize().scale(1.42f),
        )
    }
}

/** Who you are talking to: the mark, the name set large, and one line about the app. */
@Composable
fun LogoHeader(iconPadding: PaddingValues = PaddingValues()) {
    Row(
        modifier = Modifier
            .padding(iconPadding)
            .padding(start = YomiGutter, end = YomiGutter, top = 20.dp, bottom = 12.dp)
            .fillMaxWidth()
            .reveal(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YomiLogo(size = 76.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(MR.strings.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                stringResource(MR.strings.yomi_brand_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
