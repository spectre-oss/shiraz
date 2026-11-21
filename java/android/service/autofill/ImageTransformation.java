// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.autofill.AutofillId;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Transformation that sets an ImageView resource in RemoteViews based on regex match of an autofill field. */
public final class ImageTransformation implements Transformation, Parcelable {
    private final AutofillId mId;
    private final ArrayList<Pattern> mRegexs;
    private final ArrayList<Integer> mResIds;
    private final ArrayList<CharSequence> mContentDescriptions;

    ImageTransformation(
            AutofillId id,
            ArrayList<Pattern> regexs,
            ArrayList<Integer> resIds,
            ArrayList<CharSequence> contentDescriptions) {
        mId = id;
        mRegexs = new ArrayList<Pattern>(regexs);
        mResIds = new ArrayList<Integer>(resIds);
        mContentDescriptions = new ArrayList<CharSequence>(contentDescriptions);
    }

    ImageTransformation(Parcel in) {
        mId = in.readTypedObject(AutofillId.CREATOR);
        int n = in.readInt();
        mRegexs = new ArrayList<Pattern>(n);
        mResIds = new ArrayList<Integer>(n);
        mContentDescriptions = new ArrayList<CharSequence>(n);
        for (int i = 0; i < n; i++) {
            String p = in.readString();
            int f = in.readInt();
            mRegexs.add(Pattern.compile(p, f));
            mResIds.add(Integer.valueOf(in.readInt()));
            mContentDescriptions.add(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
        }
    }

    @Override
    public String toString() {
        return "ImageTransformation: [id=" + mId + ", options=" + mRegexs.size() + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(mId, flags);
        parcel.writeInt(mRegexs.size());
        for (int i = 0; i < mRegexs.size(); i++) {
            parcel.writeString(mRegexs.get(i).pattern());
            parcel.writeInt(mRegexs.get(i).flags());
            parcel.writeInt(mResIds.get(i).intValue());
            TextUtils.writeToParcel(mContentDescriptions.get(i), parcel, flags);
        }
    }

    public static final Parcelable.Creator<ImageTransformation> CREATOR =
            new Parcelable.Creator<ImageTransformation>() {
                @Override
                public ImageTransformation createFromParcel(Parcel parcel) {
                    return new ImageTransformation(parcel);
                }

                @Override
                public ImageTransformation[] newArray(int size) {
                    return new ImageTransformation[size];
                }
            };

    public static class Builder {
        private final AutofillId mId;
        private final ArrayList<Pattern> mRegexs = new ArrayList<Pattern>();
        private final ArrayList<Integer> mResIds = new ArrayList<Integer>();
        private final ArrayList<CharSequence> mContentDescriptions = new ArrayList<CharSequence>();
        private boolean mDestroyed;

        @Deprecated
        public Builder(AutofillId id, Pattern regex, int resId) {
            if (id == null || regex == null) throw new NullPointerException();
            mId = id;
            addOption(regex, resId);
        }

        public Builder(AutofillId id, Pattern regex, int resId, CharSequence contentDescription) {
            if (id == null || regex == null || contentDescription == null) throw new NullPointerException();
            mId = id;
            addOption(regex, resId, contentDescription);
        }

        @Deprecated
        public Builder addOption(Pattern regex, int resId) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (regex == null) throw new NullPointerException();
            if (resId == 0) throw new IllegalArgumentException("resId cannot be 0");
            mRegexs.add(regex);
            mResIds.add(Integer.valueOf(resId));
            mContentDescriptions.add(null);
            return this;
        }

        public Builder addOption(Pattern regex, int resId, CharSequence contentDescription) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (regex == null || contentDescription == null) throw new NullPointerException();
            if (resId == 0) throw new IllegalArgumentException("resId cannot be 0");
            mRegexs.add(regex);
            mResIds.add(Integer.valueOf(resId));
            mContentDescriptions.add(contentDescription);
            return this;
        }

        public ImageTransformation build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mDestroyed = true;
            return new ImageTransformation(mId, mRegexs, mResIds, mContentDescriptions);
        }
    }
}
