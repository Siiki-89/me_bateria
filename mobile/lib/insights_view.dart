part of 'screens.dart';

class _Details extends StatelessWidget {
  const _Details({required this.c});
  final BatteryController c;
  @override
  Widget build(BuildContext context) {
    final snapshot = _reading(c);
    final basic = snapshot?.basic;
    final time = _readTime(c);
    final temperatures = basic?.temperatures ?? const <double>[];
    String state(int mask) => basic == null
        ? '—'
        : (basic.switches & mask) != 0
        ? 'Habilitada'
        : 'Desabilitada';
    return Scaffold(
      appBar: AppBar(title: const Text('Detalhes da bateria')),
      body: SafeArea(
        top: false,
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 700),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(18, 8, 18, 28),
              children: [
                Text('${_name(c, c.selected)} · ${c.status}', style: _caption),
                if (time != null)
                  Text('Última leitura · ${_stamp(time)}', style: _caption),
                _gap,
                _Cells(snapshot),
                _gap,
                _Panel(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Temperaturas', style: _heading),
                      _gap,
                      if (temperatures.isEmpty)
                        Text(
                          basic == null
                              ? 'Sem leituras de temperatura.'
                              : 'O BMS não informou sensores de temperatura.',
                          style: _caption,
                        )
                      else
                        LayoutBuilder(
                          builder: (context, box) {
                            final columns =
                                box.maxWidth /
                                        MediaQuery.textScalerOf(
                                          context,
                                        ).scale(1) >=
                                    240
                                ? 2
                                : 1;
                            final width =
                                (box.maxWidth - (columns - 1) * 8) / columns;
                            return Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: [
                                for (var i = 0; i < temperatures.length; i++)
                                  Container(
                                    width: width,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                      vertical: 10,
                                    ),
                                    decoration: BoxDecoration(
                                      color: _elevated,
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          'Sensor ${i + 1}',
                                          style: _caption,
                                        ),
                                        _smallGap,
                                        Text(
                                          _fmt(temperatures[i], 1, '°C'),
                                          style: _number,
                                        ),
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
                      const Text('Informações do BMS', style: _heading),
                      _smallGap,
                      _Readout('Ciclos', basic?.cycles.toString() ?? '—'),
                      _Readout('Carga', state(1)),
                      _Readout('Descarga', state(2)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Cells extends StatelessWidget {
  const _Cells(this.snapshot);
  final Snapshot? snapshot;

  @override
  Widget build(BuildContext context) {
    final cells = snapshot?.cells ?? const <int>[];
    if (cells.isEmpty) {
      return const _Panel(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Grupos de células', style: _heading),
            _gap,
            Text(
              'As tensões aparecem após uma leitura dos grupos de células.',
              style: _caption,
            ),
          ],
        ),
      );
    }
    final low = cells.reduce(math.min), high = cells.reduce(math.max);
    final avg =
        cells.fold<int>(0, (total, next) => total + next) / cells.length;
    final top = math.max(10, ((high + 9) ~/ 10) * 10);
    final bottom = math.max(0, math.min(low ~/ 10 * 10, top - 150));
    String volts(num mv) => _fmt(mv / 1000, 3, 'V');
    return _Panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            spacing: 8,
            runSpacing: 4,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              const Text('Grupos de células', style: _heading),
              _Badge('Δ ${high - low} mV', _ink),
            ],
          ),
          _smallGap,
          Text(
            low == high
                ? 'Mín. e máx. ${volts(low)}'
                : 'Mín. ${volts(low)} · #${cells.indexOf(low) + 1}     Máx. ${volts(high)} · #${cells.indexOf(high) + 1}',
            style: _caption,
          ),
          _gap,
          for (var i = 0; i < cells.length; i++)
            Builder(
              builder: (context) {
                final minimum = cells[i] == low && high != low,
                    maximum = cells[i] == high && high != low;
                final label =
                    'G${_two(i + 1)}${minimum
                        ? ' ↓'
                        : maximum
                        ? ' ↑'
                        : ''}';
                final voltage = volts(cells[i]);
                return Semantics(
                  label:
                      'Grupo ${i + 1}, $voltage${minimum
                          ? ', tensão mínima'
                          : maximum
                          ? ', tensão máxima'
                          : ''}',
                  excludeSemantics: true,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      children: [
                        SizedBox(
                          width: MediaQuery.textScalerOf(context).scale(51),
                          child: Text(
                            label,
                            style: _caption.copyWith(
                              color: minimum || maximum ? _ink : _muted,
                              fontSize: 13,
                              fontWeight: minimum || maximum
                                  ? FontWeight.w600
                                  : FontWeight.w400,
                            ),
                          ),
                        ),
                        Expanded(
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(3),
                            child: LinearProgressIndicator(
                              minHeight: 5,
                              backgroundColor: _border,
                              color: _accent,
                              value: (cells[i] - bottom) / (top - bottom),
                              semanticsLabel:
                                  'Tensão relativa à escala informada',
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Text(voltage, style: _number.copyWith(fontSize: 15)),
                      ],
                    ),
                  ),
                );
              },
            ),
          _gap,
          Text(
            '${cells.length} ${cells.length == 1 ? 'grupo' : 'grupos'} · Média ${volts(avg)}',
            style: _caption,
          ),
          Text(
            'Escala das barras de ${volts(bottom)} a ${volts(top)}',
            style: _caption.copyWith(fontSize: 12),
          ),
          if (cells.length > 1 && low == high) ...[
            _smallGap,
            const Text('Todos os grupos têm a mesma tensão.', style: _caption),
          ],
          if (snapshot!.basic.groups != cells.length) ...[
            _smallGap,
            const Text(
              'A quantidade de grupos difere entre as respostas do BMS.',
              style: _caption,
            ),
          ],
        ],
      ),
    );
  }
}

class _History extends StatelessWidget {
  const _History({
    required this.c,
    required this.filter,
    required this.changeFilter,
  });
  final BatteryController c;
  final String filter;
  final VoidCallback changeFilter;

  @override
  Widget build(BuildContext context) {
    final entries =
        c.history.where((r) => filter.isEmpty || r.address == filter).toList()
          ..sort((a, b) => b.end.compareTo(a.end));
    String previous = '';
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 24),
      children: [
        const Text('Histórico de recargas', style: _title),
        _gap,
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton.icon(
            onPressed: changeFilter,
            icon: const Icon(Icons.filter_list, size: 19),
            label: Text(
              filter.isEmpty ? 'Todas as baterias' : _name(c, filter),
            ),
          ),
        ),
        _gap,
        if (entries.isEmpty)
          _Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.history, size: 36, color: _accent),
                const SizedBox(height: 18),
                const Text('Nenhuma sessão registrada', style: _heading),
                _smallGap,
                Text(
                  filter.isEmpty
                      ? 'As sessões aparecem aqui quando o app acompanha uma recarga por Bluetooth.'
                      : 'As recargas observadas para esta bateria aparecerão aqui.',
                  style: _caption,
                ),
              ],
            ),
          ),
        for (final entry in entries)
          ...() {
            final date = DateTime.fromMillisecondsSinceEpoch(entry.end);
            final day = _day(date);
            final first = previous != day;
            previous = day;
            return [
              if (first)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text(day, style: _heading),
                ),
              _Session(
                key: ValueKey((entry.address, entry.end, entry.durationMs)),
                entry: entry,
                name: _name(c, entry.address),
              ),
              _gap,
            ];
          }(),
      ],
    );
  }
}

class _Session extends StatelessWidget {
  const _Session({super.key, required this.entry, required this.name});
  final ChargeRecord entry;
  final String name;
  @override
  Widget build(BuildContext context) {
    final date = DateTime.fromMillisecondsSinceEpoch(entry.end);
    final seconds = entry.durationMs ~/ 1000;
    final duration =
        '${seconds ~/ 3600}:${_two((seconds ~/ 60) % 60)}:${_two(seconds % 60)}';
    return _Panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _Badge(
            entry.interrupted ? 'Leitura interrompida' : 'Recarga encerrada',
            entry.interrupted ? _amber : _mint,
          ),
          _gap,
          Text(name, style: _heading),
          _gap,
          Text(
            _fmt(entry.energyWh, 2, 'Wh'),
            style: _number.copyWith(fontSize: 28, letterSpacing: -.6),
          ),
          const Text('Energia estimada', style: _caption),
          _gap,
          Text(
            '${entry.interrupted ? 'Última leitura' : 'Encerramento'} às ${_hour(date)}',
            style: _caption,
          ),
          const Divider(height: 16),
          ExpansionTile(
            tilePadding: EdgeInsets.zero,
            childrenPadding: EdgeInsets.zero,
            expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
            shape: const Border(),
            collapsedShape: const Border(),
            title: const Text(
              'Detalhes da sessão',
              style: TextStyle(color: _accent),
            ),
            children: [
              _Readout('Duração observada', duration),
              _Readout('Pico de potência', _fmt(entry.peakW, 0, 'W')),
              _smallGap,
              const Text('Identificador Bluetooth', style: _caption),
              SelectableText(entry.address),
              _smallGap,
            ],
          ),
        ],
      ),
    );
  }
}
