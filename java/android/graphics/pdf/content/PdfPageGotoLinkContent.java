// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.content;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PdfPageGotoLinkContent implements Parcelable {
    private final List<RectF> mBounds;
    private final Destination mDestination;

    public PdfPageGotoLinkContent(List<RectF> bounds, Destination destination) {
        Objects.requireNonNull(bounds, "Bounds cannot be null");
        Objects.requireNonNull(destination, "Destination cannot be null");
        ArrayList<RectF> copy = new ArrayList<RectF>(bounds.size());
        for (int i = 0; i < bounds.size(); i++) {
            RectF r = Objects.requireNonNull(bounds.get(i), "Bound element cannot be null");
            copy.add(new RectF(r));
        }
        this.mBounds = Collections.unmodifiableList(copy);
        this.mDestination = destination;
    }

    public List<RectF> getBounds() {
        return mBounds;
    }

    public Destination getDestination() {
        return mDestination;
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
        mDestination.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<PdfPageGotoLinkContent> CREATOR =
            new Parcelable.Creator<PdfPageGotoLinkContent>() {
                @Override
                public PdfPageGotoLinkContent createFromParcel(Parcel in) {
                    int count = in.readInt();
                    ArrayList<RectF> bounds = new ArrayList<RectF>(Math.max(0, count));
                    for (int i = 0; i < count; i++) {
                        bounds.add(new RectF(
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
                    }
                    Destination destination = Destination.CREATOR.createFromParcel(in);
                    return new PdfPageGotoLinkContent(bounds, destination);
                }

                @Override
                public PdfPageGotoLinkContent[] newArray(int size) {
                    return new PdfPageGotoLinkContent[size];
                }
            };

    public static final class Destination implements Parcelable {
        private final int mPageNumber;
        private final float mXCoordinate;
        private final float mYCoordinate;
        private final float mZoom;

        public Destination(int pageNumber, float xCoordinate, float yCoordinate, float zoom) {
            if (pageNumber < 0) {
                throw new IllegalArgumentException("Page number must be >= 0");
            }
            if (zoom < 0f) {
                throw new IllegalArgumentException("Zoom factor must be >= 0");
            }
            this.mPageNumber = pageNumber;
            this.mXCoordinate = xCoordinate;
            this.mYCoordinate = yCoordinate;
            this.mZoom = zoom;
        }

        public int getPageNumber() {
            return mPageNumber;
        }

        public float getXCoordinate() {
            return mXCoordinate;
        }

        public float getYCoordinate() {
            return mYCoordinate;
        }

        public float getZoom() {
            return mZoom;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            Objects.requireNonNull(dest, "dest");
            dest.writeInt(mPageNumber);
            dest.writeFloat(mXCoordinate);
            dest.writeFloat(mYCoordinate);
            dest.writeFloat(mZoom);
        }

        public static final Parcelable.Creator<Destination> CREATOR =
                new Parcelable.Creator<Destination>() {
                    @Override
                    public Destination createFromParcel(Parcel in) {
                        return new Destination(
                                in.readInt(),
                                in.readFloat(),
                                in.readFloat(),
                                in.readFloat());
                    }

                    @Override
                    public Destination[] newArray(int size) {
                        return new Destination[size];
                    }
                };
    }
}
