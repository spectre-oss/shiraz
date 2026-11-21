// SPDX-License-Identifier: GPL-3.0-only
package android.icu.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.Objects;

/**
 * Overlay holder providing API 35 inner classes of {@code ListFormatter}.
 */
public final class ListFormatter {
    private ListFormatter() {
    }

    public enum Type {
        AND,
        OR,
        UNITS
    }

    public enum Width {
        WIDE,
        SHORT,
        NARROW
    }

    public static final class FormattedList implements FormattedValue {
        private final String mText;
        private final AttributedString mAttributed;

        FormattedList(String text) {
            mText = text != null ? text : "";
            mAttributed = new AttributedString(mText);
        }

        @Override
        public String toString() {
            return mText;
        }

        @Override
        public int length() {
            return mText.length();
        }

        @Override
        public char charAt(int index) {
            return mText.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return mText.subSequence(start, end);
        }

        @Override
        public <A extends Appendable> A appendTo(A appendable) {
            Objects.requireNonNull(appendable, "appendable");
            try {
                appendable.append(mText);
                return appendable;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean nextPosition(ConstrainedFieldPosition cfpos) {
            Objects.requireNonNull(cfpos, "cfpos");
            return false;
        }

        @Override
        public AttributedCharacterIterator toCharacterIterator() {
            return mAttributed.getIterator();
        }
    }
}
