# Android compatibility

This page describes the Android contract for `async_wallpaper` 3.2. It is deliberately capability- and result-driven: Android wallpaper support is affected by API level, device policy, installed system UI, and OEM behavior, so no static compatibility list can replace `getCapabilities()` and the returned operation result.

## Baseline and capability check

- The plugin supports Android API 24 and later.
- Static home, lock, and both-target direct APIs use Android's wallpaper manager and are available only when the device allows wallpaper changes.
- Live and OpenGL wallpaper flows additionally need an installed/resolvable live-wallpaper service; OpenGL needs OpenGL ES 2.0 support.

Run a capability check while the app is foregrounded, then still handle an operation failure:

```dart
final capabilities = await AsyncWallpaper.getCapabilities();

if (!capabilities.supportsStaticWallpaper || !capabilities.canSetWallpaper) {
  // Hide or explain the direct static action in your application.
}
```

`WallpaperCapabilities` reports static/live/OpenGL support, home/lock/both availability, whether a system picker resolves, manufacturer, SDK level, and available OpenGL metadata. It is a snapshot, not a guarantee that a later policy or OEM UI decision will succeed.

## Static wallpapers

### Sources

| Input | `WallpaperSource` constructor | Android requirements |
| --- | --- | --- |
| Network image | `WallpaperSource.url('https://…')` | HTTPS only; response, image type, encoded size, and decoded dimensions are bounded. |
| Local image | `WallpaperSource.filePath(path)` | The file must remain readable by the app. |
| Provider image | `WallpaperSource.contentUri(uri)` | A readable `content://` URI; the host app is responsible for the grant lifecycle. |
| In-memory image | `WallpaperSource.bytes(bytes)` | Non-empty image bytes within the loader's limits. The request copies the bytes. |

The plugin does not add storage/media permissions. If an app receives an Android picker URI, it must retain a valid grant itself or copy the data to app-controlled storage before a delayed/background operation.

### Targets and outcomes

`WallpaperTarget.home`, `lock`, and `both` are supported for direct static operations where Android policy allows them. The `both` path first asks Android to update both flags together and uses a serialized per-target fallback if needed. This avoids losing a partial mutation behind a Boolean result.

Always display or record both target outcomes:

```dart
final result = await AsyncWallpaper.applyWallpaper(request);

switch (result.status) {
  case WallpaperOperationStatus.applied:
    final home = result.home?.status;
    final lock = result.lock?.status;
    // `home` and `lock` can differ after a fallback.
    break;
  default:
    // Read errorCode/errorMessage/errorDetails for diagnostics.
}
```

`WallpaperTargetResult.status` is one of `applied`, `failed`, `unsupported`, or `notAttempted`. A successful whole-operation status is never a reason to invent a missing target result; null means the platform did not report an individual result.

### Scaling

All five `WallpaperScaleMode` values are implemented for static images:

| Mode | Behavior |
| --- | --- |
| `centerCrop` | Preserve aspect ratio, fill the output, and crop source pixels. |
| `fitCenter` | Preserve aspect ratio and fit the whole source inside the output. |
| `center` | Preserve original-size content centered in the output, cropping only the excess. |
| `fill` | Preserve aspect ratio while drawing enough source to cover the output. |
| `stretch` | Scale independently in both axes; can distort the image. |

Image decoding is bounds-first, sampled, and size-limited. EXIF orientation is normalized before scaling. An oversized, unreadable, invalid, or non-image source returns `failed` with a stable error code instead of crashing the host app.

### Direct, cropper, picker, and automatic strategies

| Strategy | User interaction | Source restrictions | Truthful result |
| --- | --- | --- | --- |
| `direct` | None | Any supported static source | `applied`, `failed`, or `unsupported` |
| `systemCropper` | Android crop UI | Readable `content://` image URI | `previewOpened`, never `applied` merely because UI opened |
| `systemPicker` | Android wallpaper picker | Android chooses the picker source | `awaitingUserConfirmation` |
| `automatic` | Engine selects its safe direct path where possible | Any supported static source | Handle the returned structured status |

`systemCropper` and `systemPicker` require a non-destroyed foreground `Activity`. If the app calls them without one, the result is `foregroundRequired`. Direct static application does not require an activity.

## WorkManager and other background work

Use only direct static work from WorkManager or another headless Android execution context:

```dart
final result = await AsyncWallpaper.applyWallpaper(
  StaticWallpaperRequest(
    source: WallpaperSource.filePath(localPath),
    target: WallpaperTarget.home,
    strategy: WallpaperApplyStrategy.direct,
  ),
);
```

Do not call cropper/picker, `openLiveWallpaperPreview`, video activation, or OpenGL activation from a worker. They enter Android system UI and need a foreground user flow. In 3.2, `goToHome` remains only for source compatibility and is intentionally ignored; the plugin never performs automatic navigation. If foreground/UI requirements cannot be met, treat `foregroundRequired` as a normal final outcome and surface an in-app action later.

> Migration: `goToHome` is retained on `StaticWallpaperRequest`/`VideoWallpaperRequest`/`OpenGlLiveWallpaperRequest`/`WallpaperRequest`/`LiveWallpaperRequest`/`MaterialYouWallpaperRequest` only so existing source still compiles; the engine intentionally ignores it and never navigates to Home. Inspect `WallpaperOperationResult` and own any foreground navigation in the app (e.g., `Navigator.pop`, home intent) after a verified result.

## Video live wallpapers

Video live wallpapers are muted by default; no foreground service is used. Playback is visibility-aware (paused when not visible) and lifecycle-safe.

Video preparation validates a playable video track, MIME type, dimensions, rotation, and duration before atomically replacing the active asset. A failed candidate does not replace a previously valid active video. Playback is visibility-aware, muted by default, and releases its player through one lifecycle path; it does not start a foreground service.

The video engine supports only `centerCrop` and `fitCenter`. Other `VideoWallpaperRequest.scaleMode` values return `unsupported` with the `video-scale-unsupported` error code; they are not silently converted to center crop.

### Live-wallpaper targets and OEM behavior

Android live wallpaper target selection is controlled by the system preview. The plugin can pass a requested target, but **cannot force lock or both targets on OEMs whose preview UI chooses a different target**. On some devices the preview may affect both displays, reset a display, or offer only a home-screen choice.

Consequently:

- `previewOpened` means that the preview UI opened, not that the wallpaper was applied.
- `awaitingUserConfirmation` means Android is waiting for the user's choice.
- This release reports `previewOpened` when it launches the system UI and does not infer `applied` after the user leaves that UI.
- `cancelled`, `unsupported`, and `failed` must remain visible to the user/app; do not replace them with a success toast.

This limitation is documented rather than hidden because Android does not expose a portable API to force a live-wallpaper target across OEM preview implementations.

## OpenGL live wallpapers

> GPU shader compile/link and live-wallpaper target selection are not validated by CI emulators; verify on physical devices and handle `previewOpened`/`awaitingUserConfirmation` as normal outcomes.

OpenGL live wallpaper support requires `supportsOpenGlLiveWallpaper`. The renderer uses an EGL/OpenGL ES 2.0 context on its own render thread, renders only while visible, and releases resources on that thread when the wallpaper surface changes or is destroyed.

The public request has these guardrails:

- a GLSL ES 1.00 fragment shader with `void main()`;
- 1–60 FPS;
- bounded source, texture, uniform, and loop counts;
- URL, local-file, content-URI, and bytes texture sources; a URL is bounded and materialized before renderer configuration, and the render thread never fetches it;
- errors such as `opengl-es2-unavailable`, `shader-main-missing`, and `frame-rate-out-of-range` are returned as structured failures.

As with video, opening an OpenGL live-wallpaper flow cannot force the target selected in Android's system UI.

## Permissions, distribution, and branding

The Android manifest contains only:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.SET_WALLPAPER" />
<uses-feature
    android:name="android.software.live_wallpaper"
    android:required="false" />
```

The optional live-wallpaper feature avoids Play/device filtering for apps that use only static wallpapers. There is no OpenGL hardware feature declaration and no broad storage/media permission.

The plugin's exported wallpaper services use overrideable resource values. Put matching names in the consuming app's `android/app/src/main/res/values/` resources to brand Android's system preview:

```xml
<resources>
    <string name="async_wallpaper_video_live_wallpaper_label">My video wallpaper</string>
    <string name="async_wallpaper_opengl_live_wallpaper_label">My OpenGL wallpaper</string>
    <string name="async_wallpaper_live_wallpaper_description">My app’s live wallpaper</string>
</resources>
```

## iOS and package managers

iOS is download-only. `downloadWallpaper(DownloadWallpaperRequest(url: ...))` saves an image URL to Photos after the host app supplies `NSPhotoLibraryAddUsageDescription`; all wallpaper-apply, video, chooser, and OpenGL APIs return `unsupported`.

The plugin ships metadata for both CocoaPods and Swift Package Manager. Flutter applications normally resolve it through their standard generated iOS integration; custom integrations should include one package manager entry point and avoid compiling the same Swift source twice.

## Validation scope

Automated tests cover request/result mapping, source/transform bounds, serialized operations, callback completion, video lifecycle state, atomic replacement, and shader-contract validation. GPU compilation/linking and system-preview target selection require an emulator or physical device.

No physical OEM device matrix is claimed as complete in this release documentation. In particular, Samsung, Xiaomi/MIUI, Oppo/Realme, Huawei, and other vendor behaviors must be tested by the consuming app on the device populations it supports. Record the capability snapshot, requested target, returned result, device model, Android version, and OEM build with any report.
