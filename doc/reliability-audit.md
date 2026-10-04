# Reliability audit

Baseline: `0d8af27` (`origin/main` at the start of the audit).

This audit covers the Dart API, native implementations, example, package metadata, and CI. Three Luna workers reviewed separate areas. Later passes examined failure paths across those areas. Independent GPT-6.1 Sol reviews checked the complete change.

## Bug ledger

Each row records a concrete failure path. The validation section distinguishes executable evidence from platform checks that need a device.

| ID | Failure before the fix | Fix and regression coverage |
| --- | --- | --- |
| A1 | A request for both screens could be reported as applied when the host replied with a valid home-only result. | Compare every structured reply with the original requested target. Reject mismatches as malformed transport data. |
| A2 | The Dart facade accepted rotation intervals larger than Android's integer range. | Reject those intervals before sending the request. Native validation independently protects the boundary. |
| A3 | URL parsing normalized malformed input, including literal control characters, instead of rejecting it. | Reject malformed HTTPS input before any platform call. Cover a hostile URL corpus across the public endpoints and preserve valid internationalized hostnames. |
| A4 | Validation copied byte buffers, and transport conversion copied them again. Near the video limit, temporary allocations could exceed 1 GiB. | Validate byte length without copying and remove the redundant transport copy. Preserve defensive copies at the public buffer boundary. |
| D1 | iOS ignored HTTP status. An error response containing an image could be saved to Photos as a successful download. | Require a successful HTTP response. The Swift transport test returns a PNG with HTTP 404 and expects failure. |
| D2 | iOS buffered the entire download without a byte limit. A large response could exhaust the host app's memory. | Stream into a temporary file with a 64 MiB limit, including responses without a trustworthy length. Test exact limits, oversized responses, transport failures, and cleanup. |
| D3 | Android opened the response before creating a temporary file. If file creation failed, the response stream stayed open. | Keep file creation inside the response's lifetime. Inject file-creation and copy failures and assert closure and cleanup. |
| D4 | Android could replace an unsupported decoded MIME type with a conflicting HTTP type and publish the wrong extension. | Use HTTP metadata only when the decoder supplies no type. Test an unsupported decoded type with a supported but conflicting HTTP type. |
| S1 | Image bounds, bitmap pixels, and EXIF metadata came from separate source reads. A changing URL or file could bypass the selected decode sample size. | Snapshot external sources once under the encoded-byte limit. All decode passes read the same private file. Test changing sources, size boundaries, and cleanup. |
| S2 | The stream returned by the bounded source opener did not count skipped bytes against its limit. | Apply one budget to reads and skips. Test mixed consuming operations at and beyond the limit. |
| R1 | Rotation start, stop, and manual apply used a separate thread pool. Cache replacement, trigger reconciliation, and stop could interleave. | Use the operation queue and one process-wide rotation coordinator around complete transactions. Cover concurrent callers and reentrant engine calls. |
| R2 | Native rotation narrowed a 64-bit interval before validation. For example, `4294967311` minutes became 15 minutes. | Validate the range before narrowing and reject malformed configurations before mutation. Cover integer extremes, empty playlists, invalid hours, and missing triggers. |
| R3 | A nonexistent spring-forward hour normalized to a later hour, which carried into the next day's alarm. Rearming exactly on the boundary could also select the same instant. | Restore the requested hour after advancing the date and request a strictly future alarm when rearming. Test DST and exact-boundary cases. |
| R4 | Timezone and manual clock changes left the local-time alarm at its old timestamp. | Recalculate the alarm after the relevant system broadcasts without resetting independent interval or charging schedules. |
| R5 | WorkManager's `UPDATE` policy retained the previous cadence when a new playlist had just applied its first wallpaper. Old work and dispatched alarms could advance the replacement playlist. | Replace periodic schedules so their first delay starts again. Fence workers and alarms with configuration generations. Reconcile broadcasts through WorkManager under the shared lock. Preserve alarms from older app versions as generation zero. |
| R6 | A false return from JPEG compression still counted as a cached wallpaper. An unusable replacement could displace a working playlist. | Treat compression failure as a rejected source. Cover rejected replacement preservation and encoder failure. |
| V1 | Video promotion replaced the active video before saving its scale mode. A settings-write failure reported failure after changing the video. | Prepare settings before replacing the video and restore prior settings if promotion fails. Inject failures and check the previous active state. |
| G1 | OpenGL preparation persisted its configuration before the final Activity check. Detachment during preparation left a changed configuration despite `foregroundRequired`. | Stage preparation off the main thread and activate only inside the final preview launch check. Discard uncommitted staging. |
| G2 | OpenGL cleanup could delete textures still referenced by an existing renderer. Recreating its GL resources could then fail. | Retain generations while engines or pending preparations reference them. Test generation ownership and cleanup. |

## Coverage and dispositions

| Area | Audit coverage |
| --- | --- |
| Dart | Public facade, all source/request/result models, Pigeon mapping, validation, platform fallbacks, and legacy endpoints. |
| Android input and static apply | URL policy, redirects, content URIs, files, bytes, stream ownership, decode limits, EXIF, transform geometry, target outcomes, cropper/picker flows, and download publication. |
| Android lifecycle | Plugin attachment, weak Activity references, queue shutdown, callback completion, main-thread handoff, and mutation ordering. |
| Android video | Metadata policy, pending/active files, scale persistence, playback state, visibility, surface recreation, and reapply. |
| Android OpenGL | Shader contracts, texture bounds and ownership, configuration persistence, render scheduling, EGL error paths, and surface lifecycle. |
| Android rotation | Input bounds, playlist cache replacement, cursor/shuffle persistence, start/stop/manual/background ordering, trigger reconciliation, boot, timezone changes, and DST. |
| iOS | HTTP transport, bounded storage, image validation, Photos handoff, permission requirements, unsupported endpoints, and callback delivery. |
| Example and packaging | Capability-driven actions, pending-operation controls, disposed widgets, documentation, manifests, podspecs, Swift package source links, generated bindings, and CI. |

The active-hours window remains specific to the time-of-day trigger. Documentation describes interval and charging as independent schedules; this audit does not change that contract.

Public native entry points remain available even when this repository has no internal callers. Unused internal code can be removed without deleting a shipped API.

## Validation record

The original branch passed 53 package tests, 10 example tests, static analysis, and Pigeon freshness. Those checks did not cover all failures in the ledger.

Two regressions were also run against the original production source with Kotlin and JUnit:

- The DST test failed: the original code returned `1773039600000` instead of `1773036000000`.
- The MIME test failed: the original code returned JPEG metadata instead of rejecting an unsupported decoded type.

Local verification after integration:

| Check | Result |
| --- | --- |
| Package tests (`flutter test --no-pub`) | 60 passed. |
| Example tests (`flutter test --no-pub` in `example`) | 10 passed. |
| Dart analysis and formatting | Passed. |
| Pigeon binding freshness (`tool/check_pigeon.sh`) | Passed with the installed Flutter SDK. |
| Swift transport (`sh tool/ios_download_tests/run.sh`) | 15 passed. |
| Android native tests (`:plugin:testDebugUnitTest`) | 96 passed across 17 suites. |
| Android lint (`:plugin:lintDebug`) | Passed. |

The permanent Android stress tests cover 100,000 geometry calculations, 801 queue completions during concurrent shutdown, and 350,400 calendar cases across ten time zones. The calendar test uses `java.time` as an independent oracle. Failure-injection tests cover file promotion, download cleanup, encoded-byte limits, and rejected rotation requests.

The first Sol review found three gaps: valid Unicode hosts, stale alarm generations, and alarms persisted by older app versions. The second review found that persisted legacy workers also needed generation zero. Each finding received a fix and regression coverage. The third fresh review reported no findings after checking the complete diff.

The separate standards review found no documented-standard violations. The comment review corrected stale source-snapshot and lock descriptions. CI results are attached to the pull request.

The first iOS CI build caught a deployment-target mismatch: the initial file writer required iOS 13.4, while the package supports iOS 13.0. The transport now uses `OutputStream` and handles partial writes and write errors without raising the deployment target. The Swift harness covers those write paths. A fresh focused Sol review of this correction reported no findings.

The corrected iOS simulator app built successfully in CI. The macOS transport-test launcher then needed explicit XCTest framework and Swift module paths, plus Darwin test discovery. The runner now uses the selected Xcode SDK and checks that the discovered suite executes completely without failures.

## Verification boundaries

Local Flutter is 3.47.4; CI uses the repository's pinned Flutter 3.41.4. Native plugin tests can run locally through an isolated Gradle project using the real production sources. The standard example build remains a CI gate.

T3 reported no available Android emulator or iOS simulator on this Linux host. Unit tests and simulator builds do not establish physical-device behavior. GPU execution, Photos authorization and saving, system preview confirmation, and OEM wallpaper target selection still need device checks.

The existing Xiaomi/MIUI target-selection reports and Android restart report need device-specific reproduction. This audit does not claim that unrelated OEM behavior is fixed.
