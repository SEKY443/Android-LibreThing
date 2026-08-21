# Librething v1.0 — Initial Release

Librething turns an Android phone or tablet into a standalone **Spotify Connect speaker**. It
runs [`go-librespot`](https://github.com/devgianlu/go-librespot) natively on-device as a managed
background service — no Termux, no computer, no separate hardware. Point Spotify at it like any
other Connect device and it plays through whatever speaker or headphones are attached.

This is the first tagged release.

## Highlights

- **Real Spotify Connect device** — discoverable automatically over Zeroconf/mDNS, or sign in
  directly with interactive login or a cached token.
- **Runs in the background**, screen off included, so a device can sit in a corner and just work
  like a dedicated speaker.
- **Two dashboard styles** — a clean, minimal "now playing" screen by default, or a classic
  card-based layout with a full bottom-nav shell (**Nerd mode**) for more visible controls.
- **Fake sleep** — fades the screen to pure black to protect OLED panels and stop the display
  from being a distraction, either automatically after a period of inactivity or with one tap.
  Wake it back up with a double-tap, a swipe, or single-tap (configurable), with a matching fade
  animation either way.
- **Auto-restart on crash** — if the background player dies unexpectedly, it relaunches itself
  automatically instead of silently going offline.
- **Predictive back gesture** support on the Settings screen (Android 13+).
- **Appearance options** — light/dark/system theme, plus tinted, pure-black, or blurred-cover-art
  dashboard backgrounds.
- **Playback controls** — bitrate selection, volume normalization (with album/track gain and
  pregain), autoplay, and a live volume slider synced with the device's own volume.
- **A built-in log console** with per-level color coding and copy/clear actions, for actually
  seeing what's going on when something doesn't work instead of guessing.
- **Reliability options** — keep-screen-on, wake lock, autostart on boot, and a battery
  optimization exemption shortcut, since Android will otherwise suspend a backgrounded speaker.
- Fully **string-resource backed UI**, laying the groundwork for future translations.

## Getting it

There's no packaged download yet for this release — see the [README](README.md) for build
instructions. Debug APKs are produced per-ABI (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) plus
a universal fallback.

**Requirements:** Android 7.0 (API 24) or newer.

## Known limitations

- No signed release build or Play Store listing yet — sideloading a self-built APK is the only
  install path for now.
- Building requires compiling the `go-librespot` daemon yourself via
  `scripts/build-go-native.sh` (Go + Android NDK); there's no prebuilt binary bundled in the repo.
- Not yet localized beyond English, though the UI is now fully resource-backed for that to
  happen.

## Credits

Built on [`go-librespot`](https://github.com/devgianlu/go-librespot) by
[**devgianlu**](https://github.com/devgianlu), licensed under GPLv3 — see [LICENSE](LICENSE) and
the README's licensing note for how it's distributed alongside this app.
