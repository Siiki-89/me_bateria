package br.com.memotos.bateria;

import android.content.SharedPreferences;
import android.util.Base64;
import org.json.*;

final class BatteryReading {
    final Jbd.Basic basic;
    final int[] groups;
    final long time;
    private final byte[] stateFrame, cellFrame;

    BatteryReading(Jbd.Basic basic,int[] groups,long time,byte[] stateFrame,byte[] cellFrame) {
        this.basic=basic;
        this.groups=groups;
        this.time=time;
        this.stateFrame=stateFrame;
        this.cellFrame=cellFrame;
    }
    void save(SharedPreferences prefs,String id) {
        try {
            JSONObject json=new JSONObject().put("time",time)
                .put("state",Base64.encodeToString(stateFrame,Base64.NO_WRAP))
                .put("cells",Base64.encodeToString(cellFrame,Base64.NO_WRAP));
            prefs.edit().putInt("last_percent_"+id,basic.percent()).putLong("last_at_"+id,time)
                .putString("reading_"+id,json.toString()).apply();
        } catch(JSONException error) {
            android.util.Log.w("MEBateria","Falha ao gravar a última leitura",error);
        }
    }
    static BatteryReading load(SharedPreferences prefs,String id) {
        String value=prefs.getString("reading_"+id,null);
        if(value==null) return null;
        try {
            JSONObject json=new JSONObject(value);
            byte[] state=new Jbd.Reader().accept(Base64.decode(json.getString("state"),Base64.DEFAULT));
            byte[] cells=new Jbd.Reader().accept(Base64.decode(json.getString("cells"),Base64.DEFAULT));
            if(state==null||cells==null) return null;
            return new BatteryReading(Jbd.basic(state),Jbd.cells(cells),json.getLong("time"),state,cells);
        } catch(JSONException|RuntimeException error) {
            android.util.Log.w("MEBateria","Última leitura indisponível",error);
            return null;
        }
    }
}
