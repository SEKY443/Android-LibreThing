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

## `android-zeroconf.patch`

`zeroconf/backend_builtin.go` calls `github.com/grandcat/zeroconf`'s `Register()`, which
enumerates network interfaces via Go's `net.Interfaces()` -- on Android that's netlink-only,
and SELinux denies untrusted apps `bind` on `netlink_route_socket` (visible in `adb logcat` as
`avc: denied { bind } ... tclass=netlink_route_socket`), so this always failed with "Could not
determine host IP addresses". The obvious non-netlink fallbacks don't work either: reading
`/sys/class/net` or `/proc/net/*` directly is *also* SELinux-denied for untrusted apps on
Android (confirmed on-device, not just documentation) -- so there is no way for the daemon
process itself to discover its network interface on Android, by any mechanism.

The real fix sources this from the Android app instead. `ConnectivityManager` and
`java.net.NetworkInterface` work fine in the exact same process/SELinux context (confirmed
on-device: Pixel 6 emulator, API 37, both return real data -- `wlan0`, index 16, `10.0.2.16/24`
-- where the Go-side equivalents fail), because they're serviced by `system_server` over Binder
rather than a raw netlink/sysfs read from app userspace. `GoLibrespotConfigWriter.kt` resolves
the active interface's name, OS index, and IPv4 address/prefix once per daemon launch and writes
them into four new config fields (`android_net_iface_name`, `android_net_iface_index`,
`android_net_ip`, `android_net_prefix_len`). On the daemon side:

- `zeroconf/zeroconf.go`'s `NewZeroconf` takes an optional `*AndroidInterface`; when set, it
  builds a `net.Interface{}` directly from the supplied index/name instead of calling
  `net.InterfaceByName` (a second, separate netlink call site the interfaces-to-advertise path
  would otherwise hit).
- `zeroconf/backend_builtin.go`'s `Register` uses `zeroconf.RegisterProxy` (skips the
  hostname/address lookup) whenever it already has an interface, using the supplied IP directly
  or falling back to the UDP-connect trick (`outboundIPv4`, ordinary unprivileged socket use, no
  netlink involved) if none was supplied.
- `daemon/player_state.go`'s `deviceAddressMask` (the `device_address_mask` connect-state
  metadata, a separate, non-fatal use of `net.Interfaces()`) also prefers the config-supplied
  address/prefix when present, for the same reason.

Verified on-device (Pixel 6 emulator, API 37): with Zeroconf discovery re-enabled in Settings,
the daemon now logs `zeroconf server listening on port ...` / `advertising on network interface
wlan0 (resolved by the Android app)` / `using built-in mDNS responder` and reaches
`Discoverable`, and stayed there through a multi-minute soak test with no crash and no further
netlink denials. The app's Settings workaround (Zeroconf off, Interactive/Cached-token
authentication) is no longer necessary but is left in place as a working alternative.

## `dealer/recv.go`'s `handleMessage` closed-channel race (no longer a local patch)

Not Android-specific, unlike the two above -- a genuine race in `dealer/recv.go`'s
`handleMessage`, just one that this app's usage pattern (switching Spotify Connect away from
this device and back repeatedly, in quick succession) exercised far more reliably than the
fork's normal usage does. Found on-device: switching away and back a few times crashed the
whole daemon process with `panic: send on closed channel` at `dealer/recv.go:217`, in
`handleMessage` -> `dispatchLoop`, restarting it (see `SpotifyConnectService`'s auto-restart)
and dropping the in-flight Connect session each time. `handleRequest` right below it already
guarded its own equivalent send with `select { case recv.c <- ...: case <-d.done: return }`;
`handleMessage`'s loop just never got the same treatment.

Fixed upstream directly in `SEKY443/go-librespot-termux` (commit `fd606a7`) rather than kept as
a patch here, since it isn't an Android-only issue -- every build already gets it for free.
