// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.content.IntentSender;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.inputmethod.InlineSuggestionsRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Represents a request to an AutofillService to interpret an activity and provide datasets. */
public final class FillRequest implements Parcelable {
    public static final int FLAG_MANUAL_REQUEST = 1;
    public static final int FLAG_COMPATIBILITY_MODE_REQUEST = 2;
    public static final int FLAG_SUPPORTS_FILL_DIALOG = 64;

    private final int mId;
    private final List<FillContext> mFillContexts;
    private final List<String> mHints;
    private final Bundle mClientState;
    private final int mFlags;
    private final InlineSuggestionsRequest mInlineSuggestionsRequest;
    private final IntentSender mDelayedFillIntentSender;

    public FillRequest(
            int id,
            List<FillContext> fillContexts,
            List<String> hints,
            Bundle clientState,
            int flags,
            InlineSuggestionsRequest inlineSuggestionsRequest,
            IntentSender delayedFillIntentSender) {
        mId = id;
        mFillContexts = fillContexts != null
                ? Collections.unmodifiableList(new ArrayList<FillContext>(fillContexts))
                : Collections.<FillContext>emptyList();
        mHints = hints != null
                ? Collections.unmodifiableList(new ArrayList<String>(hints))
                : Collections.<String>emptyList();
        mClientState = clientState;
        mFlags = flags;
        mInlineSuggestionsRequest = inlineSuggestionsRequest;
        mDelayedFillIntentSender = delayedFillIntentSender;
    }

    FillRequest(Parcel in) {
        mId = in.readInt();
        ArrayList<FillContext> ctxs = in.createTypedArrayList(FillContext.CREATOR);
        mFillContexts = ctxs != null ? Collections.unmodifiableList(ctxs) : Collections.<FillContext>emptyList();
        int hn = in.readInt();
        ArrayList<String> hs = new ArrayList<String>(Math.max(0, hn));
        for (int i = 0; i < hn; i++) hs.add(in.readString());
        mHints = Collections.unmodifiableList(hs);
        mClientState = in.readBundle(FillRequest.class.getClassLoader());
        mFlags = in.readInt();
        mInlineSuggestionsRequest = in.readTypedObject(InlineSuggestionsRequest.CREATOR);
        mDelayedFillIntentSender = in.readTypedObject(IntentSender.CREATOR);
    }

    public int getId() {
        return mId;
    }

    public List<FillContext> getFillContexts() {
        return mFillContexts;
    }

    public List<String> getHints() {
        return mHints;
    }

    public Bundle getClientState() {
        return mClientState;
    }

    public int getFlags() {
        return mFlags;
    }

    public InlineSuggestionsRequest getInlineSuggestionsRequest() {
        return mInlineSuggestionsRequest;
    }

    public IntentSender getDelayedFillIntentSender() {
        return mDelayedFillIntentSender;
    }

    @Override
    public String toString() {
        return "FillRequest[id=" + mId + ", flags=" + mFlags + ", contexts=" + mFillContexts.size() + "]";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeTypedList(mFillContexts);
        dest.writeInt(mHints.size());
        for (int i = 0; i < mHints.size(); i++) dest.writeString(mHints.get(i));
        dest.writeBundle(mClientState);
        dest.writeInt(mFlags);
        dest.writeTypedObject(mInlineSuggestionsRequest, flags);
        dest.writeTypedObject(mDelayedFillIntentSender, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<FillRequest> CREATOR =
            new Parcelable.Creator<FillRequest>() {
                @Override
                public FillRequest createFromParcel(Parcel in) {
                    return new FillRequest(in);
                }

                @Override
                public FillRequest[] newArray(int size) {
                    return new FillRequest[size];
                }
            };
}
