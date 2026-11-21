// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Top-level API for an activity to interact with the user through voice. */
public final class VoiceInteractor {
    private final LinkedHashMap<String, Request> mActiveRequests = new LinkedHashMap<String, Request>();
    private final LinkedHashMap<Runnable, Executor> mOnDestroyedCallbacks = new LinkedHashMap<Runnable, Executor>();
    private Activity mActivity;
    private Context mContext;
    private boolean mDestroyed;

    VoiceInteractor(Activity activity) {
        mActivity = activity;
        mContext = activity;
    }

    public boolean submitRequest(Request request) {
        return submitRequest(request, null);
    }

    public boolean submitRequest(Request request, String name) {
        if (mDestroyed || request == null) return false;
        String key = name != null ? name : ("req_" + System.identityHashCode(request));
        request.mName = name;
        request.mInteractor = this;
        request.mActivity = mActivity;
        request.mContext = mContext;
        synchronized (mActiveRequests) {
            mActiveRequests.put(key, request);
        }
        if (mActivity != null) {
            request.onAttached(mActivity);
        }
        return true;
    }

    public Request[] getActiveRequests() {
        synchronized (mActiveRequests) {
            return mActiveRequests.values().toArray(new Request[mActiveRequests.size()]);
        }
    }

    public Request getActiveRequest(String name) {
        if (name == null) return null;
        synchronized (mActiveRequests) {
            return mActiveRequests.get(name);
        }
    }

    public boolean[] supportsCommands(String[] commands) {
        if (commands == null) return new boolean[0];
        return new boolean[commands.length];
    }

    public boolean isDestroyed() {
        return mDestroyed;
    }

    public boolean registerOnDestroyedCallback(Executor executor, Runnable callback) {
        if (executor == null) throw new NullPointerException("executor");
        if (callback == null) throw new NullPointerException("callback");
        if (mDestroyed) return false;
        synchronized (mOnDestroyedCallbacks) {
            mOnDestroyedCallbacks.put(callback, executor);
        }
        return true;
    }

    public boolean unregisterOnDestroyedCallback(Runnable callback) {
        if (callback == null) throw new NullPointerException("callback");
        synchronized (mOnDestroyedCallbacks) {
            return mOnDestroyedCallbacks.remove(callback) != null;
        }
    }

    public void notifyDirectActionsChanged() {
    }

    public String getPackageName() {
        return mContext != null ? mContext.getPackageName() : "";
    }

    void removeRequest(Request request) {
        synchronized (mActiveRequests) {
            java.util.Iterator<Map.Entry<String, Request>> it = mActiveRequests.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() == request) {
                    it.remove();
                    break;
                }
            }
        }
    }

    public abstract static class Request {
        String mName;
        VoiceInteractor mInteractor;
        Context mContext;
        Activity mActivity;

        Request() {
        }

        public String getName() {
            return mName;
        }

        public void cancel() {
            if (mInteractor != null) {
                mInteractor.removeRequest(this);
            }
            onCancel();
        }

        public Context getContext() {
            return mContext;
        }

        public Activity getActivity() {
            return mActivity;
        }

        public void onCancel() {
        }

        public void onAttached(Activity activity) {
        }

        public void onDetached() {
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{name=" + mName + "}";
        }
    }

    public static class ConfirmationRequest extends Request {
        final Prompt mPrompt;
        final Bundle mExtras;

        public ConfirmationRequest(Prompt prompt, Bundle extras) {
            mPrompt = prompt;
            mExtras = extras;
        }

        public void onConfirmationResult(boolean confirmed, Bundle result) {
        }
    }

    public static class PickOptionRequest extends Request {
        final Prompt mPrompt;
        final Option[] mOptions;
        final Bundle mExtras;

        public PickOptionRequest(Prompt prompt, Option[] options, Bundle extras) {
            mPrompt = prompt;
            mOptions = options;
            mExtras = extras;
        }

        public void onPickOptionResult(boolean finished, Option[] selections, Bundle result) {
        }

        public static final class Option implements Parcelable {
            private final CharSequence mLabel;
            private final int mIndex;
            private final ArrayList<CharSequence> mSynonyms = new ArrayList<CharSequence>();
            private Bundle mExtras;

            public Option(CharSequence label, int index) {
                mLabel = label;
                mIndex = index;
            }

            Option(Parcel in) {
                mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                mIndex = in.readInt();
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    mSynonyms.add(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                }
                mExtras = in.readBundle(Option.class.getClassLoader());
            }

            public Option addSynonym(CharSequence synonym) {
                mSynonyms.add(synonym);
                return this;
            }

            public CharSequence getLabel() {
                return mLabel;
            }

            public int getIndex() {
                return mIndex;
            }

            public int countSynonyms() {
                return mSynonyms.size();
            }

            public CharSequence getSynonymAt(int index) {
                return mSynonyms.get(index);
            }

            public void setExtras(Bundle extras) {
                mExtras = extras;
            }

            public Bundle getExtras() {
                return mExtras;
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                TextUtils.writeToParcel(mLabel, dest, flags);
                dest.writeInt(mIndex);
                dest.writeInt(mSynonyms.size());
                for (int i = 0; i < mSynonyms.size(); i++) {
                    TextUtils.writeToParcel(mSynonyms.get(i), dest, flags);
                }
                dest.writeBundle(mExtras);
            }

            public static final Parcelable.Creator<Option> CREATOR =
                    new Parcelable.Creator<Option>() {
                        @Override
                        public Option createFromParcel(Parcel in) {
                            return new Option(in);
                        }

                        @Override
                        public Option[] newArray(int size) {
                            return new Option[size];
                        }
                    };
        }
    }

    public static class CompleteVoiceRequest extends Request {
        final Prompt mPrompt;
        final Bundle mExtras;

        public CompleteVoiceRequest(Prompt prompt, Bundle extras) {
            mPrompt = prompt;
            mExtras = extras;
        }

        public void onCompleteResult(Bundle result) {
        }
    }

    public static class AbortVoiceRequest extends Request {
        final Prompt mPrompt;
        final Bundle mExtras;

        public AbortVoiceRequest(Prompt prompt, Bundle extras) {
            mPrompt = prompt;
            mExtras = extras;
        }

        public void onAbortResult(Bundle result) {
        }
    }

    public static class CommandRequest extends Request {
        final String mCommand;
        final Bundle mArgs;

        public CommandRequest(String command, Bundle args) {
            mCommand = command;
            mArgs = args;
        }

        public void onCommandResult(boolean isCompleted, Bundle result) {
        }
    }

    public static class Prompt implements Parcelable {
        private final CharSequence[] mVoicePrompts;
        private final CharSequence mVisualPrompt;

        public Prompt(CharSequence[] voicePrompts, CharSequence visualPrompt) {
            if (voicePrompts == null) throw new NullPointerException("voicePrompts must not be null");
            if (voicePrompts.length == 0) throw new IllegalArgumentException("voicePrompts must not be empty");
            if (visualPrompt == null) throw new NullPointerException("visualPrompt must not be null");
            mVoicePrompts = voicePrompts.clone();
            mVisualPrompt = visualPrompt;
        }

        public Prompt(CharSequence prompt) {
            if (prompt == null) throw new NullPointerException("prompt must not be null");
            mVoicePrompts = new CharSequence[]{prompt};
            mVisualPrompt = prompt;
        }

        Prompt(Parcel in) {
            int count = in.readInt();
            mVoicePrompts = new CharSequence[count];
            for (int i = 0; i < count; i++) {
                mVoicePrompts[i] = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            }
            mVisualPrompt = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        }

        public CharSequence getVoicePromptAt(int index) {
            return mVoicePrompts[index];
        }

        public int countVoicePrompts() {
            return mVoicePrompts.length;
        }

        public CharSequence getVisualPrompt() {
            return mVisualPrompt;
        }

        @Override
        public String toString() {
            return String.valueOf(mVisualPrompt);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mVoicePrompts.length);
            for (int i = 0; i < mVoicePrompts.length; i++) {
                TextUtils.writeToParcel(mVoicePrompts[i], dest, flags);
            }
            TextUtils.writeToParcel(mVisualPrompt, dest, flags);
        }

        public static final Parcelable.Creator<Prompt> CREATOR =
                new Parcelable.Creator<Prompt>() {
                    @Override
                    public Prompt createFromParcel(Parcel in) {
                        return new Prompt(in);
                    }

                    @Override
                    public Prompt[] newArray(int size) {
                        return new Prompt[size];
                    }
                };
    }
}
