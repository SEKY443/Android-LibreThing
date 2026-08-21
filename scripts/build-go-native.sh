#!/usr/bin/env bash
#
# Cross-compiles the go-librespot daemon from SEKY443/go-librespot-termux for Android and
# stages the resulting binaries as app/src/main/jniLibs/<abi>/libgolibrespot.so.
#
# They are named like shared libraries (and packaged with packaging.jniLibs.useLegacyPackaging
# = true in app/build.gradle.kts) purely so Android's installer extracts them into the app's
# nativeLibraryDir with the execute bit set -- they are plain ELF executables, not .so files.
# SpotifyConnectService launches this binary as a subprocess (see GoProcessController.kt) with
# `audio_backend: pipe` writing raw PCM into a FIFO that PipeAudioPlayer.kt feeds to AudioTrack;
# it never touches ALSA/PulseAudio.
#
# CGO is required (go-librespot links libvorbis/libflac/mpg123 for track decoding), so this
# needs the Android NDK's clang plus Android builds of those three libraries. vcpkg (with its
# built-in Android community triplets) builds those; the project's own CROSS_COMPILE.md uses
# the same vcpkg + PKG_CONFIG_PATH + CC pattern for its Windows/vcpkg build, this just targets
# Android instead.
#
# Prerequisites:
#   - Go toolchain              https://go.dev/dl/
#   - Android NDK (side-by-side install via Android Studio's SDK Manager is fine)
#   - git, cmake, pkg-config, ninja on PATH (vcpkg bootstraps most of its own tooling, but
#     still shells out to a host cmake/pkg-config)
#
# Usage:
#   ANDROID_NDK_HOME=/path/to/ndk/26.1.10909125 ./scripts/build-go-native.sh
#   ./scripts/build-go-native.sh arm64-v8a              # build a single ABI
#   ./scripts/build-go-native.sh arm64-v8a armeabi-v7a   # build a subset
#
# Environment overrides:
#   ANDROID_NDK_HOME   required; path to the NDK to build against
#   ANDROID_API        default 24, must be >= app/build.gradle.kts minSdk
#   GO_LIBRESPOT_REPO   default https://github.com/SEKY443/go-librespot-termux.git
#   GO_LIBRESPOT_REF    default master

set -euo pipefail

REPO_URL="${GO_LIBRESPOT_REPO:-https://github.com/SEKY443/go-librespot-termux.git}"
REPO_REF="${GO_LIBRESPOT_REF:-master}"
ANDROID_API="${ANDROID_API:-24}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
NATIVE_DIR="$PROJECT_ROOT/native"
SRC_DIR="$NATIVE_DIR/go-librespot-src"
VCPKG_DIR="$NATIVE_DIR/vcpkg"
JNI_LIBS_DIR="$PROJECT_ROOT/app/src/main/jniLibs"

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK install, e.g. \$ANDROID_HOME/ndk/26.1.10909125}"

for tool in go git cmake pkg-config; do
  command -v "$tool" >/dev/null 2>&1 || { echo "error: '$tool' not found in PATH" >&2; exit 1; }
done

case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
  Linux) HOST_TAG="linux-x86_64" ;;
  *) echo "error: unsupported host OS for this script: $(uname -s)" >&2; exit 1 ;;
esac

TOOLCHAIN_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"
[ -d "$TOOLCHAIN_BIN" ] || { echo "error: NDK toolchain not found at $TOOLCHAIN_BIN" >&2; exit 1; }

mkdir -p "$NATIVE_DIR"

echo "==> Fetching $REPO_URL @ $REPO_REF"
if [ ! -d "$SRC_DIR/.git" ]; then
  git clone "$REPO_URL" "$SRC_DIR"
fi
git -C "$SRC_DIR" fetch origin "$REPO_REF"
git -C "$SRC_DIR" checkout "$REPO_REF"
# Hard reset (not just fast-forward) so this is safe to re-run: it always lands on a clean
# copy of $REPO_REF before the patches below are (re-)applied, regardless of what a prior
# run of this script left in the working tree.
git -C "$SRC_DIR" reset --hard "origin/$REPO_REF" 2>/dev/null || git -C "$SRC_DIR" reset --hard "$REPO_REF"

echo "==> Applying Android patches"
for patch in "$SCRIPT_DIR"/patches/*.patch; do
  [ -e "$patch" ] || continue
  echo "  - $(basename "$patch")"
  git -C "$SRC_DIR" apply --whitespace=nowarn "$patch"
done

echo "==> Bootstrapping vcpkg"
if [ ! -d "$VCPKG_DIR/.git" ]; then
  git clone https://github.com/microsoft/vcpkg.git "$VCPKG_DIR"
fi
if [ ! -x "$VCPKG_DIR/vcpkg" ]; then
  "$VCPKG_DIR/bootstrap-vcpkg.sh" -disableMetrics
fi

# ABI -> (Go GOARCH, Go GOARM if any, vcpkg triplet, NDK clang target triple)
abi_goarch() { case "$1" in arm64-v8a) echo arm64 ;; armeabi-v7a) echo arm ;; x86_64) echo amd64 ;; esac; }
abi_goarm() { case "$1" in armeabi-v7a) echo 7 ;; *) echo "" ;; esac; }
abi_vcpkg_triplet() { case "$1" in arm64-v8a) echo arm64-android ;; armeabi-v7a) echo arm-neon-android ;; x86_64) echo x64-android ;; esac; }
abi_clang_target() { case "$1" in
  arm64-v8a) echo aarch64-linux-android ;;
  armeabi-v7a) echo armv7a-linux-androideabi ;;
  x86_64) echo x86_64-linux-android ;;
esac; }

ABIS=("$@")
if [ ${#ABIS[@]} -eq 0 ]; then
  ABIS=(arm64-v8a armeabi-v7a x86_64)
fi

for ABI in "${ABIS[@]}"; do
  GOARCH="$(abi_goarch "$ABI")"
  GOARM="$(abi_goarm "$ABI")"
  VCPKG_TRIPLET="$(abi_vcpkg_triplet "$ABI")"
  CLANG_TARGET="$(abi_clang_target "$ABI")"
  [ -n "$GOARCH" ] || { echo "error: unknown ABI '$ABI'" >&2; exit 1; }

  CC="$TOOLCHAIN_BIN/${CLANG_TARGET}${ANDROID_API}-clang"
  [ -x "$CC" ] || { echo "error: missing NDK clang for $ABI: $CC" >&2; exit 1; }

  echo "==> [$ABI] vcpkg install (triplet $VCPKG_TRIPLET) -- builds libvorbis/libflac/mpg123"
  ANDROID_NDK_HOME="$ANDROID_NDK_HOME" "$VCPKG_DIR/vcpkg" install \
    --triplet "$VCPKG_TRIPLET" \
    --x-manifest-root="$SRC_DIR" \
    --x-install-root="$SRC_DIR/vcpkg_installed"

  PKG_CONFIG_PATH="$SRC_DIR/vcpkg_installed/$VCPKG_TRIPLET/lib/pkgconfig"
  [ -d "$PKG_CONFIG_PATH" ] || { echo "error: vcpkg didn't produce $PKG_CONFIG_PATH" >&2; exit 1; }

  OUT_DIR="$JNI_LIBS_DIR/$ABI"
  mkdir -p "$OUT_DIR"

  echo "==> [$ABI] go build (GOARCH=$GOARCH${GOARM:+ GOARM=$GOARM}, CC=$(basename "$CC"))"
  (
    cd "$SRC_DIR"
    export CGO_ENABLED=1
    export GOOS=android
    export GOARCH="$GOARCH"
    [ -n "$GOARM" ] && export GOARM="$GOARM"
    export CC="$CC"
    export PKG_CONFIG_PATH="$PKG_CONFIG_PATH"
    # NOT 16KB-page-aligned (see git history for the attempt): Android 15+'s 16KB-page-size
    # compliance flags (-z max-page-size=16384 etc.) left the last LOAD segment (Go's
    # BSS/heap-reservation segment) imperfectly aligned -- a known rough edge in Go's
    # external-linking output. That's silently absorbed by Android's page-size-compat shim,
    # but that shim only exists on Android 15+; on older versions (confirmed on Android 11)
    # the malformed segment boundary makes the linker's own GNU_RELRO mprotect() call fail
    # outright with ENOMEM, so the daemon never starts at all. Actual 16KB-page-size hardware
    # is still essentially nonexistent, and that same compat shim transparently handles a
    # plain 4KB-aligned binary like this one when it *does* show up -- so standard alignment
    # is strictly the more broadly compatible choice today.
    go build -trimpath \
      -ldflags="-s -w" \
      -o "$OUT_DIR/libgolibrespot.so" ./cmd/daemon
  )
  chmod 755 "$OUT_DIR/libgolibrespot.so"
  echo "==> [$ABI] wrote $OUT_DIR/libgolibrespot.so"
done

echo
echo "Done. Rebuild the app (./gradlew assembleDebug) to bundle the updated binaries."
