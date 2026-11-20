package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.BuildConfig
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

/**
 * Yomi's sync settings. Most people see one card: sign in with Google, sync now, and a single
 * automatic switch. Schedule, payload, extensions and a custom Google Cloud project sit behind
 * "Advanced options".
 */
object SettingsSyncScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_sync

    @Composable
    override fun getPreferences(): List<Preference> {
        val syncPreferences = Injekt.get<SyncPreferences>()
        val backend by syncPreferences.syncBackend().collectAsState()
        val refreshToken by syncPreferences.googleDriveRefreshToken().collectAsState()
        var advanced by rememberSaveable { mutableStateOf(false) }

        // Signed in means holding a usable credential, not only having picked a backend once: an
        // expired refresh token must bring the sign-in button back.
        val signedIn = backend == SyncBackend.GOOGLE_DRIVE && refreshToken.isNotBlank()

        return buildList {
            add(getAccountGroup(syncPreferences, signedIn, advanced) { advanced = !advanced })
            if (advanced) {
                if (signedIn) {
                    add(getScheduleGroup(syncPreferences))
                    add(getContentGroup(syncPreferences))
                }
                add(getOwnProjectGroup(syncPreferences, signedIn))
            }
        }
    }

    @Composable
    private fun getAccountGroup(
        syncPreferences: SyncPreferences,
        signedIn: Boolean,
        advanced: Boolean,
        onToggleAdvanced: () -> Unit,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val ownClientId by syncPreferences.googleDriveClientId().collectAsState()
        val accountEmail by syncPreferences.googleDriveAccountEmail().collectAsState()
        val needsReauth by syncPreferences.syncNeedsReauth().collectAsState()
        val lastSync by syncPreferences.lastSyncTimestamp().collectAsState()
        val lastError by syncPreferences.lastSyncError().collectAsState()
        val canSignIn = ownClientId.isNotBlank() || BuildConfig.GDRIVE_CLIENT_ID.isNotBlank()

        val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()

        if (!signedIn) {
            val loginState by GoogleDriveLogin.state.collectAsState()

            // Sign-in finishes while the user is in the browser, so report the result whenever
            // they come back to this screen rather than from the click handler.
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

            items += Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.yomi_sync_intro))
            items += Preference.PreferenceItem.TextPreference(
                title = if (needsReauth) {
                    stringResource(MR.strings.sync_gdrive_sign_in_again)
                } else {
                    stringResource(MR.strings.yomi_sync_sign_in)
                },
                subtitle = when {
                    !canSignIn -> stringResource(MR.strings.yomi_sync_unavailable)
                    loginState is GoogleDriveLogin.State.WaitingForBrowser ->
                        stringResource(MR.strings.sync_gdrive_waiting_browser)
                    needsReauth -> stringResource(MR.strings.sync_gdrive_session_expired)
                    else -> null
                },
                // Not `enabled = canSignIn`: settings hide disabled rows, and the row must stay to explain.
                onClick = onClick@{
                    if (!canSignIn) {
                        context.toast(MR.strings.yomi_sync_unavailable)
                        return@onClick
                    }
                    try {
                        GoogleDriveLogin.start(context)
                    } catch (e: ActivityNotFoundException) {
                        logcat(LogPriority.ERROR, e) { "No browser to open the Google consent page" }
                        context.toast(MR.strings.sync_gdrive_no_browser)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Could not start Google Drive sign-in" }
                        context.toast(e.message)
                    }
                },
            )
        } else {
            items += Preference.PreferenceItem.TextPreference(
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
            )
            items += Preference.PreferenceItem.SwitchPreference(
                preference = syncPreferences.syncAutomatically(),
                title = stringResource(MR.strings.yomi_sync_automatic),
                subtitle = stringResource(MR.strings.yomi_sync_automatic_summary),
                onValueChanged = {
                    // The schedule is part of the background job, so rebuild it after the change.
                    syncPreferences.syncAutomatically().set(it)
                    SyncDataJob.setupTask(context)
                    true
                },
            )
            if (lastError.isNotBlank()) {
                items += Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.sync_last_error, lastError),
                )
            }
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
            title = stringResource(
                if (advanced) MR.strings.yomi_sync_hide_advanced else MR.strings.yomi_sync_show_advanced,
            ),
            subtitle = if (advanced) null else stringResource(MR.strings.yomi_sync_show_advanced_summary),
            onClick = onToggleAdvanced,
        )

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.sync_service_google_drive),
            preferenceItems = items,
        )
    }

    @Composable
    private fun getScheduleGroup(syncPreferences: SyncPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val automatic by syncPreferences.syncAutomatically().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.yomi_sync_when),
            preferenceItems = listOf(
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
                    enabled = automatic,
                    onValueChanged = {
                        SyncDataJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnAppStart(),
                    title = stringResource(MR.strings.sync_on_app_start),
                    enabled = automatic,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnChapterRead(),
                    title = stringResource(MR.strings.sync_on_chapter_read),
                    enabled = automatic,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnLibraryUpdate(),
                    title = stringResource(MR.strings.sync_on_library_update),
                    enabled = automatic,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnlyOnUnmetered(),
                    title = stringResource(MR.strings.sync_only_unmetered),
                    enabled = automatic,
                    onValueChanged = {
                        // Constraints are baked into the scheduled job, so it has to be rebuilt.
                        syncPreferences.syncOnlyOnUnmetered().set(it)
                        SyncDataJob.setupTask(context)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnlyWhenCharging(),
                    title = stringResource(MR.strings.sync_only_charging),
                    enabled = automatic,
                    onValueChanged = {
                        syncPreferences.syncOnlyWhenCharging().set(it)
                        SyncDataJob.setupTask(context)
                        true
                    },
                ),
            ),
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

    @Composable
    private fun getOwnProjectGroup(syncPreferences: SyncPreferences, signedIn: Boolean): Preference.PreferenceGroup {
        val context = LocalContext.current
        val ownClientId by syncPreferences.googleDriveClientId().collectAsState()

        val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()
        if (signedIn) {
            // The signed-in session belongs to the client that issued it, so switching needs a new sign-in.
            items += Preference.PreferenceItem.InfoPreference(
                stringResource(MR.strings.yomi_sync_own_project_signed_in),
            )
        } else {
            items += Preference.PreferenceItem.InfoPreference(
                stringResource(MR.strings.yomi_sync_own_project_summary),
            )
            items += Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.googleDriveClientId(),
                title = stringResource(MR.strings.sync_gdrive_client_id),
                subtitle = ownClientId.ifBlank { stringResource(MR.strings.sync_gdrive_client_id_summary) },
            )
            items += Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.googleDriveClientSecret(),
                title = stringResource(MR.strings.sync_gdrive_client_secret),
                subtitle = stringResource(MR.strings.sync_gdrive_client_secret_summary),
            )
        }
        items += Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.sync_setup_guide),
            subtitle = stringResource(MR.strings.sync_setup_guide_summary),
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SETUP_GUIDE_URL.toUri())) },
        )

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.yomi_sync_own_project),
            preferenceItems = items,
        )
    }

    private const val SETUP_GUIDE_URL = "https://console.cloud.google.com/apis/credentials"
}
