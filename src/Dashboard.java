package br.com.memotos.bateria;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

final class Dashboard {
    final LinearLayout view;
    private final Ui ui;
    private final TextView name, state, timestamp, metricTitle, volts, amps, watts, remaining, nominal, primary;
    private final View options;
    private final LinearLayout titles;
    private final EnergyCore core;
    private final String unavailable = "—";
    private final Locale pt=Locale.forLanguageTag("pt-BR");

    Dashboard(Context context, Ui ui, Runnable change, Runnable menu, Runnable connect, Runnable details) {
        this.ui=ui;
        view=ui.column();
        var head=ui.row();
        view.addView(head);
        titles=ui.column();
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(ui.text("Painel da bateria",14,Ui.MUTED,false));
        ui.gap(titles,4);
        name=ui.text("",30,Ui.INK,true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        var picker=ui.row();
        titles.addView(picker);
        picker.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        var switchMark=new ImageView(context);
        switchMark.setImageDrawable(ui.icon("chevron",Ui.MUTED));
        switchMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        picker.addView(switchMark,ui.dp(20),ui.dp(20));
        titles.setMinimumHeight(ui.dp(52));
        titles.setBackground(ui.ripple(Ui.BG,8));
        titles.setFocusable(true);
        titles.setOnClickListener(v->change.run());
        options=ui.iconButton("more","Opções da bateria",menu);
        head.addView(options);
        ui.gap(view,16);

        var instrument=ui.panel();
        instrument.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));
        var finish=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff193138,0xff13232c,0xff12202a});
        finish.setCornerRadius(ui.dp(24));
        finish.setStroke(ui.dp(1),Ui.BORDER);
        instrument.setBackground(finish);
        view.addView(instrument);
        var top=ui.row();
        instrument.addView(top);
        top.addView(ui.text("Carga da bateria",15,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        state=ui.text("",13,Ui.MUTED,true);
        ui.pad(state,10,5);
        top.addView(state);
        core=new EnergyCore(context);
        instrument.addView(core,new LinearLayout.LayoutParams(-1,-2));
        divider(instrument);
        ui.gap(instrument,12);
        timestamp=ui.text("",14,Ui.MUTED,false);
        instrument.addView(timestamp);
        ui.gap(view,12);
        primary=ui.action("",true,connect);
        view.addView(primary);
        ui.gap(view,14);

        var quick=ui.panel();
        view.addView(quick);
        metricTitle=ui.text("Medições elétricas",18,Ui.INK,true);
        var metricHead=ui.row();
        quick.addView(metricHead);
        metricHead.addView(metricTitle,new LinearLayout.LayoutParams(0,-2,1));
        var more=ui.text("Detalhes",14,Ui.ACCENT,true);
        more.setCompoundDrawablesWithIntrinsicBounds(null,null,ui.icon("chevron",Ui.ACCENT),null);
        more.setContentDescription("Ver detalhes da bateria");
        more.setGravity(Gravity.CENTER);
        more.setMinHeight(ui.dp(48));
        ui.pad(more,6,2);
        more.setBackground(ui.ripple(Ui.SURFACE,12));
        more.setFocusable(true);
        more.setOnClickListener(v->details.run());
        metricHead.addView(more);
        ui.gap(quick,8);
        boolean stacked=context.getResources().getConfiguration().screenWidthDp /
                context.getResources().getConfiguration().fontScale < 340;
        var metrics=stacked ? ui.column() : ui.row();
        quick.addView(metrics);
        volts=metric(metrics,"Tensão",stacked);
        amps=metric(metrics,"Corrente",stacked);
        watts=metric(metrics,"Potência",stacked);
        ui.gap(view,12);
        var capacities=ui.panel();
        view.addView(capacities);
        capacities.addView(ui.text("Capacidade",18,Ui.INK,true));
        ui.gap(capacities,12);
        remaining=lineItem(capacities,"Restante");
        ui.gap(capacities,8);
        nominal=lineItem(capacities,"Nominal");
    }
    private void divider(LinearLayout box) {
        var rule=new View(box.getContext()); rule.setBackgroundColor(Ui.BORDER);
        box.addView(rule,new LinearLayout.LayoutParams(-1,ui.dp(1)));
    }
    private TextView metric(LinearLayout group,String label,boolean stacked) {
        if(stacked) { var value=lineItem(group,label);ui.gap(group,6);return value; }
        var item=ui.column();
        group.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        item.addView(ui.text(label,13,Ui.MUTED,false));
        ui.gap(item,4);
        var value=ui.text("",19,Ui.INK,true);
        item.addView(value);
        return value;
    }
    private TextView lineItem(LinearLayout parent,String title) {
        var line=ui.row(); parent.addView(line);
        line.addView(ui.text(title,15,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        var value=ui.text("",17,Ui.INK,true);line.addView(value);
        return value;
    }
    void identity(String title,boolean saved) {
        name.setText(title);
        titles.setContentDescription(title+". Trocar de bateria");
        options.setVisibility(saved?View.VISIBLE:View.GONE);
    }
    void update(Jbd.Basic sample,int percent,boolean live,boolean busy,boolean hasId,String connection,String observation) {
        state.setText(connection);
        state.setTextColor(live?Ui.MINT:busy?Ui.ACCENT:Ui.MUTED);
        state.setBackground(ui.shape(live?0xff1b403d:Ui.ELEVATED,12));
        timestamp.setText(observation);
        metricTitle.setText(!live && sample!=null?"Últimas medições salvas":"Medições elétricas");
        core.setReading(percent,live,sample==null?0:sample.current());
        volts.setText(sample==null?unavailable:String.format(pt,"%.2f V",sample.voltage()));
        amps.setText(sample==null?unavailable:String.format(pt,"%.2f A",sample.current()));
        watts.setText(sample==null?unavailable:String.format(pt,"%.0f W",Math.abs(sample.voltage()*sample.current())));
        remaining.setText(sample==null?unavailable:String.format(pt,"%.2f Ah",sample.remaining()));
        nominal.setText(sample==null?unavailable:String.format(pt,"%.2f Ah",sample.nominal()));
        primary.setVisibility(live?View.GONE:View.VISIBLE);
        primary.setEnabled(!busy);
        primary.setAlpha(busy?.65f:1);
        primary.setText(busy?"Conexão em andamento…":hasId?"Conectar à bateria":"Adicionar bateria");
    }
}
