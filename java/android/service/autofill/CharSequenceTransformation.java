// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Transformation that replaces a TextView child of RemoteViews using regex extraction on autofill fields. */
public final class CharSequenceTransformation implements Transformation, Parcelable {
    private final ArrayList<AutofillId> mIds;
    private final ArrayList<Pattern> mRegexs;
    private final ArrayList<String> mSubsts;

    CharSequenceTransformation(ArrayList<AutofillId> ids, ArrayList<Pattern> regexs, ArrayList<String> substs) {
        mIds = new ArrayList<AutofillId>(ids);
        mRegexs = new ArrayList<Pattern>(regexs);
        mSubsts = new ArrayList<String>(substs);
    }

    CharSequenceTransformation(Parcel in) {
        int n = in.readInt();
        mIds = new ArrayList<AutofillId>(n);
        mRegexs = new ArrayList<Pattern>(n);
        mSubsts = new ArrayList<String>(n);
        for (int i = 0; i < n; i++) {
            mIds.add(in.readTypedObject(AutofillId.CREATOR));
            String p = in.readString();
            int f = in.readInt();
            mRegexs.add(Pattern.compile(p, f));
            mSubsts.add(in.readString());
        }
    }

    @Override
    public String toString() {
        return "CharSequenceTransformation: [fields=" + mIds.size() + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mIds.size());
        for (int i = 0; i < mIds.size(); i++) {
            parcel.writeTypedObject(mIds.get(i), flags);
            parcel.writeString(mRegexs.get(i).pattern());
            parcel.writeInt(mRegexs.get(i).flags());
            parcel.writeString(mSubsts.get(i));
        }
    }

    public static final Parcelable.Creator<CharSequenceTransformation> CREATOR =
            new Parcelable.Creator<CharSequenceTransformation>() {
                @Override
                public CharSequenceTransformation createFromParcel(Parcel parcel) {
                    return new CharSequenceTransformation(parcel);
                }

                @Override
                public CharSequenceTransformation[] newArray(int size) {
                    return new CharSequenceTransformation[size];
                }
            };

    public static class Builder {
        private final ArrayList<AutofillId> mIds = new ArrayList<AutofillId>();
        private final ArrayList<Pattern> mRegexs = new ArrayList<Pattern>();
        private final ArrayList<String> mSubsts = new ArrayList<String>();
        private boolean mDestroyed;

        public Builder(AutofillId id, Pattern regex, String subst) {
            addField(id, regex, subst);
        }

        public Builder addField(AutofillId id, Pattern regex, String subst) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (id == null || regex == null || subst == null) throw new NullPointerException();
            mIds.add(id);
            mRegexs.add(regex);
            mSubsts.add(subst);
            return this;
        }

        public CharSequenceTransformation build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mDestroyed = true;
            return new CharSequenceTransformation(mIds, mRegexs, mSubsts);
        }
    }
}
