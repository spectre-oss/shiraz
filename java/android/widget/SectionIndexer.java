// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

/** Interface for adapters that partition items into ordered fast-scroll sections. */
public interface SectionIndexer {
    Object[] getSections();

    int getPositionForSection(int sectionIndex);

    int getSectionForPosition(int position);
}
