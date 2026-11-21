// SPDX-License-Identifier: GPL-3.0-only
package android.icu.text;

import java.text.AttributedCharacterIterator;

/** An abstract formatted value: a string with associated field attributes. */
public interface FormattedValue extends CharSequence {
    @Override
    String toString();

    <A extends Appendable> A appendTo(A appendable);

    boolean nextPosition(ConstrainedFieldPosition cfpos);

    AttributedCharacterIterator toCharacterIterator();
}
