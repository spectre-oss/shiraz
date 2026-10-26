// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;import android.content.pm.*;import java.util.*;
public final class ShortcutSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static Context context(final String pkg){return new ContextWrapper(null){public String getPackageName(){return pkg;}};}
 static ShortcutInfo shortcut(String id){return new ShortcutInfo.Builder(context("test"),id).setShortLabel(id).setIntent(new Intent(Intent.ACTION_VIEW).putExtra("route",17)).build();}
 public static void main(String[] args){
  android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putBoolean("legacy",true);extras.putIntArray("ids",new int[]{1,2});
  ShortcutInfo withExtras=new ShortcutInfo.Builder(context("test"),"extras").setShortLabel("extras").setIntent(new Intent(Intent.ACTION_MAIN)).setExtras(extras).build();
  extras.getIntArray("ids")[0]=99;check(withExtras.getExtras().getIntArray("ids")[0]==1,"extras input isolation");withExtras.getExtras().putBoolean("legacy",false);check(withExtras.getExtras().getBoolean("legacy"),"extras output isolation");
  android.os.PersistableBundle shallow=new android.os.PersistableBundle(extras);check(shallow.getIntArray("ids")==extras.getIntArray("ids"),"shallow copy semantics");
  android.os.Bundle source=new android.os.Bundle();android.os.Bundle nested=new android.os.Bundle();nested.putString("label","nested");source.putBundle("nested",nested);check(new android.os.PersistableBundle(source).getPersistableBundle("nested").getString("label").equals("nested"),"nested conversion");
  source.putFloat("invalid",1);try{new android.os.PersistableBundle(source);throw new AssertionError("invalid persistable type");}catch(IllegalArgumentException expected){}
  android.os.PersistableBundle cycle=new android.os.PersistableBundle();cycle.putPersistableBundle("self",cycle);try{cycle.deepCopy();throw new AssertionError("cycle copy");}catch(IllegalArgumentException expected){}
  try{android.os.PersistableBundle.EMPTY.putInt("x",1);throw new AssertionError("mutable EMPTY");}catch(UnsupportedOperationException expected){}
  ShortcutManager manager=ShortcutManager.shirazForProcess("test");check(manager.getDynamicShortcuts().isEmpty(),"initial empty");
  long beforePublication=System.currentTimeMillis();manager.setDynamicShortcuts(Arrays.asList(shortcut("a"),shortcut("b")));List<ShortcutInfo> snapshot=manager.getDynamicShortcuts();check(snapshot.size()==2&&snapshot.get(0).isDynamic(),"publication");
  long published=snapshot.get(0).getLastChangedTimestamp();check(published>=beforePublication&&published<=System.currentTimeMillis(),"publication wall clock timestamp");
  check(manager.getDynamicShortcuts().get(0).getLastChangedTimestamp()==published,"read preserves timestamp");
  ShortcutInfo update=shortcut("a");long beforeUpdate=System.currentTimeMillis();manager.addDynamicShortcuts(Arrays.asList(update));
  check(manager.getDynamicShortcuts().get(0).getLastChangedTimestamp()>=beforeUpdate,"replacement timestamp");check(snapshot.get(0).getLastChangedTimestamp()==published,"previous snapshot stable");
  android.os.UserHandle owner=snapshot.get(0).getUserHandle();
  check(owner.equals(android.os.Process.myUserHandle())&&owner.getIdentifier()==0,"shortcut publisher logical user");
  check(owner.equals(new android.os.UserHandle(0))&&owner.hashCode()==new android.os.UserHandle(0).hashCode(),"user value equality");
  check(!owner.equals(android.os.UserHandle.of(1))&&!owner.equals(null)&&!owner.equals("0"),"different user identities");
  check(shortcut("unpublished").getUserHandle().equals(owner),"publication preserves user");
  try{owner.writeToParcel(null,0);throw new AssertionError("unsupported parcel transport");}catch(UnsupportedOperationException expected){}
  Intent changed=snapshot.get(0).getIntent();changed.putExtra("route",99);check(manager.getDynamicShortcuts().get(0).getIntent().getIntExtra("route",0)==17,"intent isolation");snapshot.clear();check(manager.getDynamicShortcuts().size()==2,"list isolation");
  try{manager.setDynamicShortcuts(Arrays.asList(shortcut("x"),shortcut("x")));throw new AssertionError("duplicates");}catch(IllegalArgumentException expected){}check(manager.getDynamicShortcuts().size()==2,"transaction rollback");
  try{manager.addDynamicShortcuts(Arrays.asList(shortcut("c"),shortcut("d"),shortcut("e"),shortcut("f")));throw new AssertionError("capacity");}catch(IllegalArgumentException expected){}check(manager.getDynamicShortcuts().size()==2,"capacity rollback");
  ShortcutInfo foreign=new ShortcutInfo.Builder(context("other"),"x").setShortLabel("x").setIntent(new Intent(Intent.ACTION_MAIN)).build();try{manager.addDynamicShortcuts(Arrays.asList(foreign));throw new AssertionError("foreign");}catch(IllegalArgumentException expected){}
  manager.removeDynamicShortcuts(Arrays.asList("a","absent"));check(manager.getDynamicShortcuts().size()==1,"remove selected");manager.removeAllDynamicShortcuts();check(manager.getDynamicShortcuts().isEmpty(),"clear");check(!manager.isRequestPinShortcutSupported(),"pin capability");
  System.out.println("SHORTCUT_REGISTRY_OK");
 }
}
