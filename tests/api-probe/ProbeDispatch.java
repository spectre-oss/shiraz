// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;
import java.io.*;

// Assertions are separate from the catalog; missing cases fail explicitly.
final class ProbeDispatch extends ProbeChecks {
  static String execute(android.content.Context c, String id) throws Exception {
    switch (id) {
      case "runtime.reflection": {
        Class<?> type = String.class;
        eq(type.getMethod("substring", int.class).invoke("probe", 1), "robe");
        check(Integer.class.getField("MAX_VALUE").getInt(null) > 0);
        return "Reflection invokes a method and accesses a field";
      }
      case "runtime.exceptions": {
        Exception cause = new Exception("cause");
        Exception outer = new Exception("outer", cause);
        check(outer.getCause() == cause);
        return "Thrown exception retains cause";
      }
      case "runtime.thread": {
        java.util.concurrent.atomic.AtomicInteger n =
            new java.util.concurrent.atomic.AtomicInteger();
        Thread t = new Thread(() -> n.set(42));
        t.start();
        t.join(1000);
        check(!t.isAlive() && n.get() == 42);
        return "Thread starts, joins and publishes state";
      }
      case "runtime.executor": {
        java.util.concurrent.ExecutorService e =
            java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
          check(e.submit(() -> 42).get(1, java.util.concurrent.TimeUnit.SECONDS) == 42);
        } finally {
          e.shutdownNow();
        }
        return "Executor Future returns expected value";
      }
      case "runtime.classloader": {
        eq(c.getClassLoader().loadClass("org.shiraz.apiprobe.ProbeChecks").getName(),
            "org.shiraz.apiprobe.ProbeChecks");
        return "App class loads through its classloader";
      }
      case "runtime.bytebuffer": {
        java.nio.ByteBuffer b =
            java.nio.ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(42);
        b.flip();
        check(b.getInt() == 42);
        return "Direct buffer preserves little-endian integer";
      }
      case "runtime.utf8": {
        String s = "سلام 🌍";
        eq(new String(s.getBytes("UTF-8"), "UTF-8"), s);
        return "Persian and emoji survive UTF-8 round trip";
      }
      case "runtime.zip": {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(b)) {
          z.putNextEntry(new java.util.zip.ZipEntry("probe"));
          z.write(42);
          z.closeEntry();
        }
        try (java.util.zip.ZipInputStream z =
                 new java.util.zip.ZipInputStream(new ByteArrayInputStream(b.toByteArray()))) {
          eq(z.getNextEntry().getName(), "probe");
          check(z.read() == 42 && z.read() == -1);
        }
        return "ZIP entry round trip preserves bytes";
      }
      case "runtime.regex": {
        check(java.util.regex.Pattern.compile("سلام").matcher("xسلامx").find());
        return "Unicode regex finds expected text";
      }
      case "runtime.json": {
        org.json.JSONObject j = new org.json.JSONObject().put("n", 42).put("b", true);
        check(j.getInt("n") == 42 && j.getBoolean("b"));
        return "JSON round trip preserves typed values";
      }
      case "runtime.clock": {
        long t = android.os.SystemClock.elapsedRealtime();
        android.os.SystemClock.sleep(20);
        check(android.os.SystemClock.elapsedRealtime() >= t + 10
            && android.os.SystemClock.uptimeMillis() > 0);
        return "Monotonic clocks advance after sleep";
      }
      case "runtime.process": {
        check(android.os.Process.myPid() > 0 && android.os.Process.myTid() > 0
            && android.os.Process.myUid() >= 0);
        return "Process identity is positive";
      }
      case "runtime.handler": {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean ok =
            new java.util.concurrent.atomic.AtomicBoolean();
        check(new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
          ok.set(android.os.Looper.myLooper() == android.os.Looper.getMainLooper());
          latch.countDown();
        }));
        check(latch.await(2, java.util.concurrent.TimeUnit.SECONDS) && ok.get());
        return "Main Handler delivers callback on main looper";
      }
      case "runtime.handler_thread": {
        android.os.HandlerThread t = new android.os.HandlerThread("probe-looper");
        t.start();
        try {
          java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
          check(new android.os.Handler(t.getLooper()).post(latch::countDown));
          check(latch.await(2, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
          t.quitSafely();
          t.join(1000);
        }
        return "HandlerThread posts and delivers a callback";
      }
      case "ipc.bundle": {
        android.os.Bundle b = new android.os.Bundle();
        b.putInt("n", 42);
        b.putString("s", "سلام");
        android.os.Bundle nested = new android.os.Bundle();
        nested.putBoolean("b", true);
        b.putBundle("nested", nested);
        check(b.getInt("n") == 42 && b.getBundle("nested").getBoolean("b"));
        eq(b.getString("s"), "سلام");
        return "Bundle retains primitive and nested values";
      }
      case "ipc.parcel": {
        android.os.Parcel p = android.os.Parcel.obtain();
        try {
          p.writeInt(42);
          p.writeString("سلام");
          p.setDataPosition(0);
          check(p.readInt() == 42);
          eq(p.readString(), "سلام");
        } finally {
          p.recycle();
        }
        return "Parcel marshals integer and Unicode string";
      }
      case "ipc.intent": {
        android.content.Intent i = new android.content.Intent(c, RoundTripActivity.class)
                                       .setAction("probe.ACTION")
                                       .setData(android.net.Uri.parse("probe://host/path"))
                                       .putExtra("n", 42);
        check(i.getIntExtra("n", 0) == 42);
        eq(i.getAction(), "probe.ACTION");
        eq(i.getData().getPath(), "/path");
        check(i.getComponent() != null);
        return "Intent retains component action data and extras";
      }
      case "ipc.binder_local": {
        android.os.Binder b = new android.os.Binder() {
          protected boolean onTransact(
              int code, android.os.Parcel data, android.os.Parcel reply, int flags) {
            reply.writeInt(data.readInt() + 1);
            return true;
          }
        };
        android.os.Parcel in = android.os.Parcel.obtain(), out = android.os.Parcel.obtain();
        try {
          in.writeInt(41);
          check(b.transact(1, in, out, 0));
          check(out.readInt() == 42);
        } finally {
          in.recycle();
          out.recycle();
        }
        return "Local Binder transaction returns expected payload";
      }
      case "ipc.messenger": {
        java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
        android.os.Messenger m = new android.os.Messenger(
            new android.os.Handler(android.os.Looper.getMainLooper(), msg -> {
              if (msg.what == 42)
                l.countDown();
              return true;
            }));
        m.send(android.os.Message.obtain(null, 42));
        check(l.await(2, java.util.concurrent.TimeUnit.SECONDS));
        return "Messenger delivers message through Handler";
      }
      case "ipc.broadcast": {
        java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
        String action = c.getPackageName() + ".PROBE";
        android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
          public void onReceive(android.content.Context x, android.content.Intent i) {
            l.countDown();
          }
        };
        c.registerReceiver(r, new android.content.IntentFilter(action));
        try {
          c.sendBroadcast(new android.content.Intent(action).setPackage(c.getPackageName()));
          check(l.await(2, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
          c.unregisterReceiver(r);
        }
        return "Registered receiver receives app-scoped broadcast";
      }
      case "resources.strings": {
        eq(c.getString(R.string.probe_text), "Shiraz resource ✓");
        return "Compiled string resolves Unicode";
      }
      case "resources.typed_values": {
        check(c.getResources().getInteger(R.integer.probe_number) == 42);
        check(c.getResources().getBoolean(R.bool.probe_bool));
        check(c.getResources().getColor(R.color.probe_color) == 0xff123456);
        check(c.getResources().getDimension(R.dimen.probe_size) > 0);
        return "Integer boolean color and dimension resolve";
      }
      case "resources.array": {
        String[] a = c.getResources().getStringArray(R.array.probe_array);
        check(a.length == 2);
        eq(a[1], "بتا");
        return "Compiled string array preserves values";
      }
      case "resources.identifier": {
        check(c.getResources().getIdentifier("probe_text", "string", c.getPackageName())
            == R.string.probe_text);
        return "Resource lookup resolves declared name";
      }
      case "resources.metrics": {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        check(m.widthPixels > 0 && m.heightPixels > 0 && m.density > 0 && m.densityDpi > 0);
        return "Display density and size are positive";
      }
      case "resources.configuration": {
        android.content.res.Configuration q = c.getResources().getConfiguration();
        check(q.screenWidthDp > 0 && !q.getLocales().isEmpty());
        return "Configuration exposes width and locale";
      }
      case "resources.locale_context": {
        android.content.res.Configuration q =
            new android.content.res.Configuration(c.getResources().getConfiguration());
        q.setLocale(new java.util.Locale("fa"));
        android.content.Context x = c.createConfigurationContext(q);
        eq(x.getResources().getConfiguration().getLocales().get(0).getLanguage(), "fa");
        return "Configuration context retains requested locale";
      }
      case "resources.assets": {
        try (InputStream in = c.getAssets().open("probe.txt")) {
          eq(read(in), "Shiraz asset round trip\n");
        }
        return "Asset contents match packaged fixture";
      }
      case "resources.asset_list": {
        check(java.util.Arrays.asList(c.getAssets().list("")).contains("probe.txt"));
        return "Asset listing includes fixture";
      }
      case "resources.xml": {
        android.content.res.XmlResourceParser p = c.getResources().getLayout(R.layout.probe);
        boolean found = false;
        try {
          while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if ("TextView".equals(p.getName()))
              found = true;
          }
        } finally {
          p.close();
        }
        check(found);
        return "Compiled layout XML exposes TextView tag";
      }
      case "resources.drawable": {
        android.graphics.drawable.Drawable d = c.getResources().getDrawable(R.drawable.probe_shape);
        android.graphics.Bitmap b = bitmap();
        try {
          d.setBounds(0, 0, 16, 16);
          d.draw(new android.graphics.Canvas(b));
          check(b.getPixel(8, 8) == 0xff123456);
        } finally {
          b.recycle();
        }
        return "XML drawable produces expected pixels";
      }
      case "storage.files": {
        String name = "probe-roundtrip";
        try (FileOutputStream f = c.openFileOutput(name, 0)) {
          f.write(new byte[] {7, 19, 42});
          f.getFD().sync();
        }
        try (InputStream f = c.openFileInput(name)) {
          check(f.read() == 7 && f.read() == 19 && f.read() == 42 && f.read() == -1);
        }
        check(c.deleteFile(name));
        return "Private file round trip and deletion succeed";
      }
      case "storage.directories": {
        for (File d : new File[] {c.getFilesDir(), c.getCacheDir(), c.getDir("probe", 0)}) {
          check(d.isDirectory());
          File f = File.createTempFile("probe", ".tmp", d);
          check(f.delete());
        }
        return "Private data/cache directories are writable";
      }
      case "storage.preferences": {
        android.content.SharedPreferences p = c.getSharedPreferences("probe-values", 0);
        check(p.edit()
                .clear()
                .putInt("n", 42)
                .putLong("l", 12345678901L)
                .putFloat("f", 1.5f)
                .putBoolean("b", true)
                .putString("s", "سلام")
                .commit());
        p = c.getSharedPreferences("probe-values", 0);
        check(p.getInt("n", 0) == 42 && p.getLong("l", 0) == 12345678901L
            && p.getFloat("f", 0) == 1.5f && p.getBoolean("b", false));
        eq(p.getString("s", null), "سلام");
        return "Preferences commit and reopen preserve all types";
      }
      case "storage.preference_listener": {
        java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
        android.content.SharedPreferences p = c.getSharedPreferences("probe-listener", 0);
        android.content.SharedPreferences.OnSharedPreferenceChangeListener listener =
            (prefs, key) -> {
          if ("n".equals(key))
            l.countDown();
        };
        p.registerOnSharedPreferenceChangeListener(listener);
        try {
          check(p.edit().putLong("n", System.nanoTime()).commit());
          check(l.await(2, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
          p.unregisterOnSharedPreferenceChangeListener(listener);
        }
        return "Preference listener observes committed key";
      }
      case "storage.random_access": {
        File f = new File(c.getCacheDir(), "probe-random");
        try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "rw")) {
          r.setLength(0);
          r.writeInt(42);
          r.seek(0);
          check(r.readInt() == 42);
        } finally {
          f.delete();
        }
        return "RandomAccessFile seek updates exact byte";
      }
      case "storage.file_descriptor": {
        File f = new File(c.getCacheDir(), "probe-fd");
        try (android.os.ParcelFileDescriptor p = android.os.ParcelFileDescriptor.open(f,
                 android.os.ParcelFileDescriptor.MODE_CREATE
                     | android.os.ParcelFileDescriptor.MODE_READ_WRITE)) {
          check(p.getFileDescriptor().valid());
        } finally {
          f.delete();
        }
        return "ParcelFileDescriptor preserves writable file";
      }
      case "storage.statfs": {
        android.os.StatFs s = new android.os.StatFs(c.getFilesDir().getPath());
        check(s.getBlockSizeLong() > 0 && s.getTotalBytes() > 0 && s.getAvailableBytes() >= 0);
        return "StatFs reports positive block size and total bytes";
      }
      case "database.query": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          db.execSQL("CREATE TABLE probe (n INTEGER,s TEXT)");
          db.execSQL("INSERT INTO probe VALUES (?,?)", new Object[] {42, "سلام"});
          try (android.database.Cursor r =
                   db.rawQuery("SELECT n,s FROM probe WHERE n=?", new String[] {"42"})) {
            check(r.moveToFirst() && r.getInt(0) == 42);
            eq(r.getString(1), "سلام");
            check(!r.moveToNext());
          }
        } finally {
          db.close();
        }
        return "Bound SQLite query returns expected Unicode row";
      }
      case "database.rollback": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          db.execSQL("CREATE TABLE probe (n INTEGER)");
          db.beginTransaction();
          try {
            db.execSQL("INSERT INTO probe VALUES (42)");
          } finally {
            db.endTransaction();
          }
          try (android.database.Cursor r = db.rawQuery("SELECT count(*) FROM probe", null)) {
            check(r.moveToFirst() && r.getInt(0) == 0);
          }
        } finally {
          db.close();
        }
        return "Uncommitted SQLite transaction rolls back";
      }
      case "database.statement": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          android.database.sqlite.SQLiteStatement s = db.compileStatement("SELECT ? + 1");
          try {
            s.bindLong(1, 41);
            check(s.simpleQueryForLong() == 42);
          } finally {
            s.close();
          }
        } finally {
          db.close();
        }
        return "Compiled statement binds and queries integer";
      }
      case "database.blob": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          db.execSQL("CREATE TABLE probe (b BLOB)");
          android.content.ContentValues v = new android.content.ContentValues();
          v.put("b", new byte[] {1, 2, 3});
          check(db.insertOrThrow("probe", null, v) > 0);
          try (android.database.Cursor r =
                   db.query("probe", new String[] {"b"}, null, null, null, null, null)) {
            check(r.moveToFirst() && java.util.Arrays.equals(r.getBlob(0), new byte[] {1, 2, 3}));
          }
        } finally {
          db.close();
        }
        return "SQLite blob survives insert and retrieval";
      }
      case "database.matrix_cursor": {
        try (android.database.MatrixCursor r =
                 new android.database.MatrixCursor(new String[] {"id", "name"})) {
          r.addRow(new Object[] {42, "probe"});
          check(r.moveToFirst() && r.getInt(r.getColumnIndexOrThrow("id")) == 42);
          eq(r.getString(1), "probe");
        }
        return "MatrixCursor resolves column and row";
      }
      case "graphics.canvas_color": {
        android.graphics.Bitmap b = bitmap();
        try {
          new android.graphics.Canvas(b).drawColor(android.graphics.Color.RED);
          check(b.getPixel(8, 8) == android.graphics.Color.RED);
        } finally {
          b.recycle();
        }
        return "Canvas color fills bitmap";
      }
      case "graphics.rect_clip": {
        android.graphics.Bitmap b = bitmap();
        try {
          android.graphics.Canvas canvas = new android.graphics.Canvas(b);
          canvas.clipRect(4, 4, 12, 12);
          canvas.drawColor(android.graphics.Color.BLUE);
          check(b.getPixel(8, 8) == android.graphics.Color.BLUE && b.getPixel(0, 0) == 0);
        } finally {
          b.recycle();
        }
        return "Canvas clipping preserves outside pixels";
      }
      case "graphics.path": {
        android.graphics.Bitmap b = bitmap();
        try {
          android.graphics.Path p = new android.graphics.Path();
          p.addRect(2, 2, 14, 14, android.graphics.Path.Direction.CW);
          android.graphics.Paint paint = new android.graphics.Paint();
          paint.setColor(android.graphics.Color.GREEN);
          new android.graphics.Canvas(b).drawPath(p, paint);
          check(b.getPixel(8, 8) == android.graphics.Color.GREEN);
        } finally {
          b.recycle();
        }
        return "Filled Path covers interior pixel";
      }
      case "graphics.matrix": {
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setTranslate(3, 4);
        float[] p = {1, 2};
        m.mapPoints(p);
        check(p[0] == 4 && p[1] == 6);
        return "Matrix translation maps coordinates";
      }
      case "graphics.png": {
        android.graphics.Bitmap b = bitmap();
        android.graphics.Bitmap decoded = null;
        try {
          b.eraseColor(0xff123456);
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          check(b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes));
          byte[] data = bytes.toByteArray();
          decoded = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
          check(
              decoded != null && decoded.getWidth() == 16 && decoded.getPixel(8, 8) == 0xff123456);
        } finally {
          b.recycle();
          if (decoded != null)
            decoded.recycle();
        }
        return "Bitmap PNG encode/decode preserves pixels";
      }
      case "graphics.text_metrics": {
        android.graphics.Paint p = new android.graphics.Paint();
        p.setTypeface(android.graphics.Typeface.DEFAULT);
        p.setTextSize(24);
        check(p.measureText("Shiraz سلام") > 0
            && p.getFontMetrics().descent > p.getFontMetrics().ascent);
        return "Typeface and Paint expose positive text metrics";
      }
      case "graphics.text_pixels": {
        android.graphics.Bitmap b =
            android.graphics.Bitmap.createBitmap(160, 40, android.graphics.Bitmap.Config.ARGB_8888);
        try {
          android.graphics.Paint p = new android.graphics.Paint();
          p.setColor(0xffffffff);
          p.setTextSize(24);
          new android.graphics.Canvas(b).drawText("Test", 2, 28, p);
          int[] pixels = new int[6400];
          b.getPixels(pixels, 0, 160, 0, 0, 160, 40);
          boolean found = false;
          for (int v : pixels)
            if (v != 0)
              found = true;
          check(found);
        } finally {
          b.recycle();
        }
        return "Canvas text rasterization changes pixels";
      }
      case "graphics.gradient": {
        android.graphics.Bitmap b = bitmap();
        try {
          android.graphics.Paint p = new android.graphics.Paint();
          p.setShader(new android.graphics.LinearGradient(
              0, 0, 16, 0, 0xffff0000, 0xff0000ff, android.graphics.Shader.TileMode.CLAMP));
          new android.graphics.Canvas(b).drawRect(0, 0, 16, 16, p);
          check(b.getPixel(1, 8) != b.getPixel(14, 8));
        } finally {
          b.recycle();
        }
        return "LinearGradient produces different endpoint colors";
      }
      case "graphics.region": {
        android.graphics.Region r = new android.graphics.Region(0, 0, 16, 16);
        r.op(new android.graphics.Rect(4, 4, 12, 12), android.graphics.Region.Op.DIFFERENCE);
        check(r.contains(1, 1) && !r.contains(8, 8));
        return "Region difference excludes removed rectangle";
      }
      case "graphics.egl_gles": {
        egl();
        return "EGL pbuffer clears and reads back red pixel";
      }
      case "ui.inflate": {
        ui(c, () -> {
          android.view.View v = android.view.LayoutInflater.from(c).inflate(R.layout.probe, null);
          android.widget.TextView t = (android.widget.TextView) v.findViewById(R.id.probe_label);
          eq(t.getText().toString(), "Shiraz resource ✓");
        });
        return "LayoutInflater creates declared TextView";
      }
      case "ui.measure_layout": {
        ui(c, () -> {
          android.widget.LinearLayout l = new android.widget.LinearLayout(c);
          android.view.View v = new android.view.View(c);
          l.addView(v, new android.widget.LinearLayout.LayoutParams(40, 30));
          int s = android.view.View.MeasureSpec.makeMeasureSpec(
              100, android.view.View.MeasureSpec.EXACTLY);
          l.measure(s, s);
          l.layout(0, 0, 100, 100);
          check(v.getWidth() == 40 && v.getHeight() == 30);
        });
        return "LinearLayout measures and positions child";
      }
      case "ui.click": {
        ui(c, () -> {
          android.widget.Button b = new android.widget.Button(c);
          boolean[] hit = {false};
          b.setOnClickListener(v -> hit[0] = true);
          check(b.performClick() && hit[0]);
        });
        return "Button performClick dispatches listener";
      }
      case "ui.edit_text": {
        ui(c, () -> {
          android.widget.EditText e = new android.widget.EditText(c);
          e.setText("hello");
          e.setSelection(1, 4);
          e.getText().replace(e.getSelectionStart(), e.getSelectionEnd(), "i");
          eq(e.getText().toString(), "hio");
        });
        return "EditText selection replacement changes text";
      }
      case "ui.checkbox": {
        ui(c, () -> {
          android.widget.CheckBox b = new android.widget.CheckBox(c);
          boolean[] hit = {false};
          b.setOnCheckedChangeListener((button, checked) -> hit[0] = checked);
          b.setChecked(true);
          check(b.isChecked() && hit[0]);
        });
        return "CheckBox toggles and dispatches checked callback";
      }
      case "ui.motion_event": {
        android.view.MotionEvent e =
            android.view.MotionEvent.obtain(1, 2, android.view.MotionEvent.ACTION_DOWN, 12, 23, 0);
        try {
          check(e.getActionMasked() == 0 && e.getX() == 12 && e.getY() == 23);
        } finally {
          e.recycle();
        }
        return "MotionEvent retains coordinates and action";
      }
      case "ui.key_event": {
        android.view.KeyEvent e = new android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_A);
        check(e.getKeyCode() == android.view.KeyEvent.KEYCODE_A && e.getAction() == 0);
        return "KeyEvent retains action and key code";
      }
      case "ui.velocity": {
        android.view.VelocityTracker v = android.view.VelocityTracker.obtain();
        try {
          for (int i = 0; i < 5; i++) {
            android.view.MotionEvent e =
                android.view.MotionEvent.obtain(100, 100 + i * 20, i == 0 ? 0 : 2, i * 10, 0, 0);
            v.addMovement(e);
            e.recycle();
          }
          v.computeCurrentVelocity(1000);
          check(v.getXVelocity() > 0);
        } finally {
          v.recycle();
        }
        return "VelocityTracker estimates moving pointer";
      }
      case "ui.spans": {
        android.text.SpannableString s = new android.text.SpannableString("probe");
        android.text.style.StyleSpan span =
            new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);
        s.setSpan(span, 1, 4, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        check(s.getSpanStart(span) == 1 && s.getSpanEnd(span) == 4);
        return "Spannable retains style span range";
      }
      case "ui.text_layout": {
        android.text.TextPaint p = new android.text.TextPaint();
        p.setTextSize(20);
        android.text.StaticLayout l =
            android.text.StaticLayout.Builder.obtain("one two three four five", 0, 23, p, 50)
                .build();
        check(l.getLineCount() > 1 && l.getHeight() > 0);
        return "StaticLayout wraps long text into multiple lines";
      }
      case "ui.rtl": {
        check(android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl("سلام", 0, 4));
        return "RTL heuristic recognizes Persian";
      }
      case "ui.view_state": {
        ui(c, () -> {
          android.widget.CheckBox b = new android.widget.CheckBox(c);
          b.setId(123);
          b.setChecked(true);
          android.util.SparseArray<android.os.Parcelable> state = new android.util.SparseArray<>();
          b.saveHierarchyState(state);
          b.setChecked(false);
          b.restoreHierarchyState(state);
          check(b.isChecked());
        });
        return "View hierarchy stores and restores checked state";
      }
      case "ui.scroller": {
        android.widget.Scroller s = new android.widget.Scroller(c);
        s.startScroll(0, 0, 100, 50, 1);
        android.os.SystemClock.sleep(20);
        s.computeScrollOffset();
        check(s.getCurrX() == 100 && s.getCurrY() == 50);
        return "Scroller reaches final scroll position";
      }
      case "ui.choreographer": {
        java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
        new android.os.Handler(android.os.Looper.getMainLooper())
            .post(()
                      -> android.view.Choreographer.getInstance().postFrameCallback(
                          time -> l.countDown()));
        check(l.await(2, java.util.concurrent.TimeUnit.SECONDS));
        return "Choreographer delivers frame callback";
      }
      case "packages.self": {
        android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(
            c.getPackageName(), android.content.pm.PackageManager.GET_ACTIVITIES);
        eq(p.packageName, c.getPackageName());
        check(p.activities != null && p.activities.length >= 2 && p.versionName != null);
        return "PackageManager resolves own package version and activities";
      }
      case "packages.launch_intent": {
        android.content.Intent i =
            c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        check(i != null && i.getComponent() != null);
        return "PackageManager returns own launch intent";
      }
      case "packages.permission": {
        check(c.checkSelfPermission(android.Manifest.permission.INTERNET)
            == android.content.pm.PackageManager.PERMISSION_GRANTED);
        return "Declared Internet permission is granted";
      }
      case "packages.service_manifest": {
        android.content.pm.ServiceInfo s = c.getPackageManager().getServiceInfo(
            new android.content.ComponentName(c, ProbeService.class), 0);
        eq(s.processName, c.getPackageName() + ":probe_worker");
        check(!s.exported);
        return "Manifest retains remote worker process name";
      }
      case "system.activity_manager": {
        android.app.ActivityManager m = (android.app.ActivityManager) c.getSystemService(
            android.content.Context.ACTIVITY_SERVICE);
        check(m != null && m.getMemoryClass() > 0);
        return "ActivityManager returns positive memory class";
      }
      case "system.power": {
        android.os.PowerManager p =
            (android.os.PowerManager) c.getSystemService(android.content.Context.POWER_SERVICE);
        check(p != null);
        android.os.PowerManager.WakeLock w =
            p.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "probe:check");
        check(!w.isHeld());
        return "PowerManager creates a non-held wake lock";
      }
      case "system.alarm": {
        check(c.getSystemService(android.content.Context.ALARM_SERVICE)
                  instanceof android.app.AlarmManager);
        return "AlarmManager service is available";
      }
      case "system.notification": {
        android.app.NotificationManager n = (android.app.NotificationManager) c.getSystemService(
            android.content.Context.NOTIFICATION_SERVICE);
        check(n != null);
        String channel = "probe";
        n.createNotificationChannel(new android.app.NotificationChannel(
            channel, "API probe", android.app.NotificationManager.IMPORTANCE_LOW));
        check(n.getNotificationChannel(channel) != null);
        n.deleteNotificationChannel(channel);
        return "Notification channel can be created and retrieved";
      }
      case "system.pending_intent": {
        android.app.PendingIntent p = android.app.PendingIntent.getActivity(c, 42,
            new android.content.Intent(c, RoundTripActivity.class),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                | android.app.PendingIntent.FLAG_IMMUTABLE);
        try {
          eq(p.getCreatorPackage(), c.getPackageName());
        } finally {
          p.cancel();
        }
        return "PendingIntent retains creator package";
      }
      case "system.content_resolver": {
        check(c.getContentResolver() != null);
        eq(android.content.ContentResolver.SCHEME_CONTENT, "content");
        return "ContentResolver is available and resolves the content scheme constant";
      }
      case "network.uri": {
        android.net.Uri u = new android.net.Uri.Builder()
                                .scheme("https")
                                .authority("example.invalid")
                                .appendPath("سلام")
                                .appendQueryParameter("q", "a b")
                                .build();
        eq(u.getLastPathSegment(), "سلام");
        eq(u.getQueryParameter("q"), "a b");
        return "URI encoding and query decoding preserve Unicode";
      }
      case "network.loopback": {
        try (java.net.ServerSocket server =
                 new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
          server.setSoTimeout(1500);
          java.util.concurrent.FutureTask<Integer> task =
              new java.util.concurrent.FutureTask<>(() -> {
                try (java.net.Socket peer = server.accept()) {
                  peer.setSoTimeout(1500);
                  return peer.getInputStream().read();
                }
              });
          new Thread(task).start();
          try (java.net.Socket client = new java.net.Socket()) {
            client.connect(
                new java.net.InetSocketAddress("127.0.0.1", server.getLocalPort()), 1500);
            client.getOutputStream().write(42);
            check(task.get(2, java.util.concurrent.TimeUnit.SECONDS) == 42);
          }
        }
        return "Local TCP socket exchanges byte without Internet";
      }
      case "network.dns_localhost":
      case "network.tcp_half_close":
      case "network.http_chunked":
      case "network.http_error_body":
      case "network.http_post":
      case "network.http_redirect":
      case "network.http_read_timeout":
      case "network.tls_socket_factory":
      case "network.trust_manager":
        return NetworkChecks.run(id);
      case "network.tls_engine_verified":
      case "network.tls_engine_untrusted":
      case "network.tls_engine_hostname":
      case "network.tls_handshake":
      case "network.tls_verified":
      case "network.tls_untrusted":
      case "network.tls_hostname":
        return TlsNetworkChecks.run(id, name -> c.getAssets().open(name));
      case "network.local_stream":
      case "network.local_timeout":
      case "network.local_rebind":
      case "network.local_close_read":
      case "network.local_close_accept":
        return LocalSocketChecks.run(id);
      case "network.tls_stream_clean":
      case "network.tls_stream_abrupt":
      case "network.tls_stream_timeout":
        return TlsStreamChecks.run(id,name -> c.getAssets().open(name));
      case "network.nio_tcp":
      case "network.nio_udp":
      case "network.nio_selector":
        return NioNetworkChecks.run(id);
      case "network.ipv6_tcp":
      case "network.ipv6_udp":
      case "network.ipv6_http":
      case "network.ipv6_tls":
        return Ipv6Checks.run(id,(android.net.ConnectivityManager)c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE),name -> c.getAssets().open(name));
      case "network.android_trust_chain":
        return TrustManagerChecks.run(name -> c.getAssets().open(name));
      case "network.network_xml":
        return NetworkXmlChecks.run(c.getResources(),c.getPackageName());
      case "network.network_cleartext_policy":
        return NetworkApiChecks.runPolicy((c.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0);
      case "network.network_https":
        return NetworkApiChecks.runTls((android.net.ConnectivityManager)c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE),
            name -> c.getAssets().open(name));
      case "network.network_identity":
      case "network.network_dns":
      case "network.network_socket":
      case "network.network_http":
      case "network.network_bind":
      case "network.network_unknown":
        return NetworkApiChecks.run(id, (android.net.ConnectivityManager)c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE));
      case "network.http": {
        http(false);
        return "HttpURLConnection reads local HTTP response";
      }
      case "network.tls_context": {
        javax.net.ssl.SSLContext s = javax.net.ssl.SSLContext.getInstance("TLS");
        s.init(null, null, null);
        javax.net.ssl.SSLEngine e = s.createSSLEngine("localhost", 443);
        e.setUseClientMode(true);
        check(e.getEnabledProtocols().length > 0 && e.getEnabledCipherSuites().length > 0);
        return "TLS context creates client SSLEngine";
      }
      case "network.https_handler": {
        java.net.URLConnection u = new java.net.URL("https://example.invalid/").openConnection();
        check(u instanceof javax.net.ssl.HttpsURLConnection);
        return "HTTPS URL creates an HTTPS connection object";
      }
      case "network.connectivity": {
        android.net.ConnectivityManager m = (android.net.ConnectivityManager) c.getSystemService(
            android.content.Context.CONNECTIVITY_SERVICE);
        check(m != null && m.getAllNetworks() != null);
        return "ConnectivityManager service responds to network enumeration";
      }
      case "crypto.sha256": {
        byte[] d =
            java.security.MessageDigest.getInstance("SHA-256").digest("abc".getBytes("UTF-8"));
        StringBuilder b = new StringBuilder();
        for (byte v : d) b.append(String.format(java.util.Locale.ROOT, "%02x", v & 255));
        eq(b.toString(), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        return "SHA-256 matches known abc digest";
      }
      case "crypto.aes": {
        javax.crypto.KeyGenerator g = javax.crypto.KeyGenerator.getInstance("AES");
        g.init(128);
        javax.crypto.SecretKey key = g.generateKey();
        javax.crypto.Cipher e = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        e.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = e.doFinal(new byte[] {1, 2, 3});
        javax.crypto.Cipher d = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        d.init(javax.crypto.Cipher.DECRYPT_MODE, key,
            new javax.crypto.spec.GCMParameterSpec(128, e.getIV()));
        check(java.util.Arrays.equals(d.doFinal(encrypted), new byte[] {1, 2, 3}));
        return "AES-GCM encrypt/decrypt round trip";
      }
      case "crypto.random": {
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        int bits = 0;
        for (byte v : b) bits |= v;
        check(bits != 0);
        return "SecureRandom fills requested buffer";
      }
      case "hardware.location_math": {
        android.location.Location a = new android.location.Location("probe"),
                                  b = new android.location.Location("probe");
        a.setLatitude(0);
        a.setLongitude(0);
        b.setLatitude(0);
        b.setLongitude(1);
        check(a.distanceTo(b) > 110000 && a.distanceTo(b) < 112000);
        return "Location computes plausible distance without permission";
      }
      case "hardware.sensors": {
        android.hardware.SensorManager m = (android.hardware.SensorManager) c.getSystemService(
            android.content.Context.SENSOR_SERVICE);
        check(m != null);
        if (m.getSensorList(android.hardware.Sensor.TYPE_ALL).isEmpty())
          throw new Skip("No sensors advertised; event delivery untested");
        return "SensorManager enumerates sensors; absence is skipped";
      }
      case "hardware.camera_inventory": {
        android.hardware.camera2.CameraManager m =
            (android.hardware.camera2.CameraManager) c.getSystemService(
                android.content.Context.CAMERA_SERVICE);
        check(m != null);
        if (m.getCameraIdList().length == 0)
          throw new Skip("No cameras advertised; capture untested");
        return "CameraManager enumerates advertised camera ids";
      }
      case "hardware.vibrator": {
        android.os.Vibrator v =
            (android.os.Vibrator) c.getSystemService(android.content.Context.VIBRATOR_SERVICE);
        check(v != null);
        if (!v.hasVibrator())
          throw new Skip("No vibrator advertised");
        return "Vibrator reports capability without vibrating";
      }
      case "media.codecs": {
        android.media.MediaCodecInfo[] codecs =
            new android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
                .getCodecInfos();
        if (codecs.length == 0)
          throw new Skip("No codecs advertised; decode untested");
        for (android.media.MediaCodecInfo i : codecs)
          check(i.getName() != null && i.getSupportedTypes().length > 0);
        return "MediaCodecList enumerates valid codec names";
      }
      case "media.audio_config": {
        check(android.media.AudioTrack.getMinBufferSize(44100,
                  android.media.AudioFormat.CHANNEL_OUT_MONO,
                  android.media.AudioFormat.ENCODING_PCM_16BIT)
            > 0);
        return "AudioTrack exposes valid PCM minimum buffer size";
      }
      case "media.audio_manager": {
        android.media.AudioManager m =
            (android.media.AudioManager) c.getSystemService(android.content.Context.AUDIO_SERVICE);
        check(m != null);
        int volume = m.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        check(
            volume >= 0 && volume <= m.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
        return "AudioManager exposes stream volume bounds";
      }
      case "native.jni": {
        NativeProbe.run(c, "jni");
        return "JNI arithmetic, Java string and direct-buffer round trips";
      }
      case "native.looper": {
        NativeProbe.run(c, "looper");
        return "NDK ALooper dispatches a readable pipe";
      }
      case "native.window": {
        NativeProbe.run(c, "window");
        return "ANativeWindow exposes Surface dimensions";
      }
      case "native.bitmap": {
        NativeProbe.run(c, "bitmap");
        return "AndroidBitmap locks and updates bitmap pixels";
      }
      case "graphics.bitmap_copy": {
        android.graphics.Bitmap b = bitmap(), copy = null;
        try {
          b.eraseColor(0xff123456);
          copy = b.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
          check(copy != null && copy.getWidth() == 16 && copy.getPixel(3, 3) == 0xff123456);
        } finally {
          b.recycle();
          if (copy != null)
            copy.recycle();
        }
        return "Bitmap copy preserves pixels and dimensions";
      }
      case "graphics.bitmap_scale": {
        android.graphics.Bitmap b = bitmap(), scaled = null;
        try {
          b.eraseColor(0xff123456);
          scaled = android.graphics.Bitmap.createScaledBitmap(b, 32, 24, false);
          check(scaled.getWidth() == 32 && scaled.getHeight() == 24
              && scaled.getPixel(10, 10) == 0xff123456);
        } finally {
          if (scaled != null && scaled != b)
            scaled.recycle();
          b.recycle();
        }
        return "Bitmap scaling produces requested dimensions";
      }
      case "graphics.bitmap_crop": {
        android.graphics.Bitmap b = bitmap(), crop = null;
        try {
          b.setPixel(4, 5, 0xff123456);
          crop = android.graphics.Bitmap.createBitmap(b, 4, 5, 2, 2);
          check(crop.getWidth() == 2 && crop.getPixel(0, 0) == 0xff123456);
        } finally {
          if (crop != null && crop != b)
            crop.recycle();
          b.recycle();
        }
        return "Bitmap cropping preserves selected pixel";
      }
      case "graphics.bitmap_buffer": {
        android.graphics.Bitmap b = bitmap(), copy = bitmap();
        try {
          b.eraseColor(0xff123456);
          java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(b.getByteCount());
          b.copyPixelsToBuffer(buf);
          buf.rewind();
          copy.copyPixelsFromBuffer(buf);
          check(copy.getPixel(2, 2) == 0xff123456);
        } finally {
          b.recycle();
          copy.recycle();
        }
        return "Bitmap pixel buffer copy round trip";
      }
      case "graphics.decode_bounds": {
        android.graphics.Bitmap b = bitmap();
        try {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
          byte[] bytes = out.toByteArray();
          android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
          o.inJustDecodeBounds = true;
          check(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o) == null
              && o.outWidth == 16 && o.outHeight == 16);
        } finally {
          b.recycle();
        }
        return "BitmapFactory bounds decode avoids bitmap allocation";
      }
      case "graphics.jpeg": {
        android.graphics.Bitmap b = bitmap(), d = null;
        try {
          b.eraseColor(0xffff0000);
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          check(b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out));
          byte[] bytes = out.toByteArray();
          d = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
          check(d != null && d.getWidth() == 16
              && android.graphics.Color.red(d.getPixel(8, 8)) > 200);
        } finally {
          b.recycle();
          if (d != null)
            d.recycle();
        }
        return "JPEG encode/decode returns correct dimensions";
      }
      case "graphics.canvas_save_restore": {
        android.graphics.Bitmap b = bitmap();
        try {
          android.graphics.Canvas canvas = new android.graphics.Canvas(b);
          android.graphics.Paint p = new android.graphics.Paint();
          p.setColor(0xffff0000);
          int save = canvas.save();
          canvas.translate(8, 8);
          canvas.restoreToCount(save);
          canvas.drawRect(0, 0, 4, 4, p);
          check(b.getPixel(1, 1) == 0xffff0000 && b.getPixel(9, 9) == 0);
        } finally {
          b.recycle();
        }
        return "Canvas save restore restores translation";
      }
      case "graphics.canvas_alpha": {
        android.graphics.Bitmap b = bitmap();
        try {
          b.eraseColor(0xff0000ff);
          android.graphics.Paint p = new android.graphics.Paint();
          p.setColor(0x80ff0000);
          new android.graphics.Canvas(b).drawRect(0, 0, 16, 16, p);
          int color = b.getPixel(8, 8);
          check(android.graphics.Color.red(color) >= 127
              && android.graphics.Color.blue(color) >= 126
              && android.graphics.Color.alpha(color) == 255);
        } finally {
          b.recycle();
        }
        return "Canvas alpha blends source over opaque destination";
      }
      case "graphics.canvas_circle": {
        android.graphics.Bitmap b = bitmap();
        try {
          android.graphics.Paint p = new android.graphics.Paint();
          p.setColor(0xffff0000);
          new android.graphics.Canvas(b).drawCircle(8, 8, 4, p);
          check(b.getPixel(8, 8) == 0xffff0000 && b.getPixel(0, 0) == 0);
        } finally {
          b.recycle();
        }
        return "Circle covers center and leaves corner empty";
      }
      case "graphics.path_measure": {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(0, 0);
        p.lineTo(3, 4);
        android.graphics.PathMeasure m = new android.graphics.PathMeasure(p, false);
        check(Math.abs(m.getLength() - 5) < 0.01f);
        return "PathMeasure reports straight path length";
      }
      case "graphics.matrix_inverse": {
        android.graphics.Matrix m = new android.graphics.Matrix(),
                                inv = new android.graphics.Matrix();
        m.setScale(2, 3);
        m.postTranslate(7, 8);
        check(m.invert(inv));
        float[] p = {4, 5};
        m.mapPoints(p);
        inv.mapPoints(p);
        check(Math.abs(p[0] - 4) < 0.001f && Math.abs(p[1] - 5) < 0.001f);
        return "Matrix inverse reverses scale and translation";
      }
      case "graphics.color_parse": {
        check(android.graphics.Color.parseColor("#123456") == 0xff123456);
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(0xffff0000, hsv);
        check(android.graphics.Color.HSVToColor(hsv) == 0xffff0000);
        return "Color parsing and HSV conversion retain red";
      }
      case "graphics.picture": {
        android.graphics.Picture p = new android.graphics.Picture();
        p.beginRecording(16, 16).drawColor(0xff123456);
        p.endRecording();
        android.graphics.Bitmap b = bitmap();
        try {
          p.draw(new android.graphics.Canvas(b));
          check(b.getPixel(8, 8) == 0xff123456);
        } finally {
          b.recycle();
        }
        return "Picture records and replays Canvas commands";
      }
      case "graphics.dash_effect": {
        android.graphics.Paint p = new android.graphics.Paint();
        android.graphics.DashPathEffect effect =
            new android.graphics.DashPathEffect(new float[] {2, 3}, 0);
        p.setPathEffect(effect);
        check(p.getPathEffect() == effect);
        return "DashPathEffect accepts intervals and Paint retains effect";
      }
      case "storage.mapped_file": {
        File f = new File(c.getCacheDir(), "probe-map");
        try {
          try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "rw")) {
            r.setLength(16);
            java.nio.MappedByteBuffer b =
                r.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, 16);
            b.putInt(0, 42);
            b.force();
          }
          try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r")) {
            check(r.readInt() == 42);
          }
        } finally {
          f.delete();
        }
        return "Mapped file writes survive force and reopen";
      }
      case "storage.file_lock": {
        File f = new File(c.getCacheDir(), "probe-lock");
        try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "rw");
            java.nio.channels.FileLock lock = r.getChannel().tryLock()) {
          check(lock != null && lock.isValid());
        } finally {
          f.delete();
        }
        return "FileChannel obtains exclusive file lock";
      }
      case "storage.atomic_file": {
        android.util.AtomicFile a =
            new android.util.AtomicFile(new File(c.getCacheDir(), "probe-atomic"));
        try {
          FileOutputStream out = a.startWrite();
          out.write(42);
          a.finishWrite(out);
          out = a.startWrite();
          out.write(99);
          a.failWrite(out);
          try (InputStream in = a.openRead()) {
            check(in.read() == 42);
          }
        } finally {
          a.delete();
        }
        return "AtomicFile commits then rolls back failed write";
      }
      case "storage.preferences_remove": {
        android.content.SharedPreferences p = c.getSharedPreferences("probe-remove", 0);
        check(p.edit().putInt("a", 1).putInt("b", 2).commit());
        check(p.edit().remove("a").commit());
        check(!p.contains("a") && p.contains("b"));
        check(p.edit().clear().commit() && p.getAll().isEmpty());
        return "Preferences remove and clear eliminate keys";
      }
      case "storage.preferences_set": {
        android.content.SharedPreferences p = c.getSharedPreferences("probe-set", 0);
        java.util.Set<String> s = new java.util.HashSet<>(java.util.Arrays.asList("one", "سلام"));
        check(p.edit().putStringSet("set", s).commit());
        eq(p.getStringSet("set", null), s);
        return "Preferences retain string set values";
      }
      case "storage.file_rename": {
        File a = new File(c.getCacheDir(), "probe-before"),
             b = new File(c.getCacheDir(), "probe-after");
        try {
          b.delete();
          try (FileOutputStream out = new FileOutputStream(a)) {
            out.write(42);
          }
          check(a.renameTo(b) && !a.exists());
          try (InputStream in = new FileInputStream(b)) {
            check(in.read() == 42);
          }
        } finally {
          a.delete();
          b.delete();
        }
        return "Rename preserves data and removes original pathname";
      }
      case "storage.os_stat": {
        File f = new File(c.getCacheDir(), "probe-stat");
        try {
          try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[17]);
          }
          check(android.system.Os.stat(f.getPath()).st_size == 17);
        } finally {
          f.delete();
        }
        return "android.system.Os stat reports file size";
      }
      case "storage.os_pipe": {
        java.io.FileDescriptor[] p = android.system.Os.pipe();
        try {
          byte[] b = {42};
          check(android.system.Os.write(p[1], b, 0, 1) == 1);
          b[0] = 0;
          check(android.system.Os.read(p[0], b, 0, 1) == 1 && b[0] == 42);
        } finally {
          android.system.Os.close(p[0]);
          android.system.Os.close(p[1]);
        }
        return "android.system.Os pipe exchanges bytes";
      }
      case "database.commit": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          db.execSQL("CREATE TABLE probe(n INTEGER)");
          db.beginTransaction();
          try {
            db.execSQL("INSERT INTO probe VALUES(42)");
            db.setTransactionSuccessful();
          } finally {
            db.endTransaction();
          }
          try (android.database.Cursor r = db.rawQuery("SELECT n FROM probe", null)) {
            check(r.moveToFirst() && r.getInt(0) == 42);
          }
        } finally {
          db.close();
        }
        return "Committed SQLite transaction persists row";
      }
      case "database.constraint": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          db.execSQL("CREATE TABLE probe(n INTEGER UNIQUE)");
          db.execSQL("INSERT INTO probe VALUES(42)");
          boolean rejected = false;
          try {
            db.execSQL("INSERT INTO probe VALUES(42)");
          } catch (android.database.sqlite.SQLiteConstraintException expected) {
            rejected = true;
          }
          check(rejected);
        } finally {
          db.close();
        }
        return "SQLite unique constraint rejects duplicate";
      }
      case "database.update_delete": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          db.execSQL("CREATE TABLE probe(n INTEGER)");
          db.execSQL("INSERT INTO probe VALUES(1)");
          android.content.ContentValues v = new android.content.ContentValues();
          v.put("n", 42);
          check(db.update("probe", v, "n=?", new String[] {"1"}) == 1);
          check(db.delete("probe", "n=?", new String[] {"42"}) == 1);
        } finally {
          db.close();
        }
        return "SQLite update and delete report affected rows";
      }
      case "database.reopen": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        String path = db.getPath();
        try {
          db.execSQL("CREATE TABLE probe(n INTEGER)");
          db.execSQL("INSERT INTO probe VALUES(42)");
        } finally {
          db.close();
        }
        db = android.database.sqlite.SQLiteDatabase.openDatabase(path, null, 0);
        try {
          try (android.database.Cursor r = db.rawQuery("SELECT n FROM probe", null)) {
            check(r.moveToFirst() && r.getInt(0) == 42);
          }
        } finally {
          db.close();
        }
        return "SQLite disk row survives close and reopen";
      }
      case "database.wal": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          check(db.enableWriteAheadLogging() && db.isWriteAheadLoggingEnabled());
          db.disableWriteAheadLogging();
          check(!db.isWriteAheadLoggingEnabled());
        } finally {
          db.close();
        }
        return "SQLite accepts WAL and exposes enabled state";
      }
      case "database.null": {
        android.database.sqlite.SQLiteDatabase db = database(c);
        try {
          try (android.database.Cursor r = db.rawQuery("SELECT NULL,''", null)) {
            check(r.moveToFirst() && r.isNull(0) && !r.isNull(1));
            eq(r.getString(1), "");
          }
        } finally {
          db.close();
        }
        return "Cursor distinguishes SQL NULL from empty string";
      }
      case "runtime.serialization": {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
          out.writeObject(new java.util.ArrayList<>(java.util.Arrays.asList("one", "سلام")));
        }
        try (ObjectInputStream in =
                 new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
          eq(in.readObject(), java.util.Arrays.asList("one", "سلام"));
        }
        return "Object stream round trip preserves ArrayList";
      }
      case "runtime.gzip": {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream out = new java.util.zip.GZIPOutputStream(bytes)) {
          out.write("probe".getBytes("UTF-8"));
        }
        try (InputStream in =
                 new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
          eq(read(in), "probe");
        }
        return "GZIP compression round trip preserves text";
      }
      case "runtime.crc32": {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update("123456789".getBytes("US-ASCII"));
        check(crc.getValue() == 0xcbf43926L);
        return "CRC32 matches canonical test vector";
      }
      case "runtime.proxy": {
        java.util.concurrent.Callable<?> proxy =
            (java.util.concurrent.Callable<?>) java.lang.reflect.Proxy.newProxyInstance(
                c.getClassLoader(), new Class<?>[] {java.util.concurrent.Callable.class},
                (obj, method, args) -> 42);
        eq(proxy.call(), 42);
        return "Dynamic Proxy dispatches interface method";
      }
      case "runtime.array_reflection": {
        Object array = java.lang.reflect.Array.newInstance(int.class, 3);
        java.lang.reflect.Array.setInt(array, 1, 42);
        check(java.lang.reflect.Array.getInt(array, 1) == 42
            && java.lang.reflect.Array.getLength(array) == 3);
        return "Reflective array preserves component type and value";
      }
      case "runtime.thread_local": {
        ThreadLocal<Integer> value = new ThreadLocal<>();
        value.set(1);
        java.util.concurrent.atomic.AtomicInteger observed =
            new java.util.concurrent.atomic.AtomicInteger();
        Thread t = new Thread(() -> {
          value.set(42);
          observed.set(value.get());
        });
        t.start();
        t.join(1000);
        check(!t.isAlive() && observed.get() == 42 && value.get() == 1);
        return "ThreadLocal values remain isolated across threads";
      }
      case "runtime.monitor": {
        Object lock = new Object();
        boolean[] ready = {false};
        Thread t = new Thread(() -> {
          synchronized (lock) {
            ready[0] = true;
            lock.notifyAll();
          }
        });
        synchronized (lock) {
          t.start();
          long end = System.currentTimeMillis() + 1000;
          while (!ready[0] && System.currentTimeMillis() < end) lock.wait(100);
          check(ready[0]);
        }
        t.join(1000);
        return "Object wait notify publishes condition";
      }
      case "runtime.scheduled_executor": {
        java.util.concurrent.ScheduledExecutorService e =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        try {
          check(e.schedule(() -> 42, 20, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .get(1, java.util.concurrent.TimeUnit.SECONDS)
              == 42);
        } finally {
          e.shutdownNow();
        }
        return "Scheduled executor invokes delayed task";
      }
      case "runtime.semaphore": {
        java.util.concurrent.Semaphore s = new java.util.concurrent.Semaphore(1);
        check(s.tryAcquire(1, java.util.concurrent.TimeUnit.SECONDS));
        check(!s.tryAcquire());
        s.release();
        check(s.availablePermits() == 1);
        return "Semaphore acquire release tracks permits";
      }
      case "runtime.interrupt": {
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean hit =
            new java.util.concurrent.atomic.AtomicBoolean();
        Thread t = new Thread(() -> {
          ready.countDown();
          try {
            Thread.sleep(5000);
          } catch (InterruptedException expected) {
            hit.set(true);
          }
        });
        t.start();
        check(ready.await(1, java.util.concurrent.TimeUnit.SECONDS));
        t.interrupt();
        t.join(1000);
        check(!t.isAlive() && hit.get());
        return "Thread interruption wakes sleeping thread";
      }
      case "runtime.locale_format": {
        java.text.NumberFormat f = java.text.NumberFormat.getInstance(java.util.Locale.GERMANY);
        eq(f.format(1234.5), "1.234,5");
        check(f.parse("1.234,5").doubleValue() == 1234.5);
        return "Locale-specific number formatting parses its output";
      }
      case "runtime.timezone": {
        java.util.Calendar q =
            new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"));
        q.setTimeInMillis(0);
        check(q.get(java.util.Calendar.YEAR) == 1970 && q.get(java.util.Calendar.HOUR_OF_DAY) == 0);
        return "UTC GregorianCalendar resolves epoch correctly";
      }
      case "runtime.base64": {
        eq(android.util.Base64.encodeToString(
               "probe".getBytes("UTF-8"), android.util.Base64.NO_WRAP),
            "cHJvYmU=");
        eq(new String(android.util.Base64.decode("cHJvYmU=", 0), "UTF-8"), "probe");
        return "Android Base64 known vector round trip";
      }
      case "runtime.xml_pull": {
        org.xmlpull.v1.XmlPullParser p = android.util.Xml.newPullParser();
        p.setInput(new java.io.StringReader("<probe n='42'>text</probe>"));
        check(p.next() == org.xmlpull.v1.XmlPullParser.START_TAG);
        eq(p.getAttributeValue(null, "n"), "42");
        eq(p.nextText(), "text");
        return "XmlPullParser reads attribute and text";
      }
      case "network.udp": {
        try (java.net.DatagramSocket receiver =
                 new java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1"));
            java.net.DatagramSocket sender = new java.net.DatagramSocket()) {
          receiver.setSoTimeout(1500);
          byte[] data = {42};
          sender.send(new java.net.DatagramPacket(
              data, 1, java.net.InetAddress.getByName("127.0.0.1"), receiver.getLocalPort()));
          byte[] got = new byte[8];
          java.net.DatagramPacket packet = new java.net.DatagramPacket(got, got.length);
          receiver.receive(packet);
          check(packet.getLength() == 1 && got[0] == 42);
        }
        return "Local UDP datagram preserves payload";
      }
      case "network.socket_timeout": {
        try (java.net.ServerSocket server =
                 new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
            java.net.Socket client = new java.net.Socket("127.0.0.1", server.getLocalPort());
            java.net.Socket peer = server.accept()) {
          client.setSoTimeout(50);
          boolean timeout = false;
          try {
            client.getInputStream().read();
          } catch (java.net.SocketTimeoutException expected) {
            timeout = true;
          }
          check(timeout);
        }
        return "TCP read deadline raises SocketTimeoutException";
      }
      case "network.inet_address": {
        check(java.net.InetAddress.getByName("127.0.0.1").isLoopbackAddress());
        check(java.net.InetAddress.getByName("::1").isLoopbackAddress());
        return "Numeric IPv4 and IPv6 addresses parse without DNS";
      }
      case "network.interfaces": {
        java.util.Enumeration<java.net.NetworkInterface> items =
            java.net.NetworkInterface.getNetworkInterfaces();
        check(items != null && items.hasMoreElements());
        return "NetworkInterface enumerates at least one interface";
      }
      case "network.url_encode": {
        String s = "سلام a+b";
        eq(java.net.URLDecoder.decode(java.net.URLEncoder.encode(s, "UTF-8"), "UTF-8"), s);
        return "URL form encoding round trip preserves Persian and spaces";
      }
      case "network.cookie": {
        java.net.CookieManager m =
            new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
        java.net.URI uri = new java.net.URI("https://example.invalid/path");
        m.put(uri,
            java.util.Collections.singletonMap(
                "Set-Cookie", java.util.Arrays.asList("probe=42; Path=/")));
        check(m.getCookieStore().getCookies().size() == 1
            && m.get(uri, java.util.Collections.emptyMap()).toString().contains("probe=42"));
        return "CookieManager accepts and returns matching HTTP cookie";
      }
      case "network.local_socket": {
        String name = "shiraz-probe-" + android.os.Process.myPid();
        try (android.net.LocalServerSocket server = new android.net.LocalServerSocket(name);
            android.net.LocalSocket client = new android.net.LocalSocket()) {
          client.connect(new android.net.LocalSocketAddress(name));
          try (android.net.LocalSocket peer = server.accept()) {
            client.getOutputStream().write(42);
            peer.setSoTimeout(1500);
            check(peer.getInputStream().read() == 42);
          }
        }
        return "Android LocalSocket exchanges byte through abstract namespace";
      }
      case "crypto.hmac": {
        javax.crypto.Mac m = javax.crypto.Mac.getInstance("HmacSHA256");
        m.init(new javax.crypto.spec.SecretKeySpec("key".getBytes("UTF-8"), "HmacSHA256"));
        byte[] bytes = m.doFinal("The quick brown fox jumps over the lazy dog".getBytes("UTF-8"));
        StringBuilder b = new StringBuilder();
        for (byte v : bytes) b.append(String.format(java.util.Locale.ROOT, "%02x", v & 255));
        eq(b.toString(), "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
        return "HmacSHA256 produces canonical known digest";
      }
      case "crypto.rsa_signature": {
        java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        java.security.KeyPair pair = g.generateKeyPair();
        java.security.Signature s = java.security.Signature.getInstance("SHA256withRSA");
        s.initSign(pair.getPrivate());
        s.update(new byte[] {42});
        byte[] sig = s.sign();
        s.initVerify(pair.getPublic());
        s.update(new byte[] {42});
        check(s.verify(sig));
        s.initVerify(pair.getPublic());
        s.update(new byte[] {43});
        check(!s.verify(sig));
        return "RSA signature verifies data and rejects changed payload";
      }
      case "ipc.parcel_bundle": {
        android.os.Parcel p = android.os.Parcel.obtain();
        try {
          android.os.Bundle b = new android.os.Bundle();
          b.putInt("n", 42);
          b.putString("s", "سلام");
          p.writeBundle(b);
          p.setDataPosition(0);
          android.os.Bundle got = p.readBundle(c.getClassLoader());
          check(got != null && got.getInt("n") == 42);
          eq(got.getString("s"), "سلام");
        } finally {
          p.recycle();
        }
        return "Parcel round trip preserves Bundle contents";
      }
      case "ipc.parcel_marshall": {
        android.os.Parcel p = android.os.Parcel.obtain(), q = android.os.Parcel.obtain();
        try {
          p.writeLong(12345678901L);
          byte[] data = p.marshall();
          q.unmarshall(data, 0, data.length);
          q.setDataPosition(0);
          check(q.readLong() == 12345678901L);
        } finally {
          p.recycle();
          q.recycle();
        }
        return "Parcel marshall unmarshall preserves data";
      }
      case "ipc.intent_parcel": {
        android.content.Intent i =
            new android.content.Intent(c, RoundTripActivity.class).putExtra("n", 42);
        android.os.Parcel p = android.os.Parcel.obtain();
        try {
          i.writeToParcel(p, 0);
          p.setDataPosition(0);
          android.content.Intent j = android.content.Intent.CREATOR.createFromParcel(p);
          eq(j.getComponent(), i.getComponent());
          check(j.getIntExtra("n", 0) == 42);
        } finally {
          p.recycle();
        }
        return "Intent parcel round trip preserves extras and component";
      }
      case "native.jni_array": {
        NativeProbe.run(c, "jni_array");
        return "JNI array region updates Java array";
      }
      case "native.jni_exception": {
        NativeProbe.run(c, "jni_exception");
        return "JNI-thrown exception reaches Java";
      }
      case "native.jni_callback": {
        NativeProbe.run(c, "jni_callback");
        return "JNI calls a Java static method";
      }
      case "native.pthread": {
        NativeProbe.run(c, "pthread");
        return "Native thread attaches to VM and calls Java";
      }
      case "native.shared_memory": {
        NativeProbe.run(c, "shared_memory");
        return "ASharedMemory maps shared bytes";
      }
      case "native.hardware_buffer": {
        NativeProbe.run(c, "hardware_buffer");
        return "AHardwareBuffer allocates describes and locks CPU buffer";
      }
      case "native.dlopen": {
        NativeProbe.run(c, "dlopen");
        return "Bionic dlopen and dlsym resolve strlen";
      }
      case "native.clock": {
        NativeProbe.run(c, "clock");
        return "Native CLOCK_MONOTONIC returns positive timestamp";
      }
      case "graphics.gles_shader": {
        egl("gles_shader");
        return "GLES compiles and links vertex and fragment shaders";
      }
      case "graphics.gles_texture": {
        egl("gles_texture");
        return "GLES framebuffer reads uploaded texture pixel";
      }
      case "graphics.gles_buffer": {
        egl("gles_buffer");
        return "GLES buffer reports uploaded byte size";
      }
      case "graphics.gles_scissor": {
        egl("gles_scissor");
        return "GLES scissor limits clear to selected pixels";
      }
      case "graphics.gles_blend": {
        egl("gles_blend");
        return "GLES reports enabled blending and the source blend factor";
      }
      case "ui.spinner": {
        ui(c, () -> {
          android.widget.Spinner s = new android.widget.Spinner(c);
          s.setAdapter(new android.widget.ArrayAdapter<String>(
              c, android.R.layout.simple_spinner_item, new String[] {"one", "two"}));
          s.setSelection(1);
          check(s.getSelectedItemPosition() == 1);
        });
        return "Spinner retains adapter selection";
      }
      case "ui.seekbar": {
        ui(c, () -> {
          android.widget.SeekBar s = new android.widget.SeekBar(c);
          s.setMax(100);
          s.setProgress(42);
          check(s.getProgress() == 42);
          s.setProgress(200);
          check(s.getProgress() == 100);
        });
        return "SeekBar clamps progress to configured maximum";
      }
      case "ui.progress": {
        ui(c, () -> {
          android.widget.ProgressBar p =
              new android.widget.ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
          p.setMax(100);
          p.setProgress(42);
          check(p.getMax() == 100 && p.getProgress() == 42);
        });
        return "ProgressBar retains max and progress";
      }
      case "ui.image_view": {
        ui(c, () -> {
          android.graphics.Bitmap b = bitmap();
          try {
            android.widget.ImageView v = new android.widget.ImageView(c);
            v.setImageBitmap(b);
            check(v.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
          } finally {
            b.recycle();
          }
        });
        return "ImageView retains bitmap drawable";
      }
      case "ui.radio_group": {
        ui(c, () -> {
          android.widget.RadioGroup g = new android.widget.RadioGroup(c);
          android.widget.RadioButton a = new android.widget.RadioButton(c),
                                     b = new android.widget.RadioButton(c);
          a.setId(101);
          b.setId(102);
          g.addView(a);
          g.addView(b);
          g.check(101);
          check(a.isChecked() && !b.isChecked());
          g.check(102);
          check(b.isChecked() && !a.isChecked());
        });
        return "RadioGroup checks one radio button at a time";
      }
      case "ui.text_watcher": {
        ui(c, () -> {
          android.widget.EditText e = new android.widget.EditText(c);
          boolean[] hit = {false};
          e.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
              hit[0] = true;
            }
            public void afterTextChanged(android.text.Editable text) {}
          });
          e.setText("probe");
          check(hit[0]);
        });
        return "TextWatcher observes EditText change";
      }
      case "ui.input_filter": {
        ui(c, () -> {
          android.widget.EditText e = new android.widget.EditText(c);
          e.setFilters(
              new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(3)});
          e.setText("abcdef");
          eq(e.getText().toString(), "abc");
        });
        return "LengthFilter limits EditText content";
      }
      case "ui.html": {
        android.text.Spanned s =
            android.text.Html.fromHtml("<b>probe</b>", android.text.Html.FROM_HTML_MODE_LEGACY);
        eq(s.toString(), "probe");
        check(s.getSpans(0, s.length(), android.text.style.StyleSpan.class).length > 0);
        return "Html parser yields styled text without markup";
      }
      case "storage.shared_memory": {
        try (android.os.SharedMemory memory = android.os.SharedMemory.create("probe", 4096)) {
          java.nio.ByteBuffer write = memory.mapReadWrite(), read = memory.mapReadOnly();
          try {
            write.putInt(0, 42);
            check(read.getInt(0) == 42 && read.isReadOnly());
          } finally {
            android.os.SharedMemory.unmap(write);
            android.os.SharedMemory.unmap(read);
          }
        }
        return "Java SharedMemory read-write and read-only maps agree";
      }
      case "resources.theme": {
        android.content.res.Resources.Theme t = c.getResources().newTheme();
        t.applyStyle(android.R.style.Theme_Material, true);
        android.util.TypedValue value = new android.util.TypedValue();
        check(t.resolveAttribute(android.R.attr.textColorPrimary, value, true));
        return "Theme resolves text color attribute";
      }
      case "resources.plural_rules": {
        android.icu.text.PluralRules rules =
            android.icu.text.PluralRules.forLocale(java.util.Locale.ENGLISH);
        eq(rules.select(1), "one");
        eq(rules.select(2), "other");
        return "ICU plural rules classify English one and other";
      }
      case "resources.bidi": {
        android.icu.text.Bidi b = new android.icu.text.Bidi(
            "abc سلام", android.icu.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        check(b.isMixed() && b.getRunCount() > 1);
        return "ICU BiDi detects mixed-direction text";
      }
      case "runtime.dex_file": {
        dalvik.system.DexFile dex = new dalvik.system.DexFile(c.getApplicationInfo().sourceDir);
        try {
          java.util.Enumeration<String> names = dex.entries();
          boolean found = false;
          while (names.hasMoreElements())
            if (names.nextElement().equals("org.shiraz.apiprobe.ProbeActivity"))
              found = true;
          check(found);
        } finally {
          dex.close();
        }
        return "DexFile enumerates own APK activity class";
      }
      case "runtime.weak_reference": {
        Object value = new Object();
        java.lang.ref.WeakReference<Object> ref = new java.lang.ref.WeakReference<>(value);
        check(ref.get() == value);
        ref.clear();
        check(ref.get() == null);
        return "WeakReference retains strongly reachable object";
      }
      case "runtime.collections": {
        java.util.TreeMap<Integer, String> map = new java.util.TreeMap<>();
        map.put(2, "b");
        map.put(1, "a");
        check(map.firstKey() == 1);
        java.util.concurrent.ConcurrentHashMap<String, Integer> concurrent =
            new java.util.concurrent.ConcurrentHashMap<>();
        concurrent.put("n", 41);
        check(concurrent.replace("n", 41, 42) && concurrent.get("n") == 42);
        return "TreeMap orders keys and concurrent map preserves updates";
      }
      case "crypto.gcm_reject": {
        javax.crypto.KeyGenerator generator = javax.crypto.KeyGenerator.getInstance("AES");
        generator.init(128);
        javax.crypto.SecretKey key = generator.generateKey();
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(new byte[] {42});
        encrypted[0] ^= 1;
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, iv));
        boolean rejected = false;
        try {
          cipher.doFinal(encrypted);
        } catch (javax.crypto.AEADBadTagException expected) {
          rejected = true;
        }
        check(rejected);
        return "AES-GCM rejects modified ciphertext";
      }
      case "network.ssl_parameters": {
        javax.net.ssl.SSLParameters p = new javax.net.ssl.SSLParameters();
        p.setProtocols(new String[] {"TLSv1.2"});
        p.setEndpointIdentificationAlgorithm("HTTPS");
        eq(p.getProtocols()[0], "TLSv1.2");
        eq(p.getEndpointIdentificationAlgorithm(), "HTTPS");
        return "SSLParameters retain protocol and endpoint identification";
      }
      default:
        throw new IllegalArgumentException("No implementation: " + id);
    }
  }
}
