package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** The ready-made choices; any other number of days is "Custom". */
private val PRESETS = listOf(1, 7, 30)

@Composable
internal fun updateIntervalLabel(days: Int): String = when (days) {
    1 -> stringResource(MR.strings.yomi_update_interval_daily)
    7 -> stringResource(MR.strings.yomi_update_interval_weekly)
    30 -> stringResource(MR.strings.yomi_update_interval_monthly)
    else -> pluralStringResource(MR.plurals.yomi_update_every_n_days, days, days)
}

@Composable
internal fun UpdateIntervalDialog(
    current: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf(current !in PRESETS) }
    var selected by rememberSaveable { mutableStateOf(current) }
    var customText by rememberSaveable { mutableStateOf(if (current in PRESETS) "" else current.toString()) }
    val customDays = customText.toIntOrNull()?.takeIf { it in 1..365 }
    val result = if (custom) customDays else selected

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.yomi_update_interval)) },
        text = {
            Column(Modifier.selectableGroup()) {
                PRESETS.forEach { days ->
                    IntervalOption(
                        label = updateIntervalLabel(days),
                        selected = !custom && selected == days,
                        onClick = {
                            custom = false
                            selected = days
                        },
                    )
                }
                IntervalOption(
                    label = stringResource(MR.strings.yomi_update_interval_custom),
                    selected = custom,
                    onClick = { custom = true },
                )
                if (custom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { text -> customText = text.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(MR.strings.yomi_update_interval_custom_label)) },
                        supportingText = { Text(stringResource(MR.strings.yomi_update_interval_custom_hint)) },
                        isError = customText.isNotEmpty() && customDays == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = result != null, onClick = { result?.let(onConfirm) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun IntervalOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
