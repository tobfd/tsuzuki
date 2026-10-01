# App icon proposals (M12)

Three proposals for the final app icon. Nothing is decided or wired into the app yet; the launcher icon
stays the M0 placeholder until Tobias picks one.

Each sheet shows the adaptive icon as Pixel Launcher crops it (circle) and as other launchers do
(squircle), plus the themed icon (Android 13+, monochrome layer, tinted by the system) in light and
dark. `small-sizes.png` shows all three at 96, 64 and 48 px on a dark home screen.

| | Idea | Notes |
|---|---|---|
| **A · Blau** (`a-blau.png`) | The 続 glyph in white on AniList blue (`#3DB4F2` → `#0284C7`). | Closest to the current placeholder; very readable at small sizes. Close to AniList's own blue, which may read as an official AniList app (AniList API terms). |
| **B · Weiter** (`b-weiter.png`) | Blue glyph on AniList's dark navy, above a progress bar with a playhead. | Tells "what comes next" (続き) and "progress" without words; dark icons stand out on light wallpapers. The bar gets small below 64 px. |
| **C · Cover** (`c-cover.png`) | Two stacked 2:3 covers, the front one with the glyph. | Speaks of lists of shows; the light background is calm next to other icons. Least distinctive at 48 px. |

The glyph is the same artwork as `ic_logo_glyph` (Noto Sans JP Bold, SIL OFL 1.1). The images are
rendered by `render.py` (Python with matplotlib and Pillow) from that vector, so a picked proposal can be turned into the adaptive icon
layers (`ic_launcher_foreground`, `ic_launcher_background`, `ic_launcher_monochrome`) without redrawing.
