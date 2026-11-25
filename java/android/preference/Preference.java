// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.*;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;
import java.util.Set;

/** Legacy preference row with app-owned persistence and normal click dispatch. */
public class Preference implements Comparable<Preference> {
    public static final int DEFAULT_ORDER = Integer.MAX_VALUE;

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object value);
    }

    public static class BaseSavedState extends AbsSavedState {
        public static final Parcelable.Creator<BaseSavedState> CREATOR =
                new Parcelable.Creator<BaseSavedState>() {
                    @Override
                    public BaseSavedState createFromParcel(Parcel in) {
                        return new BaseSavedState(in);
                    }

                    @Override
                    public BaseSavedState[] newArray(int size) {
                        return new BaseSavedState[size];
                    }
                };

        public BaseSavedState(Parcel source) {
            super(source);
        }

        public BaseSavedState(Parcelable superState) {
            super(superState == null ? EMPTY_STATE : superState);
        }
    }

    private final Context context;
    private String key;
    private CharSequence title, summary;
    private boolean enabled = true, selectable = true, persistent = true;
    private int order = DEFAULT_ORDER;
    private String fragment;
    private Bundle extras;
    private Intent intent;
    private PreferenceManager manager;
    private PreferenceDataStore dataStore;
    PreferenceGroup parent;
    private Object defaultValue;
    private boolean baseMethodCalled;
    private OnPreferenceClickListener click;
    private OnPreferenceChangeListener change;

    public Preference(Context context) {
        this(context, null);
    }

    public Preference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Preference(Context context, AttributeSet attrs, int style) {
        this(context, attrs, style, 0);
    }

    public Preference(Context context, AttributeSet attrs, int style, int resource) {
        this.context = java.util.Objects.requireNonNull(context);
        if (attrs != null || style != 0 || resource != 0) {
            shiraz.runtime.XmlAttributes x = new shiraz.runtime.XmlAttributes(context, attrs, style, resource);
            key = x.text("key");
            title = x.text("title");
            summary = x.text("summary");
            fragment = x.text("fragment");
            enabled = x.bool("enabled", true);
            selectable = x.bool("selectable", true);
            persistent = x.bool("persistent", true);
            order = x.integer("order", DEFAULT_ORDER);
            TypedArray defaults = context.obtainStyledAttributes(attrs, new int[] {0x010101ed}, style, resource);
            try {
                if (defaults.hasValue(0)) {
                    defaultValue = onGetDefaultValue(defaults, 0);
                }
            } finally {
                defaults.recycle();
            }
        }
    }

    public Context getContext() {
        return context;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String value) {
        key = value;
    }

    public boolean hasKey() {
        return key != null && !key.isEmpty();
    }

    public CharSequence getTitle() {
        return title;
    }

    public void setTitle(CharSequence value) {
        title = value;
        notifyChanged();
    }

    public void setTitle(int id) {
        setTitle(context.getText(id));
    }

    public CharSequence getSummary() {
        return summary;
    }

    public void setSummary(CharSequence value) {
        summary = value;
        notifyChanged();
    }

    public void setSummary(int id) {
        setSummary(context.getText(id));
    }

    public boolean isEnabled() {
        return enabled && (parent == null || !parent.shouldDisableDependents());
    }

    public void setEnabled(boolean value) {
        if (enabled != value) {
            enabled = value;
            notifyDependencyChange(shouldDisableDependents());
            notifyChanged();
        }
    }

    public boolean shouldDisableDependents() {
        return !isEnabled();
    }

    public void notifyDependencyChange(boolean disableDependents) {
        notifyChanged();
    }

    public boolean isSelectable() {
        return selectable;
    }

    public void setSelectable(boolean value) {
        selectable = value;
        notifyChanged();
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean value) {
        persistent = value;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int value) {
        order = value;
        notifyHierarchyChanged();
    }

    public void setFragment(String fragment) {
        this.fragment = fragment;
    }

    public String getFragment() {
        return fragment;
    }

    public Bundle getExtras() {
        if (extras == null) {
            extras = new Bundle();
        }
        return extras;
    }

    public Bundle peekExtras() {
        return extras;
    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent value) {
        intent = value;
    }

    public void setPreferenceDataStore(PreferenceDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public PreferenceDataStore getPreferenceDataStore() {
        if (dataStore != null) {
            return dataStore;
        }
        return manager != null ? manager.getPreferenceDataStore() : null;
    }

    public PreferenceManager getPreferenceManager() {
        return manager;
    }

    public SharedPreferences getSharedPreferences() {
        if (manager == null || getPreferenceDataStore() != null) {
            return null;
        }
        return manager.getSharedPreferences();
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    protected Object onGetDefaultValue(TypedArray a, int index) {
        return null;
    }

    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
    }

    protected boolean shouldPersist() {
        return (manager != null || getPreferenceDataStore() != null) && persistent && hasKey();
    }

    protected boolean persistString(String value) {
        if (!shouldPersist()) {
            return false;
        }
        if (java.util.Objects.equals(value, getPersistedString(null))) {
            return true;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            store.putString(key, value);
        } else {
            getSharedPreferences().edit().putString(key, value).apply();
        }
        return true;
    }

    protected String getPersistedString(String fallback) {
        if (!shouldPersist()) {
            return fallback;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            return store.getString(key, fallback);
        }
        return getSharedPreferences().getString(key, fallback);
    }

    public boolean persistStringSet(Set<String> values) {
        if (!shouldPersist()) {
            return false;
        }
        if (java.util.Objects.equals(values, getPersistedStringSet(null))) {
            return true;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            store.putStringSet(key, values);
        } else {
            getSharedPreferences().edit().putStringSet(key, values).apply();
        }
        return true;
    }

    public Set<String> getPersistedStringSet(Set<String> defaultReturnValue) {
        if (!shouldPersist()) {
            return defaultReturnValue;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            return store.getStringSet(key, defaultReturnValue);
        }
        return getSharedPreferences().getStringSet(key, defaultReturnValue);
    }

    protected boolean persistBoolean(boolean value) {
        if (!shouldPersist()) {
            return false;
        }
        if (value == getPersistedBoolean(!value)) {
            return true;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            store.putBoolean(key, value);
        } else {
            getSharedPreferences().edit().putBoolean(key, value).apply();
        }
        return true;
    }

    protected boolean getPersistedBoolean(boolean fallback) {
        if (!shouldPersist()) {
            return fallback;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            return store.getBoolean(key, fallback);
        }
        return getSharedPreferences().getBoolean(key, fallback);
    }

    protected boolean persistInt(int value) {
        if (!shouldPersist()) {
            return false;
        }
        if (value == getPersistedInt(~value)) {
            return true;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            store.putInt(key, value);
        } else {
            getSharedPreferences().edit().putInt(key, value).apply();
        }
        return true;
    }

    protected int getPersistedInt(int fallback) {
        if (!shouldPersist()) {
            return fallback;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            return store.getInt(key, fallback);
        }
        return getSharedPreferences().getInt(key, fallback);
    }

    protected boolean persistFloat(float value) {
        if (!shouldPersist()) {
            return false;
        }
        if (Float.compare(value, getPersistedFloat(Float.NaN)) == 0) {
            return true;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            store.putFloat(key, value);
        } else {
            getSharedPreferences().edit().putFloat(key, value).apply();
        }
        return true;
    }

    protected float getPersistedFloat(float fallback) {
        if (!shouldPersist()) {
            return fallback;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            return store.getFloat(key, fallback);
        }
        return getSharedPreferences().getFloat(key, fallback);
    }

    protected boolean persistLong(long value) {
        if (!shouldPersist()) {
            return false;
        }
        if (value == getPersistedLong(~value)) {
            return true;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            store.putLong(key, value);
        } else {
            getSharedPreferences().edit().putLong(key, value).apply();
        }
        return true;
    }

    protected long getPersistedLong(long fallback) {
        if (!shouldPersist()) {
            return fallback;
        }
        PreferenceDataStore store = getPreferenceDataStore();
        if (store != null) {
            return store.getLong(key, fallback);
        }
        return getSharedPreferences().getLong(key, fallback);
    }

    public void setOnPreferenceClickListener(OnPreferenceClickListener listener) {
        click = listener;
    }

    public OnPreferenceClickListener getOnPreferenceClickListener() {
        return click;
    }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {
        change = listener;
    }

    public OnPreferenceChangeListener getOnPreferenceChangeListener() {
        return change;
    }

    protected boolean callChangeListener(Object value) {
        return change == null || change.onPreferenceChange(this, value);
    }

    protected void onClick() {
    }

    void performClick(PreferenceScreen screen) {
        if (!isEnabled() || !selectable) {
            return;
        }
        onClick();
        if (click != null && click.onPreferenceClick(this)) {
            return;
        }
        if (manager != null && manager.clicked(screen, this)) {
            return;
        }
        if (intent != null) {
            context.startActivity(intent);
        }
    }

    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        manager = preferenceManager;
        dispatchSetInitialValue();
    }

    private void dispatchSetInitialValue() {
        if (getPreferenceDataStore() != null) {
            onSetInitialValue(true, defaultValue);
            return;
        }
        boolean shouldPersist = shouldPersist();
        SharedPreferences prefs = shouldPersist ? getSharedPreferences() : null;
        if (!shouldPersist || prefs == null || !prefs.contains(key)) {
            if (defaultValue != null) {
                onSetInitialValue(false, defaultValue);
            }
        } else {
            onSetInitialValue(true, null);
        }
    }

    void attach(PreferenceManager value) {
        if (manager != null && manager != value) {
            throw new IllegalStateException("Preference belongs to another manager");
        }
        onAttachedToHierarchy(value);
    }

    protected void notifyChanged() {
        if (manager != null) {
            manager.changed();
        }
    }

    protected void notifyHierarchyChanged() {
        notifyChanged();
    }

    public View getView(View convertView, ViewGroup parent) {
        View view = onCreateView(parent);
        onBindView(view);
        return view;
    }

    protected View onCreateView(ViewGroup parent) {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        outer.setPadding(padding, padding, padding, padding);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        outer.addView(textCol, textParams);

        TextView titleView = new TextView(context);
        titleView.setId(0x01020016);
        textCol.addView(titleView);

        TextView summaryView = new TextView(context);
        summaryView.setId(0x01020010);
        textCol.addView(summaryView);

        LinearLayout widgetFrame = new LinearLayout(context);
        widgetFrame.setId(0x01020018);
        widgetFrame.setOrientation(LinearLayout.VERTICAL);
        widgetFrame.setVisibility(View.GONE);
        outer.addView(widgetFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    protected void onBindView(View view) {
        TextView t = view.findViewById(0x01020016);
        TextView s = view.findViewById(0x01020010);
        if (t != null) {
            t.setText(title);
            t.setEnabled(isEnabled());
        }
        if (s != null) {
            CharSequence summaryText = getSummary();
            s.setText(summaryText);
            s.setVisibility(summaryText == null || summaryText.length() == 0 ? View.GONE : View.VISIBLE);
            s.setEnabled(isEnabled());
        }
        view.setEnabled(isEnabled());
    }

    public void saveHierarchyState(Bundle container) {
        dispatchSaveInstanceState(container);
    }

    void dispatchSaveInstanceState(Bundle container) {
        if (hasKey()) {
            baseMethodCalled = false;
            Parcelable state = onSaveInstanceState();
            if (!baseMethodCalled) {
                throw new IllegalStateException("Preference class must call super.onSaveInstanceState()");
            }
            if (state != null) {
                container.putParcelable(key, state);
            }
        }
    }

    protected Parcelable onSaveInstanceState() {
        baseMethodCalled = true;
        return BaseSavedState.EMPTY_STATE;
    }

    public void restoreHierarchyState(Bundle container) {
        dispatchRestoreInstanceState(container);
    }

    void dispatchRestoreInstanceState(Bundle container) {
        if (hasKey()) {
            Parcelable state = container.getParcelable(key);
            if (state != null) {
                baseMethodCalled = false;
                onRestoreInstanceState(state);
                if (!baseMethodCalled) {
                    throw new IllegalStateException("Preference class must call super.onRestoreInstanceState()");
                }
            }
        }
    }

    protected void onRestoreInstanceState(Parcelable state) {
        baseMethodCalled = true;
        if (state != BaseSavedState.EMPTY_STATE && state != null) {
            throw new IllegalArgumentException("Wrong state class -- expecting Preference State");
        }
    }

    @Override
    public int compareTo(Preference other) {
        int result = Integer.compare(order, other.order);
        if (result != 0) {
            return result;
        }
        return String.valueOf(title == null ? "" : title)
                .compareToIgnoreCase(String.valueOf(other.title == null ? "" : other.title));
    }
}
