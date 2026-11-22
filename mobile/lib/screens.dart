import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import 'controller.dart';

part 'dashboard_view.dart';
part 'insights_view.dart';
part 'battery_views.dart';

const _bg = Color(0xFF0B1218);
const _surface = Color(0xFF14212A);
const _elevated = Color(0xFF21333D);
const _ink = Color(0xFFF0F5F6);
const _muted = Color(0xFFA7B9C3);
const _accent = Color(0xFF51D3D3);
const _mint = Color(0xFF8DE7C4);
const _amber = Color(0xFFF1BC74);
const _border = Color(0xFF2A3D48);

const _title = TextStyle(
  color: _ink,
  fontSize: 29,
  height: 1.12,
  fontWeight: FontWeight.w600,
  letterSpacing: -.8,
);
const _heading = TextStyle(
  color: _ink,
  fontSize: 18,
  height: 1.25,
  fontWeight: FontWeight.w600,
  letterSpacing: -.35,
);
const _caption = TextStyle(color: _muted, fontSize: 14, height: 1.35);
const _number = TextStyle(
  color: _ink,
  fontSize: 19,
  fontWeight: FontWeight.w600,
  fontFeatures: [FontFeature.tabularFigures()],
);
const _gap = SizedBox(height: 12);
const _smallGap = SizedBox(height: 6);

class BatteryApp extends StatelessWidget {
  const BatteryApp({super.key, required this.controller});
  final BatteryController controller;

  @override
  Widget build(BuildContext context) {
    final scheme =
        ColorScheme.fromSeed(
          seedColor: _accent,
          brightness: Brightness.dark,
        ).copyWith(
          primary: _accent,
          onPrimary: _bg,
          surface: _surface,
          onSurface: _ink,
          onSurfaceVariant: _muted,
          error: _amber,
        );
    return MaterialApp(
      title: 'ME Bateria',
      debugShowCheckedModeBanner: false,
      locale: const Locale('pt', 'BR'),
      supportedLocales: const [Locale('pt', 'BR')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: scheme,
        scaffoldBackgroundColor: _bg,
        dividerColor: _border,
        appBarTheme: const AppBarTheme(
          backgroundColor: _bg,
          foregroundColor: _ink,
          surfaceTintColor: Colors.transparent,
          systemOverlayStyle: SystemUiOverlayStyle.light,
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            minimumSize: const Size(48, 50),
            backgroundColor: _accent,
            foregroundColor: _bg,
            textStyle: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w600,
            ),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
            ),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: _elevated,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
        ),
        bottomSheetTheme: const BottomSheetThemeData(
          backgroundColor: _surface,
          showDragHandle: true,
        ),
        navigationBarTheme: const NavigationBarThemeData(
          backgroundColor: _bg,
          indicatorColor: Color(0xFF1C383E),
          elevation: 0,
          height: 68,
        ),
      ),
      home: ListenableBuilder(
        listenable: controller,
        builder: (context, _) => controller.ready
            ? _Home(c: controller)
            : Scaffold(
                body: SafeArea(
                  child: Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const _Brand(),
                          const SizedBox(height: 24),
                          if (controller.message.isEmpty)
                            const SizedBox.square(
                              dimension: 24,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          else
                            Text(
                              controller.message,
                              textAlign: TextAlign.center,
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
      ),
    );
  }
}

class _Brand extends StatelessWidget {
  const _Brand();
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      ExcludeSemantics(
        child: ClipOval(
          child: Image.asset('assets/me-symbol.png', width: 30, height: 30),
        ),
      ),
      const SizedBox(width: 9),
      const Text('ME Bateria', style: _heading),
    ],
  );
}

class _Panel extends StatelessWidget {
  const _Panel({required this.child, this.highlight = false});
  final Widget child;
  final bool highlight;
  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: _surface,
      borderRadius: BorderRadius.circular(20),
      border: highlight ? Border.all(color: const Color(0xFF366368)) : null,
    ),
    child: child,
  );
}

class _Badge extends StatelessWidget {
  const _Badge(this.text, [this.color = _muted]);
  final String text;
  final Color color;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
    decoration: BoxDecoration(
      color: Color.alphaBlend(color.withAlpha(24), _surface),
      borderRadius: BorderRadius.circular(30),
    ),
    child: Text(
      text,
      style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 12),
    ),
  );
}

class _Readout extends StatelessWidget {
  const _Readout(this.label, this.value);
  final String label, value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      children: [
        Expanded(child: Text(label, style: _caption)),
        const SizedBox(width: 8),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: _number.copyWith(fontSize: 16),
          ),
        ),
      ],
    ),
  );
}

String _fmt(num? value, int digits, String unit) => value == null
    ? '—'
    : '${value.toStringAsFixed(digits).replaceAll('.', ',')} $unit';
String _two(int n) => n.toString().padLeft(2, '0');
String _day(DateTime date) =>
    '${_two(date.day)}/${_two(date.month)}/${date.year}';
String _hour(DateTime date) => '${_two(date.hour)}:${_two(date.minute)}';
String _stamp(DateTime d) => '${_day(d)} · ${_hour(d)}';
Snapshot? _reading(BatteryController c) =>
    c.latest ?? c.batteries[c.selected]?.reading;
DateTime? _readTime(BatteryController c) {
  final data = _reading(c);
  return data == null
      ? c.batteries[c.selected]?.lastAt
      : DateTime.fromMillisecondsSinceEpoch(data.time);
}

String _name(BatteryController c, String id) =>
    c.batteries[id]?.name ?? (id.isEmpty ? 'Sua bateria' : id);

class _Home extends StatefulWidget {
  const _Home({required this.c});
  final BatteryController c;
  @override
  State<_Home> createState() => _HomeState();
}

class _HomeState extends State<_Home> {
  late int tab = widget.c.selected.isEmpty ? 1 : 0;
  String filter = '';
  BatteryController get c => widget.c;

  void _switch(int next, {String? batteryFilter}) {
    setState(() {
      if (next == 2 && (tab != 2 || batteryFilter != null)) {
        filter = batteryFilter ?? c.selected;
      }
      tab = next;
    });
  }

  Future<void> _connect(String id) async {
    _switch(0);
    await c.connect(id);
  }

  Future<void> _add() async {
    final id = await Navigator.of(
      context,
    ).push<String>(MaterialPageRoute(builder: (_) => const _Enrollment()));
    if (id != null && mounted) await _connect(id);
  }

  void _details() => Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => ListenableBuilder(
        listenable: c,
        builder: (_, _) => _Details(c: c),
      ),
    ),
  );

  Future<void> _options(String id) async {
    final battery = c.batteries[id];
    if (battery == null) return;
    final action = await _choose<_BatteryAction>(context, battery.name, [
      ListTile(
        contentPadding: EdgeInsets.zero,
        title: const Text('Identificador Bluetooth'),
        subtitle: SelectableText(id),
      ),
      ListTile(
        contentPadding: EdgeInsets.zero,
        title: const Text('Nome Bluetooth'),
        subtitle: Text(
          battery.bluetoothName.isEmpty
              ? 'Indisponível'
              : battery.bluetoothName,
        ),
      ),
      const Divider(),
      const _Option(
        _BatteryAction.history,
        Icons.history,
        'Histórico desta bateria',
      ),
      const _Option(
        _BatteryAction.rename,
        Icons.edit_outlined,
        'Editar apelido',
      ),
      if (c.selected == id && (c.live || c.busy))
        const _Option(
          _BatteryAction.disconnect,
          Icons.bluetooth_disabled,
          'Desconectar',
        ),
      const _Option(
        _BatteryAction.remove,
        Icons.delete_outline,
        'Remover bateria',
      ),
    ]);
    if (!mounted || action == null) return;
    switch (action) {
      case _BatteryAction.history:
        _switch(2, batteryFilter: id);
      case _BatteryAction.disconnect:
        await c.disconnect();
      case _BatteryAction.rename:
        final name = await showDialog<String>(
          context: context,
          builder: (_) => _Rename(battery.name),
        );
        if (name != null && mounted) await c.rename(id, name);
      case _BatteryAction.remove:
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Remover bateria?'),
            content: Text(
              '${battery.name}\n$id\n\nO histórico de recargas será preservado. Você poderá cadastrar esta bateria novamente pela etiqueta.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancelar'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text(
                  'Confirmar remoção',
                  style: TextStyle(color: _amber),
                ),
              ),
            ],
          ),
        );
        if (confirmed == true && mounted) await c.remove(id);
    }
  }

  Future<void> _filter() async {
    final ids = <String>{
      ...c.batteries.keys,
      ...c.history.map((s) => s.address),
      if (filter.isNotEmpty) filter,
    }.toList()..sort();
    final next = await _choose<String>(context, 'Filtrar por bateria', [
      _Option(
        '',
        Icons.all_inclusive,
        'Todas as baterias',
        selected: filter.isEmpty,
      ),
      for (final id in ids)
        _Option(
          id,
          Icons.battery_std_outlined,
          _name(c, id),
          subtitle: _name(c, id) == id ? null : id,
          selected: filter == id,
        ),
    ]);
    if (next != null && mounted) setState(() => filter = next);
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      _Dashboard(
        c: c,
        add: _add,
        switchBattery: () => _switch(1),
        connect: () => _connect(c.selected),
        details: _details,
        options: () => _options(c.selected),
      ),
      _Batteries(
        c: c,
        add: _add,
        options: _options,
        open: (id) =>
            id == c.selected && (c.live || c.busy) ? _switch(0) : _connect(id),
      ),
      _History(c: c, filter: filter, changeFilter: _filter),
    ];
    return PopScope(
      canPop: tab == 0 || (tab == 1 && c.selected.isEmpty),
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _switch(0);
      },
      child: Scaffold(
        body: SafeArea(
          bottom: false,
          child: Align(
            alignment: Alignment.topCenter,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 700),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Padding(
                    padding: EdgeInsets.fromLTRB(20, 12, 20, 8),
                    child: _Brand(),
                  ),
                  Expanded(
                    child: AnimatedSwitcher(
                      duration: MediaQuery.disableAnimationsOf(context)
                          ? Duration.zero
                          : const Duration(milliseconds: 160),
                      child: KeyedSubtree(
                        key: ValueKey(tab),
                        child: pages[tab],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        bottomNavigationBar: DecoratedBox(
          decoration: const BoxDecoration(
            border: Border(top: BorderSide(color: _border)),
          ),
          child: NavigationBar(
            selectedIndex: tab,
            onDestinationSelected: _switch,
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.speed_outlined),
                selectedIcon: Icon(Icons.speed, color: _accent),
                label: 'Painel',
              ),
              NavigationDestination(
                icon: Icon(Icons.battery_charging_full_outlined),
                selectedIcon: Icon(Icons.battery_charging_full, color: _accent),
                label: 'Baterias',
              ),
              NavigationDestination(
                icon: Icon(Icons.history),
                selectedIcon: Icon(Icons.history, color: _accent),
                label: 'Histórico',
              ),
            ],
          ),
        ),
      ),
    );
  }
}

enum _BatteryAction { rename, disconnect, remove, history }

Future<T?> _choose<T>(BuildContext context, String title, List<Widget> items) =>
    showModalBottomSheet<T>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * .78,
          ),
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
            children: [
              Text(title, style: _title),
              _gap,
              ...items,
            ],
          ),
        ),
      ),
    );

class _Option<T> extends StatelessWidget {
  const _Option(
    this.value,
    this.icon,
    this.label, {
    this.subtitle,
    this.selected = false,
  });
  final T value;
  final IconData icon;
  final String label;
  final String? subtitle;
  final bool selected;
  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    leading: Icon(icon, color: _accent),
    title: Text(label),
    subtitle: subtitle == null ? null : Text(subtitle!),
    trailing: Icon(
      selected ? Icons.check : Icons.chevron_right,
      color: selected ? _accent : _muted,
    ),
    onTap: () => Navigator.of(context).pop(value),
  );
}

class _Rename extends StatefulWidget {
  const _Rename(this.name);
  final String name;
  @override
  State<_Rename> createState() => _RenameState();
}

class _RenameState extends State<_Rename> {
  late final input = TextEditingController(text: widget.name);
  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  void save() {
    final name = input.text.trim();
    Navigator.pop(context, name.isEmpty ? 'Minha bateria' : name);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    scrollable: true,
    title: const Text('Editar apelido'),
    content: TextField(
      autofocus: true,
      controller: input,
      maxLength: 40,
      decoration: const InputDecoration(labelText: 'Nome da bateria'),
      onSubmitted: (_) => save(),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(onPressed: save, child: const Text('Salvar')),
    ],
  );
}
