// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import org.json.*;

/** Operator-driven test runner; all API assertions execute outside the controller. */
public final class ProbeActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<String> queue = new ArrayList<>();
  private JSONObject results = new JSONObject();
  private String token, activeId, notice = "", manualId;
  private long started;
  private boolean destroyed;
  private TextView output, summary;
  private Button categoryButton;
  private TextView caseLabel;
  private final List<String> groups = new ArrayList<>();
  private int groupIndex, selectedIndex;
  private final List<ProbeCase> visible = new ArrayList<>();
  private File report;
  private String runId = UUID.randomUUID().toString();
  private final Runnable poll = this::poll;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    report = new File(getFilesDir(), "api-probe-report.json");
    restore();
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setBackgroundColor(0xfffafafa);
    layout.setPadding(12, 12, 12, 12);
    TextView title = new TextView(this);
    title.setText("Shiraz API Probe · " + BuildConfig.VERSION);
    title.setTextSize(20);
    title.setTextColor(0xff171717);
    layout.addView(title);
    summary = new TextView(this);
    summary.setTextColor(0xff171717);
    layout.addView(summary);
    groups.add("All categories");
    for (ProbeCase test : ProbeCatalog.CASES)
      if (!groups.contains(test.category))
        groups.add(test.category);
    categoryButton = new Button(this);
    categoryButton.setBackgroundColor(0xff303030);
    categoryButton.setTextColor(0xffffffff);
    categoryButton.setOnClickListener(v -> {
      groupIndex = (groupIndex + 1) % groups.size();
      selectedIndex = 0;
      populate(groups.get(groupIndex));
    });
    layout.addView(categoryButton);
    caseLabel = new TextView(this);
    caseLabel.setTextColor(0xff171717);
    layout.addView(caseLabel);
    LinearLayout navigation = new LinearLayout(this);
    layout.addView(navigation);
    button(navigation, "Previous case", () -> {
      if (!visible.isEmpty())
        selectedIndex = (selectedIndex + visible.size() - 1) % visible.size();
      refresh();
    });
    button(navigation, "Next case", () -> {
      if (!visible.isEmpty())
        selectedIndex = (selectedIndex + 1) % visible.size();
      refresh();
    });
    if (groupIndex >= groups.size())
      groupIndex = 0;
    populate(groups.get(groupIndex));
    LinearLayout first = new LinearLayout(this);
    layout.addView(first);
    button(first, "Run selected", () -> start(false, false));
    button(first, "Run category", () -> start(true, false));
    button(first, "Run all", () -> start(true, true));
    LinearLayout second = new LinearLayout(this);
    layout.addView(second);
    button(second, "Stop queue", () -> {
      queue.clear();
      persist();
      refresh();
    });
    button(second, "Save/export", this::export);
    button(second, "Input sandbox", () -> startActivity(new Intent(this, RoundTripActivity.class)));
    LinearLayout manual = new LinearLayout(this);
    layout.addView(manual);
    button(manual, "Manual PASS", () -> manualResult("PASS"));
    button(manual, "Manual FAIL", () -> manualResult("FAIL"));
    button(manual, "Manual SKIP", () -> manualResult("SKIPPED"));
    output = new TextView(this);
    output.setTextColor(0xff171717);
    layout.addView(output);
    ScrollView page = new ScrollView(this);
    page.setFillViewport(true);
    page.addView(layout);
    setContentView(page);
    refresh();
    if (activeId != null)
      handler.post(poll);
    else
      advance();
  }
  private void button(LinearLayout row, String label, Runnable action) {
    Button b = new Button(this);
    b.setBackgroundColor(0xff303030);
    b.setTextColor(0xffffffff);
    b.setText(label);
    b.setTextSize(11);
    b.setOnClickListener(v -> action.run());
    row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
  }
  private void populate(String category) {
    visible.clear();
    for (ProbeCase test : ProbeCatalog.CASES)
      if (category.equals("All categories") || category.equals(test.category))
        visible.add(test);
    if (selectedIndex >= visible.size())
      selectedIndex = 0;
    categoryButton.setText("Category: " + category + " (tap to change)");
    refresh();
  }
  private ProbeCase selected() {
    return selectedIndex >= 0 && selectedIndex < visible.size() ? visible.get(selectedIndex) : null;
  }
  private ProbeCase find(String id) {
    for (ProbeCase test : ProbeCatalog.CASES)
      if (test.id.equals(id))
        return test;
    return null;
  }
  private void start(boolean batch, boolean all) {
    if (ProbeExecutor.localBlocked) {
      notice = "Local test timed out. Restart the app process before running more tests.";
      refresh();
      return;
    }
    if (activeId != null || manualId != null || !queue.isEmpty()) {
      notice = "Finish current test or stop queue first.";
      refresh();
      return;
    }
    if (!batch) {
      ProbeCase test = selected();
      if (test == null)
        return;
      if (test.mode.equals("manual")) {
        manualId = test.id;
        notice = "Follow the expected procedure, then record your observation with Manual "
            + "PASS/FAIL/SKIP.";
        persist();
        refresh();
        if (test.id.equals("lifecycle.activity_result"))
          startActivityForResult(new Intent(this, RoundTripActivity.class), 85);
        return;
      }
      queue.add(test.id);
    } else
      for (ProbeCase test : all ? Arrays.asList(ProbeCatalog.CASES) : visible) {
        if (test.mode.equals("automatic"))
          queue.add(test.id);
        else if (!results.has(test.id))
          recordResult(test.id, "SKIPPED", "Manual interaction required; not executed by batch", 0);
      }
    notice = "";
    if (persist())
      advance();
  }
  private void advance() {
    if (destroyed || ProbeExecutor.localBlocked || activeId != null || queue.isEmpty())
      return;
    activeId = queue.remove(0);
    token = UUID.randomUUID().toString();
    started = SystemClock.elapsedRealtime();
    if (!persist()) {
      activeId = null;
      queue.clear();
      refresh();
      return;
    }
    try {
      Intent intent = new Intent(this, ProbeService.class)
                          .putExtra("id", activeId)
                          .putExtra("token", token)
                          .putExtra("controllerPid", android.os.Process.myPid());
      if (startService(intent) == null)
        throw new IllegalStateException("startService returned null");
      handler.postDelayed(poll, 100);
    } catch (UnsupportedOperationException unavailable) {
      notice = "Separate service processes unavailable; running in-process diagnostics. Timeout "
          + "requires process restart.";
      try {
        ProbeExecutor.start(getApplicationContext(), activeId, token, false);
        handler.postDelayed(poll, 100);
      } catch (Throwable failure) {
        complete("FAIL", "Local executor failed: " + failure, 0);
      }
    } catch (Throwable e) {
      complete("UNSUPPORTED", "Worker launch failed: " + e, 0);
    }
    refresh();
  }
  private void poll() {
    if (destroyed || activeId == null)
      return;
    try {
      File file = ProbeFiles.result(this, token);
      if (file.exists()) {
        JSONObject r = ProbeFiles.read(file);
        ProbeProtocol.validate(r, activeId);
        String status = r.getString("status");
        String detail = r.getString("observed") + "; isolated=" + r.optBoolean("isolated");
        String executionMode = r.optString("executionMode", "isolated_process");
        if (status.equals("TIMEOUT") && executionMode.equals("in_process")) {
          queue.clear();
          notice = "Local timeout: restart app process before running further tests.";
        }
        if (complete(status, detail, r.getLong("durationMs"), executionMode))
          file.delete();
        return;
      }
      long elapsed = SystemClock.elapsedRealtime() - started;
      if (ProbeProtocol.overdue(started, SystemClock.elapsedRealtime())) {
        // No proof the worker stopped: pause instead of starting concurrent successors.
        queue.clear();
        complete("TIMEOUT",
            "No worker report by controller deadline; batch paused. Worker may have crashed or "
                + "failed to start.",
            Math.max(0, elapsed));
        return;
      }
      handler.postDelayed(poll, 100);
    } catch (Throwable e) {
      queue.clear();
      complete("FAIL", "Runner could not read result: " + e, 0);
    }
  }
  private void recordResult(String id, String status, String detail, long duration) {
    try {
      ProbeCase test = find(id);
      results.put(id,
          new JSONObject()
              .put("id", id)
              .put("category", test.category)
              .put("mode", test.mode)
              .put("expected", test.expected)
              .put("status", status)
              .put("observed", detail)
              .put("durationMs", duration)
              .put("timestampMs", System.currentTimeMillis()));
      android.util.Log.i("ShirazApiProbe", id + " " + status + " " + detail);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }
  private boolean complete(String status, String detail, long duration) {
    return complete(status, detail, duration, "controller");
  }
  private boolean complete(String status, String detail, long duration, String executionMode) {
    recordResult(activeId, status, detail, duration);
    try {
      results.getJSONObject(activeId).put("executionMode", executionMode);
    } catch (JSONException error) {
      throw new IllegalStateException(error);
    }
    activeId = null;
    token = null;
    boolean saved = persist();
    if (!saved)
      queue.clear();
    refresh();
    if (saved)
      handler.postDelayed(this::advance, 300);
    return saved;
  }
  private void manualResult(String status) {
    if (manualId == null) {
      notice = "Select and run a [manual] case first.";
      refresh();
      return;
    }
    recordResult(manualId, status, "Operator-recorded observation; not an automated assertion", 0);
    manualId = null;
    persist();
    refresh();
  }
  private boolean persist() {
    try {
      JSONObject root =
          new JSONObject()
              .put("schemaVersion", 1)
              .put("suiteVersion", BuildConfig.VERSION)
              .put("suiteFingerprint", BuildConfig.FINGERPRINT)
              .put("runId", runId)
              .put("catalogSize", ProbeCatalog.CASES.length)
              .put("device",
                  new JSONObject()
                      .put("manufacturer", Build.MANUFACTURER)
                      .put("model", Build.MODEL)
                      .put("sdk", Build.VERSION.SDK_INT)
                      .put("abis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS))))
              .put("results", results)
              .put("queue", new JSONArray(queue))
              .put("startedElapsedMs", started)
              .put("groupIndex", groupIndex)
              .put("selectedIndex", selectedIndex);
      if (activeId != null)
        root.put("activeId", activeId).put("token", token);
      if (manualId != null)
        root.put("manualId", manualId);
      ProbeFiles.write(report, root);
      return true;
    } catch (Throwable e) {
      notice = "REPORT SAVE FAILED; batch paused: " + e;
      queue.clear();
      return false;
    }
  }
  private void restore() {
    if (!report.exists())
      return;
    try {
      JSONObject root = ProbeFiles.read(report);
      if (root.optInt("schemaVersion", 0) != 1
          || !BuildConfig.FINGERPRINT.equals(root.optString("suiteFingerprint"))) {
        ProbeFiles.write(new File(getFilesDir(), "api-probe-report-previous.json"), root);
        notice = "Suite changed: previous report archived; starting with no observations.";
        return;
      }
      results = root.getJSONObject("results");
      groupIndex = root.optInt("groupIndex", 0);
      selectedIndex = root.optInt("selectedIndex", 0);
      runId = root.getString("runId");
      manualId = root.optString("manualId", null);
      activeId = root.optString("activeId", null);
      token = root.optString("token", null);
      started = root.optLong("startedElapsedMs", 0);
      JSONArray pending = root.optJSONArray("queue");
      if (pending != null)
        for (int i = 0; i < pending.length(); i++)
          if (find(pending.getString(i)) != null)
            queue.add(pending.getString(i));
      if (activeId != null && (find(activeId) == null || token == null))
        throw new IllegalStateException("Invalid pending case");
      notice = "Restored saved results. Unlisted cases have not run.";
    } catch (Throwable e) {
      notice = "Cannot restore report: " + e;
      results = new JSONObject();
      activeId = null;
      manualId = null;
      queue.clear();
    }
  }
  private void export() {
    if (!persist()) {
      refresh();
      return;
    }
    try {
      File directory = getExternalFilesDir(null);
      if (directory == null)
        throw new IllegalStateException("External app directory unavailable");
      File target = new File(directory, "api-probe-report.json");
      ProbeFiles.write(target, ProbeFiles.read(report));
      notice = "Saved JSON: " + target.getAbsolutePath();
      Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                          .setType("application/json")
                          .addCategory(Intent.CATEGORY_OPENABLE)
                          .putExtra(Intent.EXTRA_TITLE, "api-probe-report.json");
      startActivityForResult(intent, 84);
    } catch (Throwable e) {
      notice += "\nDocument export unavailable: " + e;
    }
    refresh();
  }
  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 84 && result == RESULT_OK && data != null && data.getData() != null) {
      try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
        if (out == null)
          throw new java.io.IOException("No output stream");
        out.write(ProbeFiles.read(report).toString(2).getBytes("UTF-8"));
        notice = "Exported JSON report.";
      } catch (Throwable e) {
        notice = "Export failed: " + e;
      }
    } else if (request == 85)
      notice = "Child Activity result: "
          + (result == RESULT_OK && data != null && data.getIntExtra("answer", 0) == 42
                  ? "correct payload 42"
                  : "missing/wrong payload");
    refresh();
  }
  private void appendResult(StringBuilder text, ProbeCase test) {
    JSONObject result = results.optJSONObject(test.id);
    text.append(test.id)
        .append(": ")
        .append(result.optString("status"))
        .append(" (")
        .append(result.optLong("durationMs"))
        .append(" ms)\n")
        .append(result.optString("observed"))
        .append("\n\n");
  }
  private void setTextIfChanged(TextView view, String value) {
    if (!value.contentEquals(view.getText()))
      view.setText(value);
  }
  private void refresh() {
    if (output == null)
      return;
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String s : new String[] {"PASS", "FAIL", "UNSUPPORTED", "SKIPPED", "TIMEOUT"})
      counts.put(s, 0);
    StringBuilder text = new StringBuilder();
    ProbeCase selected = selected();
    if (selected != null)
      setTextIfChanged(caseLabel,
          (selectedIndex + 1) + "/" + visible.size() + " · " + selected.id + " [" + selected.mode
              + "]");
    if (selected != null)
      text.append("Selected: ")
          .append(selected.id)
          .append("\nExpected: ")
          .append(selected.expected)
          .append("\n\n");
    if (manualId != null)
      text.append("AWAITING MANUAL OBSERVATION: ").append(manualId).append("\n");
    text.append(notice).append("\n\n");
    List<ProbeCase> history = new ArrayList<>();
    for (ProbeCase test : ProbeCatalog.CASES) {
      JSONObject result = results.optJSONObject(test.id);
      if (result != null) {
        history.add(test);
        String status = result.optString("status");
        if (counts.containsKey(status))
          counts.put(status, counts.get(status) + 1);
      }
    }
    if (selected != null && results.optJSONObject(selected.id) != null) {
      text.append("Selected result:\n");
      appendResult(text, selected);
    }
    Collections.sort(history,
        (left, right)
            -> Long.compare(results.optJSONObject(right.id).optLong("timestampMs"),
                results.optJSONObject(left.id).optLong("timestampMs")));
    text.append("Recent results (up to 12 of ")
        .append(history.size())
        .append("). Select any case to inspect its result; export includes every result.\n\n");
    for (int i = 0; i < Math.min(12, history.size()); i++) {
      ProbeCase test = history.get(i);
      if (selected == null || !selected.id.equals(test.id))
        appendResult(text, test);
    }
    android.content.res.Configuration config = getResources().getConfiguration();
    setTextIfChanged(summary,
        ProbeCatalog.CASES.length + " cases · " + results.length() + " recorded\n" + counts + "\n"
            + (activeId == null ? "Idle" : "Running: " + activeId) + " · queued " + queue.size()
            + "\n" + config.screenWidthDp + " × " + config.screenHeightDp
            + " dp; orientation=" + config.orientation);
    setTextIfChanged(output, text.toString());
  }
  @Override
  protected void onDestroy() {
    destroyed = true;
    handler.removeCallbacksAndMessages(null);
    persist();
    super.onDestroy();
  }
  @Override
  protected void onSaveInstanceState(Bundle state) {
    persist();
    super.onSaveInstanceState(state);
  }
}
