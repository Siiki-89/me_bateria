import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

class Basic {
  Basic._({
    required this.voltage,
    required this.current,
    required this.remaining,
    required this.nominal,
    required this.cycles,
    required this.protections,
    required this.firmware,
    required this.percent,
    required this.switches,
    required this.groups,
    required List<double> temperatures,
  }) : temperatures = List.unmodifiable(temperatures);

  final double voltage;
  final double current;
  final double remaining;
  final double nominal;
  final int cycles;
  final int protections;
  final int firmware;
  final int percent;
  final int switches;
  final int groups;
  final List<double> temperatures;
}

class Jbd {
  static Uint8List request(int command) {
    if (command != 3 && command != 4) {
      throw ArgumentError.value(command, 'command', 'Consulta desconhecida');
    }
    return Uint8List.fromList([
      0xdd,
      0xa5,
      command,
      0,
      0xff,
      -command & 0xff,
      0x77,
    ]);
  }

  static int _word(List<int> bytes, int at) => (bytes[at] << 8) | bytes[at + 1];

  static void _validate(List<int> frame) {
    const invalid = FormatException(
      'O BMS enviou uma resposta inválida ou recusou a consulta.',
    );
    if (frame.length < 7 ||
        frame.first != 0xdd ||
        frame.last != 0x77 ||
        frame.length != frame[3] + 7 ||
        frame[2] != 0) {
      throw invalid;
    }
    final end = 4 + frame[3];
    int sum = 0;
    for (int i = 2; i < end; i++) {
      sum += frame[i];
    }
    if ((-sum & 0xffff) != _word(frame, end)) throw invalid;
  }

  static Basic basic(List<int> frame) {
    _validate(frame);
    final length = frame[3];
    if (frame[1] != 3 || length < 23) {
      throw const FormatException('Resposta de estado incompleta.');
    }
    final sensors = frame[26];
    if (23 + 2 * sensors > length) {
      throw const FormatException('Temperaturas incompletas.');
    }
    return Basic._(
      voltage: _word(frame, 4) / 100,
      current: _word(frame, 6).toSigned(16) / 100,
      remaining: _word(frame, 8) / 100,
      nominal: _word(frame, 10) / 100,
      cycles: _word(frame, 12),
      protections: _word(frame, 20),
      firmware: frame[22],
      percent: frame[23],
      switches: frame[24],
      groups: frame[25],
      temperatures: [
        for (int i = 0; i < sensors; i++)
          (_word(frame, 27 + i * 2) - 2731) / 10,
      ],
    );
  }

  /// Individual cell-group voltages, in millivolts.
  static List<int> cells(List<int> frame) {
    _validate(frame);
    final length = frame[3];
    if (frame[1] != 4 || length == 0 || length.isOdd) {
      throw const FormatException('Resposta de células inválida.');
    }
    return List.unmodifiable([
      for (int offset = 0; offset < length; offset += 2)
        _word(frame, 4 + offset),
    ]);
  }
}

/// One request is outstanding at a time, as on the original BLE transport.
class JbdReader {
  final List<int> _pending = [];

  Uint8List? accept(List<int> fragment) {
    if (_pending.length + fragment.length > 262) {
      _pending.clear();
      throw const FormatException('A resposta do BMS excede o tamanho máximo.');
    }
    _pending.addAll(fragment);
    if (_pending.isEmpty) return null;
    if (_pending.first != 0xdd) {
      _pending.clear();
      throw const FormatException('O início da resposta do BMS é inválido.');
    }
    if (_pending.length < 4 || _pending.length < _pending[3] + 7) return null;
    try {
      Jbd._validate(_pending);
      return Uint8List.fromList(_pending);
    } finally {
      _pending.clear();
    }
  }
}

/// A paired state and cell reading, persisted as the two exact original frames.
class Snapshot {
  factory Snapshot.fromFrames(
    List<int> stateFrame,
    List<int> cellFrame,
    int time,
  ) {
    final state = Uint8List.fromList(stateFrame);
    final cells = Uint8List.fromList(cellFrame);
    return Snapshot._(Jbd.basic(state), Jbd.cells(cells), time, state, cells);
  }

  factory Snapshot.fromJson(Map<String, dynamic> json) {
    return Snapshot.fromFrames(
      base64Decode(_string(json, 'state')),
      base64Decode(_string(json, 'cells')),
      _number(json, 'time').toInt(),
    );
  }

  Snapshot._(
    this.basic,
    this.cells,
    this.time,
    this._stateFrame,
    this._cellFrame,
  );
  final Basic basic;
  final List<int> cells;
  final int time;
  final Uint8List _stateFrame;
  final Uint8List _cellFrame;

  Map<String, dynamic> toJson() => {
    'time': time,
    'state': base64Encode(_stateFrame),
    'cells': base64Encode(_cellFrame),
  };
}

class ChargeSummary {
  const ChargeSummary._(
    this.end,
    this.durationMs,
    this.peakW,
    this.energyWh,
    this.interrupted,
  );

  /// Timestamp on the monotonic sample clock, before conversion to wall time.
  final int end;
  final int durationMs;
  final double peakW;
  final double energyWh;
  final bool interrupted;
}

class ChargeSession {
  static const _maxGapMs = 10000;
  static const _stopConfirmMs = 6000;
  int? _start;
  int _last = 0;
  int? _stopped;
  double _previousW = 0;
  double _peakW = 0;
  double _energyWh = 0;

  /// Supply monotonic elapsed milliseconds, independent of wall-clock changes.
  ChargeSummary? sample(int elapsedMs, double volts, double amps) {
    final watts = math.max(0.0, volts * amps);
    ChargeSummary? completed;
    if (_start != null && elapsedMs - _last > _maxGapMs) {
      completed = interrupt();
    }
    if (_start == null) {
      if (watts == 0) return completed;
      _start = elapsedMs;
    } else {
      _energyWh += (_previousW + watts) * (elapsedMs - _last) / 7200000;
    }
    _last = elapsedMs;
    _previousW = watts;
    _peakW = math.max(_peakW, watts);
    if (watts > 0) {
      _stopped = null;
    } else if (_stopped == null) {
      _stopped = elapsedMs;
    } else if (elapsedMs - _stopped! >= _stopConfirmMs) {
      return _finish(_stopped!, false);
    }
    return completed;
  }

  ChargeSummary? interrupt() => _start == null ? null : _finish(_last, true);

  ChargeSummary _finish(int end, bool interrupted) {
    final summary = ChargeSummary._(
      end,
      end - _start!,
      _peakW,
      _energyWh,
      interrupted,
    );
    _start = null;
    _stopped = null;
    _previousW = 0;
    _peakW = 0;
    _energyWh = 0;
    return summary;
  }
}

class ChargeRecord {
  const ChargeRecord({
    required this.address,
    required this.end,
    required this.durationMs,
    required this.peakW,
    required this.energyWh,
    required this.interrupted,
  });

  factory ChargeRecord.fromJson(Map<String, dynamic> json) {
    final flag = json['interrupted'];
    final interrupted = flag is bool
        ? flag
        : bool.tryParse('$flag', caseSensitive: false);
    if (interrupted == null)
      throw const FormatException('Estado da recarga inválido.');
    return ChargeRecord(
      address: _string(json, 'address'),
      end: _number(json, 'end').toInt(),
      durationMs: _number(json, 'duration').toInt(),
      peakW: _number(json, 'peak').toDouble(),
      energyWh: _number(json, 'energy').toDouble(),
      interrupted: interrupted,
    );
  }

  final String address;

  /// Unix epoch milliseconds, as stored by the original Android application.
  final int end;
  final int durationMs;
  final double peakW;
  final double energyWh;
  final bool interrupted;

  Map<String, dynamic> toJson() => {
    'address': address,
    'end': end,
    'duration': durationMs,
    'peak': peakW,
    'energy': energyWh,
    'interrupted': interrupted,
  };
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String) return value;
  throw FormatException('Texto inválido no registro: $key.');
}

num _number(Map<String, dynamic> json, String key) {
  final raw = json[key];
  final value = raw is num ? raw : num.tryParse('$raw');
  if (value != null && value.isFinite) return value;
  throw FormatException('Número inválido no registro: $key.');
}
