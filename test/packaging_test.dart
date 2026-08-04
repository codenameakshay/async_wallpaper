import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
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
