// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;

/** SegmentFinder using Unicode grapheme cluster boundaries. */
public class GraphemeClusterSegmentFinder extends SegmentFinder {
    private final PrescribedSegmentFinder mDelegate;

    public GraphemeClusterSegmentFinder(CharSequence text, TextPaint textPaint) {
        if (text == null || textPaint == null) {
            throw new NullPointerException();
        }
        Locale locale = textPaint.getTextLocale();
        BreakIterator iterator = BreakIterator.getCharacterInstance(
                locale != null ? locale : Locale.getDefault());
        String str = text.toString();
        iterator.setText(str);
        ArrayList<Integer> pairs = new ArrayList<Integer>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (end > start) {
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
