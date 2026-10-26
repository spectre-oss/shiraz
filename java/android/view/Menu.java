// SPDX-License-Identifier: GPL-3.0-only
package android.view;
public interface Menu {
 int NONE=0,FIRST=1,CATEGORY_CONTAINER=0x10000,CATEGORY_SYSTEM=0x20000,CATEGORY_SECONDARY=0x30000,CATEGORY_ALTERNATIVE=0x40000;
 int FLAG_APPEND_TO_GROUP=1,FLAG_PERFORM_NO_CLOSE=1,FLAG_ALWAYS_PERFORM_CLOSE=2;
 MenuItem add(CharSequence title);MenuItem add(int title);MenuItem add(int group,int id,int order,CharSequence title);MenuItem add(int group,int id,int order,int title);
 SubMenu addSubMenu(CharSequence title);SubMenu addSubMenu(int title);SubMenu addSubMenu(int group,int id,int order,CharSequence title);SubMenu addSubMenu(int group,int id,int order,int title);
 int addIntentOptions(int group,int id,int order,android.content.ComponentName caller,android.content.Intent[] specifics,android.content.Intent intent,int flags,MenuItem[] out);
 void removeItem(int id);void removeGroup(int group);void clear();void setGroupCheckable(int group,boolean checkable,boolean exclusive);void setGroupVisible(int group,boolean visible);void setGroupEnabled(int group,boolean enabled);
 boolean hasVisibleItems();MenuItem findItem(int id);int size();MenuItem getItem(int index);void close();boolean performShortcut(int key,KeyEvent event,int flags);boolean isShortcutKey(int key,KeyEvent event);boolean performIdentifierAction(int id,int flags);void setQwertyMode(boolean enabled);
}
