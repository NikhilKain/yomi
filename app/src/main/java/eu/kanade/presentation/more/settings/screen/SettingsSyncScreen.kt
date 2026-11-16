package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.sync.GoogleDriveLogin
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.SyncBackend
import tachiyomi.domain.sync.service.SyncPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSyncScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_sync

    @Composable
    override fun getPreferences(): List<Preference> {
        val syncPreferences = Injekt.get<SyncPreferences>()
        val backend by syncPreferences.syncBackend().collectAsState()

        return listOf(
            getAccountGroup(syncPreferences),
        ) + if (backend == SyncBackend.NONE) {
            emptyList()
        } else {
            listOf(
                getScheduleGroup(syncPreferences),
                getContentGroup(syncPreferences),
            )
        }
    }

    @Composable
    private fun getAccountGroup(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val clientId by syncPreferences.googleDriveClientId().collectAsState()
        val backend by syncPreferences.syncBackend().collectAsState()
        val accountEmail by syncPreferences.googleDriveAccountEmail().collectAsState()
        val refreshToken by syncPreferences.googleDriveRefreshToken().collectAsState()
        val needsReauth by syncPreferences.syncNeedsReauth().collectAsState()

        // Being signed in is about holding a usable credential, not about which backend was picked
        // once. Deriving it from the backend alone meant an expired refresh token still rendered as
        // "Signed in as ...", leaving the user with a permanently failing sync and no way back to
        // the sign-in button.
        val signedIn = backend == SyncBackend.GOOGLE_DRIVE && refreshToken.isNotBlank()

        val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()

        if (!signedIn) {
            val loginState by GoogleDriveLogin.state.collectAsState()

            // Sign-in completes while the user is over in the browser, so report the outcome
            // whenever we come back to this screen rather than from the click handler.
            LaunchedEffect(loginState) {
                when (val current = loginState) {
                    is GoogleDriveLogin.State.Success -> {
                        context.toast(MR.strings.sync_login_success)
                        GoogleDriveLogin.consume()
                    }
                    is GoogleDriveLogin.State.Failed -> {
                        context.toast(context.stringResource(MR.strings.sync_login_failed, current.reason))
                        GoogleDriveLogin.consume()
                    }
                    else -> Unit
                }
            }

            items += Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.googleDriveClientId(),
                title = stringResource(MR.strings.sync_gdrive_client_id),
                subtitle = clientId.ifBlank { stringResource(MR.strings.sync_gdrive_client_id_summary) },
            )
            items += Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.googleDriveClientSecret(),
                title = stringResource(MR.strings.sync_gdrive_client_secret),
                subtitle = stringResource(MR.strings.sync_gdrive_client_secret_summary),
            )
            items += Preference.PreferenceItem.TextPreference(
                title = if (needsReauth) {
                    stringResource(MR.strings.sync_gdrive_sign_in_again)
                } else {
                    stringResource(MR.strings.sync_gdrive_sign_in)
                },
                subtitle = when {
                    loginState is GoogleDriveLogin.State.WaitingForBrowser ->
                        stringResource(MR.strings.sync_gdrive_waiting_browser)
                    needsReauth -> stringResource(MR.strings.sync_gdrive_session_expired)
                    else -> stringResource(MR.strings.sync_gdrive_not_signed_in)
                },
                onClick = {
                    if (clientId.isBlank()) {
                        context.toast(MR.strings.sync_gdrive_missing_client_id)
                    } else {
                        try {
                            GoogleDriveLogin.start(context)
                        } catch (e: ActivityNotFoundException) {
                            logcat(LogPriority.ERROR, e) { "No browser to open the Google consent page" }
                            context.toast(MR.strings.sync_gdrive_no_browser)
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Could not start Google Drive sign-in" }
                            context.toast(e.message)
                        }
                    }
                },
            )
        } else {
            items += Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.sync_gdrive_sign_out),
                subtitle = accountEmail.ifBlank { stringResource(MR.strings.sync_service_google_drive) }
                    .let { stringResource(MR.strings.sync_gdrive_signed_in_as, it) },
                onClick = {
                    syncPreferences.logoutGoogleDrive()
                    syncPreferences.syncBackend().set(SyncBackend.NONE)
                    SyncDataJob.setupTask(context)
                },
            )
        }

        items += Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.sync_setup_guide),
            subtitle = stringResource(MR.strings.sync_setup_guide_summary),
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SETUP_GUIDE_URL.toUri())) },
        )

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.sync_account),
            preferenceItems = items,
        )
    }

    @Composable
    private fun getScheduleGroup(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val lastSync by syncPreferences.lastSyncTimestamp().collectAsState()
        val lastError by syncPreferences.lastSyncError().collectAsState()

        val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.sync_now),
                subtitle = if (lastSync == 0L) {
                    stringResource(MR.strings.sync_never)
                } else {
                    stringResource(MR.strings.sync_last, relativeTimeSpanString(lastSync))
                },
                onClick = {
                    if (SyncDataJob.isRunning(context)) {
                        context.toast(MR.strings.sync_in_progress)
                    } else {
                        SyncDataJob.startNow(context, manual = true)
                        context.toast(MR.strings.syncing_library)
                    }
                },
            ),
            Preference.PreferenceItem.ListPreference(
                preference = syncPreferences.syncInterval(),
                entries = mapOf(
                    0 to stringResource(MR.strings.sync_frequency_manual),
                    15 to stringResource(MR.strings.sync_frequency_15min),
                    30 to stringResource(MR.strings.sync_frequency_30min),
                    60 to stringResource(MR.strings.sync_frequency_1hour),
                    180 to stringResource(MR.strings.sync_frequency_3hour),
                    360 to stringResource(MR.strings.update_6hour),
                    720 to stringResource(MR.strings.update_12hour),
                    1440 to stringResource(MR.strings.update_24hour),
                ),
                title = stringResource(MR.strings.sync_frequency),
                onValueChanged = {
                    SyncDataJob.setupTask(context, it)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = syncPreferences.syncOnlyOnUnmetered(),
                title = stringResource(MR.strings.sync_only_unmetered),
                onValueChanged = {
                    // Constraints are baked into the scheduled job, so it has to be rebuilt.
                    SyncDataJob.setupTask(context)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = syncPreferences.syncOnlyWhenCharging(),
                title = stringResource(MR.strings.sync_only_charging),
                onValueChanged = {
                    SyncDataJob.setupTask(context)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = syncPreferences.syncOnAppStart(),
                title = stringResource(MR.strings.sync_on_app_start),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = syncPreferences.syncOnLibraryUpdate(),
                title = stringResource(MR.strings.sync_on_library_update),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = syncPreferences.syncOnChapterRead(),
                title = stringResource(MR.strings.sync_on_chapter_read),
            ),
        )

        if (lastError.isNotBlank()) {
            items += Preference.PreferenceItem.InfoPreference(
                stringResource(MR.strings.sync_last_error, lastError),
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_sync),
            preferenceItems = items,
        )
    }

    @Composable
    private fun getContentGroup(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val appSettings by syncPreferences.syncAppSettings().collectAsState()
        val sourceSettings by syncPreferences.syncSourceSettings().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.sync_what),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncChapters(),
                    title = stringResource(MR.strings.chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncCategories(),
                    title = stringResource(MR.strings.categories),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncHistory(),
                    title = stringResource(MR.strings.history),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncTracking(),
                    title = stringResource(MR.strings.track),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncAppSettings(),
                    title = stringResource(MR.strings.app_settings),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncSourceSettings(),
                    title = stringResource(MR.strings.source_settings),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncPrivateSettings(),
                    title = stringResource(MR.strings.private_settings),
                    enabled = appSettings || sourceSettings,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncExtensions(),
                    title = stringResource(MR.strings.sync_install_extensions),
                    subtitle = stringResource(MR.strings.sync_install_extensions_summary),
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.sync_library_always),
                ),
            ),
        )
    }

    private const val SETUP_GUIDE_URL = "https://console.cloud.google.com/apis/credentials"
}
