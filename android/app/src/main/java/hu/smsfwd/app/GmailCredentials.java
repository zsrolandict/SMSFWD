package hu.smsfwd.app;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Only the selected account is saved. Google Play services owns token storage and refresh. */
final class GmailCredentials {
    static final String SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

    static AuthorizationRequest request(Account account) {
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope(SEND_SCOPE)))
            .setOptOutIncludingGrantedScopes(true);
        if (account != null) builder.setAccount(account);
        return builder.build();
    }

    static String address(Context context) {
        return SmsStore.preferences(context).getString("gmailAddress", "");
    }

    static boolean reconnectRequired(Context context) {
        return SmsStore.preferences(context).getBoolean("gmailReconnectRequired", false);
    }

    static void save(Context context, String address) throws Exception {
        // This is account metadata, never an OAuth access/refresh token.
        if (!SmsStore.preferences(context).edit().putString("gmailAddress", address)
            .putBoolean("gmailReconnectRequired", false).putString("emailProvider", "gmail").commit()) {
            throw new IllegalStateException("Nem sikerült menteni a Google-fiók kapcsolatát.");
        }
    }

    static void markReconnect(Context context) {
        SmsStore.preferences(context).edit().putBoolean("gmailReconnectRequired", true).commit();
    }

    static void remove(Context context) throws Exception {
        SharedPreferences.Editor editor = SmsStore.preferences(context).edit()
            .remove("gmailAddress").remove("gmailReconnectRequired");
        if (MailAccounts.provider(context).equals("gmail")) {
            editor.putString("emailProvider", EmailCredentials.metadata(context).optBoolean("configured") ? "smtp" : "none");
        }
        if (!editor.commit()) throw new IllegalStateException("Nem sikerült bontani a Google-fiók kapcsolatát.");
    }

    static String freshToken(Context context) throws MailAccounts.BlockedException {
        String address = address(context);
        if (address.isEmpty() || reconnectRequired(context)) {
            throw new MailAccounts.BlockedException("Kapcsold össze újra a Google-fiókot a Beállításokban.");
        }
        try {
            // Runs on WorkManager's background thread; a resolution cannot be displayed here.
            AuthorizationResult result = Tasks.await(Identity.getAuthorizationClient(context)
                .authorize(request(new Account(address, "com.google"))), 30, TimeUnit.SECONDS);
            if (result.hasResolution() || result.getAccessToken() == null || result.getAccessToken().isEmpty()
                || !result.getGrantedScopes().contains(SEND_SCOPE)) {
                markReconnect(context);
                throw new MailAccounts.BlockedException("A Google új engedélyezést kér. Kapcsold össze újra a Gmailt a Beállításokban.");
            }
            if (result.toGoogleSignInAccount() != null && result.toGoogleSignInAccount().getEmail() != null
                && !address.equalsIgnoreCase(result.toGoogleSignInAccount().getEmail())) {
                markReconnect(context);
                throw new MailAccounts.BlockedException("A Google-fiók megváltozott. Válaszd ki újra a küldő fiókot.");
            }
            return result.getAccessToken();
        } catch (MailAccounts.BlockedException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailAccounts.BlockedException("A Google-engedély ellenőrzése megszakadt. Próbáld újra az Előzményekben.");
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            if (cause instanceof ApiException) {
                int code = ((ApiException) cause).getStatusCode();
                if (code != 7) markReconnect(context);
                throw new MailAccounts.BlockedException(authError(code));
            }
            throw new MailAccounts.BlockedException("A Google-engedély most nem ellenőrizhető. Ellenőrizd a hálózatot; az e-mail még nem lett elküldve.");
        }
    }

    static String authError(int code) {
        if (code == 10) return "A Google-kapcsolat alkalmazásregisztrációja hiányzik vagy hibás. Google Cloud: Gmail API, Android OAuth-kliens, hu.smsfwd.app csomagnév és az APK SHA-1 aláírása szükséges.";
        if (code == 7) return "A Google nem érhető el. Ellenőrizd az internetkapcsolatot, majd próbáld újra.";
        if (code == 16 || code == 12501) return "A Google-fiók összekapcsolását megszakítottad.";
        return "A Google nem engedélyezte a kapcsolatot (" + code + "). Ellenőrizd a Google Cloud OAuth-beállításait és a tesztfelhasználókat, majd kapcsold össze újra.";
    }
}
