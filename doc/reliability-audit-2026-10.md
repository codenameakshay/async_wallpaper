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
