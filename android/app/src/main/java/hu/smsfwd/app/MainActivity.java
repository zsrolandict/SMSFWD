package hu.smsfwd.app;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(SmsForwarderPlugin.class);
        registerPlugin(GmailAuthPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
