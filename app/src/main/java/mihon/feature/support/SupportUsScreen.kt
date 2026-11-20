package mihon.feature.support

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.vytheraProjects
import eu.kanade.presentation.util.Screen
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.yomi.GroupPosition
import tachiyomi.presentation.core.components.yomi.GroupRow
import tachiyomi.presentation.core.components.yomi.ShapeBadge
import tachiyomi.presentation.core.components.yomi.YomiGutter
import tachiyomi.presentation.core.i18n.stringResource

/** Support Yomi: a coffee tip, the Telegram community and the studio's other apps. */
class SupportUsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_support_us),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(contentPadding = padding) {
                item(key = "body") {
                    Text(
                        stringResource(MR.strings.yomi_support_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = YomiGutter, vertical = 8.dp),
                    )
                }
                item(key = "coffee") {
                    Spacer(Modifier.height(8.dp))
                    GroupRow(
                        title = stringResource(MR.strings.yomi_buy_coffee),
                        subtitle = stringResource(MR.strings.yomi_buy_coffee_summary),
                        position = GroupPosition.First,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { uriHandler.openUri(Constants.URL_SUPPORT_COFFEE) },
                        leading = {
                            ShapeBadge(
                                Icons.Outlined.LocalCafe,
                                polygon = MaterialShapes.Cookie9Sided,
                                container = MaterialTheme.colorScheme.primary,
                                content = MaterialTheme.colorScheme.onPrimary,
                            )
                        },
                        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                    )
                }
                item(key = "telegram") {
                    GroupRow(
                        title = stringResource(MR.strings.yomi_telegram),
                        subtitle = stringResource(MR.strings.yomi_telegram_summary),
                        position = GroupPosition.Last,
                        onClick = { uriHandler.openUri(Constants.URL_TELEGRAM) },
                        leading = {
                            ShapeBadge(Icons.AutoMirrored.Outlined.Send, polygon = MaterialShapes.Clover4Leaf)
                        },
                        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                    )
                }
                vytheraProjects()
                item(key = "end") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
