// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.os.*;
import java.util.*;

final class BundleFixture {
    static final class Marker implements Parcelable {
        public int describeContents() { return CONTENTS_FILE_DESCRIPTOR; }
        public void writeToParcel(Parcel parcel, int flags) { throw new AssertionError("Unexpected parcel transport"); }
    }
    static void check() {
        Bundle bundle = new Bundle(16); BaseBundle base = bundle;
        base.putString("s", "text"); base.putInt("i", -7); base.putLong("l", Long.MAX_VALUE);
        base.putBoolean("b", true); base.putDouble("d", Double.NaN);
        bundle.putByte("byte", (byte)-5); bundle.putShort("short", (short)32000); bundle.putChar("char", '\u263a'); bundle.putFloat("float", -0f);
        if (!"text".equals(base.getString("s")) || base.getInt("i") != -7 || base.getLong("l") != Long.MAX_VALUE
                || !base.getBoolean("b") || !Double.isNaN(base.getDouble("d")) || bundle.getByte("byte") != -5
                || bundle.getShort("short") != 32000 || bundle.getChar("char") != '\u263a' || Float.floatToRawIntBits(bundle.getFloat("float")) != 0x80000000)
            throw new AssertionError("BaseBundle/Bundle scalar dispatch");
        if (base.getInt("absent", 17) != 17 || base.getLong("absent", 19) != 19 || !base.getBoolean("absent", true)
                || base.getDouble("absent", 3.5) != 3.5 || bundle.getFloat("absent", 4f) != 4f || !"fallback".equals(base.getString("absent", "fallback"))
                || base.getInt("s", 23) != 23 || base.getBoolean("i") || base.getString("i") != null || base.getLong("i", 9) != 9)
            throw new AssertionError("Typed defaults must not coerce unrelated types");
        base.putString(null, null);
        if (!base.containsKey(null) || base.get(null) != null || !"fallback".equals(base.getString(null, "fallback"))) throw new AssertionError("Stored null");
        base.putIntArray("ints", new int[]{1, 2}); base.putLongArray("longs", new long[]{3});
        base.putBooleanArray("booleans", new boolean[]{true}); base.putDoubleArray("doubles", new double[]{5.5}); base.putStringArray("strings", new String[]{"x", null});
        bundle.putByteArray("bytes", new byte[]{-1}); bundle.putShortArray("shorts", new short[]{2}); bundle.putCharArray("chars", new char[]{'x'}); bundle.putFloatArray("floats", new float[]{1.25f});
        if (base.getIntArray("ints")[1] != 2 || base.getLongArray("longs")[0] != 3 || !base.getBooleanArray("booleans")[0]
                || base.getDoubleArray("doubles")[0] != 5.5 || base.getStringArray("strings").length != 2 || bundle.getByteArray("bytes")[0] != -1
                || bundle.getShortArray("shorts")[0] != 2 || bundle.getCharArray("chars")[0] != 'x' || bundle.getFloatArray("floats")[0] != 1.25f
                || base.getIntArray("strings") != null) throw new AssertionError("Typed arrays");
        Bundle nested = new Bundle(); nested.putInt("nested", 42); bundle.putBundle("nested", nested);
        ArrayList<String> strings = new ArrayList<String>(Arrays.asList("a", "b")); bundle.putStringArrayList("list", strings);
        ArrayList<Integer> numbers = new ArrayList<Integer>(Arrays.asList(1, 2)); bundle.putIntegerArrayList("numbers", numbers);
        StringBuilder text = new StringBuilder("chars"); bundle.putCharSequence("chars-text", text); bundle.putCharSequenceArray("char-array", new CharSequence[]{text});
        bundle.putSerializable("serial", numbers);
        if (bundle.getBundle("nested") != nested || bundle.getStringArrayList("list") != strings || bundle.getIntegerArrayList("numbers") != numbers
                || bundle.getCharSequence("chars-text") != text || bundle.getCharSequenceArray("char-array")[0] != text || bundle.getSerializable("serial") != numbers)
            throw new AssertionError("In-process nested/list/serializable identity");
        Marker marker = new Marker(); bundle.putParcelable("parcelable", marker); bundle.putParcelableArray("parcelables", new Parcelable[]{marker});
        ArrayList<Marker> markers = new ArrayList<Marker>(); markers.add(marker); bundle.putParcelableArrayList("parcelable-list", markers);
        if (bundle.getParcelable("parcelable") != marker || bundle.getParcelableArray("parcelables")[0] != marker
                || bundle.<Marker>getParcelableArrayList("parcelable-list") != markers || bundle.getParcelable("s") != null
                || !bundle.hasFileDescriptors() || (bundle.describeContents() & Parcelable.CONTENTS_FILE_DESCRIPTOR) == 0)
            throw new AssertionError("In-process Parcelable metadata");
        ClassLoader loader = new ClassLoader(BundleFixture.class.getClassLoader()) { };
        bundle.setClassLoader(loader); Bundle copy = new Bundle(bundle), cloned = (Bundle)bundle.clone();
        copy.putInt("i", 99); cloned.remove("s");
        if (base.getInt("i") != -7 || !base.containsKey("s") || copy.getBundle("nested") != nested || copy.getIntArray("ints") != base.getIntArray("ints")
                || copy.getClassLoader() != loader || new Bundle(loader).getClassLoader() != loader)
            throw new AssertionError("Independent maps / shallow container copy / classloader");
        copy.getIntArray("ints")[0] = 8;
        if (base.getIntArray("ints")[0] != 8) throw new AssertionError("Array values are shallow shared");
        Bundle merged = new Bundle(); merged.putAll(bundle); merged.remove("i");
        if (!base.containsKey("i") || merged.containsKey("i")) throw new AssertionError("putAll map isolation");
        Set<String> keys = merged.keySet(); merged.putString("new-key", "new-value"); keys.remove("new-key");
        if (merged.containsKey("new-key")) throw new AssertionError("Live key set");
        Bundle cycle = new Bundle(); cycle.putBundle("self", cycle);
        if (cycle.describeContents() != 0) throw new AssertionError("Cyclic descriptor graph");
        cycle.putBundle("nested", bundle);
        if (!cycle.hasFileDescriptors()) throw new AssertionError("Nested descriptor graph");
        try { Bundle.EMPTY.putString("forbidden", "value"); throw new AssertionError("Immutable empty bundle changed"); }
        catch (UnsupportedOperationException expected) { }
        Bundle emptyCopy = new Bundle(Bundle.EMPTY); emptyCopy.putInt("mutable", 1);
        if (!Bundle.EMPTY.isEmpty() || emptyCopy.isEmpty()) throw new AssertionError("Mutable empty copy");
        try { bundle.writeToParcel(null, 0); throw new AssertionError("Unsupported parcel write accepted"); }
        catch (UnsupportedOperationException expected) { }
        try { Bundle.CREATOR.createFromParcel(null); throw new AssertionError("Unsupported parcel read accepted"); }
        catch (UnsupportedOperationException expected) { }
        if (Bundle.CREATOR.newArray(2).length != 2) throw new AssertionError("Parcelable array creator");
        Message first = Message.obtain(); first.setData(bundle); Message second = Message.obtain(first);
        second.getData().putInt("i", 101);
        if (first.getData().getInt("i") != -7 || second.getData().getBundle("nested") != nested) throw new AssertionError("Message Bundle copying");
        first.recycle(); second.recycle(); merged.clear();
        if (merged.size() != 0 || !merged.isEmpty() || base.size() == 0) throw new AssertionError("Map clear isolation");
        System.out.println("SHIRAZ_FIXTURE_BUNDLE_OK");
    }
}
