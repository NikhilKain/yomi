package eu.kanade.presentation.more

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.openInBrowser
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.yomi.GroupRow
import tachiyomi.presentation.core.components.yomi.ShapeBadge
import tachiyomi.presentation.core.components.yomi.YomiGutter
import tachiyomi.presentation.core.components.yomi.groupPosition
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Opens the shared Vythera feedback website with Yomi chosen. Nothing is sent from the app:
 * the user fills in and submits the form in their browser. Only the app version, device model
 * and Android version go into the link, never personal data.
 */
object FeedbackLink {
    private const val BASE = "https://vythera-feedback.narzo9990.workers.dev/"
    private const val APP_KEY = "yomi"

    enum class Type(val key: String) { Bug("bug"), Feature("feature"), Other("other"), Rate("rate") }

    fun uri(type: Type): Uri = Uri.parse(BASE).buildUpon()
        .appendQueryParameter("app", APP_KEY)
        .appendQueryParameter("type", type.key)
        .appendQueryParameter("version", BuildConfig.VERSION_NAME)
        .appendQueryParameter(
            "device",
            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
        )
        .appendQueryParameter("os", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        .appendQueryParameter("source", "app")
        .build()

    fun open(context: Context, type: Type) {
        // The real browser, not a WebView or an extension that claims the link: uploads need it.
        context.openInBrowser(uri(type), forceDefaultBrowser = true)
    }
}

private data class FeedbackChoice(
    val type: FeedbackLink.Type,
    val title: StringResource,
    val summary: StringResource,
    val icon: ImageVector,
    val polygon: RoundedPolygon,
)

private val feedbackChoices = listOf(
    FeedbackChoice(
        FeedbackLink.Type.Bug,
        MR.strings.yomi_feedback_bug,
        MR.strings.yomi_feedback_bug_summary,
        Icons.Outlined.BugReport,
        MaterialShapes.Cookie9Sided,
    ),
    FeedbackChoice(
        FeedbackLink.Type.Feature,
        MR.strings.yomi_feedback_feature,
        MR.strings.yomi_feedback_feature_summary,
        Icons.Outlined.Lightbulb,
        MaterialShapes.Sunny,
    ),
    FeedbackChoice(
        FeedbackLink.Type.Other,
        MR.strings.yomi_feedback_other,
        MR.strings.yomi_feedback_other_summary,
        Icons.Outlined.ChatBubbleOutline,
        MaterialShapes.Clover4Leaf,
    ),
    FeedbackChoice(
        FeedbackLink.Type.Rate,
        MR.strings.yomi_feedback_rate,
        MR.strings.yomi_feedback_rate_summary,
        Icons.Outlined.StarOutline,
        MaterialShapes.Flower,
    ),
)

/** The four feedback choices in Yomi's sheet; each opens the website on the matching form. */
@Composable
fun FeedbackSheet(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 16.dp)) {
            Text(
                stringResource(MR.strings.yomi_feedback),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = YomiGutter, vertical = 8.dp),
            )
            feedbackChoices.forEachIndexed { index, choice ->
                GroupRow(
                    title = stringResource(choice.title),
                    subtitle = stringResource(choice.summary),
                    position = groupPosition(index, feedbackChoices.size),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = {
                        FeedbackLink.open(context, choice.type)
                        onDismissRequest()
                    },
                    leading = { ShapeBadge(choice.icon, polygon = choice.polygon, size = 44.dp) },
                )
            }
            Text(
                stringResource(MR.strings.yomi_feedback_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = YomiGutter, vertical = 12.dp),
            )
        }
    }
}
