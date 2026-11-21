// SPDX-License-Identifier: GPL-3.0-only
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.CrossProcessCursorWrapper;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.database.DatabaseUtils;
import android.database.DefaultDatabaseErrorHandler;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteRawStatement;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.CombinedVibration;
import android.os.DropBoxManager;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.OperationCanceledException;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PatternMatcher;
import android.os.PerformanceHintManager;
import android.os.PowerMonitor;
import android.os.PowerMonitorReadings;
import android.os.SecurityStateManager;
import android.os.SharedMemory;
import android.os.StrictMode;
import android.os.TestLooperManager;
import android.os.TokenWatcher;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.WorkDuration;
import android.os.ext.SdkExtensions;
import android.os.health.HealthStats;
import android.os.health.SystemHealthManager;
import android.os.health.TimerStat;
import android.os.health.UidHealthStats;
import android.os.strictmode.CustomViolation;
import android.os.strictmode.Violation;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceDataStore;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.preference.SwitchPreference;
import android.system.ErrnoException;
import android.view.View;
import android.widget.Checkable;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import shiraz.runtime.HostPreferences;

/** Behavioral verification for Milestone F2 (android.database, android.preference, android.os). */
public final class FrameworkMilestoneF2Smoke {
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static final class MemoryDataStore implements PreferenceDataStore {
        final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public String getString(String key, String defValue) {
            Object v = values.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            values.put(key, Boolean.valueOf(value));
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = values.get(key);
            return v instanceof Boolean ? ((Boolean) v).booleanValue() : defValue;
        }

        @Override
        public void putStringSet(String key, Set<String> set) {
            values.put(key, set == null ? null : new HashSet<String>(set));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object v = values.get(key);
            return v instanceof Set ? new HashSet<String>((Set<String>) v) : defValues;
        }
    }

    private static final class HostScreen extends PreferenceActivity {
        Intent startedIntent;

        HostScreen(Context base) {
            attachBaseContext(base);
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            startedIntent = intent;
            Intent result = new Intent();
            result.putExtra(
                    "android.intent.extra.ringtone.PICKED_URI",
                    Uri.parse("content://media/internal/audio/media/42"));
            onActivityResult(requestCode, RESULT_OK, result);
        }
    }

    private static final class LocalDropBox extends DropBoxManager {
        LocalDropBox() {
            super();
        }
    }

    private static void verifyDatabase(File dir) throws Exception {
        File dbFile = new File(dir, "f2_test.db");
        SQLiteDatabase.OpenParams params = new SQLiteDatabase.OpenParams.Builder()
                .setOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.OPEN_READWRITE)
                .setJournalMode(SQLiteDatabase.JOURNAL_MODE_WAL)
                .setSynchronousMode(SQLiteDatabase.SYNC_MODE_NORMAL)
                .setErrorHandler(new DefaultDatabaseErrorHandler())
                .build();

        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile, params);
        try {
            db.execSQL("CREATE TABLE items (_id INTEGER PRIMARY KEY, code TEXT NOT NULL, qty INTEGER, payload BLOB)");

            DatabaseUtils.InsertHelper helper = new DatabaseUtils.InsertHelper(db, "items");
            try {
                int codeIdx = helper.getColumnIndex("code");
                int qtyIdx = helper.getColumnIndex("qty");
                helper.prepareForInsert();
                helper.bind(codeIdx, "alpha");
                helper.bind(qtyIdx, 10);
                long id1 = helper.execute();
                check(id1 == 1L, "InsertHelper first row id");

                ContentValues cv = new ContentValues();
                cv.put("code", "beta");
                cv.put("qty", Integer.valueOf(25));
                long id2 = helper.insert(cv);
                check(id2 == 2L, "InsertHelper second row id");
            } finally {
                helper.close();
            }

            SQLiteRawStatement rawInsert = db.createRawStatement(
                    "INSERT INTO items (code, qty, payload) VALUES (:code, :qty, :blob)");
            try {
                check(rawInsert.getParameterCount() == 3, "raw param count");
                int pCode = rawInsert.getParameterIndex(":code");
                int pQty = rawInsert.getParameterIndex(":qty");
                int pBlob = rawInsert.getParameterIndex(":blob");
                check(pCode == 1 && pQty == 2 && pBlob == 3, "raw param indices");
                rawInsert.bindText(pCode, "gamma");
                rawInsert.bindInt(pQty, 99);
                rawInsert.bindBlob(pBlob, new byte[] {1, 2, 3, 4, 5});
                check(!rawInsert.step(), "raw insert step returns false on DONE");
            } finally {
                rawInsert.close();
            }

            SQLiteRawStatement rawSelect = db.createRawStatement(
                    "SELECT code, qty, payload FROM items WHERE qty >= ? ORDER BY qty ASC");
            try {
                rawSelect.bindInt(1, 20);
                check(rawSelect.step(), "raw select row 1");
                check(rawSelect.getResultColumnCount() == 3, "raw result col count");
                check("code".equals(rawSelect.getColumnName(0)), "raw col 0 name");
                check("beta".equals(rawSelect.getColumnText(0)), "raw row 1 code");
                check(rawSelect.getColumnInt(1) == 25, "raw row 1 qty");

                check(rawSelect.step(), "raw select row 2");
                check("gamma".equals(rawSelect.getColumnText(0)), "raw row 2 code");
                check(rawSelect.getColumnInt(1) == 99, "raw row 2 qty");
                check(rawSelect.getColumnLength(2) == 5, "raw blob length");
                byte[] slice = new byte[3];
                int read = rawSelect.readColumnBlob(2, slice, 0, 3, 1);
                check(read == 3 && Arrays.equals(slice, new byte[] {2, 3, 4}), "readColumnBlob slice");
                check(!rawSelect.step(), "raw select exhausted");
            } finally {
                rawSelect.close();
            }

            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables("items");
            qb.setStrict(true);
            Map<String, String> projMap = new HashMap<String, String>();
            projMap.put("_id", "_id");
            projMap.put("label", "code AS label");
            projMap.put("qty", "qty");
            qb.setProjectionMap(projMap);
            qb.appendWhere("qty >= ");
            qb.appendWhereEscapeString("10");

            Cursor c = qb.query(db, new String[] {"label", "qty"}, null, null, null, null, "qty DESC");
            try {
                CrossProcessCursorWrapper wrapped = new CrossProcessCursorWrapper(c);
                check(wrapped.getCount() == 3, "SQLiteQueryBuilder count");
                check(wrapped.moveToFirst() && "gamma".equals(wrapped.getString(0)), "SQLiteQueryBuilder top row");
            } finally {
                c.close();
            }
        } finally {
            db.close();
        }

        MatrixCursor left = new MatrixCursor(new String[] {"k", "v"});
        left.addRow(new Object[] {"a", "1"});
        left.addRow(new Object[] {"b", "2"});
        MatrixCursor right = new MatrixCursor(new String[] {"k", "v"});
        right.addRow(new Object[] {"b", "20"});
        right.addRow(new Object[] {"c", "30"});

        StringBuilder joined = new StringBuilder();
        CursorJoiner joiner = new CursorJoiner(left, new String[] {"k"}, right, new String[] {"k"});
        for (CursorJoiner.Result r : joiner) {
            joined.append(r.name()).append(':');
        }
        check("LEFT:BOTH:RIGHT:".equals(joined.toString()), "CursorJoiner sequence: " + joined);

        MergeCursor merged = new MergeCursor(new Cursor[] {left, right});
        try {
            check(merged.getCount() == 4, "MergeCursor count");
            check(merged.moveToPosition(2) && "b".equals(merged.getString(0)) && "20".equals(merged.getString(1)),
                    "MergeCursor cross-cursor positioning");
        } finally {
            merged.close();
        }

        // Verify DefaultDatabaseErrorHandler removes corrupted files cleanly
        File corrupt = new File(dir, "corrupt.db");
        Files.write(corrupt.toPath(), "not-sqlite".getBytes(StandardCharsets.UTF_8));
        SQLiteDatabase dummy = SQLiteDatabase.openOrCreateDatabase(
                new File(dir, "corrupt_handle.db"), null);
        dummy.close();
        check(SQLiteDatabase.deleteDatabase(corrupt) && !corrupt.exists(), "deleteDatabase cleanup");
    }

    private static void verifyPreference(File dir) throws Exception {
        final HostPreferences hostPrefs = new HostPreferences(dir);
        Context context = new ContextWrapper(null) {
            final Resources.Theme theme = Resources.getSystem().newTheme();

            @Override
            public Resources getResources() {
                return Resources.getSystem();
            }

            @Override
            public Resources.Theme getTheme() {
                return theme;
            }

            @Override
            public String getPackageName() {
                return "shiraz.f2.test";
            }

            @Override
            public Object getSystemService(String name) {
                return null;
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return hostPrefs.get(name, mode);
            }
        };

        try {
            HostScreen activity = new HostScreen(context);
            PreferenceManager pm = activity.getPreferenceManager();
            MemoryDataStore store = new MemoryDataStore();
            pm.setPreferenceDataStore(store);
            check(pm.getSharedPreferences() == null, "SharedPreferences null when dataStore active");

            PreferenceScreen screen = pm.createPreferenceScreen(activity);
            PreferenceCategory category = new PreferenceCategory(activity);
            category.setTitle("General");
            screen.addPreference(category);
            check(!category.isEnabled(), "PreferenceCategory itself is not clickable/enabled");

            try {
                category.addPreference(new PreferenceCategory(activity));
                throw new AssertionError("Nested PreferenceCategory should be rejected");
            } catch (IllegalArgumentException expected) {
            }

            CheckBoxPreference cbPref = new CheckBoxPreference(activity);
            cbPref.setKey("wifi");
            cbPref.setTitle("Wi-Fi");
            cbPref.setSummaryOn("Connected");
            cbPref.setSummaryOff("Disconnected");
            category.addPreference(cbPref);
            check(cbPref.isEnabled(), "Child inside enabled PreferenceCategory must be enabled");

            cbPref.setChecked(true);
            check(store.getBoolean("wifi", false), "CheckBoxPreference persisted to PreferenceDataStore");
            View cbRow = cbPref.getView(null, null);
            Checkable cbWidget = (Checkable) cbRow.findViewById(0x01020001);
            check(cbWidget != null && cbWidget.isChecked(), "CheckBoxPreference bound widget checked");
            TextView cbSummary = cbRow.findViewById(0x01020010);
            check("Connected".contentEquals(cbSummary.getText()), "CheckBoxPreference summaryOn rendered");

            SwitchPreference swPref = new SwitchPreference(activity);
            swPref.setKey("bt");
            swPref.setSwitchTextOn("ON");
            swPref.setSwitchTextOff("OFF");
            screen.addPreference(swPref);
            swPref.setChecked(true);
            check(store.getBoolean("bt", false), "SwitchPreference persisted to PreferenceDataStore");

            EditTextPreference editPref = new EditTextPreference(activity);
            editPref.setKey("hostname");
            editPref.setPersistent(false);
            screen.addPreference(editPref);
            editPref.setText("shiraz-dev");
            check("shiraz-dev".equals(editPref.getText()), "EditTextPreference text");

            ListPreference listPref = new ListPreference(activity);
            listPref.setKey("mode");
            listPref.setEntries(new CharSequence[] {"Fast", "Balanced", "Eco"});
            listPref.setEntryValues(new CharSequence[] {"fast", "bal", "eco"});
            listPref.setSummary("Mode: %s");
            screen.addPreference(listPref);
            listPref.setValueIndex(1);
            check("bal".equals(listPref.getValue()), "ListPreference value");
            check("Balanced".contentEquals(listPref.getEntry()), "ListPreference entry");
            check("Mode: Balanced".contentEquals(listPref.getSummary()), "ListPreference formatted summary");

            MultiSelectListPreference multiPref = new MultiSelectListPreference(activity);
            multiPref.setKey("tags");
            multiPref.setEntries(new CharSequence[] {"A", "B", "C"});
            multiPref.setEntryValues(new CharSequence[] {"a", "b", "c"});
            screen.addPreference(multiPref);
            Set<String> tags = new HashSet<String>(Arrays.asList("a", "c"));
            multiPref.setValues(tags);
            check(store.getStringSet("tags", Collections.<String>emptySet()).equals(tags),
                    "MultiSelectListPreference persisted StringSet");

            RingtonePreference ringPref = new RingtonePreference(activity);
            ringPref.setKey("tone");
            ringPref.setTitle("Ringtone");
            screen.addPreference(ringPref);
            activity.setPreferenceScreen(screen);
            // Trigger RingtonePreference click -> startActivityForResult -> onActivityResult
            ringPref.getOnPreferenceChangeListener();
            activity.getListView().performItemClick(
                    ringPref.getView(null, activity.getListView()),
                    screen.getPreferenceCount() - 1,
                    0);
            check(activity.startedIntent != null, "RingtonePreference launched picker intent");
            check("content://media/internal/audio/media/42".equals(store.getString("tone", null)),
                    "RingtonePreference persisted picked URI");

            // Hierarchy state save/restore for non-persistent EditTextPreference
            Bundle saved = new Bundle();
            screen.saveHierarchyState(saved);
            editPref.setText("changed");
            screen.restoreHierarchyState(saved);
            check("shiraz-dev".equals(editPref.getText()), "Non-persistent EditTextPreference restored from Bundle");

            // PreferenceActivity.Header Parcel roundtrip
            PreferenceActivity.Header header = new PreferenceActivity.Header();
            header.id = 77L;
            header.title = "Network";
            header.summary = "Wi-Fi & VPN";
            header.fragment = "com.example.NetFragment";
            header.extras = new Bundle();
            header.extras.putString("k", "v");
            header.intent = new Intent("com.example.ACTION");
            Parcel p = Parcel.obtain();
            try {
                header.writeToParcel(p, 0);
                p.setDataPosition(0);
                PreferenceActivity.Header copy = PreferenceActivity.Header.CREATOR.createFromParcel(p);
                check(copy.id == 77L && "Network".contentEquals(copy.title)
                        && "com.example.NetFragment".equals(copy.fragment)
                        && "v".equals(copy.extras.getString("k"))
                        && "com.example.ACTION".equals(copy.intent.getAction()),
                        "PreferenceActivity.Header Parcel roundtrip");
            } finally {
                p.recycle();
            }
        } finally {
            hostPrefs.close();
        }
    }

    private static void verifyOs(File dir) throws Exception {
        // 1. PatternMatcher
        PatternMatcher glob = new PatternMatcher("/api/.*/users", PatternMatcher.PATTERN_SIMPLE_GLOB);
        check(glob.match("/api/v1/users") && !glob.match("/api/v1/posts"), "PatternMatcher simple glob");
        PatternMatcher adv = new PatternMatcher(
                "/items/[a-z]+_[0-9]{2,3}\\.json", PatternMatcher.PATTERN_ADVANCED_GLOB);
        check(adv.match("/items/book_42.json") && !adv.match("/items/book_7.json")
                && !adv.match("/items/BOOK_42.json"), "PatternMatcher advanced glob");
        PatternMatcher suffix = new PatternMatcher(".apk", PatternMatcher.PATTERN_SUFFIX);
        check(suffix.match("base.apk") && !suffix.match("base.jar"), "PatternMatcher suffix");
        Parcel p = Parcel.obtain();
        try {
            adv.writeToParcel(p, 0);
            p.setDataPosition(0);
            PatternMatcher advCopy = PatternMatcher.CREATOR.createFromParcel(p);
            check(advCopy.match("/items/test_123.json"), "PatternMatcher Parcel roundtrip");
        } finally {
            p.recycle();
        }

        // 2. FileUtils.copy + ProgressListener + CancellationSignal
        byte[] payload = new byte[20000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31 + 7);
        }
        final AtomicLong reported = new AtomicLong(-1L);
        ByteArrayOutputStream copiedOut = new ByteArrayOutputStream();
        long count = FileUtils.copy(
                new ByteArrayInputStream(payload),
                copiedOut,
                new CancellationSignal(),
                DIRECT_EXECUTOR,
                new FileUtils.ProgressListener() {
                    @Override
                    public void onProgress(long progress) {
                        reported.set(progress);
                    }
                });
        check(count == payload.length && reported.get() == payload.length
                && Arrays.equals(payload, copiedOut.toByteArray()), "FileUtils stream copy");

        File srcFile = new File(dir, "src.bin");
        File dstFile = new File(dir, "dst.bin");
        Files.write(srcFile.toPath(), payload);
        FileInputStream fis = new FileInputStream(srcFile);
        FileOutputStream fos = new FileOutputStream(dstFile);
        try {
            long fdCopied = FileUtils.copy(fis.getFD(), fos.getFD());
            check(fdCopied == payload.length, "FileUtils FD copy count");
        } finally {
            FileUtils.closeQuietly(fis);
            FileUtils.closeQuietly(fos);
        }
        check(Arrays.equals(payload, Files.readAllBytes(dstFile.toPath())), "FileUtils FD copy bytes");

        CancellationSignal canceled = new CancellationSignal();
        canceled.cancel();
        try {
            FileUtils.copy(new ByteArrayInputStream(payload), new ByteArrayOutputStream(), canceled, null, null);
            throw new AssertionError("Canceled FileUtils.copy should throw OperationCanceledException");
        } catch (OperationCanceledException expected) {
        }

        // 3. SharedMemory + Parcel roundtrip + protection enforcement
        SharedMemory shm = SharedMemory.create("f2test", 4096);
        try {
            check(shm.getSize() == 4096, "SharedMemory size");
            ByteBuffer rw = shm.mapReadWrite();
            rw.putInt(0, 0xCAFEBABE);
            rw.putInt(4, 123456789);
            SharedMemory.unmap(rw);

            Parcel shmParcel = Parcel.obtain();
            try {
                shm.writeToParcel(shmParcel, 0);
                shmParcel.setDataPosition(0);
                SharedMemory dupShm = SharedMemory.CREATOR.createFromParcel(shmParcel);
                try {
                    ByteBuffer ro = dupShm.mapReadOnly();
                    check(ro.getInt(0) == 0xCAFEBABE && ro.getInt(4) == 123456789,
                            "SharedMemory Parcel FD sharing readback");
                } finally {
                    dupShm.close();
                }
            } finally {
                shmParcel.recycle();
            }

            check(shm.setProtect(1 /* PROT_READ */), "SharedMemory setProtect PROT_READ");
            try {
                shm.mapReadWrite();
                throw new AssertionError("mapReadWrite after PROT_READ must fail");
            } catch (ErrnoException expected) {
            }
            ByteBuffer ro2 = shm.mapReadOnly();
            check(ro2.getInt(0) == 0xCAFEBABE, "SharedMemory mapReadOnly after PROT_READ");
        } finally {
            shm.close();
        }

        // 4. DropBoxManager (text + gzipped binary entry + Parcel)
        LocalDropBox dropBox = new LocalDropBox();
        dropBox.addText("crash_tag", "stacktrace-line-1");
        ByteArrayOutputStream gzBytes = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(gzBytes);
        gz.write("compressed-diagnostics".getBytes(StandardCharsets.UTF_8));
        gz.close();
        dropBox.addData("gz_tag", gzBytes.toByteArray(), DropBoxManager.IS_TEXT | DropBoxManager.IS_GZIPPED);

        DropBoxManager.Entry e1 = dropBox.getNextEntry("crash_tag", 0L);
        check(e1 != null && "stacktrace-line-1".equals(e1.getText(1024)), "DropBox text entry");
        e1.close();

        DropBoxManager.Entry e2 = dropBox.getNextEntry("gz_tag", 0L);
        check(e2 != null && "compressed-diagnostics".equals(e2.getText(1024)), "DropBox gzipped text entry");
        e2.close();

        // 5. SystemHealthManager + HealthStats + TimerStat + OutcomeReceiver
        SystemHealthManager healthManager = new SystemHealthManager();
        HealthStats uidStats = healthManager.takeMyUidSnapshot();
        check("UidHealthStats".equals(uidStats.getDataType()), "HealthStats dataType");
        check(uidStats.hasMeasurement(UidHealthStats.MEASUREMENT_UPTIME_BATTERY_MS),
                "HealthStats uptime measurement");
        final AtomicReference<PowerMonitorReadings> readingsRef = new AtomicReference<PowerMonitorReadings>();
        PowerMonitor pm = new PowerMonitor(0, PowerMonitor.POWER_MONITOR_TYPE_CONSUMER, "CPU");
        healthManager.getPowerMonitorReadings(
                Collections.singletonList(pm),
                DIRECT_EXECUTOR,
                new OutcomeReceiver<PowerMonitorReadings, RuntimeException>() {
                    @Override
                    public void onResult(PowerMonitorReadings result) {
                        readingsRef.set(result);
                    }
                });
        check(readingsRef.get() != null
                && readingsRef.get().getConsumedEnergy(pm) == PowerMonitorReadings.ENERGY_UNAVAILABLE,
                "SystemHealthManager OutcomeReceiver delivery");

        TimerStat timer = new TimerStat(5, 9876L);
        Parcel tp = Parcel.obtain();
        try {
            timer.writeToParcel(tp, 0);
            tp.setDataPosition(0);
            TimerStat tCopy = TimerStat.CREATOR.createFromParcel(tp);
            check(tCopy.getCount() == 5 && tCopy.getTime() == 9876L, "TimerStat Parcel roundtrip");
        } finally {
            tp.recycle();
        }

        // 6. VibrationEffect + CombinedVibration + VibrationAttributes Parcel roundtrips
        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f, 10)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 20)
                .compose();
        CombinedVibration stereo = CombinedVibration.startParallel()
                .addVibrator(0, VibrationEffect.createOneShot(50L, 128))
                .addVibrator(1, composed)
                .combine();
        VibrationAttributes vibAttrs = new VibrationAttributes.Builder(
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                .setFlags(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY,
                        VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY)
                .build();
        check(vibAttrs.getUsage() == VibrationAttributes.USAGE_ALARM
                && vibAttrs.getUsageClass() == VibrationAttributes.USAGE_CLASS_ALARM
                && vibAttrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY),
                "VibrationAttributes from AudioAttributes");

        Parcel vp = Parcel.obtain();
        try {
            stereo.writeToParcel(vp, 0);
            vibAttrs.writeToParcel(vp, 0);
            vp.setDataPosition(0);
            CombinedVibration stereoCopy = CombinedVibration.CREATOR.createFromParcel(vp);
            VibrationAttributes attrsCopy = VibrationAttributes.CREATOR.createFromParcel(vp);
            check(stereo.equals(stereoCopy) && vibAttrs.equals(attrsCopy),
                    "CombinedVibration & VibrationAttributes Parcel roundtrip");
        } finally {
            vp.recycle();
        }

        // 7. SdkExtensions, Build.Partition, SecurityStateManager, WorkDuration, PerformanceHintManager
        check(SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) == 0, "SdkExtensions R");
        List<Build.Partition> partitions = Build.getFingerprintedPartitions();
        check(!partitions.isEmpty()
                && Build.Partition.PARTITION_NAME_SYSTEM.equals(partitions.get(0).getName()),
                "Build.getFingerprintedPartitions");
        Bundle sec = new SecurityStateManager().getGlobalSecurityState();
        check(sec.getString(SecurityStateManager.KEY_KERNEL_VERSION) != null, "SecurityStateManager");

        WorkDuration wd = new WorkDuration();
        wd.setWorkPeriodStartTimestampNanos(1000L);
        wd.setActualTotalDurationNanos(12_000_000L);
        wd.setActualCpuDurationNanos(8_000_000L);
        wd.setActualGpuDurationNanos(4_000_000L);
        PerformanceHintManager phm = new PerformanceHintManager();
        PerformanceHintManager.Session hintSession = phm.createHintSession(new int[] {1}, 16_666_666L);
        hintSession.reportActualWorkDuration(wd);
        hintSession.close();

        // 8. StrictMode OnThreadViolationListener + CustomViolation
        StrictMode.ThreadPolicy prevPolicy = StrictMode.getThreadPolicy();
        final AtomicReference<Violation> capturedViolation = new AtomicReference<Violation>();
        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectCustomSlowCalls()
                    .penaltyListener(DIRECT_EXECUTOR, new StrictMode.OnThreadViolationListener() {
                        @Override
                        public void onThreadViolation(Violation v) {
                            capturedViolation.set(v);
                        }
                    })
                    .build());
            StrictMode.noteSlowCall("f2-slow-op");
            check(capturedViolation.get() instanceof CustomViolation, "StrictMode CustomViolation listener");
        } finally {
            StrictMode.setThreadPolicy(prevPolicy);
        }

        // 9. TestLooperManager + TokenWatcher
        HandlerThread ht = new HandlerThread("f2-looper-test");
        ht.start();
        Looper workerLooper = ht.getLooper();
        try {
            Handler h = new Handler(workerLooper);
            final AtomicInteger transitions = new AtomicInteger(0);
            TokenWatcher watcher = new TokenWatcher(h, "f2-tokens") {
                @Override
                public void acquired() {
                    transitions.addAndGet(10);
                }

                @Override
                public void released() {
                    transitions.addAndGet(1);
                }
            };
            TestLooperManager tlm = new TestLooperManager(workerLooper);
            Binder token = new Binder();
            watcher.acquire(token, "tok1");
            check(watcher.isAcquired(), "TokenWatcher acquired state");
            Message m1 = tlm.next();
            tlm.execute(m1);
            tlm.recycle(m1);
            check(transitions.get() == 10, "TokenWatcher acquired callback dispatched");

            watcher.release(token);
            check(!watcher.isAcquired(), "TokenWatcher released state");
            Message m2 = tlm.next();
            tlm.execute(m2);
            tlm.recycle(m2);
            check(transitions.get() == 11, "TokenWatcher released callback dispatched");

            h.sendEmptyMessage(777);
            check(tlm.hasMessages(h, null, 777), "TestLooperManager hasMessages");
            Message m3 = tlm.next();
            check(m3.what == 777, "TestLooperManager next what");
            tlm.execute(m3);
            tlm.recycle(m3);
            tlm.release();
        } finally {
            workerLooper.quit();
            ht.join(5000);
        }
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        File tempDir = Files.createTempDirectory("shiraz-f2-smoke").toFile();
        try {
            verifyDatabase(tempDir);
            verifyPreference(tempDir);
            verifyOs(tempDir);
            System.out.println("FRAMEWORK_F2_OK");
        } finally {
            ParcelFileDescriptor[] empty = new ParcelFileDescriptor[0];
            if (empty.length == 0 && tempDir.exists()) {
                File[] children = tempDir.listFiles();
                if (children != null) {
                    for (int i = 0; i < children.length; i++) {
                        children[i].delete();
                    }
                }
                tempDir.delete();
            }
        }
    }
}
