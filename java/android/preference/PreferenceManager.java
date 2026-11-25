// SPDX-License-Identifier: GPL-3.0-only
package android.preference;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.InflateException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class PreferenceManager {
    public static final String KEY_HAS_SET_DEFAULT_VALUES = "_has_set_default_values";
    private static final int MAX_XML_DEPTH = 32;
    private static final int MAX_XML_NODES = 1024;
    private static final String INTENT_TAG_NAME = "intent";
    private static final String EXTRA_TAG_NAME = "extra";
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

    public static int getDefaultSharedPreferencesMode() {
        return Context.MODE_PRIVATE;
    }

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences(
                getDefaultSharedPreferencesName(context), getDefaultSharedPreferencesMode());
    }

    public static void setDefaultValues(Context context, int resId, boolean readAgain) {
        setDefaultValues(
                context,
                getDefaultSharedPreferencesName(context),
                getDefaultSharedPreferencesMode(),
                resId,
                readAgain);
    }

    public static void setDefaultValues(
            Context context,
            String sharedPreferencesName,
            int sharedPreferencesMode,
            int resId,
            boolean readAgain) {
        Objects.requireNonNull(context, "context");
        SharedPreferences defaultValueSp =
                context.getSharedPreferences(KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE);
        if (readAgain || !defaultValueSp.getBoolean(KEY_HAS_SET_DEFAULT_VALUES, false)) {
            PreferenceManager pm = new PreferenceManager(context);
            pm.setSharedPreferencesName(sharedPreferencesName);
            pm.setSharedPreferencesMode(sharedPreferencesMode);
            pm.inflateFromResource(context, resId, null);
            defaultValueSp.edit().putBoolean(KEY_HAS_SET_DEFAULT_VALUES, true).apply();
        }
    }

    public PreferenceScreen inflateFromResource(
            Context context, int resId, PreferenceScreen rootPreferences) {
        Objects.requireNonNull(context, "context");
        XmlResourceParser parser = context.getResources().getXml(resId);
        try {
            int type = parser.next();
            while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                type = parser.next();
            }
            if (type != XmlPullParser.START_TAG) {
                throw new InflateException(parser.getPositionDescription() + ": No start tag found");
            }
            int[] nodes = {0};
            Preference root = inflatePreference(context, parser, 0, nodes);
            PreferenceScreen target;
            if (rootPreferences == null) {
                if (root instanceof PreferenceScreen) {
                    target = (PreferenceScreen) root;
                } else {
                    target = new PreferenceScreen(context, null);
                    target.addPreference(root);
                }
                target.attach(this);
                return target;
            }
            if (root instanceof PreferenceGroup) {
                List<Preference> children = ((PreferenceGroup) root).takeChildren();
                for (int i = 0; i < children.size(); i++) {
                    rootPreferences.addPreference(children.get(i));
                }
            } else {
                rootPreferences.addPreference(root);
            }
            return rootPreferences;
        } catch (XmlPullParserException | IOException error) {
            throw new InflateException("Cannot inflate preference XML resource #" + Integer.toHexString(resId), error);
        } finally {
            parser.close();
        }
    }

    private static Preference inflatePreference(
            Context context, XmlResourceParser parser, int depth, int[] nodes)
            throws XmlPullParserException, IOException {
        if (depth > MAX_XML_DEPTH) {
            throw new InflateException("Preference XML depth limit exceeded");
        }
        if (++nodes[0] > MAX_XML_NODES) {
            throw new InflateException("Preference XML element limit exceeded");
        }
        Preference preference = createPreference(context, parser.getName(), parser);
        int parentDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > parentDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            String childName = parser.getName();
            if (INTENT_TAG_NAME.equals(childName)) {
                preference.setIntent(parseIntentElement(context, parser));
            } else if (EXTRA_TAG_NAME.equals(childName)) {
                parseExtraElement(context, parser, preference.getExtras());
                skipElement(parser);
            } else {
                Preference child = inflatePreference(context, parser, depth + 1, nodes);
                if (preference instanceof PreferenceGroup) {
                    ((PreferenceGroup) preference).addPreference(child);
                }
            }
        }
        return preference;
    }

    private static Preference createPreference(Context context, String tagName, AttributeSet attrs) {
        String className = tagName.indexOf('.') >= 0 ? tagName : "android.preference." + tagName;
        Class<?> raw;
        try {
            ClassLoader loader = context.getClassLoader();
            raw = loader != null ? loader.loadClass(className) : Class.forName(className);
        } catch (ClassNotFoundException primary) {
            try {
                raw = PreferenceManager.class.getClassLoader().loadClass(className);
            } catch (ClassNotFoundException fallback) {
                throw new InflateException("Cannot load preference class: " + className, primary);
            }
        }
        if (!Preference.class.isAssignableFrom(raw)) {
            throw new InflateException("XML tag is not a Preference: " + className);
        }
        Class<? extends Preference> prefClass = raw.asSubclass(Preference.class);
        try {
            try {
                Constructor<? extends Preference> ctor =
                        prefClass.getConstructor(Context.class, AttributeSet.class);
                return ctor.newInstance(context, attrs);
            } catch (NoSuchMethodException ignored) {
                try {
                    Constructor<? extends Preference> ctor =
                            prefClass.getConstructor(Context.class, AttributeSet.class, int.class);
                    return ctor.newInstance(context, attrs, Integer.valueOf(0));
                } catch (NoSuchMethodException ignoredAgain) {
                    Constructor<? extends Preference> ctor = prefClass.getConstructor(Context.class);
                    return ctor.newInstance(context);
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new InflateException("Cannot instantiate preference: " + className, error);
        }
    }

    private static Intent parseIntentElement(Context context, XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        shiraz.runtime.XmlAttributes x = new shiraz.runtime.XmlAttributes(context, parser);
        Intent intent = new Intent();
        String action = x.text("action");
        if (action != null) {
            intent.setAction(action);
        }
        String data = x.text("data");
        String mimeType = x.text("mimeType");
        if (data != null && mimeType != null) {
            intent.setDataAndType(Uri.parse(data), mimeType);
        } else if (data != null) {
            intent.setData(Uri.parse(data));
        } else if (mimeType != null) {
            intent.setType(mimeType);
        }
        String pkg = x.text("targetPackage");
        String cls = x.text("targetClass");
        if (pkg != null && cls != null) {
            intent.setComponent(new ComponentName(pkg, cls));
        }
        Bundle extras = new Bundle();
        int depth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > depth)) {
            if (type == XmlPullParser.START_TAG) {
                if (EXTRA_TAG_NAME.equals(parser.getName())) {
                    parseExtraElement(context, parser, extras);
                }
                skipElement(parser);
            }
        }
        if (!extras.isEmpty()) {
            intent.putExtras(extras);
        }
        return intent;
    }

    private static void parseExtraElement(Context context, AttributeSet attrs, Bundle target) {
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {0x01010003, 0x01010024});
        try {
            String extraName = a.getString(0);
            if (extraName == null || !a.hasValue(1)) {
                return;
            }
            TypedValue value = a.peekValue(1);
            if (value == null) {
                return;
            }
            if (value.type == TypedValue.TYPE_INT_BOOLEAN) {
                target.putBoolean(extraName, value.data != 0);
            } else if (value.type == TypedValue.TYPE_FLOAT) {
                target.putFloat(extraName, value.getFloat());
            } else if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) {
                target.putInt(extraName, value.data);
            } else {
                target.putString(extraName, a.getString(1));
            }
        } finally {
            a.recycle();
        }
    }

    private static void skipElement(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        int depth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > depth)) {
            // Skip nested content.
        }
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
