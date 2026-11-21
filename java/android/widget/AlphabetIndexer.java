// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.database.Cursor;
import android.database.DataSetObserver;
import android.util.SparseIntArray;
import java.text.Collator;

/** Binary-search section indexer over a sorted Cursor column and alphabet string. */
public class AlphabetIndexer extends DataSetObserver implements SectionIndexer {
    protected Cursor mDataCursor;
    protected int mColumnIndex;
    protected CharSequence mAlphabet;

    private int mAlphabetLength;
    private String[] mAlphabetArray;
    private final Collator mCollator;
    private final SparseIntArray mAlphaMap;

    public AlphabetIndexer(Cursor cursor, int sortedColumnIndex, CharSequence alphabet) {
        mDataCursor = cursor;
        mColumnIndex = sortedColumnIndex;
        mAlphabet = alphabet != null ? alphabet : "";
        mAlphabetLength = mAlphabet.length();
        mAlphabetArray = new String[mAlphabetLength];
        for (int i = 0; i < mAlphabetLength; i++) {
            mAlphabetArray[i] = Character.toString(mAlphabet.charAt(i));
        }
        mAlphaMap = new SparseIntArray(mAlphabetLength);
        if (cursor != null) {
            cursor.registerDataSetObserver(this);
        }
        mCollator = Collator.getInstance();
        mCollator.setStrength(Collator.PRIMARY);
    }

    @Override
    public Object[] getSections() {
        return mAlphabetArray;
    }

    public void setCursor(Cursor cursor) {
        if (mDataCursor != null) {
            mDataCursor.unregisterDataSetObserver(this);
        }
        mDataCursor = cursor;
        if (cursor != null) {
            mDataCursor.registerDataSetObserver(this);
        }
        mAlphaMap.clear();
    }

    protected int compare(String word, String letter) {
        final String firstLetter;
        if (word == null || word.length() == 0) {
            firstLetter = " ";
        } else {
            firstLetter = word.substring(0, 1);
        }
        return mCollator.compare(firstLetter, letter);
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        final SparseIntArray alphaMap = mAlphaMap;
        final Cursor cursor = mDataCursor;
        if (cursor == null || mAlphabet == null || mAlphabetLength == 0) {
            return 0;
        }
        if (sectionIndex <= 0) {
            return 0;
        }
        if (sectionIndex >= mAlphabetLength) {
            sectionIndex = mAlphabetLength - 1;
        }

        int savedCursorPos = cursor.getPosition();
        int count = cursor.getCount();
        int start = 0;
        int end = count;
        char letter = mAlphabet.charAt(sectionIndex);
        String targetLetter = Character.toString(letter);
        int key = letter;
        int pos = alphaMap.get(key, Integer.MIN_VALUE);
        if (Integer.MIN_VALUE != pos) {
            if (pos < 0) {
                pos = -pos;
                end = pos;
            } else {
                return pos;
            }
        }

        if (sectionIndex > 0) {
            int prevLetter = mAlphabet.charAt(sectionIndex - 1);
            int prevLetterPos = alphaMap.get(prevLetter, Integer.MIN_VALUE);
            if (prevLetterPos != Integer.MIN_VALUE) {
                start = Math.abs(prevLetterPos);
            }
        }

        pos = (end + start) / 2;
        while (pos < end) {
            cursor.moveToPosition(pos);
            String curName = cursor.getString(mColumnIndex);
            if (curName == null) {
                if (pos == 0) {
                    break;
                } else {
                    pos--;
                    continue;
                }
            }
            int diff = compare(curName, targetLetter);
            if (diff != 0) {
                if (diff < 0) {
                    start = pos + 1;
                    if (start >= count) {
                        pos = count;
                        break;
                    }
                } else {
                    end = pos;
                }
            } else {
                if (start == pos) {
                    break;
                } else {
                    end = pos;
                }
            }
            pos = (start + end) / 2;
        }
        alphaMap.put(key, pos);
        cursor.moveToPosition(savedCursorPos);
        return pos;
    }

    @Override
    public int getSectionForPosition(int position) {
        if (mDataCursor == null || mAlphabetLength == 0) {
            return 0;
        }
        int savedCursorPos = mDataCursor.getPosition();
        mDataCursor.moveToPosition(position);
        String curName = mDataCursor.getString(mColumnIndex);
        mDataCursor.moveToPosition(savedCursorPos);
        for (int i = 0; i < mAlphabetLength; i++) {
            char letter = mAlphabet.charAt(i);
            String targetLetter = Character.toString(letter);
            if (compare(curName, targetLetter) == 0) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void onChanged() {
        super.onChanged();
        mAlphaMap.clear();
    }

    @Override
    public void onInvalidated() {
        super.onInvalidated();
        mAlphaMap.clear();
    }
}
