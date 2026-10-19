package com.familyguard.android;

import android.content.*;
import android.telephony.TelephonyManager;
import java.util.Set;

public class CallStateReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(i.getAction())) return;
    if (!Consent.allowed(c, "calls")
        || !Consent.granted(c, android.Manifest.permission.READ_PHONE_STATE)) return;
    String state = i.getStringExtra(TelephonyManager.EXTRA_STATE);
    if (state == null || !Set.of("RINGING", "OFFHOOK", "IDLE").contains(state)) return;
    var p = c.getSharedPreferences("call", 0);
    if (state.equals(p.getString("last", ""))) return;
    p.edit().putString("last", state).apply();
    Events.add(c, "calls", "CALL_STATE", null, Events.data("state", state), null);
  }
}
