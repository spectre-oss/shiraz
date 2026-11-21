// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import java.util.Locale;

public class PhoneNumberFormattingTextWatcher implements TextWatcher {
    private boolean mSelfChange = false;
    private boolean mStopFormatting;
    private final String mCountryCode;

    public PhoneNumberFormattingTextWatcher() {
        this(Locale.getDefault().getCountry());
    }

    public PhoneNumberFormattingTextWatcher(String countryCode) {
        if (countryCode == null) {
            throw new IllegalArgumentException();
        }
        mCountryCode = countryCode;
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (mSelfChange || mStopFormatting) {
            return;
        }
        if (count > 0 && hasSeparator(s, start, count)) {
            stopFormatting();
        }
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mSelfChange || mStopFormatting) {
            return;
        }
        if (count > 0 && hasSeparator(s, start, count)) {
            stopFormatting();
        }
    }

    public synchronized void afterTextChanged(Editable s) {
        if (mStopFormatting) {
            mStopFormatting = (s.length() != 0);
            return;
        }
        if (mSelfChange) {
            return;
        }
        int cursorPos = Selection.getSelectionEnd(s);
        String formatted = reformat(s, cursorPos);
        if (formatted != null && !formatted.equals(s.toString())) {
            int rememberedDigitsBeforeCursor = countDigitsBefore(s, cursorPos);
            mSelfChange = true;
            s.replace(0, s.length(), formatted, 0, formatted.length());
            if (formatted.equals(s.toString())) {
                int newCursor = findCursorAfterDigits(s, rememberedDigitsBeforeCursor);
                Selection.setSelection(s, newCursor);
            }
            mSelfChange = false;
        }
        PhoneNumberUtils.addTtsSpan(s, 0, s.length());
    }

    private String reformat(CharSequence s, int cursor) {
        if (s == null || s.length() == 0) {
            return null;
        }
        String raw = s.toString();
        return PhoneNumberUtils.formatNumber(raw, mCountryCode);
    }

    private static int countDigitsBefore(CharSequence s, int pos) {
        if (pos < 0) {
            pos = s.length();
        }
        int end = Math.min(pos, s.length());
        int count = 0;
        for (int i = 0; i < end; i++) {
            if (PhoneNumberUtils.isNonSeparator(s.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static int findCursorAfterDigits(CharSequence s, int targetDigits) {
        if (targetDigits <= 0) {
            return 0;
        }
        int seen = 0;
        for (int i = 0; i < s.length(); i++) {
            if (PhoneNumberUtils.isNonSeparator(s.charAt(i))) {
                seen++;
                if (seen == targetDigits) {
                    return i + 1;
                }
            }
        }
        return s.length();
    }

    private void stopFormatting() {
        mStopFormatting = true;
    }

    private boolean hasSeparator(CharSequence s, int start, int count) {
        for (int i = start; i < start + count; i++) {
            char c = s.charAt(i);
            if (!PhoneNumberUtils.isNonSeparator(c)) {
                return true;
            }
        }
        return false;
    }
}
