// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.text.ConstrainedFieldPosition;
import android.icu.text.DisplayOptions;
import android.icu.text.FormattedValue;
import android.icu.util.MeasureUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

/** Result of a number formatting operation from LocalizedNumberFormatter. */
public class FormattedNumber implements FormattedValue {
    private final String mString;
    private final BigDecimal mNumber;
    private final MeasureUnit mOutputUnit;
    private final DisplayOptions.NounClass mNounClass;

    FormattedNumber(
            String string,
            BigDecimal number,
            MeasureUnit outputUnit,
            DisplayOptions.NounClass nounClass) {
        mString = string != null ? string : "";
        mNumber = number != null ? number : BigDecimal.ZERO;
        mOutputUnit = outputUnit;
        mNounClass = nounClass != null ? nounClass : DisplayOptions.NounClass.UNDEFINED;
    }

    @Override
    public String toString() {
        return mString;
    }

    @Override
    public int length() {
        return mString.length();
    }

    @Override
    public char charAt(int index) {
        return mString.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return mString.subSequence(start, end);
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
    public boolean nextPosition(ConstrainedFieldPosition cfpos) {
        return false;
    }

    @Override
    public AttributedCharacterIterator toCharacterIterator() {
        return new AttributedString(mString).getIterator();
    }

    public BigDecimal toBigDecimal() {
        return mNumber;
    }

    public MeasureUnit getOutputUnit() {
        return mOutputUnit;
    }

    public DisplayOptions.NounClass getNounClass() {
        return mNounClass;
    }
}
