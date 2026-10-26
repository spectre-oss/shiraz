// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import java.util.*;
/** Process-scoped dynamic shortcut registry. Desktop export and pinning are not implemented. */
public final class ShortcutManager {
 private final String pkg;private LinkedHashMap<String,ShortcutInfo> dynamic=new LinkedHashMap<String,ShortcutInfo>();
 private ShortcutManager(String pkg){this.pkg=Objects.requireNonNull(pkg);}
 public static ShortcutManager shirazForProcess(String pkg){return new ShortcutManager(pkg);}
 public int getMaxShortcutCountPerActivity(){return 5;}public int getIconMaxWidth(){return 96;}public int getIconMaxHeight(){return 96;}
 public boolean isRateLimitingActive(){return false;}public boolean isRequestPinShortcutSupported(){return false;}
 public synchronized List<ShortcutInfo> getDynamicShortcuts(){return new ArrayList<ShortcutInfo>(dynamic.values());}
 public List<ShortcutInfo> getPinnedShortcuts(){return new ArrayList<ShortcutInfo>();}
 public List<ShortcutInfo> getManifestShortcuts(){return new ArrayList<ShortcutInfo>();}
 private LinkedHashMap<String,ShortcutInfo> checked(List<ShortcutInfo> input,boolean replace){
  if(input==null)throw new NullPointerException("shortcuts");if(input.size()>128)throw new IllegalArgumentException("shortcut count");
  LinkedHashMap<String,ShortcutInfo> result=replace?new LinkedHashMap<String,ShortcutInfo>():new LinkedHashMap<String,ShortcutInfo>(dynamic);
  HashSet<String> ids=new HashSet<String>();for(ShortcutInfo shortcut:input){if(shortcut==null||!pkg.equals(shortcut.getPackage()))throw new IllegalArgumentException("foreign shortcut");if(!shortcut.complete())throw new IllegalArgumentException("shortcut label and intents required");if(!ids.add(shortcut.getId()))throw new IllegalArgumentException("duplicate shortcut id");result.put(shortcut.getId(),shortcut.published());}
  if(result.size()>128)throw new IllegalArgumentException("shortcut count");HashMap<Object,Integer> counts=new HashMap<Object,Integer>();for(ShortcutInfo shortcut:result.values()){Object activity=shortcut.getActivity();Integer count=counts.get(activity);int next=count==null?1:count+1;if(next>getMaxShortcutCountPerActivity())throw new IllegalArgumentException("shortcuts per activity");counts.put(activity,next);}return result;
 }
 public synchronized boolean setDynamicShortcuts(List<ShortcutInfo> values){dynamic=checked(values,true);return true;}
 public synchronized boolean addDynamicShortcuts(List<ShortcutInfo> values){dynamic=checked(values,false);return true;}
 public synchronized void removeDynamicShortcuts(List<String> ids){if(ids==null)throw new NullPointerException("ids");if(ids.size()>128)throw new IllegalArgumentException("ids");for(String id:ids)if(id==null)throw new NullPointerException("id");for(String id:ids)dynamic.remove(id);}
 public synchronized void removeAllDynamicShortcuts(){dynamic.clear();}
}
