// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.drawable.Drawable;
public interface ContextMenu extends Menu {
 interface ContextMenuInfo { }
 ContextMenu setHeaderTitle(int title);ContextMenu setHeaderTitle(CharSequence title);ContextMenu setHeaderIcon(int icon);ContextMenu setHeaderIcon(Drawable icon);ContextMenu setHeaderView(View view);void clearHeader();
}
