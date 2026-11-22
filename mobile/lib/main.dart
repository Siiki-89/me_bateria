import 'dart:async';

import 'package:material_ui/material_ui.dart';

import 'controller.dart';
import 'screens.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const _Bootstrap());
}

class _Bootstrap extends StatefulWidget {
  const _Bootstrap();
  @override
  State<_Bootstrap> createState() => _BootstrapState();
}

class _BootstrapState extends State<_Bootstrap> with WidgetsBindingObserver {
  final controller = BatteryController();
  late Future<void> loading;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    loading = controller.initialize();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.detached) {
      unawaited(controller.disconnect());
    } else if (state == AppLifecycleState.paused) {
      unawaited(controller.flush());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<void>(
    future: loading,
    builder: (context, snapshot) {
      if (snapshot.connectionState == ConnectionState.done &&
          !snapshot.hasError) {
        return BatteryApp(controller: controller);
      }
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: ThemeData.dark(),
        home: Scaffold(
          backgroundColor: const Color(0xff0b1218),
          body: Center(
            child: snapshot.hasError
                ? Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text(
                          'Não foi possível abrir os dados salvos neste celular.',
                          textAlign: TextAlign.center,
                        ),
                        TextButton(
                          onPressed: () => setState(() {
                            loading = controller.initialize();
                          }),
                          child: const Text('Tentar novamente'),
                        ),
                      ],
                    ),
                  )
                : const CircularProgressIndicator(color: Color(0xff51d3d3)),
          ),
        ),
      );
    },
  );
}
