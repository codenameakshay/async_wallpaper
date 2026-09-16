# Migration guide

## From 3.2 to 3.3

The Dart API is unchanged. Update the version and check these Android behavior changes.

- **Rotation URLs must be HTTPS.** Rotation now loads sources through the same bounded loader as `applyWallpaper`. HTTP URLs, redirects to HTTP, non-image responses, and images over the size limits fail to load.
- **Rotation runs without a foreground service.** The plugin no longer declares `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, or `SCHEDULE_EXACT_ALARM`. If your app declared these only for rotation, you can remove them. The interval and charging triggers use `WorkManager`. The time-of-day trigger uses one inexact alarm.
- **Charging rotation** applies at most once per interval while the device charges, not once per plug-in.
- **The first scheduled rotation** runs one interval after start. `startWallpaperRotation` still applies the first wallpaper immediately.
- **`WallpaperRotationStatus.totalCount`** is the number of requested sources. `cachedCount` is the number that loaded.
- **Static and rotation wallpapers** are drawn at the display size. Launchers no longer pan the wallpaper.
- **Picasso was removed.** If your app used the plugin's transitive Picasso or OkHttp 3 dependency, add it to your own app.
- **Downloads on Android 9 and older** need `WRITE_EXTERNAL_STORAGE`. See [Downloads on Android 9 and older](android-compatibility.md#downloads-on-android-9-and-older).
- **Results decided in Dart** (unsupported platform, invalid input, transport errors) now fill `home` and `lock` for each requested target. An unsupported video scale mode returns `unsupported` with `video-scale-unsupported`.
- **Off Android**, the rotation APIs return `unsupported` or a not-running status instead of calling the platform.
- **`AsyncWallpaper.debugHasClientOverride`** was removed. Tests can still call `debugSetClient`.

## From 3.1 to 3.2

3.1 calls still compile. Move to the structured API to get per-target results.

| 3.1 | 3.2 and later |
|---|---|
| `setWallpaper(WallpaperRequest(sourceType: ..., source: ...))` | `applyWallpaper(StaticWallpaperRequest(source: WallpaperSource.url(...)))` |
| `setLiveWallpaper(...)` | `setVideoWallpaper(...)`, then `openLiveWallpaperPreview(...)` |
| `WallpaperResult.isSuccess` | `WallpaperOperationResult` with `status`, `home`, and `lock` |
| `goToHome: true` | Ignored. Navigate in your app after a result. |

- `previewOpened` and `awaitingUserConfirmation` do not mean the wallpaper was applied.
- Call `getCapabilities()` before you show optional flows such as live, OpenGL, or the system picker.
- The plugin no longer declares broad media or storage permissions. Declare what your own sources need.
- `setLiveWallpaper` is deprecated.

See [doc/issues-3.2.0.md](issues-3.2.0.md) for the issues that 3.2 resolved.

## From 2.x to 3.x

- Flutter `>=3.41.4`, Android `minSdk 24`, Java 17.
- Integer constants such as `AsyncWallpaper.HOME_SCREEN` became the `WallpaperTarget` enum.
- Boolean setters became typed request objects and result objects.
- The plugin no longer shows toasts. Show your own UI from the result.
