package br.com.memotos.bateria

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "br.com.memotos.bateria/legacy")
            .setMethodCallHandler { call, result ->
                if (call.method == "read") {
                    result.success(mapOf(
                        "batteries" to getSharedPreferences("saved_batteries", MODE_PRIVATE).all,
                        "sessions" to getSharedPreferences("charge_history", MODE_PRIVATE).getString("sessions", "[]")
                    ))
                } else result.notImplemented()
            }
    }
}
