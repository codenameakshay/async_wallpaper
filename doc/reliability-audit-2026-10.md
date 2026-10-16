# Reliability audit: October 2026

Baseline: `4b7cba70b31430e6aa4d5bdadff565f9999fe390`, equal to `origin/main` when the audit started.

This audit follows [the previous reliability audit](reliability-audit.md). It includes the complete runtime, public API, native implementations, example, packaging, and CI configuration.

## Audit passes

Three Luna workers first audited separate areas: Dart/iOS, Android sources/static operations, and Android rotation/video/OpenGL. Each worker traced the call paths and examined failure cases. A fresh Luna worker then reviewed the boundaries between those areas. The parent reviewed the implementations and checked the evidence.

The audit also examined the example's asynchronous actions, platform manifests, package-manager entry points, generated bindings, publication contents, and open issue reports.

Separate Luna standards/comments and spec reviews found a duplicated mode reader and an unguarded snapshot-size read; both were fixed. The first complete GPT-6.1 Sol review found that shader arithmetic must allow for narrower GPU integer precision and that failed postcommit journal cleanup must preserve a successful promotion. Both corrections were reviewed again with no findings remaining. The second independent complete GPT-6.1 Sol review inspected every changed file and caller again and reported no findings.

## Bug ledger

| ID | Failure | Correction and evidence |
| --- | --- | --- |
| I1 | On iOS 13, download requested read/write Photos permission. An app with only the documented add-only usage key can terminate. | Let the Photos add operation request add-only permission on iOS 13. Keep explicit `.addOnly` authorization on iOS 14+. The permission prompt requires an iOS device or simulator. |
| U1 | Dart accepted internationalized HTTPS hostnames, but both Android source loaders rejected them before network access. | Normalize the hostname to IDNA ASCII while preserving the remaining URL components. Regression tests cover Unicode hosts, IPv6, escaped paths, redirects, and malformed authorities. |
| G2 | EGL config selection required a pbuffer even though cleanup already supports destroying the context without one. A device exposing only window-capable configs failed initialization. | Prefer a window+pbuffer config, then retry with window support alone. This is verified against EGL selection semantics; no matching GPU is available locally. |
| G1 | Shader validation accepted floating-point counters that cannot advance and integer counters whose final update overflows. | Require integer counters and constrain the initial value, bound, step, and final update to the portable `-255..255` range. This conservatively supports the minimum ES2 integer precision without assuming a 32-bit GPU counter. The unsafe-loop regression failed against the original validator. |
| V1 | Player setup read the scale mode and then opened the canonical video path. Promotion between those reads paired different generations. | Read the mode and open the video under the same repository lock. Keep the opened file through the MediaPlayer handoff. |
| R1 | Rotation replaced its fixed cache directory before saving the new configuration. Process death could leave old configuration pointing at missing or replacement files. | Prepare an immutable playlist directory and save its configuration synchronously before pruning unreferenced generations. |
| R2 | Process death after saving rotation settings but before registering triggers left a running playlist with no current schedules. | Save a pending-schedules marker with the config. Reconcile current settings on queued plugin startup or a worker arrival, await WorkManager operations, then clear the marker without advancing the playlist. |
| R3 | Stop saved preferences asynchronously before deleting cache. Process death could restore running state with missing files. | Commit stopped intent synchronously before cancellation or deletion. Preserve cache and registrations if that commit fails. |
| V2 | Video promotion installed the new scale mode before replacing the active video. Process death between those operations left mismatched state. | Recover interrupted promotion before subsequent repository operations. Preserve the existing canonical video paths. Cleanup failure after the final video rename preserves success and coherent reads, but blocks further mutation until the journal can be cleared. |

Three unused internal rotation accessors were removed after a repository-wide call-site search. Public native entry points remain available.

## Coverage and dispositions

| Area | Coverage |
| --- | --- |
| Dart | Every public endpoint, validation, source ownership, request conversion, target/status combinations, legacy results, and platform fallbacks. |
| Android inputs | HTTPS policy, redirects, files, content URIs, byte sources, snapshots, stream limits, image bounds, EXIF, transform geometry, and download publication. |
| Android lifecycle | Engine/Activity attachment, weak references, queue ordering, shutdown, callback completion, and main-thread timeout gates. |
| Rotation | Input bounds, cache publication, persistence, cursor/shuffle state, trigger reconciliation, generations, stop ordering, boot, clock changes, and DST. |
| Video | Metadata, candidate promotion, file replacement, scale persistence, player setup, callback generations, and surface/visibility/error/destroy sequences. |
| OpenGL | Shader contracts, texture bounds, staged activation, generation leases, renderer scheduling, EGL teardown, and configuration recovery. |
| iOS | HTTPS transport, status and redirect policy, size bounds, partial writes, temporary files, exactly-once completion, image validation, and Photos handoff. |
| Integration | Example actions and disposal, manifests, podspecs, Swift package links, Pigeon freshness, package publication, and CI commands. |

The stream mark/reset hypothesis was rejected. No affected production consumer was established on the supported Android versions. The byte-limit implementation remains unchanged.

The malformed UTF-16 shader probe did not establish a production failure. Its initial failure came from the test's missing Flutter binding.

OpenGL generation leases protect staged and active renderer textures. Stale-generation cleanup was examined without an unsafe deletion finding.

## Validation

The unchanged baseline passed 60 package tests, 10 example tests, and 15 Swift transport tests. Package publication checks found no packaging defect.

New adversarial coverage includes a larger malformed-URL corpus and all 262,144 six-event video lifecycle sequences, totaling 1,572,864 transitions. Existing stress tests cover 100,000 geometry calculations, concurrent queue shutdown, and 350,400 calendar cases across ten time zones.

Regression evidence is in `HttpsSourceUrlParserTest`, `ShaderContractTest`, `AtomicFileReplacementTest`, `RotationStartTest`, `WallpaperRotationStoreTest`, `VideoStateMachineTest`, and `dart_edge_case_audit_test.dart`. Filesystem crash tests copy persistent state at successful rename boundaries and open a fresh repository. Store tests reconstruct preferences after failed commits and recovery failures.

Final checks:

| Check | Result |
| --- | --- |
| Package tests (`flutter test --no-pub`) | 60 passed. |
| Example tests (`flutter test --no-pub` in `example`) | 10 passed; example code was unchanged. |
| Package/example analysis and tracked Dart formatting | Passed. |
| Pigeon freshness (`tool/check_pigeon.sh`) | Passed using the installed Flutter SDK. |
| Swift transport (`sh tool/ios_download_tests/run.sh`) | 15 passed. |
| Android full unit suite (`:plugin:testDebugUnitTest`) and lint (`:plugin:lintDebug`) | 124 tests passed across 19 suites; lint passed with 0 errors and 8 advisory warnings. |
| Two complete GPT-6.1 Sol review passes | All first-pass findings resolved; second pass found no findings. |
| `git diff --check` | Passed. |

Shader validation intentionally accepts a smaller loop contract: integer counters, at most 128 iterations, and values/steps in `-255..255`. A shader outside this range is rejected even if a particular GPU could execute it. Driver compilation remains authoritative for GLSL syntax and device support.

## Platform limits

Local Flutter is 3.47.4. CI uses the repository's pinned Flutter 3.41.4. The local pinned SDK cache is unavailable.

The standard local Android example build stops at an incomplete NDK installation. An isolated Gradle harness compiles the real plugin project and runs its native tests and lint. The standard Android and iOS builds remain CI gates. Local lint initially failed parsing installed preview SDK metadata (`ApiLevel=37.0`); the final harness uses a temporary view containing platforms 34/35/36 and the original tools, leaving the shared SDK untouched.

T3 exposes no Android emulator or iOS simulator on this host. GPU execution, Photos permission prompts, system preview confirmation, and OEM target selection need device checks.

Pending schedules recover on plugin attachment or a later worker/receiver entry. If the process dies before any trigger exists, recovery waits for one of those entry points. The JVM tests use reconstructed preferences and filesystem snapshots; they do not execute actual WorkManager cancellation or system wallpaper preview.

Process-interruption tests cover persistent state at transaction boundaries. They do not establish power-loss durability on every filesystem.

Open issues #23, #25, #33, and #34 require device-specific reproduction. This audit does not claim to resolve their OEM behavior.

## Device validation

The unreleased changes (`3.3.0..08933f1`, PRs #77 and #78) were run on Android emulators and an iOS simulator before the 3.4.0 release. Three plugin bugs were found and fixed (B1–B3). Two Codex review rounds checked the fixes.

### Devices and tools

| Target | Details |
| --- | --- |
| Android 16 (API 36) | Emulator `sdk_gphone64_arm64`, Google APIs arm64-v8a, build `BE2A.250530.026.F3`, 1080x2400 at 420 dpi, 4 GB RAM, Pixel launcher, wallpaper picker, live-wallpaper picker, and cropper installed. GL renderer: `Android Emulator OpenGL ES Translator (Apple M5 Pro), OpenGL ES 3.0 (4.1 Metal)`. |
| Android 9 (API 28) | Emulator `sdk_gphone_arm64`, Google APIs arm64-v8a, build `PSR1.210301.009.B6`, 1080x2280 at 440 dpi, Launcher3. |
| iOS 27.0 | iPhone 17 simulator, runtime 24A434. |
| Host | macOS 27.0.1 on Apple M5 Pro, Xcode 27.0 (27A266a), Android emulator 36.4.10, JDK 21 (Android Studio JBR), Flutter 3.41.4 / Dart 3.11.1 (pinned in `.fvmrc`). |

The example app was driven through its UI with `uiautomator` (Android) and AXe (iOS). A temporary harness entry point that was not committed called the public API for combinations that the example UI does not expose: the full static matrix, a URL corpus, invalid shaders, and rotation trigger configurations. UI-driven Android runs used profile builds; see E2.

### Scenarios and results

| Area | Scenario | Result |
| --- | --- | --- |
| Baseline | `flutter test` (package, example), `flutter analyze`, `dart format`, Pigeon check, Swift transport tests, `:testDebugUnitTest`, `:lintDebug`, `flutter build apk --debug` | Pass before and after the fixes. JVM tests: 124 before, 135 after. Swift: 15 before, 17 after. `flutter build ios --simulator` fails on this host with the pinned SDK (E1, E4) and passes with Flutter 3.47.5. |
| Capabilities | API 36 and API 28 | Static, live, OpenGL, home, lock, both, and picker all supported; foreground required; GL 3.0. `openGlRenderer` is null by design. |
| Static | 4 sources × 3 targets × 5 scale modes × `direct`/`automatic` (120 applies, API 36) | 120 applied. Home-only, lock-only, and both results report the correct per-target fields. |
| Static | Launcher and lock-screen screenshots | All five scale modes match the documented geometry. Lock-only and home-only applies change only that target. |
| Static | `systemCropper` with an app-owned MediaStore URI; with a file source; `systemPicker` | `previewOpened` with home `notAttempted`; the cropper reads the granted URI. A file source fails with `system-cropper-requires-content-uri`. The picker returns `awaitingUserConfirmation`. |
| More APIs | Chooser, legacy set, Material You check and set, platform version | All succeed on API 36. |
| U1 | Unicode host `https://i❤.ws/…`, the same host with an emoji variation selector, punycode, uppercase, explicit port, redirect into an IDN host, OpenGL texture from an IDN URL | Applied, or for the texture, rendered. An IDN rotation source starts. |
| U1 | Redirect to HTTP, too many redirects, HTTP 404, HTML body, `image/*` header with a JSON body, 128 MB PNG, DNS failure, malformed authorities | `invalid-url`, `http-status`, `invalid-content-type`, `invalid-image`, `image-too-large` (in 1.5 s, from `Content-Length`), and `source-unavailable`. The Dart layer rejects HTTP, user info, bad ports, spaces, empty hosts, and percent-encoded hosts with `invalid-input`. A 21600x10800 JPEG is sampled and applied. |
| G1 | Float counter, stagnant float step, out-of-range bound, step, and initial value, final-update overflow, 200 iterations, zero step, wrong direction, `while`, dynamic bound, missing `main`, frame rate 0 (API 36 and API 28) | Rejected before any UI opens, with the documented codes. A valid 16-iteration loop shader renders and animates on the launcher on both API levels. |
| G1 | Shader that passes validation but fails driver compilation | Black preview; `shader-compilation-failed` logged once; no crash loop after process death (O2). |
| G2 | Window-only EGL config | Not testable on an emulator; every config exposes pbuffers. |
| V1/V2 | Prepare, preview, and set video A (`centerCrop`) and video B (`fitCenter`); reapply across modes | The repository stores each video with its own mode. fitCenter stretched before the fix (B1) and letterboxes after it on API 36 and API 28. |
| V1/V2 | `kill -9` after set; crash state injected between the mode and video renames (P2); committed rename with a journal left behind (P3); real-timing kill sweep with 12 kill points | The engine restarts with the matching video and mode and does not crash-loop. P2 restores the previous mode and clears the journal. P3 keeps the committed state. Every sweep point keeps the pairing invariant. |
| V1 | Main thread while a 223 MB video is prepared and a new engine starts | Before the fix, the main thread was parked on the repository lock for 2–4 s and Choreographer skipped 220 frames (B2). After it, the main thread never waits. |
| R1/R2 | Kill at 0.3–8 s after a rotation start | Never running with missing files. One kill landed after the save and before trigger registration; relaunch re-registered the job, cleared the marker, and kept the index. |
| R3 | Kill at 0–0.25 s after stop | Stop is committed before cancellation and cache deletion; relaunch cancels the job. |
| Rotation | Interval (clock advanced, job forced), manual rotation, charging (`dumpsys battery`), reboot, time-of-day 23→1 (clock moved into the window), all triggers with shuffle 22→6 | Index advances, and shuffle order persists and regenerates. Constraints follow the battery state. Boot re-registers exactly one job. The alarm fires once and re-arms for the next day. |
| Download | API 36; API 28 | API 36 saves to `Pictures/AsyncWallpaper`. API 28 fails in about 1.5 s and logs that `WRITE_EXTERNAL_STORAGE` is required; no MediaStore row and no temporary file. |
| I1 / iOS | Add-only prompt; Allow, Don't Allow, retry after denial; every non-download API | The add-only prompt shows the usage string. Before the fix, every save failed (B3). After it, JPEGs (800x1204, 2610x3929, 21600x10800) save. Denial fails without a second prompt. Other APIs return `unsupported`. |
| iOS errors | Redirect to HTTP, 128 MB PNG, HTML, 404, redirect chain, JSON body, plain HTTP | Each fails with `platformFailure` ("Failed to download wallpaper."), because the iOS method returns a Boolean. Plain HTTP fails in Dart with `invalidInput`. No temporary files remain. |

Logcat had no app crash, no StrictMode violation, and no leaked-resource warning in profile builds. Other processes crashed only when system_server restarted (E6).

### Bug ledger

| ID | Symptom | Root cause | Fix and test | Verified |
| --- | --- | --- | --- | --- |
| B1 | A 1280x720 `fitCenter` video filled a portrait screen out of proportion. Present since the video feature was added, so also in 3.3.0. | `FIT_CENTER` mapped to `MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT`, which stretches. MediaPlayer has no letterbox mode. | `VideoFitRenderer` draws the SurfaceTexture frame with GLES2 inside the fitCenter rectangle on a black clear. centerCrop keeps the direct path. `VideoFitViewportTest`. | API 36 and API 28: 1080x608 band at the documented position, crop/fit reapply four times, one render thread while active, sleep/wake, `kill -9`, and a 90°-rotated clip displayed as 1080x1920. |
| B2 | The app's main thread, which also runs Flutter, froze while a video was prepared and an engine started. | `preparePending` held the replacement lock during the source copy; the engine takes that lock on the main thread. 3.3.0 locked the same way. | A separate mutation lock serializes writers; the replacement lock covers only renames and readers. `AtomicFileReplacementTest`: a slow candidate copy does not block the wallpaper engine (it timed out before the fix). | API 36: no main-thread waits during a 223 MB copy. P2 recovery re-run on the fixed build. |
| B3 | Every iOS download failed after the Photos permission was granted. Regression in the unreleased bounded transport. | The transport saved to `async-wallpaper-<UUID>.download`, and Photos validates file resources by extension (`PHPhotosErrorDomain` 3302). A `uniformTypeIdentifier` option alone did not help. | `WallpaperDownloadImage.prepareForPhotos` renames the validated file with the extension of the decoded type. Swift tests for the PNG rename and for an undecodable file. | iOS 27 simulator: saves succeed; failures and denial behave as documented. |
| C1 | Codex review: a main-thread promotion could wait for another plugin instance's copy. | `promotePending` took the mutation lock unconditionally. | It rejects with `IOException` when another mutation holds the lock. `AtomicFileReplacementTest`. | JVM. One engine never contends. |
| C2 | Codex review: a delayed preview could promote another instance's pending video. | Pending candidates had no owner. | In-memory ownership tokens per storage directory; a fresh instance can still promote a persisted candidate. `VideoPendingOwnershipTest`. | JVM. |
| C3 | Codex review: after a renderer initialization timeout, MediaPlayer could attach to a surface that EGL might still connect to. | `create()` returned null on timeout as well as on failure. | A timeout throws and the engine takes its player-failure path; only a renderer that failed and released the surface falls back. Codex's larger surface-lease design was declined as too much new concurrency for a release gate. | Codex round 2 and device re-run of the video flows. |
| C4 | Codex review: each renderer left one EGL display reference initialized. | Teardown skipped `eglTerminate`. | Teardown balances a successful `eglInitialize`, as `GlRenderer` does. | Codex round 2 and device re-run. |

Observations not fixed in this release. They are pre-existing, or they need a product decision:

| ID | Observation |
| --- | --- |
| O1 | Dart `_isHttpsUrl` uses `Uri.isAbsolute`, which is false for any URL with a `#fragment`. Such URLs return `invalid-input` although Android accepts them. Present in 3.3.0. |
| O2 | The plugin activates an OpenGL configuration when it opens the preview. A shader that passes validation but fails driver compilation stays active after a cancelled preview, and a restarted engine renders black. Driver compilation is already documented as authoritative. |
| O3 | Android `downloadWallpaper` saves only JPEG, PNG, WebP, GIF, BMP, HEIC, HEIF, and AVIF. An ICO response returns false without logging the reason. |
| O4 | `OpenGlLiveWallpaper.onSurfaceDestroyed` posts the EGL detach and returns without waiting, although `SurfaceHolder.Callback` requires the render thread to stop first. No EGL error was observed. |
| O5 | If `VideoFitRenderer.release()` exceeds its 1 s bound (driver hang) and a reapply prepares a direct player at once, the connect fails and the engine stays idle until the next surface. Codex round 2 found no crash or corrupted state on this path. |
| O6 | OpenGL textures sampled with the plugin's `v_uv` appear upside down, because `v_uv` starts at the bottom-left and bitmap row 0 is uploaded at t=0. Changing this breaks shaders that already compensate; the other option is to document the convention. |

Environment limitations, not plugin defects:

| ID | Limitation |
| --- | --- |
| E1 | Flutter 3.41.4 runs `lipo <file> -verify_arch arm64 x86_64`, which Xcode 27's `lipo` rejects, so `flutter build ios --simulator` fails locally. Flutter 3.47.5 checks one architecture at a time. CI uses `macos-14` and is unaffected. |
| E2 | Debug APKs hit input-dispatch ANRs during `uiautomator` dumps. Flutter runs Dart on the Android main thread, and each dump cost about 1.1 s there with CheckJNI. Profile builds did not. |
| E3 | The emulator's AOSP Wallpaper & style app crashes with a `RecentWallpapersProvider` permission error when opened from the picker. |
| E4 | Xcode 27 supports iOS 15.0–27.0 deployment targets; the example targets 13.0. iOS was run with a local, uncommitted 15.0 target. The plugin still declares iOS 13.0. |
| E5 | `am force-stop` makes Android reset a live wallpaper to the default and cancels the app's jobs; WorkManager reschedules them on the next start. Process death was simulated with `kill -9`. |
| E6 | Host load (several other simulators and emulators) restarted the emulator's system_server through its watchdog twice and caused a boot-time SystemUI ANR. One input-dispatch ANR of the video wallpaper window came during that stall, while system_server's UI thread was blocked for 108 s. Its trace had no stacks, so it is not attributed to the plugin; B2 was then reproduced and fixed separately. |
| E7 | Under memory pressure, WallpaperManagerService reverted a repeatedly killed live wallpaper to the default after a reconnect timeout. |
| E8 | No iOS 13 simulator runtime can be installed (`xcodebuild -downloadPlatform iOS -buildVersion 13.7`: "not available for download"), so I1 was verified only on the iOS 14+ path. The iOS 13 `#available` branches were reviewed but not run. |

### Not verified

- G2: emulator EGL always exposes pbuffer configs, so the window-only retry path has not run.
- Real GPUs and OEM drivers, including shader compile, link, and precision behavior beyond the emulator translator.
- OEM live-wallpaper target selection and OEM preview UIs (issues #23, #25, #33, #34); only the AOSP picker was used.
- iOS 13 add-only authorization at runtime (E8) and Photos behavior on a physical iPhone.
- Precise process death inside `promotePending`'s millisecond window. The real-timing sweep did not land inside it, so crash states P2 and P3 were injected on the device file system instead.
- Doze, App Standby, and battery-saver deferral of WorkManager and inexact alarms over real time; triggers were forced with clock changes and `jobscheduler`.
- Renderer and EGL timeout paths (C3, O5), which need a driver that hangs.
- Power-loss durability, as in the earlier audit.
