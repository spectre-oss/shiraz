// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Remote user input specification and Intent ClipData payload carrier. */
public final class RemoteInput implements Parcelable {
    public static final String RESULTS_CLIP_LABEL = "android.remoteinput.results";
    public static final String EXTRA_RESULTS_DATA = "android.remoteinput.resultsData";

    public static final int SOURCE_FREE_FORM_INPUT = 0;
    public static final int SOURCE_CHOICE = 1;

    public static final int EDIT_CHOICES_BEFORE_SENDING_AUTO = 0;
    public static final int EDIT_CHOICES_BEFORE_SENDING_DISABLED = 1;
    public static final int EDIT_CHOICES_BEFORE_SENDING_ENABLED = 2;

    private static final String EXTRA_DATA_TYPE_RESULTS_DATA =
            "android.remoteinput.dataTypeResultsData";
    private static final String EXTRA_RESULTS_SOURCE = "android.remoteinput.resultsSource";

    private static final int FLAG_ALLOW_FREE_FORM_INPUT = 1;

    private final String mResultKey;
    private final CharSequence mLabel;
    private final CharSequence[] mChoices;
    private final int mFlags;
    private final int mEditChoicesBeforeSending;
    private final Bundle mExtras;
    private final Set<String> mAllowedDataTypes;

    private RemoteInput(
            String resultKey,
            CharSequence label,
            CharSequence[] choices,
            int flags,
            int editChoicesBeforeSending,
            Bundle extras,
            Set<String> allowedDataTypes) {
        this.mResultKey = resultKey;
        this.mLabel = label;
        this.mChoices = choices;
        this.mFlags = flags;
        this.mEditChoicesBeforeSending = editChoicesBeforeSending;
        this.mExtras = extras;
        this.mAllowedDataTypes = Collections.unmodifiableSet(new LinkedHashSet<String>(allowedDataTypes));
        if (getEditChoicesBeforeSending() == EDIT_CHOICES_BEFORE_SENDING_ENABLED
                && !getAllowFreeFormInput()) {
            throw new IllegalArgumentException(
                    "setEditChoicesBeforeSending requires setAllowFreeFormInput");
        }
    }

    private RemoteInput(Parcel in) {
        mResultKey = in.readString();
        mLabel = in.readString();
        int choicesLen = in.readInt();
        if (choicesLen < 0) {
            mChoices = null;
        } else {
            mChoices = new CharSequence[choicesLen];
            for (int i = 0; i < choicesLen; i++) {
                mChoices[i] = in.readString();
            }
        }
        mFlags = in.readInt();
        mEditChoicesBeforeSending = in.readInt();
        Bundle readExtras = in.readBundle(RemoteInput.class.getClassLoader());
        mExtras = readExtras != null ? readExtras : new Bundle();
        int dataTypesSize = in.readInt();
        LinkedHashSet<String> types = new LinkedHashSet<String>();
        for (int i = 0; i < dataTypesSize; i++) {
            String t = in.readString();
            if (t != null) {
                types.add(t);
            }
        }
        mAllowedDataTypes = Collections.unmodifiableSet(types);
    }

    public String getResultKey() {
        return mResultKey;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public CharSequence[] getChoices() {
        return mChoices;
    }

    public Set<String> getAllowedDataTypes() {
        return mAllowedDataTypes;
    }

    public boolean isDataOnly() {
        return !getAllowFreeFormInput()
                && (getChoices() == null || getChoices().length == 0)
                && !getAllowedDataTypes().isEmpty();
    }

    public boolean getAllowFreeFormInput() {
        return (mFlags & FLAG_ALLOW_FREE_FORM_INPUT) != 0;
    }

    public int getEditChoicesBeforeSending() {
        return mEditChoicesBeforeSending;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public static final class Builder {
        private final String mResultKey;
        private final Set<String> mAllowedDataTypes = new LinkedHashSet<String>();
        private final Bundle mExtras = new Bundle();
        private CharSequence mLabel;
        private CharSequence[] mChoices;
        private int mFlags = FLAG_ALLOW_FREE_FORM_INPUT;
        private int mEditChoicesBeforeSending = EDIT_CHOICES_BEFORE_SENDING_AUTO;

        public Builder(String resultKey) {
            if (resultKey == null) {
                throw new IllegalArgumentException("Result key can't be null");
            }
            mResultKey = resultKey;
        }

        public Builder setLabel(CharSequence label) {
            mLabel = label;
            return this;
        }

        public Builder setChoices(CharSequence[] choices) {
            mChoices = choices;
            return this;
        }

        public Builder setAllowDataType(String mimeType, boolean doAllow) {
            if (mimeType != null) {
                if (doAllow) {
                    mAllowedDataTypes.add(mimeType);
                } else {
                    mAllowedDataTypes.remove(mimeType);
                }
            }
            return this;
        }

        public Builder setAllowFreeFormInput(boolean allowFreeFormTextInput) {
            if (allowFreeFormTextInput) {
                mFlags |= FLAG_ALLOW_FREE_FORM_INPUT;
            } else {
                mFlags &= ~FLAG_ALLOW_FREE_FORM_INPUT;
            }
            return this;
        }

        public Builder setEditChoicesBeforeSending(int editChoicesBeforeSending) {
            if (editChoicesBeforeSending < EDIT_CHOICES_BEFORE_SENDING_AUTO
                    || editChoicesBeforeSending > EDIT_CHOICES_BEFORE_SENDING_ENABLED) {
                throw new IllegalArgumentException(
                        "Invalid editChoicesBeforeSending: " + editChoicesBeforeSending);
            }
            mEditChoicesBeforeSending = editChoicesBeforeSending;
            return this;
        }

        public Builder addExtras(Bundle extras) {
            if (extras != null) {
                mExtras.putAll(extras);
            }
            return this;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        public RemoteInput build() {
            return new RemoteInput(
                    mResultKey,
                    mLabel,
                    mChoices,
                    mFlags,
                    mEditChoicesBeforeSending,
                    new Bundle(mExtras),
                    mAllowedDataTypes);
        }
    }

    public static Map<String, Uri> getDataResultsFromIntent(
            Intent intent, String remoteInputResultKey) {
        if (intent == null || remoteInputResultKey == null) {
            return null;
        }
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) {
            return null;
        }
        Bundle extras = clipDataIntent.getExtras();
        if (extras == null) {
            return null;
        }
        Map<String, Uri> results = new HashMap<String, Uri>();
        for (String key : extras.keySet()) {
            if (key != null && key.startsWith(EXTRA_DATA_TYPE_RESULTS_DATA)) {
                String mimeType = key.substring(EXTRA_DATA_TYPE_RESULTS_DATA.length());
                if (mimeType.isEmpty()) {
                    continue;
                }
                Bundle bundle = clipDataIntent.getBundleExtra(key);
                if (bundle == null) {
                    continue;
                }
                String uriStr = bundle.getString(remoteInputResultKey);
                if (uriStr != null && !uriStr.isEmpty()) {
                    results.put(mimeType, Uri.parse(uriStr));
                }
            }
        }
        return results.isEmpty() ? null : results;
    }

    public static Bundle getResultsFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) {
            return null;
        }
        return clipDataIntent.getBundleExtra(EXTRA_RESULTS_DATA);
    }

    public static void addResultsToIntent(
            RemoteInput[] remoteInputs, Intent intent, Bundle results) {
        if (remoteInputs == null || intent == null || results == null) {
            return;
        }
        Intent clipDataIntent = getOrCreateClipDataIntent(intent);
        Bundle resultsBundle = clipDataIntent.getBundleExtra(EXTRA_RESULTS_DATA);
        if (resultsBundle == null) {
            resultsBundle = new Bundle();
        }
        for (int i = 0; i < remoteInputs.length; i++) {
            RemoteInput remoteInput = remoteInputs[i];
            if (remoteInput == null) {
                continue;
            }
            Object result = results.get(remoteInput.getResultKey());
            if (result instanceof CharSequence) {
                resultsBundle.putCharSequence(remoteInput.getResultKey(), (CharSequence) result);
            }
        }
        clipDataIntent.putExtra(EXTRA_RESULTS_DATA, resultsBundle);
        intent.setClipData(ClipData.newIntent(RESULTS_CLIP_LABEL, clipDataIntent));
    }

    public static void addDataResultToIntent(
            RemoteInput remoteInput, Intent intent, Map<String, Uri> results) {
        if (remoteInput == null || intent == null || results == null) {
            return;
        }
        Intent clipDataIntent = getOrCreateClipDataIntent(intent);
        for (Map.Entry<String, Uri> entry : results.entrySet()) {
            String mimeType = entry.getKey();
            Uri uri = entry.getValue();
            if (mimeType == null) {
                continue;
            }
            String extraKey = getExtraResultsKeyForData(mimeType);
            Bundle resultsBundle = clipDataIntent.getBundleExtra(extraKey);
            if (resultsBundle == null) {
                resultsBundle = new Bundle();
            }
            if (uri != null) {
                resultsBundle.putString(remoteInput.getResultKey(), uri.toString());
            }
            clipDataIntent.putExtra(extraKey, resultsBundle);
        }
        intent.setClipData(ClipData.newIntent(RESULTS_CLIP_LABEL, clipDataIntent));
    }

    public static void setResultsSource(Intent intent, int source) {
        if (intent == null) {
            return;
        }
        Intent clipDataIntent = getOrCreateClipDataIntent(intent);
        clipDataIntent.putExtra(EXTRA_RESULTS_SOURCE, source);
        intent.setClipData(ClipData.newIntent(RESULTS_CLIP_LABEL, clipDataIntent));
    }

    public static int getResultsSource(Intent intent) {
        if (intent == null) {
            return SOURCE_FREE_FORM_INPUT;
        }
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) {
            return SOURCE_FREE_FORM_INPUT;
        }
        return clipDataIntent.getIntExtra(EXTRA_RESULTS_SOURCE, SOURCE_FREE_FORM_INPUT);
    }

    private static String getExtraResultsKeyForData(String mimeType) {
        return EXTRA_DATA_TYPE_RESULTS_DATA + mimeType;
    }

    private static Intent getClipDataIntentFromIntent(Intent intent) {
        ClipData clipData = intent.getClipData();
        if (clipData == null) {
            return null;
        }
        ClipDescription clipDescription = clipData.getDescription();
        if (clipDescription == null
                || !clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_INTENT)) {
            return null;
        }
        CharSequence label = clipDescription.getLabel();
        if (label == null || !RESULTS_CLIP_LABEL.equals(label.toString())) {
            return null;
        }
        if (clipData.getItemCount() <= 0) {
            return null;
        }
        return clipData.getItemAt(0).getIntent();
    }

    private static Intent getOrCreateClipDataIntent(Intent intent) {
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) {
            clipDataIntent = new Intent();
        }
        return clipDataIntent;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mResultKey);
        out.writeString(mLabel == null ? null : mLabel.toString());
        if (mChoices == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(mChoices.length);
            for (int i = 0; i < mChoices.length; i++) {
                out.writeString(mChoices[i] == null ? null : mChoices[i].toString());
            }
        }
        out.writeInt(mFlags);
        out.writeInt(mEditChoicesBeforeSending);
        out.writeBundle(mExtras);
        out.writeInt(mAllowedDataTypes.size());
        for (String type : mAllowedDataTypes) {
            out.writeString(type);
        }
    }

    public static final Parcelable.Creator<RemoteInput> CREATOR =
            new Parcelable.Creator<RemoteInput>() {
                @Override
                public RemoteInput createFromParcel(Parcel in) {
                    return new RemoteInput(in);
                }

                @Override
                public RemoteInput[] newArray(int size) {
                    return new RemoteInput[size];
                }
            };
}
