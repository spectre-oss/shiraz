// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.drawable.Drawable;
import android.content.Intent;
/** Menu data, selection and action-view contract. */
public interface MenuItem {
 int SHOW_AS_ACTION_NEVER=0,SHOW_AS_ACTION_IF_ROOM=1,SHOW_AS_ACTION_ALWAYS=2,SHOW_AS_ACTION_WITH_TEXT=4,SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW=8;
 MenuItem setIconTintList(android.content.res.ColorStateList tint);android.content.res.ColorStateList getIconTintList();
 MenuItem setIconTintMode(android.graphics.PorterDuff.Mode mode);android.graphics.PorterDuff.Mode getIconTintMode();
 MenuItem setContentDescription(CharSequence description);CharSequence getContentDescription();
 MenuItem setTooltipText(CharSequence tooltip);CharSequence getTooltipText();
 void setShowAsAction(int actionEnum);MenuItem setShowAsActionFlags(int actionEnum);
 interface OnActionExpandListener {boolean onMenuItemActionExpand(MenuItem item);boolean onMenuItemActionCollapse(MenuItem item);}
 MenuItem setActionView(View view);MenuItem setActionView(int resource);View getActionView();boolean expandActionView();boolean collapseActionView();boolean isActionViewExpanded();MenuItem setOnActionExpandListener(OnActionExpandListener listener);
 interface OnMenuItemClickListener {boolean onMenuItemClick(MenuItem item);}
 int getItemId();int getGroupId();int getOrder();MenuItem setTitle(CharSequence title);MenuItem setTitle(int title);CharSequence getTitle();MenuItem setTitleCondensed(CharSequence title);CharSequence getTitleCondensed();MenuItem setIcon(Drawable icon);MenuItem setIcon(int icon);Drawable getIcon();
 MenuItem setIntent(Intent intent);Intent getIntent();MenuItem setCheckable(boolean checkable);boolean isCheckable();MenuItem setChecked(boolean checked);boolean isChecked();MenuItem setVisible(boolean visible);boolean isVisible();MenuItem setEnabled(boolean enabled);boolean isEnabled();boolean hasSubMenu();SubMenu getSubMenu();MenuItem setOnMenuItemClickListener(OnMenuItemClickListener listener);ContextMenu.ContextMenuInfo getMenuInfo();
}
