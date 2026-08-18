# go-librespot Connect (Android)

A standalone, background-capable Spotify Connect receiver for Android. It runs the
[`go-librespot`](https://github.com/SEKY443/go-librespot-termux) daemon as a managed child
process inside a foreground service and plays its audio through `AudioTrack` — no Termux, no
proot, no PulseAudio.

## Architecture

```
┌─────────────────────────── Android app process ───────────────────────────┐
│                                                                             │
│  Jetpack Compose UI  ──StateFlow──►  SpotifyConnectServiceState            │
│  (Dashboard/Settings)                        ▲                             │
│                                               │ updates                    │
│                                  ┌────────────┴─────────────┐              │
│                                  │  SpotifyConnectService     │            │
│                                  │  (Foreground Service)      │            │
│                                  └──────┬──────────┬─────────┘             │
│                          ProcessBuilder │          │ REST + /events WS     │
│                                         ▼          ▼ (127.0.0.1:24879)     │
│                          ┌──────────────────┐  ┌───────────────────────┐  │
│                          │ libgolibrespot.so│  │ GoLibrespotApiClient   │  │
│                          │ (go-librespot,   │◄─┤ (OkHttp)               │  │
│                          │  child process)  │  └───────────────────────┘  │
│                          └────────┬─────────┘                             │
│                                   │ raw s16le PCM, 44.1kHz stereo          │
│                                   ▼ (named pipe / FIFO)                    │
│                          ┌──────────────────┐                             │
│                          │  PipeAudioPlayer │──► AudioTrack ──► speakers   │
│                          └──────────────────┘                             │
└─────────────────────────────────────────────────────────────────────────┘
```

Why a subprocess instead of Gomobile/JNI bindings: `go-librespot` already ships exactly the
integration points this needs out of the box — an `audio_backend: pipe` driver that writes raw
PCM to a named pipe, and a REST + WebSocket API (`server.enabled`) for control and now-playing
state. Shipping the daemon as a plain ELF binary and driving it over those two interfaces is
far less code than re-plumbing its internals through Gomobile, and it's the same shape the
[`Windows build`](https://github.com/SEKY443/go-librespot-termux/blob/master/CROSS_COMPILE.md)
already uses (CGO + vcpkg + a target-specific `CC`) — this project targets Android instead of
`mingw`.

- **`GoProcessController`** launches the binary and parses its logrus output into the log
  console.
- **`PipeAudioPlayer`** creates the FIFO, and feeds the bytes it reads into a streaming
  `AudioTrack`.
- **`GoLibrespotApiClient`** talks to the daemon's loopback-only API server for status, events,
  and playback control (play/pause/next/prev/volume).
- **`SpotifyConnectServiceState`** is the single-process state hub the Compose UI observes and
  issues commands through — see its kdoc for why binding wasn't used.

The daemon binary is shipped as `app/src/main/jniLibs/<abi>/libgolibrespot.so` — named like a
shared library purely so Android's installer extracts it into the app's native library
directory with the execute bit set. It's a plain executable, not a `.so`.

## Prerequisites

- **Android Studio** (or just the Gradle wrapper + a JDK 21) for the app itself.
- **Go** (https://go.dev/dl/) and the **Android NDK** (installed via Android Studio's SDK
  Manager) for the native daemon build.
- `git`, `cmake`, `pkg-config` on `PATH` — `go-librespot` links `libvorbis`/`libflac`/`mpg123`
  via CGO, and `scripts/build-go-native.sh` uses [vcpkg](https://vcpkg.io) (bootstrapped
  automatically into `native/vcpkg`) to build Android versions of them.

## 1. Build the native daemon

```shell
export ANDROID_NDK_HOME=$HOME/Library/Android/sdk/ndk/<installed-version>
./scripts/build-go-native.sh
```

This clones `SEKY443/go-librespot-termux`, builds `libvorbis`/`libflac`/`mpg123` for each
Android ABI via vcpkg, cross-compiles `cmd/daemon` against them with the NDK's `clang`, and
writes:

```
app/src/main/jniLibs/arm64-v8a/libgolibrespot.so   # real devices (the common case)
app/src/main/jniLibs/armeabi-v7a/libgolibrespot.so # older 32-bit devices
app/src/main/jniLibs/x86_64/libgolibrespot.so      # emulator
```

Build just one ABI with `./scripts/build-go-native.sh arm64-v8a`. Re-run the script any time
you want to pick up upstream fork changes (it does a `git fetch` + checkout of `master`, or set
`GO_LIBRESPOT_REF` to pin a tag/commit).

## 2. Build and install the app

```shell
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

or open the project in Android Studio and run it normally — the jniLibs built in step 1 are
picked up automatically, no manifest/Gradle changes needed.

## 3. First run

1. Launch the app, grant the notification permission prompt (Android 13+).
2. On the **Settings** tab, set a device name and tap **Disable battery optimization for this
   app** — without this Android will eventually suspend the daemon in the background.
3. On the **Dashboard** tab, tap **Start**. The status indicator moves to *Discoverable* once
   the daemon's Zeroconf/mDNS advertisement is up.
4. Open Spotify on any device on the same Wi-Fi network — the phone should appear as a Connect
   speaker.

The **log console** on the Dashboard streams the daemon's own logrus output in real time
(filterable by level, with copy/clear actions) — check it first if discovery or playback isn't
working.

## Licensing note

`go-librespot` is **GPLv3**. This project ships it as a separate, unmodified-at-runtime
executable that the app launches as a subprocess and talks to over a pipe and a loopback HTTP
API — not linked into the app's own code — which is the same "mere aggregation" shape as
shipping any other GPL command-line tool alongside a program. If you distribute a build of this
app, you're expected to also make the corresponding `go-librespot` source available (the
upstream repo URL and ref used are recorded in `scripts/build-go-native.sh`) and not impose
restrictions that block someone from rebuilding/replacing that binary. This isn't legal advice;
if you plan to publish the app, have someone qualified confirm your specific distribution
channel is fine with that.
