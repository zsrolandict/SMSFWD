package hu.smsfwd.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;

final class SmsStore {
    static SharedPreferences preferences(Context context) { return context.getSharedPreferences("smsfwd_native", Context.MODE_PRIVATE); }
    static synchronized JSONArray allHistory(Context context) {
        try { return new JSONArray(preferences(context).getString("history", "[]")); } catch (Exception ignored) { return new JSONArray(); }
    }
    static synchronized JSONArray history(Context context) {
        JSONArray all = allHistory(context), visible = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item == null) continue;
            if (item.optString("status").equals("in_flight") && System.currentTimeMillis() - item.optLong("started") > 600000) {
                try { item.put("status", "unknown").put("error", "Nem érkezett teljes küldési visszajelzés. Automatikusan nem ismételjük."); } catch (Exception ignored) {}
            }
            if (!item.optBoolean("hidden")) visible.put(item);
        }
        persist(context, all);
        return visible;
    }
    static synchronized boolean add(Context context, JSONObject item) {
        JSONArray old = allHistory(context), next = new JSONArray(); next.put(item);
        for (int i = 0; i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry != null && (i < 199 || entry.optString("status").equals("in_flight") || entry.optString("status").equals("pending"))) next.put(entry);
        }
        return persist(context, next);
    }
    static synchronized void clearVisibleHistory(Context context) {
        JSONArray old = allHistory(context), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry != null && (entry.optString("status").equals("in_flight") || entry.optString("status").equals("pending"))) {
                try { entry.put("hidden", true); next.put(entry); } catch (Exception ignored) {}
            }
        }
        persist(context, next);
    }
    static synchronized boolean persist(Context context, JSONArray list) { return preferences(context).edit().putString("history", list.toString()).commit(); }
    static synchronized JSONObject find(Context context,String id) {
        JSONArray all=allHistory(context);for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null && item.optString("id").equals(id))return item;}return null;
    }
    static synchronized boolean setState(Context context, String id, String status, String error) {
        JSONArray all = allHistory(context);
        for (int i = 0; i < all.length(); i++) {
            JSONObject entry = all.optJSONObject(i);
            if (entry != null && entry.optString("id").equals(id)) {
                try { entry.put("status", status).put("error", error); if(status.equals("in_flight"))entry.put("started",System.currentTimeMillis()); } catch (Exception ignored) {}
            }
        }
        return persist(context, all);
    }
    static synchronized void sentResult(Context context, String id, int part, int result) {
        JSONArray all = allHistory(context);
        for (int i = 0; i < all.length(); i++) {
            JSONObject entry = all.optJSONObject(i);
            if (entry == null || !entry.optString("id").equals(id)) continue;
            try {
                JSONObject results = entry.optJSONObject("partResults");
                if (results == null) results = new JSONObject();
                results.put(String.valueOf(part), result);
                entry.put("partResults", results);
                if (result != android.app.Activity.RESULT_OK) {
                    entry.put("status", "unknown").put("error", "Egy SMS-rész küldése hibát jelzett (" + result + "). Lehet részleges küldés; nincs automatikus ismétlés.");
                } else if (results.length() == entry.optInt("parts")) {
                    boolean success = true;
                    java.util.Iterator<String> keys = results.keys();
                    while (keys.hasNext()) if (results.optInt(keys.next()) != android.app.Activity.RESULT_OK) success = false;
                    if (success) entry.put("status", "sent").put("error", "");
                }
            } catch (Exception ignored) {}
        }
        persist(context, all);
    }
}
