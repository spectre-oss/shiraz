// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class EditorBoundsInfo implements Parcelable {
    private final RectF mEditorBounds;
    private final RectF mHandwritingBounds;

    private EditorBoundsInfo(RectF editorBounds, RectF handwritingBounds) {
        mEditorBounds = editorBounds != null ? new RectF(editorBounds) : null;
        mHandwritingBounds = handwritingBounds != null ? new RectF(handwritingBounds) : null;
    }

    private EditorBoundsInfo(Parcel source) {
        mEditorBounds = readRectF(source);
        mHandwritingBounds = readRectF(source);
    }

    private static RectF readRectF(Parcel source) {
        if (source.readInt() == 0) {
            return null;
        }
        return new RectF(
                source.readFloat(), source.readFloat(), source.readFloat(), source.readFloat());
    }

    private static void writeRectF(Parcel dest, RectF rect) {
        if (rect == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(1);
            dest.writeFloat(rect.left);
            dest.writeFloat(rect.top);
            dest.writeFloat(rect.right);
            dest.writeFloat(rect.bottom);
        }
    }

    public RectF getEditorBounds() {
        return mEditorBounds != null ? new RectF(mEditorBounds) : null;
    }

    public RectF getHandwritingBounds() {
        return mHandwritingBounds != null ? new RectF(mHandwritingBounds) : null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEditorBounds, mHandwritingBounds);
    }

    @Override
    public String toString() {
        return "EditorBoundsInfo{mEditorBounds=" + mEditorBounds
                + " mHandwritingBounds=" + mHandwritingBounds + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EditorBoundsInfo)) return false;
        EditorBoundsInfo other = (EditorBoundsInfo) obj;
        return Objects.equals(mEditorBounds, other.mEditorBounds)
                && Objects.equals(mHandwritingBounds, other.mHandwritingBounds);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeRectF(dest, mEditorBounds);
        writeRectF(dest, mHandwritingBounds);
    }

    public static final Parcelable.Creator<EditorBoundsInfo> CREATOR =
            new Parcelable.Creator<EditorBoundsInfo>() {
                @Override
                public EditorBoundsInfo createFromParcel(Parcel source) {
                    return new EditorBoundsInfo(source);
                }

                @Override
                public EditorBoundsInfo[] newArray(int size) {
                    return new EditorBoundsInfo[size];
                }
            };

    public static final class Builder {
        private RectF mEditorBounds;
        private RectF mHandwritingBounds;

        public Builder() {
        }

        public Builder setEditorBounds(RectF bounds) {
            mEditorBounds = bounds != null ? new RectF(bounds) : null;
            return this;
        }

        public Builder setHandwritingBounds(RectF bounds) {
            mHandwritingBounds = bounds != null ? new RectF(bounds) : null;
            return this;
        }

        public EditorBoundsInfo build() {
            return new EditorBoundsInfo(mEditorBounds, mHandwritingBounds);
        }
    }
}
