// SPDX-License-Identifier: GPL-3.0-only
package android.app.slice;

import android.app.PendingIntent;
import android.app.RemoteInput;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Typed data item contained inside a {@link Slice}. */
public final class SliceItem implements Parcelable {
    public static final String FORMAT_SLICE = "slice";
    public static final String FORMAT_TEXT = "text";
    public static final String FORMAT_IMAGE = "image";
    public static final String FORMAT_ACTION = "action";
    public static final String FORMAT_INT = "int";
    public static final String FORMAT_LONG = "long";
    public static final String FORMAT_REMOTE_INPUT = "input";
    public static final String FORMAT_BUNDLE = "bundle";

    private static final AtomicInteger NEXT_LOCAL_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Icon> LOCAL_ICONS =
            new ConcurrentHashMap<Integer, Icon>();
    private static final ConcurrentHashMap<Integer, PendingIntent> LOCAL_ACTIONS =
            new ConcurrentHashMap<Integer, PendingIntent>();

    private final String[] mHints;
    private final String mFormat;
    private final String mSubType;
    private final Object mObj;
    private final Slice mActionSlice;

    SliceItem(Object obj, String format, String subType, List<String> hints) {
        this(
                obj,
                null,
                format,
                subType,
                hints == null ? new String[0] : hints.toArray(new String[hints.size()]));
    }

    SliceItem(Object obj, String format, String subType, String[] hints) {
        this(obj, null, format, subType, hints);
    }

    SliceItem(
            PendingIntent action,
            Slice slice,
            String format,
            String subType,
            String[] hints) {
        this((Object) action, slice, format, subType, hints);
    }

    private SliceItem(
            Object obj, Slice actionSlice, String format, String subType, String[] hints) {
        this.mObj = obj;
        this.mActionSlice = actionSlice;
        this.mFormat = Objects.requireNonNull(format, "format");
        this.mSubType = subType;
        this.mHints = hints == null ? new String[0] : hints.clone();
    }

    private SliceItem(Parcel in) {
        mHints = in.createStringArray();
        mFormat = Objects.requireNonNull(in.readString(), "format");
        mSubType = in.readString();
        if (FORMAT_SLICE.equals(mFormat)) {
            mObj = in.readTypedObject(Slice.CREATOR);
            mActionSlice = null;
        } else if (FORMAT_TEXT.equals(mFormat)) {
            mObj = in.readString();
            mActionSlice = null;
        } else if (FORMAT_IMAGE.equals(mFormat)) {
            mObj = readIconFromParcel(in);
            mActionSlice = null;
        } else if (FORMAT_ACTION.equals(mFormat)) {
            mObj = readPendingIntentFromParcel(in);
            mActionSlice = in.readTypedObject(Slice.CREATOR);
        } else if (FORMAT_INT.equals(mFormat)) {
            mObj = Integer.valueOf(in.readInt());
            mActionSlice = null;
        } else if (FORMAT_LONG.equals(mFormat)) {
            mObj = Long.valueOf(in.readLong());
            mActionSlice = null;
        } else if (FORMAT_REMOTE_INPUT.equals(mFormat)) {
            mObj = in.readTypedObject(RemoteInput.CREATOR);
            mActionSlice = null;
        } else if (FORMAT_BUNDLE.equals(mFormat)) {
            mObj = in.readBundle(SliceItem.class.getClassLoader());
            mActionSlice = null;
        } else {
            throw new IllegalArgumentException("Unsupported SliceItem format: " + mFormat);
        }
    }

    public List<String> getHints() {
        return Collections.unmodifiableList(Arrays.asList(mHints));
    }

    public String getFormat() {
        return mFormat;
    }

    public String getSubType() {
        return mSubType;
    }

    public CharSequence getText() {
        return (CharSequence) mObj;
    }

    public Bundle getBundle() {
        return (Bundle) mObj;
    }

    public Icon getIcon() {
        return (Icon) mObj;
    }

    public PendingIntent getAction() {
        return (PendingIntent) mObj;
    }

    public RemoteInput getRemoteInput() {
        return (RemoteInput) mObj;
    }

    public int getInt() {
        return ((Integer) mObj).intValue();
    }

    public Slice getSlice() {
        if (FORMAT_ACTION.equals(mFormat)) {
            return mActionSlice;
        }
        return (Slice) mObj;
    }

    public long getLong() {
        return ((Long) mObj).longValue();
    }

    public boolean hasHint(String hint) {
        if (hint == null || mHints == null) {
            return false;
        }
        for (int i = 0; i < mHints.length; i++) {
            if (hint.equals(mHints[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mHints);
        dest.writeString(mFormat);
        dest.writeString(mSubType);
        if (FORMAT_SLICE.equals(mFormat)) {
            dest.writeTypedObject((Slice) mObj, flags);
        } else if (FORMAT_TEXT.equals(mFormat)) {
            dest.writeString(mObj == null ? null : mObj.toString());
        } else if (FORMAT_IMAGE.equals(mFormat)) {
            writeIconToParcel((Icon) mObj, dest);
        } else if (FORMAT_ACTION.equals(mFormat)) {
            writePendingIntentToParcel((PendingIntent) mObj, dest, flags);
            dest.writeTypedObject(mActionSlice, flags);
        } else if (FORMAT_INT.equals(mFormat)) {
            dest.writeInt(((Integer) mObj).intValue());
        } else if (FORMAT_LONG.equals(mFormat)) {
            dest.writeLong(((Long) mObj).longValue());
        } else if (FORMAT_REMOTE_INPUT.equals(mFormat)) {
            dest.writeTypedObject((RemoteInput) mObj, flags);
        } else if (FORMAT_BUNDLE.equals(mFormat)) {
            dest.writeBundle((Bundle) mObj);
        } else {
            throw new IllegalArgumentException("Unsupported SliceItem format: " + mFormat);
        }
    }

    private static void writeIconToParcel(Icon icon, Parcel dest) {
        if (icon == null) {
            dest.writeInt(0);
            return;
        }
        int type = icon.getType();
        if (type == Icon.TYPE_RESOURCE) {
            dest.writeInt(1);
            dest.writeString(icon.getResPackage());
            dest.writeInt(icon.getResId());
        } else if (type == Icon.TYPE_URI) {
            dest.writeInt(2);
            dest.writeString(icon.getUri().toString());
        } else {
            int id = NEXT_LOCAL_ID.getAndIncrement();
            if (LOCAL_ICONS.size() >= 1024) {
                LOCAL_ICONS.clear();
            }
            LOCAL_ICONS.put(Integer.valueOf(id), icon);
            dest.writeInt(3);
            dest.writeInt(id);
        }
    }

    private static Icon readIconFromParcel(Parcel in) {
        int mode = in.readInt();
        if (mode == 0) {
            return null;
        }
        if (mode == 1) {
            String pkg = in.readString();
            int resId = in.readInt();
            return Icon.createWithResource(pkg, resId);
        }
        if (mode == 2) {
            return Icon.createWithContentUri(in.readString());
        }
        if (mode == 3) {
            int id = in.readInt();
            return LOCAL_ICONS.get(Integer.valueOf(id));
        }
        throw new IllegalArgumentException("Invalid Icon parcel mode: " + mode);
    }

    private static void writePendingIntentToParcel(PendingIntent pi, Parcel dest, int flags) {
        if (pi == null) {
            dest.writeInt(0);
            return;
        }
        Parcel probe = Parcel.obtain();
        try {
            pi.writeToParcel(probe, flags);
            dest.writeInt(1);
            pi.writeToParcel(dest, flags);
        } catch (UnsupportedOperationException localOnly) {
            int id = NEXT_LOCAL_ID.getAndIncrement();
            if (LOCAL_ACTIONS.size() >= 1024) {
                LOCAL_ACTIONS.clear();
            }
            LOCAL_ACTIONS.put(Integer.valueOf(id), pi);
            dest.writeInt(2);
            dest.writeInt(id);
        } finally {
            probe.recycle();
        }
    }

    private static PendingIntent readPendingIntentFromParcel(Parcel in) {
        int mode = in.readInt();
        if (mode == 0) {
            return null;
        }
        if (mode == 1) {
            return PendingIntent.CREATOR.createFromParcel(in);
        }
        if (mode == 2) {
            int id = in.readInt();
            return LOCAL_ACTIONS.get(Integer.valueOf(id));
        }
        throw new IllegalArgumentException("Invalid PendingIntent parcel mode: " + mode);
    }

    public static final Parcelable.Creator<SliceItem> CREATOR =
            new Parcelable.Creator<SliceItem>() {
                @Override
                public SliceItem createFromParcel(Parcel in) {
                    return new SliceItem(in);
                }

                @Override
                public SliceItem[] newArray(int size) {
                    return new SliceItem[size];
                }
            };
}
