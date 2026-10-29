// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.text.*;
import android.view.KeyEvent;
import android.view.View;

/** ASCII numeric editor for host committed text, paste and input connections. */
public class DigitsKeyListener extends NumberKeyListener {
    private final boolean signed, decimal;
    private final String accepted;
    private final shiraz.runtime.TextKeyListener editor;
    public DigitsKeyListener() { this(false, false); }
    public DigitsKeyListener(boolean signed, boolean decimal) {
        this.signed = signed; this.decimal = decimal;
        accepted = "0123456789" + (signed ? "-+" : "") + (decimal ? "." : "");
        editor = new shiraz.runtime.TextKeyListener(getInputType());
    }
    private DigitsKeyListener(String accepted) {
        if (accepted == null) throw new NullPointerException("accepted");
        if (accepted.length() > 256) throw new IllegalArgumentException("Accepted characters bound");
        this.accepted = accepted; signed = decimal = false;
        editor = new shiraz.runtime.TextKeyListener(getInputType());
    }
    public static DigitsKeyListener getInstance() { return new DigitsKeyListener(); }
    public static DigitsKeyListener getInstance(boolean sign, boolean decimal) { return new DigitsKeyListener(sign, decimal); }
    public static DigitsKeyListener getInstance(String accepted) { return new DigitsKeyListener(accepted); }
    @Override protected char[] getAcceptedChars() { return accepted.toCharArray(); }
    @Override public int getInputType() {
        return InputType.TYPE_CLASS_NUMBER | (signed ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0)
                | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0);
    }
    @Override public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        CharSequence basic = super.filter(source, start, end, dest, dstart, dend);
        if (!signed && !decimal) return basic;
        CharSequence input = basic == null ? source.subSequence(start, end) : basic;
        boolean hasPoint = false, hasSign = false;
        for (int i = 0; i < dest.length(); i++) {
            if (i >= dstart && i < dend) continue;
            char c = dest.charAt(i);
            if (c == '.') hasPoint = true;
            if (c == '-' || c == '+') {
                // Inserting before a retained sign would move it away from the start.
                if (i >= dend) return input.length() == 0 ? basic : "";
                hasSign = true;
            }
        }
        SpannableStringBuilder filtered = new SpannableStringBuilder(input);
        int kept = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean remove = false;
            if (c == '-' || c == '+') {
                remove = hasSign || dstart != 0 || kept != 0;
                if (!remove) hasSign = true;
            } else if (c == '.') {
                remove = hasPoint;
                if (!remove) hasPoint = true;
            }
            if (remove) filtered.delete(kept, kept + 1); else kept++;
        }
        return kept == input.length() ? basic : filtered;
    }
    @Override public boolean onKeyDown(View view, Editable text, int code, KeyEvent event) { return editor.onKeyDown(view,text,code,event); }
    @Override public boolean onKeyUp(View view, Editable text, int code, KeyEvent event) { return editor.onKeyUp(view,text,code,event); }
    @Override public boolean onKeyOther(View view, Editable text, KeyEvent event) { return editor.onKeyOther(view,text,event); }
    @Override public void clearMetaKeyState(View view, Editable text, int states) { editor.clearMetaKeyState(view,text,states); }
}
