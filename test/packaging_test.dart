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
    expect(manifest, isNot(contains('android.hardware.opengles')));

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
        RegExp(r'<intent-filter\b[\s\S]*?</intent-filter>')
            .allMatches(service),
        hasLength(1),
      );
      expect(
        RegExp(
          r'<intent-filter>\s*<action android:name="android\.service\.wallpaper\.WallpaperService" />\s*</intent-filter>',
        ).hasMatch(service),
        isTrue,
      );
      expect(
        RegExp(r'<meta-data\b[^>]*/>').allMatches(service),
        hasLength(1),
      );
      expect(
        RegExp(
          r'<meta-data\b(?=[^>]*android:name="android\.service\.wallpaper")(?=[^>]*android:resource="@xml/wallpaper")[^>]*/>',
        ).hasMatch(service),
        isTrue,
      );
    }
    expect(manifest, isNot(contains('@xml/livewallpaper')));
    expect(manifest, isNot(contains('settingsActivity')));
    expect(manifest, isNot(contains('MainActivity')));

    final xmlDirectory = Directory('android/src/main/res/xml');
    final xmlFiles = xmlDirectory
        .listSync()
        .whereType<File>()
        .map((file) => file.uri.pathSegments.last)
        .toSet();
    expect(xmlFiles, unorderedEquals(<String>['wallpaper.xml']));

    final wallpaperMetadata = File(
      'android/src/main/res/xml/wallpaper.xml',
    ).readAsStringSync();
    expect(wallpaperMetadata, contains('<wallpaper'));
    expect(
      wallpaperMetadata,
      contains(
        'android:description="@string/async_wallpaper_live_wallpaper_description"',
      ),
    );
    expect(wallpaperMetadata, isNot(contains('settingsActivity')));
    expect(wallpaperMetadata, isNot(contains('android:thumbnail')));

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
    expect(
      strings,
      contains(
        '<string name="async_wallpaper_video_live_wallpaper_label">Video live wallpaper</string>',
      ),
    );
    expect(
      strings,
      contains(
        '<string name="async_wallpaper_opengl_live_wallpaper_label">OpenGL live wallpaper</string>',
      ),
    );
    expect(
      strings,
      contains(
        '<string name="async_wallpaper_live_wallpaper_description">Async Wallpaper live wallpaper</string>',
      ),
    );
    for (final legacyName in <String>[
      'app_name',
      'about',
      'choose_video_file',
      'update_log',
      'wallpaper_settings',
      'play_video_with_sound',
      'preference_play_video_with_sound',
    ]) {
      expect(strings, isNot(contains('name="$legacyName"')));
    }
  });

  test('iOS packages and example use the shared plugin sources', () {
    final package = File('ios/async_wallpaper/Package.swift');
    expect(package.existsSync(), isTrue);
    final packageContents = package.readAsStringSync();
    expect(packageContents, contains('name: "async_wallpaper"'));
    expect(packageContents, contains('.iOS("13.0")'));
    expect(
      packageContents,
      contains('.library(name: "async-wallpaper", targets: ["async_wallpaper"])'),
    );
    expect(packageContents, isNot(contains('unsafeFlags')));

    final rootPodspec = File('async_wallpaper.podspec').readAsStringSync();
    final iosPodspec = File('ios/async_wallpaper.podspec').readAsStringSync();
    expect(
      rootPodspec,
      contains("s.source_files     = 'ios/async_wallpaper/Sources/async_wallpaper/**/*'"),
    );
    expect(
      iosPodspec,
      contains("s.source_files     = 'async_wallpaper/Sources/async_wallpaper/**/*'"),
    );
    expect(iosPodspec, contains("s.platform = :ios, '13.0'"));
    for (final podspec in <String>[rootPodspec, iosPodspec]) {
      expect(podspec, isNot(contains('OTHER_SWIFT_FLAGS')));
    }

    final xcodeProject = File('example/ios/Runner.xcodeproj/project.pbxproj').readAsStringSync();
    final xcodeScheme = File(
      'example/ios/Runner.xcodeproj/xcshareddata/xcschemes/Runner.xcscheme',
    ).readAsStringSync();
    expect(xcodeProject, contains('FlutterGeneratedPluginSwiftPackage'));
    expect(
      xcodeProject,
      contains('relativePath = Flutter/ephemeral/Packages/FlutterGeneratedPluginSwiftPackage;'),
    );
    expect(xcodeScheme, contains('xcode_backend.sh&quot; prepare'));

    final pluginSource = File('ios/Classes/AsyncWallpaperPlugin.swift').readAsStringSync();
    expect(pluginSource, contains('public class AsyncWallpaperPlugin'));
    expect(pluginSource, contains('public static func register'));
    expect(pluginSource, isNot(contains('public func ')));

    for (final sourceName in <String>[
      'AsyncWallpaperPlugin.swift',
      'PigeonApi.g.swift',
    ]) {
      final spmSource = File('ios/async_wallpaper/Sources/async_wallpaper/$sourceName');
      final cocoaPodsSource = File('ios/Classes/$sourceName');

      expect(FileSystemEntity.isLinkSync(spmSource.path), isTrue);
      expect(spmSource.readAsStringSync(), cocoaPodsSource.readAsStringSync());
    }
  });
}
