// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TextAttribute implements Parcelable {
    private static final int TYPE_NULL = 0;
    private static final int TYPE_BOOLEAN = 1;
    private static final int TYPE_INT = 2;
    private static final int TYPE_LONG = 3;
    private static final int TYPE_DOUBLE = 4;
    private static final int TYPE_STRING = 5;
    private static final int TYPE_BOOLEAN_ARRAY = 6;
    private static final int TYPE_INT_ARRAY = 7;
    private static final int TYPE_LONG_ARRAY = 8;
    private static final int TYPE_DOUBLE_ARRAY = 9;
    private static final int TYPE_STRING_ARRAY = 10;
    private static final int TYPE_PERSISTABLE_BUNDLE = 11;

    private final List<String> mTextConversionSuggestions;
    private final PersistableBundle mExtras;

    private TextAttribute(Builder builder) {
        mTextConversionSuggestions = builder.mTextConversionSuggestions;
        mExtras = builder.mExtras;
    }

    private TextAttribute(Parcel source) {
        int count = source.readInt();
        if (count <= 0) {
            mTextConversionSuggestions = Collections.emptyList();
        } else {
            ArrayList<String> list = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) {
                list.add(source.readString());
            }
            mTextConversionSuggestions = Collections.unmodifiableList(list);
        }
        PersistableBundle extras = readPersistableBundle(source);
        mExtras = extras == null ? PersistableBundle.EMPTY : extras;
    }

    public List<String> getTextConversionSuggestions() {
        return mTextConversionSuggestions;
    }

    public PersistableBundle getExtras() {
        return mExtras;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mTextConversionSuggestions.size());
        for (int i = 0; i < mTextConversionSuggestions.size(); i++) {
            dest.writeString(mTextConversionSuggestions.get(i));
        }
        writePersistableBundle(dest, mExtras);
    }

    private static void writePersistableBundle(Parcel dest, PersistableBundle bundle) {
        if (bundle == null || bundle.isEmpty()) {
            dest.writeInt(0);
            return;
        }
        Set<String> keys = bundle.keySet();
        dest.writeInt(keys.size());
        for (String key : keys) {
            dest.writeString(key);
            Object val = bundle.get(key);
            if (val == null) {
                dest.writeInt(TYPE_NULL);
            } else if (val instanceof Boolean) {
                dest.writeInt(TYPE_BOOLEAN);
                dest.writeInt(((Boolean) val).booleanValue() ? 1 : 0);
            } else if (val instanceof Integer) {
                dest.writeInt(TYPE_INT);
                dest.writeInt(((Integer) val).intValue());
            } else if (val instanceof Long) {
                dest.writeInt(TYPE_LONG);
                dest.writeLong(((Long) val).longValue());
            } else if (val instanceof Double) {
                dest.writeInt(TYPE_DOUBLE);
                dest.writeDouble(((Double) val).doubleValue());
            } else if (val instanceof String) {
                dest.writeInt(TYPE_STRING);
                dest.writeString((String) val);
            } else if (val instanceof boolean[]) {
                dest.writeInt(TYPE_BOOLEAN_ARRAY);
                boolean[] arr = (boolean[]) val;
                dest.writeInt(arr.length);
                for (int i = 0; i < arr.length; i++) {
                    dest.writeInt(arr[i] ? 1 : 0);
                }
            } else if (val instanceof int[]) {
                dest.writeInt(TYPE_INT_ARRAY);
                dest.writeIntArray((int[]) val);
            } else if (val instanceof long[]) {
                dest.writeInt(TYPE_LONG_ARRAY);
                dest.writeLongArray((long[]) val);
            } else if (val instanceof double[]) {
                dest.writeInt(TYPE_DOUBLE_ARRAY);
                double[] arr = (double[]) val;
                dest.writeInt(arr.length);
                for (int i = 0; i < arr.length; i++) {
                    dest.writeDouble(arr[i]);
                }
            } else if (val instanceof String[]) {
                dest.writeInt(TYPE_STRING_ARRAY);
                dest.writeStringArray((String[]) val);
            } else if (val instanceof PersistableBundle) {
                dest.writeInt(TYPE_PERSISTABLE_BUNDLE);
                writePersistableBundle(dest, (PersistableBundle) val);
            } else {
                dest.writeInt(TYPE_NULL);
            }
        }
    }

    private static PersistableBundle readPersistableBundle(Parcel source) {
        int size = source.readInt();
        if (size <= 0) {
            return PersistableBundle.EMPTY;
        }
        PersistableBundle bundle = new PersistableBundle(size);
        for (int i = 0; i < size; i++) {
            String key = source.readString();
            int type = source.readInt();
            switch (type) {
                case TYPE_NULL:
                    bundle.putString(key, null);
                    break;
                case TYPE_BOOLEAN:
                    bundle.putBoolean(key, source.readInt() != 0);
                    break;
                case TYPE_INT:
                    bundle.putInt(key, source.readInt());
                    break;
                case TYPE_LONG:
                    bundle.putLong(key, source.readLong());
                    break;
                case TYPE_DOUBLE:
                    bundle.putDouble(key, source.readDouble());
                    break;
                case TYPE_STRING:
                    bundle.putString(key, source.readString());
                    break;
                case TYPE_BOOLEAN_ARRAY: {
                    int n = source.readInt();
                    boolean[] arr = new boolean[Math.max(0, n)];
                    for (int k = 0; k < arr.length; k++) {
                        arr[k] = source.readInt() != 0;
                    }
                    bundle.putBooleanArray(key, arr);
                    break;
                }
                case TYPE_INT_ARRAY:
                    bundle.putIntArray(key, source.createIntArray());
                    break;
                case TYPE_LONG_ARRAY:
                    bundle.putLongArray(key, source.createLongArray());
                    break;
                case TYPE_DOUBLE_ARRAY: {
                    int n = source.readInt();
                    double[] arr = new double[Math.max(0, n)];
                    for (int k = 0; k < arr.length; k++) {
                        arr[k] = source.readDouble();
                    }
                    bundle.putDoubleArray(key, arr);
                    break;
                }
                case TYPE_STRING_ARRAY:
                    bundle.putStringArray(key, source.createStringArray());
                    break;
                case TYPE_PERSISTABLE_BUNDLE:
                    bundle.putPersistableBundle(key, readPersistableBundle(source));
                    break;
                default:
                    break;
            }
        }
        return bundle;
    }

    public static final Parcelable.Creator<TextAttribute> CREATOR =
            new Parcelable.Creator<TextAttribute>() {
                @Override
                public TextAttribute createFromParcel(Parcel source) {
                    return new TextAttribute(source);
                }

                @Override
                public TextAttribute[] newArray(int size) {
                    return new TextAttribute[size];
                }
            };

    public static final class Builder {
        private List<String> mTextConversionSuggestions = Collections.emptyList();
        private PersistableBundle mExtras = PersistableBundle.EMPTY;

        public Builder() {
        }

        public Builder setTextConversionSuggestions(List<String> textConversionSuggestions) {
            Objects.requireNonNull(textConversionSuggestions, "textConversionSuggestions");
            mTextConversionSuggestions = Collections.unmodifiableList(
                    new ArrayList<String>(textConversionSuggestions));
            return this;
        }

        public Builder setExtras(PersistableBundle extras) {
            Objects.requireNonNull(extras, "extras");
            mExtras = extras;
            return this;
        }

        public TextAttribute build() {
            return new TextAttribute(this);
        }
    }
}
