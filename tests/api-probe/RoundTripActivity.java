// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

/** A real input/scroll/activity-result sandbox; operator results are explicitly manual. */
public final class RoundTripActivity extends Activity {
  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xfffafafa);
    TextView event = new TextView(this);
    event.setTextColor(0xff171717);
    event.setText("Tap each corner target; type, select, scroll and rotate.");
    root.addView(event);
    row(root, event, "Top left", "Top right");
    EditText input = new EditText(this);
    input.setTextColor(0xff171717);
    input.setBackgroundColor(0xffffffff);
    input.setId(0x123001);
    input.setHint("English / فارسی input");
    input.setText(
        saved == null ? getPreferences(0).getString("input", "") : saved.getString("input", ""));
    root.addView(input);
    ScrollView scroll = new ScrollView(this);
    TextView longText = new TextView(this);
    longText.setTextColor(0xff171717);
    StringBuilder text = new StringBuilder();
    for (int i = 1; i <= 100; i++) text.append(i).append(" · Shiraz · شیراز · touch and scroll\n");
    longText.setText(text);
    scroll.addView(longText);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    row(root, event, "Bottom left", "Bottom right");
    Button finish = new Button(this);
    finish.setBackgroundColor(0xff303030);
    finish.setTextColor(0xffffffff);
    finish.setText("Save text and return result 42");
    finish.setOnClickListener(v -> {
      boolean ok = getPreferences(0).edit().putString("input", input.getText().toString()).commit();
      if (!ok) {
        event.setText("FAIL: could not persist input");
        return;
      }
      setResult(RESULT_OK, new Intent().putExtra("answer", 42));
      finish();
    });
    root.addView(finish);
    setContentView(root);
  }
  private void row(LinearLayout root, TextView event, String a, String b) {
    LinearLayout row = new LinearLayout(this);
    for (String label : new String[] {a, b}) {
      Button target = new Button(this);
      target.setBackgroundColor(0xff303030);
      target.setTextColor(0xffffffff);
      target.setText(label);
      target.setOnTouchListener((v, e) -> {
        event.setText(label + " action=" + e.getActionMasked() + " local=" + e.getX() + ","
            + e.getY() + " bounds=" + v.getWidth() + "x" + v.getHeight());
        return false;
      });
      row.addView(target, new LinearLayout.LayoutParams(0, -2, 1));
    }
    root.addView(row);
  }
  @Override
  protected void onSaveInstanceState(Bundle state) {
    EditText input = (EditText) findViewById(0x123001);
    state.putString("input", input.getText().toString());
    super.onSaveInstanceState(state);
  }
}
