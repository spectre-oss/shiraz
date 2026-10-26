// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import java.util.*;
public final class ContentValues implements android.os.Parcelable {
    private final LinkedHashMap<String,Object> values;
    public ContentValues() { values = new LinkedHashMap<String,Object>(); }
    public ContentValues(int size) { values = new LinkedHashMap<String,Object>(size); }
    public ContentValues(ContentValues source) { values = new LinkedHashMap<String,Object>(source.values); }
    public void put(String key, String value) { values.put(key, value); }
    public void put(String key, Byte value) { values.put(key, value); }
    public void put(String key, Short value) { values.put(key, value); }
    public void put(String key, Integer value) { values.put(key, value); }
    public void put(String key, Long value) { values.put(key, value); }
    public void put(String key, Float value) { values.put(key, value); }
    public void put(String key, Double value) { values.put(key, value); }
    public void put(String key, Boolean value) { values.put(key, value); }
    public void put(String key, byte[] value) { values.put(key, value); }
    public void putNull(String key) { values.put(key, null); }
    public void putAll(ContentValues other) { values.putAll(other.values); }
    public Object get(String key) { return values.get(key); }
    public String getAsString(String key) { Object v = get(key); return v == null ? null : v.toString(); }
    public Long getAsLong(String key) { Object v = get(key); if (v == null) return null; try { return v instanceof Number ? ((Number)v).longValue() : Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; } }
    public Integer getAsInteger(String key) { Object v = get(key); if (v == null) return null; try { return v instanceof Number ? ((Number)v).intValue() : Integer.valueOf(v.toString()); } catch (NumberFormatException e) { return null; } }
    public Short getAsShort(String key) { Object v = get(key); if (v == null) return null; try { return v instanceof Number ? ((Number)v).shortValue() : Short.valueOf(v.toString()); } catch (NumberFormatException e) { return null; } }
    public Byte getAsByte(String key) { Object v = get(key); if (v == null) return null; try { return v instanceof Number ? ((Number)v).byteValue() : Byte.valueOf(v.toString()); } catch (NumberFormatException e) { return null; } }
    public Double getAsDouble(String key) { Object v = get(key); if (v == null) return null; try { return v instanceof Number ? ((Number)v).doubleValue() : Double.valueOf(v.toString()); } catch (NumberFormatException e) { return null; } }
    public Float getAsFloat(String key) { Object v = get(key); if (v == null) return null; try { return v instanceof Number ? ((Number)v).floatValue() : Float.valueOf(v.toString()); } catch (NumberFormatException e) { return null; } }
    public Boolean getAsBoolean(String key) {
        Object value = get(key);
        if (value instanceof Boolean) return (Boolean)value;
        if (value instanceof Number) return ((Number)value).intValue() != 0;
        if (value instanceof CharSequence) return Boolean.valueOf(value.toString());
        return null;
    }
    public byte[] getAsByteArray(String key) { Object value = get(key); return value instanceof byte[] ? (byte[])value : null; }
    public int size() { return values.size(); }
    public boolean containsKey(String key) { return values.containsKey(key); }
    public void remove(String key) { values.remove(key); }
    public void clear() { values.clear(); }
    public Set<String> keySet() { return values.keySet(); }
    public Set<Map.Entry<String,Object>> valueSet() { return values.entrySet(); }
    public int describeContents() { return 0; }
    public void writeToParcel(android.os.Parcel destination, int flags) { throw new UnsupportedOperationException("ContentValues parcel transport is not implemented"); }
    public static final android.os.Parcelable.Creator<ContentValues> CREATOR = new android.os.Parcelable.Creator<ContentValues>() {
        public ContentValues createFromParcel(android.os.Parcel source) { throw new UnsupportedOperationException("ContentValues parcel decoding is not implemented"); }
        public ContentValues[] newArray(int size) { return new ContentValues[size]; }
    };
    @Override public boolean equals(Object other) { return other instanceof ContentValues && values.equals(((ContentValues)other).values); }
    @Override public int hashCode() { return values.hashCode(); }
}
