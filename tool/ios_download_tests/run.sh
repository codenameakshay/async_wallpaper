#!/bin/sh
set -eu

SCRIPT_DIR=$(unset CDPATH; cd "$(dirname "$0")" && pwd -P)
REPO_ROOT=$(unset CDPATH; cd "$SCRIPT_DIR/../.." && pwd -P)
TEST_BINARY=$(mktemp "${TMPDIR:-/tmp}/async-wallpaper-ios-download.XXXXXX")
trap 'rm -f "$TEST_BINARY"' EXIT HUP INT TERM

case $(uname -s) in
  Darwin)
    MACOS_SDK_PATH=$(xcrun --sdk macosx --show-sdk-path)
    MACOS_PLATFORM_PATH=$(xcrun --sdk macosx --show-sdk-platform-path)
    XCTEST_FRAMEWORKS="$MACOS_PLATFORM_PATH/Developer/Library/Frameworks"
    xcrun --sdk macosx swiftc -sdk "$MACOS_SDK_PATH" \
      -F "$XCTEST_FRAMEWORKS" \
      -framework XCTest \
      -Xlinker -rpath -Xlinker "$XCTEST_FRAMEWORKS" \
      -swift-version 5 -o "$TEST_BINARY" \
      "$REPO_ROOT/ios/async_wallpaper/Sources/async_wallpaper/WallpaperDownloadTransport.swift" \
      "$SCRIPT_DIR/main.swift"
    ;;
  *)
    swiftc -swift-version 5 -o "$TEST_BINARY" \
      "$REPO_ROOT/ios/async_wallpaper/Sources/async_wallpaper/WallpaperDownloadTransport.swift" \
      "$SCRIPT_DIR/main.swift"
    ;;
esac
"$TEST_BINARY"
