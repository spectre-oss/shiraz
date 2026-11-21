// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

/** ExpandableListAdapter backed by a group Cursor and per-group child Cursors. */
public abstract class CursorTreeAdapter extends BaseExpandableListAdapter implements Filterable {
    private final Context mContext;
    private final Handler mHandler;
    private final boolean mAutoRequery;
    private MyCursorHelper mGroupCursorHelper;
    private final SparseArray<MyCursorHelper> mChildrenCursorHelpers;
    private Filter mCursorFilter;
    private FilterQueryProvider mFilterQueryProvider;

    public CursorTreeAdapter(Cursor cursor, Context context) {
        this(cursor, context, true);
    }

    public CursorTreeAdapter(Cursor cursor, Context context, boolean autoRequery) {
        mContext = context;
        Looper looper = Looper.myLooper();
        if (looper == null) {
            looper = Looper.getMainLooper();
        }
        mHandler = looper != null ? new Handler(looper) : null;
        mAutoRequery = autoRequery;
        mGroupCursorHelper = new MyCursorHelper(cursor);
        mChildrenCursorHelpers = new SparseArray<MyCursorHelper>();
    }

    synchronized MyCursorHelper getChildrenCursorHelper(int groupPosition, boolean requestCursor) {
        MyCursorHelper cursorHelper = mChildrenCursorHelpers.get(groupPosition);
        if (cursorHelper == null) {
            if (mGroupCursorHelper.moveTo(groupPosition) == null) {
                return null;
            }
            final Cursor cursor = getChildrenCursor(mGroupCursorHelper.getCursor());
            cursorHelper = new MyCursorHelper(cursor);
            mChildrenCursorHelpers.put(groupPosition, cursorHelper);
        }
        return cursorHelper;
    }

    protected abstract Cursor getChildrenCursor(Cursor groupCursor);

    public void setGroupCursor(Cursor cursor) {
        mGroupCursorHelper.changeCursor(cursor, false);
    }

    public void setChildrenCursor(int groupPosition, Cursor childrenCursor) {
        MyCursorHelper childrenCursorHelper = getChildrenCursorHelper(groupPosition, false);
        if (childrenCursorHelper != null) {
            childrenCursorHelper.changeCursor(childrenCursor, false);
        }
    }

    @Override
    public Cursor getChild(int groupPosition, int childPosition) {
        MyCursorHelper helper = getChildrenCursorHelper(groupPosition, true);
        return helper != null ? helper.moveTo(childPosition) : null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        MyCursorHelper helper = getChildrenCursorHelper(groupPosition, true);
        return helper != null ? helper.getId(childPosition) : 0L;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        MyCursorHelper helper = getChildrenCursorHelper(groupPosition, true);
        return (mGroupCursorHelper.isValid() && helper != null) ? helper.getCount() : 0;
    }

    @Override
    public Cursor getGroup(int groupPosition) {
        return mGroupCursorHelper.moveTo(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return mGroupCursorHelper.getCount();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return mGroupCursorHelper.getId(groupPosition);
    }

    @Override
    public View getGroupView(
            int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        Cursor cursor = mGroupCursorHelper.moveTo(groupPosition);
        if (cursor == null) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        View v;
        if (convertView == null) {
            v = newGroupView(mContext, cursor, isExpanded, parent);
        } else {
            v = convertView;
        }
        bindGroupView(v, mContext, cursor, isExpanded);
        return v;
    }

    protected abstract View newGroupView(
            Context context, Cursor cursor, boolean isExpanded, ViewGroup parent);

    protected abstract void bindGroupView(
            View view, Context context, Cursor cursor, boolean isExpanded);

    @Override
    public View getChildView(
            int groupPosition,
            int childPosition,
            boolean isLastChild,
            View convertView,
            ViewGroup parent) {
        MyCursorHelper cursorHelper = getChildrenCursorHelper(groupPosition, true);
        Cursor cursor = cursorHelper != null ? cursorHelper.moveTo(childPosition) : null;
        if (cursor == null) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        View v;
        if (convertView == null) {
            v = newChildView(mContext, cursor, isLastChild, parent);
        } else {
            v = convertView;
        }
        bindChildView(v, mContext, cursor, isLastChild);
        return v;
    }

    protected abstract View newChildView(
            Context context, Cursor cursor, boolean isLastChild, ViewGroup parent);

    protected abstract void bindChildView(
            View view, Context context, Cursor cursor, boolean isLastChild);

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private synchronized void releaseCursorHelpers() {
        for (int pos = mChildrenCursorHelpers.size() - 1; pos >= 0; pos--) {
            mChildrenCursorHelpers.valueAt(pos).deactivate();
        }
        mChildrenCursorHelpers.clear();
    }

    @Override
    public void notifyDataSetChanged() {
        notifyDataSetChanged(true);
    }

    public void notifyDataSetChanged(boolean releaseCursors) {
        if (releaseCursors) {
            releaseCursorHelpers();
        }
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetInvalidated() {
        releaseCursorHelpers();
        super.notifyDataSetInvalidated();
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
        deactivateChildrenCursorHelper(groupPosition);
    }

    synchronized void deactivateChildrenCursorHelper(int groupPosition) {
        MyCursorHelper cursorHelper = mChildrenCursorHelpers.get(groupPosition);
        mChildrenCursorHelpers.remove(groupPosition);
        if (cursorHelper != null) {
            cursorHelper.deactivate();
        }
    }

    public String convertToString(Cursor cursor) {
        return cursor == null ? "" : cursor.toString();
    }

    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (mFilterQueryProvider != null) {
            return mFilterQueryProvider.runQuery(constraint);
        }
        return mGroupCursorHelper.getCursor();
    }

    @Override
    public Filter getFilter() {
        if (mCursorFilter == null) {
            mCursorFilter = new Filter() {
                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    return convertToString((Cursor) resultValue);
                }

                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    Cursor cursor = runQueryOnBackgroundThread(constraint);
                    FilterResults results = new FilterResults();
                    if (cursor != null) {
                        results.count = cursor.getCount();
                        results.values = cursor;
                    } else {
                        results.count = 0;
                        results.values = null;
                    }
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    Cursor oldCursor = getCursor();
                    if (results != null && results.values != null && results.values != oldCursor) {
                        changeCursor((Cursor) results.values);
                    }
                }
            };
        }
        return mCursorFilter;
    }

    public FilterQueryProvider getFilterQueryProvider() {
        return mFilterQueryProvider;
    }

    public void setFilterQueryProvider(FilterQueryProvider filterQueryProvider) {
        mFilterQueryProvider = filterQueryProvider;
    }

    public void changeCursor(Cursor cursor) {
        mGroupCursorHelper.changeCursor(cursor, true);
    }

    public Cursor getCursor() {
        return mGroupCursorHelper.getCursor();
    }

    final class MyCursorHelper {
        private Cursor mCursor;
        private boolean mDataValid;
        private int mRowIDColumn;
        private final ContentObserver mContentObserver;
        private final DataSetObserver mDataSetObserver;

        MyCursorHelper(Cursor cursor) {
            final boolean cursorPresent = cursor != null;
            mCursor = cursor;
            mDataValid = cursorPresent;
            mRowIDColumn = cursorPresent ? cursor.getColumnIndex("_id") : -1;
            mContentObserver = new ContentObserver(mHandler) {
                @Override
                public boolean deliverSelfNotifications() {
                    return true;
                }

                @Override
                public void onChange(boolean selfChange) {
                    if (mAutoRequery && mCursor != null && !mCursor.isClosed()) {
                        mDataValid = mCursor.requery();
                    }
                }
            };
            mDataSetObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    mDataValid = true;
                    notifyDataSetChanged();
                }

                @Override
                public void onInvalidated() {
                    mDataValid = false;
                    notifyDataSetInvalidated();
                }
            };
            if (cursorPresent) {
                cursor.registerContentObserver(mContentObserver);
                cursor.registerDataSetObserver(mDataSetObserver);
            }
        }

        Cursor getCursor() {
            return mCursor;
        }

        int getCount() {
            if (mDataValid && mCursor != null) {
                return mCursor.getCount();
            }
            return 0;
        }

        long getId(int position) {
            if (mDataValid && mCursor != null) {
                if (mCursor.moveToPosition(position)) {
                    return mRowIDColumn >= 0 ? mCursor.getLong(mRowIDColumn) : 0L;
                }
                return 0L;
            }
            return 0L;
        }

        Cursor moveTo(int position) {
            if (mDataValid && mCursor != null && mCursor.moveToPosition(position)) {
                return mCursor;
            }
            return null;
        }

        void changeCursor(Cursor cursor, boolean releaseCursors) {
            if (cursor == mCursor) {
                return;
            }
            deactivate();
            mCursor = cursor;
            if (cursor != null) {
                cursor.registerContentObserver(mContentObserver);
                cursor.registerDataSetObserver(mDataSetObserver);
                mRowIDColumn = cursor.getColumnIndex("_id");
                mDataValid = true;
                notifyDataSetChanged(releaseCursors);
            } else {
                mRowIDColumn = -1;
                mDataValid = false;
                notifyDataSetInvalidated();
            }
        }

        void deactivate() {
            if (mCursor == null) {
                return;
            }
            mCursor.unregisterContentObserver(mContentObserver);
            mCursor.unregisterDataSetObserver(mDataSetObserver);
            mCursor.close();
            mCursor = null;
            mDataValid = false;
        }

        boolean isValid() {
            return mDataValid && mCursor != null;
        }
    }
}
