// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PreferenceManager {
    public interface OnActivityResultListener {
        boolean onActivityResult(int requestCode, int resultCode, Intent data);
    }

    public interface OnActivityStopListener {
        void onActivityStop();
    }

    public interface OnActivityDestroyListener {
        void onActivityDestroy();
    }

    private final Context context;
    private String name;
    private int mode = Context.MODE_PRIVATE;
    private PreferenceDataStore dataStore;
    private PreferenceScreen screen;
    private PreferenceActivity activity;
    private PreferenceFragment fragment;
    private int nextRequestCode = 100;
    private final List<OnActivityDestroyListener> destroyListeners = new ArrayList<OnActivityDestroyListener>();
    private final List<OnActivityResultListener> resultListeners = new ArrayList<OnActivityResultListener>();
    private final List<OnActivityStopListener> stopListeners = new ArrayList<OnActivityStopListener>();

    PreferenceManager(Context context) {
        this(context, (PreferenceActivity) null);
    }

    PreferenceManager(Context context, PreferenceActivity activity) {
        this.context = Objects.requireNonNull(context);
        this.activity = activity;
        this.name = getDefaultSharedPreferencesName(context);
    }

    PreferenceManager(Context context, PreferenceFragment fragment) {
        this.context = Objects.requireNonNull(context);
        this.fragment = fragment;
        this.name = getDefaultSharedPreferencesName(context);
    }

    public static String getDefaultSharedPreferencesName(Context context) {
        return context.getPackageName() + "_preferences";
    }

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences(getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE);
    }

    public void setPreferenceDataStore(PreferenceDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public PreferenceDataStore getPreferenceDataStore() {
        return dataStore;
    }

    public String getSharedPreferencesName() {
        return name;
    }

    public void setSharedPreferencesName(String value) {
        name = Objects.requireNonNull(value);
    }

    public int getSharedPreferencesMode() {
        return mode;
    }

    public void setSharedPreferencesMode(int value) {
        mode = value;
    }

    public SharedPreferences getSharedPreferences() {
        if (dataStore != null) {
            return null;
        }
        return context.getSharedPreferences(name, mode);
    }

    public PreferenceScreen createPreferenceScreen(Context context) {
        PreferenceScreen value = new PreferenceScreen(context, null);
        value.attach(this);
        return value;
    }

    public Preference findPreference(CharSequence key) {
        return screen == null ? null : screen.findPreference(key);
    }

    PreferenceActivity getActivity() {
        return activity;
    }

    PreferenceFragment getFragment() {
        return fragment;
    }

    synchronized int allocateRequestCode() {
        return nextRequestCode++;
    }

    void registerOnActivityDestroyListener(OnActivityDestroyListener listener) {
        if (listener != null && !destroyListeners.contains(listener)) {
            destroyListeners.add(listener);
        }
    }

    void unregisterOnActivityDestroyListener(OnActivityDestroyListener listener) {
        destroyListeners.remove(listener);
    }

    void dispatchActivityDestroy() {
        List<OnActivityDestroyListener> copy = new ArrayList<OnActivityDestroyListener>(destroyListeners);
        for (int i = 0; i < copy.size(); i++) {
            copy.get(i).onActivityDestroy();
        }
    }

    void registerOnActivityResultListener(OnActivityResultListener listener) {
        if (listener != null && !resultListeners.contains(listener)) {
            resultListeners.add(listener);
        }
    }

    void unregisterOnActivityResultListener(OnActivityResultListener listener) {
        resultListeners.remove(listener);
    }

    void dispatchActivityResult(int requestCode, int resultCode, Intent data) {
        List<OnActivityResultListener> copy = new ArrayList<OnActivityResultListener>(resultListeners);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).onActivityResult(requestCode, resultCode, data)) {
                break;
            }
        }
    }

    void registerOnActivityStopListener(OnActivityStopListener listener) {
        if (listener != null && !stopListeners.contains(listener)) {
            stopListeners.add(listener);
        }
    }

    void unregisterOnActivityStopListener(OnActivityStopListener listener) {
        stopListeners.remove(listener);
    }

    void dispatchActivityStop() {
        List<OnActivityStopListener> copy = new ArrayList<OnActivityStopListener>(stopListeners);
        for (int i = 0; i < copy.size(); i++) {
            copy.get(i).onActivityStop();
        }
    }

    void screen(PreferenceScreen value) {
        if (value != null) {
            value.attach(this);
        }
        screen = value;
    }

    PreferenceScreen getPreferenceScreen() {
        return screen;
    }

    void changed() {
        if (screen != null) {
            screen.changed();
        }
    }

    boolean clicked(PreferenceScreen screen, Preference preference) {
        if (fragment != null && fragment.onPreferenceTreeClick(screen, preference)) {
            return true;
        }
        return activity != null && activity.onPreferenceTreeClick(screen, preference);
    }
}
