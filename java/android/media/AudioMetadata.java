// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AudioMetadata {
    private AudioMetadata() {}

    public interface Key<T> {
        String getName();

        Class<T> getValueClass();
    }

    public static class Format {
        public static final Key<Integer> KEY_BIT_RATE = createKey("bitrate", Integer.class);
        public static final Key<Integer> KEY_CHANNEL_MASK = createKey("channel-mask", Integer.class);
        public static final Key<String> KEY_MIME = createKey("mime", String.class);
        public static final Key<Integer> KEY_SAMPLE_RATE = createKey("sample-rate", Integer.class);
        public static final Key<Integer> KEY_BIT_WIDTH = createKey("bit-width", Integer.class);
        public static final Key<Boolean> KEY_ATMOS_PRESENT = createKey("atmos-present", Boolean.class);
        public static final Key<Integer> KEY_AUDIO_ENCODING = createKey("audio-encoding", Integer.class);
        public static final Key<Integer> KEY_PRESENTATION_ID = createKey("presentation-id", Integer.class);
        public static final Key<Integer> KEY_PROGRAM_ID = createKey("program-id", Integer.class);
        public static final Key<Integer> KEY_PRESENTATION_CONTENT_CLASSIFIER =
                createKey("presentation-content-classifier", Integer.class);
        public static final Key<String> KEY_PRESENTATION_LANGUAGE =
                createKey("presentation-language", String.class);

        private Format() {}
    }

    public static AudioMetadataMap createMap() {
        return new BaseMap();
    }

    static <T> Key<T> createKey(String name, Class<T> type) {
        return new KeyImpl<T>(name, type);
    }

    private static final class KeyImpl<T> implements Key<T> {
        private final String mName;
        private final Class<T> mType;

        KeyImpl(String name, Class<T> type) {
            mName = Objects.requireNonNull(name, "name");
            mType = Objects.requireNonNull(type, "type");
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public Class<T> getValueClass() {
            return mType;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Key<?>)) return false;
            Key<?> other = (Key<?>) obj;
            return mName.equals(other.getName()) && mType.equals(other.getValueClass());
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName, mType);
        }

        @Override
        public String toString() {
            return "Key(" + mName + ", " + mType.getName() + ")";
        }
    }

    private static final class BaseMap implements AudioMetadataMap {
        private final Map<Key<?>, Object> mMap;

        BaseMap() {
            mMap = new LinkedHashMap<Key<?>, Object>();
        }

        BaseMap(Map<Key<?>, Object> source) {
            mMap = new LinkedHashMap<Key<?>, Object>(source);
        }

        @Override
        public <T> boolean containsKey(Key<T> key) {
            Objects.requireNonNull(key, "key");
            return mMap.containsKey(normalizeKey(key));
        }

        @Override
        public AudioMetadataMap dup() {
            return new BaseMap(mMap);
        }

        @Override
        public <T> T get(Key<T> key) {
            Objects.requireNonNull(key, "key");
            Object val = mMap.get(normalizeKey(key));
            return val == null ? null : key.getValueClass().cast(val);
        }

        @Override
        public <T> T remove(Key<T> key) {
            Objects.requireNonNull(key, "key");
            Object val = mMap.remove(normalizeKey(key));
            return val == null ? null : key.getValueClass().cast(val);
        }

        @Override
        public <T> T set(Key<T> key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            key.getValueClass().cast(value);
            Object prev = mMap.put(normalizeKey(key), value);
            return prev == null ? null : key.getValueClass().cast(prev);
        }

        @Override
        public int size() {
            return mMap.size();
        }

        private static <T> Key<T> normalizeKey(Key<T> key) {
            if (key instanceof KeyImpl<?>) {
                return key;
            }
            return new KeyImpl<T>(key.getName(), key.getValueClass());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BaseMap)) return false;
            return mMap.equals(((BaseMap) obj).mMap);
        }

        @Override
        public int hashCode() {
            return mMap.hashCode();
        }
    }
}
