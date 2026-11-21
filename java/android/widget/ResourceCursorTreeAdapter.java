// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** CursorTreeAdapter that inflates group and child views from layout resources. */
public abstract class ResourceCursorTreeAdapter extends CursorTreeAdapter {
    private final int mCollapsedGroupLayout;
    private final int mExpandedGroupLayout;
    private final int mChildLayout;
    private final int mLastChildLayout;
    private final LayoutInflater mInflater;

    public ResourceCursorTreeAdapter(
            Context context,
            Cursor cursor,
            int collapsedGroupLayout,
            int expandedGroupLayout,
            int childLayout,
            int lastChildLayout) {
        super(cursor, context);
        mCollapsedGroupLayout = collapsedGroupLayout;
        mExpandedGroupLayout = expandedGroupLayout;
        mChildLayout = childLayout;
        mLastChildLayout = lastChildLayout;
        mInflater = LayoutInflater.from(context);
    }

    public ResourceCursorTreeAdapter(
            Context context,
            Cursor cursor,
            int collapsedGroupLayout,
            int expandedGroupLayout,
            int childLayout) {
        this(context, cursor, collapsedGroupLayout, expandedGroupLayout, childLayout, childLayout);
    }

    public ResourceCursorTreeAdapter(
            Context context, Cursor cursor, int groupLayout, int childLayout) {
        this(context, cursor, groupLayout, groupLayout, childLayout, childLayout);
    }

    @Override
    public View newChildView(
            Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
        return mInflater.inflate(
                isLastChild ? mLastChildLayout : mChildLayout, parent, false);
    }

    @Override
    public View newGroupView(
            Context context, Cursor cursor, boolean isExpanded, ViewGroup parent) {
        return mInflater.inflate(
                isExpanded ? mExpandedGroupLayout : mCollapsedGroupLayout, parent, false);
    }
}
