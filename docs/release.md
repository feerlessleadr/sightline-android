# Releasing Sightline for Android

A release is an APK built and signed by GitHub when a version tag is pushed. The signing key is never in this repository
and never printed in a log. This follows the same method as dockge-for-android.

## One-time setup (the owner does this; Claude does not touch the key)

1. **Make a signing key** on your PC, outside this folder (a private key folder that you back up offline). If you lose it,
   nobody can ever publish an update that installs over the old app, and the key must never change.

   ```powershell
   $keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
   & $keytool -genkeypair -v -storetype PKCS12 -keystore sightline-android.jks -alias sightline -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Sightline for Android, O=feerlessleadr"
   ```

   It asks for a password twice (a PKCS12 key uses one password for the store and the key). The `-dname` text is the
   certificate's name. It is public (anyone can read it from the APK), so keep it free of personal or work details.
2. **Give GitHub the key** as four repository secrets (Settings > Secrets and variables > Actions), or with the GitHub CLI
   (each command asks you to type or paste the value; nothing is stored in a file):

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("sightline-android.jks")) | gh secret set SIGNING_KEYSTORE_BASE64
   gh secret set SIGNING_STORE_PASSWORD
   gh secret set SIGNING_KEY_ALIAS      # sightline
   gh secret set SIGNING_KEY_PASSWORD   # the same password again
   ```
3. **Pin the key's fingerprint.** The release workflow only publishes an APK signed with the pinned certificate, so a wrong
   key can never be released. The fingerprint is public. Read it (the password stays with you):

   ```powershell
   keytool -list -v -keystore sightline-android.jks -alias sightline | Select-String "SHA256:"
   ```

   Turn the value into lower case hex without colons (`($fp -replace ':','').ToLower()`) and give it to Claude (or put it
   in `EXPECTED_CERT_SHA256` in `.github/workflows/release.yml`). The workflow fails while it is empty.

## Publishing

Only when you say "publish it":

1. Write the release notes in `docs/releases/vX.Y.Z.md` (see the README there) and merge them to main.
2. Tag the commit on main and push the tag:

   ```powershell
   git tag v0.1.0
   git push origin v0.1.0
   ```

The Release workflow (`.github/workflows/release.yml`) then:

- stops at once if a secret or the pinned certificate is missing, if the tag is not on main, or if the notes file is missing;
- runs the tests, builds and signs the release APK with the version from the tag (`v0.1.0` gives version `0.1.0`, version
  code `1000`), and deletes the key from the runner;
- checks with `apksigner` that the APK has a v2 or v3 signature and the pinned certificate, and with `aapt2` that the
  package, version name and version code match the tag;
- creates the GitHub release with your notes, the APK, its SHA-256 and the certificate's SHA-256.

Rules: tags look like `v1.2.3` only. A tag that produced a public release is never moved or deleted; a bad release is
replaced by the next patch version. (A tag whose workflow failed before anything was public can be deleted and made again.)

## Trying the release build on your phone first

The release build is shrunk and obfuscated (R8), which can break things the debug build does not. CI builds it on every
push, and before the first release you should use it:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease "-PreleaseDebugSigned=true"
```

This signs it with the debug key and gives it its own app id, `com.feerlessleadr.sightline.rtest`, so it sits next to the
debug build and can never stop you installing the real release later. The APK is in `app\build\outputs\apk\release\`.
Do not share that one: it is only for testing.

## Signing on your own PC (optional)

Make a git-ignored `keystore.properties` in the project folder with `storeFile`, `storePassword`, `keyAlias` and
`keyPassword`, then `.\gradlew.bat assembleRelease` signs with your key. The file is already in `.gitignore`.
