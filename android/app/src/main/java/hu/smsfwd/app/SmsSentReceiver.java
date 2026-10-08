package hu.smsfwd.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class SmsSentReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("deliveryId");
        if (id != null) SmsStore.sentResult(context, id, intent.getIntExtra("part", -1), getResultCode());
    }
}
