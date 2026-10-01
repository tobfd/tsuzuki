# Contributing to Tsuzuki

Thanks for wanting to help. Tsuzuki is a small, personal project, so please open an issue before you start on anything bigger than a bug fix; that way nobody builds something that doesn't fit the plan in `docs/PRODUCT.md`.

## Getting set up

Follow "Build from source" in the [README](../README.md). You need your own AniList API client; never commit its ID, an access token or a keystore.

## How the code is organized

[`CLAUDE.md`](../CLAUDE.md) holds the rules for the whole codebase (it is also what Claude Code reads). The short version:

- Modules: `feature/*` depends on `core/*` only, never on another feature. GraphQL and Room types stay in `core/data`; features only see `core/model`.
- UI: a stateless `XxxScreen(uiState, onEvent...)` with previews, driven by a ViewModel exposing `StateFlow<UiState>`. Colors, type, shapes and spacing come from the theme (`MaterialTheme`, `TsuzukiSpacing`, `TsuzukiSizes`), icons from `TsuzukiIcons`.
- Strings: English in `values/strings.xml`, German in `values-de/strings.xml`. Both complete in every PR that adds UI text.
- AniList: the API currently allows 30 requests a minute. One query per screen, no prefetching, no loops over pages. See [`docs/ANILIST_API.md`](../docs/ANILIST_API.md).
- Tests: unit tests for ViewModels, repositories and mappers; hand-written fakes from `core/testing` rather than mocks.

## Pull requests

- Branch from `main`. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org) (`feat(lists): add +1 with undo`), one small change per commit.
- Run `./gradlew spotlessApply` and then `./gradlew build` (compile, unit tests, lint) before you push. CI runs `./gradlew spotlessCheck build`.
- Fill in the PR template. For UI changes, add screenshots in light and dark.
- Don't copy code from other AniList clients unless their license allows it; reading them for ideas is fine.

## License

Tsuzuki is licensed under the [GNU General Public License v3.0](../LICENSE). By contributing, you agree that your contribution is licensed under the same terms.
