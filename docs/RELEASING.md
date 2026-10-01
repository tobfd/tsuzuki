# Releasing

Releases are built by GitHub Actions (`.github/workflows/release.yml`) when a tag like `v1.2.3` is pushed: a signed release APK, attached to a GitHub release with generated release notes.

- **Version:** from the tag. `versionName` = `1.2.3`, `versionCode` = `1 * 10000 + 2 * 100 + 3` = `10203` (minor and patch stay below 100). Local builds keep `0.1.0` / `1`.
- **Signing:** only through GitHub secrets. The keystore never goes into Git (`*.jks`, `*.keystore` are git-ignored); the workflow decodes it into the runner's temp folder, builds without the configuration cache and deletes it again.
- **Release notes:** generated from the merged pull requests since the last tag, dependency updates in their own section (`.github/release.yml`).

## Secrets

| Secret | What |
|---|---|
| `ANILIST_CLIENT_ID` | The numeric AniList client ID (as in `local.properties`). |
| `RELEASE_KEYSTORE_BASE64` | The upload/release keystore (`.jks`), Base64-encoded in one line. |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore password. |
| `RELEASE_KEY_ALIAS` | The key alias, e.g. `tsuzuki`. |
| `RELEASE_KEY_PASSWORD` | The key password (with PKCS12 keystores the same as the keystore password). |

## One-time setup

1. Create the keystore (once, keep it and its passwords safe; every update must be signed with the same key):
   ```bash
   keytool -genkeypair -v -keystore tsuzuki-release.jks -alias tsuzuki -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Encode it in one line: `base64 -w0 tsuzuki-release.jks` (Linux, Git Bash), or in PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("tsuzuki-release.jks"))`.
3. Add the five secrets under Settings > Secrets and variables > Actions > New repository secret.

## A release

```bash
git checkout main && git pull
git tag v1.0.0
git push origin v1.0.0
```

The run shows up under Actions > Release; the APK appears under Releases as `tsuzuki-v1.0.0.apk`. A failed run can be repeated by deleting the tag (`git push --delete origin v1.0.0`, `git tag -d v1.0.0`) and pushing it again.

Signing locally works the same way: set `TSUZUKI_KEYSTORE_FILE`, `TSUZUKI_KEYSTORE_PASSWORD`, `TSUZUKI_KEY_ALIAS` and `TSUZUKI_KEY_PASSWORD` and run `./gradlew :app:assembleRelease --no-configuration-cache`.
