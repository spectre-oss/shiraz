// SPDX-License-Identifier: GPL-3.0-only
package android.inputmethodservice;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodSubtype;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Objects;

public class InputMethodService extends AbstractInputMethodService {
    public static final int BACK_DISPOSITION_DEFAULT = 0;
    public static final int BACK_DISPOSITION_WILL_NOT_DISMISS = 1;
    public static final int BACK_DISPOSITION_WILL_DISMISS = 2;
    public static final int BACK_DISPOSITION_ADJUST_NOTHING = 3;

    private static final Duration MAX_STYLUS_HANDWRITING_TIMEOUT = Duration.ofSeconds(30);

    private int mTheme;
    private int mBackDisposition = BACK_DISPOSITION_DEFAULT;
    private IBinder mToken;
    private InputBinding mInputBinding;
    private InputConnection mInputConnection;
    private EditorInfo mInputEditorInfo;
    private boolean mInputStarted;
    private boolean mInputViewStarted;
    private boolean mCandidatesViewStarted;
    private boolean mWindowVisible;
    private boolean mShowInputRequested;
    private boolean mIsInputViewShown;
    private boolean mIsFullscreen;
    private boolean mExtractViewHidden;
    private boolean mCandidatesViewShown;
    private View mExtractView;
    private ExtractEditText mExtractEditText;
    private View mCandidatesView;
    private View mInputView;
    private Dialog mWindow;
    private Duration mStylusTimeout = Duration.ofSeconds(10);

    public class InputMethodImpl extends AbstractInputMethodImpl {
        public InputMethodImpl() {
        }

        @Override
        public void attachToken(IBinder token) {
            mToken = token;
        }

        @Override
        public void bindInput(InputBinding binding) {
            mInputBinding = binding;
            if (binding != null) {
                mInputConnection = binding.getConnection();
            }
            onBindInput();
        }

        @Override
        public void unbindInput() {
            onUnbindInput();
            mInputBinding = null;
            mInputConnection = null;
        }

        @Override
        public void startInput(InputConnection ic, EditorInfo attribute) {
            doStartInput(ic, attribute, false);
        }

        @Override
        public void restartInput(InputConnection ic, EditorInfo attribute) {
            doStartInput(ic, attribute, true);
        }

        @Override
        public void hideSoftInput(int flags, ResultReceiver resultReceiver) {
            boolean wasVis = isInputViewShown();
            mShowInputRequested = false;
            hideWindow();
            if (resultReceiver != null) {
                resultReceiver.send(wasVis != isInputViewShown() ? 3 : 1, null);
            }
        }

        @Override
        public void showSoftInput(int flags, ResultReceiver resultReceiver) {
            boolean wasVis = isInputViewShown();
            if (onShowInputRequested(flags, false)) {
                showWindow(true);
            }
            if (resultReceiver != null) {
                resultReceiver.send(wasVis != isInputViewShown() ? 2 : 0, null);
            }
        }

        @Override
        public void changeInputMethodSubtype(InputMethodSubtype subtype) {
            onCurrentInputMethodSubtypeChanged(subtype);
        }
    }

    public class InputMethodSessionImpl extends AbstractInputMethodSessionImpl {
        public InputMethodSessionImpl() {
        }

        @Override
        public void finishInput() {
            if (!isEnabled()) return;
            doFinishInput();
        }

        @Override
        public void displayCompletions(CompletionInfo[] completions) {
            if (!isEnabled()) return;
            onDisplayCompletions(completions);
        }

        @Override
        public void updateExtractedText(int token, ExtractedText text) {
            if (!isEnabled()) return;
            onUpdateExtractedText(token, text);
        }

        @Override
        public void updateSelection(int oldSelStart, int oldSelEnd,
                int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
            if (!isEnabled()) return;
            onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                    candidatesStart, candidatesEnd);
        }

        @Override
        public void viewClicked(boolean focusChanged) {
            if (!isEnabled()) return;
            onViewClicked(focusChanged);
        }

        @Override
        public void updateCursor(Rect newCursor) {
            if (!isEnabled()) return;
            onUpdateCursor(newCursor);
        }

        @Override
        public void appPrivateCommand(String action, Bundle data) {
            if (!isEnabled()) return;
            onAppPrivateCommand(action, data);
        }

        @Override
        public void toggleSoftInput(int showFlags, int hideFlags) {
            if (isInputViewShown()) {
                requestHideSelf(hideFlags);
            } else {
                requestShowSelf(showFlags);
            }
        }

        @Override
        public void updateCursorAnchorInfo(CursorAnchorInfo info) {
            if (!isEnabled()) return;
            onUpdateCursorAnchorInfo(info);
        }
    }

    public static final class Insets {
        public static final int TOUCHABLE_INSETS_FRAME = 0;
        public static final int TOUCHABLE_INSETS_CONTENT = 1;
        public static final int TOUCHABLE_INSETS_VISIBLE = 2;
        public static final int TOUCHABLE_INSETS_REGION = 3;

        public int contentTopInsets;
        public int visibleTopInsets;
        public final Region touchableRegion = new Region();
        public int touchableInsets;

        public Insets() {
        }
    }

    public InputMethodService() {
    }

    public InlineSuggestionsRequest onCreateInlineSuggestionsRequest(Bundle uiExtras) {
        return null;
    }

    public boolean onInlineSuggestionsResponse(InlineSuggestionsResponse response) {
        return false;
    }

    @Override
    public void setTheme(int theme) {
        mTheme = theme;
        if (getBaseContext() != null) {
            super.setTheme(theme);
        }
    }

    @Deprecated
    public boolean enableHardwareAcceleration() {
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        onInitializeInterface();
    }

    public void onInitializeInterface() {
    }

    @Override
    public void onDestroy() {
        doFinishInput();
        if (mWindow != null && mWindow.isShowing()) {
            mWindow.dismiss();
        }
        mWindow = null;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onInitializeInterface();
    }

    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
        return new InputMethodImpl();
    }

    @Override
    public AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface() {
        return new InputMethodSessionImpl();
    }

    public LayoutInflater getLayoutInflater() {
        return LayoutInflater.from(this);
    }

    public Dialog getWindow() {
        if (mWindow == null && getBaseContext() != null) {
            mWindow = new Dialog(this, mTheme);
        }
        return mWindow;
    }

    public void setBackDisposition(int disposition) {
        mBackDisposition = disposition;
    }

    public int getBackDisposition() {
        return mBackDisposition;
    }

    public int getMaxWidth() {
        if (getBaseContext() != null && getResources() != null
                && getResources().getDisplayMetrics() != null) {
            return getResources().getDisplayMetrics().widthPixels;
        }
        return 480;
    }

    public InputBinding getCurrentInputBinding() {
        return mInputBinding;
    }

    public InputConnection getCurrentInputConnection() {
        if (mInputConnection != null) {
            return mInputConnection;
        }
        return mInputBinding != null ? mInputBinding.getConnection() : null;
    }

    public final boolean switchToPreviousInputMethod() {
        return false;
    }

    public final boolean switchToNextInputMethod(boolean onlyCurrentIme) {
        return false;
    }

    public final boolean shouldOfferSwitchingToNextInputMethod() {
        return false;
    }

    public boolean getCurrentInputStarted() {
        return mInputStarted;
    }

    public EditorInfo getCurrentInputEditorInfo() {
        return mInputEditorInfo;
    }

    public void updateFullscreenMode() {
        mIsFullscreen = mShowInputRequested && onEvaluateFullscreenMode();
    }

    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
    }

    public boolean isFullscreenMode() {
        return mIsFullscreen;
    }

    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    public void setExtractViewShown(boolean shown) {
        mExtractViewHidden = !shown;
    }

    public boolean isExtractViewShown() {
        return mIsFullscreen && !mExtractViewHidden;
    }

    public void onComputeInsets(Insets outInsets) {
        if (outInsets != null) {
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE;
        }
    }

    public void updateInputViewShown() {
        mIsInputViewShown = mShowInputRequested && onEvaluateInputViewShown();
    }

    public boolean isShowInputRequested() {
        return mShowInputRequested;
    }

    public boolean isInputViewShown() {
        return mIsInputViewShown && mWindowVisible;
    }

    public boolean onEvaluateInputViewShown() {
        return true;
    }

    public void setCandidatesViewShown(boolean shown) {
        mCandidatesViewShown = shown;
        if (mCandidatesView != null) {
            mCandidatesView.setVisibility(shown ? View.VISIBLE : getCandidatesHiddenVisibility());
        }
    }

    public int getCandidatesHiddenVisibility() {
        return isExtractViewShown() ? View.GONE : View.INVISIBLE;
    }

    public void showStatusIcon(int iconResId) {
    }

    public void hideStatusIcon() {
    }

    public void switchInputMethod(String id) {
    }

    public final void switchInputMethod(String id, InputMethodSubtype subtype) {
    }

    public void setExtractView(View view) {
        mExtractView = view;
        if (view instanceof ExtractEditText) {
            mExtractEditText = (ExtractEditText) view;
            mExtractEditText.setIME(this);
        } else {
            mExtractEditText = null;
        }
    }

    public void setCandidatesView(View view) {
        mCandidatesView = view;
    }

    public void setInputView(View view) {
        mInputView = view;
    }

    public View onCreateExtractTextView() {
        if (getBaseContext() != null) {
            ExtractEditText eet = new ExtractEditText(this);
            eet.setIME(this);
            return eet;
        }
        return null;
    }

    public View onCreateCandidatesView() {
        return null;
    }

    public View onCreateInputView() {
        return null;
    }

    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
    }

    public void onFinishInputView(boolean finishingInput) {
    }

    public void onStartCandidatesView(EditorInfo editorInfo, boolean restarting) {
    }

    public void onFinishCandidatesView(boolean finishingInput) {
    }

    public void onPrepareStylusHandwriting() {
    }

    public boolean onStartStylusHandwriting() {
        return false;
    }

    public boolean onStartConnectionlessStylusHandwriting(
            int inputType, CursorAnchorInfo cursorAnchorInfo) {
        return false;
    }

    public void onStylusHandwritingMotionEvent(MotionEvent motionEvent) {
    }

    public void onFinishStylusHandwriting() {
    }

    public final Window getStylusHandwritingWindow() {
        return null;
    }

    public final void finishStylusHandwriting() {
        onFinishStylusHandwriting();
    }

    public final void finishConnectionlessStylusHandwriting(CharSequence text) {
        onFinishStylusHandwriting();
    }

    public final void setStylusHandwritingSessionTimeout(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        mStylusTimeout = duration.compareTo(MAX_STYLUS_HANDWRITING_TIMEOUT) > 0
                ? MAX_STYLUS_HANDWRITING_TIMEOUT : duration;
    }

    public static final Duration getStylusHandwritingIdleTimeoutMax() {
        return MAX_STYLUS_HANDWRITING_TIMEOUT;
    }

    public final Duration getStylusHandwritingSessionTimeout() {
        return mStylusTimeout;
    }

    public boolean onShowInputRequested(int flags, boolean configChange) {
        if (!onEvaluateInputViewShown()) {
            return false;
        }
        mShowInputRequested = true;
        return true;
    }

    public void showWindow(boolean showInput) {
        boolean wasVisible = mWindowVisible;
        mWindowVisible = true;
        if (showInput) {
            mShowInputRequested = true;
            updateInputViewShown();
            updateFullscreenMode();
            if (mInputView == null) {
                View created = onCreateInputView();
                if (created != null) {
                    setInputView(created);
                }
            }
            if (!mInputViewStarted) {
                mInputViewStarted = true;
                onStartInputView(mInputEditorInfo, false);
            }
        }
        if (!wasVisible) {
            onWindowShown();
        }
    }

    public void hideWindow() {
        if (mInputViewStarted) {
            onFinishInputView(false);
            mInputViewStarted = false;
        }
        if (mCandidatesViewStarted) {
            onFinishCandidatesView(false);
            mCandidatesViewStarted = false;
        }
        if (mWindowVisible) {
            mWindowVisible = false;
            mIsInputViewShown = false;
            onWindowHidden();
        }
    }

    public void onWindowShown() {
    }

    public void onWindowHidden() {
    }

    public void onBindInput() {
    }

    public void onUnbindInput() {
    }

    public void onStartInput(EditorInfo attribute, boolean restarting) {
    }

    public void onFinishInput() {
    }

    public void onDisplayCompletions(CompletionInfo[] completions) {
    }

    public void onUpdateExtractedText(int token, ExtractedText text) {
        if (mExtractEditText != null && text != null) {
            mExtractEditText.setExtractedText(text);
        }
    }

    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        if (mExtractEditText != null && isExtractViewShown()) {
            mExtractEditText.startInternalChanges();
            try {
                mExtractEditText.setSelection(newSelStart, newSelEnd);
            } finally {
                mExtractEditText.finishInternalChanges();
            }
        }
    }

    @Deprecated
    public void onViewClicked(boolean focusChanged) {
    }

    public void onUpdateEditorToolType(int toolType) {
    }

    @Deprecated
    public void onUpdateCursor(Rect newCursor) {
    }

    public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
    }

    public void requestHideSelf(int flags) {
        mShowInputRequested = false;
        hideWindow();
    }

    public final void requestShowSelf(int flags) {
        if (onShowInputRequested(flags, false)) {
            showWindow(true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown()) {
            hideWindow();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return false;
    }

    public void onAppPrivateCommand(String action, Bundle data) {
    }

    public void sendDownUpKeyEvents(int keyEventCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long eventTime = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        ic.sendKeyEvent(new KeyEvent(eventTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    public boolean sendDefaultEditorAction(boolean fromEnterKey) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null
                && (!fromEnterKey || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
                && (ei.imeOptions & EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_NONE) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.performEditorAction(ei.imeOptions & EditorInfo.IME_MASK_ACTION);
            }
            return true;
        }
        return false;
    }

    public void sendKeyChar(char charCode) {
        if (charCode == '\n') {
            if (!sendDefaultEditorAction(true)) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            }
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(String.valueOf(charCode), 1);
        }
    }

    public void onExtractedSelectionChanged(int start, int end) {
        InputConnection conn = getCurrentInputConnection();
        if (conn != null) {
            conn.setSelection(start, end);
        }
    }

    public void onExtractedTextClicked() {
    }

    public void onExtractedCursorMovement(int dx, int dy) {
    }

    public boolean onExtractTextContextMenuItem(int id) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        if (id == 0x0102001f) { // android.R.id.selectAll
            ExtractedText et = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
            if (et != null && et.text != null) {
                return ic.setSelection(0, et.text.length());
            }
        }
        return false;
    }

    public CharSequence getTextForImeAction(int imeOptions) {
        switch (imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_NONE:
                return null;
            case EditorInfo.IME_ACTION_GO:
                return "Go";
            case EditorInfo.IME_ACTION_SEARCH:
                return "Search";
            case EditorInfo.IME_ACTION_SEND:
                return "Send";
            case EditorInfo.IME_ACTION_NEXT:
                return "Next";
            case EditorInfo.IME_ACTION_DONE:
                return "Done";
            case EditorInfo.IME_ACTION_PREVIOUS:
                return "Prev";
            default:
                return "Action";
        }
    }

    public void onUpdateExtractingVisibility(EditorInfo ei) {
        setExtractViewShown(ei == null
                || (ei.imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) == 0);
    }

    public void onUpdateExtractingViews(EditorInfo ei) {
    }

    public void onExtractingInputChanged(EditorInfo ei) {
    }

    protected void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) {
    }

    public int getInputMethodWindowRecommendedHeight() {
        return 0;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
        super.dump(fd, fout, args);
    }

    @Override
    public Object getSystemService(String name) {
        if (Context.LAYOUT_INFLATER_SERVICE.equals(name) && getBaseContext() != null) {
            return LayoutInflater.from(this);
        }
        return super.getSystemService(name);
    }

    private void doFinishInput() {
        if (mInputViewStarted) {
            onFinishInputView(true);
            mInputViewStarted = false;
        }
        if (mCandidatesViewStarted) {
            onFinishCandidatesView(true);
            mCandidatesViewStarted = false;
        }
        if (mInputStarted) {
            onFinishInput();
            mInputStarted = false;
        }
        mInputEditorInfo = null;
    }

    private void doStartInput(InputConnection ic, EditorInfo attribute, boolean restarting) {
        if (!restarting) {
            doFinishInput();
        }
        mInputStarted = true;
        mInputConnection = ic;
        mInputEditorInfo = attribute;
        onStartInput(attribute, restarting);
        if (mWindowVisible && mIsInputViewShown) {
            mInputViewStarted = true;
            onStartInputView(mInputEditorInfo, restarting);
        }
    }
}
