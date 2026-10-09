package hu.smsfwd.app;

import android.accounts.Account;
import android.app.Activity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import java.util.Collections;

@CapacitorPlugin(name = "GmailAuth")
public class GmailAuthPlugin extends Plugin {
    private ActivityResultLauncher<IntentSenderRequest> authorizationLauncher;
    private PluginCall pendingConnect;
    private boolean disconnecting;

    @Override public void load() {
        // Registry registration supports IntentSender without exposing any token to the WebView.
        authorizationLauncher = getActivity().getActivityResultRegistry().register("smsfwd-gmail-authorization",
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                PluginCall call = pendingConnect;
                if (call == null) return;
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    finishError(call, "A Google-fiók összekapcsolását megszakítottad.");
                    return;
                }
                try { finishConnection(call, client().getAuthorizationResultFromIntent(result.getData())); }
                catch (ApiException e) { finishError(call, GmailCredentials.authError(e.getStatusCode())); }
            });
    }

    private AuthorizationClient client() { return Identity.getAuthorizationClient(getActivity()); }

    @PluginMethod public void connect(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (pendingConnect != null || disconnecting) { call.reject("Már folyamatban van egy Google-fiók művelet."); return; }
            int availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext());
            if (availability != 0) {
                call.reject("A Google Play-szolgáltatások nem érhetők el vagy frissítés szükséges (" + availability + "). SMTP-vel továbbra is beállíthatod a küldést.");
                return;
            }
            pendingConnect = call;
            client().authorize(GmailCredentials.request(null))
                .addOnSuccessListener(result -> {
                    if (pendingConnect != call) return;
                    if (result.hasResolution()) {
                        if (result.getPendingIntent() == null) { finishError(call, "A Google engedélykérő ablaka nem érhető el."); return; }
                        try { authorizationLauncher.launch(new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build()); }
                        catch (Exception e) { finishError(call, "Nem sikerült megnyitni a Google engedélykérő ablakát."); }
                    } else finishConnection(call, result);
                })
                .addOnFailureListener(e -> finishError(call, e instanceof ApiException
                    ? GmailCredentials.authError(((ApiException) e).getStatusCode())
                    : "Nem sikerült elindítani a Google-fiók összekapcsolását. Ellenőrizd a hálózatot és a Google Play-szolgáltatásokat."));
        });
    }

    private void finishConnection(PluginCall call, AuthorizationResult result) {
        if (pendingConnect != call) return;
        if (result.getAccessToken() == null || result.getAccessToken().isEmpty()
            || !result.getGrantedScopes().contains(GmailCredentials.SEND_SCOPE)) {
            finishError(call, "A Gmail-küldési engedély nem érkezett meg. Engedélyezd az e-mail-küldést a Google ablakában.");
            return;
        }
        String address = result.toGoogleSignInAccount() == null ? null : result.toGoogleSignInAccount().getEmail();
        if (address == null || address.isEmpty()) {
            finishError(call, "A Google nem adta vissza a kiválasztott fiókot. Próbáld újra az összekapcsolást.");
            return;
        }
        try {
            GmailCredentials.save(getContext(), address);
            JSObject metadata = new JSObject(MailAccounts.metadata(getContext()).toString());
            pendingConnect = null;
            call.resolve(metadata);
        } catch (Exception e) { finishError(call, "Nem sikerült menteni a Google-fiók kapcsolatát."); }
        // The AuthorizationResult/token is discarded, never persisted or sent to JavaScript.
    }

    private void finishError(PluginCall call, String message) {
        if (pendingConnect != call) return;
        pendingConnect = null;
        call.reject(message);
    }

    @PluginMethod public void disconnect(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (pendingConnect != null || disconnecting) { call.reject("Már folyamatban van egy Google-fiók művelet."); return; }
            String address = GmailCredentials.address(getContext());
            try { GmailCredentials.remove(getContext()); }
            catch (Exception e) { call.reject("Nem sikerült bontani a Google-fiók kapcsolatát."); return; }
            if (address.isEmpty()) { resolveDisconnected(call, false); return; }
            disconnecting = true;
            client().revokeAccess(RevokeAccessRequest.builder().setAccount(new Account(address, "com.google"))
                .setScopes(Collections.singletonList(new Scope(GmailCredentials.SEND_SCOPE))).build())
                .addOnSuccessListener(unused -> { disconnecting = false; resolveDisconnected(call, false); })
                .addOnFailureListener(error -> { disconnecting = false; resolveDisconnected(call, true); });
        });
    }

    private void resolveDisconnected(PluginCall call, boolean revocationPending) {
        try {
            JSObject result = new JSObject(MailAccounts.metadata(getContext()).toString());
            if (revocationPending) result.put("warning", "A helyi kapcsolat megszűnt. A Google-engedély visszavonása nem igazolható; a Google-fiók Biztonság oldalán is eltávolíthatod az SMSFWD hozzáférését.");
            call.resolve(result);
        } catch (Exception e) { call.reject("A Google-kapcsolat bontása után nem olvasható az állapot."); }
    }

    @Override protected void handleOnDestroy() {
        if (pendingConnect != null) finishError(pendingConnect, "A Google összekapcsolási ablaka bezárult. Próbáld újra a Beállításokban.");
        if (authorizationLauncher != null) authorizationLauncher.unregister();
    }
}
