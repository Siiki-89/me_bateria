import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import 'bms.dart';
import 'storage.dart';
import 'transport.dart';
export 'bms.dart';
export 'storage.dart' show SavedBattery, isBatteryId;

class BatteryController extends ChangeNotifier {
  BatteryController({BatteryStore? store, BatteryTransport? transport})
    : _store = store ?? BatteryStore(),
      _transport = transport ?? BatteryTransport();

  final BatteryStore _store;
  final BatteryTransport _transport;
  final _clock = Stopwatch()..start();
  final _session = ChargeSession();
  final batteries = <String, SavedBattery>{};
  final history = <ChargeRecord>[];
  String selected = '',
      _savedSelection = '',
      status = 'Desconectada',
      message = '';
  bool live = false, busy = false, ready = false;
  Snapshot? get latest => batteries[selected]?.reading;

  StreamSubscription<Snapshot>? _stream;
  Future<void> _operations = Future.value(), _writes = Future.value();
  int _generation = 0, _persistedAt = 0;
  String _advertisedName = '';
  bool _disposed = false;

  Future<void> initialize() async {
    final data = await _store.load();
    batteries
      ..clear()
      ..addAll(data.batteries);
    history
      ..clear()
      ..addAll(data.history);
    selected = _savedSelection = data.selected;
    ready = true;
    _update();
  }

  void _update() {
    if (!_disposed) notifyListeners();
  }

  Future<void> _operation(Future<void> Function() action) {
    final current = _operations.then((_) => action());
    _operations = current.catchError((Object e) {
      message = 'Não foi possível concluir a operação.';
      _update();
    });
    return current;
  }

  Future<void> flush() {
    if (!ready) return Future.value();
    final encoded = jsonEncode(
      BatteryData(batteries, history, _savedSelection).toJson(),
    );
    _writes = _writes.then((_) => _store.save(encoded)).catchError((Object e) {
      message = 'Não foi possível salvar os dados neste celular.';
      _update();
    });
    return _writes;
  }

  Future<void> connect(String id) => _operation(() async {
    final address = id.trim().toUpperCase();
    if (!ready ||
        !isBatteryId(address) ||
        (_stream != null && selected == address))
      return;
    await _stop();
    final generation = ++_generation;
    selected = address;
    live = false;
    busy = true;
    status = 'Procurando';
    message = '';
    _advertisedName = '';
    _persistedAt = 0;
    _update();
    try {
      final readings = _transport.connect(
        address,
        onStatus: (value) {
          if (_generation == generation) {
            status = value;
            _update();
          }
        },
        onName: (value) {
          if (_generation == generation) _advertisedName = value;
        },
      );
      _stream = readings.listen(
        (snapshot) {
          if (_generation != generation) return;
          final before = batteries[address];
          batteries[address] = SavedBattery(
            address,
            before?.name ?? 'Minha bateria',
            bluetoothName: _advertisedName.isEmpty
                ? before?.bluetoothName ?? ''
                : _advertisedName,
            percent: snapshot.basic.percent,
            lastAt: DateTime.fromMillisecondsSinceEpoch(snapshot.time),
            reading: snapshot,
          );
          _savedSelection = address;
          final record = _session.sample(
            _clock.elapsedMilliseconds,
            snapshot.basic.voltage,
            snapshot.basic.current,
          );
          _record(record);
          if (!live ||
              before?.percent != snapshot.basic.percent ||
              record != null ||
              _clock.elapsedMilliseconds - _persistedAt >= 60000) {
            _persistedAt = _clock.elapsedMilliseconds;
            unawaited(flush());
          }
          live = true;
          busy = false;
          status = 'Conectada';
          _update();
        },
        onError: (Object error) => _ended(generation, error.toString()),
        onDone: () =>
            _ended(generation, 'A conexão com a bateria foi encerrada.'),
      );
    } catch (error) {
      _ended(generation, error.toString());
    }
  });

  void _record(ChargeSummary? summary) {
    if (summary == null) return;
    final ended =
        DateTime.now().millisecondsSinceEpoch -
        (_clock.elapsedMilliseconds - summary.end);
    history.insert(
      0,
      ChargeRecord(
        address: selected,
        end: ended,
        durationMs: summary.durationMs,
        peakW: summary.peakW,
        energyWh: summary.energyWh,
        interrupted: summary.interrupted,
      ),
    );
    if (history.length > 50) history.removeRange(50, history.length);
  }

  void _ended(int generation, String reason) {
    if (_generation != generation) return;
    ++_generation;
    unawaited(
      _operation(() async {
        // A subsequent request may already have established a different connection.
        if (_generation != generation + 1) return;
        await _stop();
        status = 'Sem conexão';
        message = reason;
        _update();
      }),
    );
  }

  Future<void> _stop() async {
    ++_generation;
    final sub = _stream;
    _stream = null;
    final wasLive = live;
    _record(_session.interrupt());
    live = false;
    busy = false;
    status = 'Desconectada';
    message = '';
    if (sub != null) {
      await _transport.disconnect();
      await sub.cancel();
    }
    if (wasLive) await flush();
  }

  Future<void> disconnect() => _operation(() async {
    await _stop();
    _update();
  });

  Future<void> rename(String id, String name) => _operation(() async {
    final unit = batteries[id];
    if (unit == null) return;
    final clean = name.trim();
    batteries[id] = unit.renamed(clean.isEmpty ? 'Minha bateria' : clean);
    _update();
    await flush();
  });

  Future<void> remove(String id) => _operation(() async {
    if (!batteries.containsKey(id)) return;
    if (selected == id) {
      await _stop();
      selected = '';
    }
    batteries.remove(id);
    if (_savedSelection == id) _savedSelection = '';
    _update();
    await flush();
  });

  @override
  void dispose() {
    _disposed = true;
    unawaited(disconnect());
    super.dispose();
  }
}
