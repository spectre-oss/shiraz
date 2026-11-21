// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.text.ConstrainedFieldPosition;
import android.icu.text.FormattedValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.Objects;

/** Result of a number range formatting operation from LocalizedNumberRangeFormatter. */
public class FormattedNumberRange implements FormattedValue {
    private final String mString;
    private final BigDecimal mFirst;
    private final BigDecimal mSecond;
    private final NumberRangeFormatter.RangeIdentityResult mIdentityResult;

    FormattedNumberRange(
            String string,
            BigDecimal first,
            BigDecimal second,
            NumberRangeFormatter.RangeIdentityResult identityResult) {
        mString = string != null ? string : "";
        mFirst = first != null ? first : BigDecimal.ZERO;
        mSecond = second != null ? second : BigDecimal.ZERO;
        mIdentityResult = identityResult != null
                ? identityResult
                : NumberRangeFormatter.RangeIdentityResult.NOT_EQUAL;
    }

    @Override
    public String toString() {
        return mString;
    }

    @Override
    public <A extends Appendable> A appendTo(A appendable) {
        try {
            appendable.append(mString);
            return appendable;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public char charAt(int index) {
        return mString.charAt(index);
    }

    @Override
    public int length() {
        return mString.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return mString.subSequence(start, end);
    }

    @Override
    public boolean nextPosition(ConstrainedFieldPosition cfpos) {
        return false;
    }

    @Override
    public AttributedCharacterIterator toCharacterIterator() {
        return new AttributedString(mString).getIterator();
    }

    public BigDecimal getFirstBigDecimal() {
        return mFirst;
    }

    public BigDecimal getSecondBigDecimal() {
        return mSecond;
    }

    public NumberRangeFormatter.RangeIdentityResult getIdentityResult() {
        return mIdentityResult;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mString, mFirst, mSecond, mIdentityResult);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FormattedNumberRange)) return false;
        FormattedNumberRange that = (FormattedNumberRange) other;
        return Objects.equals(mString, that.mString)
                && Objects.equals(mFirst, that.mFirst)
                && Objects.equals(mSecond, that.mSecond)
                && mIdentityResult == that.mIdentityResult;
    }
}
