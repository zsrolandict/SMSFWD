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
        call.resolve(result);
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
