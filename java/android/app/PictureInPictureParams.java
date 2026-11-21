// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Rational;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PictureInPictureParams implements Parcelable {
    private final Rational mAspectRatio;
    private final Rational mExpandedAspectRatio;
    private final List<RemoteAction> mUserActions;
    private final RemoteAction mCloseAction;
    private final Rect mSourceRectHint;
    private final Boolean mAutoEnterEnabled;
    private final Boolean mSeamlessResizeEnabled;
    private final CharSequence mTitle;
    private final CharSequence mSubtitle;

    PictureInPictureParams(Rational aspectRatio, Rational expandedAspectRatio,
            List<RemoteAction> actions, RemoteAction closeAction, Rect sourceRectHint,
            Boolean autoEnterEnabled, Boolean seamlessResizeEnabled,
            CharSequence title, CharSequence subtitle) {
        mAspectRatio = aspectRatio;
        mExpandedAspectRatio = expandedAspectRatio;
        mUserActions = actions != null ? new ArrayList<RemoteAction>(actions) : null;
        mCloseAction = closeAction;
        mSourceRectHint = sourceRectHint != null ? new Rect(sourceRectHint) : null;
        mAutoEnterEnabled = autoEnterEnabled;
        mSeamlessResizeEnabled = seamlessResizeEnabled;
        mTitle = title;
        mSubtitle = subtitle;
    }

    private PictureInPictureParams(Parcel in) {
        mAspectRatio = in.readInt() != 0 ? new Rational(in.readInt(), in.readInt()) : null;
        mExpandedAspectRatio = in.readInt() != 0 ? new Rational(in.readInt(), in.readInt()) : null;
        if (in.readInt() != 0) {
            int count = in.readInt();
            mUserActions = new ArrayList<RemoteAction>(count);
            for (int i = 0; i < count; i++) {
                mUserActions.add(RemoteAction.CREATOR.createFromParcel(in));
            }
        } else {
            mUserActions = null;
        }
        mCloseAction = in.readInt() != 0 ? RemoteAction.CREATOR.createFromParcel(in) : null;
        mSourceRectHint = in.readInt() != 0
                ? new Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt())
                : null;
        mAutoEnterEnabled = in.readInt() != 0 ? Boolean.valueOf(in.readInt() != 0) : null;
        mSeamlessResizeEnabled = in.readInt() != 0 ? Boolean.valueOf(in.readInt() != 0) : null;
        mTitle = in.readInt() != 0 ? in.readString() : null;
        mSubtitle = in.readInt() != 0 ? in.readString() : null;
    }

    public Rational getAspectRatio() {
        return mAspectRatio;
    }

    public Rational getExpandedAspectRatio() {
        return mExpandedAspectRatio;
    }

    public List<RemoteAction> getActions() {
        return mUserActions != null
                ? Collections.unmodifiableList(mUserActions)
                : Collections.<RemoteAction>emptyList();
    }

    public RemoteAction getCloseAction() {
        return mCloseAction;
    }

    public Rect getSourceRectHint() {
        return mSourceRectHint != null ? new Rect(mSourceRectHint) : null;
    }

    public boolean isAutoEnterEnabled() {
        return mAutoEnterEnabled != null && mAutoEnterEnabled.booleanValue();
    }

    public boolean isSeamlessResizeEnabled() {
        return mSeamlessResizeEnabled == null || mSeamlessResizeEnabled.booleanValue();
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PictureInPictureParams)) {
            return false;
        }
        PictureInPictureParams that = (PictureInPictureParams) o;
        return Objects.equals(mAspectRatio, that.mAspectRatio)
                && Objects.equals(mExpandedAspectRatio, that.mExpandedAspectRatio)
                && Objects.equals(mUserActions, that.mUserActions)
                && Objects.equals(mCloseAction, that.mCloseAction)
                && Objects.equals(mSourceRectHint, that.mSourceRectHint)
                && isAutoEnterEnabled() == that.isAutoEnterEnabled()
                && isSeamlessResizeEnabled() == that.isSeamlessResizeEnabled()
                && Objects.equals(mTitle, that.mTitle)
                && Objects.equals(mSubtitle, that.mSubtitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAspectRatio, mExpandedAspectRatio, mUserActions, mCloseAction,
                mSourceRectHint, Boolean.valueOf(isAutoEnterEnabled()),
                Boolean.valueOf(isSeamlessResizeEnabled()), mTitle, mSubtitle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        if (mAspectRatio != null) {
            out.writeInt(1);
            out.writeInt(mAspectRatio.getNumerator());
            out.writeInt(mAspectRatio.getDenominator());
        } else {
            out.writeInt(0);
        }
        if (mExpandedAspectRatio != null) {
            out.writeInt(1);
            out.writeInt(mExpandedAspectRatio.getNumerator());
            out.writeInt(mExpandedAspectRatio.getDenominator());
        } else {
            out.writeInt(0);
        }
        if (mUserActions != null) {
            out.writeInt(1);
            out.writeInt(mUserActions.size());
            for (int i = 0; i < mUserActions.size(); i++) {
                mUserActions.get(i).writeToParcel(out, flags);
            }
        } else {
            out.writeInt(0);
        }
        if (mCloseAction != null) {
            out.writeInt(1);
            mCloseAction.writeToParcel(out, flags);
        } else {
            out.writeInt(0);
        }
        if (mSourceRectHint != null) {
            out.writeInt(1);
            out.writeInt(mSourceRectHint.left);
            out.writeInt(mSourceRectHint.top);
            out.writeInt(mSourceRectHint.right);
            out.writeInt(mSourceRectHint.bottom);
        } else {
            out.writeInt(0);
        }
        if (mAutoEnterEnabled != null) {
            out.writeInt(1);
            out.writeInt(mAutoEnterEnabled.booleanValue() ? 1 : 0);
        } else {
            out.writeInt(0);
        }
        if (mSeamlessResizeEnabled != null) {
            out.writeInt(1);
            out.writeInt(mSeamlessResizeEnabled.booleanValue() ? 1 : 0);
        } else {
            out.writeInt(0);
        }
        if (mTitle != null) {
            out.writeInt(1);
            out.writeString(mTitle.toString());
        } else {
            out.writeInt(0);
        }
        if (mSubtitle != null) {
            out.writeInt(1);
            out.writeString(mSubtitle.toString());
        } else {
            out.writeInt(0);
        }
    }

    @Override
    public String toString() {
        return "PictureInPictureParams(aspectRatio=" + mAspectRatio
                + " expandedAspectRatio=" + mExpandedAspectRatio
                + " sourceRectHint=" + mSourceRectHint
                + " isAutoEnterEnabled=" + isAutoEnterEnabled()
                + " isSeamlessResizeEnabled=" + isSeamlessResizeEnabled() + ")";
    }

    public static final Parcelable.Creator<PictureInPictureParams> CREATOR =
            new Parcelable.Creator<PictureInPictureParams>() {
                @Override
                public PictureInPictureParams createFromParcel(Parcel in) {
                    return new PictureInPictureParams(in);
                }

                @Override
                public PictureInPictureParams[] newArray(int size) {
                    return new PictureInPictureParams[size];
                }
            };

    public static class Builder {
        private Rational mAspectRatio;
        private Rational mExpandedAspectRatio;
        private List<RemoteAction> mUserActions;
        private RemoteAction mCloseAction;
        private Rect mSourceRectHint;
        private Boolean mAutoEnterEnabled;
        private Boolean mSeamlessResizeEnabled;
        private CharSequence mTitle;
        private CharSequence mSubtitle;

        public Builder() {
        }

        public Builder(PictureInPictureParams original) {
            Objects.requireNonNull(original, "original");
            mAspectRatio = original.mAspectRatio;
            mExpandedAspectRatio = original.mExpandedAspectRatio;
            mUserActions = original.mUserActions != null
                    ? new ArrayList<RemoteAction>(original.mUserActions)
                    : null;
            mCloseAction = original.mCloseAction;
            mSourceRectHint = original.mSourceRectHint != null
                    ? new Rect(original.mSourceRectHint)
                    : null;
            mAutoEnterEnabled = original.mAutoEnterEnabled;
            mSeamlessResizeEnabled = original.mSeamlessResizeEnabled;
            mTitle = original.mTitle;
            mSubtitle = original.mSubtitle;
        }

        public Builder setAspectRatio(Rational aspectRatio) {
            if (aspectRatio != null && (aspectRatio.isNaN() || aspectRatio.isZero()
                    || aspectRatio.isInfinite() || aspectRatio.floatValue() <= 0f)) {
                throw new IllegalArgumentException("Invalid aspect ratio: " + aspectRatio);
            }
            mAspectRatio = aspectRatio;
            return this;
        }

        public Builder setExpandedAspectRatio(Rational expandedAspectRatio) {
            if (expandedAspectRatio != null && (expandedAspectRatio.isNaN()
                    || expandedAspectRatio.isInfinite() || expandedAspectRatio.floatValue() < 0f)) {
                throw new IllegalArgumentException("Invalid expanded aspect ratio: " + expandedAspectRatio);
            }
            mExpandedAspectRatio = expandedAspectRatio;
            return this;
        }

        public Builder setActions(List<RemoteAction> actions) {
            mUserActions = actions != null ? new ArrayList<RemoteAction>(actions) : null;
            return this;
        }

        public Builder setCloseAction(RemoteAction action) {
            mCloseAction = action;
            return this;
        }

        public Builder setSourceRectHint(Rect launchBounds) {
            mSourceRectHint = launchBounds != null ? new Rect(launchBounds) : null;
            return this;
        }

        public Builder setAutoEnterEnabled(boolean autoEnterEnabled) {
            mAutoEnterEnabled = Boolean.valueOf(autoEnterEnabled);
            return this;
        }

        public Builder setSeamlessResizeEnabled(boolean seamlessResizeEnabled) {
            mSeamlessResizeEnabled = Boolean.valueOf(seamlessResizeEnabled);
            return this;
        }

        public Builder setTitle(CharSequence title) {
            mTitle = title;
            return this;
        }

        public Builder setSubtitle(CharSequence subtitle) {
            mSubtitle = subtitle;
            return this;
        }

        public PictureInPictureParams build() {
            return new PictureInPictureParams(mAspectRatio, mExpandedAspectRatio,
                    mUserActions, mCloseAction, mSourceRectHint, mAutoEnterEnabled,
                    mSeamlessResizeEnabled, mTitle, mSubtitle);
        }
    }
}
