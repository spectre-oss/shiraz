// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.util.*;

/** Eager, in-process typed values. No lazy parcel decoding or cross-process transport. */
public class BaseBundle {
    protected final Map<String, Object> values;
    protected ClassLoader loader;
    protected BaseBundle() { this(0); }
    protected BaseBundle(int capacity) {
        if (capacity < 0 || capacity > 65536) throw new IllegalArgumentException("Bundle initial capacity out of range");
        values = new HashMap<String, Object>(capacity); loader = getClass().getClassLoader();
    }
    protected BaseBundle(boolean immutable) { values = immutable ? Collections.<String, Object>emptyMap() : new HashMap<String, Object>(); loader = getClass().getClassLoader(); }
    protected BaseBundle(BaseBundle other) { values = new HashMap<String, Object>(other.values); loader = other.loader; }
    protected <T> T value(String key, Class<T> type, T fallback) {
        Object item = values.get(key);
        if (item == null) return fallback;
        if (type.isInstance(item)) return type.cast(item);
        android.util.Log.w("Bundle", "Value has unexpected type; returning the requested default");
        return fallback;
    }
    public Object get(String key) { return values.get(key); }
    public boolean containsKey(String key) { return values.containsKey(key); }
    public boolean isEmpty() { return values.isEmpty(); }
    public int size() { return values.size(); }
    public void clear() { values.clear(); }
    public void remove(String key) { values.remove(key); }
    public Set<String> keySet() { return values.keySet(); }
    public ClassLoader getClassLoader() { return loader; }
    public void setClassLoader(ClassLoader value) { loader = value; }
    public void putString(String key, String item) { values.put(key, item); }
    public String getString(String key) { return getString(key, null); }
    public String getString(String key, String fallback) { return value(key, String.class, fallback); }
    public void putBoolean(String key, boolean item) { values.put(key, item); }
    public boolean getBoolean(String key) { return getBoolean(key, false); }
    public boolean getBoolean(String key, boolean fallback) { return value(key, Boolean.class, fallback); }
    public void putByte(String key, byte item) { values.put(key, item); }
    public byte getByte(String key) { return getByte(key, (byte)0); }
    public byte getByte(String key, byte fallback) { return value(key, Byte.class, fallback); }
    public void putShort(String key, short item) { values.put(key, item); }
    public short getShort(String key) { return getShort(key, (short)0); }
    public short getShort(String key, short fallback) { return value(key, Short.class, fallback); }
    public void putChar(String key, char item) { values.put(key, item); }
    public char getChar(String key) { return getChar(key, (char)0); }
    public char getChar(String key, char fallback) { return value(key, Character.class, fallback); }
    public void putInt(String key, int item) { values.put(key, item); }
    public int getInt(String key) { return getInt(key, 0); }
    public int getInt(String key, int fallback) { return value(key, Integer.class, fallback); }
    public void putLong(String key, long item) { values.put(key, item); }
    public long getLong(String key) { return getLong(key, 0L); }
    public long getLong(String key, long fallback) { return value(key, Long.class, fallback); }
    public void putFloat(String key, float item) { values.put(key, item); }
    public float getFloat(String key) { return getFloat(key, 0f); }
    public float getFloat(String key, float fallback) { return value(key, Float.class, fallback); }
    public void putDouble(String key, double item) { values.put(key, item); }
    public double getDouble(String key) { return getDouble(key, 0d); }
    public double getDouble(String key, double fallback) { return value(key, Double.class, fallback); }
    public void putBooleanArray(String key, boolean[] item) { values.put(key, item); }
    public boolean[] getBooleanArray(String key) { return value(key, boolean[].class, null); }
    public void putByteArray(String key, byte[] item) { values.put(key, item); }
    public byte[] getByteArray(String key) { return value(key, byte[].class, null); }
    public void putShortArray(String key, short[] item) { values.put(key, item); }
    public short[] getShortArray(String key) { return value(key, short[].class, null); }
    public void putCharArray(String key, char[] item) { values.put(key, item); }
    public char[] getCharArray(String key) { return value(key, char[].class, null); }
    public void putIntArray(String key, int[] item) { values.put(key, item); }
    public int[] getIntArray(String key) { return value(key, int[].class, null); }
    public void putLongArray(String key, long[] item) { values.put(key, item); }
    public long[] getLongArray(String key) { return value(key, long[].class, null); }
    public void putFloatArray(String key, float[] item) { values.put(key, item); }
    public float[] getFloatArray(String key) { return value(key, float[].class, null); }
    public void putDoubleArray(String key, double[] item) { values.put(key, item); }
    public double[] getDoubleArray(String key) { return value(key, double[].class, null); }
    public void putStringArray(String key, String[] item) { values.put(key, item); }
    public String[] getStringArray(String key) { return value(key, String[].class, null); }
}
