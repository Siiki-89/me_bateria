package br.com.memotos.bateria;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final UUID SERVICE = uuid("ff00");
    private static final UUID RX = uuid("ff01");
    private static final UUID TX = uuid("ff02");
    private static final UUID CCC = uuid("2902");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Ui ui;
    private BatteryFlows flows;
    private BatteryInsights insights;
    private Dashboard dashboard;
    private ScrollView scroll;
    private LinearLayout homePanel, devicesPanel, deviceList, detailsPanel, historyPanel, historyList, visiblePanel;
    private final List<TextView> tabs = new ArrayList<>();
    private TextView detailConnection, historyFilterButton;
    private String selected = "", pendingName = "", explanation = "", linkState = "Desconectada";
    private boolean live, busy;
    private byte[] pendingBasic;
    private BatteryReading last;
    private long lastSavedAt;
    private int lastSavedPercent = -1;
    private Camera camera;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private Jbd.Reader reader;
    private Jbd.Basic basic;
    private int expected;
    private String target;
    private final ChargeSession chargeSession = new ChargeSession();
    private final Runnable timeout = () -> fail(expected == 0
            ? "Tempo esgotado na busca ou conexão Bluetooth."
            : "Bluetooth conectado, mas o BMS não respondeu à consulta " + expected + ".");
    private final Runnable refresh = () -> { if (gatt != null) send(3); };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getSplashScreen().setOnExitAnimationListener(splash -> {
            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) { splash.remove(); return; }
            splash.animate().alpha(0f).setDuration(170).withEndAction(splash::remove).start();
        });
        ui = new Ui(this);
        flows = new BatteryFlows(this, ui);
        insights = new BatteryInsights(this, ui);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        var root = ui.column();
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            var edges = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom);
            return insets;
        });
        setContentView(root);
        getWindow().getInsetsController().setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        addBrand(root);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        var content = ui.column();
        content.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24));
        scroll.addView(content);
        dashboard = new Dashboard(this,ui,() -> showPanel(devicesPanel),
                () -> showBatteryOptions(selected),() -> { if (selected.isEmpty()) addBattery(); else connect(); },
                () -> showPanel(detailsPanel));
        homePanel = dashboard.view;
        devicesPanel = ui.column();
        detailsPanel = ui.column();
        historyPanel = ui.column();
        for (var page : List.of(homePanel, devicesPanel, detailsPanel, historyPanel)) content.addView(page);
        devicesPanel.addView(ui.text("Minhas baterias",32,Ui.INK,true));
        ui.gap(devicesPanel,16);
        deviceList = ui.column();
        devicesPanel.addView(deviceList);
        var detailHeader = ui.row();
        detailHeader.addView(ui.iconButton("back","Voltar ao painel",() -> showPanel(homePanel)));
        ui.gap(detailHeader,12);
        detailHeader.addView(ui.text("Detalhes da bateria",24,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        detailsPanel.addView(detailHeader);
        ui.gap(detailsPanel,12);
        detailConnection=ui.text("",14,Ui.MUTED,false);
        detailsPanel.addView(detailConnection);
        ui.gap(detailsPanel,16);
        detailsPanel.addView(insights.detailsView());
        historyPanel.addView(ui.text("Histórico de recargas",30,Ui.INK,true));
        ui.gap(historyPanel,12);
        historyFilterButton=ui.action("",false,this::filterHistory);
        historyPanel.addView(historyFilterButton);
        ui.gap(historyPanel,16);
        historyList=ui.column();
        historyPanel.addView(historyList);
        var rule = new View(this);
        rule.setBackgroundColor(Ui.BORDER);
        root.addView(rule,-1,ui.dp(1));
        var nav=ui.row();
        ui.pad(nav,12,6);
        root.addView(nav);
        tab(nav,"Painel","panel",() -> showPanel(homePanel));
        tab(nav,"Baterias","battery",() -> showPanel(devicesPanel));
        tab(nav,"Histórico","history",this::showChargeHistory);
        selectBattery(batteries().getString("selected",""));
        showPanel(selected.isEmpty()?devicesPanel:homePanel);
    }

    private void addBrand(LinearLayout root) {
        var row=ui.row();
        row.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(4));
        try (var asset=getAssets().open("me-logo.png")) {
            Bitmap original=BitmapFactory.decodeStream(asset);
            var logo=new ImageView(this);
            logo.setImageBitmap(Bitmap.createBitmap(original,94,163,269,269));
            logo.setBackground(ui.shape(Ui.SURFACE,24));
            logo.setClipToOutline(true);
            logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(logo,ui.dp(30),ui.dp(30));
            ui.gap(row,8);
        } catch (java.io.IOException ignored) {}
        row.addView(ui.text("ME Bateria",17,Ui.INK,true));
        root.addView(row);
    }

    private void tab(LinearLayout nav,String title,String icon,Runnable action) {
        var t=ui.text(title,13,Ui.MUTED,true);
        t.setTag(icon);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(ui.dp(52));
        t.setCompoundDrawablePadding(ui.dp(3));
        ui.pad(t,4,4);
        t.setFocusable(true);
        t.setOnClickListener(v -> action.run());
        nav.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        tabs.add(t);
    }

    private void showPanel(LinearLayout page) {
        boolean changed=visiblePanel!=page;
        visiblePanel=page;
        for (var p : List.of(homePanel,devicesPanel,historyPanel,detailsPanel))
            p.setVisibility(p==page?View.VISIBLE:View.GONE);
        if (page==devicesPanel) renderDevices();
        if (page==detailsPanel) updateDetails();
        if (page==historyPanel) renderHistory();
        int active=page==devicesPanel?1:page==historyPanel?2:0;
        for (int i=0;i<tabs.size();i++) {
            var t=tabs.get(i);
            boolean on=i==active;
            t.setSelected(on);
            int color=on?Ui.ACCENT:Ui.MUTED;
            t.setTextColor(color);
            t.setBackground(ui.ripple(on?0xff172d33:Ui.BG,16));
            t.setCompoundDrawablesWithIntrinsicBounds(null,ui.icon((String)t.getTag(),color),null,null);
        }
        if(changed) { scroll.scrollTo(0,0); ui.enter(page); }
    }

    private android.content.SharedPreferences batteries() {
        return getSharedPreferences("saved_batteries",MODE_PRIVATE);
    }

    private String alias(String id) { return batteries().getString(id,"Minha bateria"); }

    private void selectBattery(String id) {
        if(!selected.equals(id)) {
            if(busy||live) fail("");
            selected=id;
            last=BatteryReading.load(batteries(),id);
            lastSavedAt=last==null?0:last.time;
            lastSavedPercent=last==null?-1:last.basic.percent();
            linkState="Desconectada";
            explanation="";
        }
        dashboard.identity(id.isEmpty()?"Sua bateria":alias(id),batteries().contains(id));
        updateDashboard();
    }

    private String stamp(long time) {
        return android.text.format.DateUtils.formatDateTime(this,time,
                android.text.format.DateUtils.FORMAT_SHOW_DATE | android.text.format.DateUtils.FORMAT_SHOW_TIME |
                android.text.format.DateUtils.FORMAT_ABBREV_MONTH);
    }

    private String observation() {
        if(!explanation.isEmpty()) return explanation+(last==null?"":"\nÚltima leitura · "+stamp(last.time));
        if(last!=null) return "Última leitura · "+stamp(last.time);
        if(selected.isEmpty()) return "Adicione uma bateria para acompanhar a carga.";
        if(batteries().contains("last_percent_"+selected)) return "Porcentagem salva · horário indisponível";
        return "Aguardando a primeira leitura.";
    }

    private void updateDashboard() {
        int percent=last==null?batteries().getInt("last_percent_"+selected,-1):last.basic.percent();
        dashboard.update(last==null?null:last.basic,percent,live,busy,!selected.isEmpty(),linkState,observation());
        if(visiblePanel==detailsPanel) updateDetails();
    }

    private void updateDetails() {
        detailConnection.setText(alias(selected)+" · "+linkState+"\n"+observation());
        insights.updateDetails(last==null?null:last.basic,last==null?null:last.groups);
    }

    private void connectionState(String label,String note,boolean connecting) {
        linkState=label; explanation=note; busy=connecting;
        updateDashboard();
        if(visiblePanel==devicesPanel) renderDevices();
    }

    private void renderDevices() {
        deviceList.removeAllViews();
        var ids=batteries().getAll().keySet().stream().filter(MainActivity::validAddress).sorted().toArray(String[]::new);
        if(ids.length==0) { flows.empty(deviceList,this::addBattery);return; }
        for (String id : ids) {
            boolean active=selected.equals(id);
            boolean current=active && live;
            flows.card(deviceList,id,alias(id),batteries().getString("ble_name_"+id,""),active,current,
                    active && busy,active?linkState:"Desconectada",
                    active && last!=null?last.basic.percent():batteries().getInt("last_percent_"+id,-1),
                    active && last!=null?last.time:batteries().getLong("last_at_"+id,0),
                    () -> { if(active && (live||busy)) showPanel(homePanel); else { selectBattery(id); connect(); } },
                    () -> showBatteryOptions(id));
        }
        flows.addButton(deviceList,this::addBattery);
    }

    private void addBattery() { flows.choose(this::openCamera,this::manualBattery); }

    private void manualBattery() {
        flows.manual(MainActivity::validAddress,id -> connectTo(id));
    }

    private void connectTo(String id) {
        String normalized=id.trim().toUpperCase(Locale.ROOT);
        if(!normalized.equals(selected) || !(busy || live)) { selectBattery(normalized);connect(); }
        else showPanel(homePanel);
    }

    private void showBatteryOptions(String id) {
        if (!batteries().contains(id)) return;
        flows.options(alias(id),id,batteries().getString("ble_name_"+id,""),
                () -> flows.rename(alias(id),name -> {
                    batteries().edit().putString(id,name.trim().isEmpty()?"Minha bateria":name.trim()).apply();
                    dashboard.identity(selected.isEmpty()?"Sua bateria":alias(selected),batteries().contains(selected));
                    if(visiblePanel==devicesPanel) renderDevices();
                }),
                id.equals(selected) && (busy || live)?() -> fail("Desconectada pelo usuário."):null,
                () -> removeBattery(id));
    }

    private void removeBattery(String id) {
        var saved = batteries();
        if (!saved.contains(id)) return;
        if (id.equals(selected)) selectBattery("");
        if (id.equals(target)) target = null;
        var edit = saved.edit().remove(id).remove("ble_name_" + id)
                .remove("last_percent_" + id).remove("last_at_" + id).remove("reading_" + id);
        if (id.equals(saved.getString("selected", ""))) edit.remove("selected");
        edit.apply();
        showPanel(devicesPanel);
        Toast.makeText(this, "Bateria removida", Toast.LENGTH_SHORT).show();
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }
        SurfaceView preview = new SurfaceView(this);
        android.app.Dialog dialog = flows.camera(preview,this::manualBattery);
        dialog.setOnDismissListener(d -> releaseCamera());
        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                try {
                    camera = Camera.open();
                    camera.setDisplayOrientation(90);
                    var parameters = camera.getParameters();
                    if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        camera.setParameters(parameters);
                    }
                    var size=parameters.getPreviewSize();
                    flows.previewAspect(preview,size.height,size.width);
                    camera.setPreviewDisplay(holder);
                    camera.startPreview();
                    scanFrame(dialog, camera);
                } catch (Exception error) {
                    dialog.dismiss();
                    flows.scanError("A câmera está indisponível. Você também pode usar o identificador da etiqueta.",MainActivity.this::openCamera,MainActivity.this::manualBattery);
                }
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override public void surfaceDestroyed(SurfaceHolder holder) { releaseCamera(); }
        });
        dialog.show();
        dialog.getWindow().setLayout(-1, -1);
    }

    private void scanFrame(android.app.Dialog dialog, Camera source) {
        if (camera != source || !dialog.isShowing()) return;
        source.setOneShotPreviewCallback((data, active) -> {
            var size = active.getParameters().getPreviewSize();
            try {
                var image = new PlanarYUVLuminanceSource(data, size.width, size.height,
                        0, 0, size.width, size.height, false);
                var result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(image)),
                        Map.of(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE)));
                dialog.dismiss();
                if (validAddress(result.getText().trim())) {
                    connectTo(result.getText());
                } else {
                    flows.scanError("Este QR não identifica a bateria. Procure o segundo código da etiqueta, marcado como Second step.",this::openCamera,this::manualBattery);
                }
            } catch (NotFoundException ignored) {
                handler.postDelayed(() -> scanFrame(dialog, source), 150);
            }
        });
    }

    private void releaseCamera() {
        if (camera != null) {
            Camera active = camera;
            camera = null;
            active.release();
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        for (int result : results) if (result != PackageManager.PERMISSION_GRANTED) {
            if(request==1) flows.scanError("A câmera precisa de permissão para ler a etiqueta.",this::openCamera,this::manualBattery);
            else connectionState("Sem permissão","Permita o acesso a dispositivos próximos nos ajustes do Android.",false);
            return;
        }
        if (results.length == 0) return;
        if (request == 1) openCamera();
        if (request == 2) connect();
    }

    private static boolean validAddress(String value) {
        return value.toUpperCase(Locale.ROOT).matches("([0-9A-F]{2}:){5}[0-9A-F]{2}");
    }

    private void connect() {
        showPanel(homePanel);
        fail("");
        target = selected;
        pendingName = "";
        if (!validAddress(target)) {
            connectionState("Sem identificação","Escaneie o segundo QR ou informe o identificador completo.",false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }
        var adapter = getSystemService(BluetoothManager.class).getAdapter();
        if (adapter == null || !adapter.isEnabled() || (scanner = adapter.getBluetoothLeScanner()) == null) {
            connectionState("Bluetooth desligado","Ative o Bluetooth no celular e toque em conectar.",false);
            return;
        }
        connectionState("Procurando","Buscando a bateria por Bluetooth…",true);
        scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scan);
        armTimeout();
    }

    private final ScanCallback scan = new ScanCallback() {
        @Override public void onScanFailed(int code) { handler.post(() -> { if (scanner != null) fail("Busca Bluetooth falhou (" + code + ")."); }); }
        @Override public void onScanResult(int type, ScanResult result) {
            handler.post(() -> {
                if (scanner == null || !matches(result)) return;
                stopScan();
                pendingName = result.getDevice().getName();
                if (pendingName == null && result.getScanRecord() != null)
                    pendingName = result.getScanRecord().getDeviceName();
                connectionState("Conectando","Bateria encontrada. Abrindo a conexão…",true);
                gatt = result.getDevice().connectGatt(MainActivity.this, false, callbacks, BluetoothDevice.TRANSPORT_LE);
                if (gatt == null) fail("Não foi possível iniciar a conexão.");
                else armTimeout();
            });
        }
    };

    private boolean matches(ScanResult result) {
        if (target.equalsIgnoreCase(result.getDevice().getAddress())) return true;
        if (result.getScanRecord() == null) return false;
        var manufacturer = result.getScanRecord().getManufacturerSpecificData();
        for (int i = 0; i < manufacturer.size(); i++) {
            byte[] bytes = manufacturer.valueAt(i);
            if (bytes.length < 6) continue;
            StringJoiner prefix = new StringJoiner(":");
            for (int j = 0; j < 6; j++) prefix.add(String.format(Locale.ROOT, "%02X", bytes[j] & 255));
            if (target.equals(prefix.toString())) return true;
        }
        return false;
    }

    private final BluetoothGattCallback callbacks = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt link, int result, int next) {
            handler.post(() -> {
                if (gatt != link) return;
                if (result != BluetoothGatt.GATT_SUCCESS || next == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("Conexão encerrada (" + result + ").");
                } else if (next == BluetoothProfile.STATE_CONNECTED && !link.discoverServices()) {
                    fail("Falha ao consultar serviços Bluetooth.");
                }
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt link, int result) {
            handler.post(() -> {
                if (gatt != link) return;
                var service = link.getService(SERVICE);
                var rx = service == null ? null : service.getCharacteristic(RX);
                tx = service == null ? null : service.getCharacteristic(TX);
                var ccc = rx == null ? null : rx.getDescriptor(CCC);
                if (result != BluetoothGatt.GATT_SUCCESS || tx == null || ccc == null
                        || !link.setCharacteristicNotification(rx, true)) {
                    fail("Serviço de telemetria JBD indisponível.");
                    return;
                }
                byte[] enable = (rx.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                ccc.setValue(enable);
                if (!link.writeDescriptor(ccc)) fail("Falha ao habilitar respostas do BMS.");
            });
        }

        @Override public void onDescriptorWrite(BluetoothGatt link, BluetoothGattDescriptor descriptor, int result) {
            handler.post(() -> {
                if (gatt != link) return;
                if (result == BluetoothGatt.GATT_SUCCESS) {
                    connectionState("Aguardando BMS","Bluetooth conectado. Aguardando a primeira medição…",true);
                    handler.postDelayed(() -> { if (gatt == link) send(3); }, 500);
                }
                else fail("O BMS não habilitou as respostas Bluetooth.");
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt link, BluetoothGattCharacteristic item, int result) {
            handler.post(() -> { if (gatt == link && result != BluetoothGatt.GATT_SUCCESS) fail("A consulta Bluetooth falhou."); });
        }

        @Override public void onCharacteristicChanged(BluetoothGatt link, BluetoothGattCharacteristic item) {
            receive(link, item, item.getValue());
        }

        @Override public void onCharacteristicChanged(BluetoothGatt link, BluetoothGattCharacteristic item, byte[] bytes) {
            receive(link, item, bytes);
        }
    };

    private void send(int command) {
        expected = command;
        reader = new Jbd.Reader();
        int properties = tx.getProperties();
        tx.setWriteType((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        android.util.Log.i("MEBateria", "Consulta " + command + ", propriedades TX " + properties
                + ", modo " + tx.getWriteType());
        tx.setValue(Jbd.request(command));
        if (gatt.writeCharacteristic(tx)) armTimeout();
        else fail("Não foi possível enviar a consulta ao BMS.");
    }

    private void receive(BluetoothGatt link, BluetoothGattCharacteristic item, byte[] bytes) {
        if (!RX.equals(item.getUuid()) || bytes == null) return;
        byte[] chunk = bytes.clone();
        handler.post(() -> {
            if (gatt != link || expected == 0) return;
            android.util.Log.i("MEBateria", "Fragmento recebido: " + chunk.length + " bytes");
            try {
                byte[] frame = reader.accept(chunk);
                if (frame == null) return;
                if (frame[1] != expected) throw new IllegalArgumentException("Resposta inesperada do BMS.");
                if (expected == 3) {
                    basic = Jbd.basic(frame);
                    pendingBasic = frame;
                    send(4);
                } else {
                    int[] groups = Jbd.cells(frame);
                    expected = 0;
                    handler.removeCallbacks(timeout);
                    showReadings(groups, frame);
                    handler.postDelayed(refresh, 2500);
                }
            } catch (IllegalArgumentException error) {
                fail(error.getMessage());
            }
        });
    }

    private void showReadings(int[] groups,byte[] cellFrame) {
        boolean first=!live;
        if (first) {
            var prefs=batteries();
            var edit=prefs.edit().putString(target,alias(target)).putString("selected",target);
            if(pendingName!=null && !pendingName.trim().isEmpty()) edit.putString("ble_name_"+target,pendingName);
            edit.apply();
            dashboard.identity(alias(target),true);
        }
        long now=System.currentTimeMillis();
        last=new BatteryReading(basic,groups,now,pendingBasic,cellFrame);
        boolean cache=first || lastSavedPercent!=basic.percent() || Math.abs(now-lastSavedAt)>=60_000;
        if(cache) saveLatest();
        saveCharge(chargeSession.sample(android.os.SystemClock.elapsedRealtime(),basic.voltage(),basic.current()));
        live=true;
        busy=false;
        explanation="";
        linkState="Conectada";
        updateDashboard();
        if(cache && visiblePanel==devicesPanel) renderDevices();
    }

    private void saveLatest() {
        if(last==null || selected.isEmpty() || last.time==lastSavedAt) return;
        last.save(batteries(),selected);
        lastSavedAt=last.time;
        lastSavedPercent=last.basic.percent();
    }

    private JSONArray chargeHistory() {
        String stored = getSharedPreferences("charge_history", MODE_PRIVATE).getString("sessions", "[]");
        try {
            return new JSONArray(stored);
        } catch (JSONException error) {
            throw new IllegalStateException("Histórico local inválido", error);
        }
    }

    private void saveCharge(ChargeSession.Summary summary) {
        if (summary == null) return;
        try {
            long end = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtime() - summary.end());
            var entry = new JSONObject()
                    .put("address", target)
                    .put("end", end)
                    .put("duration", summary.durationMs())
                    .put("peak", summary.peakW())
                    .put("energy", summary.energyWh())
                    .put("interrupted", summary.interrupted());
            var previous = chargeHistory();
            var updated = new JSONArray().put(entry);
            for (int i = 0; i < Math.min(previous.length(), 49); i++) updated.put(previous.getJSONObject(i));
            getSharedPreferences("charge_history", MODE_PRIVATE).edit().putString("sessions", updated.toString()).apply();
            if (historyPanel.getVisibility() == View.VISIBLE) renderHistory();
        } catch (JSONException | IllegalStateException error) {
            android.util.Log.e("MEBateria", "Falha ao salvar o histórico", error);
            Toast.makeText(this, "Não foi possível salvar a sessão.", Toast.LENGTH_LONG).show();
        }
    }

    private String historyFilter="";

    private void showChargeHistory() {
        if(visiblePanel!=historyPanel) historyFilter=selected;
        showPanel(historyPanel);
    }

    private void filterHistory() {
        var ids=new LinkedHashSet<String>();
        ids.add("");
        if(validAddress(selected)) ids.add(selected);
        batteries().getAll().keySet().stream().filter(MainActivity::validAddress).sorted().forEach(ids::add);
        try {
            var history=chargeHistory();
            for(int i=0;i<history.length();i++) ids.add(history.getJSONObject(i).getString("address"));
        } catch(JSONException|IllegalStateException error) {
            Toast.makeText(this,"Não foi possível listar as baterias do histórico.",Toast.LENGTH_LONG).show();
            return;
        }
        String[] filters=ids.toArray(new String[0]);
        flows.filter(filters, historyFilter, this::historyName, id -> {
            historyFilter=id;
            renderHistory();
        });
    }

    private void renderHistory() {
        historyFilterButton.setText("Filtrar · "+(historyFilter.isEmpty()?"Todas as baterias":historyName(historyFilter)));
        historyFilterButton.setContentDescription("Filtrar por bateria. "+historyFilterButton.getText());
        try { insights.renderHistory(historyList,chargeHistory(),historyFilter,this::historyName); }
        catch(IllegalStateException error) { insights.showHistoryError(historyList); }
    }

    private String historyName(String id) { return batteries().getString(id, id); }

    private void armTimeout() {
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 15000);
    }

    private void stopScan() {
        if (scanner != null) {
            scanner.stopScan(scan);
            scanner = null;
        }
    }

    private void fail(String message) {
        saveCharge(chargeSession.interrupt());
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(refresh);
        stopScan();
        if (gatt != null) {
            BluetoothGatt old = gatt;
            gatt = null;
            old.disconnect();
            old.close();
        }
        expected = 0;
        if(live) saveLatest();
        live=false;
        boolean idle=message.isEmpty() || message.equals("Desconectada pelo usuário.");
        connectionState(idle?"Desconectada":"Sem conexão",message,false);
    }

    @Override public void onBackPressed() {
        if(visiblePanel==detailsPanel) showPanel(homePanel);
        else if(visiblePanel!=homePanel && !selected.isEmpty()) showPanel(homePanel);
        else if(selected.isEmpty() && visiblePanel!=devicesPanel) showPanel(devicesPanel);
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        super.onPause();
        if(live) saveLatest();
    }

    @Override protected void onDestroy() {
        releaseCamera();
        fail("");
        super.onDestroy();
    }

    private static UUID uuid(String shortId) {
        return UUID.fromString("0000" + shortId + "-0000-1000-8000-00805f9b34fb");
    }
}
