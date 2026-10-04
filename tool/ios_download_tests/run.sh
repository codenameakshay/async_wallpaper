#!/bin/sh
set -eu

SCRIPT_DIR=$(unset CDPATH; cd "$(dirname "$0")" && pwd -P)
REPO_ROOT=$(unset CDPATH; cd "$SCRIPT_DIR/../.." && pwd -P)
TEST_BINARY=$(mktemp "${TMPDIR:-/tmp}/async-wallpaper-ios-download.XXXXXX")
trap 'rm -f "$TEST_BINARY"' EXIT HUP INT TERM

swiftc -swift-version 5 -o "$TEST_BINARY" \
  "$REPO_ROOT/ios/async_wallpaper/Sources/async_wallpaper/WallpaperDownloadTransport.swift" \
  "$SCRIPT_DIR/main.swift"
"$TEST_BINARY"
