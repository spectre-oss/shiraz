// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextAttribute;
import java.util.Objects;

public class InputMethod {
    private final AccessibilityService mService;
    private boolean mInputStarted;
    private EditorInfo mInputEditorInfo;
    private AccessibilityInputConnection mInputConnection;

    public InputMethod(AccessibilityService service) {
        mService = Objects.requireNonNull(service, "service");
    }

    public final AccessibilityInputConnection getCurrentInputConnection() {
        return mInputStarted ? mInputConnection : null;
    }

    public final boolean getCurrentInputStarted() {
        return mInputStarted;
    }

    public final EditorInfo getCurrentInputEditorInfo() {
        return mInputStarted ? mInputEditorInfo : null;
    }

    public void onStartInput(EditorInfo attribute, boolean restarting) {
    }

    public void onFinishInput() {
    }

    public void onUpdateSelection(
            int oldSelStart,
            int oldSelEnd,
            int newSelStart,
            int newSelEnd,
            int candidatesStart,
            int candidatesEnd) {
    }

    void startInputInternal(EditorInfo editorInfo, boolean restarting) {
        mInputEditorInfo = editorInfo;
        mInputStarted = true;
        if (mInputConnection == null) {
            mInputConnection = new AccessibilityInputConnection(this);
        }
        onStartInput(editorInfo, restarting);
    }

    void finishInputInternal() {
        if (mInputStarted) {
            mInputStarted = false;
            mInputEditorInfo = null;
            onFinishInput();
        }
    }

    public final class AccessibilityInputConnection {
        private final StringBuilder mBuffer = new StringBuilder();
        private int mSelStart;
        private int mSelEnd;
        private int mLastEditorAction;
        private int mLastContextMenuAction;

        AccessibilityInputConnection(InputMethod parent) {
        }

        public void commitText(
                CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
            if (text == null) {
                return;
            }
            int a = Math.max(0, Math.min(mSelStart, mSelEnd));
            int b = Math.min(mBuffer.length(), Math.max(mSelStart, mSelEnd));
            String str = text.toString();
            mBuffer.replace(a, b, str);
            int nextCursor;
            if (newCursorPosition > 0) {
                nextCursor = a + str.length() + newCursorPosition - 1;
            } else {
                nextCursor = a + newCursorPosition;
            }
            nextCursor = Math.max(0, Math.min(mBuffer.length(), nextCursor));
            mSelStart = nextCursor;
            mSelEnd = nextCursor;
        }

        public void setSelection(int start, int end) {
            int len = mBuffer.length();
            mSelStart = Math.max(0, Math.min(len, start));
            mSelEnd = Math.max(0, Math.min(len, end));
        }

        public SurroundingText getSurroundingText(
                int beforeLength, int afterLength, int flags) {
            if (beforeLength < 0 || afterLength < 0) {
                return null;
            }
            int len = mBuffer.length();
            int selMin = Math.max(0, Math.min(mSelStart, mSelEnd));
            int selMax = Math.min(len, Math.max(mSelStart, mSelEnd));
            int start = Math.max(0, selMin - beforeLength);
            int end = Math.min(len, selMax + afterLength);
            String sub = mBuffer.substring(start, end);
            return new SurroundingText(
                    sub, mSelStart - start, mSelEnd - start, start);
        }

        public void deleteSurroundingText(int beforeLength, int afterLength) {
            if (beforeLength < 0 || afterLength < 0) {
                return;
            }
            int selMin = Math.max(0, Math.min(mSelStart, mSelEnd));
            int selMax = Math.min(mBuffer.length(), Math.max(mSelStart, mSelEnd));
            int afterEnd = Math.min(mBuffer.length(), selMax + afterLength);
            if (afterEnd > selMax) {
                mBuffer.delete(selMax, afterEnd);
            }
            int beforeStart = Math.max(0, selMin - beforeLength);
            if (selMin > beforeStart) {
                int removed = selMin - beforeStart;
                mBuffer.delete(beforeStart, selMin);
                mSelStart = Math.max(0, mSelStart - removed);
                mSelEnd = Math.max(0, mSelEnd - removed);
            }
        }

        public void sendKeyEvent(KeyEvent event) {
            if (event == null) {
                return;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int ch = event.getUnicodeChar();
                if (ch != 0) {
                    commitText(String.valueOf((char) ch), 1, null);
                }
            }
        }

        public void performEditorAction(int editorAction) {
            mLastEditorAction = editorAction;
        }

        public void performContextMenuAction(int id) {
            mLastContextMenuAction = id;
        }

        public int getCursorCapsMode(int reqModes) {
            int offset = Math.max(0, Math.min(mBuffer.length(), Math.min(mSelStart, mSelEnd)));
            return TextUtils.getCapsMode(mBuffer, offset, reqModes);
        }

        public void clearMetaKeyStates(int states) {
        }
    }
}
