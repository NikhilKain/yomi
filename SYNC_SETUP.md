# Cloud sync setup

This fork syncs your library, reading progress, categories, history, tracking, extension
repositories and settings between devices through **your own** Google Drive. Nothing is uploaded
anywhere else, and there is no server to run.

This build does not ship shared Google OAuth credentials. Create an OAuth client for
your own Google Cloud
project.

---

## What this build is

| | |
|---|---|
| Application ID | `com.manga.yomi` (installs alongside stock Mihon) |
| Signing keystore | `D:\Project_keys\yomi\key` |
| Key alias | `yomi` |
| Certificate SHA-1 | `CC:16:1D:E1:03:0C:C0:AD:4D:E9:D3:7E:1D:B3:40:D1:19:8F:E2:99` |
| OAuth client type | **Desktop app** |
| OAuth redirect | `http://127.0.0.1:<port>` — handled automatically, nothing to register |

Use the same keystore for every device. The OAuth client no longer depends on the signing
certificate, but Android still refuses to install an update over an app signed with a different key,
so losing the `.jks` means every device has to uninstall and start over. The SHA-1 above is recorded
only for reference.

### Why "Desktop app" and not "Android"

Google **disables custom URI scheme redirects on Android OAuth clients** and rejects them with
`Error 400: invalid_request — this app doesn't comply with Google's OAuth 2.0 policy`. Google's
documented replacement for Android is the Play Services sign-in SDK, which this fork deliberately
does not depend on.

The flow Google *does* still commit to supporting for a self-built app is the **loopback IP flow on
a Desktop app client**. The app opens a one-shot HTTP listener on `127.0.0.1`, sends you to the
browser, and catches the redirect locally. Nothing listens on an externally reachable interface, and
the port is chosen fresh each time. No redirect URI has to be registered, because Google accepts any
loopback port for a Desktop client.

---

## 1. Create the Google OAuth client

1. Go to <https://console.cloud.google.com/> and create a project (any name, e.g. `mihon-sync`).

2. Enable the Drive API: **APIs & Services → Library → Google Drive API → Enable**.

3. Configure the consent screen: **APIs & Services → OAuth consent screen**
   - User type: **External**
   - App name, your support email, your developer email — that is all that is required.
   - On the **Scopes** step you do not need to add anything. The app requests
     `drive.file` and `email`, both of which are **non-sensitive**, so Google will never ask you to
     verify the app.
   - Add your own Google account under **Test users**.
   - Then **publish the app** (`Publishing status → In production`). This is important: while the
     app is in *Testing*, Google expires refresh tokens after 7 days and you would have to sign in
     again every week. Because the scopes are non-sensitive, publishing needs no review and takes
     effect immediately.

4. Create the client: **APIs & Services → Credentials → Create credentials → OAuth client ID**
   - Application type: **Desktop app**
   - Name it anything.
   - There is nothing else to fill in — no package name, no SHA-1, no redirect URI.

5. Copy both the **Client ID** (`...apps.googleusercontent.com`) and the **Client secret**
   (`GOCSPX-...`).

> If you already made an **Android** client, delete it or just leave it unused — it will not work
> for this flow.

---

## 2. Set up the first device

1. Install the APK.
2. **Settings → Sync**.
3. Paste the **Client ID** and the **Client secret**. Both are required for a Desktop app client.
4. Tap **Sign in with Google**, pick your account, and allow access.
5. The browser shows "Signed in — you can close this tab". Go back to the app.

The app immediately uploads your current library as the starting point. It creates one file in your
Drive called `yomi_sync.tachibk`.

## 3. Set up every other device

Same APK, same steps, same Google account. On the first sync the device pulls the shared library
down and merges it with whatever is already on that device — nothing is wiped.

---

## When it syncs

All of these are individually toggleable under **Settings → Sync**:

- On a schedule — every hour by default, adjustable from 15 minutes to daily, or manual only
- When the app opens
- After a library update finishes
- When you leave the reader (batched, so a long reading session costs one sync, not one per chapter)
- On demand, via **Sync now**

Optional constraints: Wi-Fi only, and charging only.

---

## How conflicts are resolved

The database keeps a `version` counter on every manga and every chapter, incremented by SQL triggers
whenever you actually change something — mark a chapter read, bookmark it, turn a page, add or
remove an entry from the library. Sync compares those counters rather than wall-clock timestamps,
which are unreliable across devices.

- Higher `version` wins.
- Equal versions, higher `last_modified_at` wins.
- A genuine tie merges conservatively: read progress takes the furthest page, and bookmarks are kept.

Consequences worth knowing:

- **Marking something unread propagates.** It is a real edit, so it bumps the version and wins over
  a stale "read" from the other device. Only in an exact tie does read state get OR-ed.
- **Removing an entry from your library propagates**, via the separate `favorite_modified_at`
  timestamp, instead of the other device silently adding it back.
- **Reading the same chapter on both devices** keeps the furthest page and the longer read duration.
- **Categories are matched by name.** Renaming a category on one device only will produce a second
  category rather than a rename, until the other device catches up.

---

## Extensions and sources

Extension **repositories** sync automatically and silently.

Extension **APKs** cannot be installed silently by an ordinary Android app. When a synced library
needs a source this device does not have, the app downloads the matching extension and Android shows
its normal install prompt for each one. You get a notification listing what is needed. Turn this off
with **Install missing extensions** if you would rather do it by hand.

Downloaded chapter files are not synced — only the library metadata and your progress. Downloads
stay local to each device.

---

## Things to be aware of

- **The sync file is a real backup.** `yomi_sync.tachibk` in your Drive is a byte-for-byte valid
  Mihon backup. You can download it and feed it to **Restore backup** at any time, including if you
  ever abandon this fork.
- **Sensitive settings are excluded by default.** Tracker login tokens are only included if you turn
  on *Include sensitive settings*. Everything goes to your own private Drive either way.
- **Concurrent syncs.** Google Drive has no atomic compare-and-swap, so the app checks the remote
  revision immediately before writing and retries the whole merge if another device got there first.
  Two devices syncing within the same second could still interleave; because the merge is
  version-driven and idempotent, the losing device folds the result in on its next sync rather than
  losing data.
- **Signing out** leaves the Drive file and your local library untouched.
- **The client secret is not really secret.** Google issues one for Desktop clients and knows it
  cannot be kept confidential in an installed app; that is why the flow also uses PKCE. It is stored
  under the app's private-preference prefix, so it is excluded from backups and from sync unless you
  explicitly enable *Include sensitive settings*.
- **If sign-in fails with `invalid_request`**, you are almost certainly still pointing at an Android
  OAuth client. Create a Desktop app client instead (see above).

---

## Rebuilding

```bash
./gradlew :app:assembleRelease
```

Signing is read from `keystore.properties` at the repo root (gitignored). The APKs land in
`app/build/outputs/apk/release/` — `app-universal-release.apk` is the one to copy to other devices.
