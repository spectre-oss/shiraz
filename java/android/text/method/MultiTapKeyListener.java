// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;

/** 12-key multi-tap phone keypad text entry listener. */
public class MultiTapKeyListener extends BaseKeyListener implements SpanWatcher {
    private static final SparseArray<String> sRecs = new SparseArray<String>();
    private static final MultiTapKeyListener[] sInstance =
            new MultiTapKeyListener[TextKeyListener.Capitalize.values().length * 2];

    static {
        sRecs.put(KeyEvent.KEYCODE_1, ".,1!@#$%^&*:/?'=()");
        sRecs.put(KeyEvent.KEYCODE_2, "abc2ABC");
        sRecs.put(KeyEvent.KEYCODE_3, "def3DEF");
        sRecs.put(KeyEvent.KEYCODE_4, "ghi4GHI");
        sRecs.put(KeyEvent.KEYCODE_5, "jkl5JKL");
        sRecs.put(KeyEvent.KEYCODE_6, "mno6MNO");
        sRecs.put(KeyEvent.KEYCODE_7, "pqrs7PQRS");
        sRecs.put(KeyEvent.KEYCODE_8, "tuv8TUV");
        sRecs.put(KeyEvent.KEYCODE_9, "wxyz9WXYZ");
        sRecs.put(KeyEvent.KEYCODE_0, "0+");
        sRecs.put(KeyEvent.KEYCODE_POUND, " ");
    }

    private final TextKeyListener.Capitalize mCapitalize;
    private final boolean mAutoText;

    public MultiTapKeyListener(TextKeyListener.Capitalize cap, boolean autotext) {
        mCapitalize = cap != null ? cap : TextKeyListener.Capitalize.NONE;
        mAutoText = autotext;
    }

    public static MultiTapKeyListener getInstance(
            boolean autotext, TextKeyListener.Capitalize cap) {
        int off = cap.ordinal() * 2 + (autotext ? 1 : 0);
        if (sInstance[off] == null) {
            sInstance[off] = new MultiTapKeyListener(cap, autotext);
        }
        return sInstance[off];
    }

    @Override
    public int getInputType() {
        int contentType = InputType.TYPE_CLASS_TEXT;
        switch (mCapitalize) {
            case CHARACTERS:
                contentType |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
                break;
            case WORDS:
                contentType |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
                break;
            case SENTENCES:
                contentType |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                break;
            default:
                break;
        }
        if (mAutoText) {
            contentType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        }
        return contentType;
    }

    @Override
    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        int selStart = Selection.getSelectionStart(content);
        int selEnd = Selection.getSelectionEnd(content);
        int min = Math.min(selStart, selEnd);
        int max = Math.max(selStart, selEnd);
        if (min < 0 || max < 0) {
            min = max = 0;
            Selection.setSelection(content, 0, 0);
        }

        int activeStart = content.getSpanStart(ACTIVE);
        int activeEnd = content.getSpanEnd(ACTIVE);
        int rec = (content.getSpanFlags(ACTIVE)
                & Spanned.SPAN_USER) >>> Spanned.SPAN_USER_SHIFT;

        if (activeStart == min && activeEnd == max && max - min == 1
                && rec >= 0 && rec < sRecs.size()) {
            if (keyCode == KeyEvent.KEYCODE_STAR) {
                char current = content.charAt(min);
                if (Character.isLowerCase(current)) {
                    content.replace(min, max, String.valueOf(Character.toUpperCase(current)));
                    removeTimeouts(content);
                    new Timeout(content);
                    return true;
                }
                if (Character.isUpperCase(current)) {
                    content.replace(min, max, String.valueOf(Character.toLowerCase(current)));
                    removeTimeouts(content);
                    new Timeout(content);
                    return true;
                }
            }
            if (sRecs.indexOfKey(keyCode) == rec) {
                String val = sRecs.valueAt(rec);
                char ch = content.charAt(min);
                int ix = val.indexOf(ch);
                if (ix >= 0) {
                    ix = (ix + 1) % val.length();
                    content.replace(min, max, val, ix, ix + 1);
                    removeTimeouts(content);
                    new Timeout(content);
                    return true;
                }
            }
        }

        int newRec = sRecs.indexOfKey(keyCode);
        if (newRec >= 0) {
            Selection.setSelection(content, max, max);
            min = max;
            String val = sRecs.valueAt(newRec);
            int off = 0;
            if (TextKeyListener.shouldCap(mCapitalize, content, min)) {
                for (int i = 0; i < val.length(); i++) {
                    if (Character.isUpperCase(val.charAt(i))) {
                        off = i;
                        break;
                    }
                }
            }
            if (min != max) {
                Selection.setSelection(content, max);
            }
            content.setSpan(OLD_SEL_START, min, min, Spanned.SPAN_MARK_MARK);
            content.replace(min, max, val, off, off + 1);

            int oldStart = content.getSpanStart(OLD_SEL_START);
            int curEnd = Selection.getSelectionEnd(content);
            if (curEnd != oldStart) {
                Selection.setSelection(content, oldStart, curEnd);
                content.setSpan(LAST_TYPED,
                        oldStart, curEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                content.setSpan(ACTIVE,
                        oldStart, curEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                | (newRec << Spanned.SPAN_USER_SHIFT));
            }
            removeTimeouts(content);
            new Timeout(content);

            if (content.getSpanStart(this) < 0) {
                KeyListener[] methods = content.getSpans(0, content.length(), KeyListener.class);
                for (int i = 0; i < methods.length; i++) {
                    content.removeSpan(methods[i]);
                }
                content.setSpan(this, 0, content.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }
            return true;
        }

        return super.onKeyDown(view, content, keyCode, event);
    }

    @Override
    public void onSpanChanged(
            Spannable buf, Object what, int s, int e, int start, int stop) {
        if (what == Selection.SELECTION_END) {
            buf.removeSpan(ACTIVE);
            removeTimeouts(buf);
        }
    }

    private static void removeTimeouts(Spannable buf) {
        Timeout[] timeout = buf.getSpans(0, buf.length(), Timeout.class);
        for (int i = 0; i < timeout.length; i++) {
            Timeout t = timeout[i];
            if (t.mHandler != null) {
                t.mHandler.removeCallbacks(t);
            }
            t.mBuffer = null;
            buf.removeSpan(t);
        }
    }

    private static final class Timeout implements Runnable {
        Editable mBuffer;
        final Handler mHandler;

        Timeout(Editable buffer) {
            mBuffer = buffer;
            mBuffer.setSpan(this, 0, mBuffer.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            Looper looper = Looper.myLooper();
            if (looper == null) {
                looper = Looper.getMainLooper();
            }
            if (looper != null) {
                mHandler = new Handler(looper);
                mHandler.postAtTime(this, SystemClock.uptimeMillis() + 2000);
            } else {
                mHandler = null;
            }
        }

        @Override
        public void run() {
            Spannable buf = mBuffer;
            if (buf != null) {
                int st = Selection.getSelectionStart(buf);
                int en = Selection.getSelectionEnd(buf);
                int start = buf.getSpanStart(ACTIVE);
                int end = buf.getSpanEnd(ACTIVE);
                if (st == start && en == end) {
                    Selection.setSelection(buf, Selection.getSelectionEnd(buf));
                }
                buf.removeSpan(this);
            }
        }
    }

    @Override
    public void onSpanAdded(Spannable s, Object what, int start, int end) {
    }

    @Override
    public void onSpanRemoved(Spannable s, Object what, int start, int end) {
    }

    private static final Object OLD_SEL_START = new android.text.NoCopySpan.Concrete();
    private static final Object ACTIVE = new android.text.NoCopySpan.Concrete();
    private static final Object LAST_TYPED = new android.text.NoCopySpan.Concrete();
}
