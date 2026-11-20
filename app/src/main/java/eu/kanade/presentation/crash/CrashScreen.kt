package eu.kanade.presentation.crash

import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.remember
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.FeedbackLink
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.util.CrashLogUtil
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen

@Composable
fun CrashScreen(
    exception: Throwable?,
    onRestartClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    InfoScreen(
        icon = Icons.Outlined.BugReport,
        headingText = stringResource(MR.strings.crash_screen_title),
        subtitleText = stringResource(MR.strings.crash_screen_description, stringResource(MR.strings.app_name)),
        acceptText = stringResource(MR.strings.pref_dump_crash_logs),
        onAcceptClick = {
            scope.launch {
                CrashLogUtil(context).dumpLogs(exception)
            }
        },
        rejectText = stringResource(MR.strings.crash_screen_restart_application),
        onRejectClick = onRestartClick,
    ) {
        val saved = remember {
            runCatching { Injekt.get<BasePreferences>().saveCrashLogs.get() }.getOrDefault(false)
        }
        if (saved) {
            Text(
                text = stringResource(MR.strings.yomi_crash_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
            )
        }
        FilledTonalButton(
            onClick = { FeedbackLink.open(context, FeedbackLink.Type.Bug) },
            modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
        ) {
            Text(stringResource(MR.strings.yomi_report_crash))
        }
        Box(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .clip(MaterialTheme.shapes.small)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = exception.toString(),
                modifier = Modifier
                    .padding(all = MaterialTheme.padding.small),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CrashScreenPreview() {
    TachiyomiPreviewTheme {
        CrashScreen(exception = RuntimeException("Dummy")) {}
    }
}
