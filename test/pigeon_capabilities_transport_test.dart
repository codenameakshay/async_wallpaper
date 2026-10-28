import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:async_wallpaper/pigeon_impl_api.dart';
import 'package:async_wallpaper/src/wallpaper_client.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

const _channel =
    'dev.flutter.pigeon.async_wallpaper.WallpaperApi.getCapabilities';

class _CapabilityFields {
  const _CapabilityFields(this.fields);

  final List<Object?> fields;
}

/// Writes the native custom-type wire shape, including legacy field counts.
class _HostCodec extends StandardMessageCodec {
  const _HostCodec();

  @override
  void writeValue(WriteBuffer buffer, Object? value) {
    if (value is _CapabilityFields) {
      buffer.putUint8(
        WallpaperApi.pigeonChannelCodec
            .encodeMessage(WallpaperCapabilitiesData())!
            .getUint8(0),
      );
      writeValue(buffer, value.fields);
    } else {
      super.writeValue(buffer, value);
    }
  }
}

List<Object?> _legacyFields() => <Object?>[
  true,
  true,
  true,
  true,
  true,
  true,
  true,
  true,
  true,
  'Legacy Android',
  33,
  'OpenGL ES 3.2',
  'Adreno',
];

void _expectExistingCapabilities(WallpaperCapabilities capabilities) {
  expect(capabilities.supportsStaticWallpaper, isTrue);
  expect(capabilities.supportsLiveWallpaper, isTrue);
  expect(capabilities.supportsOpenGlLiveWallpaper, isTrue);
  expect(capabilities.supportsHomeWallpaper, isTrue);
  expect(capabilities.supportsLockWallpaper, isTrue);
  expect(capabilities.supportsBothWallpapers, isTrue);
  expect(capabilities.canSetWallpaper, isTrue);
  expect(capabilities.hasSystemWallpaperPicker, isTrue);
  expect(capabilities.requiresForeground, isTrue);
  expect(capabilities.manufacturer, 'Legacy Android');
  expect(capabilities.sdkInt, 33);
  expect(capabilities.openGlVersion, 'OpenGL ES 3.2');
  expect(capabilities.openGlRenderer, 'Adreno');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  void replyWith(Object? envelope) {
    messenger.setMockMessageHandler(
      _channel,
      (_) async => const _HostCodec().encodeMessage(envelope),
    );
  }

  setUp(() {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    AsyncWallpaper.debugResetClient();
  });

  tearDown(() {
    messenger.setMockMessageHandler(_channel, null);
    AsyncWallpaper.debugResetClient();
    debugDefaultTargetPlatformOverride = null;
  });

  test(
    'public default client retains capabilities from a 13-field host',
    () async {
      replyWith(<Object?>[_CapabilityFields(_legacyFields())]);

      final capabilities = await AsyncWallpaper.getCapabilities();

      _expectExistingCapabilities(capabilities);
      expect(capabilities.supportsTilt, isFalse);

      final directCapabilities = await PigeonWallpaperClient()
          .getCapabilities();
      _expectExistingCapabilities(directCapabilities);
      expect(directCapabilities.supportsTilt, isFalse);
    },
  );

  for (final tilt in <bool?>[true, null]) {
    test(
      'current 14-field host retains tilt=$tilt and existing flags',
      () async {
        replyWith(<Object?>[
          _CapabilityFields(<Object?>[..._legacyFields(), tilt]),
        ]);

        final capabilities = await AsyncWallpaper.getCapabilities();

        _expectExistingCapabilities(capabilities);
        expect(capabilities.supportsTilt, tilt ?? false);
      },
    );
  }

  test('a malformed 12-field host is not repaired', () async {
    replyWith(<Object?>[_CapabilityFields(_legacyFields().sublist(0, 12))]);

    await expectLater(
      PigeonWallpaperClient().getCapabilities(),
      throwsRangeError,
    );
    final capabilities = await AsyncWallpaper.getCapabilities();
    expect(capabilities.supportsStaticWallpaper, isFalse);
    expect(capabilities.supportsTilt, isFalse);
  });

  test('preserves the platform error envelope', () async {
    replyWith(<Object?>[
      'native-error',
      'Capabilities unavailable',
      <String, Object?>{'reason': 'permission'},
    ]);

    await expectLater(
      PigeonWallpaperClient().getCapabilities(),
      throwsA(
        isA<PlatformException>()
            .having((error) => error.code, 'code', 'native-error')
            .having(
              (error) => error.message,
              'message',
              'Capabilities unavailable',
            )
            .having((error) => error.details, 'details', <String, Object?>{
              'reason': 'permission',
            }),
      ),
    );
  });

  test('preserves the non-null result envelope error', () async {
    replyWith(<Object?>[null]);

    await expectLater(
      PigeonWallpaperClient().getCapabilities(),
      throwsA(
        isA<PlatformException>()
            .having((error) => error.code, 'code', 'null-error')
            .having(
              (error) => error.message,
              'message',
              'Host platform returned null value for non-null return value.',
            ),
      ),
    );
  });

  test('preserves the missing-channel error', () async {
    await expectLater(
      PigeonWallpaperClient().getCapabilities(),
      throwsA(
        isA<PlatformException>()
            .having((error) => error.code, 'code', 'channel-error')
            .having(
              (error) => error.message,
              'message',
              'Unable to establish connection on channel: "$_channel".',
            ),
      ),
    );
  });

  test('retains an explicitly injected API', () async {
    final capabilities = await PigeonWallpaperClient(
      api: _InjectedApi(),
    ).getCapabilities();

    expect(capabilities.manufacturer, 'Injected API');
    expect(capabilities.supportsTilt, isTrue);
  });

  test(
    'retains messenger injection, channel suffix, and inherited APIs',
    () async {
      const capabilitiesChannel = '$_channel.legacy';
      const versionChannel =
          'dev.flutter.pigeon.async_wallpaper.WallpaperApi.getPlatformVersion.legacy';
      messenger.setMockMessageHandler(
        capabilitiesChannel,
        (_) async => const _HostCodec().encodeMessage(<Object?>[
          _CapabilityFields(_legacyFields()),
        ]),
      );
      messenger.setMockMessageHandler(
        versionChannel,
        (_) async =>
            const StandardMessageCodec().encodeMessage(<Object?>['Android 13']),
      );
      addTearDown(() {
        messenger.setMockMessageHandler(capabilitiesChannel, null);
        messenger.setMockMessageHandler(versionChannel, null);
      });
      final api = CapabilitiesCompatibleWallpaperApi(
        binaryMessenger: messenger,
        messageChannelSuffix: 'legacy',
      );

      final capabilities = await PigeonWallpaperClient(
        api: api,
      ).getCapabilities();

      _expectExistingCapabilities(capabilities);
      expect(capabilities.supportsTilt, isFalse);
      expect(await api.getPlatformVersion(), 'Android 13');
    },
  );
}

class _InjectedApi extends WallpaperApi {
  @override
  Future<WallpaperCapabilitiesData> getCapabilities() async =>
      WallpaperCapabilitiesData(
        manufacturer: 'Injected API',
        supportsTilt: true,
      );
}
