# async_wallpaper

`async_wallpaper` is a Flutter plugin for applying static wallpapers on Android, preparing video and OpenGL live-wallpaper flows, and downloading an image to Photos on iOS. Version 3.2 adds structured requests, capability checks, and truthful operation results while keeping the 3.1 API available.

> The package never shows a toast or other package-owned UI. Inspect the returned result and present feedback in your application.

## Requirements

- Flutter `>=3.41.4`
- Dart `>=3.9.0 <4.0.0`
- Android `minSdk 24`
- iOS `13.0+` for download-to-Photos only

## Install

```yaml
dependencies:
  async_wallpaper: ^3.2.0
```

```dart
import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
```

## Start with capabilities

Capabilities are a snapshot of the current platform, device policy, available wallpaper services, and OpenGL support. Query them before exposing optional UI; still handle a failure because policy or OEM state can change after the check.

```dart
final WallpaperCapabilities capabilities =
    await AsyncWallpaper.getCapabilities();

if (capabilities.supportsStaticWallpaper && capabilities.canSetWallpaper) {
  // Show your static-wallpaper action.
}
```

`requiresForeground` means at least one supported flow opens Android system UI. It does **not** make a direct static apply require an `Activity`; choose `WallpaperApplyStrategy.direct` for a background-safe static operation.

## Apply a static wallpaper

```dart
final WallpaperOperationResult result = await AsyncWallpaper.applyWallpaper(
  const StaticWallpaperRequest(
    source: WallpaperSource.url('https://example.com/wallpaper.jpg'),
    target: WallpaperTarget.both,
    scaleMode: WallpaperScaleMode.centerCrop,
    strategy: WallpaperApplyStrategy.direct,
  ),
);

if (result.status == WallpaperOperationStatus.applied) {
  // Inspect `home` and `lock` independently when target is `both`.
} else {
  // Present result.errorMessage in the app's own UI if appropriate.
}
```

### Sources

Static and video requests use `WallpaperSource`. The Android loader accepts each of these without a picker dependency:

| Source | Construction | Notes |
| --- | --- | --- |
| HTTPS URL | `const WallpaperSource.url('https://…')` | Android downloads a bounded HTTPS response. Plain HTTP is rejected. |
| Local file | `const WallpaperSource.filePath('/storage/…/image.jpg')` | Pass a path from your own picker, cache, or app storage. |
| Android content URI | `const WallpaperSource.contentUri('content://…')` | Your application owns URI access and any persistable grant it needs. |
| Memory bytes | `WallpaperSource.bytes(Uint8List.fromList(bytes))` | The request defensively copies the input. |

For example, an app that already has an image picker can pass its file path directly:

```dart
final result = await AsyncWallpaper.applyWallpaper(
  StaticWallpaperRequest(
    source: WallpaperSource.filePath(pickedFile.path),
    target: WallpaperTarget.home,
    scaleMode: WallpaperScaleMode.fitCenter,
  ),
);
```

No picker, cache, or HTTP package is required by this plugin. The [example app](example/README.md) uses editable sample values to demonstrate URL, file, content-URI, and byte sources.

### Targets and scale modes

`WallpaperTarget.home`, `lock`, and `both` express the requested Android static target. For `both`, do not collapse the outcome into one boolean: check `result.home` and `result.lock` separately.

| Scale mode | Static-image behavior |
| --- | --- |
| `centerCrop` | Fills the target while preserving aspect ratio; excess source pixels are cropped around the center. |
| `fitCenter` | Fits the whole image inside the target while preserving aspect ratio. |
| `center` | Centers original-size content and crops only what cannot fit. |
| `fill` | Preserves aspect ratio while drawing enough image to cover the target. |
| `stretch` | Fills the target dimensions and can distort the image. |

The video service supports only `centerCrop` and `fitCenter`. Other `VideoWallpaperRequest.scaleMode` values return `unsupported` with `video-scale-unsupported`; choose one of the supported modes before preparing the video. OpenGL rendering is controlled by the shader rather than `WallpaperScaleMode`.

### Direct versus Android system UI

| Strategy | Meaning | Background-safe? | Result to expect |
| --- | --- | --- | --- |
| `direct` | Plugin decodes, transforms, and calls Android's wallpaper manager. | Yes, when Android policy allows it. | `applied`, `failed`, or `unsupported`. |
| `systemCropper` | Opens Android's crop-and-set UI for a readable content URI. | No; requires a foreground `Activity`. | `previewOpened`; Android owns the final choice. |
| `systemPicker` | Opens the system wallpaper picker. | No; requires a foreground `Activity`. | `awaitingUserConfirmation`; it is not an apply confirmation. |
| `automatic` | Uses the engine's safe direct path where possible. | Treat as foreground-independent only after handling the returned result. | Any structured status. |

Use direct application for deterministic, app-owned transforms. Use a system cropper or picker when the user should make the final crop or target choice. A picker or preview result must never be presented as “wallpaper applied.”

## Read operation results honestly

`WallpaperOperationResult.status` describes the whole operation:

| Status | Meaning |
| --- | --- |
| `applied` | Android reported a direct apply; inspect target results for `both`. |
| `previewOpened` | A preview/crop flow opened. The user can still cancel or choose another target. |
| `awaitingUserConfirmation` | Android system UI owns the next decision. |
| `cancelled` | A user-visible flow was cancelled or did not become active when observable. |
| `failed` | The plugin could not complete the requested operation. Read `errorCode`, `errorMessage`, and `errorDetails`. |
| `unsupported` | Platform, Android policy, service, or device capability cannot fulfill the operation. |
| `foregroundRequired` | The request needs a live foreground `Activity`; run it from UI instead of a worker. |

`WallpaperTargetResult.status` is `applied`, `failed`, `unsupported`, or `notAttempted`. `fallbackUsed` and `fallbackStrategy` show when a combined target apply required a serialized fallback. Error strings are diagnostic values, not user-facing copy; localize your own UI around them.

## Video live wallpapers

Prepare the video before opening Android's live-wallpaper flow. Preparation validates a playable video and replaces the plugin's active asset atomically; it is distinct from user confirmation.

```dart
final VideoWallpaperRequest request = VideoWallpaperRequest(
  source: WallpaperSource.filePath('/storage/emulated/0/Download/loop.mp4'),
  target: WallpaperTarget.home,
  scaleMode: WallpaperScaleMode.centerCrop,
);

final WallpaperOperationResult prepared =
    await AsyncWallpaper.setVideoWallpaper(request);

if (prepared.status == WallpaperOperationStatus.awaitingUserConfirmation) {
  final WallpaperOperationResult preview =
      await AsyncWallpaper.openLiveWallpaperPreview(request);
  // `previewOpened` means the Android UI appeared, not that the user accepted it.
}
```

Android live-wallpaper target selection is owned by the system preview. The plugin can pass a requested target, but it cannot force home, lock, or both on OEMs that choose differently in their preview UI. See [Android compatibility](docs/android-compatibility.md#live-wallpaper-targets-and-oem-behavior).

## OpenGL live wallpapers

OpenGL support is Android-only and requires a live-wallpaper-capable device with OpenGL ES 2.0 support. Check `supportsOpenGlLiveWallpaper` first.

```dart
const shader = '''
precision mediump float;
varying vec2 v_uv;
uniform float u_time;
void main() {
  gl_FragColor = vec4(v_uv.x, v_uv.y, 0.5 + 0.5 * sin(u_time), 1.0);
}
''';

final result = await AsyncWallpaper.setOpenGlLiveWallpaper(
  OpenGlLiveWallpaperRequest(
    fragmentShader: shader,
    target: WallpaperTarget.home,
    frameRate: 30,
  ),
);
```

The shader contract is intentionally bounded: GLSL ES 1.00, `void main()`, 1–60 FPS, a limited number of textures/uniforms, and no unbounded loops or unsupported constructs. OpenGL texture inputs accept URL, local file-path, content-URI, and byte sources. URL textures are downloaded and bounded before the renderer is configured; the render thread never fetches a URL.

## WorkManager and background work

For Android scheduled work:

1. Use `StaticWallpaperRequest(strategy: WallpaperApplyStrategy.direct, …)`.
2. Do not rely on `goToHome`: 3.2 retains the field only for source compatibility and performs no automatic navigation.
3. Do not invoke `systemCropper`, `systemPicker`, `openLiveWallpaperPreview`, or a live/OpenGL activation flow from a worker. Those flows need system UI and may return `foregroundRequired`.
4. Persist only source values that your worker can still read. In particular, ensure a content URI grant remains valid or copy the asset into app-controlled storage first.
5. Treat all structured statuses as normal outcomes and retry only errors your app can safely retry.

Direct static application intentionally does not dereference an `Activity`, so it can run from WorkManager when Android allows wallpaper changes. User-visible system UI always belongs in a foreground flow.

## Platform behavior, permissions, and branding

- **Android:** static apply, video preparation/preview, and OpenGL live-wallpaper flows are available only when `getCapabilities()` says the device supports them. OEM behavior still matters.
- **iOS:** `downloadWallpaper` saves an image URL to Photos. Apply, video, chooser, and OpenGL APIs return `unsupported`; iOS does not let third-party apps set the system wallpaper.
- **Web and desktop:** wallpaper operations return `unsupported`.

The Android plugin declares only `INTERNET` and `SET_WALLPAPER`; it does not declare broad media/storage permissions. Your app must request or retain the access required by its own picker/content provider.

Live-wallpaper support is optional in the manifest (`android:required="false"`) so using the package does not exclude devices without `android.software.live_wallpaper` from distribution. To customize system-preview branding, override these resources in the consuming app:

```xml
<!-- android/app/src/main/res/values/async_wallpaper.xml -->
<resources>
    <string name="async_wallpaper_video_live_wallpaper_label">My video wallpaper</string>
    <string name="async_wallpaper_opengl_live_wallpaper_label">My animated wallpaper</string>
    <string name="async_wallpaper_live_wallpaper_description">My app’s wallpaper service</string>
</resources>
```

More detail, including OEM limits and an Android target matrix, is in [Android compatibility](docs/android-compatibility.md).

### Hardware and OEM verification matrix

> `3.2.0` OpenGL ES 2.0 live-wallpaper rendering (`GlRenderer`, `ShaderProgramValidator`) and video live-wallpaper preview target selection are validated by syntax/unit tests (`ShaderContractTest`, `VideoStateMachineTest`) but require physical device verification. No CI emulator compiles/links a real GPU driver, and Android does not offer a cross-OEM API to force a live wallpaper onto home, lock, or both. Check `getCapabilities()` before showing the action, handle `previewOpened`/`awaitingUserConfirmation`/`foregroundRequired` as normal outcomes, and report `capabilities` + `WallpaperOperationResult` + device model, Android version, and OEM skin when filing OEM-specific reports.

### iOS download permission

Add a Photos usage description to the host app before calling `downloadWallpaper` on iOS:

```xml
<key>NSPhotoLibraryAddUsageDescription</key>
<string>Allows saving downloaded wallpapers to your Photos library.</string>
```

Both CocoaPods and Swift Package Manager metadata are included with the plugin. Normal Flutter apps resolve the platform implementation through `flutter pub get`; custom iOS integrations should keep either the podspec or `ios/async_wallpaper/Package.swift` in the dependency graph, not duplicate the Swift sources.

## Migrate from 3.1

3.1 methods and request types remain available for source compatibility:

- `setWallpaper(WallpaperRequest(...))`
- `setLiveWallpaper(LiveWallpaperRequest(...))`
- `openWallpaperChooser()`
- `downloadWallpaper(DownloadWallpaperRequest(...))`
- Material You helpers

Move new code to the structured APIs for richer sources and truthful results:

| 3.1 style | 3.2 replacement |
| --- | --- |
| `WallpaperRequest(sourceType: url/file, source: …)` | `StaticWallpaperRequest(source: WallpaperSource.…, target: …)` |
| Boolean-like success handling | `WallpaperOperationResult.status`, `home`, `lock`, and error details |
| `setLiveWallpaper(LiveWallpaperRequest(filePath: …))` | `setVideoWallpaper(VideoWallpaperRequest(source: …))`, then `openLiveWallpaperPreview(...)` in foreground UI |
| External toast configuration | Render your own app feedback from the returned result |

`goToHome` remains on request types (`StaticWallpaperRequest`, `VideoWallpaperRequest`, `OpenGlLiveWallpaperRequest`, `WallpaperRequest`, `LiveWallpaperRequest`, `MaterialYouWallpaperRequest`) so existing source code compiles, but 3.2 intentionally ignores it and never navigates the app to Home. Inspect `WallpaperOperationResult` and own any foreground navigation in the app (e.g., `Navigator.pop`, home intent) after a verified result. WorkManager/headless callers must not use `systemCropper`/`systemPicker`/`openLiveWallpaperPreview`; those return `foregroundRequired` without an `Activity`.

## Further documentation

- [Example app](example/README.md)
- [Android compatibility and OEM limits](docs/android-compatibility.md)
- [3.2.0 issue-resolution ledger](docs/issues-3.2.0.md)
- [Bug report template](https://github.com/codenameakshay/async_wallpaper/issues/new?template=bug_report.md)

When reporting Android behavior, include the device model, Android version, OEM build/skin, chosen strategy, source kind, target, and returned structured result.

## License

MIT
