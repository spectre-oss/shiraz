// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
public interface HeterogeneousExpandableList {
 int getGroupType(int group);int getChildType(int group,int child);int getGroupTypeCount();int getChildTypeCount();
}
