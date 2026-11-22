import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:me_bateria/controller.dart';
import 'package:me_bateria/screens.dart';

import 'bms_test.dart' show cellFrame, stateFrame;

// Presentation fixtures only. The widget tests never contact a BLE peripheral.
const _a = 'AA:BB:CC:DD:EE:01';
const _b = 'AA:BB:CC:DD:EE:02';

class _DeviceState extends ChangeNotifier implements BatteryController {
  @override
  bool ready = true;
  @override
  bool live = false;
  @override
  bool busy = false;
  @override
  String message = '';
  @override
  String selected = '';
  @override
  String status = 'Desconectada';
  @override
  final batteries = <String, SavedBattery>{};
  @override
  final history = <ChargeRecord>[];
  @override
  Snapshot? get latest => batteries[selected]?.reading;
  @override
  Future<void> connect(String id) async {
    selected = id;
    status = 'Procurando';
    notifyListeners();
  }

  @override
  Future<void> remove(String id) async {
    batteries.remove(id);
    if (selected == id) selected = '';
    notifyListeners();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> tapText(WidgetTester tester, String label) async {
  final finder = find.text(label).last;
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  expect(finder.hitTestable(), findsOneWidget);
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'Manual enrollment requires the full address and returns to the dashboard',
    (tester) async {
      final state = _DeviceState();
      await tester.pumpWidget(BatteryApp(controller: state));
      await tester.pumpAndSettle();
      expect(find.text('Sua primeira bateria'), findsOneWidget);
      await tapText(tester, 'Adicionar bateria');
      await tapText(tester, 'Digitar identificador');
      await tester.enterText(find.byType(TextField), 'BC');
      await tapText(tester, 'Buscar bateria');
      expect(find.textContaining('Use seis pares'), findsOneWidget);
      await tester.enterText(find.byType(TextField), _a.toLowerCase());
      await tapText(tester, 'Buscar bateria');
      expect(state.selected, _a);
      expect(find.text('Painel da bateria'), findsOneWidget);
      expect(find.text('Procurando'), findsOneWidget);
    },
  );

  testWidgets('Saved readings and cells retain reported units and extrema', (
    tester,
  ) async {
    final state = _DeviceState()..selected = _a;
    final snapshot = Snapshot.fromFrames(stateFrame(), cellFrame(), 100000);
    state.batteries[_a] = SavedBattery(_a, 'Oficina', reading: snapshot);
    await tester.pumpWidget(BatteryApp(controller: state));
    await tester.pumpAndSettle();
    expect(find.text('Última leitura'), findsOneWidget);
    expect(find.text('58,88 V'), findsOneWidget);
    await tapText(tester, 'Detalhes');
    expect(find.text('Grupos de células'), findsOneWidget);
    final low = snapshot.cells.reduce((a, b) => a < b ? a : b);
    final high = snapshot.cells.reduce((a, b) => a > b ? a : b);
    expect(find.text('Δ ${high - low} mV'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Temperaturas'), 150);
    expect(find.text('20,3 °C'), findsOneWidget);
    expect(find.text('21,5 °C'), findsOneWidget);
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(find.text('Detalhes da bateria'), findsNothing);
    expect(find.text('Detalhes').hitTestable(), findsOneWidget);
  });

  testWidgets(
    'History uses the full ID filter, retains removed batteries and expands observed metrics',
    (tester) async {
      final state = _DeviceState()..selected = _a;
      state.batteries[_a] = const SavedBattery(_a, 'Oficina');
      state.history.addAll([
        ChargeRecord(
          address: _a,
          end: 50000,
          durationMs: 43000,
          peakW: 34,
          energyWh: 0.32,
          interrupted: true,
        ),
        ChargeRecord(
          address: _b,
          end: 40000,
          durationMs: 11000,
          peakW: 20,
          energyWh: 0.15,
          interrupted: false,
        ),
      ]);
      await tester.pumpWidget(BatteryApp(controller: state));
      await tester.pumpAndSettle();
      await tapText(tester, 'Histórico');
      expect(find.text('Leitura interrompida'), findsOneWidget);
      expect(find.text('Recarga encerrada'), findsNothing);
      await tapText(tester, 'Detalhes da sessão');
      expect(find.text('34 W'), findsOneWidget);
      expect(find.text('0:00:43'), findsOneWidget);
      await tester.tap(find.byIcon(Icons.filter_list));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ListTile, _b));
      await tester.pumpAndSettle();
      expect(find.text('Recarga encerrada'), findsOneWidget);
      expect(find.text('Leitura interrompida'), findsNothing);
    },
  );
}
