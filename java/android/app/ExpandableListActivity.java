// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Bundle;
import android.view.ContextMenu;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;

@Deprecated
public class ExpandableListActivity extends Activity
        implements View.OnCreateContextMenuListener,
        ExpandableListView.OnChildClickListener,
        ExpandableListView.OnGroupCollapseListener,
        ExpandableListView.OnGroupExpandListener {
    private static final int ID_LIST = 0x0102000a;
    private static final int ID_EMPTY = 0x01020004;

    private ExpandableListAdapter mAdapter;
    private ExpandableListView mList;
    private boolean mFinishedStart;

    public ExpandableListActivity() {
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
        return false;
    }

    @Override
    public void onGroupCollapse(int groupPosition) {
    }

    @Override
    public void onGroupExpand(int groupPosition) {
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        ensureList();
        super.onRestoreInstanceState(state);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        View emptyView = findViewById(ID_EMPTY);
        View rawList = findViewById(ID_LIST);
        if (rawList == null) {
            return;
        }
        if (!(rawList instanceof ExpandableListView)) {
            throw new RuntimeException(
                    "Your content must have a ExpandableListView whose id attribute is 'android.R.id.list'");
        }
        mList = (ExpandableListView) rawList;
        if (emptyView != null) {
            mList.setEmptyView(emptyView);
        }
        mList.setOnChildClickListener(this);
        mList.setOnGroupExpandListener(this);
        mList.setOnGroupCollapseListener(this);
        if (mFinishedStart) {
            setListAdapter(mAdapter);
        }
        mFinishedStart = true;
    }

    public void setListAdapter(ExpandableListAdapter adapter) {
        synchronized (this) {
            ensureList();
            mAdapter = adapter;
            mList.setAdapter(adapter);
        }
    }

    public ExpandableListView getExpandableListView() {
        ensureList();
        return mList;
    }

    public ExpandableListAdapter getExpandableListAdapter() {
        return mAdapter;
    }

    private void ensureList() {
        if (mList != null) {
            return;
        }
        ExpandableListView list = new ExpandableListView(this);
        list.setId(ID_LIST);
        setContentView(list);
        if (mList == null) {
            mList = list;
            mList.setOnChildClickListener(this);
            mList.setOnGroupExpandListener(this);
            mList.setOnGroupCollapseListener(this);
        }
    }

    public long getSelectedId() {
        ensureList();
        return mList.getSelectedId();
    }

    public long getSelectedPosition() {
        ensureList();
        return mList.getSelectedPosition();
    }

    public boolean setSelectedChild(int groupPosition, int childPosition, boolean shouldExpandGroup) {
        ensureList();
        return mList.setSelectedChild(groupPosition, childPosition, shouldExpandGroup);
    }

    public void setSelectedGroup(int groupPosition) {
        ensureList();
        mList.setSelectedGroup(groupPosition);
    }
}
