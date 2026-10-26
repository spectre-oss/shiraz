// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.drawable.Drawable;
public interface SubMenu extends Menu {
 SubMenu setHeaderTitle(int title);SubMenu setHeaderTitle(CharSequence title);SubMenu setHeaderIcon(int icon);SubMenu setHeaderIcon(Drawable icon);SubMenu setHeaderView(View view);void clearHeader();SubMenu setIcon(int icon);SubMenu setIcon(Drawable icon);MenuItem getItem();
}
