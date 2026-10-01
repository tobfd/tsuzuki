# Images

Product images for the README and a later Play Store listing (made 2026-10-01). One look throughout: the app in English, AniList blue, dark theme.

| Path | What |
|---|---|
| `banner.png` | README banner, 1280 x 640 (also fits GitHub's social preview). |
| `framed/` | Screenshots in a device frame with a transparent background (WebP), for the row in the README: `home`, `lists`, `detail`, `widgets`. |
| `screenshots/` | The same screenshots without a frame: phone (Tobias's account on a Pixel 9, 1080 x 2424), tablet (emulator as a 2560 x 1600 tablet, guest mode) and widgets (emulator, debug sample data). |
| `store/` | Play Store assets: `feature-graphic.png` (1024 x 500), phone images with captions (1080 x 1920) and one tablet image (1920 x 1080). |

Every status bar is replaced by a neutral one (12:00, Wi-Fi, signal, full battery). No other AniList users appear: the phone shots avoid the feed and the Social tabs, and the sample friends in the widget data have made-up names.

## Making them again

1. Phone: on a device with your own account, the app in English, AniList blue and the dark theme, take `adb exec-out screencap -p` shots named `phone_<home|lists|detail|share>_dark.png`. Long-press a list row to open the detail page (a tap opens the list editor).
2. Tablet: on the emulator, `adb shell wm size 2560x1600`, `wm density 320`, `settings put system user_rotation 0`; guest mode, Browse, search, open a title; save `tablet_detail_dark.png`. Reset with `wm size reset; wm density reset`.
3. Widgets: the debug build's `WidgetSampleDataActivity` and `PinWidgetActivity` (see `CLAUDE.md`, Widgets). Its sample session has a fake token, so keep the app away from the API: run `python docs/images/tools/cdn_only_proxy.py` and point the emulator at it (`adb shell settings put global http_proxy 10.0.2.2:8888`); it lets only AniList's image CDN through. Open Home and Lists once so the covers are cached, switch on airplane mode (otherwise the widgets' own updates fail against the proxy and the widgets say "Offline"), then pin the widgets. Save the two home pages as `w_dark_1.png` (In Progress, Next episode) and `w_dark_2.png` (Friends' activity). Afterwards: `settings put global http_proxy :0`, `settings delete global global_http_proxy_host` and `global_http_proxy_port`.
4. Render the logo glyph into the same folder: `python docs/images/tools/logo.py <folder>/glyph_white.png 600 '#ffffff'` and `... glyph_navy.png 600 '#0b2a3d'` (needs matplotlib).
5. `python docs/images/tools/make_images.py <folder>` (needs Pillow) writes everything in this folder.
