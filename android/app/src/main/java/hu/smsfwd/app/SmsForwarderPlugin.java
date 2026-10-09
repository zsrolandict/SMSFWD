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
import com.getcapacitor.annotation.ActivityCallback;
import androidx.activity.result.ActivityResult;

@CapacitorPlugin(name = "SmsForwarder", permissions = {
    @Permission(alias = "receiveSms", strings = { Manifest.permission.RECEIVE_SMS }),
    @Permission(alias = "sendSms", strings = { Manifest.permission.SEND_SMS })
})
public class SmsForwarderPlugin extends Plugin {
    @PluginMethod public void revealEmailPassword(PluginCall call) {
        android.app.KeyguardManager manager=getContext().getSystemService(android.app.KeyguardManager.class);
        if(manager!=null && manager.isDeviceSecure()) {
            android.content.Intent intent=manager.createConfirmDeviceCredentialIntent("SMSFWD", "A mentett alkalmazásjelszó megjelenítése");
            if(intent==null){call.reject("Nem érhető el a telefon feloldási ellenőrzése.");return;}
            startActivityForResult(call,intent,"passwordAuthenticated");
        }else revealPassword(call);
    }
    @ActivityCallback private void passwordAuthenticated(PluginCall call,ActivityResult result) {
        if(call==null)return;
        if(result.getResultCode()!=android.app.Activity.RESULT_OK){call.reject("A jelszó megjelenítését megszakítottad.");return;}
        revealPassword(call);
    }
    private void revealPassword(PluginCall call) {
        try{EmailConfig config=EmailCredentials.load(getContext());if(config==null){call.reject("Nincs mentett postafiók.");return;}JSObject result=new JSObject();result.put("password",config.password);call.resolve(result);}
        catch(Exception e){call.reject("A mentett jelszó nem olvasható. Add meg újra.");}
    }
    @PluginMethod public void configure(PluginCall call) {
        JSArray rules = call.getArray("rules", new JSArray());
        int limit = call.getInt("dailyLimit", 20);
        if (limit < 1 || limit > 1000) { call.reject("Érvénytelen SMS-limit."); return; }
        boolean active = Boolean.TRUE.equals(call.getBoolean("active", false));
        SmsStore.preferences(getContext()).edit().putString("rules", rules.toString()).putBoolean("active", active).putInt("limit", limit).commit();
        call.resolve();
    }
    @PluginMethod public void getState(PluginCall call) {
        JSObject result = new JSObject();
        boolean receiveGranted = getPermissionState("receiveSms") == PermissionState.GRANTED;
        boolean sendGranted = getPermissionState("sendSms") == PermissionState.GRANTED;
        result.put("granted", receiveGranted);
        result.put("receiveGranted", receiveGranted);
        result.put("sendGranted", sendGranted);
        result.put("history", SmsStore.history(getContext()));
        result.put("email",MailAccounts.metadata(getContext()));
        result.put("debug", debugState(receiveGranted, sendGranted));
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
            EmailCredentials.save(getContext(),config);MailAccounts.selectSmtp(getContext());call.resolve();
        }catch(Exception e){call.reject(e instanceof IllegalArgumentException ? e.getMessage() : "Nem sikerült menteni a postafiókot. Állítsd be újra.");}
    }
    @PluginMethod public void queueTestEmail(PluginCall call) {
        try {
            if(!MailAccounts.isConfigured(getContext())){call.reject("Előbb állítsd be a küldő postafiókot.");return;}
            String target=call.getString("target","");new javax.mail.internet.InternetAddress(target,true);
            String id=java.util.UUID.randomUUID().toString();
            org.json.JSONObject item=new org.json.JSONObject().put("id",id).put("sender","SMSFWD próba").put("target",target).put("channel","email").put("ruleName","Valódi e-mail próba").put("body","Az SMSFWD e-mail-küldési próbája.").put("at",java.time.Instant.now().toString()).put("status","pending").put("simulated",false).put("testEmail",true).put("started",System.currentTimeMillis());
            if(!SmsStore.add(getContext(),item)){call.reject("Nem sikerült tárolni a próbát.");return;}
            EmailWorker.enqueue(getContext(),id);call.resolve();
        }catch(Exception e){call.reject("Ellenőrizd a címzettet és a postafiók beállításait.");}
    }
    @PluginMethod public void retryEmail(PluginCall call) {
        String id=call.getString("id","");org.json.JSONObject item=SmsStore.find(getContext(),id);
        if(item==null || !item.optString("channel").equals("email") || !item.optString("status").equals("blocked")){call.reject("Csak a beállítás miatt blokkolt e-mail küldhető újra innen.");return;}
        if(!MailAccounts.isConfigured(getContext())){call.reject("Előbb állítsd be a küldő postafiókot.");return;}
        if(!SmsStore.setState(getContext(),id,"pending","")){call.reject("Nem sikerült tárolni az újraküldést.");return;}
        EmailWorker.enqueue(getContext(),id);call.resolve();
    }
    @PluginMethod public void enableSms(PluginCall call) {
        if (getPermissionState("receiveSms") == PermissionState.GRANTED) { getState(call); return; }
        SmsStore.preferences(getContext()).edit().putBoolean("receiveRequested",true).commit();
        requestPermissionForAlias("receiveSms", call, "permissionResult");
    }
    @PluginMethod public void enableSmsSending(PluginCall call) {
        if (getPermissionState("sendSms") == PermissionState.GRANTED) { getState(call); return; }
        SmsStore.preferences(getContext()).edit().putBoolean("sendRequested",true).commit();
        requestPermissionForAlias("sendSms", call, "permissionResult");
    }
    @PermissionCallback private void permissionResult(PluginCall call) { getState(call); }
    @PluginMethod public void openAppSettings(PluginCall call) {
        try {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:"+getContext().getPackageName()));
            getActivity().startActivity(intent);
            call.resolve();
        } catch (android.content.ActivityNotFoundException e) {
            call.reject("Nem nyithatók meg az alkalmazásbeállítások. A telefon Beállítások → Alkalmazások → SMSFWD → Engedélyek menüjét használd.");
        }
    }
    private JSObject debugState(boolean receiveGranted,boolean sendGranted) {
        android.content.SharedPreferences preferences=SmsStore.preferences(getContext());
        JSObject debug=new JSObject();
        try { debug.put("appVersion",getContext().getPackageManager().getPackageInfo(getContext().getPackageName(),0).versionName); }
        catch(android.content.pm.PackageManager.NameNotFoundException e){debug.put("appVersion","ismeretlen");}
        debug.put("androidVersion",android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")");
        debug.put("deviceManufacturer",android.os.Build.MANUFACTURER);
        debug.put("deviceModel",android.os.Build.MODEL);
        debug.put("receiveGranted",receiveGranted);debug.put("sendGranted",sendGranted);
        debug.put("receiveRequested",preferences.getBoolean("receiveRequested",false));
        debug.put("sendRequested",preferences.getBoolean("sendRequested",false));
        debug.put("receiveRationale",androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),Manifest.permission.RECEIVE_SMS));
        debug.put("sendRationale",androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),Manifest.permission.SEND_SMS));
        debug.put("emailConfigured",MailAccounts.isConfigured(getContext()));
        debug.put("active",preferences.getBoolean("active",false));
        debug.put("forwardingReady",preferences.getBoolean("active",false)&&receiveGranted);
        try { debug.put("ruleCount",new org.json.JSONArray(preferences.getString("rules","[]")).length()); }
        catch(org.json.JSONException e){debug.put("ruleCount",0);}
        return debug;
    }
    @PluginMethod public void clearHistory(PluginCall call) {
        // In-flight records must survive until sent callbacks arrive, even after UI deletion.
        SmsStore.clearVisibleHistory(getContext());
        call.resolve();
    }
}
