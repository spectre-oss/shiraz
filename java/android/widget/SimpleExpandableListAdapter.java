// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import java.util.Map;

/** ExpandableListAdapter mapping static group and child Map lists to XML layouts. */
public class SimpleExpandableListAdapter extends BaseExpandableListAdapter {
    private final List<? extends Map<String, ?>> mGroupData;
    private final int mExpandedGroupLayout;
    private final int mCollapsedGroupLayout;
    private final String[] mGroupFrom;
    private final int[] mGroupTo;
    private final List<? extends List<? extends Map<String, ?>>> mChildData;
    private final int mChildLayout;
    private final int mLastChildLayout;
    private final String[] mChildFrom;
    private final int[] mChildTo;
    private final LayoutInflater mInflater;

    public SimpleExpandableListAdapter(
            Context context,
            List<? extends Map<String, ?>> groupData,
            int groupLayout,
            String[] groupFrom,
            int[] groupTo,
            List<? extends List<? extends Map<String, ?>>> childData,
            int childLayout,
            String[] childFrom,
            int[] childTo) {
        this(context, groupData, groupLayout, groupLayout, groupFrom, groupTo,
                childData, childLayout, childLayout, childFrom, childTo);
    }

    public SimpleExpandableListAdapter(
            Context context,
            List<? extends Map<String, ?>> groupData,
            int expandedGroupLayout,
            int collapsedGroupLayout,
            String[] groupFrom,
            int[] groupTo,
            List<? extends List<? extends Map<String, ?>>> childData,
            int childLayout,
            String[] childFrom,
            int[] childTo) {
        this(context, groupData, expandedGroupLayout, collapsedGroupLayout, groupFrom, groupTo,
                childData, childLayout, childLayout, childFrom, childTo);
    }

    public SimpleExpandableListAdapter(
            Context context,
            List<? extends Map<String, ?>> groupData,
            int expandedGroupLayout,
            int collapsedGroupLayout,
            String[] groupFrom,
            int[] groupTo,
            List<? extends List<? extends Map<String, ?>>> childData,
            int childLayout,
            int lastChildLayout,
            String[] childFrom,
            int[] childTo) {
        mGroupData = groupData;
        mExpandedGroupLayout = expandedGroupLayout;
        mCollapsedGroupLayout = collapsedGroupLayout;
        mGroupFrom = groupFrom;
        mGroupTo = groupTo;
        mChildData = childData;
        mChildLayout = childLayout;
        mLastChildLayout = lastChildLayout;
        mChildFrom = childFrom;
        mChildTo = childTo;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return mChildData.get(groupPosition).get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(
            int groupPosition,
            int childPosition,
            boolean isLastChild,
            View convertView,
            ViewGroup parent) {
        View v;
        if (convertView == null) {
            v = newChildView(isLastChild, parent);
        } else {
            v = convertView;
        }
        bindView(v, mChildData.get(groupPosition).get(childPosition), mChildFrom, mChildTo);
        return v;
    }

    public View newChildView(boolean isLastChild, ViewGroup parent) {
        return mInflater.inflate(
                isLastChild ? mLastChildLayout : mChildLayout, parent, false);
    }

    private void bindView(View view, Map<String, ?> data, String[] from, int[] to) {
        int len = to.length;
        for (int i = 0; i < len; i++) {
            TextView v = (TextView) view.findViewById(to[i]);
            if (v != null) {
                Object val = data.get(from[i]);
                v.setText(val != null ? val.toString() : "");
            }
        }
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return mChildData.get(groupPosition).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mGroupData.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return mGroupData.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(
            int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        View v;
        if (convertView == null) {
            v = newGroupView(isExpanded, parent);
        } else {
            v = convertView;
        }
        bindView(v, mGroupData.get(groupPosition), mGroupFrom, mGroupTo);
        return v;
    }

    public View newGroupView(boolean isExpanded, ViewGroup parent) {
        return mInflater.inflate(
                isExpanded ? mExpandedGroupLayout : mCollapsedGroupLayout, parent, false);
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
