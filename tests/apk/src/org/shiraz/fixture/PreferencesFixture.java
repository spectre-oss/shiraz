// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class PreferencesFixture {
    private SharedPreferences preferences, durable;
    private final List<String> events = new ArrayList<String>();
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (Looper.myLooper() != Looper.getMainLooper() || p != preferences) throw new AssertionError("Preference listener thread/identity");
            events.add(key);
            if ("reentrant".equals(key)) p.edit().putInt("nested", 2).apply();
        }
    };
    void start(Context context) {
        preferences = context.getSharedPreferences("fixture.preferences", Context.MODE_PRIVATE);
        if (preferences != context.getApplicationContext().getSharedPreferences("fixture.preferences", 0)
                || preferences != context.getSharedPreferences("fixture.preferences", 0)) throw new AssertionError("Preference cache identity");
        try { context.getSharedPreferences("fixture.preferences", 4); throw new AssertionError("Multiprocess supported"); }
        catch (UnsupportedOperationException expected) { }
        if (!preferences.edit().clear().commit()) throw new AssertionError("Clear failed");
        Set<String> set = new HashSet<String>(Arrays.asList("one", "two"));
        SharedPreferences.Editor editor = preferences.edit().putString("s", "café\u0000\ud800")
            .putInt("i", -17).putLong("l", Long.MAX_VALUE).putFloat("f", -0.0f).putBoolean("b", true).putStringSet("set", set);
        set.add("later");
        if (preferences.contains("s") || !editor.commit()) throw new AssertionError("Transaction visibility/commit");
        if (!"café\u0000\ud800".equals(preferences.getString("s", null)) || preferences.getInt("i", 0) != -17
                || preferences.getLong("l", 0) != Long.MAX_VALUE || Float.floatToRawIntBits(preferences.getFloat("f", 1)) != 0x80000000
                || !preferences.getBoolean("b", false) || preferences.getStringSet("set", null).size() != 2)
            throw new AssertionError("Typed preference values");
        preferences.getStringSet("set", null).clear();
        Map<String, ?> snapshot = preferences.getAll(); ((Set<?>)snapshot.get("set")).clear(); snapshot.clear();
        if (preferences.getStringSet("set", null).size() != 2 || !preferences.contains("i")) throw new AssertionError("Snapshot alias");
        try { preferences.getString("i", "fallback"); throw new AssertionError("Wrong type accepted"); }
        catch (ClassCastException expected) { }
        android.os.StrictMode.ThreadPolicy policy = android.os.StrictMode.getThreadPolicy();
        try {
            android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder().detectDiskWrites().penaltyDeath().build());
            try { preferences.edit().putInt("policy-denied", 1).commit(); throw new AssertionError("Blocking commit bypassed I/O policy"); }
            catch (RuntimeException expected) { if (expected.getMessage() == null || !expected.getMessage().contains("disk write")) throw expected; }
            if (preferences.contains("policy-denied")) throw new AssertionError("Denied commit changed memory");
            preferences.edit().putInt("background-only", 1).apply();
        } finally { android.os.StrictMode.setThreadPolicy(policy); }
        if (preferences.getInt("missing", 42) != 42 || preferences.getStringSet("missing", set) != set) throw new AssertionError("Defaults");
        preferences.edit().putString("s", null).putStringSet("set", null).apply();
        if (preferences.contains("s") || preferences.contains("set")) throw new AssertionError("Null removes values");
        preferences.edit().putInt("keep", 3).clear().putInt("last", 1).putInt("last", 7).apply();
        if (preferences.getAll().size() != 2 || preferences.getInt("keep", 0) != 3 || preferences.getInt("last", 0) != 7)
            throw new AssertionError("Clear before modifications / last value wins");
        preferences.registerOnSharedPreferenceChangeListener(listener);
        preferences.edit().putInt("reentrant", 1).apply();
        if (!events.equals(Arrays.asList("reentrant", "nested"))) throw new AssertionError("Reentrant listener");
        events.clear(); preferences.edit().putInt("nested", 2).remove("absent").apply();
        if (!events.isEmpty()) throw new AssertionError("Unchanged value notified");
        preferences.edit().clear().commit();
        if (!events.isEmpty()) throw new AssertionError("API 27 clear listener contract");
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread worker = new Thread(new Runnable() { public void run() {
            try {
                for (int i = 0; i < 8; ++i) preferences.edit().putInt("ordered", i).apply();
                if (!preferences.edit().putInt("worker", 99).commit()) throw new AssertionError("Worker commit failed");
            } catch (Throwable failure) { error.set(failure); }
        }});
        worker.start();
        try { worker.join(5000); } catch (InterruptedException e) { throw new AssertionError(e); }
        if (worker.isAlive() || error.get() != null) throw new AssertionError("Worker preferences", error.get());
        if (!events.isEmpty()) throw new AssertionError("Worker callback ran inline");
        durable = context.getSharedPreferences("fixture.persistence", 0);
        String phase = System.getenv("SHIRAZ_FIXTURE_PREFERENCES");
        if ("read".equals(phase)) {
            if (!"café\u0000\ud800".equals(durable.getString("s", null)) || durable.getInt("i", 0) != -17
                    || durable.getLong("l", 0) != Long.MAX_VALUE || Float.floatToRawIntBits(durable.getFloat("f", 1)) != 0x80000000
                    || !durable.getBoolean("b", false) || !durable.getStringSet("set", Collections.<String>emptySet()).equals(new HashSet<String>(Arrays.asList("one", null)))
                    || durable.getInt("applied-on-close", 0) != 7 || durable.getInt("sequence", 0) != 11)
                throw new AssertionError("Preferences did not survive separate ART VM");
            System.out.println("SHIRAZ_FIXTURE_PREFERENCES_PERSISTED");
        }
        if ("write".equals(phase)) {
            if (!durable.edit().clear().putString("s", "café\u0000\ud800").putInt("i", -17).putLong("l", Long.MAX_VALUE)
                    .putFloat("f", -0.0f).putBoolean("b", true).putStringSet("set", new HashSet<String>(Arrays.asList("one", null))).commit())
                throw new AssertionError("Persistent values commit failed");
            for (int i = 1; i <= 11; ++i) durable.edit().putInt("sequence", i).apply();
        }
        // Encoded names cannot escape the data directory or collide at a shared prefix.
        context.getSharedPreferences("../" + new String(new char[40]).replace('\0', 'x'), 0).edit().putInt("value", 1).commit();
        context.getSharedPreferences("", 0).edit().putInt(null, 1).commit();
        if (context.getSharedPreferences("", 0).getInt(null, 0) != 1) throw new AssertionError("Null key");
    }
    void verify() {
        // Intentional early-failure sessions may exit before the main queue is pumped.
        if (System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_CALLBACK_FAIL") == null
                && System.getenv("SHIRAZ_FIXTURE_POINTER_FAIL") == null
                && (!events.contains("worker") || Collections.frequency(events, "ordered") != 8
                    || preferences.getInt("ordered", -1) != 7 || preferences.getInt("worker", 0) != 99))
            throw new AssertionError("Queued preference callbacks/order");
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        durable.edit().putInt("applied-on-close", 7).apply();
        System.out.println("SHIRAZ_FIXTURE_PREFERENCES_OK");
    }
}
