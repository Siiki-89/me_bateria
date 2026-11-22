import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'bms.dart';

final _identifier = RegExp(r'^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$');
bool isBatteryId(String value) => _identifier.hasMatch(value);

class SavedBattery {
  const SavedBattery(
    this.id,
    this.name, {
    this.bluetoothName = '',
    this.percent,
    this.lastAt,
    this.reading,
  });
  final String id, name, bluetoothName;
  final int? percent;
  final DateTime? lastAt;
  final Snapshot? reading;

  SavedBattery renamed(String value) => SavedBattery(
    id,
    value,
    bluetoothName: bluetoothName,
    percent: percent,
    lastAt: lastAt,
    reading: reading,
  );

  Map<String, dynamic> toJson() => {
    'name': name,
    'bluetoothName': bluetoothName,
    'percent': percent,
    'lastAt': lastAt?.millisecondsSinceEpoch,
    'reading': reading?.toJson(),
  };

  factory SavedBattery.fromJson(String id, Map<String, dynamic> data) {
    final cached = data['reading'];
    final time = data['lastAt'] as int?;
    return SavedBattery(
      id,
      data['name'] as String,
      bluetoothName: data['bluetoothName'] as String? ?? '',
      percent: data['percent'] as int?,
      lastAt: time == null ? null : DateTime.fromMillisecondsSinceEpoch(time),
      reading: cached == null
          ? null
          : Snapshot.fromJson(cached as Map<String, dynamic>),
    );
  }
}

class BatteryData {
  BatteryData(this.batteries, this.history, this.selected);
  final Map<String, SavedBattery> batteries;
  final List<ChargeRecord> history;
  final String selected;

  Map<String, dynamic> toJson() => {
    'version': 1,
    'selected': selected,
    'batteries': batteries.map((id, unit) => MapEntry(id, unit.toJson())),
    'history': history.map((session) => session.toJson()).toList(),
  };

  factory BatteryData.decode(String encoded) {
    final json = jsonDecode(encoded) as Map<String, dynamic>;
    if (json['version'] != 1)
      throw const FormatException('Versão dos dados desconhecida.');
    final devices = (json['batteries'] as Map<String, dynamic>).map(
      (id, data) => MapEntry(id, SavedBattery.fromJson(id, data)),
    );
    final sessions = (json['history'] as List)
        .map((e) => ChargeRecord.fromJson(e))
        .toList();
    return BatteryData(devices, sessions, json['selected'] as String);
  }

  factory BatteryData.fromLegacy(Map<dynamic, dynamic> prefs, String sessions) {
    final units = <String, SavedBattery>{};
    for (final key in prefs.keys.whereType<String>().where(isBatteryId)) {
      Snapshot? reading;
      final cached = prefs['reading_$key'] as String?;
      if (cached != null) {
        // The native pilot also ignores an unreadable optional snapshot.
        try {
          final frame = jsonDecode(cached);
          if (frame is Map<String, dynamic>) reading = Snapshot.fromJson(frame);
        } on FormatException {
          reading = null;
        }
      }
      final time = prefs['last_at_$key'] as int?;
      units[key] = SavedBattery(
        key,
        prefs[key] as String,
        bluetoothName: prefs['ble_name_$key'] as String? ?? '',
        percent: reading?.basic.percent ?? prefs['last_percent_$key'] as int?,
        lastAt: time == null ? null : DateTime.fromMillisecondsSinceEpoch(time),
        reading: reading,
      );
    }
    final selected = prefs['selected'] as String? ?? '';
    final records = (jsonDecode(sessions) as List)
        .map((e) => ChargeRecord.fromJson(e))
        .toList();
    return BatteryData(
      units,
      records,
      units.containsKey(selected) ? selected : '',
    );
  }
}

class BatteryStore {
  static const _channel = MethodChannel('br.com.memotos.bateria/legacy');
  static const _key = 'me_bateria_v1';
  final _prefs = SharedPreferencesAsync();

  Future<BatteryData> load() async {
    final existing = await _prefs.getString(_key);
    if (existing != null) return BatteryData.decode(existing);
    final legacy = Platform.isAndroid
        ? await _channel.invokeMapMethod<String, dynamic>('read')
        : null;
    final data = BatteryData.fromLegacy(
      legacy?['batteries'] as Map? ?? {},
      legacy?['sessions'] as String? ?? '[]',
    );
    await save(jsonEncode(data.toJson()));
    return data;
  }

  Future<void> save(String encoded) => _prefs.setString(_key, encoded);
}
