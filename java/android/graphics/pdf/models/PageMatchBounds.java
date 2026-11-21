// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.models;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PageMatchBounds implements Parcelable {
    private final List<RectF> mBounds;
    private final int mTextStartIndex;

    public PageMatchBounds(List<RectF> bounds, int textStartIndex) {
        Objects.requireNonNull(bounds, "Bounds cannot be null");
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("Bounds cannot be empty");
        }
        if (textStartIndex < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }
        ArrayList<RectF> copy = new ArrayList<RectF>(bounds.size());
        for (int i = 0; i < bounds.size(); i++) {
            RectF r = Objects.requireNonNull(bounds.get(i), "Bound element cannot be null");
            copy.add(new RectF(r));
        }
        this.mBounds = Collections.unmodifiableList(copy);
        this.mTextStartIndex = textStartIndex;
    }

    public List<RectF> getBounds() {
        return mBounds;
    }

    public int getTextStartIndex() {
        return mTextStartIndex;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mBounds.size());
        for (int i = 0; i < mBounds.size(); i++) {
            RectF r = mBounds.get(i);
            dest.writeFloat(r.left);
            dest.writeFloat(r.top);
            dest.writeFloat(r.right);
            dest.writeFloat(r.bottom);
        }
        dest.writeInt(mTextStartIndex);
    }

    public static final Parcelable.Creator<PageMatchBounds> CREATOR =
            new Parcelable.Creator<PageMatchBounds>() {
                @Override
                public PageMatchBounds createFromParcel(Parcel in) {
                    int count = in.readInt();
                    ArrayList<RectF> bounds = new ArrayList<RectF>(Math.max(0, count));
                    for (int i = 0; i < count; i++) {
                        bounds.add(new RectF(
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
                    }
                    int startIndex = in.readInt();
                    return new PageMatchBounds(bounds, startIndex);
                }

                @Override
                public PageMatchBounds[] newArray(int size) {
                    return new PageMatchBounds[size];
                }
            };
}
