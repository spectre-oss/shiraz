// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GenericDocument {
    private static final int MAX_INDEXED_PROPERTIES = 64;

    private final String mNamespace;
    private final String mId;
    private final String mSchemaType;
    private final long mCreationTimestampMillis;
    private final long mTtlMillis;
    private final int mScore;
    private final Map<String, Object> mProperties;

    protected GenericDocument(GenericDocument document) {
        Objects.requireNonNull(document);
        mNamespace = document.mNamespace;
        mId = document.mId;
        mSchemaType = document.mSchemaType;
        mCreationTimestampMillis = document.mCreationTimestampMillis;
        mTtlMillis = document.mTtlMillis;
        mScore = document.mScore;
        mProperties = copyProperties(document.mProperties);
    }

    GenericDocument(
            String namespace,
            String id,
            String schemaType,
            long creationTimestampMillis,
            long ttlMillis,
            int score,
            Map<String, Object> properties) {
        mNamespace = Objects.requireNonNull(namespace);
        mId = Objects.requireNonNull(id);
        mSchemaType = Objects.requireNonNull(schemaType);
        mCreationTimestampMillis = creationTimestampMillis;
        mTtlMillis = ttlMillis;
        mScore = score;
        mProperties = copyProperties(properties);
    }

    private static Map<String, Object> copyProperties(Map<String, Object> src) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            copy.put(e.getKey(), cloneValue(e.getValue()));
        }
        return copy;
    }

    private static Object cloneValue(Object val) {
        if (val instanceof String[]) return ((String[]) val).clone();
        if (val instanceof long[]) return ((long[]) val).clone();
        if (val instanceof double[]) return ((double[]) val).clone();
        if (val instanceof boolean[]) return ((boolean[]) val).clone();
        if (val instanceof byte[][]) {
            byte[][] src = (byte[][]) val;
            byte[][] dst = new byte[src.length][];
            for (int i = 0; i < src.length; i++) {
                dst[i] = src[i] != null ? src[i].clone() : null;
            }
            return dst;
        }
        if (val instanceof GenericDocument[]) return ((GenericDocument[]) val).clone();
        return val;
    }

    public static int getMaxIndexedProperties() {
        return MAX_INDEXED_PROPERTIES;
    }

    public String getId() {
        return mId;
    }

    public String getNamespace() {
        return mNamespace;
    }

    public String getSchemaType() {
        return mSchemaType;
    }

    public long getCreationTimestampMillis() {
        return mCreationTimestampMillis;
    }

    public long getTtlMillis() {
        return mTtlMillis;
    }

    public int getScore() {
        return mScore;
    }

    public Set<String> getPropertyNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(mProperties.keySet()));
    }

    public Object getProperty(String path) {
        Objects.requireNonNull(path);
        PropertyPath pp = new PropertyPath(path);
        Object current = this;
        for (int i = 0; i < pp.size(); i++) {
            if (!(current instanceof GenericDocument)) {
                return null;
            }
            GenericDocument doc = (GenericDocument) current;
            PropertyPath.PathSegment seg = pp.get(i);
            Object val = doc.mProperties.get(seg.getPropertyName());
            if (val == null) return null;
            int idx = seg.getPropertyIndex();
            if (idx != PropertyPath.PathSegment.NON_REPEATED_CARDINALITY) {
                int len = Array.getLength(val);
                if (idx < 0 || idx >= len) return null;
                if (val instanceof String[]) current = new String[] { ((String[]) val)[idx] };
                else if (val instanceof long[]) current = new long[] { ((long[]) val)[idx] };
                else if (val instanceof double[]) current = new double[] { ((double[]) val)[idx] };
                else if (val instanceof boolean[]) current = new boolean[] { ((boolean[]) val)[idx] };
                else if (val instanceof byte[][]) current = new byte[][] { ((byte[][]) val)[idx].clone() };
                else if (val instanceof GenericDocument[]) {
                    GenericDocument nested = ((GenericDocument[]) val)[idx];
                    current = (i == pp.size() - 1) ? new GenericDocument[] { nested } : nested;
                } else {
                    return null;
                }
            } else if (i < pp.size() - 1) {
                if (val instanceof GenericDocument[] && ((GenericDocument[]) val).length > 0) {
                    current = ((GenericDocument[]) val)[0];
                } else {
                    return null;
                }
            } else {
                current = cloneValue(val);
            }
        }
        return current;
    }

    public String getPropertyString(String path) {
        String[] arr = getPropertyStringArray(path);
        return (arr != null && arr.length > 0) ? arr[0] : null;
    }

    public long getPropertyLong(String path) {
        long[] arr = getPropertyLongArray(path);
        return (arr != null && arr.length > 0) ? arr[0] : 0L;
    }

    public double getPropertyDouble(String path) {
        double[] arr = getPropertyDoubleArray(path);
        return (arr != null && arr.length > 0) ? arr[0] : 0.0;
    }

    public boolean getPropertyBoolean(String path) {
        boolean[] arr = getPropertyBooleanArray(path);
        return arr != null && arr.length > 0 && arr[0];
    }

    public byte[] getPropertyBytes(String path) {
        byte[][] arr = getPropertyBytesArray(path);
        return (arr != null && arr.length > 0 && arr[0] != null) ? arr[0].clone() : null;
    }

    public GenericDocument getPropertyDocument(String path) {
        GenericDocument[] arr = getPropertyDocumentArray(path);
        return (arr != null && arr.length > 0) ? arr[0] : null;
    }

    public String[] getPropertyStringArray(String path) {
        Object val = getProperty(path);
        return val instanceof String[] ? (String[]) val : null;
    }

    public long[] getPropertyLongArray(String path) {
        Object val = getProperty(path);
        return val instanceof long[] ? (long[]) val : null;
    }

    public double[] getPropertyDoubleArray(String path) {
        Object val = getProperty(path);
        return val instanceof double[] ? (double[]) val : null;
    }

    public boolean[] getPropertyBooleanArray(String path) {
        Object val = getProperty(path);
        return val instanceof boolean[] ? (boolean[]) val : null;
    }

    public byte[][] getPropertyBytesArray(String path) {
        Object val = getProperty(path);
        return val instanceof byte[][] ? (byte[][]) val : null;
    }

    public GenericDocument[] getPropertyDocumentArray(String path) {
        Object val = getProperty(path);
        return val instanceof GenericDocument[] ? (GenericDocument[]) val : null;
    }

    void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeString(mNamespace);
        dest.writeString(mId);
        dest.writeString(mSchemaType);
        dest.writeLong(mCreationTimestampMillis);
        dest.writeLong(mTtlMillis);
        dest.writeInt(mScore);
        dest.writeInt(mProperties.size());
        for (Map.Entry<String, Object> e : mProperties.entrySet()) {
            dest.writeString(e.getKey());
            Object v = e.getValue();
            if (v instanceof String[]) {
                dest.writeInt(1);
                String[] arr = (String[]) v;
                dest.writeInt(arr.length);
                for (String s : arr) dest.writeString(s);
            } else if (v instanceof long[]) {
                dest.writeInt(2);
                long[] arr = (long[]) v;
                dest.writeInt(arr.length);
                for (long l : arr) dest.writeLong(l);
            } else if (v instanceof double[]) {
                dest.writeInt(3);
                double[] arr = (double[]) v;
                dest.writeInt(arr.length);
                for (double d : arr) dest.writeDouble(d);
            } else if (v instanceof boolean[]) {
                dest.writeInt(4);
                boolean[] arr = (boolean[]) v;
                dest.writeInt(arr.length);
                for (boolean b : arr) dest.writeInt(b ? 1 : 0);
            } else if (v instanceof byte[][]) {
                dest.writeInt(5);
                byte[][] arr = (byte[][]) v;
                dest.writeInt(arr.length);
                for (byte[] b : arr) dest.writeByteArray(b);
            } else if (v instanceof GenericDocument[]) {
                dest.writeInt(6);
                GenericDocument[] arr = (GenericDocument[]) v;
                dest.writeInt(arr.length);
                for (GenericDocument gd : arr) gd.writeToParcelInternal(dest, flags);
            } else {
                dest.writeInt(0);
            }
        }
    }

    static GenericDocument createFromParcelInternal(Parcel in) {
        String ns = in.readString();
        String id = in.readString();
        String schema = in.readString();
        long created = in.readLong();
        long ttl = in.readLong();
        int score = in.readInt();
        int count = in.readInt();
        Builder<?> b = new Builder<Builder<?>>(ns, id, schema)
                .setCreationTimestampMillis(created)
                .setTtlMillis(ttl)
                .setScore(score);
        for (int i = 0; i < count; i++) {
            String key = in.readString();
            int kind = in.readInt();
            if (kind == 1) {
                int len = in.readInt();
                String[] arr = new String[len];
                for (int j = 0; j < len; j++) arr[j] = in.readString();
                b.setPropertyString(key, arr);
            } else if (kind == 2) {
                int len = in.readInt();
                long[] arr = new long[len];
                for (int j = 0; j < len; j++) arr[j] = in.readLong();
                b.setPropertyLong(key, arr);
            } else if (kind == 3) {
                int len = in.readInt();
                double[] arr = new double[len];
                for (int j = 0; j < len; j++) arr[j] = in.readDouble();
                b.setPropertyDouble(key, arr);
            } else if (kind == 4) {
                int len = in.readInt();
                boolean[] arr = new boolean[len];
                for (int j = 0; j < len; j++) arr[j] = in.readInt() != 0;
                b.setPropertyBoolean(key, arr);
            } else if (kind == 5) {
                int len = in.readInt();
                byte[][] arr = new byte[len][];
                for (int j = 0; j < len; j++) arr[j] = in.createByteArray();
                b.setPropertyBytes(key, arr);
            } else if (kind == 6) {
                int len = in.readInt();
                GenericDocument[] arr = new GenericDocument[len];
                for (int j = 0; j < len; j++) arr[j] = createFromParcelInternal(in);
                b.setPropertyDocument(key, arr);
            }
        }
        return b.build();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GenericDocument)) return false;
        GenericDocument that = (GenericDocument) other;
        if (mCreationTimestampMillis != that.mCreationTimestampMillis
                || mTtlMillis != that.mTtlMillis
                || mScore != that.mScore
                || !Objects.equals(mNamespace, that.mNamespace)
                || !Objects.equals(mId, that.mId)
                || !Objects.equals(mSchemaType, that.mSchemaType)
                || mProperties.size() != that.mProperties.size()) {
            return false;
        }
        for (Map.Entry<String, Object> e : mProperties.entrySet()) {
            Object v1 = e.getValue();
            Object v2 = that.mProperties.get(e.getKey());
            if (!propertyEquals(v1, v2)) return false;
        }
        return true;
    }

    private static boolean propertyEquals(Object v1, Object v2) {
        if (v1 == v2) return true;
        if (v1 == null || v2 == null || v1.getClass() != v2.getClass()) return false;
        if (v1 instanceof String[]) return Arrays.equals((String[]) v1, (String[]) v2);
        if (v1 instanceof long[]) return Arrays.equals((long[]) v1, (long[]) v2);
        if (v1 instanceof double[]) return Arrays.equals((double[]) v1, (double[]) v2);
        if (v1 instanceof boolean[]) return Arrays.equals((boolean[]) v1, (boolean[]) v2);
        if (v1 instanceof byte[][]) return Arrays.deepEquals((byte[][]) v1, (byte[][]) v2);
        if (v1 instanceof GenericDocument[]) {
            return Arrays.equals((GenericDocument[]) v1, (GenericDocument[]) v2);
        }
        return Objects.equals(v1, v2);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(
                mNamespace,
                mId,
                mSchemaType,
                Long.valueOf(mCreationTimestampMillis),
                Long.valueOf(mTtlMillis),
                Integer.valueOf(mScore));
        for (Map.Entry<String, Object> e : mProperties.entrySet()) {
            h = 31 * h + e.getKey().hashCode();
            Object v = e.getValue();
            if (v instanceof String[]) h = 31 * h + Arrays.hashCode((String[]) v);
            else if (v instanceof long[]) h = 31 * h + Arrays.hashCode((long[]) v);
            else if (v instanceof double[]) h = 31 * h + Arrays.hashCode((double[]) v);
            else if (v instanceof boolean[]) h = 31 * h + Arrays.hashCode((boolean[]) v);
            else if (v instanceof byte[][]) h = 31 * h + Arrays.deepHashCode((byte[][]) v);
            else if (v instanceof GenericDocument[]) h = 31 * h + Arrays.hashCode((GenericDocument[]) v);
        }
        return h;
    }

    @Override
    public String toString() {
        return "GenericDocument{namespace='" + mNamespace + "', id='" + mId
                + "', schemaType='" + mSchemaType + "', score=" + mScore + "}";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static class Builder<BuilderType extends Builder> {
        private String mNamespace;
        private String mId;
        private String mSchemaType;
        private long mCreationTimestampMillis = -1L;
        private long mTtlMillis = 0L;
        private int mScore = 0;
        private final Map<String, Object> mProperties = new LinkedHashMap<String, Object>();

        public Builder(String namespace, String id, String schemaType) {
            mNamespace = Objects.requireNonNull(namespace);
            mId = Objects.requireNonNull(id);
            mSchemaType = Objects.requireNonNull(schemaType);
        }

        public Builder(GenericDocument document) {
            Objects.requireNonNull(document);
            mNamespace = document.mNamespace;
            mId = document.mId;
            mSchemaType = document.mSchemaType;
            mCreationTimestampMillis = document.mCreationTimestampMillis;
            mTtlMillis = document.mTtlMillis;
            mScore = document.mScore;
            mProperties.putAll(copyProperties(document.mProperties));
        }

        private static void validatePropertyName(String name) {
            Objects.requireNonNull(name);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Property name cannot be blank");
            }
        }

        public BuilderType setNamespace(String namespace) {
            mNamespace = Objects.requireNonNull(namespace);
            return (BuilderType) this;
        }

        public BuilderType setId(String id) {
            mId = Objects.requireNonNull(id);
            return (BuilderType) this;
        }

        public BuilderType setSchemaType(String schemaType) {
            mSchemaType = Objects.requireNonNull(schemaType);
            return (BuilderType) this;
        }

        public BuilderType setScore(int score) {
            if (score < 0) {
                throw new IllegalArgumentException("Document score cannot be negative");
            }
            mScore = score;
            return (BuilderType) this;
        }

        public BuilderType setCreationTimestampMillis(long creationTimestampMillis) {
            mCreationTimestampMillis = creationTimestampMillis;
            return (BuilderType) this;
        }

        public BuilderType setTtlMillis(long ttlMillis) {
            if (ttlMillis < 0) {
                throw new IllegalArgumentException("Document ttlMillis cannot be negative");
            }
            mTtlMillis = ttlMillis;
            return (BuilderType) this;
        }

        public BuilderType setPropertyString(String name, String... values) {
            validatePropertyName(name);
            Objects.requireNonNull(values);
            for (String s : values) Objects.requireNonNull(s);
            mProperties.put(name, values.clone());
            return (BuilderType) this;
        }

        public BuilderType setPropertyBoolean(String name, boolean... values) {
            validatePropertyName(name);
            Objects.requireNonNull(values);
            mProperties.put(name, values.clone());
            return (BuilderType) this;
        }

        public BuilderType setPropertyLong(String name, long... values) {
            validatePropertyName(name);
            Objects.requireNonNull(values);
            mProperties.put(name, values.clone());
            return (BuilderType) this;
        }

        public BuilderType setPropertyDouble(String name, double... values) {
            validatePropertyName(name);
            Objects.requireNonNull(values);
            mProperties.put(name, values.clone());
            return (BuilderType) this;
        }

        public BuilderType setPropertyBytes(String name, byte[]... values) {
            validatePropertyName(name);
            Objects.requireNonNull(values);
            byte[][] copy = new byte[values.length][];
            for (int i = 0; i < values.length; i++) {
                Objects.requireNonNull(values[i]);
                copy[i] = values[i].clone();
            }
            mProperties.put(name, copy);
            return (BuilderType) this;
        }

        public BuilderType setPropertyDocument(String name, GenericDocument... values) {
            validatePropertyName(name);
            Objects.requireNonNull(values);
            for (GenericDocument d : values) Objects.requireNonNull(d);
            mProperties.put(name, values.clone());
            return (BuilderType) this;
        }

        public BuilderType clearProperty(String name) {
            Objects.requireNonNull(name);
            mProperties.remove(name);
            return (BuilderType) this;
        }

        public GenericDocument build() {
            long created = mCreationTimestampMillis < 0
                    ? System.currentTimeMillis()
                    : mCreationTimestampMillis;
            return new GenericDocument(
                    mNamespace, mId, mSchemaType, created, mTtlMillis, mScore, mProperties);
        }
    }
}
