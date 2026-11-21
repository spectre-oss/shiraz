// SPDX-License-Identifier: GPL-3.0-only
package android.inputmethodservice;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.ExtractedText;
import android.widget.EditText;

public class ExtractEditText extends EditText {
    private InputMethodService mIME;
    private int mSettingExtractedText;

    public ExtractEditText(Context context) {
        super(context, null);
    }

    public ExtractEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtractEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ExtractEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    void setIME(InputMethodService ime) {
        mIME = ime;
    }

    public void startInternalChanges() {
        mSettingExtractedText += 1;
    }

    public void finishInternalChanges() {
        mSettingExtractedText = Math.max(0, mSettingExtractedText - 1);
    }

    public void setExtractedText(ExtractedText text) {
        startInternalChanges();
        try {
            if (text != null) {
                setText(text.text);
                int len = getText() != null ? getText().length() : 0;
                int selStart = Math.max(0, Math.min(text.selectionStart, len));
                int selEnd = Math.max(0, Math.min(text.selectionEnd, len));
                setSelection(selStart, selEnd);
            } else {
                setText("");
            }
        } finally {
            finishInternalChanges();
        }
    }

    protected void onSelectionChanged(int selStart, int selEnd) {
        if (mSettingExtractedText == 0 && mIME != null && selStart >= 0 && selEnd >= 0) {
            mIME.onExtractedSelectionChanged(selStart, selEnd);
        }
    }

    @Override
    public boolean performClick() {
        if (!super.performClick() && mIME != null) {
            mIME.onExtractedTextClicked();
            return true;
        }
        return false;
    }

    public boolean onTextContextMenuItem(int id) {
        if (mIME != null && mIME.onExtractTextContextMenuItem(id)) {
            return true;
        }
        return false;
    }

    public boolean isInputMethodTarget() {
        return true;
    }

    public boolean hasVerticalScrollBar() {
        return computeVerticalScrollRange() > computeVerticalScrollExtent();
    }

    @Override
    public boolean hasWindowFocus() {
        return isEnabled();
    }

    @Override
    public boolean isFocused() {
        return isEnabled();
    }

    @Override
    public boolean hasFocus() {
        return isEnabled();
    }
}
