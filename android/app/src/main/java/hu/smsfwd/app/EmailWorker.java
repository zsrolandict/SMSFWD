package hu.smsfwd.app;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
public class EmailWorker extends Worker {
    public EmailWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    static void enqueue(Context context,String id) {
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(EmailWorker.class).setInputData(new Data.Builder().putString("id",id).build()).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("email-"+id,ExistingWorkPolicy.KEEP,work);
    }
    @NonNull @Override public Result doWork() {
        Context context=getApplicationContext();String id=getInputData().getString("id");JSONObject item=SmsStore.find(context,id);
        if(item==null || !item.optString("status").equals("pending"))return Result.success();
        if(!SmsStore.preferences(context).getBoolean("active",false))return Result.retry();
        EmailConfig config;
        try{config=EmailCredentials.load(context);}
        catch(Exception e){SmsStore.setState(context,id,"blocked","A mentett postafiók nem olvasható. Állítsd be újra.");return Result.success();}
        if(config==null){SmsStore.setState(context,id,"blocked","Állítsd be a küldő postafiókot a Beállításokban.");return Result.success();}
        if(!SmsStore.setState(context,id,"in_flight",""))return Result.failure();
        try {
            MailTransport.send(config,item.getString("target"),"SMS érkezett: "+item.optString("sender"),"Feladó: "+item.optString("sender")+"\nÉrkezett (UTC): "+item.optString("at")+"\n\n"+item.optString("body"));
            SmsStore.setState(context,id,"sent","");
        } catch(javax.mail.AuthenticationFailedException e) {
            SmsStore.setState(context,id,"blocked","A postafiók nem fogadta el a belépést. Gmailhez alkalmazásjelszó kell; ellenőrizd a küldő címet és a fiók beállításait.");
        } catch(Exception e) {
            // SMTP acceptance after a disconnect can be uncertain: never blindly resend.
            SmsStore.setState(context,id,"unknown","Az e-mail elküldése nem igazolható ("+e.getClass().getSimpleName()+"). Ellenőrizd a postafiókot és a hálózatot; újraküldés duplikációt okozhat.");
        }
        return Result.success();
    }
}
