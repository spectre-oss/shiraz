// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.icu.util.ULocale;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;

/** SegmentFinder using locale-aware Unicode word boundaries (excluding whitespace-only runs). */
public class WordSegmentFinder extends SegmentFinder {
    private final PrescribedSegmentFinder mDelegate;

    public WordSegmentFinder(CharSequence text, ULocale locale) {
        if (text == null || locale == null) {
            throw new NullPointerException();
        }
        Locale javaLocale = locale.toLocale();
        BreakIterator iterator = BreakIterator.getWordInstance(
                javaLocale != null ? javaLocale : Locale.getDefault());
        String str = text.toString();
        iterator.setText(str);
        ArrayList<Integer> pairs = new ArrayList<Integer>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (end > start && !isWhitespaceOnly(str, start, end)) {
                pairs.add(Integer.valueOf(start));
                pairs.add(Integer.valueOf(end));
            }
        }
        int[] segments = new int[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            segments[i] = pairs.get(i).intValue();
        }
        mDelegate = new PrescribedSegmentFinder(segments);
    }

    private static boolean isWhitespaceOnly(String text, int start, int end) {
        for (int i = start; i < end;) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    @Override
    public int previousStartBoundary(int offset) {
        return mDelegate.previousStartBoundary(offset);
    }

    @Override
    public int previousEndBoundary(int offset) {
        return mDelegate.previousEndBoundary(offset);
    }

    @Override
    public int nextStartBoundary(int offset) {
        return mDelegate.nextStartBoundary(offset);
    }

    @Override
    public int nextEndBoundary(int offset) {
        return mDelegate.nextEndBoundary(offset);
    }
}
