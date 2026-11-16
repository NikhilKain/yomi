package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.presentation.core.components.yomi.reveal
import tachiyomi.presentation.core.i18n.stringResource

/** A titled section of a page; its content supplies its own tonal surfaces. */
@Suppress("UnusedReceiverParameter")
@Composable
fun LazyItemScope.SectionCard(
    titleRes: StringResource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().reveal()) {
        if (titleRes != null) {
            Text(
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 10.dp),
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            content()
        }
    }
}
