# Cloud sync

Yomi syncs your library, reading progress, categories, history, tracking, extension repositories and
settings between devices through **your own** Google Drive. There is no Yomi server. The app can
only see the one file it creates in your Drive (`drive.file` scope).

## For people using Yomi

1. **Settings → Sync → Sign in with Google**.
2. Pick your account in the browser and allow access. The browser says "Signed in — you can close
   this tab".
3. Do the same on every other device with the same Google account.

That is all. The first device uploads its library as the starting point (`yomi_sync.tachibk` in your
Drive). Every other device merges its own library with it on its first sync; nothing is wiped.

**Automatic sync** is on by default: Yomi syncs when it opens, after you read and every hour. Turn it
off to sync only with **Sync now**. Everything else (schedule, Wi-Fi or charging only, what to sync,
extensions, using your own Google project) is under **Advanced options**.

---

## For the owner: Yomi's built-in sign-in

Yomi ships with one Google OAuth client so users never touch Google Cloud. You create it once.

**Use a separate Google account for this** (for example a Vythera developer account), not your
personal one. The client cannot be used to get into any account, but if it were ever abused Google
would act on the project, and that should stay away from your personal account.

1. Go to <https://console.cloud.google.com/> signed in with that account and create a project, for
   example `yomi-sync`. **Do not attach a billing account.** The Drive API is free.
2. **APIs & Services → Library → Google Drive API → Enable.**
3. **APIs & Services → OAuth consent screen** (Google Auth Platform → Branding / Audience):
   - App name `Yomi`, a support email, a developer contact email.
   - Audience: **External**.
   - Data access / scopes: add `.../auth/drive.file` and `.../auth/userinfo.email`. Both are
     non-sensitive, so Google does not need to review the app.
   - Do **not** upload a logo yet: a logo triggers brand verification. Add it later if you want.
   - **Publish** the app (Audience → Publishing status → *In production*). In *Testing*, sign-ins
     expire after 7 days and only listed test users can sign in.
4. **Credentials → Create credentials → OAuth client ID**, application type **Desktop app**, name
   `Yomi Android`. Nothing else to fill in.
5. Copy the **Client ID** and **Client secret** into a new file outside the repository,
   `D:\Project_keys\yomi\gdrive-client.properties`:

   ```properties
   clientId=1234567890-abc.apps.googleusercontent.com
   clientSecret=GOCSPX-...
   ```

6. Build as usual. `build/yomi-logs/build-yomi.sh` reads that file into `YOMI_GDRIVE_CLIENT_ID` and
   `YOMI_GDRIVE_CLIENT_SECRET`, which Gradle turns into `BuildConfig` fields. The values never go into
   Git. Any other build (CI, a release script) only needs those two environment variables.

Without the file the app still works: **Sign in with Google** is disabled with a note, and users can
enter their own Google Cloud client under **Advanced options**.

**Why a Desktop app client.** Google rejects custom URI scheme redirects for Android clients, and its
Android replacement needs Play Services, which Yomi avoids. A Desktop client allows the loopback flow:
Yomi opens a one-shot listener on `127.0.0.1`, sends you to the browser and catches the redirect
locally, with PKCE.

**If the client ever leaks or is abused**, nobody can reach anyone's account with it. In the worst
case Google limits or suspends the project, and sync stops until you create a new secret
(Credentials → the client → *Add secret*, then delete the old one), update the properties file and
ship a new build. Users then sign in once more.

---

## When it syncs

With **Automatic sync** on (the default), Yomi syncs at these moments. Each one can be changed under
**Settings → Sync → Advanced options**:

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
  Yomi backup. You can download it and feed it to **Restore backup** at any time, including if you
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
