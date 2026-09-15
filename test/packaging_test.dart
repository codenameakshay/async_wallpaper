import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android live wallpaper packaging is optional and self-contained', () {
    final manifest = File(
      'android/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final permissions = RegExp(
      r'<uses-permission\b[^>]*android:name="([^"]+)"[^>]*/?>',
    ).allMatches(manifest).map((match) => match.group(1)!).toList();
    expect(
      permissions,
      unorderedEquals(<String>[
        'android.permission.INTERNET',
        'android.permission.SET_WALLPAPER',
        'android.permission.RECEIVE_BOOT_COMPLETED',
        'android.permission.FOREGROUND_SERVICE',
        'android.permission.POST_NOTIFICATIONS',
        'android.permission.SCHEDULE_EXACT_ALARM',
      ]),
    );

    final features = RegExp(
      r'<uses-feature\b[^>]*android:name="([^"]+)"[^>]*/?>',
    ).allMatches(manifest).map((match) => match.group(1)!).toList();
    expect(
      features,
      unorderedEquals(<String>['android.software.live_wallpaper']),
    );
    expect(
      RegExp(
        r'<uses-feature\b(?=[^>]*android:name="android\.software\.live_wallpaper")(?=[^>]*android:required="false")[^>]*/>',
      ).hasMatch(manifest),
      isTrue,
    );

    final services = RegExp(
      r'<service\b[\s\S]*?</service>',
    ).allMatches(manifest).map((match) => match.group(0)!).toList();
    expect(services, hasLength(2));

    final expectedServices = <String, String>{
      '.VideoLiveWallpaper': 'async_wallpaper_video_live_wallpaper_label',
      '.OpenGlLiveWallpaper': 'async_wallpaper_opengl_live_wallpaper_label',
    };
    for (final entry in expectedServices.entries) {
      final service = services.singleWhere(
        (candidate) => candidate.contains('android:name="${entry.key}"'),
      );
      expect(service, contains('android:exported="true"'));
      expect(
        service,
        contains('android:permission="android.permission.BIND_WALLPAPER"'),
      );
      expect(service, contains('android:label="@string/${entry.value}"'));
      expect(
        RegExp(r'<intent-filter\b[\s\S]*?</intent-filter>').allMatches(service),
        hasLength(1),
      );
      expect(
        RegExp(
          r'<intent-filter>\s*<action android:name="android\.service\.wallpaper\.WallpaperService" />\s*</intent-filter>',
        ).hasMatch(service),
        isTrue,
      );
      expect(RegExp(r'<meta-data\b[^>]*/>').allMatches(service), hasLength(1));
      expect(
        RegExp(
          r'<meta-data\b(?=[^>]*android:name="android\.service\.wallpaper")(?=[^>]*android:resource="@xml/wallpaper")[^>]*/>',
        ).hasMatch(service),
        isTrue,
      );
    }

    final wallpaperMetadata = File(
      'android/src/main/res/xml/wallpaper.xml',
    ).readAsStringSync();
    expect(
      wallpaperMetadata,
      contains(
        'android:description="@string/async_wallpaper_live_wallpaper_description"',
      ),
    );

    final strings = File(
      'android/src/main/res/values/strings.xml',
    ).readAsStringSync();
    final stringNames = RegExp(
      r'<string\s+name="([^"]+)"[^>]*>',
    ).allMatches(strings).map((match) => match.group(1)!).toList();
    expect(
      stringNames,
      unorderedEquals(<String>[
        'async_wallpaper_video_live_wallpaper_label',
        'async_wallpaper_opengl_live_wallpaper_label',
        'async_wallpaper_live_wallpaper_description',
      ]),
    );
  });

  test('iOS packages and example use the shared plugin sources', () {
    final packageContents = File(
      'ios/async_wallpaper/Package.swift',
    ).readAsStringSync();
    expect(
      packageContents,
      contains(
        '.package(name: "FlutterFramework", path: "../FlutterFramework")',
      ),
    );
    expect(
      packageContents,
      contains(
        '.product(name: "FlutterFramework", package: "FlutterFramework")',
      ),
    );

    for (final sourceName in <String>[
      'AsyncWallpaperPlugin.swift',
      'PigeonApi.g.swift',
    ]) {
      final spmSource = File(
        'ios/async_wallpaper/Sources/async_wallpaper/$sourceName',
      );
      final cocoaPodsSource = File('ios/Classes/$sourceName');

      expect(FileSystemEntity.isLinkSync(spmSource.path), isTrue);
      expect(spmSource.readAsStringSync(), cocoaPodsSource.readAsStringSync());
    }
  });
}
