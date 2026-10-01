## What and why

<!-- What this PR changes and why. Link the issue it closes: "Closes #123". -->

## How it was tested

<!-- Unit tests, Compose UI tests, or the device and steps you checked by hand. Screenshots for UI changes (light and dark). -->

## Checklist

- [ ] `./gradlew spotlessApply` and `./gradlew build` pass locally.
- [ ] New UI text is in `values/strings.xml` (English) and `values-de/strings.xml` (German).
- [ ] Colors, type, shapes and spacing come from the theme; new icons go through `TsuzukiIcons`.
- [ ] Screens handle loading, content, empty and error states.
- [ ] No new AniList requests in loops or in the background beyond what `docs/ANILIST_API.md` allows.
- [ ] Docs updated if behavior changed (`CLAUDE.md`, `docs/`).
- [ ] No secrets (client ID, tokens, keystores) in the diff.
