# Async Wallpaper 3.2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship an additive 3.2.0 release that applies static, video, and OpenGL wallpapers reliably on Android, reports truthful per-target outcomes, and resolves every currently open GitHub issue through code, regression coverage, or an explicit Android platform limitation.

**Architecture:** Keep `AsyncWallpaper` as the public Dart seam, but replace boolean Pigeon replies with structured transport data and route Android work through small native modules: source loading, image transformation, serialized application, video preparation/playback, and OpenGL rendering. UI-opening operations are distinct from background-safe direct application, and no operation reports `applied` when it only opened a system preview. Existing 3.x methods remain source-compatible and adapt onto the new modules.

**Tech Stack:** Flutter 3.41.4, Dart 3.9+, Pigeon 25.3, Kotlin 2.2.20, Android SDK 24-36, Swift 5, JUnit/Kotlin test support, GitHub Actions.

---

### Task 1: Additive Dart domain model and injectable facade seam

**Files:**
- Create: `lib/src/models.dart`
- Create: `lib/src/wallpaper_client.dart`
- Modify: `lib/async_wallpaper.dart`
- Test: `test/models_test.dart`
- Test: `test/async_wallpaper_test.dart`

- [ ] **Step 1: Write failing tests for new public values and compatibility**

Add tests that construct every `WallpaperSource` variant, every scale/apply strategy, per-target outcomes, capabilities, and verify existing request constructors still compile. Add a fake `WallpaperClient` and assert `AsyncWallpaper.debugSetClient` routes requests and resets safely.

```dart
test('both target result exposes independent screen outcomes', () {
  const result = WallpaperOperationResult(
    status: WallpaperOperationStatus.applied,
    requestedTarget: WallpaperTarget.both,
    home: WallpaperTargetResult(status: WallpaperTargetStatus.applied),
    lock: WallpaperTargetResult(status: WallpaperTargetStatus.failed),
  );
  expect(result.home?.status, WallpaperTargetStatus.applied);
  expect(result.lock?.status, WallpaperTargetStatus.failed);
});
```

- [ ] **Step 2: Verify RED**

Run: `fvm flutter test test/models_test.dart test/async_wallpaper_test.dart --no-pub`
Expected: FAIL because the new types and injectable client do not exist.

- [ ] **Step 3: Implement focused immutable Dart models**

Define `WallpaperSource.url/filePath/contentUri/bytes`, `WallpaperScaleMode`, `WallpaperApplyStrategy`, `WallpaperOperationStatus`, `WallpaperTargetStatus`, `WallpaperTargetResult`, `WallpaperOperationResult`, `WallpaperCapabilities`, `VideoWallpaperRequest`, and `OpenGlLiveWallpaperRequest`. Move existing request/result types into `models.dart` and export them through `async_wallpaper.dart`. Keep existing constructors and methods working.

```dart
abstract interface class WallpaperClient {
  Future<WallpaperOperationResult> apply(WallpaperRequest request);
  Future<WallpaperCapabilities> getCapabilities();
}
```

- [ ] **Step 4: Verify GREEN and refactor**

Run: `fvm flutter test test/models_test.dart test/async_wallpaper_test.dart --no-pub`
Expected: PASS with no warnings.

- [ ] **Step 5: Commit**

Commit with Lore intent: make the public contract expressive enough to report real Android outcomes while preserving 3.x callers.

### Task 2: Replace boolean Pigeon replies with structured transport contracts

**Files:**
- Modify: `pigeons/messages.dart`
- Regenerate: `lib/pigeon_impl_api.dart`
- Regenerate: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/PigeonApi.kt`
- Regenerate: `ios/Classes/PigeonApi.g.swift`
- Create: `test/pigeon_mapping_test.dart`
- Modify: `lib/src/wallpaper_client.dart`

- [ ] **Step 1: Write failing mapping tests**

Test transport-to-domain mapping for applied, preview opened, foreground required, unsupported, partial both-screen success, native error details, and capability flags.

```dart
expect(
  operationResultFromData(
    OperationResultData(status: OperationStatusData.previewOpened),
  ).status,
  WallpaperOperationStatus.previewOpened,
);
```

- [ ] **Step 2: Verify RED**

Run: `fvm flutter test test/pigeon_mapping_test.dart --no-pub`
Expected: FAIL because structured Pigeon types and mapping functions do not exist.

- [ ] **Step 3: Define and generate the contract**

Add Pigeon enums/data classes for source, scaling, strategy, operation status, per-target result, capabilities, static request, video request, and OpenGL request. Replace mutating boolean methods with `applyWallpaper`, `prepareVideoWallpaper`, `openLiveWallpaperPreview`, and `applyOpenGlWallpaper`. Retain deprecated generated endpoints only where required by current Dart compatibility adapters.

Run: `fvm dart run pigeon --input pigeons/messages.dart`
Expected: Dart, Kotlin, and Swift outputs regenerated by Pigeon 25.3.0.

- [ ] **Step 4: Implement mappings and verify GREEN**

Run: `fvm flutter test test/pigeon_mapping_test.dart --no-pub`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit the schema, all generated outputs, mappings, and tests together.

### Task 3: Android source loading, validation, and image transforms

**Files:**
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/WallpaperSourceLoader.kt`
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/BitmapTransformer.kt`
- Create: `android/src/test/kotlin/com/codenameakshay/async_wallpaper/BitmapTransformMathTest.kt`
- Modify: `android/build.gradle.kts`

- [ ] **Step 1: Write failing pure Kotlin transform tests**

Cover center-crop, fit-center, center, fill, stretch, focal-point clamping, portrait/landscape inputs, and invalid dimensions. Keep geometry in a pure Kotlin value module so tests do not need a device.

```kotlin
assertEquals(RectSpec(500, 0, 1500, 1000), calculateCenterCrop(2000, 1000, 1000, 1000, 0.5f, 0.5f))
```

- [ ] **Step 2: Verify RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*BitmapTransformMathTest'`
Expected: FAIL because transform calculation is missing.

- [ ] **Step 3: Implement loaders and bounded decoding**

Load HTTPS URLs with explicit connect/read timeouts and status/content checks, file paths via `FileInputStream`, content URIs via `ContentResolver`, and bytes via `ByteArrayInputStream`. Decode bounds first, calculate sample size, cap decoded pixels, normalize EXIF orientation when available through platform APIs, and never retain the entire network payload unnecessarily.

- [ ] **Step 4: Verify GREEN**

Run: `cd android && ./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit loaders, transform module, build test configuration, and regression tests.

### Task 4: Serialized and truthful Android static-wallpaper engine

**Files:**
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/StaticWallpaperEngine.kt`
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/OperationQueue.kt`
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/AndroidCapabilities.kt`
- Modify: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/PigeonApiImpl.kt`
- Modify: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/AsyncWallpaperPlugin.kt`
- Test: `android/src/test/kotlin/com/codenameakshay/async_wallpaper/OperationQueueTest.kt`
- Test: `android/src/test/kotlin/com/codenameakshay/async_wallpaper/ResultContractTest.kt`

- [ ] **Step 1: Write failing queue/result tests**

Prove calls execute in submission order, callbacks complete exactly once, partial both-screen results remain distinguishable, unsupported/restricted devices never return applied, and shutdown rejects new operations cleanly.

- [ ] **Step 2: Verify RED**

Run: `cd android && ./gradlew testDebugUnitTest`
Expected: FAIL for missing queue and result policies.

- [ ] **Step 3: Implement direct, cropper, picker, and automatic strategies**

Use `WallpaperManager.isWallpaperSupported`, `isSetWallpaperAllowed`, `setBitmap` crop hints, return IDs, and `FLAG_SYSTEM`/`FLAG_LOCK`. `both` first uses combined flags, then uses serialized target-specific fallback when needed. UI strategies run on the main thread and return `foregroundRequired` when the app lacks a visible activity; direct strategy remains WorkManager-safe.

- [ ] **Step 4: Add ActivityAware lifecycle and exact-once completion**

Store a weak current activity only while attached, cancel/resolve pending UI requests on detach, shut down the operation queue on engine detach, and remove delayed navigation to home from the application engine.

- [ ] **Step 5: Verify GREEN**

Run: `cd android && ./gradlew testDebugUnitTest lintDebug`
Expected: PASS with zero lint errors.

- [ ] **Step 6: Commit**

Commit the static engine and lifecycle changes as one behaviorally complete slice.

### Task 5: Wire the new Dart API and preserve 3.x behavior

**Files:**
- Modify: `lib/async_wallpaper.dart`
- Modify: `lib/src/wallpaper_client.dart`
- Test: `test/async_wallpaper_test.dart`
- Test: `test/platform_result_test.dart`

- [ ] **Step 1: Write failing facade tests**

Cover every source/target/strategy mapping, invalid URL/file/URI/bytes inputs, unsupported platforms, exception mapping, legacy `WallpaperResult`, and new truthful statuses.

- [ ] **Step 2: Verify RED**

Run: `fvm flutter test test/async_wallpaper_test.dart test/platform_result_test.dart --no-pub`
Expected: FAIL until the facade uses the structured client.

- [ ] **Step 3: Implement adapters**

Expose `applyWallpaper`, `getCapabilities`, `setVideoWallpaper`, `openLiveWallpaperPreview`, and `setOpenGlLiveWallpaper`. Keep `setWallpaper`, `setLiveWallpaper`, `openWallpaperChooser`, `downloadWallpaper`, and Material You methods available; mark misleading legacy names deprecated where appropriate and map results without false success.

- [ ] **Step 4: Verify GREEN**

Run: `fvm flutter test --no-pub`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit Dart wiring and compatibility coverage.

### Task 6: Harden video preparation and WallpaperService lifecycle

**Files:**
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/VideoWallpaperRepository.kt`
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/VideoMetadataValidator.kt`
- Modify: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/VideoLiveWallpaper.kt`
- Modify: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/PigeonApiImpl.kt`
- Test: `android/src/test/kotlin/com/codenameakshay/async_wallpaper/VideoStateMachineTest.kt`
- Test: `android/src/test/kotlin/com/codenameakshay/async_wallpaper/AtomicFileReplacementTest.kt`

- [ ] **Step 1: Write failing state-machine and atomic-replacement tests**

Cover prepare/start/pause/resume/error/release transitions, duplicate lifecycle callbacks, missing/corrupt video, source equal to destination, concurrent replacement, and cleanup after failure.

- [ ] **Step 2: Verify RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*Video*' --tests '*AtomicFile*'`
Expected: FAIL.

- [ ] **Step 3: Implement validation and atomic preparation**

Validate a video track, MIME, dimensions, rotation, and duration through `MediaMetadataRetriever`; copy to a unique temporary file; fsync where supported; atomically replace the active file; and retain the previous valid file if preparation fails.

- [ ] **Step 4: Implement resilient engine lifecycle**

Use asynchronous player preparation, a single release path, visibility-aware playback, muted audio by default, configured scale mode, no foreground service, and no calls on a released player. Opening the preview returns `previewOpened`; active-component verification after resume may return `applied` or `cancelled` when observable.

- [ ] **Step 5: Verify GREEN**

Run: `cd android && ./gradlew testDebugUnitTest lintDebug`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit video repository, lifecycle engine, integrations, and tests.

### Task 7: Add shader-based OpenGL live wallpapers

**Files:**
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/OpenGlLiveWallpaper.kt`
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/GlRenderer.kt`
- Create: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/ShaderProgramValidator.kt`
- Modify: `android/src/main/AndroidManifest.xml`
- Modify: `android/src/main/kotlin/com/codenameakshay/async_wallpaper/PigeonApiImpl.kt`
- Test: `android/src/test/kotlin/com/codenameakshay/async_wallpaper/ShaderContractTest.kt`

- [ ] **Step 1: Write failing shader contract tests**

Validate required entry point, source/texture/uniform size limits, frame-rate bounds, OpenGL capability failures, and stable error codes. GPU compilation remains an instrumented/device verification; pure contract checks run on JVM.

- [ ] **Step 2: Verify RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*ShaderContractTest'`
Expected: FAIL.

- [ ] **Step 3: Implement EGL renderer and wallpaper engine**

Create an EGL context on the wallpaper surface, compile/link validated shaders, expose time/resolution/touch/offset uniforms, load bounded textures, render only while visible, honor a capped frame rate, recover on surface recreation, and release GL resources on the render thread.

- [ ] **Step 4: Verify GREEN**

Run: `cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit OpenGL engine, manifest registration, Pigeon wiring, and tests.

### Task 8: Manifest compatibility, branding, permissions, and iOS package repair

**Files:**
- Modify: `android/src/main/AndroidManifest.xml`
- Modify: `android/src/main/res/xml/wallpaper.xml`
- Delete: `android/src/main/res/xml/livewallpaper.xml`
- Modify: `android/src/main/res/values/strings.xml`
- Create: `ios/async_wallpaper/Package.swift`
- Create: `ios/async_wallpaper/Sources/async_wallpaper/` symlink-compatible source layout or update Pigeon output paths consistently
- Modify: `async_wallpaper.podspec`
- Test: `example/ios` simulator build

- [ ] **Step 1: Write failing packaging checks**

Add a shell/Dart repository test that asserts live wallpaper is optional, obsolete storage/media permissions are absent, service labels are override-friendly, only referenced XML remains, and both CocoaPods and Swift Package Manager metadata expose the Swift plugin sources.

- [ ] **Step 2: Verify RED**

Run: `fvm flutter test test/packaging_test.dart --no-pub`
Expected: FAIL against current manifest and missing Swift package.

- [ ] **Step 3: Apply packaging fixes**

Set `android.software.live_wallpaper` to `required=false`, retain only `INTERNET` and `SET_WALLPAPER`, use narrowly prefixed overrideable branding resources, delete legacy strings/XML, and add a valid local Swift package without breaking the podspec layout.

- [ ] **Step 4: Verify GREEN**

Run: `fvm flutter test test/packaging_test.dart --no-pub && cd example && fvm flutter build ios --simulator --no-pub`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit Android packaging and iOS integration repair.

### Task 9: Example application, documentation, migration, and issue ledger

**Files:**
- Modify: `example/lib/main.dart`
- Modify: `example/test/widget_test.dart`
- Replace: `example/README.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Create: `docs/android-compatibility.md`
- Create: `docs/issues-3.2.0.md`

- [ ] **Step 1: Write failing widget tests**

Test source selection, scaling/strategy controls, capability display, separate home/lock outcomes, video/OpenGL preview statuses, loading serialization, and restored state after lifecycle recreation.

- [ ] **Step 2: Verify RED**

Run: `cd example && fvm flutter test --no-pub`
Expected: FAIL until the new UI exists.

- [ ] **Step 3: Implement the demonstrator and documentation**

Show all supported flows without package-owned toast behavior. Document direct versus picker application, background rules, OEM/live-target limitations, branding overrides, migration from 3.1, every new error/status, and map issues #6, #10, #14, #15, #16, #19, #22-26, #28-31, #33-35, and #39 to implementation/tests/limitations.

- [ ] **Step 4: Verify GREEN**

Run: `cd example && fvm flutter test --no-pub`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit example and documentation together.

### Task 10: CI, release metadata, and full release verification

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `tool/check_pigeon.sh`
- Modify: `pubspec.yaml`
- Modify: `async_wallpaper.podspec`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add CI and generated-code freshness check**

CI must run format verification, analyzer, Dart/widget tests, Pigeon regeneration diff, Android unit tests/lint/debug build, and iOS simulator build. The script regenerates Pigeon outputs and fails if tracked output changes.

- [ ] **Step 2: Set version 3.2.0 consistently**

Update Dart and podspec versions and finalize the changelog without changing SDK baselines unless a verified platform requirement demands it.

- [ ] **Step 3: Run the complete verification matrix**

Run:

```bash
fvm dart format --output=none --set-exit-if-changed lib test pigeons example/lib example/test
fvm flutter analyze --no-pub
fvm flutter test --no-pub
(cd example && fvm flutter test --no-pub)
(cd example && fvm flutter build apk --debug --no-pub)
(cd example && fvm flutter build ios --simulator --no-pub)
(cd android && ./gradlew testDebugUnitTest lintDebug)
tool/check_pigeon.sh
```

Expected: every command exits 0 with no analyzer/lint/test/build errors and no tracked diff from code generation.

- [ ] **Step 4: Manual Android device matrix**

Record static home/lock/both, URL/file/URI/bytes, every scale mode, WorkManager direct apply, video preview/apply/lifecycle, and OpenGL apply on API 24, 29, 31, 33, 34, 35, and 36 emulators plus available Pixel, Samsung, Xiaomi/MIUI, Realme/Oppo, and Huawei devices. Never label an unavailable physical-device check as passed.

- [ ] **Step 5: Final review and release commit**

Run a full diff review against `3a9a8cb`, resolve all Critical/Important findings, and create the final Lore release-readiness commit containing only metadata or fixes discovered during verification.

---

## Completion criteria

- All ten tasks are implemented and reviewed for specification and code quality.
- Existing 3.x source compatibility is preserved or explicitly deprecated without removal.
- Every public behavior has automated coverage and was developed red-green-refactor.
- Every open issue is represented in `docs/issues-3.2.0.md` with a concrete code/test/limitation resolution.
- No false `applied` result is possible when only a picker or preview was opened.
- All locally available release commands pass; unavailable OEM hardware checks remain honestly documented rather than guessed.
