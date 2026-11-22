part of 'screens.dart';

class _Batteries extends StatelessWidget {
  const _Batteries({
    required this.c,
    required this.add,
    required this.options,
    required this.open,
  });
  final BatteryController c;
  final VoidCallback add;
  final ValueChanged<String> options, open;

  @override
  Widget build(BuildContext context) {
    final batteries = c.batteries.values.toList()
      ..sort((a, b) => a.id.compareTo(b.id));
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 24),
      children: [
        const Text('Minhas baterias', style: _title),
        const SizedBox(height: 18),
        if (batteries.isEmpty)
          _Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(
                  height: 120,
                  child: CustomPaint(painter: _PackDrawing()),
                ),
                _gap,
                const Text('Sua primeira bateria', style: _heading),
                _smallGap,
                const Text(
                  'Use o segundo QR Code da etiqueta original ou digite o identificador Bluetooth.',
                  style: _caption,
                ),
                const SizedBox(height: 18),
                FilledButton.icon(
                  onPressed: add,
                  icon: const Icon(Icons.add),
                  label: const Text('Adicionar bateria'),
                ),
              ],
            ),
          )
        else ...[
          FilledButton.icon(
            onPressed: add,
            icon: const Icon(Icons.add),
            label: const Text('Adicionar bateria'),
          ),
          _gap,
          for (final battery in batteries) ...[
            _BatteryCard(
              battery: battery,
              c: c,
              options: () => options(battery.id),
              open: () => open(battery.id),
            ),
            _gap,
          ],
        ],
      ],
    );
  }
}

class _BatteryCard extends StatelessWidget {
  const _BatteryCard({
    required this.c,
    required this.battery,
    required this.options,
    required this.open,
  });
  final BatteryController c;
  final SavedBattery battery;
  final VoidCallback options, open;
  @override
  Widget build(BuildContext context) {
    final selected = battery.id == c.selected;
    final active = selected && c.live, busy = selected && c.busy;
    final snapshot = selected ? _reading(c) : battery.reading;
    final percent = snapshot?.basic.percent ?? battery.percent;
    final time = snapshot != null
        ? DateTime.fromMillisecondsSinceEpoch(snapshot.time)
        : battery.lastAt;
    return _Panel(
      highlight: selected,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              DecoratedBox(
                decoration: BoxDecoration(
                  color: _elevated,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(10),
                  child: Icon(
                    Icons.battery_std_outlined,
                    color: active ? _accent : _ink,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (selected)
                      const Text(
                        'SELECIONADA',
                        style: TextStyle(
                          color: _accent,
                          fontSize: 10,
                          letterSpacing: 1.2,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    Text(battery.name, style: _heading),
                  ],
                ),
              ),
              IconButton(
                onPressed: options,
                tooltip: 'Opções de ${battery.name}',
                icon: const Icon(Icons.more_horiz),
              ),
            ],
          ),
          _gap,
          Align(
            alignment: Alignment.centerLeft,
            child: _Badge(
              selected ? c.status : 'Desconectada',
              active
                  ? _mint
                  : busy
                  ? _accent
                  : _muted,
            ),
          ),
          _smallGap,
          Text(
            battery.bluetoothName.isEmpty
                ? battery.id
                : '${battery.bluetoothName} · ${battery.id}',
            style: _caption,
          ),
          const Divider(height: 24),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      active ? 'Carga atual' : 'Última leitura',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    Text(
                      time != null
                          ? _stamp(time)
                          : percent != null
                          ? 'Horário indisponível'
                          : 'Aguardando dados do BMS',
                      style: _caption,
                    ),
                  ],
                ),
              ),
              Text(
                percent == null ? '—' : '$percent%',
                style: _number.copyWith(
                  fontSize: 28,
                  color: active ? _accent : _ink,
                ),
              ),
            ],
          ),
          _gap,
          OutlinedButton.icon(
            onPressed: open,
            icon: Icon(active || busy ? Icons.chevron_right : Icons.bluetooth),
            label: Text(
              active || busy
                  ? 'Ver painel'
                  : percent != null
                  ? 'Reconectar'
                  : 'Conectar',
            ),
          ),
        ],
      ),
    );
  }
}

class _PackDrawing extends CustomPainter {
  const _PackDrawing();
  @override
  void paint(Canvas canvas, Size size) {
    final factor = math.min(size.width / 280, size.height / 116);
    canvas.save();
    canvas.translate(size.width / 2, size.height / 2);
    canvas.scale(factor);
    final paint = Paint()..color = _border;
    for (var x = -126; x <= 126; x += 12) {
      for (var y = -42; y <= 42; y += 12) {
        canvas.drawCircle(Offset(x.toDouble(), y.toDouble()), .8, paint);
      }
    }
    void face(Color color, List<Offset> points) {
      final path = Path()..addPolygon(points, true);
      paint
        ..color = color
        ..style = PaintingStyle.fill;
      canvas.drawPath(path, paint);
      paint
        ..color = const Color(0xFF53888B)
        ..strokeWidth = 1.2
        ..strokeJoin = StrokeJoin.round
        ..style = PaintingStyle.stroke;
      canvas.drawPath(path, paint);
    }

    face(const Color(0xFF28434B), const [
      Offset(-63, -17),
      Offset(27, -40),
      Offset(65, -18),
      Offset(-25, 7),
    ]);
    face(_elevated, const [
      Offset(-63, -17),
      Offset(-25, 7),
      Offset(-25, 39),
      Offset(-63, 16),
    ]);
    face(const Color(0xFF192C34), const [
      Offset(-25, 7),
      Offset(65, -18),
      Offset(65, 14),
      Offset(-25, 39),
    ]);
    face(_surface, const [
      Offset(-12, -24),
      Offset(14, -31),
      Offset(27, -23),
      Offset(1, -16),
    ]);
    paint
      ..color = _muted
      ..strokeWidth = 1.6
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 5; i++) {
      final start = Offset(-12 + i * 13.0, 10 - i * 3.6);
      canvas.drawLine(start, start + const Offset(0, 15), paint);
    }
    paint.color = _accent;
    canvas.drawLine(const Offset(-54, -1), const Offset(-39, 8), paint);
    canvas.restore();
  }

  @override
  bool shouldRepaint(_PackDrawing old) => false;
}

enum _EnrollmentMode { choose, manual, scan }

class _Enrollment extends StatefulWidget {
  const _Enrollment();
  @override
  State<_Enrollment> createState() => _EnrollmentState();
}

class _EnrollmentState extends State<_Enrollment> {
  _EnrollmentMode mode = _EnrollmentMode.choose;
  final input = TextEditingController();
  String? error;
  int cameraKey = 0;
  bool completed = false;

  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  void switchMode(_EnrollmentMode next) => setState(() {
    mode = next;
    error = null;
  });

  String? parse(String text) {
    final normalized = text.trim().toUpperCase();
    return isBatteryId(normalized) ? normalized : null;
  }

  void finish(String id) {
    if (completed || !mounted) return;
    completed = true;
    Navigator.of(context).pop(id);
  }

  void submit() {
    final id = parse(input.text);
    if (id != null) {
      finish(id);
      return;
    }
    setState(
      () => error = 'Use seis pares de 0–9 ou A–F separados por dois-pontos.',
    );
  }

  void detect(BarcodeCapture capture) {
    if (!mounted || completed || mode != _EnrollmentMode.scan) return;
    var invalid = false;
    for (final barcode in capture.barcodes) {
      if (barcode.format != BarcodeFormat.qrCode || barcode.rawValue == null)
        continue;
      final id = parse(barcode.rawValue!);
      if (id != null) {
        finish(id);
        return;
      }
      invalid = true;
    }
    if (invalid && error == null) {
      setState(
        () => error =
            'Este QR não identifica a bateria. Procure o segundo código da etiqueta, marcado como “Second step”.',
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = switch (mode) {
      _EnrollmentMode.choose => 'Adicionar bateria',
      _EnrollmentMode.manual => 'Identificador Bluetooth',
      _EnrollmentMode.scan => 'Escanear QR Code',
    };
    return PopScope<String>(
      canPop: mode == _EnrollmentMode.choose || completed,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) switchMode(_EnrollmentMode.choose);
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(title),
          leading: BackButton(
            onPressed: () {
              if (mode == _EnrollmentMode.choose) {
                Navigator.pop(context);
              } else {
                switchMode(_EnrollmentMode.choose);
              }
            },
          ),
        ),
        body: SafeArea(
          top: false,
          child: switch (mode) {
            _EnrollmentMode.choose => ListView(
              padding: const EdgeInsets.all(18),
              children: [
                const Text(
                  'Como você quer identificar a bateria?',
                  style: _caption,
                ),
                _gap,
                _MethodButton(
                  icon: Icons.qr_code_scanner,
                  title: 'Escanear QR Code',
                  text: 'Use o segundo QR Code da etiqueta original.',
                  onTap: () => switchMode(_EnrollmentMode.scan),
                ),
                _gap,
                _MethodButton(
                  icon: Icons.keyboard_alt_outlined,
                  title: 'Digitar identificador',
                  text: 'Informe os seis pares do identificador Bluetooth.',
                  onTap: () => switchMode(_EnrollmentMode.manual),
                ),
              ],
            ),
            _EnrollmentMode.manual => ListView(
              padding: const EdgeInsets.all(18),
              children: [
                const Text(
                  'Digite os seis pares separados por dois-pontos, como aparecem na etiqueta original.',
                  style: _caption,
                ),
                _gap,
                TextField(
                  autofocus: true,
                  autocorrect: false,
                  enableSuggestions: false,
                  controller: input,
                  textCapitalization: TextCapitalization.characters,
                  textInputAction: TextInputAction.done,
                  onSubmitted: (_) => submit(),
                  onChanged: (_) {
                    if (error != null) setState(() => error = null);
                  },
                  decoration: InputDecoration(
                    labelText: 'Identificador',
                    hintText: 'AA:BB:CC:DD:EE:FF',
                    errorText: error,
                    errorMaxLines: 3,
                  ),
                ),
                _gap,
                FilledButton.icon(
                  onPressed: submit,
                  icon: const Icon(Icons.bluetooth_searching),
                  label: const Text('Buscar bateria'),
                ),
              ],
            ),
            _EnrollmentMode.scan => LayoutBuilder(
              builder: (context, box) {
                // The entire page remains scrollable in landscape and with enlarged text.
                final height = (box.maxHeight * .60).clamp(172.0, 560.0);
                return ListView(
                  padding: const EdgeInsets.all(18),
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(20),
                      child: SizedBox(
                        height: height,
                        child: LayoutBuilder(
                          builder: (context, preview) {
                            final window = Rect.fromCenter(
                              center: Offset(
                                preview.maxWidth / 2,
                                preview.maxHeight / 2,
                              ),
                              width: preview.biggest.shortestSide * .72,
                              height: preview.biggest.shortestSide * .72,
                            );
                            return MobileScanner(
                              key: ValueKey(cameraKey),
                              onDetect: detect,
                              scanWindow: window,
                              overlayBuilder: (_, _) => IgnorePointer(
                                child: CustomPaint(
                                  size: preview.biggest,
                                  painter: _Finder(window),
                                ),
                              ),
                              errorBuilder: (_, error) => ColoredBox(
                                color: _surface,
                                child: Center(
                                  child: Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: SingleChildScrollView(
                                      child: Column(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          const Icon(
                                            Icons.videocam_off_outlined,
                                            size: 30,
                                            color: _amber,
                                          ),
                                          _smallGap,
                                          Text(
                                            error.errorCode ==
                                                    MobileScannerErrorCode
                                                        .permissionDenied
                                                ? 'Permita o uso da câmera nas configurações do aparelho para escanear a etiqueta.'
                                                : 'Não foi possível abrir a câmera.',
                                            textAlign: TextAlign.center,
                                          ),
                                          TextButton(
                                            onPressed: () =>
                                                setState(() => cameraKey++),
                                            child: const Text(
                                              'Tentar novamente',
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text('Enquadre o segundo QR Code', style: _heading),
                    _smallGap,
                    const Text(
                      'Na etiqueta original da bateria, procure a indicação “Second step”.',
                      style: _caption,
                    ),
                    if (error != null) ...[
                      _gap,
                      Semantics(
                        liveRegion: true,
                        child: Text(
                          error!,
                          style: const TextStyle(color: _amber),
                        ),
                      ),
                    ],
                    _gap,
                    OutlinedButton.icon(
                      onPressed: () => switchMode(_EnrollmentMode.manual),
                      icon: const Icon(Icons.edit_outlined),
                      label: const Text('Digitar identificador'),
                    ),
                  ],
                );
              },
            ),
          },
        ),
      ),
    );
  }
}

class _MethodButton extends StatelessWidget {
  const _MethodButton({
    required this.icon,
    required this.title,
    required this.text,
    required this.onTap,
  });
  final IconData icon;
  final String title, text;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Material(
    color: _surface,
    borderRadius: BorderRadius.circular(18),
    child: InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(icon, color: _accent, size: 30),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: _heading),
                  _smallGap,
                  Text(text, style: _caption),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: _muted),
          ],
        ),
      ),
    ),
  );
}

class _Finder extends CustomPainter {
  const _Finder(this.window);
  final Rect window;
  @override
  void paint(Canvas canvas, Size size) {
    final shade = Path()
      ..fillType = PathFillType.evenOdd
      ..addRect(Offset.zero & size)
      ..addRRect(RRect.fromRectAndRadius(window, const Radius.circular(5)));
    canvas.drawPath(shade, Paint()..color = const Color(0x66060B0E));
    final line = Paint()
      ..color = _accent
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round;
    final len = math.min(24.0, window.shortestSide / 5);
    for (var i = 0; i < 4; i++) {
      canvas.save();
      canvas.translate(window.center.dx, window.center.dy);
      canvas.rotate(i * math.pi / 2);
      final x = -window.width / 2, y = -window.height / 2;
      canvas.drawPath(
        Path()
          ..moveTo(x, y + len)
          ..lineTo(x, y + 4)
          ..quadraticBezierTo(x, y, x + 4, y)
          ..lineTo(x + len, y),
        line,
      );
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(_Finder old) => old.window != window;
}
