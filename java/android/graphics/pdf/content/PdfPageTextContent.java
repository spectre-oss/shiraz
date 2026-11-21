// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.content;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PdfPageTextContent implements Parcelable {
    private final String mText;
    private final List<RectF> mBounds;

    public PdfPageTextContent(String text) {
        this(text, Collections.<RectF>emptyList());
    }

    public PdfPageTextContent(String text, List<RectF> bounds) {
        this.mText = Objects.requireNonNull(text, "Text cannot be null");
        Objects.requireNonNull(bounds, "Bounds cannot be null");
        ArrayList<RectF> copy = new ArrayList<RectF>(bounds.size());
        for (int i = 0; i < bounds.size(); i++) {
            RectF r = Objects.requireNonNull(bounds.get(i), "Bound element cannot be null");
            copy.add(new RectF(r));
        }
        this.mBounds = Collections.unmodifiableList(copy);
    }

    public String getText() {
        return mText;
    }

    public List<RectF> getBounds() {
        return mBounds;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mText);
        dest.writeInt(mBounds.size());
        for (int i = 0; i < mBounds.size(); i++) {
            RectF r = mBounds.get(i);
            dest.writeFloat(r.left);
            dest.writeFloat(r.top);
            dest.writeFloat(r.right);
            dest.writeFloat(r.bottom);
        }
    }

    public static final Parcelable.Creator<PdfPageTextContent> CREATOR =
            new Parcelable.Creator<PdfPageTextContent>() {
                @Override
                public PdfPageTextContent createFromParcel(Parcel in) {
                    String text = in.readString();
                    int count = in.readInt();
                    ArrayList<RectF> bounds = new ArrayList<RectF>(Math.max(0, count));
                    for (int i = 0; i < count; i++) {
                        bounds.add(new RectF(
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
                    }
                    return new PdfPageTextContent(text, bounds);
                }

                @Override
                public PdfPageTextContent[] newArray(int size) {
                    return new PdfPageTextContent[size];
                }
            };
}
