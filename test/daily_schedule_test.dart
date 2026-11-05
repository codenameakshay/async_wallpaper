import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

const _codec = WallpaperApi.pigeonChannelCodec;

const _morning = DailyScheduleEntry(
  minuteOfDay: 420,
  source: WallpaperRotationSource(
    sourceType: WallpaperSourceType.url,
    source: 'https://example.com/morning.jpg',
  ),
);

const _evening = DailyScheduleEntry(
  minuteOfDay: 1140,
  source: WallpaperRotationSource(
    sourceType: WallpaperSourceType.file,
    source: '/storage/emulated/0/evening.jpg',
  ),
);

/// Replaces the host handler for one WallpaperApi method and removes it after
/// the test. Same channel and teardown as dart_edge_case_audit_test.dart.
void _mockHostMethod(
  String method,
  Future<ByteData?> Function(ByteData? message) handler,
) {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final channel = 'dev.flutter.pigeon.async_wallpaper.WallpaperApi.$method';
  messenger.setMockMessageHandler(channel, handler);
  addTearDown(() => messenger.setMockMessageHandler(channel, null));
}

/// A Pigeon error envelope: the host threw, so the Dart side gets a
/// PlatformException.
ByteData? _hostThrows() =>
    _codec.encodeMessage(<Object?>['boom', 'host failed', null]);

void main() {
  test(
    'off Android every daily schedule call is unsupported and calls no host method',
    () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final calls = <String>[];
      for (final method in <String>[
        'startDailySchedule',
        'stopDailySchedule',
        'applyCurrentScheduleEntry',
        'getDailyScheduleStatus',
      ]) {
        _mockHostMethod(method, (message) async {
          calls.add(method);
          return _codec.encodeMessage(<Object?>[true]);
        });
      }

      final start = await AsyncWallpaper.startDailySchedule(
        const DailyScheduleRequest(
          entries: <DailyScheduleEntry>[_morning, _evening],
          target: WallpaperTarget.both,
        ),
      );
      expect(start.isSuccess, isFalse);
      expect(start.error?.code, WallpaperErrorCode.unsupported);

      final stop = await AsyncWallpaper.stopDailySchedule();
      expect(stop.isSuccess, isFalse);
      expect(stop.error?.code, WallpaperErrorCode.unsupported);

      final apply = await AsyncWallpaper.applyCurrentScheduleEntry();
      expect(apply.outcome, DailyScheduleApplyOutcome.unsupported);
      expect(apply.entryIndex, isNull);

      final status = await AsyncWallpaper.getDailyScheduleStatus();
      expect(status.mode, WallpaperScheduleMode.none);
      expect(status.entryCount, 0);
      expect(status.nextSwitchEpochMs, 0);

      expect(calls, isEmpty);
    },
  );

  final invalidCases = <String, List<DailyScheduleEntry>>{
    'one entry is rejected': <DailyScheduleEntry>[_morning],
    '101 entries are rejected': List<DailyScheduleEntry>.generate(
      101,
      (i) => DailyScheduleEntry(
        minuteOfDay: i,
        source: WallpaperRotationSource(
          sourceType: WallpaperSourceType.url,
          source: 'https://example.com/$i.jpg',
        ),
      ),
    ),
    'minute -1 is rejected': <DailyScheduleEntry>[
      const DailyScheduleEntry(
        minuteOfDay: -1,
        source: WallpaperRotationSource(
          sourceType: WallpaperSourceType.url,
          source: 'https://example.com/a.jpg',
        ),
      ),
      _evening,
    ],
    'minute 1440 is rejected': <DailyScheduleEntry>[
      const DailyScheduleEntry(
        minuteOfDay: 1440,
        source: WallpaperRotationSource(
          sourceType: WallpaperSourceType.url,
          source: 'https://example.com/a.jpg',
        ),
      ),
      _evening,
    ],
    'two entries with the same minute are rejected': <DailyScheduleEntry>[
      _morning,
      const DailyScheduleEntry(
        minuteOfDay: 420,
        source: WallpaperRotationSource(
          sourceType: WallpaperSourceType.file,
          source: '/storage/emulated/0/other.jpg',
        ),
      ),
    ],
    'a blank source is rejected': <DailyScheduleEntry>[
      _morning,
      const DailyScheduleEntry(
        minuteOfDay: 1140,
        source: WallpaperRotationSource(
          sourceType: WallpaperSourceType.file,
          source: '   ',
        ),
      ),
    ],
    'an http:// URL source is rejected': <DailyScheduleEntry>[
      _morning,
      const DailyScheduleEntry(
        minuteOfDay: 1140,
        source: WallpaperRotationSource(
          sourceType: WallpaperSourceType.url,
          source: 'http://example.com/evening.jpg',
        ),
      ),
    ],
  };

  for (final invalid in invalidCases.entries) {
    test('startDailySchedule on Android: ${invalid.key}', () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final calls = <String>[];
      _mockHostMethod('startDailySchedule', (message) async {
        calls.add('startDailySchedule');
        return _codec.encodeMessage(<Object?>[true]);
      });

      final result = await AsyncWallpaper.startDailySchedule(
        DailyScheduleRequest(
          entries: invalid.value,
          target: WallpaperTarget.home,
        ),
      );

      expect(result.isSuccess, isFalse);
      expect(result.error?.code, WallpaperErrorCode.invalidInput);
      expect(calls, isEmpty);
    });
  }

  test(
    'valid request reaches the host in request order and a true reply is success',
    () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      DailyScheduleRequestData? received;
      _mockHostMethod('startDailySchedule', (message) async {
        received =
            (_codec.decodeMessage(message)! as List).single
                as DailyScheduleRequestData;
        return _codec.encodeMessage(<Object?>[true]);
      });

      final result = await AsyncWallpaper.startDailySchedule(
        const DailyScheduleRequest(
          entries: <DailyScheduleEntry>[_morning, _evening],
          target: WallpaperTarget.both,
        ),
      );

      expect(result.isSuccess, isTrue);
      expect(received?.target, WallpaperTargetData.both);
      expect(received?.entries?.length, 2);
      expect(received?.entries?.map((e) => e?.minuteOfDay).toList(), <int?>[
        420,
        1140,
      ]);
      expect(
        received?.entries?.map((e) => e?.source?.source).toList(),
        <String?>[
          'https://example.com/morning.jpg',
          '/storage/emulated/0/evening.jpg',
        ],
      );
      expect(
        received?.entries?.map((e) => e?.source?.sourceType).toList(),
        <RotationSourceTypeData?>[
          RotationSourceTypeData.url,
          RotationSourceTypeData.file,
        ],
      );
    },
  );

  test('valid request with a false host reply is platformFailure', () async {
    TestWidgetsFlutterBinding.ensureInitialized();
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    _mockHostMethod('startDailySchedule', (message) async {
      return _codec.encodeMessage(<Object?>[false]);
    });

    final result = await AsyncWallpaper.startDailySchedule(
      const DailyScheduleRequest(
        entries: <DailyScheduleEntry>[_morning, _evening],
        target: WallpaperTarget.both,
      ),
    );

    expect(result.isSuccess, isFalse);
    expect(result.error?.code, WallpaperErrorCode.platformFailure);
  });

  test('stopDailySchedule with a true host reply is success', () async {
    TestWidgetsFlutterBinding.ensureInitialized();
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    _mockHostMethod('stopDailySchedule', (message) async {
      return _codec.encodeMessage(<Object?>[true]);
    });

    final result = await AsyncWallpaper.stopDailySchedule();

    expect(result.isSuccess, isTrue);
    expect(result.error, isNull);
  });

  final applyOutcomes =
      <DailyScheduleApplyOutcomeData, DailyScheduleApplyOutcome>{
        DailyScheduleApplyOutcomeData.applied:
            DailyScheduleApplyOutcome.applied,
        DailyScheduleApplyOutcomeData.alreadyCurrent:
            DailyScheduleApplyOutcome.alreadyCurrent,
        DailyScheduleApplyOutcomeData.notRunning:
            DailyScheduleApplyOutcome.notRunning,
        DailyScheduleApplyOutcomeData.failed: DailyScheduleApplyOutcome.failed,
      };

  for (final mapping in applyOutcomes.entries) {
    test(
      'applyCurrentScheduleEntry maps host ${mapping.key.name} to ${mapping.value.name}',
      () async {
        TestWidgetsFlutterBinding.ensureInitialized();
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        addTearDown(() => debugDefaultTargetPlatformOverride = null);
        _mockHostMethod('applyCurrentScheduleEntry', (message) async {
          return _codec.encodeMessage(<Object?>[
            DailyScheduleApplyResultData(outcome: mapping.key, entryIndex: 1),
          ]);
        });

        final result = await AsyncWallpaper.applyCurrentScheduleEntry();

        expect(result.outcome, mapping.value);
        expect(result.entryIndex, 1);
      },
    );
  }

  test(
    'applyCurrentScheduleEntry maps a host error envelope to failed',
    () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      _mockHostMethod('applyCurrentScheduleEntry', (message) async {
        return _hostThrows();
      });

      final result = await AsyncWallpaper.applyCurrentScheduleEntry();

      expect(result.outcome, DailyScheduleApplyOutcome.failed);
      expect(result.entryIndex, isNull);
    },
  );

  test(
    'getDailyScheduleStatus maps every field of a daily schedule status',
    () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      _mockHostMethod('getDailyScheduleStatus', (message) async {
        return _codec.encodeMessage(<Object?>[
          DailyScheduleStatusData(
            mode: WallpaperScheduleModeData.dailySchedule,
            entryCount: 2,
            currentEntryIndex: 1,
            lastAppliedEntryIndex: 0,
            nextSwitchEpochMs: 1767250800000,
            lastError: 'x',
          ),
        ]);
      });

      final status = await AsyncWallpaper.getDailyScheduleStatus();

      expect(status.mode, WallpaperScheduleMode.dailySchedule);
      expect(status.entryCount, 2);
      expect(status.currentEntryIndex, 1);
      expect(status.lastAppliedEntryIndex, 0);
      expect(status.nextSwitchEpochMs, 1767250800000);
      expect(status.lastError, 'x');
    },
  );

  test('getDailyScheduleStatus maps rotation mode to rotation', () async {
    TestWidgetsFlutterBinding.ensureInitialized();
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    _mockHostMethod('getDailyScheduleStatus', (message) async {
      return _codec.encodeMessage(<Object?>[
        DailyScheduleStatusData(mode: WallpaperScheduleModeData.rotation),
      ]);
    });

    final status = await AsyncWallpaper.getDailyScheduleStatus();

    expect(status.mode, WallpaperScheduleMode.rotation);
  });

  test(
    'getDailyScheduleStatus with a throwing host returns none with lastError',
    () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      _mockHostMethod('getDailyScheduleStatus', (message) async {
        return _hostThrows();
      });

      final status = await AsyncWallpaper.getDailyScheduleStatus();

      expect(status.mode, WallpaperScheduleMode.none);
      expect(status.entryCount, 0);
      expect(status.nextSwitchEpochMs, 0);
      expect(status.lastError, 'Failed to get the daily schedule status.');
    },
  );
}
