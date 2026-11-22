import 'dart:async';
import 'dart:collection';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_reactive_ble/flutter_reactive_ble.dart';
import 'package:permission_handler/permission_handler.dart';

import 'bms.dart';

/// One peripheral at a time. Every notification, timer and subscription belongs
/// to the attempt that created it, even when reconnecting to the same battery.
class BatteryTransport {
  _BatteryLink? _active;
  Future<void> _retired = Future<void>.value();
  Future<void>? _permissionRequest;

  Stream<Snapshot> connect(
    String id, {
    required void Function(String) onStatus,
    required void Function(String) onName,
  }) {
    final previous = disconnect();
    final link = _BatteryLink(id.trim().toUpperCase(), onStatus, onName);
    _active = link;
    link.output.onListen = () => unawaited(link.start(previous, _permissions));
    link.output.onCancel = link.close;
    return link.output.stream;
  }

  Future<void> disconnect() {
    final previous = _active;
    _active = null;
    if (previous != null) {
      _retired = Future.wait<void>([_retired, previous.close()]).then((_) {});
    }
    return _retired;
  }

  Future<void> _permissions() async {
    // CoreBluetooth requests permission on first use.
    if (Platform.isIOS) return;
    if (!Platform.isAndroid) {
      throw const _BleProblem(
        'Este dispositivo não tem suporte à conexão Bluetooth do aplicativo.',
      );
    }
    // Two attempts can overlap a single Android system permission prompt.
    await (_permissionRequest ??= _androidPermissions().whenComplete(() {
      _permissionRequest = null;
    }));
  }

  Future<void> _androidPermissions() async {
    // The Android application targets devices with API 31 or newer.
    final result = await [
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
    ].request();
    if (result[Permission.bluetoothScan]?.isGranted != true ||
        result[Permission.bluetoothConnect]?.isGranted != true) {
      throw const _BleProblem(
        'Permita o acesso a dispositivos próximos nos ajustes do aplicativo.',
      );
    }
  }
}

class _BatteryLink {
  _BatteryLink(this.id, this.onStatus, this.onName);

  static final _mac = RegExp(r'^([0-9A-F]{2}:){5}[0-9A-F]{2}$');
  static final _service = Uuid.parse('ff00').expanded;
  static final _rx = Uuid.parse('ff01').expanded;
  static final _tx = Uuid.parse('ff02').expanded;
  static const _timeout = Duration(seconds: 15);

  final String id;
  final void Function(String) onStatus;
  final void Function(String) onName;
  final output = StreamController<Snapshot>();
  late final FlutterReactiveBle _ble = FlutterReactiveBle();
  late final List<int> _addressBytes;
  StreamSubscription<BleStatus>? _adapter;
  StreamSubscription<DiscoveredDevice>? _scan;
  StreamSubscription<ConnectionStateUpdate>? _connection;
  StreamSubscription<List<int>>? _notifications;
  Timer? _deadline;
  bool _scanStarted = false;
  bool _found = false;
  bool _linkEstablished = false;
  bool _closed = false;
  Future<void>? _cleanup;
  JbdReader? _reader;
  Completer<Uint8List?>? _reply;
  int _expectedCommand = 0;

  Future<void> start(
    Future<void> previous,
    Future<void> Function() permissions,
  ) async {
    try {
      await previous;
      if (_closed) return;
      if (!_mac.hasMatch(id)) {
        throw const _BleProblem(
          'Informe o identificador completo, com os seis pares da etiqueta.',
        );
      }
      _addressBytes = id
          .split(':')
          .map((s) => int.parse(s, radix: 16))
          .toList(growable: false);
      onStatus('Verificando Bluetooth');
      if (_closed) return;
      await permissions();
      if (_closed) return;
      _adapter = _ble.statusStream.listen(
        _adapterChanged,
        onError: (Object _) =>
            _fail('Não foi possível verificar o Bluetooth do celular.'),
        onDone: () => _fail('O serviço Bluetooth foi encerrado.'),
      );
    } on _BleProblem catch (e) {
      _fail(e.message);
    } catch (_) {
      _fail(
        'Não foi possível iniciar o Bluetooth. Confira as permissões do aplicativo.',
      );
    }
  }

  void _adapterChanged(BleStatus state) {
    if (_closed) return;
    switch (state) {
      case BleStatus.unknown:
        // CoreBluetooth can stay undetermined while its permission sheet is open.
        return;
      case BleStatus.unsupported:
        _fail('Este celular não oferece Bluetooth de baixa energia.');
      case BleStatus.unauthorized:
        _fail('Permita o uso de Bluetooth nos ajustes do aplicativo.');
      case BleStatus.poweredOff:
        _fail('Ative o Bluetooth do celular e tente novamente.');
      case BleStatus.locationServicesDisabled:
      case BleStatus.ready:
        // Android 12+: scan with neverForLocation and without the location toggle.
        if (!_scanStarted) _startScan();
    }
  }

  void _startScan() {
    _scanStarted = true;
    onStatus('Procurando');
    if (_closed) return;
    _arm(
      'Bateria não encontrada. Aproxime o celular e confira o identificador.',
    );
    _scan = _ble
        .scanForDevices(
          // Some BMS advertisements omit their GATT service.
          withServices: const [],
          scanMode: ScanMode.lowLatency,
          requireLocationServicesEnabled: false,
        )
        .listen(
          (device) {
            if (_closed || _found || !_matches(device)) return;
            _found = true;
            unawaited(_connectDevice(device));
          },
          onError: (Object _) =>
              _fail('A busca Bluetooth falhou. Tente novamente.'),
          onDone: () {
            if (!_found) {
              _fail('A busca Bluetooth terminou sem encontrar a bateria.');
            }
          },
        );
  }

  bool _matches(DiscoveredDevice device) {
    if (device.id.toUpperCase() == id) return true;
    return matchesBatteryManufacturerData(
      device.manufacturerData,
      _addressBytes,
    );
  }

  Future<void> _connectDevice(DiscoveredDevice device) async {
    try {
      _arm('A bateria foi encontrada, mas a conexão demorou demais.');
      await _scan?.cancel();
      if (_closed) return;
      if (device.name.trim().isNotEmpty) onName(device.name.trim());
      if (_closed) return;
      onStatus('Conectando');
      if (_closed) return;
      _connection = _ble
          .connectToDevice(
            id: device.id,
            servicesWithCharacteristicsToDiscover: {
              _service: [_rx, _tx],
            },
            connectionTimeout: _timeout,
          )
          .listen(
            (event) {
              if (_closed) return;
              if (event.connectionState == DeviceConnectionState.disconnected ||
                  event.failure != null) {
                _fail('A conexão Bluetooth com a bateria foi encerrada.');
              } else if (event.connectionState ==
                      DeviceConnectionState.connected &&
                  !_linkEstablished) {
                _linkEstablished = true;
                unawaited(_readBattery(device.id));
              }
            },
            onError: (Object _) =>
                _fail('Falha ao conectar à bateria. Tente novamente.'),
            onDone: () => _fail('A conexão com a bateria foi encerrada.'),
          );
    } catch (_) {
      _fail('Não foi possível abrir a conexão Bluetooth.');
    }
  }

  Future<void> _readBattery(String deviceId) async {
    try {
      _arm('A bateria não respondeu à descoberta dos serviços Bluetooth.');
      final services = await _ble
          .getDiscoveredServices(deviceId)
          .timeout(_timeout);
      if (_closed) return;
      final service = services
          .where((s) => s.id.expanded == _service)
          .firstOrNull;
      final rx = service?.characteristics
          .where((c) => c.id.expanded == _rx)
          .firstOrNull;
      final tx = service?.characteristics
          .where((c) => c.id.expanded == _tx)
          .firstOrNull;
      if (rx == null ||
          !(rx.isNotifiable || rx.isIndicatable) ||
          tx == null ||
          !(tx.isWritableWithoutResponse || tx.isWritableWithResponse)) {
        throw const _BleProblem(
          'O serviço de telemetria JBD está indisponível nesta bateria.',
        );
      }
      _deadline?.cancel();
      _notifications = rx.subscribe().listen(
        _receive,
        onError: (Object _) =>
            _fail('Não foi possível receber notificações do BMS.'),
        onDone: () => _fail('O BMS encerrou o envio de dados.'),
      );
      onStatus('Aguardando BMS');
      await Future<void>.delayed(const Duration(milliseconds: 500));
      var first = true;
      while (!_closed) {
        final state = await _query(tx, 3);
        if (_closed || state == null) return;
        final cells = await _query(tx, 4);
        if (_closed || cells == null) return;
        final sample = Snapshot.fromFrames(
          state,
          cells,
          DateTime.now().millisecondsSinceEpoch,
        );
        if (first) {
          first = false;
          onStatus('Conectada');
        }
        if (_closed) return;
        output.add(sample);
        await Future<void>.delayed(const Duration(milliseconds: 2500));
      }
    } on _BleProblem catch (e) {
      _fail(e.message);
    } on FormatException {
      _fail('O BMS enviou uma leitura inválida ou incompleta.');
    } on TimeoutException {
      _fail('O BMS não respondeu a tempo. Reconecte a bateria.');
    } catch (_) {
      _fail('A consulta de telemetria falhou. Reconecte a bateria.');
    }
  }

  Future<Uint8List?> _query(Characteristic tx, int command) async {
    _reader = JbdReader();
    _expectedCommand = command;
    final reply = _reply = Completer<Uint8List?>();
    _arm(
      'O BMS não respondeu à consulta de ${command == 3 ? "estado" : "células"}.',
    );
    // Register the receiver before writing. A notification can precede the GATT
    // write acknowledgment; the next command must wait for both to finish.
    await tx
        .write(
          Jbd.request(command),
          withResponse: !tx.isWritableWithoutResponse,
        )
        .timeout(_timeout);
    final frame = await reply.future;
    _deadline?.cancel();
    _reader = null;
    _reply = null;
    return frame;
  }

  void _receive(List<int> chunk) {
    final reply = _reply;
    if (_closed || reply == null || reply.isCompleted) return;
    try {
      final frame = _reader!.accept(chunk);
      if (frame == null) return;
      if (frame[1] != _expectedCommand) {
        _fail('O BMS respondeu a uma consulta diferente da solicitada.');
        return;
      }
      reply.complete(frame);
    } on FormatException {
      _fail('O BMS enviou uma resposta inválida ou recusou a consulta.');
    }
  }

  void _arm(String message) {
    _deadline?.cancel();
    _deadline = Timer(_timeout, () => _fail(message));
  }

  void _fail(String message) {
    if (_closed) return;
    output.addError(_BleProblem(message));
    unawaited(close());
  }

  Future<void> close() {
    if (_closed) return _cleanup ?? Future<void>.value();
    _closed = true;
    _deadline?.cancel();
    final reply = _reply;
    if (reply != null && !reply.isCompleted) reply.complete(null);
    _cleanup = _release();
    unawaited(output.close());
    return _cleanup!;
  }

  Future<void> _release() async {
    // Cancel notifications before the connection. An unplugged peripheral may
    // already have invalidated its GATT handles; still release the rest.
    for (final sub in [_scan, _notifications, _connection, _adapter]) {
      try {
        await sub?.cancel();
      } catch (_) {
        // A dropped connection can close a platform subscription first.
      }
    }
  }
}

class _BleProblem implements Exception {
  const _BleProblem(this.message);
  final String message;
  @override
  String toString() => message;
}

bool matchesBatteryManufacturerData(List<int> bytes, List<int> address) {
  if (address.length != 6) return false;
  // JBD advertisements can start with the label or a two-byte company ID.
  for (final offset in const [0, 2]) {
    if (bytes.length < offset + address.length) continue;
    var i = 0;
    while (i < address.length && bytes[offset + i] == address[i]) {
      i++;
    }
    if (i == address.length) return true;
  }
  return false;
}
