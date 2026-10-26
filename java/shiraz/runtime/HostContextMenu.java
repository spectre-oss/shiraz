// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.*;
import java.util.ArrayList;

/** Bounded menu data and selection. Native popup presentation remains unsupported. */
public final class HostContextMenu extends MenuModel implements ContextMenu {
 public static int shirazShowAsAction(MenuItem item){if(!(item instanceof MenuModel.Item))throw new IllegalArgumentException("Foreign menu item");return ((MenuModel.Item)item).showAsAction;}
 public static void shirazSetExclusive(MenuItem item,boolean value){if(!(item instanceof MenuModel.Item))throw new UnsupportedOperationException("Exclusive menu item implementation");((MenuModel.Item)item).exclusive=value;}
 public HostContextMenu(Context context){super(context,null,0,new Budget());}
 public ContextMenu setHeaderTitle(int id){return setHeaderTitle(context.getString(id));}
 public ContextMenu setHeaderTitle(CharSequence title){headerView=null;headerTitle=title;return this;}
 public ContextMenu setHeaderIcon(int id){return setHeaderIcon(context.getDrawable(id));}
 public ContextMenu setHeaderIcon(Drawable icon){headerView=null;headerIcon=icon;return this;}
 public ContextMenu setHeaderView(View view){headerTitle=null;headerIcon=null;headerView=view;return this;}
 public void clearHeader(){clearHeaderData();}
 public CharSequence shirazHeaderTitle(){return headerTitle;}public Drawable shirazHeaderIcon(){return headerIcon;}public View shirazHeaderView(){return headerView;}
 public boolean shirazIsClosed(){return closed;}
 public void shirazSetSelectionListener(MenuItem.OnMenuItemClickListener listener){selection=listener;}
 public void shirazSetCurrentInfo(ContextMenu.ContextMenuInfo info){currentInfo=info;}
}
class MenuModel implements Menu {
 static final class Budget {int items;}
 final Context context;final MenuModel parent;final int depth;final Budget budget;final ArrayList<Item> items=new ArrayList<Item>();
 ContextMenu.ContextMenuInfo currentInfo;MenuItem.OnMenuItemClickListener selection;boolean closed;CharSequence headerTitle;Drawable headerIcon;View headerView;
 MenuModel(Context context,MenuModel parent,int depth,Budget budget){if(context==null)throw new NullPointerException("context");this.context=context;this.parent=parent;this.depth=depth;this.budget=budget;}
 private static int rank(int order){int category=(order>>>16);if(category>5)throw new IllegalArgumentException("Menu order category");int[] categories={1,4,5,3,2,0};return categories[category]<<16|order&65535;}
 public MenuItem add(CharSequence title){return add(0,0,0,title);}public MenuItem add(int title){return add(context.getString(title));}public MenuItem add(int group,int id,int order,int title){return add(group,id,order,context.getString(title));}
 public MenuItem add(int group,int id,int order,CharSequence title){int ordering=rank(order);if(budget.items>=4096)throw new IllegalStateException("Menu item limit");Item item=new Item(this,group,id,order,ordering,title,currentInfo);int i=items.size();while(i>0&&items.get(i-1).ordering>ordering)i--;items.add(i,item);budget.items++;return item;}
 public SubMenu addSubMenu(CharSequence title){return addSubMenu(0,0,0,title);}public SubMenu addSubMenu(int title){return addSubMenu(context.getString(title));}public SubMenu addSubMenu(int group,int id,int order,int title){return addSubMenu(group,id,order,context.getString(title));}
 public SubMenu addSubMenu(int group,int id,int order,CharSequence title){if(depth>=16)throw new IllegalStateException("Submenu depth limit");Item item=(Item)add(group,id,order,title);item.submenu=new Nested(this,item);return item.submenu;}
 public int addIntentOptions(int group,int id,int order,android.content.ComponentName caller,Intent[] specifics,Intent intent,int flags,MenuItem[] out){throw new UnsupportedOperationException("Menu intent resolution is not implemented");}
 private void dispose(Item item){if(item.submenu!=null)item.submenu.clear();budget.items--;}
 public void removeItem(int id){for(int i=0;i<items.size();i++)if(items.get(i).id==id){dispose(items.remove(i));return;}}
 public void removeGroup(int group){for(int i=items.size()-1;i>=0;i--)if(items.get(i).group==group)dispose(items.remove(i));}
 public void clear(){for(Item item:items)dispose(item);items.clear();}
 public void setGroupCheckable(int group,boolean checkable,boolean exclusive){for(Item item:items)if(item.group==group){item.checkable=checkable;item.exclusive=exclusive;}}
 public void setGroupVisible(int group,boolean visible){for(Item item:items)if(item.group==group)item.visible=visible;}
 public void setGroupEnabled(int group,boolean enabled){for(Item item:items)if(item.group==group)item.enabled=enabled;}
 public boolean hasVisibleItems(){for(Item item:items)if(item.visible)return true;return false;}
 public MenuItem findItem(int id){for(Item item:items){if(item.id==id)return item;if(item.submenu!=null){MenuItem found=item.submenu.findItem(id);if(found!=null)return found;}}return null;}
 public int size(){return items.size();}public MenuItem getItem(int index){return items.get(index);}
 public void close(){closed=true;if(parent!=null)parent.close();}
 public boolean performShortcut(int key,KeyEvent event,int flags){throw new UnsupportedOperationException("Menu keyboard shortcuts are not implemented");}
 public boolean isShortcutKey(int key,KeyEvent event){throw new UnsupportedOperationException("Menu keyboard shortcuts are not implemented");}
 public void setQwertyMode(boolean value){throw new UnsupportedOperationException("Menu keyboard shortcuts are not implemented");}
 private boolean dispatch(Item item){if(selection!=null&&selection.onMenuItemClick(item))return true;return parent!=null&&parent.dispatch(item);}
 public boolean performIdentifierAction(int id,int flags){if((flags&~3)!=0)throw new IllegalArgumentException("Menu action flags");Item item=(Item)findItem(id);if(item==null||!item.enabled)return false;
  boolean handled=item.listener!=null&&item.listener.onMenuItemClick(item);if(!handled)handled=item.owner.dispatch(item);
  if(!handled&&item.intent!=null)throw new UnsupportedOperationException("Menu activity intent dispatch is not implemented");
  if(item.submenu!=null)throw new UnsupportedOperationException("Submenu presentation is not implemented");
  if((flags&FLAG_PERFORM_NO_CLOSE)==0||(flags&FLAG_ALWAYS_PERFORM_CLOSE)!=0)close();return handled;
 }
 void clearHeaderData(){headerTitle=null;headerIcon=null;headerView=null;}
 static final class Nested extends MenuModel implements SubMenu {
  final Item item;Nested(MenuModel parent,Item item){super(parent.context,parent,parent.depth+1,parent.budget);this.item=item;headerTitle=item.title;}
  public SubMenu setHeaderTitle(int id){return setHeaderTitle(context.getString(id));}public SubMenu setHeaderTitle(CharSequence title){headerView=null;headerTitle=title;return this;}
  public SubMenu setHeaderIcon(int id){return setHeaderIcon(context.getDrawable(id));}public SubMenu setHeaderIcon(Drawable icon){headerView=null;headerIcon=icon;return this;}
  public SubMenu setHeaderView(View view){headerTitle=null;headerIcon=null;headerView=view;return this;}public void clearHeader(){clearHeaderData();}
  public SubMenu setIcon(int id){item.setIcon(id);return this;}public SubMenu setIcon(Drawable icon){item.setIcon(icon);return this;}public MenuItem getItem(){return item;}
 }
 static final class Item implements MenuItem {
  final MenuModel owner;final int group,id,order,ordering;final ContextMenu.ContextMenuInfo info;CharSequence title,condensed;Drawable icon;Intent intent;boolean checkable,checked,exclusive,visible=true,enabled=true;Nested submenu;OnMenuItemClickListener listener;
  android.content.res.ColorStateList iconTint;android.graphics.PorterDuff.Mode iconTintMode;boolean hasIconTint,hasIconTintMode;
  private void applyIconTint(){if(icon!=null&&(hasIconTint||hasIconTintMode)){icon=icon.mutate();if(hasIconTint)icon.setTintList(iconTint);if(hasIconTintMode)icon.setTintMode(iconTintMode);}}
  public MenuItem setIconTintList(android.content.res.ColorStateList value){iconTint=value;hasIconTint=true;applyIconTint();return this;}
  public android.content.res.ColorStateList getIconTintList(){return iconTint;}
  public MenuItem setIconTintMode(android.graphics.PorterDuff.Mode value){iconTintMode=value;hasIconTintMode=true;applyIconTint();return this;}
  public android.graphics.PorterDuff.Mode getIconTintMode(){return iconTintMode;}
  CharSequence contentDescription,tooltipText;
  public MenuItem setContentDescription(CharSequence value){contentDescription=value;return this;}public CharSequence getContentDescription(){return contentDescription;}
  public MenuItem setTooltipText(CharSequence value){tooltipText=value;return this;}public CharSequence getTooltipText(){return tooltipText;}
  View actionView;OnActionExpandListener actionListener;
  public MenuItem setActionView(View view){actionView=view;if(view!=null&&view.getId()==View.NO_ID&&id>0)view.setId(id);return this;}
  public MenuItem setActionView(int resource){android.widget.LinearLayout parent=new android.widget.LinearLayout(owner.context);return setActionView(LayoutInflater.from(owner.context).inflate(resource,parent,false));}
  public View getActionView(){return actionView;}
  public MenuItem setOnActionExpandListener(OnActionExpandListener listener){actionListener=listener;return this;}
  // Host menus have no action-bar presenter; they must not report a displayed expansion.
  public boolean expandActionView(){if((showAsAction&SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)==0||actionView==null)return false;if(actionListener!=null&&!actionListener.onMenuItemActionExpand(this))return false;return false;}
  public boolean collapseActionView(){if((showAsAction&SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)==0)return false;if(actionView==null)return true;if(actionListener!=null&&!actionListener.onMenuItemActionCollapse(this))return false;return false;}
  public boolean isActionViewExpanded(){return false;}
  int showAsAction;public void setShowAsAction(int flags){if((flags&~15)!=0||(flags&3)==3)throw new IllegalArgumentException("Menu action placement flags");showAsAction=flags;}public MenuItem setShowAsActionFlags(int flags){setShowAsAction(flags);return this;}
  Item(MenuModel owner,int group,int id,int order,int ordering,CharSequence title,ContextMenu.ContextMenuInfo info){this.owner=owner;this.group=group;this.id=id;this.order=order;this.ordering=ordering;this.title=title;this.info=info;}
  public int getItemId(){return id;}public int getGroupId(){return group;}public int getOrder(){return order;}
  public MenuItem setTitle(CharSequence title){this.title=title;if(submenu!=null)submenu.setHeaderTitle(title);return this;}public MenuItem setTitle(int id){return setTitle(owner.context.getString(id));}public CharSequence getTitle(){return title;}
  public MenuItem setTitleCondensed(CharSequence value){condensed=value;return this;}public CharSequence getTitleCondensed(){return condensed==null?title:condensed;}
  public MenuItem setIcon(Drawable icon){this.icon=icon;applyIconTint();return this;}public MenuItem setIcon(int id){return setIcon(id==0?null:owner.context.getDrawable(id));}public Drawable getIcon(){return icon;}
  public MenuItem setIntent(Intent intent){this.intent=intent;return this;}public Intent getIntent(){return intent;}
  public MenuItem setCheckable(boolean value){checkable=value;return this;}public boolean isCheckable(){return checkable;}
  public MenuItem setChecked(boolean value){if(exclusive){for(Item item:owner.items)if(item.group==group&&item.exclusive&&item.checkable)item.checked=item==this;}else checked=value;return this;}public boolean isChecked(){return checked;}
  public MenuItem setVisible(boolean value){visible=value;return this;}public boolean isVisible(){return visible;}public MenuItem setEnabled(boolean value){enabled=value;return this;}public boolean isEnabled(){return enabled;}
  public boolean hasSubMenu(){return submenu!=null;}public SubMenu getSubMenu(){return submenu;}public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener value){listener=value;return this;}public ContextMenu.ContextMenuInfo getMenuInfo(){return info;}
 }
}
