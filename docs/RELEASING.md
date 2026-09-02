# Releasing Ultron Wear

Releases are automatic. **Push to `main` and you get one.**

[`.github/workflows/release.yml`](../.github/workflows/release.yml) builds a **signed** release
APK and AAB and publishes them to a GitHub Release, tagging the exact commit it built.

| You do | You get |
| --- | --- |
| `git push` to `main` | patch bump off the highest tag — `v2.1.0` becomes `v2.1.1` |
| `git tag v2.2.0 && git push origin v2.2.0` | exactly `v2.2.0` — how you cut a minor or major |
| `[skip release]` in the commit message | pushed, not shipped |
| edit only `*.md` / `docs/` / `.gitignore` | nothing ships |

A tag you push always wins over the auto-bump, and always ships — `[skip release]` does not
apply to it, because an explicit tag is an explicit request.

One-time setup is below. You only do it once, and it involves a private key that must never
reach this repository.

---

## The version comes from the tag

| Tag       | versionName | versionCode |
| --------- | ----------- | ----------- |
| `v2.1.0`  | `2.1.0`     | `20100`     |
| `v2.10.3` | `2.10.3`    | `21003`     |
| `v3.0.0`  | `3.0.0`     | `30000`     |

`versionCode = major*10000 + minor*100 + patch`, which is monotonic as long as your tags are.
The workflow rejects any tag not shaped like `vMAJOR.MINOR.PATCH`.

The literals in `app/build.gradle.kts` remain the default for every non-release build; the
workflow overrides them with `-PultronVersionName` / `-PultronVersionCode`.

---

## One-time setup

### 1. Create the upload keystore

Run this **on your machine**, not in CI. It will prompt for a password and for your name and
organisation — the identity fields do not matter for sideloading, but the password does.

```bash
keytool -genkeypair -v -keystore ultron-wear-release.jks -alias ultron -keyalg RSA -keysize 4096 -validity 10000
```

Then keep the resulting `ultron-wear-release.jks` somewhere safe and backed up — a password
manager or an encrypted drive.

> **This key is not recoverable.** Android identifies an app by its signing key, so if you lose
> it you cannot ship an update that upgrades an existing install — every user (you) has to
> uninstall and reinstall, losing the local attendance ledger. Back it up before you continue.
>
> Do not commit it. `*.jks` is not currently in `.gitignore`; if you keep the file inside the
> repo folder, add it first.

### 2. Base64-encode it for GitHub

GitHub secrets hold text, so the binary keystore has to be encoded:

```bash
base64 -w0 ultron-wear-release.jks > keystore.b64
```

On macOS, `base64 -i ultron-wear-release.jks -o keystore.b64` (no `-w0`).

### 3. Set the repository secrets

In **Settings → Secrets and variables → Actions → New repository secret**, add four secrets.
Set these yourself in the GitHub UI — never paste a keystore password into a file, a commit,
or a chat window.

| Secret                      | Value                                                    |
| --------------------------- | -------------------------------------------------------- |
| `RELEASE_KEYSTORE_BASE64`   | the entire contents of `keystore.b64`                     |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password from step 1                         |
| `RELEASE_KEY_ALIAS`         | `ultron` (or whatever `-alias` you used)                  |
| `RELEASE_KEY_PASSWORD`      | the key password — same as the store password unless you set a separate one |

Delete `keystore.b64` afterwards.

### 4. Optional but recommended

| Secret                        | Effect if missing                                                     |
| ----------------------------- | --------------------------------------------------------------------- |
| `IGOR_BASE_URL`               | app builds, but ships with no Igor endpoint and stays offline-only     |
| `IGOR_API_KEY`                | same                                                                   |
| `GOOGLE_SERVICES_JSON_BASE64` | app builds, but **attendance push notifications will not work**; the workflow logs a warning rather than failing |

---

## What the workflow does

1. **Refuses to start** if any of the four signing secrets is missing. An unsigned APK will not
   install on a watch, so publishing one would produce a release that looks fine and is useless.
2. Validates the tag shape and derives the version.
3. Runs unit tests and `lintVitalRelease`.
4. Builds `assembleRelease` + `bundleRelease` with R8 in full mode.
5. **Verifies the APK signature with `apksigner`** before publishing — the build succeeding is
   not the same as the artifact being correctly signed.
6. Publishes the APK, the AAB and a `SHA256SUMS.txt` to a GitHub Release with auto-generated
   notes.
7. Uploads the **R8 mapping file** as a 365-day artifact. This is the only way to read a stack
   trace from a minified build; without it every future crash report is unreadable.
8. Deletes the decoded keystore from the runner.

The keystore is decoded to `$RUNNER_TEMP`, outside the workspace, so it cannot be swept into an
artifact upload glob.

---

## Installing on the watch

```bash
adb connect <watch-ip>:5555
adb install -r ultron-wear-2.1.0.apk
```

Enable **Developer options → ADB debugging → Debug over Wi-Fi** on the watch first; the IP is
shown under that setting.

---

## Profiling a release build locally

Release is the only build worth judging performance from — a debuggable Compose build is 2–5×
slower. But an unsigned APK cannot be installed to measure it. For that case only:

```bash
./gradlew installRelease -PdebugSignRelease=true
```

This signs the release build with the local debug key. Never set it in CI: a debug-signed
install cannot later be updated by a properly signed release, so you would have to uninstall
(and lose the attendance ledger) to get back onto real builds.

---

## CI on every push

[`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) runs two jobs on
pushes and PRs to `main`:

- **`build`** — lint, unit tests, debug APK.
- **`release-path`** — `lintVitalRelease` + `assembleRelease`, **unsigned**.

The second exists because R8 and `lintVitalRelease` run only on the release variant, so the
debug job cannot see failures they produce — a reflection-only class getting stripped, or a
lint error that is fatal for release and a warning for debug. It needs no secrets and publishes
nothing; its only job is to guarantee that pushing a tag will not fail.
