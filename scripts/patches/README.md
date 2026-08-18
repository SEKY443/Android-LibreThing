# Patches applied to SEKY443/go-librespot-termux for Android

`build-go-native.sh` applies every `*.patch` file in this directory (via `git apply`) to a
clean checkout of the daemon source before building, in filename order. These exist because
this is the first time this fork has actually run with `GOOS=android` end to end (it's
normally cross-compiled for `GOOS=linux` inside a Termux proot) — some code paths that were
never exercised turned out to need real fixes, found by running the daemon on-device.

## `android-clienttoken.patch`

Spotify's session setup unconditionally calls `POST https://clienttoken.spotify.com/v1/clienttoken`
(`session/client_token.go`) to get a client token before it will do anything else. Two issues
found running this on Android:

1. `platform.go`'s `GetPlatformSpecificData()` sent an all-zero `NativeAndroidData{}` for the
   `android` GOOS case -- no OS version, model, or vendor. Patched to populate real values via
   `getprop`, which is legitimately readable by any Android app (not privileged).
2. The POST's body is protobuf but the request never set `Content-Type`, since it's built via
   a raw `&http.Request{}` rather than `http.NewRequest` (which would infer it from a
   `*bytes.Reader` body).

**Neither of these turned out to fix the actual problem** -- verified by also patching the
error path to log the response body, which came back empty (`body: ""`) even after both
fixes. An empty body on a 400 usually means rejection at an edge/gateway layer before the
request reaches Spotify's application logic (has nothing to do with the protobuf payload
content), which would point at something environment-level -- e.g. the cross-compiled
binary's TLS fingerprint, or the emulator's outbound IP being flagged -- rather than anything
fixable by changing what this app sends. Kept anyway since both changes are strictly more
correct regardless (a real device fingerprint, a declared content type), and the response-body
logging is worth keeping for whoever picks this up next.

**Zeroconf is a separate, larger issue** and isn't fixed by anything here: `zeroconf/backend_builtin.go`
calls `github.com/grandcat/zeroconf`'s `Register()`, which enumerates network interfaces via
Go's `net.Interfaces()` -- on Android that's netlink-only with no fallback, and SELinux denies
untrusted apps `bind` on `netlink_route_socket` (visible in `adb logcat` as
`avc: denied { bind } ... tclass=netlink_route_socket`). The app's own workaround (Settings:
turn off Zeroconf discovery, set Authentication to Interactive or Cached Spotify access token)
avoids the crash by skipping that code path entirely, but a real fix needs interface/address
data sourced without netlink -- there's no config-only fix for this from the Android app side.
