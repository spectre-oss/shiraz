// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import shiraz.runtime.HostContextMenu;

/**
 * PopupMenu shown as a PopupWindow list under its anchor. Top-level items
 * only: selecting an item with a submenu, icons and checkmarks are not drawn.
 */
public class PopupMenu {
    public interface OnMenuItemClickListener {boolean onMenuItemClick(MenuItem item);}
    public interface OnDismissListener {void onDismiss(PopupMenu menu);}

    private final Context context;
    private final View anchor;
    private final HostContextMenu menu;
    private OnMenuItemClickListener listener;
    private OnDismissListener dismissListener;
    private PopupWindow popup;
    private int gravity;

    public PopupMenu(Context context,View anchor){this(context,anchor,Gravity.NO_GRAVITY);}
    public PopupMenu(Context context,View anchor,int gravity){this(context,anchor,gravity,0,0);}
    public PopupMenu(Context context,View anchor,int gravity,int attr,int style){
        if(context==null||anchor==null)throw new NullPointerException("PopupMenu context and anchor");
        this.context=context;this.anchor=anchor;this.gravity=gravity;menu=new HostContextMenu(context);
        menu.shirazSetSelectionListener(new MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(MenuItem item){return listener!=null&&listener.onMenuItemClick(item);}});
    }
    public Menu getMenu(){return menu;}
    public MenuInflater getMenuInflater(){return new MenuInflater(context);}
    public void inflate(int resource){getMenuInflater().inflate(resource,menu);}
    public void setOnMenuItemClickListener(OnMenuItemClickListener value){listener=value;}
    public void setOnDismissListener(OnDismissListener value){dismissListener=value;}
    public int getGravity(){return gravity;}
    public void setGravity(int value){gravity=value;}
    public void show(){
        dismiss();
        LinearLayout list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);
        float density=context.getResources().getDisplayMetrics().density;int pad=(int)(16*density),height=(int)(48*density);
        for(int i=0;i<menu.size();i++){
            final MenuItem item=menu.getItem(i);if(!item.isVisible())continue;
            TextView row=new TextView(context);row.setText(item.getTitle());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(pad,0,pad,0);
            row.setTextSize(16);row.setTextColor(item.isEnabled()?0xde000000:0x61000000);row.setMinimumHeight(height);row.setEnabled(item.isEnabled());
            row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dismiss();select(item);}});
            list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        popup=new PopupWindow(list,(int)(200*density),-2,true);
        popup.setBackgroundDrawable(new ColorDrawable(0xffffffff));popup.setOutsideTouchable(true);
        popup.setOnDismissListener(new PopupWindow.OnDismissListener(){public void onDismiss(){popup=null;if(dismissListener!=null)dismissListener.onDismiss(PopupMenu.this);}});
        popup.showAsDropDown(anchor,0,0,gravity==Gravity.NO_GRAVITY?Gravity.START:gravity);
    }
    private void select(MenuItem item){
        if(item.getItemId()!=0&&menu.findItem(item.getItemId())==item){menu.performIdentifierAction(item.getItemId(),0);return;}
        if(listener!=null)listener.onMenuItemClick(item);
    }
    public void dismiss(){if(popup!=null)popup.dismiss();}
}
