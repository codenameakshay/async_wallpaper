## 3.1.0

- Add Android wallpaper rotation APIs: start, stop, status, and rotate now.
- Add mixed rotation playlist support for URL and file sources with local caching.
- Add rotation order modes: sequential and shuffle.
- Add rotation triggers for interval, charging connected, and time-of-day windows.
- Enforce minimum rotation interval of 15 minutes.
- Add active hours configuration for time-of-day trigger.
- Update example app and docs with rotation controls.

## 3.0.0

- Upgrade baseline to Flutter 3.41.4 via FVM.
- Migrate Android host implementation from Java to Kotlin.
- Upgrade Android build stack to AGP 8.11.1, Gradle 8.14, Kotlin plugin 2.2.20, Java 17.
- Raise Android minimum SDK to 24.
- Regenerate platform channel bindings using Pigeon Kotlin host output.
- Breaking API redesign:
  - Replace integer location constants with enums.
  - Replace bool-focused APIs with typed request/result models.
  - Remove package-owned toast behavior and `fluttertoast` dependency.
- Refresh example app to use the v3 typed API.

## 2.1.0

- Fix Android 14 crash in apply methods
- Add method to open native wallpaper chooser
- Update to Flutter v3.22.1
- Update dependencies

## 2.0.3

- Fix native external apply methods not working
- Update dependencies

## 2.0.2

- Add fluttertoast package to show success/failure toasts
- Add timeout of 2 seconds in goToHome method to prevent it looking like a crash

## 2.0.1

- Add support for Flutter 3.3.3
- Update dependencies

## 2.0.0

- Add support for Android 13
- Add a new bool parameter `goToHome` to all methods
- Breaking change - All methods now return a bool instead of String, to make error handling easier
- Breaking change - All methods now accept named parameters instead of positional parameters
- Fixed documentation

## 1.0.1

- Add support for Android 12
- Fixed documentation

## 1.0.0+1

- Fixed documentation
- Add example gif, and screenshot

## 1.0.0

- Initial release
- Supports setting wallpaper from file/url
- Supports video live wallpapers (.mp4)
