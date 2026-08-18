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
you want to pick up upstream fork changes (it does a `git fetch` + hard reset to `master`, or
set `GO_LIBRESPOT_REF` to pin a tag/commit) — the reset is deliberate and safe to rely on: it
always lands on a clean copy of that ref before applying the patches in `scripts/patches/`
(see that directory's README for what they do and why), so the daemon source under `native/`
never needs to be hand-edited.

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

## Tested status

Verified end-to-end on an arm64-v8a emulator (API 37): the daemon binary launches with a real
cross-compiled `libgolibrespot.so`, loads its generated config, binds its loopback API server,
the app's WebSocket client connects to `/events`, and the daemon resolves and reaches Spotify's
real access-point/dealer/spclient infrastructure over the network. Three real bugs surfaced and
were fixed by this testing, all still relevant if you're changing this code:

- `GoLibrespotApiClient.getStatus()` didn't catch the `ConnectException` that's expected while
  the daemon is still starting up — crashed the app on every cold start. Covered by
  `GoLibrespotApiClientTest`.
- The daemon needs `$HOME` set in its process environment: Go's `os.UserConfigDir()` runs
  unconditionally while `cmd/daemon` computes the *default* value for `--config_dir`, before it
  ever looks at the `--config_dir` override this app always passes — and Android app processes
  don't have `$HOME` set. See the comment in `GoProcessController.start()`.
- Access points default to port 4070, which the emulator's (and plenty of real) networks block
  outbound; `prefer_firewall_friendly_ports: true` is now always set in the generated config.

**Fixed — client-token request (`scripts/patches/android-clienttoken.patch`)**: `session.NewSession`
unconditionally requests a Spotify client token before any credential flow can proceed, and on
Android that request was failing with an opaque, empty-body `400`. Two independent real bugs,
both patched:

1. `session/client_token.go` built its `POST` as a raw `&http.Request{}` literal with the body
   wrapped in `io.NopCloser`, which hides the underlying `*bytes.Reader`'s length from
   `net/http`'s auto-detection. Left at its zero value, `ContentLength` defaults to 0, so Go's
   `Transport` sent `Content-Length: 0` while still writing the real body afterward — a
   malformed request that Spotify's edge rejects before its application logic ever sees it,
   with no error body (which is exactly the symptom observed). Fixed by building the request
   with `http.NewRequest` instead, which sets `ContentLength`, `Host`, `GetBody`, and
   `Proto`/`ProtoMajor`/`ProtoMinor` correctly.
2. `platform.go`'s `GetOS`/`GetPlatform`/`GetPlatformSpecificData` reported native Android
   identity (`OS_ANDROID`, `PLATFORM_ANDROID_ARM`, `NativeAndroidData`) for `GOOS=android`.
   Verified in isolation (same request, varying only this field, sent from a plain desktop Go
   program) that Spotify's client-token endpoint accepts this project's client ID as a Linux
   client but rejects it — still with an empty `400` — as a native Android one; it's evidently
   only approved for desktop platforms. Fixed by reporting Linux identity for `GOOS=android`
   throughout, the same as this fork's normal Termux/proot (`GOOS=linux`) build would.

With both fixes, `credentials.type: interactive` (or `spotify_token`) now runs all the way
through: the daemon obtains a real client token and prints a genuine
`https://accounts.spotify.com/authorize?...` URL to log in with. Verified on-device, repeatably,
across clean reinstalls.

**Fixed — zeroconf itself (`scripts/patches/android-zeroconf.patch`)**: `credentials.type:
zeroconf` (and `zeroconf_enabled: true` generally) used to fail unconditionally.
`zeroconf/backend_builtin.go` calls `github.com/grandcat/zeroconf`'s `Register()`, which
enumerates interfaces via Go's `net.Interfaces()` — on Linux/Android that's netlink-only, and
Android's SELinux policy denies untrusted apps `bind` on `netlink_route_socket` (visible in
`logcat` as `avc: denied { bind } ... tclass=netlink_route_socket`). The obvious non-netlink
fallbacks don't work either — reading `/sys/class/net` or `/proc/net/*` directly is *also*
SELinux-denied for untrusted apps (confirmed on-device) — so the daemon process itself has no
way to discover its network interface on Android by any mechanism.

The real fix sources this from the Android app instead: `ConnectivityManager` and
`java.net.NetworkInterface` work fine in the exact same process/SELinux context (confirmed
on-device — real interface name, index, and IPv4 address/prefix, where the Go-side equivalents
fail), because they're serviced by `system_server` over Binder rather than a raw netlink/sysfs
read from app userspace. `GoLibrespotConfigWriter` resolves this once per daemon launch and
passes it into four new config fields; the daemon uses them to skip `net.Interfaces()` /
`net.InterfaceByName()` entirely and register via `zeroconf.RegisterProxy` instead. See
`scripts/patches/README.md`'s `android-zeroconf.patch` section for the full mechanism.

Verified on-device, repeatably: with Zeroconf discovery enabled, the daemon reaches
**Discoverable** and stays there — confirmed through a multi-minute soak test with no crash and
no further SELinux denials once startup completes. The app's earlier workaround (Settings:
Zeroconf off, Interactive/Cached-token authentication) still works and is left in place as an
alternative, but is no longer necessary.

**On the "random crashes" reported during earlier testing**: with zeroconf now actually working,
a multi-minute on-device soak test (Zeroconf enabled, app in the foreground, `adb logcat`
monitored continuously) showed the daemon process staying alive throughout, with no `FATAL
EXCEPTION`, `Fatal signal`, ANR, or unexplained process death. Earlier apparent hangs/crashes are
consistent with what was already root-caused before this fix: the daemon failing fast on the
zeroconf fatal error above (`main.go`'s `log.Fatal`, process exit code 1) reads, from the outside,
like the app going silent — no further log lines are the process actually being gone, not a hang.
No separate, still-unexplained crash was found; if a new one turns up, capture the log console
output (Dashboard's copy button) and the `adb logcat` output around the time of the drop.

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
