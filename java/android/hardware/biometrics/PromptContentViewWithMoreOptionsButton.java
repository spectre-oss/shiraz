// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.biometrics;

import android.content.DialogInterface;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class PromptContentViewWithMoreOptionsButton
        implements PromptContentView, Parcelable {
    static final int MAX_DESCRIPTION_CHARACTER_NUMBER = 225;

    private final String mDescription;
    private final DialogInterface.OnClickListener mListener;

    private PromptContentViewWithMoreOptionsButton(
            String description, DialogInterface.OnClickListener listener) {
        mDescription = description;
        mListener = listener;
    }

    private PromptContentViewWithMoreOptionsButton(Parcel in) {
        mDescription = in.readString();
        mListener = null;
    }

    public String getDescription() {
        return mDescription;
    }

    public DialogInterface.OnClickListener getMoreOptionsButtonListener() {
        return mListener;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDescription);
    }

    public static final Parcelable.Creator<PromptContentViewWithMoreOptionsButton> CREATOR =
            new Parcelable.Creator<PromptContentViewWithMoreOptionsButton>() {
                @Override
                public PromptContentViewWithMoreOptionsButton createFromParcel(Parcel in) {
                    return new PromptContentViewWithMoreOptionsButton(in);
                }

                @Override
                public PromptContentViewWithMoreOptionsButton[] newArray(int size) {
                    return new PromptContentViewWithMoreOptionsButton[size];
                }
            };

    public static final class Builder {
        private String mDescription;
        private Executor mExecutor;
        private DialogInterface.OnClickListener mListener;

        public Builder() {
        }

        public Builder setDescription(String description) {
            if (description != null && description.length() > MAX_DESCRIPTION_CHARACTER_NUMBER) {
                throw new IllegalArgumentException(
                        "The character number of description exceeds "
                                + MAX_DESCRIPTION_CHARACTER_NUMBER);
            }
            mDescription = description;
            return this;
        }

        public Builder setMoreOptionsButtonListener(
                Executor executor, DialogInterface.OnClickListener listener) {
            mExecutor = Objects.requireNonNull(executor, "executor");
            mListener = Objects.requireNonNull(listener, "listener");
            return this;
        }

        public PromptContentViewWithMoreOptionsButton build() {
            if (mListener == null || mExecutor == null) {
                throw new IllegalArgumentException(
                        "The listener of more options button on prompt content must be set");
            }
            return new PromptContentViewWithMoreOptionsButton(mDescription, mListener);
        }
    }
}
