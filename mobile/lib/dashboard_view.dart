part of 'screens.dart';

class _Dashboard extends StatelessWidget {
  const _Dashboard({
    required this.c,
    required this.add,
    required this.switchBattery,
    required this.connect,
    required this.details,
    required this.options,
  });
  final BatteryController c;
  final VoidCallback add, switchBattery, connect, details, options;

  @override
  Widget build(BuildContext context) {
    final reading = _reading(c);
    final basic = reading?.basic;
    final saved = c.batteries[c.selected];
    final percent = basic?.percent ?? saved?.percent;
    final time = _readTime(c);
    final stamp = time != null
        ? 'Última leitura · ${_stamp(time)}'
        : percent != null
        ? 'Porcentagem salva · horário indisponível'
        : c.selected.isEmpty
        ? 'Adicione uma bateria para acompanhar a carga.'
        : 'Aguardando a primeira leitura.';
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 8, 18, 24),
      children: [
        Row(
          children: [
            Expanded(
              child: InkWell(
                onTap: switchBattery,
                borderRadius: BorderRadius.circular(12),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Painel da bateria', style: _caption),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Flexible(
                            child: Text(_name(c, c.selected), style: _title),
                          ),
                          const SizedBox(width: 4),
                          const Icon(Icons.chevron_right, color: _muted),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
            if (saved != null)
              IconButton.filledTonal(
                onPressed: options,
                tooltip: 'Opções da bateria',
                icon: const Icon(Icons.more_horiz),
              ),
          ],
        ),
        _gap,
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF193138), Color(0xFF13242D), Color(0xFF12202A)],
            ),
            border: Border.all(color: _border),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Wrap(
                alignment: WrapAlignment.spaceBetween,
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 6,
                runSpacing: 6,
                children: [
                  const Text('Carga da bateria', style: _heading),
                  _Badge(
                    c.status,
                    c.live
                        ? _mint
                        : c.busy
                        ? _accent
                        : _muted,
                  ),
                ],
              ),
              _EnergyCore(
                percent: percent,
                live: c.live,
                current: basic?.current,
              ),
              const Divider(height: 18),
              if (c.message.isNotEmpty && c.message != c.status)
                Semantics(
                  liveRegion: true,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 5),
                    child: Text(
                      c.message,
                      style: _caption.copyWith(color: _ink),
                    ),
                  ),
                ),
              Text(stamp, style: _caption),
            ],
          ),
        ),
        if (!c.live) ...[
          _gap,
          FilledButton.icon(
            onPressed: c.busy
                ? null
                : c.selected.isEmpty
                ? add
                : connect,
            icon: Icon(c.selected.isEmpty ? Icons.add : Icons.bluetooth),
            label: Text(
              c.busy
                  ? 'Conexão em andamento…'
                  : c.selected.isEmpty
                  ? 'Adicionar bateria'
                  : 'Conectar à bateria',
            ),
          ),
        ],
        _gap,
        _Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 4,
                alignment: WrapAlignment.spaceBetween,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Text(
                    !c.live && basic != null
                        ? 'Últimas medições salvas'
                        : 'Medições elétricas',
                    style: _heading,
                  ),
                  TextButton.icon(
                    onPressed: details,
                    icon: const Icon(Icons.chevron_right, size: 18),
                    label: const Text('Detalhes'),
                  ),
                ],
              ),
              _smallGap,
              LayoutBuilder(
                builder: (context, box) {
                  final items = [
                    ('Tensão', _fmt(basic?.voltage, 2, 'V')),
                    ('Corrente', _fmt(basic?.current, 2, 'A')),
                    (
                      'Potência',
                      _fmt(
                        basic == null
                            ? null
                            : (basic.current * basic.voltage).abs(),
                        0,
                        'W',
                      ),
                    ),
                  ];
                  final scaledWidth =
                      box.maxWidth / MediaQuery.textScalerOf(context).scale(1);
                  if (scaledWidth < 270)
                    return Column(
                      children: [
                        for (final (label, value) in items)
                          _Readout(label, value),
                      ],
                    );
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      for (final (label, value) in items)
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(label, style: _caption),
                              const SizedBox(height: 3),
                              Text(value, style: _number),
                            ],
                          ),
                        ),
                    ],
                  );
                },
              ),
            ],
          ),
        ),
        _gap,
        _Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Capacidade', style: _heading),
              _smallGap,
              _Readout('Restante', _fmt(basic?.remaining, 2, 'Ah')),
              _Readout('Nominal', _fmt(basic?.nominal, 2, 'Ah')),
            ],
          ),
        ),
      ],
    );
  }
}

class _EnergyCore extends StatelessWidget {
  const _EnergyCore({
    required this.percent,
    required this.live,
    required this.current,
  });
  final int? percent;
  final bool live;
  final double? current;
  @override
  Widget build(BuildContext context) {
    final p = percent;
    final valid = p != null && p >= 0 && p <= 100;
    final active = live && valid;
    final direction = active ? (current ?? 0).sign.toInt() : 0;
    final label = !valid
        ? 'Sem leitura'
        : !active
        ? 'Última leitura'
        : direction > 0
        ? 'Carregando'
        : direction < 0
        ? 'Descarregando'
        : 'Sem corrente';
    final animate = direction != 0 && !MediaQuery.disableAnimationsOf(context);
    return Semantics(
      label: 'Carga da bateria. ${valid ? '$p por cento. ' : ''}$label.',
      child: ExcludeSemantics(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 290),
            child: AspectRatio(
              aspectRatio: 1.32,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Positioned.fill(
                    child: RepaintBoundary(
                      child: TweenAnimationBuilder<double>(
                        key: ValueKey((p, active, direction)),
                        tween: Tween(begin: animate ? 0.0 : 1.0, end: 1.0),
                        duration: animate
                            ? const Duration(milliseconds: 680)
                            : Duration.zero,
                        builder: (context, glint, _) => CustomPaint(
                          painter: _GaugePainter(
                            valid ? p : null,
                            active,
                            direction,
                            glint,
                          ),
                        ),
                      ),
                    ),
                  ),
                  FractionallySizedBox(
                    widthFactor: .66,
                    heightFactor: .8,
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.bolt,
                            size: 24,
                            color: active ? _mint : _muted,
                          ),
                          const SizedBox(height: 4),
                          Text.rich(
                            TextSpan(
                              children: [
                                TextSpan(
                                  text: valid ? '$p' : '—',
                                  style: _number.copyWith(
                                    fontSize: 58,
                                    height: 1.02,
                                    letterSpacing: -2,
                                  ),
                                ),
                                if (valid)
                                  TextSpan(
                                    text: '%',
                                    style: _number.copyWith(
                                      fontSize: 22,
                                      color: _muted,
                                    ),
                                  ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 5),
                          _Badge(label, direction == 0 ? _muted : _mint),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _GaugePainter extends CustomPainter {
  const _GaugePainter(this.percent, this.live, this.direction, this.glint);
  final int? percent;
  final bool live;
  final int direction;
  final double glint;
  @override
  void paint(Canvas canvas, Size size) {
    final scale = math.min(size.width / 245, size.height / 193);
    canvas.save();
    canvas.translate(size.width / 2, (size.height - 193 * scale) / 2);
    canvas.scale(scale);
    const center = Offset(0, 103);
    final arc = Rect.fromCircle(center: center, radius: 91);
    final pen = Paint()..style = PaintingStyle.stroke;
    const start = .8 * math.pi, sweep = 1.4 * math.pi, step = sweep / 48;
    final extent = sweep * (percent ?? 0) / 100;
    final moving = direction < 0 ? extent * (1 - glint) : extent * glint;
    for (var i = 0; i < 48; i++) {
      final angle = step * i;
      pen
        ..strokeWidth = 10
        ..color = const Color(0xFF30454D);
      canvas.drawArc(arc, start + angle, step * .82, false, pen);
      final fill = math.min(step * .82, extent - angle);
      if (fill <= 0) continue;
      pen.color = !live
          ? const Color(0xFF78999E)
          : extent - angle < 3 * step
          ? _mint
          : const Color(0xFF36BCC1);
      if (glint < 1 && (angle - moving).abs() < step)
        pen.color = const Color(0xFFDCFFF2);
      canvas.drawArc(arc, start + angle, fill, false, pen);
    }
    pen
      ..strokeWidth = .9
      ..color = const Color(0xFF354B50);
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: 79),
      start,
      sweep,
      false,
      pen,
    );
    for (var i = 0; i <= 10; i++) {
      final a = start + sweep * i / 10;
      final unit = Offset(math.cos(a), math.sin(a));
      canvas.drawLine(
        center + unit * 79,
        center + unit * (i % 5 == 0 ? 73 : 76),
        pen,
      );
    }
    for (final (x, label) in [(-73.0, '0'), (73.0, '100')]) {
      final text = TextPainter(
        text: TextSpan(
          text: label,
          style: const TextStyle(color: _muted, fontSize: 10),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      text.paint(canvas, Offset(x - text.width / 2, 163));
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_GaugePainter old) =>
      old.percent != percent ||
      old.live != live ||
      old.direction != direction ||
      old.glint != glint;
}
