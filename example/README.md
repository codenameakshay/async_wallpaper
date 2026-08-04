# Async Wallpaper 3.2 example

This app is an intentionally dependency-light demonstrator for the structured 3.2 APIs. It does not include a picker, cache manager workflow, or package-owned toast UI. Instead, it gives each source type a controlled value you can replace with values from your own app.

Run it with:

```sh
cd example
flutter pub get
flutter run
```

## What to try

1. Choose **URL**, **File path**, **Content URI**, or **Embedded bytes** under _Static wallpaper_.
   - URL is an HTTPS sample.
   - File path and content URI are editable placeholders; replace them with a path/URI returned by your app's picker or provider.
   - Embedded bytes use a bundled 1×1 PNG so the source selection is demonstrable without another package.
2. Choose a requested target, scale mode, and static apply strategy.
3. Tap **Refresh capabilities** before enabling an optional device-specific flow in a real product.
4. Apply a static wallpaper and inspect the separate **Home** and **Lock** outcomes. A `both` request is not reduced to one optimistic success label.
5. Try **Prepare video**, then **Open live preview**. The first can return `awaitingUserConfirmation`; the second reports that Android preview UI opened, not that the user applied it.
6. Try the built-in OpenGL flow on a capable Android device. It uses a fixed GLSL ES 1.00 color-pulse shader at 30 FPS, not arbitrary user shader input.

The app runs exactly one operation at a time, keeps accessible live status text, and checks `mounted` after every async call before it updates state.

The demo intentionally has no “go home” option. The 3.2 API retains `goToHome` only for source compatibility and never navigates automatically; app-owned navigation belongs after a verified foreground result.

## Android notes

- Use `direct` static application for WorkManager/headless use. System cropper/picker and live-wallpaper preview need foreground Android UI.
- Android's live-wallpaper preview chooses the final home/lock/both target. The plugin cannot force a lock or both target across OEMs.
- iOS supports `downloadWallpaper` only; applying wallpapers and live/OpenGL flows return `unsupported`.

See the package [README](../README.md) and [Android compatibility guide](../docs/android-compatibility.md) for source, permission, branding, and OEM details.

## Tests

```sh
cd example
flutter test
```

The widget suite uses an injected API adapter to verify source controls, capability display, per-target results, video status distinctions, busy-state serialization, and disposal safety without calling a real platform channel.
