// SPDX-License-Identifier: GPL-3.0-only
package android.service.voice;

import android.app.Dialog;
import android.app.DirectAction;
import android.app.VoiceInteractor;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** An active voice interaction session, providing a facility for the implementation to interact with the user. */
public class VoiceInteractionSession implements KeyEvent.Callback, ComponentCallbacks2 {
    public static final int SHOW_WITH_ASSIST = 1;
    public static final int SHOW_WITH_SCREENSHOT = 2;
    public static final int SHOW_SOURCE_ASSIST_GESTURE = 4;
    public static final int SHOW_SOURCE_APPLICATION = 8;
    public static final int SHOW_SOURCE_ACTIVITY = 16;
    public static final int SHOW_SOURCE_PUSH_TO_TALK = 32;
    public static final int SHOW_SOURCE_NOTIFICATION = 64;
    public static final int SHOW_SOURCE_AUTOMOTIVE_SYSTEM_UI = 128;

    public static final String KEY_FOREGROUND_ACTIVITIES = "android.service.voice.FOREGROUND_ACTIVITIES";
    public static final String KEY_SHOW_SESSION_ID = "android.service.voice.SHOW_SESSION_ID";

    private final Context mContext;
    private final Handler mHandler;
    private int mDisabledShowContext;
    private boolean mUiEnabled = true;
    private int mTheme;
    private boolean mKeepAwake;
    private Dialog mWindow;
    private View mContentView;
    private final LinkedHashMap<VisibleActivityCallback, Executor> mVisibleCallbacks =
            new LinkedHashMap<VisibleActivityCallback, Executor>();

    public VoiceInteractionSession(Context context) {
        this(context, new Handler(Looper.getMainLooper()));
    }

    public VoiceInteractionSession(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    public Context getContext() {
        return mContext;
    }

    public void setDisabledShowContext(int flags) {
        mDisabledShowContext = flags;
    }

    public int getDisabledShowContext() {
        return mDisabledShowContext;
    }

    public int getUserDisabledShowContext() {
        return mDisabledShowContext;
    }

    public void show(Bundle args, int flags) {
        onPrepareShow(args, flags);
        onShow(args, flags);
    }

    public void hide() {
        onHide();
    }

    public void setUiEnabled(boolean enabled) {
        mUiEnabled = enabled;
    }

    public void setTheme(int theme) {
        mTheme = theme;
    }

    public void startVoiceActivity(Intent intent) {
        if (mContext != null && intent != null) {
            mContext.startActivity(intent);
        }
    }

    public void startAssistantActivity(Intent intent) {
        startAssistantActivity(intent, null);
    }

    public void startAssistantActivity(Intent intent, Bundle bundle) {
        if (mContext != null && intent != null) {
            mContext.startActivity(intent, bundle);
        }
    }

    public final void requestDirectActions(
            ActivityId activityId,
            CancellationSignal cancellationSignal,
            Executor resultExecutor,
            final Consumer<List<DirectAction>> callback) {
        if (activityId == null) throw new NullPointerException("activityId");
        if (resultExecutor == null) throw new NullPointerException("resultExecutor");
        if (callback == null) throw new NullPointerException("callback");
        if (cancellationSignal != null && cancellationSignal.isCanceled()) return;
        resultExecutor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(Collections.<DirectAction>emptyList());
            }
        });
    }

    public void onDirectActionsInvalidated(ActivityId activityId) {
    }

    public final void performDirectAction(
            DirectAction action,
            Bundle extras,
            CancellationSignal cancellationSignal,
            Executor resultExecutor,
            final Consumer<Bundle> resultListener) {
        if (action == null) throw new NullPointerException("action");
        if (resultExecutor == null) throw new NullPointerException("resultExecutor");
        if (resultListener == null) throw new NullPointerException("resultListener");
        if (cancellationSignal != null && cancellationSignal.isCanceled()) return;
        resultExecutor.execute(new Runnable() {
            @Override
            public void run() {
                resultListener.accept(Bundle.EMPTY);
            }
        });
    }

    public void setKeepAwake(boolean keepAwake) {
        mKeepAwake = keepAwake;
    }

    public void closeSystemDialogs() {
    }

    public LayoutInflater getLayoutInflater() {
        return mContext != null ? LayoutInflater.from(mContext) : null;
    }

    public Dialog getWindow() {
        if (mWindow == null && mContext != null) {
            mWindow = mTheme != 0 ? new Dialog(mContext, mTheme) : new Dialog(mContext);
        }
        return mWindow;
    }

    public void finish() {
        hide();
        onDestroy();
    }

    public void onCreate() {
    }

    public void onPrepareShow(Bundle args, int showFlags) {
    }

    public void onShow(Bundle args, int showFlags) {
    }

    public void onHide() {
    }

    public void onDestroy() {
    }

    public View onCreateContentView() {
        return null;
    }

    public void setContentView(View view) {
        mContentView = view;
        Dialog win = getWindow();
        if (win != null && view != null) {
            win.setContentView(view);
        }
    }

    public void onAssistStructureFailure(Throwable failure) {
    }

    public void onHandleAssist(Bundle data, AssistStructure structure, AssistContent content) {
    }

    public void onHandleAssist(AssistState state) {
        if (state == null) return;
        if (state.getIndex() == 0) {
            onHandleAssist(state.getAssistData(), state.getAssistStructure(), state.getAssistContent());
        } else {
            onHandleAssistSecondary(
                    state.getAssistData(),
                    state.getAssistStructure(),
                    state.getAssistContent(),
                    state.getIndex(),
                    state.getCount());
        }
    }

    public void onHandleAssistSecondary(
            Bundle data, AssistStructure structure, AssistContent content, int index, int count) {
    }

    public void onHandleScreenshot(Bitmap screenshot) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return false;
    }

    public void onBackPressed() {
        hide();
    }

    public void onCloseSystemDialogs() {
        hide();
    }

    public void onLockscreenShown() {
        hide();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void onLowMemory() {
    }

    @Override
    public void onTrimMemory(int level) {
    }

    public void onComputeInsets(Insets outInsets) {
    }

    public void onTaskStarted(Intent intent, int taskId) {
    }

    public void onTaskFinished(Intent intent, int taskId) {
        hide();
    }

    public boolean[] onGetSupportedCommands(String[] commands) {
        return commands != null ? new boolean[commands.length] : new boolean[0];
    }

    public void onRequestConfirmation(ConfirmationRequest request) {
    }

    public void onRequestPickOption(PickOptionRequest request) {
    }

    public void onRequestCompleteVoice(CompleteVoiceRequest request) {
    }

    public void onRequestAbortVoice(AbortVoiceRequest request) {
    }

    public void onRequestCommand(CommandRequest request) {
    }

    public void onCancelRequest(Request request) {
    }

    public final void registerVisibleActivityCallback(
            Executor executor, VisibleActivityCallback callback) {
        if (executor == null) throw new NullPointerException("executor");
        if (callback == null) throw new NullPointerException("callback");
        synchronized (mVisibleCallbacks) {
            mVisibleCallbacks.put(callback, executor);
        }
    }

    public final void unregisterVisibleActivityCallback(VisibleActivityCallback callback) {
        if (callback == null) throw new NullPointerException("callback");
        synchronized (mVisibleCallbacks) {
            mVisibleCallbacks.remove(callback);
        }
    }

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (writer != null) {
            writer.println(prefix + "mUiEnabled=" + mUiEnabled + " mKeepAwake=" + mKeepAwake
                    + " mContentView=" + mContentView + " mHandler=" + mHandler);
        }
    }

    public static class Request {
        private final String mCallingPackage;
        private final int mCallingUid;
        private final Bundle mExtras;
        private boolean mActive = true;

        Request(String callingPackage, int callingUid, Bundle extras) {
            mCallingPackage = callingPackage;
            mCallingUid = callingUid;
            mExtras = extras;
        }

        public int getCallingUid() {
            return mCallingUid;
        }

        public String getCallingPackage() {
            return mCallingPackage;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        public boolean isActive() {
            return mActive;
        }

        public void cancel() {
            mActive = false;
        }

        void finishRequest() {
            mActive = false;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{pkg=" + mCallingPackage + ", active=" + mActive + "}";
        }
    }

    public static final class ConfirmationRequest extends Request {
        private final VoiceInteractor.Prompt mPrompt;

        ConfirmationRequest(String pkg, int uid, VoiceInteractor.Prompt prompt, Bundle extras) {
            super(pkg, uid, extras);
            mPrompt = prompt;
        }

        public VoiceInteractor.Prompt getVoicePrompt() {
            return mPrompt;
        }

        public CharSequence getPrompt() {
            return mPrompt != null ? mPrompt.getVoicePromptAt(0) : null;
        }

        public void sendConfirmationResult(boolean confirmed, Bundle result) {
            finishRequest();
        }
    }

    public static final class PickOptionRequest extends Request {
        private final VoiceInteractor.Prompt mPrompt;
        private final VoiceInteractor.PickOptionRequest.Option[] mOptions;

        PickOptionRequest(
                String pkg,
                int uid,
                VoiceInteractor.Prompt prompt,
                VoiceInteractor.PickOptionRequest.Option[] options,
                Bundle extras) {
            super(pkg, uid, extras);
            mPrompt = prompt;
            mOptions = options;
        }

        public VoiceInteractor.Prompt getVoicePrompt() {
            return mPrompt;
        }

        public CharSequence getPrompt() {
            return mPrompt != null ? mPrompt.getVoicePromptAt(0) : null;
        }

        public VoiceInteractor.PickOptionRequest.Option[] getOptions() {
            return mOptions;
        }

        public void sendIntermediatePickOptionResult(
                VoiceInteractor.PickOptionRequest.Option[] selections, Bundle result) {
        }

        public void sendPickOptionResult(
                VoiceInteractor.PickOptionRequest.Option[] selections, Bundle result) {
            finishRequest();
        }
    }

    public static final class CompleteVoiceRequest extends Request {
        private final VoiceInteractor.Prompt mPrompt;

        CompleteVoiceRequest(String pkg, int uid, VoiceInteractor.Prompt prompt, Bundle extras) {
            super(pkg, uid, extras);
            mPrompt = prompt;
        }

        public VoiceInteractor.Prompt getVoicePrompt() {
            return mPrompt;
        }

        public CharSequence getMessage() {
            return mPrompt != null ? mPrompt.getVoicePromptAt(0) : null;
        }

        public void sendCompleteResult(Bundle result) {
            finishRequest();
        }
    }

    public static final class AbortVoiceRequest extends Request {
        private final VoiceInteractor.Prompt mPrompt;

        AbortVoiceRequest(String pkg, int uid, VoiceInteractor.Prompt prompt, Bundle extras) {
            super(pkg, uid, extras);
            mPrompt = prompt;
        }

        public VoiceInteractor.Prompt getVoicePrompt() {
            return mPrompt;
        }

        public CharSequence getMessage() {
            return mPrompt != null ? mPrompt.getVoicePromptAt(0) : null;
        }

        public void sendAbortResult(Bundle result) {
            finishRequest();
        }
    }

    public static final class CommandRequest extends Request {
        private final String mCommand;

        CommandRequest(String pkg, int uid, String command, Bundle extras) {
            super(pkg, uid, extras);
            mCommand = command;
        }

        public String getCommand() {
            return mCommand;
        }

        public void sendIntermediateResult(Bundle result) {
        }

        public void sendResult(Bundle result) {
            finishRequest();
        }
    }

    public static class ActivityId {
        private final int mTaskId;
        private final IBinder mAssistToken;

        ActivityId(int taskId, IBinder assistToken) {
            mTaskId = taskId;
            mAssistToken = assistToken;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ActivityId)) return false;
            ActivityId other = (ActivityId) o;
            return mTaskId == other.mTaskId && Objects.equals(mAssistToken, other.mAssistToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(mTaskId), mAssistToken);
        }
    }

    public static final class AssistState {
        private final ActivityId mActivityId;
        private final Bundle mData;
        private final AssistStructure mStructure;
        private final AssistContent mContent;
        private final int mIndex;
        private final int mCount;

        AssistState(
                ActivityId activityId,
                Bundle data,
                AssistStructure structure,
                AssistContent content,
                int index,
                int count) {
            mActivityId = activityId;
            mData = data;
            mStructure = structure;
            mContent = content;
            mIndex = index;
            mCount = count;
        }

        public boolean isFocused() {
            return mIndex == 0;
        }

        public int getIndex() {
            return mIndex;
        }

        public int getCount() {
            return mCount;
        }

        public ActivityId getActivityId() {
            return mActivityId;
        }

        public Bundle getAssistData() {
            return mData;
        }

        public AssistStructure getAssistStructure() {
            return mStructure;
        }

        public AssistContent getAssistContent() {
            return mContent;
        }
    }

    public static final class Insets {
        public static final int TOUCHABLE_INSETS_FRAME = 0;
        public static final int TOUCHABLE_INSETS_CONTENT = 1;
        public static final int TOUCHABLE_INSETS_REGION = 3;

        public final Rect contentInsets = new Rect();
        public int touchableInsets;
        public final Region touchableRegion = new Region();

        public Insets() {
        }
    }

    public interface VisibleActivityCallback {
        default void onVisible(VisibleActivityInfo activityInfo) {
        }

        default void onInvisible(ActivityId activityId) {
        }
    }
}
