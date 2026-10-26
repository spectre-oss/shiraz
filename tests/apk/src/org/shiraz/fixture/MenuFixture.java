// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;
public final class MenuFixture {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static final class Info implements ContextMenu.ContextMenuInfo { }
 public static final class Probe extends View {
  final Info info=new Info();Probe(Context c){super(c);}protected ContextMenu.ContextMenuInfo getContextMenuInfo(){return info;}
  protected void onCreateContextMenu(ContextMenu menu){menu.add(1,10,0,"View action");}
 }
 public static void check(Context context,ContextMenu menu){
  Probe child=new Probe(context);FrameLayout parent=new FrameLayout(context);parent.addView(child);
  final StringBuilder callbacks=new StringBuilder();child.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener(){public void onCreateContextMenu(ContextMenu menu,View v,ContextMenu.ContextMenuInfo info){check(v==child&&info==child.info,"source/info callback");check(menu.size()==1,"view callback before listener");menu.add(1,11,0,"Listener action");callbacks.append('C');}});
  parent.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener(){public void onCreateContextMenu(ContextMenu menu,View v,ContextMenu.ContextMenuInfo info){check(info==null,"parent info");menu.add(2,12,0,"Parent action");callbacks.append('P');}});
  child.createContextMenu(menu);check(callbacks.toString().equals("CP")&&menu.size()==3,"view/parent callback order");check(menu.getItem(0).getMenuInfo()==child.info&&menu.getItem(1).getMenuInfo()==child.info&&menu.getItem(2).getMenuInfo()==null,"item source metadata");
  final int[] selected={0};MenuItem action=menu.findItem(11);action.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(MenuItem item){selected[0]++;check(item.getItemId()==11,"selection item");return true;}});check(menu.performIdentifierAction(11,Menu.FLAG_PERFORM_NO_CLOSE)&&selected[0]==1,"identifier action");action.setEnabled(false);check(!menu.performIdentifierAction(11,0)&&selected[0]==1,"disabled selection");action.setEnabled(true);
  menu.setGroupCheckable(1,true,true);MenuItem first=menu.findItem(10);first.setChecked(true);check(first.isChecked()&&!action.isChecked(),"exclusive first selection");action.setChecked(true);check(!first.isChecked()&&action.isChecked(),"exclusive next selection");menu.setGroupVisible(1,false);check(!first.isVisible()&&!action.isVisible()&&menu.hasVisibleItems(),"group visibility");menu.setGroupEnabled(1,false);check(!first.isEnabled()&&!action.isEnabled(),"group enabled");
  menu.clear();MenuItem a=menu.add(0,20,10,"A"),b=menu.add(0,21,5,"B"),c=menu.add(0,22,5,"C");check(menu.getItem(0)==b&&menu.getItem(1)==c&&menu.getItem(2)==a,"stable ordering");check(a.setTitleCondensed("Short").getTitleCondensed().equals("Short")&&b.getTitleCondensed().equals("B"),"titles");
  SubMenu sub=menu.addSubMenu(3,30,0,"Submenu");sub.add(4,31,0,"Nested");check(menu.findItem(31)==sub.getItem(0)&&sub.getItem()==menu.findItem(30)&&sub.getItem().hasSubMenu(),"recursive lookup");menu.removeGroup(3);check(menu.findItem(30)==null&&menu.findItem(31)==null,"recursive removal");menu.removeItem(21);check(menu.size()==2,"item removal");
  try{menu.add(0,99,0x60000,"Invalid");throw new AssertionError("invalid category accepted");}catch(IllegalArgumentException expected){}check(menu.size()==2,"failed insertion atomic");
  child.setOnCreateContextMenuListener(null);menu.clear();child.createContextMenu(menu);check(menu.size()==2,"listener removal");
  menu.clear();MenuItem tinted=menu.add("Tint");check(tinted.setContentDescription("action")==tinted&&"action".contentEquals(tinted.getContentDescription()),"Menu accessibility label");check(tinted.setTooltipText("hint")==tinted&&"hint".contentEquals(tinted.getTooltipText()),"Menu tooltip");tinted.setContentDescription(null);tinted.setTooltipText(null);check(tinted.getContentDescription()==null&&tinted.getTooltipText()==null,"Clear menu labels");android.content.res.ColorStateList colors=android.content.res.ColorStateList.valueOf(0xff00ff00);check(tinted.setIconTintList(colors)==tinted&&tinted.getIconTintList()==colors,"Tint metadata before icon");tinted.setIconTintMode(android.graphics.PorterDuff.Mode.SRC_IN);tinted.setIcon(new android.graphics.drawable.ColorDrawable(0xffff0000));check(tinted.getIconTintMode()==android.graphics.PorterDuff.Mode.SRC_IN,"Tint mode metadata");android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);tinted.getIcon().setBounds(0,0,2,2);tinted.getIcon().draw(new android.graphics.Canvas(pixels));check(pixels.getPixel(0,0)==0xff00ff00,"Deferred icon tint pixels");tinted.setIconTintList(null);tinted.getIcon().draw(new android.graphics.Canvas(pixels));check(pixels.getPixel(0,0)==0xffff0000,"Clear icon tint");pixels.recycle();
  menu.clear();System.out.println("SHIRAZ_FIXTURE_MENU_MODEL_OK");
 }
}
