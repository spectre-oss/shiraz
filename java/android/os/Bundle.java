// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Android's Bundle hierarchy with eager values, shallow copies and bounded eager parcel transport. */
public final class Bundle extends BaseBundle implements Parcelable, Cloneable {
    public static final Bundle EMPTY = new Bundle(true);
    public Bundle() { super(); }
    public Bundle(int capacity) { super(capacity); }
    public Bundle(ClassLoader loader) { super(); if (loader != null) this.loader = loader; }
    public Bundle(Bundle other) { super(other); }
    private Bundle(boolean immutable) { super(immutable); }
    @Override public Object clone() { return new Bundle(this); }
    public void putAll(Bundle other) { values.putAll(other.values); }
    public void putBundle(String key, Bundle item) { values.put(key, item); }
    public Bundle getBundle(String key) { return value(key, Bundle.class, null); }
    public void putCharSequence(String key, CharSequence item) { values.put(key, item); }
    public CharSequence getCharSequence(String key) { return getCharSequence(key, null); }
    public CharSequence getCharSequence(String key, CharSequence fallback) { return value(key, CharSequence.class, fallback); }
    public void putCharSequenceArray(String key, CharSequence[] item) { values.put(key, item); }
    public CharSequence[] getCharSequenceArray(String key) { return value(key, CharSequence[].class, null); }
    public void putSerializable(String key, Serializable item) { values.put(key, item); }
    public Serializable getSerializable(String key) { return value(key, Serializable.class, null); }
    public <T extends Serializable> T getSerializable(String key, Class<T> type) { return value(key, java.util.Objects.requireNonNull(type), null); }
    public void putBinder(String key, IBinder item) { values.put(key, item); }
    public IBinder getBinder(String key) { return value(key, IBinder.class, null); }
    public void putParcelable(String key, Parcelable item) { values.put(key, item); }
    public <T extends Parcelable> T getParcelable(String key) { return (T)value(key, Parcelable.class, null); }
    public <T> T getParcelable(String key, Class<T> type) { return value(key, java.util.Objects.requireNonNull(type), null); }
    public void putParcelableArray(String key, Parcelable[] item) { values.put(key, item); }
    public Parcelable[] getParcelableArray(String key) { return value(key, Parcelable[].class, null); }
    public void putStringArrayList(String key, ArrayList<String> item) { values.put(key, item); }
    public ArrayList<String> getStringArrayList(String key) { return (ArrayList<String>)value(key, ArrayList.class, null); }
    public void putIntegerArrayList(String key, ArrayList<Integer> item) { values.put(key, item); }
    public ArrayList<Integer> getIntegerArrayList(String key) { return (ArrayList<Integer>)value(key, ArrayList.class, null); }
    public void putParcelableArrayList(String key, ArrayList<? extends Parcelable> item) { values.put(key, item); }
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(String key) { return (ArrayList<T>)value(key, ArrayList.class, null); }
    public void putSparseParcelableArray(String key,android.util.SparseArray<? extends Parcelable> value){values.put(key,value);}
    @SuppressWarnings("unchecked") public <T extends Parcelable> android.util.SparseArray<T> getSparseParcelableArray(String key){return (android.util.SparseArray<T>)value(key,android.util.SparseArray.class,null);}
    private int broadcastBytes;
    private static final class BroadcastBudget {
        int bytes, items;
        void add(long amount) { if (amount < 0 || amount > 4 * 1024 * 1024L - bytes || ++items > 65536) throw new IllegalArgumentException("Broadcast extras exceed host limit"); bytes += (int)amount; }
    }
    public Bundle shirazBroadcastCopy() {
        BroadcastBudget budget = new BroadcastBudget();
        Bundle result = (Bundle)broadcastValue(this, new IdentityHashMap<Object, Boolean>(), budget, 0);
        result.broadcastBytes = budget.bytes; return result;
    }
    public int shirazBroadcastCopiedBytes() { return broadcastBytes; }
    private static Object broadcastValue(Object value, IdentityHashMap<Object, Boolean> active, BroadcastBudget budget, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Broadcast extras nesting exceeds host limit");
        budget.add(32);
        if (value == null || value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double || value instanceof Character) return value;
        if (value instanceof String) { budget.add((long)((String)value).length() * 2); return value; }
        if (value instanceof CharSequence) { String text = value.toString(); budget.add((long)text.length() * 2); return text; }
        if (value instanceof android.net.Uri || value instanceof android.content.ComponentName) { budget.add((long)value.toString().length() * 2); return value; }
        if (active.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("Cyclic broadcast extras");
        try {
            if (value instanceof Bundle) {
                Bundle result = new Bundle(); result.loader = ((Bundle)value).loader;
                for (java.util.Map.Entry<String,Object> entry : ((Bundle)value).values.entrySet()) {
                    if (entry.getKey() != null) budget.add((long)entry.getKey().length() * 2);
                    result.values.put(entry.getKey(), broadcastValue(entry.getValue(), active, budget, depth + 1));
                } return result;
            }
            if (value instanceof android.location.Location) {
                android.location.Location result = new android.location.Location((android.location.Location)value);
                if (result.getExtras() != null) result.setExtras((Bundle)broadcastValue(result.getExtras(), active, budget, depth + 1));
                return result;
            }
            if (value.getClass().isArray()) {
                Class<?> component = value.getClass().getComponentType(); int length = java.lang.reflect.Array.getLength(value);
                int width = component == byte.class || component == boolean.class ? 1 : component == char.class || component == short.class ? 2 : component == int.class || component == float.class ? 4 : 8;
                budget.add((long)length * width);
                Class<?> outputComponent = component;
                if (component != String.class && CharSequence.class.isAssignableFrom(component)) outputComponent = CharSequence.class;
                else if (Parcelable.class.isAssignableFrom(component)) outputComponent = Parcelable.class;
                Object result = java.lang.reflect.Array.newInstance(outputComponent, length);
                if (component.isPrimitive()) System.arraycopy(value, 0, result, 0, length);
                else for (int i = 0; i < length; ++i) java.lang.reflect.Array.set(result, i, broadcastValue(java.lang.reflect.Array.get(value, i), active, budget, depth + 1));
                return result;
            }
            if (value instanceof ArrayList) {
                budget.add((long)((ArrayList<?>)value).size() * 8); ArrayList<Object> result = new ArrayList<Object>();
                for (Object item : (ArrayList<?>)value) result.add(broadcastValue(item, active, budget, depth + 1)); return result;
            }
            if (value instanceof IBinder || value instanceof Parcelable || value instanceof Serializable) {
                budget.add(64);
                return value;
            }
            throw new UnsupportedOperationException("Broadcast extras value transport is not implemented: " + value.getClass().getName());
        } finally { active.remove(value); }
    }
    @Override public int describeContents() { return contents(this, new IdentityHashMap<Object, Boolean>(), 0); }
    private static int contents(Object item, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (item == null || seen.containsKey(item)) return 0;
        if (depth > 128 || seen.size() >= 65536) throw new IllegalStateException("Bundle content graph exceeds traversal limits");
        seen.put(item, true); int flags = 0;
        if (item instanceof Bundle) for (Object value : ((Bundle)item).values.values()) flags |= contents(value, seen, depth + 1);
        else if (item instanceof Parcelable) flags = ((Parcelable)item).describeContents();
        else if (item instanceof Object[]) for (Object value : (Object[])item) flags |= contents(value, seen, depth + 1);
        else if (item instanceof ArrayList) for (Object value : (ArrayList<?>)item) flags |= contents(value, seen, depth + 1);
        return flags;
    }
    public boolean hasFileDescriptors() { return (describeContents() & Parcelable.CONTENTS_FILE_DESCRIPTOR) != 0; }
    @Override public void writeToParcel(Parcel out, int flags) { BundleParcel.write(out,this,flags); }
    public static final Parcelable.Creator<Bundle> CREATOR = new Parcelable.Creator<Bundle>() {
        public Bundle createFromParcel(Parcel source) { return BundleParcel.read(source,Bundle.class.getClassLoader()); }
        public Bundle[] newArray(int size) { return new Bundle[size]; }
    };
}
