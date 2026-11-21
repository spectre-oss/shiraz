// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.text.method.KeyListener;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;

/** Legacy dual-input dialer filter layout supporting letters, digits, or combined mode. */
@Deprecated
public class DialerFilter extends RelativeLayout {
    public static final int DIGITS_AND_LETTERS = 1;
    public static final int DIGITS_AND_LETTERS_NO_DIGITS = 2;
    public static final int DIGITS_AND_LETTERS_NO_LETTERS = 3;
    public static final int DIGITS_ONLY = 4;
    public static final int LETTERS_ONLY = 5;

    private EditText mLetters;
    private EditText mDigits;
    private ImageView mIcon;
    private int mMode;
    private boolean mIsQwerty;

    public DialerFilter(Context context) {
        super(context);
        initViews(context);
    }

    public DialerFilter(Context context, AttributeSet attrs) {
        super(context, attrs);
        initViews(context);
    }

    private void initViews(Context context) {
        mLetters = new EditText(context);
        mDigits = new EditText(context);
        setMode(DIGITS_AND_LETTERS);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIcon = findViewById(0x01020006);
        EditText hint = findViewById(0x01020005);
        if (hint != null) {
            mLetters = hint;
        }
        EditText primary = findViewById(0x0102000c);
        if (primary != null) {
            mDigits = primary;
        }
        if (mLetters != null) {
            mLetters.setKeyListener(TextKeyListener.getInstance());
            mLetters.setMovementMethod(null);
            mLetters.setFocusable(false);
        }
        if (mDigits != null) {
            mDigits.setFilters(new InputFilter[] { new InputFilter.AllCaps() });
            mDigits.setKeyListener(DialerKeyListener.getInstance());
            mDigits.setMovementMethod(null);
            mDigits.setFocusable(false);
        }
        mIsQwerty = true;
        setMode(DIGITS_AND_LETTERS);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (mIcon != null) {
            mIcon.setVisibility(focused ? VISIBLE : GONE);
        }
    }

    public boolean isQwertyKeyboard() {
        return mIsQwerty;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                break;
            case KeyEvent.KEYCODE_DEL:
                switch (mMode) {
                    case DIGITS_AND_LETTERS:
                        handled = mDigits.onKeyDown(keyCode, event);
                        handled &= mLetters.onKeyDown(keyCode, event);
                        break;
                    case DIGITS_AND_LETTERS_NO_DIGITS:
                        handled = mLetters.onKeyDown(keyCode, event);
                        if (mLetters.getText().length() == mDigits.getText().length()) {
                            setMode(DIGITS_AND_LETTERS);
                        }
                        break;
                    case DIGITS_AND_LETTERS_NO_LETTERS:
                        if (mDigits.getText().length() == mLetters.getText().length()) {
                            mLetters.onKeyDown(keyCode, event);
                            setMode(DIGITS_AND_LETTERS);
                        }
                        handled = mDigits.onKeyDown(keyCode, event);
                        break;
                    case DIGITS_ONLY:
                        handled = mDigits.onKeyDown(keyCode, event);
                        break;
                    case LETTERS_ONLY:
                        handled = mLetters.onKeyDown(keyCode, event);
                        break;
                }
                break;
            default:
                switch (mMode) {
                    case DIGITS_AND_LETTERS:
                        handled = mLetters.onKeyDown(keyCode, event);
                        if (KeyEvent.isModifierKey(keyCode)) {
                            mDigits.onKeyDown(keyCode, event);
                            handled = true;
                            break;
                        }
                        boolean isPrint = event.isPrintingKey()
                                || keyCode == KeyEvent.KEYCODE_SPACE
                                || keyCode == KeyEvent.KEYCODE_TAB;
                        if (isPrint) {
                            char c = event.getMatch(DialerKeyListener.CHARACTERS);
                            if (c != 0) {
                                handled &= mDigits.onKeyDown(keyCode, event);
                            } else {
                                setMode(DIGITS_AND_LETTERS_NO_DIGITS);
                            }
                        }
                        break;
                    case DIGITS_AND_LETTERS_NO_LETTERS:
                    case DIGITS_ONLY:
                        handled = mDigits.onKeyDown(keyCode, event);
                        break;
                    case DIGITS_AND_LETTERS_NO_DIGITS:
                    case LETTERS_ONLY:
                        handled = mLetters.onKeyDown(keyCode, event);
                        break;
                }
        }
        if (!handled) {
            return super.onKeyDown(keyCode, event);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean a = mLetters.onKeyUp(keyCode, event);
        boolean b = mDigits.onKeyUp(keyCode, event);
        return a || b;
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int newMode) {
        switch (newMode) {
            case DIGITS_AND_LETTERS:
                makeDigitsPrimary();
                mLetters.setVisibility(VISIBLE);
                mDigits.setVisibility(VISIBLE);
                break;
            case DIGITS_ONLY:
                makeDigitsPrimary();
                mLetters.setVisibility(GONE);
                mDigits.setVisibility(VISIBLE);
                break;
            case LETTERS_ONLY:
                makeLettersPrimary();
                mLetters.setVisibility(VISIBLE);
                mDigits.setVisibility(GONE);
                break;
            case DIGITS_AND_LETTERS_NO_LETTERS:
                makeDigitsPrimary();
                mLetters.setVisibility(INVISIBLE);
                mDigits.setVisibility(VISIBLE);
                break;
            case DIGITS_AND_LETTERS_NO_DIGITS:
                makeLettersPrimary();
                mLetters.setVisibility(VISIBLE);
                mDigits.setVisibility(INVISIBLE);
                break;
        }
        int oldMode = mMode;
        mMode = newMode;
        onModeChange(oldMode, newMode);
    }

    private void makeLettersPrimary() {
    }

    private void makeDigitsPrimary() {
    }

    public CharSequence getLetters() {
        return mLetters.getVisibility() == VISIBLE ? mLetters.getText() : "";
    }

    public CharSequence getDigits() {
        return mDigits.getVisibility() == VISIBLE ? mDigits.getText() : "";
    }

    public CharSequence getFilterText() {
        if (mMode != DIGITS_ONLY) {
            return getLetters();
        } else {
            return getDigits();
        }
    }

    public void append(String text) {
        switch (mMode) {
            case DIGITS_AND_LETTERS:
                mDigits.getText().append(text);
                mLetters.getText().append(text);
                break;
            case DIGITS_AND_LETTERS_NO_LETTERS:
            case DIGITS_ONLY:
                mDigits.getText().append(text);
                break;
            case DIGITS_AND_LETTERS_NO_DIGITS:
            case LETTERS_ONLY:
                mLetters.getText().append(text);
                break;
        }
    }

    public void clearText() {
        Editable text = mLetters.getText();
        text.clear();
        text = mDigits.getText();
        text.clear();
        if (mIsQwerty) {
            setMode(DIGITS_AND_LETTERS);
        } else {
            setMode(DIGITS_ONLY);
        }
    }

    public void setLettersWatcher(TextWatcher watcher) {
        CharSequence text = mLetters.getText();
        Spannable span = (Spannable) text;
        span.setSpan(watcher, 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }

    public void setDigitsWatcher(TextWatcher watcher) {
        CharSequence text = mDigits.getText();
        Spannable span = (Spannable) text;
        span.setSpan(watcher, 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }

    public void setFilterWatcher(TextWatcher watcher) {
        if (mMode != DIGITS_ONLY) {
            setLettersWatcher(watcher);
        } else {
            setDigitsWatcher(watcher);
        }
    }

    public void removeFilterWatcher(TextWatcher watcher) {
        Spannable text;
        if (mMode != DIGITS_ONLY) {
            text = mLetters.getText();
        } else {
            text = mDigits.getText();
        }
        text.removeSpan(watcher);
    }

    protected void onModeChange(int oldMode, int newMode) {
    }
}
