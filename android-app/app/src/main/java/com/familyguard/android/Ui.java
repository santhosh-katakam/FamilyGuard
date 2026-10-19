package com.familyguard.android;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

final class Ui {
  static LinearLayout column(Activity a) {
    return (LinearLayout) a.findViewById(android.R.id.content).findViewWithTag("column");
  }

  static void page(Activity a, String title, String subtitle) {
    ScrollView scroll = new ScrollView(a);
    scroll.setBackgroundColor(Color.rgb(246, 249, 243));
    LinearLayout layout = new LinearLayout(a);
    layout.setTag("column");
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 45, 32, 35);
    scroll.addView(layout);
    a.setContentView(scroll);
    TextView head = text(a, title);
    head.setTextSize(31);
    head.setTextColor(Color.rgb(37, 91, 65));
    text(a, subtitle);
  }

  static TextView text(Activity a, String value) {
    TextView v = new TextView(a);
    v.setText(value);
    v.setTextSize(15);
    v.setTextColor(Color.rgb(70, 89, 73));
    v.setPadding(0, 14, 0, 14);
    column(a).addView(v);
    return v;
  }

  static Button button(Activity a, String title, Runnable action) {
    Button b = new Button(a);
    b.setText(title);
    b.setAllCaps(false);
    b.setOnClickListener(v -> action.run());
    column(a).addView(b, new LinearLayout.LayoutParams(-1, -2));
    return b;
  }

  static EditText input(Activity a, String hint) {
    EditText e = new EditText(a);
    e.setHint(hint);
    e.setSingleLine(true);
    column(a).addView(e);
    return e;
  }

  static CheckBox check(
      Activity a,
      String label,
      boolean value,
      android.widget.CompoundButton.OnCheckedChangeListener changed) {
    CheckBox b = new CheckBox(a);
    b.setText(label);
    b.setChecked(value);
    b.setPadding(0, 12, 0, 12);
    column(a).addView(b);
    b.setOnCheckedChangeListener(changed);
    return b;
  }

  static void confirm(Activity a, String message, Runnable action) {
    new AlertDialog.Builder(a)
        .setTitle("FamilyGuard")
        .setMessage(message)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Confirm", (d, w) -> action.run())
        .show();
  }
}
