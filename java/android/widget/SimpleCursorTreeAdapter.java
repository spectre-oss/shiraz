// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;

/** ResourceCursorTreeAdapter mapping group and child Cursor columns to views. */
public abstract class SimpleCursorTreeAdapter extends ResourceCursorTreeAdapter {
    public interface ViewBinder {
        boolean setViewValue(View view, Cursor cursor, int columnIndex);
    }

    private final String[] mGroupFromNames;
    private int[] mGroupFrom;
    private final int[] mGroupTo;
    private final String[] mChildFromNames;
    private int[] mChildFrom;
    private final int[] mChildTo;
    private ViewBinder mViewBinder;

    public SimpleCursorTreeAdapter(
            Context context,
            Cursor cursor,
            int collapsedGroupLayout,
            int expandedGroupLayout,
            String[] groupFrom,
            int[] groupTo,
            int childLayout,
            int lastChildLayout,
            String[] childFrom,
            int[] childTo) {
        super(context, cursor, collapsedGroupLayout, expandedGroupLayout, childLayout, lastChildLayout);
        mGroupFromNames = groupFrom;
        mGroupTo = groupTo;
        mChildFromNames = childFrom;
        mChildTo = childTo;
    }

    public SimpleCursorTreeAdapter(
            Context context,
            Cursor cursor,
            int collapsedGroupLayout,
            int expandedGroupLayout,
            String[] groupFrom,
            int[] groupTo,
            int childLayout,
            String[] childFrom,
            int[] childTo) {
        this(context, cursor, collapsedGroupLayout, expandedGroupLayout, groupFrom, groupTo,
                childLayout, childLayout, childFrom, childTo);
    }

    public SimpleCursorTreeAdapter(
            Context context,
            Cursor cursor,
            int groupLayout,
            String[] groupFrom,
            int[] groupTo,
            int childLayout,
            String[] childFrom,
            int[] childTo) {
        this(context, cursor, groupLayout, groupLayout, groupFrom, groupTo,
                childLayout, childLayout, childFrom, childTo);
    }

    public ViewBinder getViewBinder() {
        return mViewBinder;
    }

    public void setViewBinder(ViewBinder viewBinder) {
        mViewBinder = viewBinder;
    }

    private void bindView(View view, Context context, Cursor cursor, int[] from, int[] to) {
        final ViewBinder binder = mViewBinder;
        for (int i = 0; i < to.length; i++) {
            View v = view.findViewById(to[i]);
            if (v != null) {
                boolean bound = false;
                if (binder != null) {
                    bound = binder.setViewValue(v, cursor, from[i]);
                }
                if (!bound) {
                    String text = cursor.getString(from[i]);
                    if (text == null) {
                        text = "";
                    }
                    if (v instanceof TextView) {
                        setViewText((TextView) v, text);
                    } else if (v instanceof ImageView) {
                        setViewImage((ImageView) v, text);
                    } else {
                        throw new IllegalStateException(
                                "SimpleCursorTreeAdapter can bind values only to TextView and ImageView!");
                    }
                }
            }
        }
    }

    private void initFromColumns(Cursor cursor, String[] fromColumnNames, int[] fromColumns) {
        for (int i = fromColumnNames.length - 1; i >= 0; i--) {
            fromColumns[i] = cursor.getColumnIndexOrThrow(fromColumnNames[i]);
        }
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
        if (mChildFrom == null) {
            mChildFrom = new int[mChildFromNames.length];
            initFromColumns(cursor, mChildFromNames, mChildFrom);
        }
        bindView(view, context, cursor, mChildFrom, mChildTo);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        if (mGroupFrom == null) {
            mGroupFrom = new int[mGroupFromNames.length];
            initFromColumns(cursor, mGroupFromNames, mGroupFrom);
        }
        bindView(view, context, cursor, mGroupFrom, mGroupTo);
    }

    protected void setViewImage(ImageView v, String value) {
        try {
            v.setImageResource(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            v.setImageURI(Uri.parse(value));
        }
    }

    public void setViewText(TextView v, String text) {
        v.setText(text);
    }
}
