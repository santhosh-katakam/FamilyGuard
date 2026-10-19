package com.familyguard.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    if (BuildConfig.APP_ROLE.equals("parent") || BuildConfig.APP_ROLE.equals("child")) {
      startActivity(
          new Intent(
              this,
              BuildConfig.APP_ROLE.equals("parent") ? ParentActivity.class : ChildActivity.class));
      finish();
      return;
    }
    Ui.page(this, "FamilyGuard", "A little closer. Wherever you are.");
    Ui.text(
        this,
        "One app, two clear roles. Parent Admin opens your secure family dashboard. Child Mode"
            + " provides visible, permission-based supervision on this Android device.");
    Ui.button(
        this, "Parent Admin Mode", () -> startActivity(new Intent(this, ParentActivity.class)));
    Ui.button(this, "Child Mode", () -> startActivity(new Intent(this, ChildActivity.class)));
    Ui.text(
        this,
        "No hidden monitoring. No private conversation collection. You choose which permissions to"
            + " enable.");
  }
}
