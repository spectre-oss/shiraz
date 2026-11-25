// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Looper;
import android.preference.*;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import shiraz.runtime.BinaryXml;
import shiraz.runtime.CompiledXmlParser;
import shiraz.runtime.HostPreferences;

public final class PreferenceScreenSmoke {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static final class Screen extends PreferenceActivity {
        int clicks;

        Screen(Context c) {
            attachBaseContext(c);
        }

        public boolean onPreferenceTreeClick(PreferenceScreen s, Preference p) {
            clicks++;
            return true;
        }
    }

    static final class Stored extends Preference {
        Stored(Context c) {
            super(c);
        }

        void save(String text) {
            check(persistString(text), "not persisted");
        }

        String read() {
            return getPersistedString("absent");
        }
    }

    /** Minimal valid Android binary XML (AXML) encoder for preference inflation tests. */
    static final class AxmlBuilder {
        private final List<String> pool = new ArrayList<String>();
        private final List<Integer> resIds = new ArrayList<Integer>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        int attr(String name, int resId) {
            int idx = pool.size();
            pool.add(name);
            while (resIds.size() < idx) resIds.add(0);
            resIds.add(resId);
            return idx;
        }

        int str(String value) {
            int existing = pool.indexOf(value);
            if (existing >= 0) return existing;
            int idx = pool.size();
            pool.add(value);
            return idx;
        }

        void startTag(int nsIdx, int nameIdx, int[][] attrs) {
            int size = 36 + attrs.length * 20;
            ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort((short) 0x0102).putShort((short) 16).putInt(size).putInt(1).putInt(-1);
            buf.putInt(nsIdx).putInt(nameIdx);
            buf.putShort((short) 20).putShort((short) 20).putShort((short) attrs.length);
            buf.putShort((short) 0).putShort((short) 0).putShort((short) 0);
            for (int[] a : attrs) {
                buf.putInt(a[0]).putInt(a[1]).putInt(a[2]);
                buf.putShort((short) 8).put((byte) 0).put((byte) a[3]).putInt(a[4]);
            }
            body.write(buf.array(), 0, size);
        }

        void endTag(int nsIdx, int nameIdx) {
            ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort((short) 0x0103).putShort((short) 16).putInt(24).putInt(1).putInt(-1);
            buf.putInt(nsIdx).putInt(nameIdx);
            body.write(buf.array(), 0, 24);
        }

        byte[] finish() {
            ByteArrayOutputStream strData = new ByteArrayOutputStream();
            int[] offsets = new int[pool.size()];
            for (int i = 0; i < pool.size(); i++) {
                offsets[i] = strData.size();
                byte[] utf8 = pool.get(i).getBytes(StandardCharsets.UTF_8);
                strData.write(pool.get(i).length());
                strData.write(utf8.length);
                strData.write(utf8, 0, utf8.length);
                strData.write(0);
            }
            while (strData.size() % 4 != 0) strData.write(0);
            int poolHeader = 28;
            int stringsStart = poolHeader + pool.size() * 4;
            int poolSize = stringsStart + strData.size();
            ByteBuffer poolBuf = ByteBuffer.allocate(poolSize).order(ByteOrder.LITTLE_ENDIAN);
            poolBuf.putShort((short) 1).putShort((short) poolHeader).putInt(poolSize);
            poolBuf.putInt(pool.size()).putInt(0).putInt(256).putInt(stringsStart).putInt(0);
            for (int off : offsets) poolBuf.putInt(off);
            poolBuf.put(strData.toByteArray());

            int mapSize = 8 + resIds.size() * 4;
            ByteBuffer mapBuf = ByteBuffer.allocate(mapSize).order(ByteOrder.LITTLE_ENDIAN);
            mapBuf.putShort((short) 0x0180).putShort((short) 8).putInt(mapSize);
            for (int id : resIds) mapBuf.putInt(id);

            byte[] bodyBytes = body.toByteArray();
            int total = 8 + poolSize + mapSize + bodyBytes.length;
            ByteBuffer doc = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
            doc.putShort((short) 3).putShort((short) 8).putInt(total);
            doc.put(poolBuf.array()).put(mapBuf.array()).put(bodyBytes);
            return doc.array();
        }
    }

    private static byte[] samplePrefsXml() {
        AxmlBuilder b = new AxmlBuilder();
        int aKey = b.attr("key", 0x010101e8);
        int aTitle = b.attr("title", 0x010101e1);
        int aDefault = b.attr("defaultValue", 0x010101ed);
        int aAction = b.attr("action", 0x0101002d);
        int aName = b.attr("name", 0x01010003);
        int aValue = b.attr("value", 0x01010024);

        int ns = b.str(BinaryXml.ANDROID);
        int tScreen = b.str("PreferenceScreen");
        int tCat = b.str("PreferenceCategory");
        int tCheck = b.str("CheckBoxPreference");
        int tSwitch = b.str("SwitchPreference");
        int tEdit = b.str("EditTextPreference");
        int tList = b.str("ListPreference");
        int tPref = b.str("Preference");
        int tIntent = b.str("intent");
        int tExtra = b.str("extra");

        int sAudio = b.str("Audio");
        int sSound = b.str("sound");
        int sVibrate = b.str("vibrate");
        int sPlayer = b.str("player");
        int sAda = b.str("Ada");
        int sDifficulty = b.str("difficulty");
        int sHard = b.str("hard");
        int sHelp = b.str("help");
        int sHelpAction = b.str("com.example.HELP");
        int sPage = b.str("page");

        b.startTag(-1, tScreen, new int[0][]);
        b.startTag(-1, tCat, new int[][] {{ns, aTitle, sAudio, 3, sAudio}});
        b.startTag(
                -1,
                tCheck,
                new int[][] {{ns, aKey, sSound, 3, sSound}, {ns, aDefault, -1, 18, -1}});
        b.endTag(-1, tCheck);
        b.startTag(
                -1,
                tSwitch,
                new int[][] {{ns, aKey, sVibrate, 3, sVibrate}, {ns, aDefault, -1, 18, 0}});
        b.endTag(-1, tSwitch);
        b.endTag(-1, tCat);
        b.startTag(
                -1,
                tEdit,
                new int[][] {{ns, aKey, sPlayer, 3, sPlayer}, {ns, aDefault, sAda, 3, sAda}});
        b.endTag(-1, tEdit);
        b.startTag(
                -1,
                tList,
                new int[][] {{ns, aKey, sDifficulty, 3, sDifficulty}, {ns, aDefault, sHard, 3, sHard}});
        b.endTag(-1, tList);
        b.startTag(-1, tPref, new int[][] {{ns, aKey, sHelp, 3, sHelp}});
        b.startTag(-1, tIntent, new int[][] {{ns, aAction, sHelpAction, 3, sHelpAction}});
        b.endTag(-1, tIntent);
        b.startTag(
                -1,
                tExtra,
                new int[][] {{ns, aName, sPage, 3, sPage}, {ns, aValue, -1, 16, 2}});
        b.endTag(-1, tExtra);
        b.endTag(-1, tPref);
        b.endTag(-1, tScreen);
        return b.finish();
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        final HostPreferences storage =
                new HostPreferences(Files.createTempDirectory("preference-smoke").toFile());
        final byte[] prefsXml = samplePrefsXml();
        final int xmlResId = 0x7f0b0001;
        final Resources resources =
                new Resources(null) {
                    @Override
                    public XmlResourceParser getXml(int id) {
                        if (id == xmlResId) {
                            return new CompiledXmlParser(prefsXml, id);
                        }
                        return super.getXml(id);
                    }
                };
        resources.setImpl(Resources.getSystem().getImpl());
        Context context =
                new ContextWrapper(null) {
                    final Resources.Theme theme = resources.newTheme();

                    public Resources getResources() {
                        return resources;
                    }

                    public Resources.Theme getTheme() {
                        return theme;
                    }

                    public String getPackageName() {
                        return "fixture";
                    }

                    public Object getSystemService(String name) {
                        return null;
                    }

                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return storage.get(name, mode);
                    }
                };
        try {
            Screen activity = new Screen(context);
            PreferenceScreen root = activity.getPreferenceManager().createPreferenceScreen(activity);
            Stored first = new Stored(activity);
            first.setKey("value");
            first.setTitle("Value");
            first.setSummary("Description");
            root.addPreference(first);
            activity.setPreferenceScreen(root);
            first.save("saved");
            check("saved".equals(first.read()), "stored value");
            View row = activity.getListAdapter().getView(0, null, activity.getListView());
            check(
                    "Value".contentEquals(((android.widget.TextView) row.findViewById(0x01020016)).getText()),
                    "title rendering");
            activity.getListView().performItemClick(row, 0, 0);
            check(activity.clicks == 1, "tree click");
            first.setEnabled(false);
            activity.getListView().performItemClick(row, 0, 0);
            check(activity.clicks == 1, "disabled click");
            first.setEnabled(true);
            first.setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener() {
                        public boolean onPreferenceClick(Preference p) {
                            return true;
                        }
                    });
            activity.getListView().performItemClick(row, 0, 0);
            check(activity.clicks == 1, "consumed click reached tree");
            Preference second = new Preference(activity);
            second.setOrder(-1);
            second.setKey("second");
            root.addPreference(second);
            check(
                    activity.getListAdapter().getCount() == 2 && root.getPreference(0) == second,
                    "live insertion/order");
            check(activity.findPreference("value") == first, "key lookup");
            root.removePreference(first);
            check(activity.findPreference("value") == null, "removed key");
            Screen reopened = new Screen(context);
            PreferenceScreen again = reopened.getPreferenceManager().createPreferenceScreen(reopened);
            Stored restored = new Stored(reopened);
            restored.setKey("value");
            again.addPreference(restored);
            reopened.setPreferenceScreen(again);
            check("saved".equals(restored.read()), "screen recreation persistence");
            try {
                again.addPreference(again);
                throw new AssertionError("cycle accepted");
            } catch (IllegalArgumentException expected) {
            }
            try {
                again.addPreference(second);
                throw new AssertionError("two parents accepted");
            } catch (IllegalStateException expected) {
            }
            try {
                again.addPreference(first);
                throw new AssertionError("foreign manager accepted");
            } catch (IllegalStateException expected) {
            }
            check(again.getPreferenceCount() == 1, "failed attachment changed hierarchy");
            PreferenceScreen chain = new PreferenceScreen(activity, null);
            for (int i = 0; i < 32; i++) {
                PreferenceScreen parent = new PreferenceScreen(activity, null);
                parent.addPreference(chain);
                chain = parent;
            }
            try {
                new PreferenceScreen(activity, null).addPreference(chain);
                throw new AssertionError("deep subtree accepted");
            } catch (IllegalStateException expected) {
            }

            // PreferenceManager.setDefaultValues + XML preference inflation.
            PreferenceManager.setDefaultValues(context, xmlResId, false);
            SharedPreferences defaults = PreferenceManager.getDefaultSharedPreferences(context);
            check(defaults.getBoolean("sound", false), "checkbox XML default true");
            check(defaults.contains("vibrate") && !defaults.getBoolean("vibrate", true), "switch XML default false");
            check("Ada".equals(defaults.getString("player", null)), "edit text XML default");
            check("hard".equals(defaults.getString("difficulty", null)), "list XML default");
            check(
                    context.getSharedPreferences(
                                    PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE)
                            .getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false),
                    "default values sentinel persisted");

            // User overrides stay intact; readAgain=false skips missing keys; readAgain=true fills missing keys only.
            defaults.edit().putBoolean("sound", false).putString("player", "Bob").remove("difficulty").apply();
            PreferenceManager.setDefaultValues(context, xmlResId, false);
            check(!defaults.contains("difficulty"), "readAgain=false skips once sentinel is set");
            PreferenceManager.setDefaultValues(context, xmlResId, true);
            check(!defaults.getBoolean("sound", true), "readAgain=true preserves user boolean");
            check("Bob".equals(defaults.getString("player", null)), "readAgain=true preserves user string");
            check("hard".equals(defaults.getString("difficulty", null)), "readAgain=true restores missing default");

            // Named shared preferences overload.
            PreferenceManager.setDefaultValues(context, "custom_prefs", Context.MODE_PRIVATE, xmlResId, true);
            SharedPreferences custom = context.getSharedPreferences("custom_prefs", Context.MODE_PRIVATE);
            check("Ada".equals(custom.getString("player", null)), "named setDefaultValues persisted");

            // PreferenceActivity.addPreferencesFromResource inflates nested hierarchy, intent and extras.
            Screen xmlActivity = new Screen(context);
            xmlActivity.addPreferencesFromResource(xmlResId);
            Preference help = xmlActivity.findPreference("help");
            check(help != null && help.getIntent() != null, "XML intent child parsed");
            check("com.example.HELP".equals(help.getIntent().getAction()), "XML intent action");
            check(help.getExtras().getInt("page", 0) == 2, "XML extra child parsed");
            try {
                xmlActivity.addPreferencesFromResource(123);
                throw new AssertionError("missing XML resource succeeded");
            } catch (Resources.NotFoundException expected) {
            }
            System.out.println("PREFERENCE_SCREEN_OK");
        } finally {
            storage.close();
        }
    }
}
