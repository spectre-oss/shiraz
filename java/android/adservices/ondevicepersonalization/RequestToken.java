// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.content.ContentValues;
import android.net.NetworkCapabilities;
import android.os.Parcel;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Opaque token identifying a single request handled by an {@link IsolatedService}. */
public class RequestToken {
    private final MutableKeyValueStore mLocalData = new InMemoryKeyValueStore();
    private final KeyValueStore mRemoteData = new InMemoryKeyValueStore();
    private final LogReader mLogReader = new LogReader();
    private final EventUrlProvider mEventUrlProvider = new EventUrlProvider();
    private final FederatedComputeScheduler mScheduler = new FederatedComputeScheduler();
    private final ModelManager mModelManager = new ModelManager();
    private final UserData mUserData = UserData.createDefault();

    RequestToken() {
    }

    KeyValueStore remoteData() {
        return mRemoteData;
    }

    MutableKeyValueStore localData() {
        return mLocalData;
    }

    LogReader logReader() {
        return mLogReader;
    }

    EventUrlProvider eventUrlProvider() {
        return mEventUrlProvider;
    }

    FederatedComputeScheduler scheduler() {
        return mScheduler;
    }

    ModelManager modelManager() {
        return mModelManager;
    }

    UserData userData() {
        return mUserData;
    }

    static final class InMemoryKeyValueStore implements MutableKeyValueStore {
        private final Map<String, byte[]> mMap = new ConcurrentHashMap<String, byte[]>();

        @Override
        public byte[] get(String key) {
            Objects.requireNonNull(key, "key");
            byte[] val = mMap.get(key);
            return val == null ? null : val.clone();
        }

        @Override
        public Set<String> keySet() {
            return Collections.unmodifiableSet(new LinkedHashSet<String>(mMap.keySet()));
        }

        @Override
        public byte[] put(String key, byte[] value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            byte[] prev = mMap.put(key, value.clone());
            return prev == null ? null : prev.clone();
        }

        @Override
        public byte[] remove(String key) {
            Objects.requireNonNull(key, "key");
            byte[] prev = mMap.remove(key);
            return prev == null ? null : prev.clone();
        }
    }

    static void writeInstant(Parcel out, Instant time) {
        if (time == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeLong(time.getEpochSecond());
            out.writeInt(time.getNano());
        }
    }

    static Instant readInstant(Parcel in) {
        if (!in.readBoolean()) {
            return null;
        }
        long sec = in.readLong();
        int nano = in.readInt();
        return Instant.ofEpochSecond(sec, nano);
    }

    static void writeContentValues(Parcel out, ContentValues values) {
        if (values == null) {
            out.writeInt(-1);
            return;
        }
        Set<Map.Entry<String, Object>> entries = values.valueSet();
        out.writeInt(entries.size());
        for (Map.Entry<String, Object> e : entries) {
            out.writeString(e.getKey());
            Object v = e.getValue();
            if (v == null) {
                out.writeInt(0);
            } else if (v instanceof String) {
                out.writeInt(1);
                out.writeString((String) v);
            } else if (v instanceof Integer) {
                out.writeInt(2);
                out.writeInt(((Integer) v).intValue());
            } else if (v instanceof Long) {
                out.writeInt(3);
                out.writeLong(((Long) v).longValue());
            } else if (v instanceof Double) {
                out.writeInt(4);
                out.writeDouble(((Double) v).doubleValue());
            } else if (v instanceof Float) {
                out.writeInt(5);
                out.writeFloat(((Float) v).floatValue());
            } else if (v instanceof Boolean) {
                out.writeInt(6);
                out.writeBoolean(((Boolean) v).booleanValue());
            } else if (v instanceof byte[]) {
                out.writeInt(7);
                out.writeByteArray((byte[]) v);
            } else if (v instanceof Short) {
                out.writeInt(8);
                out.writeInt(((Short) v).intValue());
            } else if (v instanceof Byte) {
                out.writeInt(9);
                out.writeByte(((Byte) v).byteValue());
            } else {
                out.writeInt(1);
                out.writeString(v.toString());
            }
        }
    }

    static ContentValues readContentValues(Parcel in) {
        int size = in.readInt();
        if (size < 0) {
            return null;
        }
        ContentValues cv = new ContentValues(size);
        for (int i = 0; i < size; i++) {
            String k = in.readString();
            int tag = in.readInt();
            switch (tag) {
                case 0:
                    cv.putNull(k);
                    break;
                case 1:
                    cv.put(k, in.readString());
                    break;
                case 2:
                    cv.put(k, Integer.valueOf(in.readInt()));
                    break;
                case 3:
                    cv.put(k, Long.valueOf(in.readLong()));
                    break;
                case 4:
                    cv.put(k, Double.valueOf(in.readDouble()));
                    break;
                case 5:
                    cv.put(k, Float.valueOf(in.readFloat()));
                    break;
                case 6:
                    cv.put(k, Boolean.valueOf(in.readBoolean()));
                    break;
                case 7:
                    cv.put(k, in.createByteArray());
                    break;
                case 8:
                    cv.put(k, Short.valueOf((short) in.readInt()));
                    break;
                case 9:
                    cv.put(k, Byte.valueOf(in.readByte()));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown ContentValues tag: " + tag);
            }
        }
        return cv;
    }

    static void writeNetworkCapabilities(Parcel out, NetworkCapabilities nc) {
        if (nc == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        long caps = 0L;
        for (int i = 0; i < 64; i++) {
            if (nc.hasCapability(i)) {
                caps |= (1L << i);
            }
        }
        int transports = 0;
        for (int i = 0; i < 16; i++) {
            if (nc.hasTransport(i)) {
                transports |= (1 << i);
            }
        }
        out.writeLong(caps);
        out.writeInt(transports);
    }

    static NetworkCapabilities readNetworkCapabilities(Parcel in) {
        if (!in.readBoolean()) {
            return null;
        }
        long caps = in.readLong();
        int transports = in.readInt();
        NetworkCapabilities nc = new NetworkCapabilities();
        for (int i = 0; i < 64; i++) {
            if ((caps & (1L << i)) != 0L) {
                nc.addCapability(i);
            }
        }
        for (int i = 0; i < 16; i++) {
            if ((transports & (1 << i)) != 0) {
                nc.addTransportType(i);
            }
        }
        return nc;
    }

    static boolean networkCapabilitiesEqual(NetworkCapabilities a, NetworkCapabilities b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < 64; i++) {
            if (a.hasCapability(i) != b.hasCapability(i)) {
                return false;
            }
        }
        for (int i = 0; i < 16; i++) {
            if (a.hasTransport(i) != b.hasTransport(i)) {
                return false;
            }
        }
        return true;
    }

    static boolean contentValuesEqual(ContentValues a, ContentValues b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, Object> e : a.valueSet()) {
            String k = e.getKey();
            if (!b.containsKey(k)) {
                return false;
            }
            Object va = e.getValue();
            Object vb = b.get(k);
            if (va instanceof byte[] && vb instanceof byte[]) {
                if (!Arrays.equals((byte[]) va, (byte[]) vb)) {
                    return false;
                }
            } else if (!Objects.equals(va, vb)) {
                return false;
            }
        }
        return true;
    }

    static boolean contentValuesListEqual(List<ContentValues> a, List<ContentValues> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!contentValuesEqual(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    static int contentValuesHashCode(ContentValues cv) {
        if (cv == null) {
            return 0;
        }
        int h = 0;
        for (Map.Entry<String, Object> e : cv.valueSet()) {
            Object v = e.getValue();
            int vh = (v instanceof byte[]) ? Arrays.hashCode((byte[]) v) : Objects.hashCode(v);
            h += Objects.hashCode(e.getKey()) ^ vh;
        }
        return h;
    }

    static int contentValuesListHashCode(List<ContentValues> list) {
        if (list == null) {
            return 0;
        }
        int h = 1;
        for (ContentValues cv : list) {
            h = 31 * h + contentValuesHashCode(cv);
        }
        return h;
    }
}
