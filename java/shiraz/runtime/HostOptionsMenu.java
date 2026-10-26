// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Context;
import android.view.MenuItem;
/** Options-panel menu data; popup rendering is owned separately. */
public final class HostOptionsMenu extends MenuModel {
 private boolean retired;
 public HostOptionsMenu(Context context,MenuItem.OnMenuItemClickListener listener){super(context,null,0,new Budget());selection=listener;}
 public void retire(){retired=true;selection=null;clear();close();}
 @Override public boolean performIdentifierAction(int id,int flags){return !retired&&super.performIdentifierAction(id,flags);}
}
