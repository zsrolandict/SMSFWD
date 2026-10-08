package hu.smsfwd.app;

import android.Manifest;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(name = "SmsForwarder", permissions = {
    @Permission(alias = "sms", strings = { Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS })
})
public class SmsForwarderPlugin extends Plugin {
    @PluginMethod public void configure(PluginCall call) {
        JSArray rules = call.getArray("rules", new JSArray());
        int limit = call.getInt("dailyLimit", 20);
        if (limit < 1 || limit > 1000) { call.reject("Érvénytelen SMS-limit."); return; }
        boolean active = Boolean.TRUE.equals(call.getBoolean("active", false)) && getPermissionState("sms") == PermissionState.GRANTED;
        SmsStore.preferences(getContext()).edit().putString("rules", rules.toString()).putBoolean("active", active).putInt("limit", limit).commit();
        call.resolve();
    }
    @PluginMethod public void getState(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", getPermissionState("sms") == PermissionState.GRANTED);
        result.put("history", SmsStore.history(getContext()));
        result.put("email",EmailCredentials.metadata(getContext()));
        // Recover persisted mail records if a receiver was interrupted before scheduling.
        org.json.JSONArray all=SmsStore.allHistory(getContext());
        for(int i=0;i<all.length();i++){org.json.JSONObject item=all.optJSONObject(i);if(item!=null && item.optString("channel").equals("email") && item.optString("status").equals("pending"))EmailWorker.enqueue(getContext(),item.optString("id"));}
        call.resolve(result);
    }
    @PluginMethod public void saveEmail(PluginCall call) {
        try {
            EmailConfig old=EmailCredentials.load(getContext());
            String host=call.getString("host","").trim(),address=call.getString("address","").trim();
            String password=call.getString("password","");
            if(password.isEmpty() && old!=null) {
                if(!old.host.equals(host) || !old.address.equals(address)){call.reject("Másik fiókhoz vagy kiszolgálóhoz add meg újra az alkalmazásjelszót.");return;}
                password=old.password;
            }
            if(host.equalsIgnoreCase("smtp.gmail.com"))password=password.replaceAll("\\s","");
            EmailConfig config=new EmailConfig(host,call.getInt("port",587),address,password);
            EmailCredentials.save(getContext(),config);call.resolve();
        }catch(Exception e){call.reject(e instanceof IllegalArgumentException ? e.getMessage() : "Nem sikerült menteni a postafiókot. Állítsd be újra.");}
    }
    @PluginMethod public void queueTestEmail(PluginCall call) {
        try {
            if(EmailCredentials.load(getContext())==null){call.reject("Előbb mentsd a küldő postafiókot.");return;}
            String target=call.getString("target","");new javax.mail.internet.InternetAddress(target,true);
            String id=java.util.UUID.randomUUID().toString();
            org.json.JSONObject item=new org.json.JSONObject().put("id",id).put("sender","SMSFWD próba").put("target",target).put("channel","email").put("ruleName","Valódi e-mail próba").put("body","Az SMSFWD e-mail-küldési próbája.").put("at",java.time.Instant.now().toString()).put("status","pending").put("simulated",false).put("started",System.currentTimeMillis());
            if(!SmsStore.add(getContext(),item)){call.reject("Nem sikerült tárolni a próbát.");return;}
            EmailWorker.enqueue(getContext(),id);call.resolve();
        }catch(Exception e){call.reject("Ellenőrizd a címzettet és a postafiók beállításait.");}
    }
    @PluginMethod public void retryEmail(PluginCall call) {
        String id=call.getString("id","");org.json.JSONObject item=SmsStore.find(getContext(),id);
        if(item==null || !item.optString("channel").equals("email") || !item.optString("status").equals("blocked")){call.reject("Csak a beállítás miatt blokkolt e-mail küldhető újra innen.");return;}
        if(!EmailCredentials.metadata(getContext()).optBoolean("configured")){call.reject("Előbb állítsd be a küldő postafiókot.");return;}
        if(!SmsStore.setState(getContext(),id,"pending","")){call.reject("Nem sikerült tárolni az újraküldést.");return;}
        EmailWorker.enqueue(getContext(),id);call.resolve();
    }
    @PluginMethod public void enableSms(PluginCall call) {
        if (getPermissionState("sms") == PermissionState.GRANTED) { getState(call); return; }
        requestPermissionForAlias("sms", call, "permissionResult");
    }
    @PermissionCallback private void permissionResult(PluginCall call) { getState(call); }
    @PluginMethod public void clearHistory(PluginCall call) {
        // In-flight records must survive until sent callbacks arrive, even after UI deletion.
        SmsStore.clearVisibleHistory(getContext());
        call.resolve();
    }
}
