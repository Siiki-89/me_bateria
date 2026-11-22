import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:me_bateria/storage.dart';

void main() {
  const id = '00:11:22:33:44:55';
  test('Migra o cadastro, a seleção e as recargas do piloto', () {
    final old = {
      id: 'Moto azul',
      'selected': id,
      'ble_name_$id': 'DB20',
      'last_at_$id': 120000,
      'last_percent_$id': 98,
      'reading_$id': '[]',
    };
    final sessions = jsonEncode([
      {
        'address': id,
        'end': 120000,
        'duration': 18000,
        'peak': 50,
        'energy': 0.09,
        'interrupted': false,
      },
    ]);
    final imported = BatteryData.fromLegacy(old, sessions);
    final restored = BatteryData.decode(jsonEncode(imported.toJson()));
    expect(restored.selected, id);
    final unit = restored.batteries[id]!;
    expect(unit.name, 'Moto azul');
    expect(unit.bluetoothName, 'DB20');
    expect(unit.lastAt!.millisecondsSinceEpoch, 120000);
    expect(unit.percent, 98);
    expect(unit.reading, isNull);
    expect(restored.history.single.address, id);
    expect(restored.history.single.energyWh, 0.09);
    expect(restored.history.single.durationMs, 18000);
  });
  test('Ignora seleção antiga que já foi removida', () {
    expect(BatteryData.fromLegacy({'selected': id}, '[]').selected, isEmpty);
  });
}
