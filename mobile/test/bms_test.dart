import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:me_bateria/bms.dart';

Uint8List hex(String value) => Uint8List.fromList([
  for (int i = 0; i < value.length; i += 2)
    int.parse(value.substring(i, i + 2), radix: 16),
]);

// Unidentified protocol samples from the Java regression suite.
Uint8List stateFrame() =>
    hex('DD03001B1700000002D003E8000020780000000000001048030F020B760B82FBFF77');
Uint8List cellFrame() => hex(
  'DD04001E0F660F630F630F640F3E0F630F370F5B0F650F3B0F630F630F3C0F660F3DF9F977',
);

void fixChecksum(Uint8List bytes) {
  final end = 4 + bytes[3];
  final sum = bytes.sublist(2, end).fold<int>(0, (a, b) => a + b);
  final checksum = -sum & 0xffff;
  bytes[end] = checksum >> 8;
  bytes[end + 1] = checksum & 0xff;
}

void main() {
  group('JBD', () {
    test('emits only the two original read requests', () {
      expect(Jbd.request(3), hex('DDA50300FFFD77'));
      expect(Jbd.request(4), hex('DDA50400FFFC77'));
      expect(() => Jbd.request(0xe1), throwsArgumentError);
    });

    test('reassembles every fragment boundary and decodes original fields', () {
      final state = stateFrame();
      final reader = JbdReader();
      for (var split = 0; split < state.length; split++) {
        expect(reader.accept(state.sublist(0, split)), isNull);
        final basic = Jbd.basic(reader.accept(state.sublist(split))!);
        expect(basic.voltage, 58.88);
        expect(basic.current, 0);
        expect(basic.remaining, 7.2);
        expect(basic.nominal, 10);
        expect(basic.cycles, 0);
        expect(basic.protections, 0);
        expect(basic.firmware, 0x10);
        expect(basic.percent, 72);
        expect(basic.switches, 3);
        expect(basic.groups, 15);
        expect(basic.temperatures, [20.3, 21.5]);
      }
      final cells = Jbd.cells(reader.accept(cellFrame())!);
      expect(cells.length, 15);
      expect(cells[4], 3902);
    });

    test('preserves signed hundredths of an amp and charge endpoints', () {
      final state = stateFrame();
      for (final raw in [-32768, -123, 0, 123, 32767]) {
        for (final percent in [0, 100]) {
          state[6] = (raw >> 8) & 0xff;
          state[7] = raw & 0xff;
          state[23] = percent;
          fixChecksum(state);
          final basic = Jbd.basic(state);
          expect(basic.current, raw / 100);
          expect(basic.percent, percent);
        }
      }
    });

    test('discards corrupt packets and recovers on the next notification', () {
      final denied = stateFrame()..[2] = 0x80;
      fixChecksum(denied);
      final reader = JbdReader();
      for (final invalid in [
        [0xff],
        cellFrame()..[8] ^= 1,
        stateFrame()..last = 0x22,
        [...stateFrame(), 0],
        denied,
        List<int>.filled(263, 0xdd),
      ]) {
        expect(() => reader.accept(invalid), throwsFormatException);
        expect(reader.accept(cellFrame()), cellFrame());
      }
    });

    test('rejects wrong response types and truncated sensor or cell data', () {
      final shortSensors = stateFrame()..[26] = 3;
      fixChecksum(shortSensors);
      expect(() => Jbd.basic(shortSensors), throwsFormatException);
      expect(() => Jbd.basic(cellFrame()), throwsFormatException);
      expect(() => Jbd.cells(stateFrame()), throwsFormatException);
      expect(() => Jbd.cells(hex('DD04000100FFFF77')), throwsFormatException);
      expect(() => Jbd.cells(hex('DD040000000077')), throwsFormatException);
    });
  });

  group('Legacy persistence', () {
    test(
      'round-trips Base64 and time without exposing mutable source frames',
      () {
        final state = stateFrame();
        final cells = cellFrame();
        final legacy = {
          'time': 8000,
          'state': base64Encode(state),
          'cells': base64Encode(cells),
        };
        final original = Snapshot.fromFrames(state, cells, 8000);
        state[23] = 0;
        cells[5] = 0;
        expect(original.toJson(), legacy);
        final saved = Snapshot.fromJson(jsonDecode(jsonEncode(legacy)));
        expect(saved.time, 8000);
        expect(saved.basic.percent, 72);
        expect(saved.cells[4], 3902);
        expect(saved.toJson(), legacy);
        expect(() => saved.cells[0] = 0, throwsUnsupportedError);
        expect(() => saved.basic.temperatures.clear(), throwsUnsupportedError);
        expect(
          () => Snapshot.fromJson({...legacy, 'state': base64Encode(state)}),
          throwsFormatException,
        );
      },
    );

    test(
      'loads and writes exact history keys, including integer-valued watts',
      () {
        const legacy = {
          'address': 'fixture',
          'end': 10000,
          'duration': 6000,
          'peak': 120,
          'energy': 0.15,
          'interrupted': false,
        };
        final record = ChargeRecord.fromJson(legacy);
        expect(record.address, 'fixture');
        expect(record.end, 10000);
        expect(record.durationMs, 6000);
        expect(record.peakW, 120.0);
        expect(record.energyWh, 0.15);
        expect(record.interrupted, isFalse);
        expect(record.toJson(), legacy);
      },
    );
  });

  group('Charge detection', () {
    test(
      'confirms a normal stop after six seconds and integrates only charging',
      () {
        final tracker = ChargeSession();
        expect(tracker.sample(0, 60, 0), isNull);
        expect(tracker.sample(1000, 60, 2), isNull);
        expect(tracker.sample(4000, 60, 2), isNull);
        expect(tracker.sample(7000, 60, 0), isNull);
        expect(tracker.sample(10000, 60, -1), isNull);
        final done = tracker.sample(13000, 60, 0)!;
        expect(done.end, 7000);
        expect(done.durationMs, 6000);
        expect(done.peakW, 120);
        expect(done.energyWh, closeTo(0.15, 1e-6));
        expect(done.interrupted, isFalse);
        expect(tracker.sample(16000, 60, 0), isNull);
      },
    );

    test(
      'interrupts only active charging and never integrates across a gap',
      () {
        final tracker = ChargeSession();
        expect(tracker.sample(1000, 60, -1), isNull);
        expect(tracker.interrupt(), isNull);
        tracker.sample(19000, 60, 1);
        tracker.sample(22000, 60, 1);
        final disconnected = tracker.interrupt()!;
        expect(disconnected.interrupted, isTrue);
        expect(disconnected.end, 22000);
        expect(tracker.interrupt(), isNull);
        tracker.sample(25000, 60, 1);
        final gap = tracker.sample(50000, 60, 1)!;
        expect(gap.end, 25000);
        expect(gap.interrupted, isTrue);
        expect(tracker.interrupt()!.energyWh, 0);
      },
    );

    test('accepts a ten-second interval and cancels an unconfirmed stop', () {
      final tracker = ChargeSession();
      tracker.sample(0, 10, 1);
      expect(tracker.sample(10000, 10, 0), isNull);
      expect(tracker.sample(12000, 10, 3), isNull);
      expect(tracker.sample(13000, 10, 0), isNull);
      expect(tracker.sample(18000, 10, 0), isNull);
      final stopped = tracker.sample(19000, 10, 0)!;
      expect(stopped.end, 13000);
      expect(stopped.peakW, 30);
      expect(stopped.interrupted, isFalse);
    });
  });
}
