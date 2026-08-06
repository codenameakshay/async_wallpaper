import 'dart:typed_data';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';

const List<int> _demoPngBytes = <int>[
  137,
  80,
  78,
  71,
  13,
  10,
  26,
  10,
  0,
  0,
  0,
  13,
  73,
  72,
  68,
  82,
  0,
  0,
  0,
  1,
  0,
  0,
  0,
  1,
  8,
  6,
  0,
  0,
  0,
  31,
  21,
  196,
  137,
  0,
  0,
  0,
  10,
  73,
  68,
  65,
  84,
  120,
  156,
  99,
  96,
  0,
  0,
  0,
  2,
  0,
  1,
  229,
  39,
  212,
  162,
  0,
  0,
  0,
  0,
  73,
  69,
  78,
  68,
  174,
  66,
  96,
  130,
];

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
        final ColorScheme scheme =
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
  final TextEditingController _fileController = TextEditingController(
    text: '/storage/emulated/0/Download/wallpaper.jpg',
  );
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

  bool get _isBusy => _activeAction != null;

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
      final WallpaperCapabilities capabilities = await widget.api
          .getCapabilities();
      if (!mounted) {
        return;
      }
      setState(() {
        _capabilities = capabilities;
        _status = 'Capabilities loaded for ${capabilities.manufacturer}.';
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
  }) async {
    if (_isBusy) {
      return;
    }

    setState(() {
      _activeAction = action;
      _status = startingStatus;
    });

    try {
      final WallpaperOperationResult result = await operation();
      if (!mounted) {
        return;
      }
      setState(() {
        storeResult(result);
        _status = '$action: ${_resultSummary(result)}';
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
    switch (_sourceKind) {
      case _DemoSourceKind.url:
        return WallpaperSource.url(_requiredInput(_urlController.text, 'URL'));
      case _DemoSourceKind.filePath:
        return WallpaperSource.filePath(
          _requiredInput(_fileController.text, 'File path'),
        );
      case _DemoSourceKind.contentUri:
        return WallpaperSource.contentUri(
          _requiredInput(_contentUriController.text, 'Content URI'),
        );
      case _DemoSourceKind.bytes:
        return WallpaperSource.bytes(Uint8List.fromList(_demoPngBytes));
    }
  }

  String _requiredInput(String value, String label) {
    final String trimmed = value.trim();
    if (trimmed.isEmpty) {
      throw ArgumentError('$label cannot be empty.');
    }
    return trimmed;
  }

  String _resultSummary(WallpaperOperationResult result) {
    final List<String> details = <String>[result.status.name];
    if (result.fallbackUsed) {
      details.add(
        'fallback ${result.fallbackStrategy?.name ?? 'strategy'} used',
      );
    }
    if (result.errorCode != null) {
      details.add(result.errorCode!);
    }
    if (result.errorMessage != null) {
      details.add(result.errorMessage!);
    }
    return details.join(' — ');
  }

  String _targetOutcome(
    String targetLabel,
    WallpaperTargetResult? targetResult,
  ) {
    if (targetResult == null) {
      return '$targetLabel: not reported';
    }
    final List<String> details = <String>[targetResult.status.name];
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
            DropdownButtonFormField<_DemoSourceKind>(
              key: const Key('source-selector'),
              initialValue: _sourceKind,
              decoration: const InputDecoration(labelText: 'Source type'),
              items: _DemoSourceKind.values
                  .map(
                    (_DemoSourceKind kind) => DropdownMenuItem<_DemoSourceKind>(
                      value: kind,
                      child: Text(_sourceKindLabel(kind)),
                    ),
                  )
                  .toList(growable: false),
              onChanged: _isBusy
                  ? null
                  : (_DemoSourceKind? value) {
                      if (value != null) {
                        setState(() => _sourceKind = value);
                      }
                    },
            ),
            const SizedBox(height: 12),
            _sourceInput(),
            const SizedBox(height: 12),
            DropdownButtonFormField<WallpaperTarget>(
              key: const Key('target-selector'),
              initialValue: _target,
              decoration: const InputDecoration(labelText: 'Requested target'),
              items: WallpaperTarget.values
                  .map(
                    (WallpaperTarget target) =>
                        DropdownMenuItem<WallpaperTarget>(
                          value: target,
                          child: Text(_enumLabel(target.name)),
                        ),
                  )
                  .toList(growable: false),
              onChanged: _isBusy
                  ? null
                  : (WallpaperTarget? value) {
                      if (value != null) {
                        setState(() => _target = value);
                      }
                    },
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<WallpaperScaleMode>(
              key: const Key('scale-selector'),
              initialValue: _scaleMode,
              decoration: const InputDecoration(labelText: 'Scale mode'),
              items: WallpaperScaleMode.values
                  .map(
                    (WallpaperScaleMode mode) =>
                        DropdownMenuItem<WallpaperScaleMode>(
                          value: mode,
                          child: Text(_enumLabel(mode.name)),
                        ),
                  )
                  .toList(growable: false),
              onChanged: _isBusy
                  ? null
                  : (WallpaperScaleMode? value) {
                      if (value != null) {
                        setState(() => _scaleMode = value);
                      }
                    },
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<WallpaperApplyStrategy>(
              key: const Key('strategy-selector'),
              initialValue: _strategy,
              decoration: const InputDecoration(labelText: 'Apply strategy'),
              items: WallpaperApplyStrategy.values
                  .map(
                    (WallpaperApplyStrategy strategy) =>
                        DropdownMenuItem<WallpaperApplyStrategy>(
                          value: strategy,
                          child: Text(_enumLabel(strategy.name)),
                        ),
                  )
                  .toList(growable: false),
              onChanged: _isBusy
                  ? null
                  : (WallpaperApplyStrategy? value) {
                      if (value != null) {
                        setState(() => _strategy = value);
                      }
                    },
            ),
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

  Widget _sourceInput() {
    switch (_sourceKind) {
      case _DemoSourceKind.url:
        return TextField(
          key: const Key('url-source-input'),
          controller: _urlController,
          enabled: !_isBusy,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(
            labelText: 'HTTPS image or video URL',
            helperText: 'Use an HTTPS URL that your app is allowed to fetch.',
          ),
        );
      case _DemoSourceKind.filePath:
        return TextField(
          key: const Key('file-source-input'),
          controller: _fileController,
          enabled: !_isBusy,
          decoration: const InputDecoration(
            labelText: 'Local file path',
            helperText: 'Pass the path returned by your own picker or cache.',
          ),
        );
      case _DemoSourceKind.contentUri:
        return TextField(
          key: const Key('uri-source-input'),
          controller: _contentUriController,
          enabled: !_isBusy,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(
            labelText: 'Android content URI',
            helperText:
                'Persist access in your app if the URI outlives a session.',
          ),
        );
      case _DemoSourceKind.bytes:
        return const DecoratedBox(
          decoration: BoxDecoration(color: Color(0x11000000)),
          child: Padding(
            padding: EdgeInsets.all(12),
            child: Text(
              'Embedded bytes: a bundled 1×1 PNG is used for this demo. '
              'Replace WallpaperSource.bytes(...) with your image bytes.',
            ),
          ),
        );
    }
  }

  Widget _staticOutcome(BuildContext context) {
    final WallpaperOperationResult? result = _staticResult;
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
    final WallpaperCapabilities? capabilities = _capabilities;
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
              'Video preparation: '
              '${_videoPreparationResult?.status.name ?? 'not run'}',
            ),
            Text(
              'Video preview: ${_videoPreviewResult?.status.name ?? 'not run'}',
            ),
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
            Text(
              'Status: ${result == null ? 'not run' : _resultSummary(result)}',
            ),
            ...children,
          ],
        ),
      ),
    );
  }

  static String _sourceKindLabel(_DemoSourceKind kind) {
    switch (kind) {
      case _DemoSourceKind.url:
        return 'URL';
      case _DemoSourceKind.filePath:
        return 'File path';
      case _DemoSourceKind.contentUri:
        return 'Content URI';
      case _DemoSourceKind.bytes:
        return 'Embedded bytes';
    }
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
