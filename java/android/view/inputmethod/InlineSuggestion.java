// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Size;
import android.widget.inline.InlineContentView;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class InlineSuggestion implements Parcelable {
    private final InlineSuggestionInfo mInfo;

    public InlineSuggestion(InlineSuggestionInfo info) {
        mInfo = Objects.requireNonNull(info, "info must not be null");
    }

    public void inflate(final Context context, Size size, Executor callbackExecutor,
            final Consumer<InlineContentView> callback) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        final InlineContentView view = new InlineContentView(context);
        callbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(view);
            }
        });
    }

    public InlineSuggestionInfo getInfo() {
        return mInfo;
    }

    @Override
    public String toString() {
        return "InlineSuggestion{info=" + mInfo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlineSuggestion)) return false;
        InlineSuggestion that = (InlineSuggestion) o;
        return Objects.equals(mInfo, that.mInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mInfo);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mInfo, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InlineSuggestion> CREATOR = new Creator<InlineSuggestion>() {
        @Override
        public InlineSuggestion createFromParcel(Parcel in) {
            InlineSuggestionInfo info = in.readTypedObject(InlineSuggestionInfo.CREATOR);
            return new InlineSuggestion(info);
        }

        @Override
        public InlineSuggestion[] newArray(int size) {
            return new InlineSuggestion[size];
        }
    };
}
