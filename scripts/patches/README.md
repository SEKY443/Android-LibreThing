# Patches applied to SEKY443/go-librespot-termux for Android

`build-go-native.sh` applies every `*.patch` file in this directory (via `git apply`) to a
clean checkout of the daemon source before building, in filename order. These exist because
this is the first time this fork has actually run with `GOOS=android` end to end (it's
normally cross-compiled for `GOOS=linux` inside a Termux proot) — some code paths that were
never exercised turned out to need real fixes, found by running the daemon on-device.

## `android-clienttoken.patch`

Spotify's session setup unconditionally calls `POST https://clienttoken.spotify.com/v1/clienttoken`
(`session/client_token.go`) to get a client token before it will do anything else, and on
Android that call was failing with an opaque, empty-body `400`. Two independent real bugs,
found by isolating each variable against the live endpoint (not guessed at):

1. **Malformed request framing.** The POST was built as a raw `&http.Request{}` literal with
   `Body: io.NopCloser(bytes.NewReader(body))`. Wrapping in `io.NopCloser` hides the underlying
   `*bytes.Reader`'s `Len()` from `net/http`'s auto-detection, so `ContentLength` was left at
   its zero value. Go's `Transport` then sent `Content-Length: 0` while still writing the real
   body afterward -- a malformed request that gets rejected at the edge, before Spotify's own
   application logic ever sees it, with an empty error body (exactly the symptom). Confirmed by
   sending byte-identical protobuf payloads both ways from a plain desktop Go program: the
   `&http.Request{}` literal form got a `400`, `http.NewRequest` got a `200` with a real
   granted token. Fixed by switching to `http.NewRequest`, which sets `ContentLength`, `Host`,
   `GetBody`, and `Proto`/`ProtoMajor`/`ProtoMinor` correctly in one call.
2. **Unapproved platform claim.** `platform.go`'s `GetOS`, `GetPlatform`, and
   `GetPlatformSpecificData` all reported native Android identity (`OS_ANDROID`,
   `PLATFORM_ANDROID_ARM`, `NativeAndroidData`) for `GOOS=android`. Confirmed in isolation (same
   request, varying only this one field) that Spotify's client-token endpoint accepts this
   project's client ID as a Linux client but rejects it -- still with an empty `400` -- as a
   native Android one. It's evidently only approved for desktop platforms, not native mobile.
   Fixed by reporting Linux identity for `GOOS=android` throughout (OS, platform, *and*
   platform-specific data need to agree, or the mismatch itself could look suspicious), the
   same as this fork's normal Termux/proot (`GOOS=linux`) build already does.

Both were necessary; neither alone was sufficient. With both applied, `credentials.type:
interactive` (and presumably `spotify_token`) now completes: the daemon obtains a real client
token and prints a genuine `https://accounts.spotify.com/authorize?...` URL. Verified
on-device, repeatably, across clean reinstalls (fresh device ID each time, so it isn't
something narrower like a flagged device ID either).

**Zeroconf is a separate, still-open issue** and isn't fixed by anything here:
`zeroconf/backend_builtin.go` calls `github.com/grandcat/zeroconf`'s `Register()`, which
enumerates network interfaces via Go's `net.Interfaces()` -- on Android that's netlink-only
with no fallback, and SELinux denies untrusted apps `bind` on `netlink_route_socket` (visible
in `adb logcat` as `avc: denied { bind } ... tclass=netlink_route_socket`). The app's own
workaround (Settings: turn off Zeroconf discovery, set Authentication to Interactive or Cached
Spotify access token) avoids the crash by skipping that code path entirely, but a real fix
needs interface/address data sourced without netlink (a real interface index, not just a fake
one, since `zeroconf.RegisterProxy`'s IP override still enumerates interfaces the same way when
none are supplied) -- there's no config-only fix for this from the Android app side.
