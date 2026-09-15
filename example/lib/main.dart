import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';

/// A 1×1 blue PNG used by the embedded-bytes and sample-file sources.
final Uint8List demoPngBytes = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR42mOwD9wKAAIYAUbd/mmIAAAAAElFTkSuQmCC',
);

/// A deliberately small GLSL ES 1.00 shader that needs no textures or input.
///
/// It only uses the standard `v_uv` and `u_time` values supplied by the
/// plugin's OpenGL renderer, keeping the example safe to run on capable
/// devices without taking arbitrary shader text from the user.
const String _safeOpenGlFragmentShader = '''
precision mediump float;
varying vec2 v_uv;
uniform float u_time;

void main() {
  float pulse = 0.5 + 0.5 * sin(u_time);
  gl_FragColor = vec4(v_uv.x, v_uv.y, pulse, 1.0);
}
''';

/// Small seam that keeps the example's widgets testable without a platform
/// channel or a package-owned notification mechanism.
abstract interface class WallpaperDemoApi {
  Future<WallpaperCapabilities> getCapabilities();

  Future<WallpaperOperationResult> applyWallpaper(
    StaticWallpaperRequest request,
  );

  Future<WallpaperOperationResult> setVideoWallpaper(
    VideoWallpaperRequest request,
  );

  Future<WallpaperOperationResult> openLiveWallpaperPreview(
    VideoWallpaperRequest request,
  );

  Future<WallpaperOperationResult> setOpenGlLiveWallpaper(
    OpenGlLiveWallpaperRequest request,
  );

  Future<String> platformVersion();

  Future<MaterialYouSupport> checkMaterialYouSupport();

  Future<WallpaperResult> setWallpaper(WallpaperRequest request);

  Future<WallpaperResult> setMaterialYouWallpaper(
    MaterialYouWallpaperRequest request,
  );

  Future<WallpaperResult> openWallpaperChooser();

  Future<WallpaperResult> downloadWallpaper(DownloadWallpaperRequest request);

  Future<WallpaperResult> startWallpaperRotation(
    WallpaperRotationRequest request,
  );

  Future<WallpaperResult> stopWallpaperRotation();

  Future<WallpaperRotationStatus> getWallpaperRotationStatus();

  Future<WallpaperResult> rotateWallpaperNow();
}

class AsyncWallpaperDemoApi implements WallpaperDemoApi {
  const AsyncWallpaperDemoApi();

  @override
  Future<WallpaperCapabilities> getCapabilities() =>
      AsyncWallpaper.getCapabilities();

  @override
  Future<WallpaperOperationResult> applyWallpaper(
    StaticWallpaperRequest request,
  ) => AsyncWallpaper.applyWallpaper(request);

  @override
  Future<WallpaperOperationResult> setVideoWallpaper(
    VideoWallpaperRequest request,
  ) => AsyncWallpaper.setVideoWallpaper(request);

  @override
  Future<WallpaperOperationResult> openLiveWallpaperPreview(
    VideoWallpaperRequest request,
  ) => AsyncWallpaper.openLiveWallpaperPreview(request);

  @override
  Future<WallpaperOperationResult> setOpenGlLiveWallpaper(
    OpenGlLiveWallpaperRequest request,
  ) => AsyncWallpaper.setOpenGlLiveWallpaper(request);

  @override
  Future<String> platformVersion() => AsyncWallpaper.platformVersion;

  @override
  Future<MaterialYouSupport> checkMaterialYouSupport() =>
      AsyncWallpaper.checkMaterialYouSupport();

  @override
  Future<WallpaperResult> setWallpaper(WallpaperRequest request) =>
      AsyncWallpaper.setWallpaper(request);

  @override
  Future<WallpaperResult> setMaterialYouWallpaper(
    MaterialYouWallpaperRequest request,
  ) => AsyncWallpaper.setMaterialYouWallpaper(request);

  @override
  Future<WallpaperResult> openWallpaperChooser() =>
      AsyncWallpaper.openWallpaperChooser();

  @override
  Future<WallpaperResult> downloadWallpaper(DownloadWallpaperRequest request) =>
      AsyncWallpaper.downloadWallpaper(request);

  @override
  Future<WallpaperResult> startWallpaperRotation(
    WallpaperRotationRequest request,
  ) => AsyncWallpaper.startWallpaperRotation(request);

  @override
  Future<WallpaperResult> stopWallpaperRotation() =>
      AsyncWallpaper.stopWallpaperRotation();

  @override
  Future<WallpaperRotationStatus> getWallpaperRotationStatus() =>
      AsyncWallpaper.getWallpaperRotationStatus();

  @override
  Future<WallpaperResult> rotateWallpaperNow() =>
      AsyncWallpaper.rotateWallpaperNow();
}

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key, this.api = const AsyncWallpaperDemoApi()});

  final WallpaperDemoApi api;

  @override
  Widget build(BuildContext context) {
    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        final scheme =
            lightDynamic ?? ColorScheme.fromSeed(seedColor: Colors.blue);
        return MaterialApp(
          title: 'Async Wallpaper Example',
          theme: ThemeData(colorScheme: scheme, useMaterial3: true),
          home: HomePage(api: api),
        );
      },
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key, this.api = const AsyncWallpaperDemoApi()});

  final WallpaperDemoApi api;

  @override
  State<HomePage> createState() => _HomePageState();
}

enum _DemoSourceKind { url, filePath, contentUri, bytes }

class _HomePageState extends State<HomePage> {
  final TextEditingController _urlController = TextEditingController(
    text: 'https://images.unsplash.com/photo-1635593701810-3156162e184f',
  );
  final TextEditingController _fileController = TextEditingController();
  final TextEditingController _contentUriController = TextEditingController(
    text: 'content://media/external/images/media/1',
  );

  _DemoSourceKind _sourceKind = _DemoSourceKind.url;
  WallpaperTarget _target = WallpaperTarget.both;
  WallpaperScaleMode _scaleMode = WallpaperScaleMode.centerCrop;
  WallpaperApplyStrategy _strategy = WallpaperApplyStrategy.automatic;
  String _status = 'Ready. Choose a source and an action.';
  String? _activeAction;
  WallpaperCapabilities? _capabilities;
  WallpaperOperationResult? _staticResult;
  WallpaperOperationResult? _videoPreparationResult;
  WallpaperOperationResult? _videoPreviewResult;
  WallpaperOperationResult? _openGlResult;
  String? _moreOutcome;

  bool get _isBusy => _activeAction != null;

  @override
  void initState() {
    super.initState();
    _writeSampleFile();
  }

  /// Gives the file-path source a readable default instead of a path that
  /// needs storage permission.
  Future<void> _writeSampleFile() async {
    try {
      final file = File(
        '${Directory.systemTemp.path}/async_wallpaper_demo.png',
      );
      await file.writeAsBytes(demoPngBytes, flush: true);
      if (mounted && _fileController.text.isEmpty) {
        _fileController.text = file.path;
      }
    } on FileSystemException {
      // The field stays empty and the user can enter a path.
    }
  }

  @override
  void dispose() {
    _urlController.dispose();
    _fileController.dispose();
    _contentUriController.dispose();
    super.dispose();
  }

  Future<void> _loadCapabilities() async {
    if (_isBusy) {
      return;
    }

    setState(() {
      _activeAction = 'capabilities';
      _status = 'Loading wallpaper capabilities…';
    });

    try {
      final capabilities = await widget.api.getCapabilities();
      if (!mounted) {
        return;
      }
      setState(() {
        _capabilities = capabilities;
        _status = 'Capabilities loaded.';
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _status = 'Could not load capabilities: $error';
      });
    } finally {
      if (mounted) {
        setState(() => _activeAction = null);
      }
    }
  }

  Future<void> _runOperation({
    required String action,
    required String startingStatus,
    required Future<WallpaperOperationResult> Function() operation,
    required void Function(WallpaperOperationResult result) storeResult,
  }) {
    return _run<WallpaperOperationResult>(
      action: action,
      startingStatus: startingStatus,
      operation: operation,
      describe: _resultSummary,
      storeResult: storeResult,
    );
  }

  /// Runs an action from the "More APIs" card and keeps its outcome there.
  Future<void> _runMore<T>(
    String action,
    Future<T> Function() operation,
    String Function(T result) describe,
  ) async {
    if (_isBusy) {
      return;
    }
    await _run<T>(
      action: action,
      startingStatus: '$action…',
      operation: operation,
      describe: describe,
      storeResult: (T result) {},
    );
    if (mounted) {
      setState(() => _moreOutcome = _status);
    }
  }

  Future<void> _run<T>({
    required String action,
    required String startingStatus,
    required Future<T> Function() operation,
    required String Function(T result) describe,
    required void Function(T result) storeResult,
  }) async {
    if (_isBusy) {
      return;
    }

    setState(() {
      _activeAction = action;
      _status = startingStatus;
    });

    try {
      final result = await operation();
      if (!mounted) {
        return;
      }
      setState(() {
        storeResult(result);
        _status = '$action: ${describe(result)}';
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _status = '$action failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() => _activeAction = null);
      }
    }
  }

  Future<void> _applyStaticWallpaper() {
    return _runOperation(
      action: 'Apply static wallpaper',
      startingStatus: 'Applying static wallpaper…',
      operation: () => widget.api.applyWallpaper(
        StaticWallpaperRequest(
          source: _selectedSource(),
          target: _target,
          scaleMode: _scaleMode,
          strategy: _strategy,
        ),
      ),
      storeResult: (WallpaperOperationResult result) {
        _staticResult = result;
      },
    );
  }

  Future<void> _prepareVideoWallpaper() {
    return _runOperation(
      action: 'Prepare video wallpaper',
      startingStatus: 'Preparing video wallpaper…',
      operation: () => widget.api.setVideoWallpaper(_videoRequest()),
      storeResult: (WallpaperOperationResult result) {
        _videoPreparationResult = result;
      },
    );
  }

  Future<void> _openVideoPreview() {
    return _runOperation(
      action: 'Open live wallpaper preview',
      startingStatus: 'Opening live wallpaper preview…',
      operation: () => widget.api.openLiveWallpaperPreview(_videoRequest()),
      storeResult: (WallpaperOperationResult result) {
        _videoPreviewResult = result;
      },
    );
  }

  Future<void> _applyOpenGlWallpaper() {
    return _runOperation(
      action: 'OpenGL live wallpaper',
      startingStatus: 'Opening the OpenGL live wallpaper flow…',
      operation: () => widget.api.setOpenGlLiveWallpaper(
        OpenGlLiveWallpaperRequest(
          fragmentShader: _safeOpenGlFragmentShader,
          target: _target,
          frameRate: 30,
        ),
      ),
      storeResult: (WallpaperOperationResult result) {
        _openGlResult = result;
      },
    );
  }

  VideoWallpaperRequest _videoRequest() {
    return VideoWallpaperRequest(
      source: _selectedSource(),
      target: _target,
      scaleMode: _scaleMode,
    );
  }

  WallpaperSource _selectedSource() {
    return switch (_sourceKind) {
      _DemoSourceKind.url => WallpaperSource.url(
        _requiredInput(_urlController.text, 'URL'),
      ),
      _DemoSourceKind.filePath => WallpaperSource.filePath(
        _requiredInput(_fileController.text, 'File path'),
      ),
      _DemoSourceKind.contentUri => WallpaperSource.contentUri(
        _requiredInput(_contentUriController.text, 'Content URI'),
      ),
      _DemoSourceKind.bytes => WallpaperSource.bytes(demoPngBytes),
    };
  }

  String _requiredInput(String value, String label) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      throw ArgumentError('$label cannot be empty.');
    }
    return trimmed;
  }

  String _resultSummary(WallpaperOperationResult result) {
    final details = <String>[result.status.name];
    if (result.fallbackUsed) {
      details.add(
        'fallback ${result.fallbackStrategy?.name ?? 'strategy'} used',
      );
    }
    if (result.errorCode != null && result.errorCode != result.status.name) {
      details.add(result.errorCode!);
    }
    if (result.errorMessage != null) {
      details.add(result.errorMessage!);
    }
    return details.join(' — ');
  }

  String _summaryOrNotRun(WallpaperOperationResult? result) =>
      result == null ? 'not run' : _resultSummary(result);

  static String _legacySummary(WallpaperResult result) {
    final error = result.error;
    if (result.isSuccess || error == null) {
      return 'success';
    }
    return 'failed — ${error.code.name} — ${error.message}';
  }

  static String _materialYouSummary(MaterialYouSupport support) {
    return support.isSupported
        ? 'supported on SDK ${support.sdkInt}'
        : 'unavailable';
  }

  String _rotationSummary(WallpaperRotationStatus status) {
    final details = <String>[status.isRunning ? 'running' : 'not running'];
    if (status.isRunning) {
      final nextRun = DateTime.fromMillisecondsSinceEpoch(
        status.nextRunEpochMs,
      );
      details.add(
        'index ${status.currentIndex} of ${status.totalCount}, '
        '${status.cachedCount} cached, '
        'every ${status.effectiveIntervalMinutes} min, '
        'next at ${TimeOfDay.fromDateTime(nextRun).format(context)}',
      );
    }
    if (status.lastError != null) {
      details.add(status.lastError!);
    }
    return details.join(' — ');
  }

  WallpaperRequest _legacyRequest() {
    return switch (_sourceKind) {
      _DemoSourceKind.url => WallpaperRequest(
        target: _target,
        sourceType: WallpaperSourceType.url,
        source: _requiredInput(_urlController.text, 'URL'),
      ),
      _DemoSourceKind.filePath => WallpaperRequest(
        target: _target,
        sourceType: WallpaperSourceType.file,
        source: _requiredInput(_fileController.text, 'File path'),
      ),
      _ => throw ArgumentError(
        'Legacy set wallpaper accepts only a URL or file path source.',
      ),
    };
  }

  WallpaperRotationRequest _rotationRequest() {
    final filePath = _fileController.text.trim();
    return WallpaperRotationRequest(
      sources: <WallpaperRotationSource>[
        WallpaperRotationSource(
          sourceType: WallpaperSourceType.url,
          source: _requiredInput(_urlController.text, 'URL'),
        ),
        if (filePath.isNotEmpty)
          WallpaperRotationSource(
            sourceType: WallpaperSourceType.file,
            source: filePath,
          ),
      ],
      target: _target,
      intervalMinutes: 15,
    );
  }

  String _targetOutcome(
    String targetLabel,
    WallpaperTargetResult? targetResult,
  ) {
    if (targetResult == null) {
      return '$targetLabel: not reported';
    }
    final details = <String>[targetResult.status.name];
    if (targetResult.errorCode != null) {
      details.add(targetResult.errorCode!);
    }
    if (targetResult.errorMessage != null) {
      details.add(targetResult.errorMessage!);
    }
    return '$targetLabel: ${details.join(' — ')}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Async Wallpaper 3.2 example')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            _statusCard(context),
            const SizedBox(height: 16),
            _sourceAndStaticControls(context),
            const SizedBox(height: 16),
            _capabilitiesCard(context),
            const SizedBox(height: 16),
            _videoCard(context),
            const SizedBox(height: 16),
            _openGlCard(context),
            const SizedBox(height: 16),
            _moreApisCard(context),
          ],
        ),
      ),
    );
  }

  Widget _statusCard(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Semantics(
          liveRegion: true,
          label: 'Operation status: $_status',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text('Status', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Row(
                children: <Widget>[
                  if (_isBusy)
                    const Padding(
                      padding: EdgeInsets.only(right: 12),
                      child: SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    ),
                  Expanded(child: Text(_status, key: const Key('status-text'))),
                ],
              ),
              if (_isBusy) ...<Widget>[
                const SizedBox(height: 8),
                Text('One action runs at a time: $_activeAction.'),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _sourceAndStaticControls(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              'Static wallpaper',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            const Text(
              'This demo uses controlled values instead of a file-picker dependency. '
              'Replace the text with a source from your app.',
            ),
            const SizedBox(height: 16),
            _enumDropdown<_DemoSourceKind>(
              key: const Key('source-selector'),
              label: 'Source type',
              value: _sourceKind,
              values: _DemoSourceKind.values,
              labelOf: _sourceKindLabel,
              onChanged: (_DemoSourceKind value) =>
                  setState(() => _sourceKind = value),
            ),
            const SizedBox(height: 12),
            _sourceInput(),
            const SizedBox(height: 12),
            _enumDropdown<WallpaperTarget>(
              key: const Key('target-selector'),
              label: 'Requested target',
              value: _target,
              values: WallpaperTarget.values,
              labelOf: (WallpaperTarget target) => _enumLabel(target.name),
              onChanged: (WallpaperTarget value) =>
                  setState(() => _target = value),
            ),
            const SizedBox(height: 12),
            _enumDropdown<WallpaperScaleMode>(
              key: const Key('scale-selector'),
              label: 'Scale mode',
              value: _scaleMode,
              values: WallpaperScaleMode.values,
              labelOf: (WallpaperScaleMode mode) => _enumLabel(mode.name),
              onChanged: (WallpaperScaleMode value) =>
                  setState(() => _scaleMode = value),
            ),
            const SizedBox(height: 12),
            _enumDropdown<WallpaperApplyStrategy>(
              key: const Key('strategy-selector'),
              label: 'Apply strategy',
              value: _strategy,
              values: WallpaperApplyStrategy.values,
              labelOf: (WallpaperApplyStrategy strategy) =>
                  _enumLabel(strategy.name),
              onChanged: (WallpaperApplyStrategy value) =>
                  setState(() => _strategy = value),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              key: const Key('apply-static-button'),
              onPressed: _isBusy ? null : _applyStaticWallpaper,
              icon: const Icon(Icons.wallpaper_outlined),
              label: const Text('Apply static wallpaper'),
            ),
            const SizedBox(height: 16),
            _staticOutcome(context),
          ],
        ),
      ),
    );
  }

  Widget _enumDropdown<T>({
    required Key key,
    required String label,
    required T value,
    required List<T> values,
    required String Function(T value) labelOf,
    required void Function(T value) onChanged,
  }) {
    return DropdownButtonFormField<T>(
      key: key,
      initialValue: value,
      decoration: InputDecoration(labelText: label),
      items: values
          .map(
            (T item) =>
                DropdownMenuItem<T>(value: item, child: Text(labelOf(item))),
          )
          .toList(growable: false),
      onChanged: _isBusy
          ? null
          : (T? selected) {
              if (selected != null) {
                onChanged(selected);
              }
            },
    );
  }

  Widget _sourceInput() {
    return switch (_sourceKind) {
      _DemoSourceKind.url => TextField(
        key: const Key('url-source-input'),
        controller: _urlController,
        enabled: !_isBusy,
        keyboardType: TextInputType.url,
        decoration: const InputDecoration(
          labelText: 'HTTPS image or video URL',
          helperText: 'Use an HTTPS URL that your app is allowed to fetch.',
        ),
      ),
      _DemoSourceKind.filePath => TextField(
        key: const Key('file-source-input'),
        controller: _fileController,
        enabled: !_isBusy,
        decoration: const InputDecoration(
          labelText: 'Local file path',
          helperText: 'Pass the path returned by your own picker or cache.',
        ),
      ),
      _DemoSourceKind.contentUri => TextField(
        key: const Key('uri-source-input'),
        controller: _contentUriController,
        enabled: !_isBusy,
        keyboardType: TextInputType.url,
        decoration: const InputDecoration(
          labelText: 'Android content URI',
          helperText:
              'Persist access in your app if the URI outlives a session.',
        ),
      ),
      _DemoSourceKind.bytes => const DecoratedBox(
        decoration: BoxDecoration(color: Color(0x11000000)),
        child: Padding(
          padding: EdgeInsets.all(12),
          child: Text(
            'Embedded bytes: a bundled 1×1 PNG is used for this demo. '
            'Replace WallpaperSource.bytes(...) with your image bytes.',
          ),
        ),
      ),
    };
  }

  Widget _staticOutcome(BuildContext context) {
    final result = _staticResult;
    return _resultPanel(
      context,
      title: 'Static outcome',
      result: result,
      children: <Widget>[
        Text(_targetOutcome('Home', result?.home)),
        Text(_targetOutcome('Lock', result?.lock)),
      ],
    );
  }

  Widget _capabilitiesCard(BuildContext context) {
    final capabilities = _capabilities;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('Capabilities', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            if (capabilities == null)
              const Text(
                'Not loaded yet. Check before exposing optional flows.',
              )
            else ...<Widget>[
              Text('Manufacturer: ${capabilities.manufacturer}'),
              Text('SDK: ${capabilities.sdkInt}'),
              Text(
                'Static: ${_supportLabel(capabilities.supportsStaticWallpaper)} · '
                'Video: ${_supportLabel(capabilities.supportsLiveWallpaper)} · '
                'OpenGL: ${_supportLabel(capabilities.supportsOpenGlLiveWallpaper)}',
              ),
              Text(
                'Home: ${_supportLabel(capabilities.supportsHomeWallpaper)} · '
                'Lock: ${_supportLabel(capabilities.supportsLockWallpaper)} · '
                'Both: ${_supportLabel(capabilities.supportsBothWallpapers)}',
              ),
              Text(
                'System picker: ${_supportLabel(capabilities.hasSystemWallpaperPicker)} · '
                'Foreground required: ${capabilities.requiresForeground ? 'yes' : 'no'}',
              ),
              if (capabilities.openGlVersion != null)
                Text('OpenGL: ${capabilities.openGlVersion}'),
              if (capabilities.openGlRenderer != null)
                Text('Renderer: ${capabilities.openGlRenderer}'),
            ],
            const SizedBox(height: 12),
            OutlinedButton.icon(
              key: const Key('refresh-capabilities-button'),
              onPressed: _isBusy ? null : _loadCapabilities,
              icon: const Icon(Icons.refresh),
              label: const Text('Refresh capabilities'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _videoCard(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              'Video live wallpaper',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            const Text(
              'Prepare validates and stages the selected source. Preview opens '
              'Android’s live-wallpaper UI; it is not proof that the user applied it.',
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                FilledButton.tonalIcon(
                  key: const Key('prepare-video-button'),
                  onPressed: _isBusy ? null : _prepareVideoWallpaper,
                  icon: const Icon(Icons.movie_creation_outlined),
                  label: const Text('Prepare video'),
                ),
                FilledButton.tonalIcon(
                  key: const Key('preview-video-button'),
                  onPressed: _isBusy ? null : _openVideoPreview,
                  icon: const Icon(Icons.preview_outlined),
                  label: const Text('Open live preview'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Video preparation: ${_summaryOrNotRun(_videoPreparationResult)}',
            ),
            Text('Video preview: ${_summaryOrNotRun(_videoPreviewResult)}'),
          ],
        ),
      ),
    );
  }

  Widget _openGlCard(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              'OpenGL live wallpaper',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            const Text(
              'Uses a bundled GLSL ES 1.00 color-pulse shader at 30 FPS. '
              'The example does not execute user-supplied shader text or download textures.',
            ),
            const SizedBox(height: 12),
            FilledButton.tonalIcon(
              key: const Key('apply-opengl-button'),
              onPressed: _isBusy ? null : _applyOpenGlWallpaper,
              icon: const Icon(Icons.animation_outlined),
              label: const Text('Open OpenGL wallpaper flow'),
            ),
            const SizedBox(height: 12),
            _resultPanel(
              context,
              title: 'OpenGL outcome',
              result: _openGlResult,
            ),
          ],
        ),
      ),
    );
  }

  Widget _moreApisCard(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('More APIs', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            const Text(
              'Download, Material You, and rotation use the URL above. Legacy '
              'set wallpaper uses the selected URL or file path. iOS supports '
              'only download.',
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                _moreButton(
                  'download-button',
                  'Download to gallery',
                  Icons.download_outlined,
                  () => _runMore(
                    'Download wallpaper',
                    () => widget.api.downloadWallpaper(
                      DownloadWallpaperRequest(
                        url: _requiredInput(_urlController.text, 'URL'),
                      ),
                    ),
                    _legacySummary,
                  ),
                ),
                _moreButton(
                  'chooser-button',
                  'Open wallpaper chooser',
                  Icons.photo_library_outlined,
                  () => _runMore(
                    'Open wallpaper chooser',
                    widget.api.openWallpaperChooser,
                    _legacySummary,
                  ),
                ),
                _moreButton(
                  'legacy-set-button',
                  'Legacy set wallpaper',
                  Icons.history,
                  () => _runMore(
                    'Legacy set wallpaper',
                    () => widget.api.setWallpaper(_legacyRequest()),
                    _legacySummary,
                  ),
                ),
                _moreButton(
                  'material-you-check-button',
                  'Check Material You',
                  Icons.palette_outlined,
                  () => _runMore(
                    'Material You',
                    widget.api.checkMaterialYouSupport,
                    _materialYouSummary,
                  ),
                ),
                _moreButton(
                  'material-you-set-button',
                  'Set Material You wallpaper',
                  Icons.format_paint_outlined,
                  () => _runMore(
                    'Set Material You wallpaper',
                    () => widget.api.setMaterialYouWallpaper(
                      MaterialYouWallpaperRequest(
                        url: _requiredInput(_urlController.text, 'URL'),
                      ),
                    ),
                    _legacySummary,
                  ),
                ),
                _moreButton(
                  'platform-version-button',
                  'Platform version',
                  Icons.info_outline,
                  () => _runMore(
                    'Platform version',
                    widget.api.platformVersion,
                    (String version) => version,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text('Rotation', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            const Text('Rotates the URL and the file path every 15 minutes.'),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                _moreButton(
                  'rotation-start-button',
                  'Start rotation',
                  Icons.play_arrow_outlined,
                  () => _runMore(
                    'Start rotation',
                    () => widget.api.startWallpaperRotation(_rotationRequest()),
                    _legacySummary,
                  ),
                ),
                _moreButton(
                  'rotation-now-button',
                  'Rotate now',
                  Icons.skip_next_outlined,
                  () => _runMore(
                    'Rotate now',
                    widget.api.rotateWallpaperNow,
                    _legacySummary,
                  ),
                ),
                _moreButton(
                  'rotation-status-button',
                  'Rotation status',
                  Icons.schedule,
                  () => _runMore(
                    'Rotation status',
                    widget.api.getWallpaperRotationStatus,
                    _rotationSummary,
                  ),
                ),
                _moreButton(
                  'rotation-stop-button',
                  'Stop rotation',
                  Icons.stop_outlined,
                  () => _runMore(
                    'Stop rotation',
                    widget.api.stopWallpaperRotation,
                    _legacySummary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Last result: ${_moreOutcome ?? 'not run'}',
              key: const Key('more-outcome-text'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _moreButton(
    String key,
    String label,
    IconData icon,
    VoidCallback onPressed,
  ) {
    return OutlinedButton.icon(
      key: Key(key),
      onPressed: _isBusy ? null : onPressed,
      icon: Icon(icon),
      label: Text(label),
    );
  }

  Widget _resultPanel(
    BuildContext context, {
    required String title,
    required WallpaperOperationResult? result,
    List<Widget> children = const <Widget>[],
  }) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(title, style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 4),
            Text('Status: ${_summaryOrNotRun(result)}'),
            ...children,
          ],
        ),
      ),
    );
  }

  static String _sourceKindLabel(_DemoSourceKind kind) {
    return switch (kind) {
      _DemoSourceKind.url => 'URL',
      _DemoSourceKind.filePath => 'File path',
      _DemoSourceKind.contentUri => 'Content URI',
      _DemoSourceKind.bytes => 'Embedded bytes',
    };
  }

  static String _enumLabel(String value) {
    return value.replaceAllMapped(
      RegExp(r'([A-Z])'),
      (Match match) => ' ${match.group(1)!.toLowerCase()}',
    );
  }

  static String _supportLabel(bool supported) {
    return supported ? 'supported' : 'unavailable';
  }
}
