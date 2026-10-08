package hu.smsfwd.app;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;
final class EmailCredentials {
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        String alias="smsfwd_email_v1";
        if (!store.containsAlias(alias)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
        }
        return (SecretKey)store.getKey(alias,null);
    }
    static synchronized void save(Context context,EmailConfig config) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String secret=Base64.encodeToString(cipher.doFinal(config.password.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        JSONObject json=new JSONObject().put("host",config.host).put("port",config.port).put("address",config.address).put("secret",secret).put("iv",iv);
        if (!SmsStore.preferences(context).edit().putString("emailConfig",json.toString()).commit()) throw new IllegalStateException("Nem sikerült menteni a postafiókot.");
    }
    static EmailConfig load(Context context) throws Exception {
        String raw=SmsStore.preferences(context).getString("emailConfig",null);if(raw==null)return null;
        JSONObject json=new JSONObject(raw);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(json.getString("iv"),Base64.NO_WRAP)));
        String password=new String(cipher.doFinal(Base64.decode(json.getString("secret"),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        return new EmailConfig(json.getString("host"),json.getInt("port"),json.getString("address"),password);
    }
    static JSONObject metadata(Context context) {
        try { JSONObject j=new JSONObject(SmsStore.preferences(context).getString("emailConfig","{}"));return new JSONObject().put("configured",j.has("secret")).put("host",j.optString("host")).put("port",j.optInt("port",587)).put("address",j.optString("address")); }
        catch(Exception e){return new JSONObject();}
    }
}
