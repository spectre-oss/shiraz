// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.AttributeSet;

/** Editable text view supporting completion across multiple delimited tokens. */
public class MultiAutoCompleteTextView extends AutoCompleteTextView {
    public interface Tokenizer {
        int findTokenStart(CharSequence text, int cursor);
        int findTokenEnd(CharSequence text, int cursor);
        CharSequence terminateToken(CharSequence text);
    }

    public static class CommaTokenizer implements Tokenizer {
        public CommaTokenizer() {
        }

        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0 && text.charAt(i - 1) != ',') {
                i--;
            }
            while (i < cursor && text.charAt(i) == ' ') {
                i++;
            }
            return i;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            while (i < len) {
                if (text.charAt(i) == ',') {
                    return i;
                }
                i++;
            }
            return len;
        }

        @Override
        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();
            while (i > 0 && text.charAt(i - 1) == ' ') {
                i--;
            }
            if (i > 0 && text.charAt(i - 1) == ',') {
                return text;
            }
            if (text instanceof Spanned) {
                SpannableString sp = new SpannableString(text + ", ");
                TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, sp, 0);
                return sp;
            }
            return text + ", ";
        }
    }

    private Tokenizer mTokenizer;

    public MultiAutoCompleteTextView(Context context) {
        this(context, null);
    }

    public MultiAutoCompleteTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101006b);
    }

    public MultiAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MultiAutoCompleteTextView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setTokenizer(Tokenizer t) {
        mTokenizer = t;
    }

    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        if (enoughToFilter()) {
            int end = getSelectionEnd();
            if (end < 0) {
                end = text.length();
            }
            int start = mTokenizer.findTokenStart(text, end);
            performFiltering(text, start, end, keyCode);
        } else {
            dismissDropDown();
            Filter f = getFilter();
            if (f != null) {
                f.filter(null);
            }
        }
    }

    @Override
    public boolean enoughToFilter() {
        Editable text = getText();
        int end = getSelectionEnd();
        if (end < 0 || mTokenizer == null) {
            return false;
        }
        int start = mTokenizer.findTokenStart(text, end);
        return end - start >= getThreshold();
    }

    @Override
    public void performValidation() {
        Validator v = getValidator();
        if (v == null || mTokenizer == null) {
            return;
        }
        Editable e = getText();
        int i = getText().length();
        while (i > 0) {
            int start = mTokenizer.findTokenStart(e, i);
            int end = mTokenizer.findTokenEnd(e, start);
            CharSequence sub = e.subSequence(start, end);
            if (TextUtils.isEmpty(sub)) {
                e.replace(start, i, "");
            } else if (!v.isValid(sub)) {
                e.replace(start, i, mTokenizer.terminateToken(v.fixText(sub)));
            }
            i = start;
        }
    }

    protected void performFiltering(CharSequence text, int start, int end, int keyCode) {
        Filter f = getFilter();
        if (f != null) {
            f.filter(text.subSequence(start, end), this);
        }
    }

    @Override
    protected void replaceText(CharSequence text) {
        int end = getSelectionEnd();
        Editable editable = getText();
        if (end < 0) {
            end = editable.length();
        }
        int start = mTokenizer != null ? mTokenizer.findTokenStart(editable, end) : 0;
        CharSequence replacement = mTokenizer != null ? mTokenizer.terminateToken(text) : text;
        editable.replace(start, end, replacement);
        Selection.setSelection(editable, Math.min(editable.length(), start + replacement.length()));
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return MultiAutoCompleteTextView.class.getName();
    }
}
