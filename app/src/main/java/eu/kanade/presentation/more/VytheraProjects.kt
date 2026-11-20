package eu.kanade.presentation.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.yomi.GroupRow
import tachiyomi.presentation.core.components.yomi.SectionHeader
import tachiyomi.presentation.core.components.yomi.groupPosition
import tachiyomi.presentation.core.i18n.stringResource

/** Other apps by Vythera, the studio behind Yomi. Links open the store page or the repository. */
private data class VytheraApp(
    val name: String,
    val summary: StringResource,
    @DrawableRes val icon: Int,
    val url: String,
)

private val vytheraApps = listOf(
    VytheraApp(
        "Vyxel Apps",
        MR.strings.yomi_app_vyxel,
        R.drawable.vythera_vyxel,
        "https://github.com/NikhilKain/vyxel-apps",
    ),
    VytheraApp(
        "Stride",
        MR.strings.yomi_app_stride,
        R.drawable.vythera_stride,
        "https://play.google.com/store/apps/details?id=com.vythera.stride",
    ),
    VytheraApp(
        "Hanami",
        MR.strings.yomi_app_hanami,
        R.drawable.vythera_hanami,
        "https://github.com/NikhilKain/hanami-app",
    ),
    VytheraApp(
        "Auralis",
        MR.strings.yomi_app_auralis,
        R.drawable.vythera_auralis,
        "https://play.google.com/store/apps/details?id=com.vythera.auralis",
    ),
    VytheraApp(
        "Relay",
        MR.strings.yomi_app_relay,
        R.drawable.vythera_relay,
        "https://play.google.com/store/apps/details?id=com.vythera.relay",
    ),
)

/** "More from Vythera" as grouped rows, followed by the studio's credit line. */
internal fun LazyListScope.vytheraProjects() {
    item(key = "vythera-title") { SectionHeader(stringResource(MR.strings.yomi_more_from_vythera)) }
    vytheraApps.forEachIndexed { index, app ->
        item(key = "vythera-${app.name}") {
            val uriHandler = LocalUriHandler.current
            GroupRow(
                title = app.name,
                subtitle = stringResource(app.summary),
                position = groupPosition(index, vytheraApps.size),
                onClick = { uriHandler.openUri(app.url) },
                leading = {
                    Image(
                        painter = painterResource(app.icon),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium),
                    )
                },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
    item(key = "vythera-credit") { VytheraCredit() }
}

@Composable
internal fun VytheraCredit(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(
            stringResource(MR.strings.yomi_credit_line),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
