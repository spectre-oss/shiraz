// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.net.Uri;
import android.os.*;
import java.io.Serializable;
import java.util.*;

/** Intent routing and extras carrier; activity/provider/IPC dispatch is separate. */
public class Intent implements Parcelable, Cloneable {
    public static final String ACTION_MAIN = "android.intent.action.MAIN", ACTION_VIEW = "android.intent.action.VIEW",
        ACTION_SEND = "android.intent.action.SEND", ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT",
        ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT", ACTION_PICK = "android.intent.action.PICK",
        ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE",
        ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED",
        ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON", ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF",
        ACTION_LOCALE_CHANGED = "android.intent.action.LOCALE_CHANGED", ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED",
        CATEGORY_DEFAULT = "android.intent.category.DEFAULT", CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER",
        CATEGORY_OPENABLE = "android.intent.category.OPENABLE",
        EXTRA_TEXT = "android.intent.extra.TEXT", EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000, FLAG_ACTIVITY_CLEAR_TOP = 0x04000000, FLAG_ACTIVITY_SINGLE_TOP = 0x20000000, FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000,
        FLAG_ACTIVITY_NO_ANIMATION = 0x00010000, FLAG_GRANT_READ_URI_PERMISSION = 0x00000001, FLAG_GRANT_WRITE_URI_PERMISSION = 0x00000002, FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x00000040, FLAG_GRANT_PREFIX_URI_PERMISSION = 0x00000080,
        FLAG_RECEIVER_REGISTERED_ONLY = 0x40000000, FLAG_RECEIVER_FOREGROUND = 0x10000000, FLAG_RECEIVER_REPLACE_PENDING = 0x20000000;
    public static final int URI_INTENT_SCHEME=1,URI_ANDROID_APP_SCHEME=2,URI_ALLOW_UNSAFE=4;
    private Intent selector;
    private android.graphics.Rect sourceBounds;
    private String action, type, pkg;
    private Uri data;
    private ComponentName component;
    private int flags;
    private LinkedHashSet<String> categories;
    private Bundle extras;
    public ComponentName resolveActivity(android.content.pm.PackageManager manager){if(component!=null)return component;android.content.pm.ResolveInfo resolved=manager.resolveActivity(this,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);return resolved==null||resolved.activityInfo==null?null:new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name);}
    public android.content.pm.ActivityInfo resolveActivityInfo(android.content.pm.PackageManager manager,int flags){if(component!=null){try{return manager.getActivityInfo(component,flags);}catch(android.content.pm.PackageManager.NameNotFoundException missing){return null;}}android.content.pm.ResolveInfo resolved=manager.resolveActivity(this,flags);return resolved==null?null:resolved.activityInfo;}
    public static Intent makeMainActivity(ComponentName component){return new Intent(ACTION_MAIN).setComponent(component).addCategory(CATEGORY_LAUNCHER);}
    public static Intent makeMainSelectorActivity(String selectorAction,String selectorCategory){Intent main=makeMainActivity(null);main.setSelector(new Intent(selectorAction).addCategory(selectorCategory));return main;}
    public Intent() { }
    public Intent(String action) { this.action = action; }
    public Intent(String action, Uri uri) { this.action = action; data = uri; }
    public Intent(Context context, Class<?> cls) { component = new ComponentName(context, cls); }
    public Intent(String action, Uri uri, Context context, Class<?> cls) { this(action, uri); component = new ComponentName(context, cls); }
    public Intent(Intent source) { this(source,0); }
    private Intent(Intent source,int depth) { if(depth>32)throw new IllegalArgumentException("Intent selector depth");selector=source.selector==null?null:new Intent(source.selector,depth+1);sourceBounds=source.sourceBounds==null?null:new android.graphics.Rect(source.sourceBounds);action = source.action; type = source.type; pkg = source.pkg; data = source.data; component = source.component; flags = source.flags; categories = source.categories == null ? null : new LinkedHashSet<String>(source.categories); extras = source.extras == null ? null : new Bundle(source.extras); }
    public Intent cloneFilter() { Intent value = new Intent(this); value.extras = null; return value; }
    @Override public Object clone() { return new Intent(this); }
    public String getAction() { return action; }
    public Intent setAction(String value) { action = value; return this; }
    public Uri getData() { return data; }
    public String getDataString() { return data == null ? null : data.toString(); }
    public String getScheme() { return data == null ? null : data.getScheme(); }
    public String getType() { return type; }
    public Intent setData(Uri value) { data = value; type = null; return this; }
    public Intent setType(String value) { type = value; data = null; return this; }
    public Intent setDataAndType(Uri uri, String value) { data = uri; type = value; return this; }
    public static String normalizeMimeType(String value) { if (value == null) return null; value = value.trim().toLowerCase(Locale.ROOT); int semi = value.indexOf(';'); return semi < 0 ? value : value.substring(0, semi); }
    public Intent setTypeAndNormalize(String value) { return setType(normalizeMimeType(value)); }
    public String getPackage() { return pkg; }
    public Intent setPackage(String value) { if(value!=null&&selector!=null)throw new IllegalArgumentException("Package and selector are mutually exclusive");pkg = value; return this; }
    public Intent getSelector(){return selector;}
    public void setSelector(Intent value){
        if(value!=null&&pkg!=null)throw new IllegalArgumentException("Package and selector are mutually exclusive");
        int count=0;for(Intent next=value;next!=null;next=next.selector)if(next==this||++count>32)throw new IllegalArgumentException("Intent selector cycle/depth");selector=value;
    }
    public android.graphics.Rect getSourceBounds(){return sourceBounds;}
    public void setSourceBounds(android.graphics.Rect value){sourceBounds=value==null?null:new android.graphics.Rect(value);}
    public static Intent parseUri(String text,int options)throws java.net.URISyntaxException{return IntentUri.parse(text,options);}

    public ComponentName getComponent() { return component; }
    public Intent setComponent(ComponentName value) { component = value; return this; }
    public Intent setClass(Context context, Class<?> cls) { component = new ComponentName(context, cls); return this; }
    public Intent setClassName(Context context, String cls) { component = new ComponentName(context, cls); return this; }
    public Intent setClassName(String pkg, String cls) { component = new ComponentName(pkg, cls); return this; }
    public int getFlags() { return flags; }
    public Intent setFlags(int value) { flags = value; return this; }
    public Intent addFlags(int value) { flags |= value; return this; }
    public void removeFlags(int value) { flags &= ~value; }
    public Intent addCategory(String category) { if (categories == null) categories = new LinkedHashSet<String>(); categories.add(Objects.requireNonNull(category)); return this; }
    public void removeCategory(String value) { if (categories != null) { categories.remove(value); if (categories.isEmpty()) categories = null; } }
    public boolean hasCategory(String value) { return categories != null && categories.contains(value); }
    public Set<String> getCategories() { return categories == null ? null : Collections.unmodifiableSet(categories); }
    private Bundle values() { if (extras == null) extras = new Bundle(); return extras; }
    public Bundle getExtras() { return extras == null ? null : new Bundle(extras); }
    public boolean hasExtra(String key) { return extras != null && extras.containsKey(key); }
    public boolean hasFileDescriptors() { return extras != null && extras.hasFileDescriptors(); }
    public void removeExtra(String key) { if (extras != null) extras.remove(key); }
    public Intent putExtras(Bundle value) { values().putAll(value); return this; }
    public Intent putExtras(Intent value) { if (value.extras != null) values().putAll(value.extras); return this; }
    public Intent replaceExtras(Bundle value) { extras = value == null ? null : new Bundle(value); return this; }
    public Intent replaceExtras(Intent value) { return replaceExtras(value.extras); }
    public Intent putExtra(String key, Serializable value) { values().putSerializable(key, value); return this; }
    public Serializable getSerializableExtra(String key) { return extras == null ? null : extras.getSerializable(key); }
    public <T extends Serializable> T getSerializableExtra(String key, Class<T> type) { return extras == null ? null : extras.getSerializable(key, type); }
    public static final String ACTION_CHOOSER = "android.intent.action.CHOOSER", EXTRA_INTENT = "android.intent.extra.INTENT", EXTRA_TITLE = "android.intent.extra.TITLE";
    public static Intent createChooser(Intent target, CharSequence title) {
        Intent chooser = new Intent(ACTION_CHOOSER);
        chooser.putExtra(EXTRA_INTENT, (Parcelable) target);
        if (title != null) chooser.putExtra(EXTRA_TITLE, title);
        return chooser;
    }
    public Intent putExtra(String key, Parcelable value) { values().putParcelable(key, value); return this; }
    public <T extends Parcelable> T getParcelableExtra(String key) { return extras == null ? null : extras.<T>getParcelable(key); }
    public <T> T getParcelableExtra(String key, Class<T> type) { return extras == null ? null : extras.getParcelable(key, type); }
    public Intent putExtra(String key, Parcelable[] value) { values().putParcelableArray(key, value); return this; }
    public Parcelable[] getParcelableArrayExtra(String key) { return extras == null ? null : extras.getParcelableArray(key); }
    public Intent putStringArrayListExtra(String key, ArrayList<String> value) { values().putStringArrayList(key, value); return this; }
    public ArrayList<String> getStringArrayListExtra(String key) { return extras == null ? null : extras.getStringArrayList(key); }
    public Intent putIntegerArrayListExtra(String key, ArrayList<Integer> value) { values().putIntegerArrayList(key, value); return this; }
    public ArrayList<Integer> getIntegerArrayListExtra(String key) { return extras == null ? null : extras.getIntegerArrayList(key); }
    public Intent putParcelableArrayListExtra(String key, ArrayList<? extends Parcelable> value) { values().putParcelableArrayList(key, value); return this; }
    public <T extends Parcelable> ArrayList<T> getParcelableArrayListExtra(String key) { return extras == null ? null : extras.<T>getParcelableArrayList(key); }
    public Intent putExtra(String key, boolean value) { values().putBoolean(key, value); return this; }
    public boolean getBooleanExtra(String key, boolean fallback) { return extras == null ? fallback : extras.getBoolean(key, fallback); }
    public Intent putExtra(String key, byte value) { values().putByte(key, value); return this; }
    public byte getByteExtra(String key, byte fallback) { return extras == null ? fallback : extras.getByte(key, fallback); }
    public Intent putExtra(String key, char value) { values().putChar(key, value); return this; }
    public char getCharExtra(String key, char fallback) { return extras == null ? fallback : extras.getChar(key, fallback); }
    public Intent putExtra(String key, short value) { values().putShort(key, value); return this; }
    public short getShortExtra(String key, short fallback) { return extras == null ? fallback : extras.getShort(key, fallback); }
    public Intent putExtra(String key, int value) { values().putInt(key, value); return this; }
    public int getIntExtra(String key, int fallback) { return extras == null ? fallback : extras.getInt(key, fallback); }
    public Intent putExtra(String key, long value) { values().putLong(key, value); return this; }
    public long getLongExtra(String key, long fallback) { return extras == null ? fallback : extras.getLong(key, fallback); }
    public Intent putExtra(String key, float value) { values().putFloat(key, value); return this; }
    public float getFloatExtra(String key, float fallback) { return extras == null ? fallback : extras.getFloat(key, fallback); }
    public Intent putExtra(String key, double value) { values().putDouble(key, value); return this; }
    public double getDoubleExtra(String key, double fallback) { return extras == null ? fallback : extras.getDouble(key, fallback); }
    public Intent putExtra(String key, String value) { values().putString(key, value); return this; }
    public String getStringExtra(String key) { return extras == null ? null : extras.getString(key); }
    public Intent putExtra(String key, CharSequence value) { values().putCharSequence(key, value); return this; }
    public CharSequence getCharSequenceExtra(String key) { return extras == null ? null : extras.getCharSequence(key); }
    public Intent putExtra(String key, Bundle value) { values().putBundle(key, value); return this; }
    public Bundle getBundleExtra(String key) { return extras == null ? null : extras.getBundle(key); }
    public Intent putExtra(String key, boolean[] value) { values().putBooleanArray(key, value); return this; }
    public boolean[] getBooleanArrayExtra(String key) { return extras == null ? null : extras.getBooleanArray(key); }
    public Intent putExtra(String key, byte[] value) { values().putByteArray(key, value); return this; }
    public byte[] getByteArrayExtra(String key) { return extras == null ? null : extras.getByteArray(key); }
    public Intent putExtra(String key, char[] value) { values().putCharArray(key, value); return this; }
    public char[] getCharArrayExtra(String key) { return extras == null ? null : extras.getCharArray(key); }
    public Intent putExtra(String key, short[] value) { values().putShortArray(key, value); return this; }
    public short[] getShortArrayExtra(String key) { return extras == null ? null : extras.getShortArray(key); }
    public Intent putExtra(String key, int[] value) { values().putIntArray(key, value); return this; }
    public int[] getIntArrayExtra(String key) { return extras == null ? null : extras.getIntArray(key); }
    public Intent putExtra(String key, long[] value) { values().putLongArray(key, value); return this; }
    public long[] getLongArrayExtra(String key) { return extras == null ? null : extras.getLongArray(key); }
    public Intent putExtra(String key, float[] value) { values().putFloatArray(key, value); return this; }
    public float[] getFloatArrayExtra(String key) { return extras == null ? null : extras.getFloatArray(key); }
    public Intent putExtra(String key, double[] value) { values().putDoubleArray(key, value); return this; }
    public double[] getDoubleArrayExtra(String key) { return extras == null ? null : extras.getDoubleArray(key); }
    public Intent putExtra(String key, String[] value) { values().putStringArray(key, value); return this; }
    public String[] getStringArrayExtra(String key) { return extras == null ? null : extras.getStringArray(key); }
    public Intent putExtra(String key, CharSequence[] value) { values().putCharSequenceArray(key, value); return this; }
    public CharSequence[] getCharSequenceArrayExtra(String key) { return extras == null ? null : extras.getCharSequenceArray(key); }
    private int broadcastBytes;
    public int shirazBroadcastBytes() { return broadcastBytes; }
    public Intent shirazBroadcastCopy() {
        Intent value = new Intent(this); value.extras = extras == null ? null : extras.shirazBroadcastCopy();
        long bytes = 256 + (value.extras == null ? 0 : value.extras.shirazBroadcastCopiedBytes());
        java.util.ArrayList<String> texts = new java.util.ArrayList<String>(java.util.Arrays.asList(action, type, pkg, data == null ? null : data.toString(), component == null ? null : component.flattenToString()));
        if (categories != null) { if (categories.size() > 1024) throw new IllegalArgumentException("Broadcast category limit exceeded"); texts.addAll(categories); }
        for (String text : texts) if (text != null) { if (text.length() > 65536) throw new IllegalArgumentException("Broadcast routing text exceeds host limit"); bytes += 32L + text.length() * 2L; }
        if(selector!=null){value.selector=selector.shirazBroadcastCopy();bytes+=value.selector.broadcastBytes;}
        if (bytes > 4 * 1024 * 1024) throw new IllegalArgumentException("Broadcast intent exceeds host limit");
        value.broadcastBytes = (int)bytes; return value;
    }
    public int describeContents() { return (extras == null ? 0 : extras.describeContents())|(selector==null?0:selector.describeContents()); }
    public void writeToParcel(Parcel out, int flags) { writeParcel(out,flags,0); }
    private void writeParcel(Parcel out,int flags,int depth) { if(depth>32)throw new IllegalArgumentException("Intent selector depth");out.writeString(action);out.writeString(type);out.writeString(pkg);out.writeString(getDataString());
        out.writeString(component==null?null:component.flattenToString());out.writeInt(this.flags);
        if(categories!=null&&categories.size()>1024)throw new IllegalArgumentException("Intent category limit");
        out.writeStringArray(categories==null?null:categories.toArray(new String[categories.size()]));out.writeBundle(extras);
        out.writeInt(sourceBounds==null?0:1);if(sourceBounds!=null){out.writeInt(sourceBounds.left);out.writeInt(sourceBounds.top);out.writeInt(sourceBounds.right);out.writeInt(sourceBounds.bottom);}
        out.writeInt(selector==null?0:1);if(selector!=null)selector.writeParcel(out,flags,depth+1); }
    private static Intent readParcel(Parcel in,int depth){if(depth>32)throw new IllegalArgumentException("Intent selector depth"); Intent result=new Intent();result.action=in.readString();result.type=in.readString();result.pkg=in.readString();String uri=in.readString(),name=in.readString();
            result.data=uri==null?null:Uri.parse(uri);result.component=name==null?null:ComponentName.unflattenFromString(name);
            if(name!=null&&result.component==null)throw new IllegalArgumentException("Invalid intent component");result.flags=in.readInt();
            String[] categories=in.createStringArray();if(categories!=null){if(categories.length>1024)throw new IllegalArgumentException("Intent category limit");for(String category:categories)result.addCategory(category);}
            result.extras=in.readBundle(Intent.class.getClassLoader());
            if(in.readInt()!=0)result.sourceBounds=new android.graphics.Rect(in.readInt(),in.readInt(),in.readInt(),in.readInt());
            if(in.readInt()!=0)result.setSelector(readParcel(in,depth+1));return result; }
    public static final Parcelable.Creator<Intent> CREATOR = new Parcelable.Creator<Intent>() {
        public Intent createFromParcel(Parcel in){return readParcel(in,0);}
        public Intent[] newArray(int size) { return new Intent[size]; }
    };
}
