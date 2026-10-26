// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.content.*;import android.graphics.drawable.Icon;import java.util.*;
/** Local shortcut metadata with copied intent containers; launcher persistence and IPC are separate. */
public final class ShortcutInfo {
 private final long lastChangedTimestamp;
 private final android.os.PersistableBundle extras;
 private final String id,pkg;private final CharSequence label,longLabel,disabledMessage;private final ComponentName activity;private final Intent[] intents;private final Set<String> categories;private final Icon icon;private final int rank;private final boolean dynamic;
 private ShortcutInfo(Builder b,boolean dynamic){lastChangedTimestamp=System.currentTimeMillis();extras=b.extras==null?null:b.extras.deepCopy();id=b.id;pkg=b.pkg;label=b.label;longLabel=b.longLabel;disabledMessage=b.disabledMessage;activity=b.activity;intents=copy(b.intents);categories=b.categories==null?null:Collections.unmodifiableSet(new HashSet<String>(b.categories));icon=b.icon;rank=b.rank;this.dynamic=dynamic;}
 private static Intent[] copy(Intent[] values){if(values==null)return null;Intent[] result=new Intent[values.length];for(int i=0;i<values.length;i++)result[i]=new Intent(values[i]);return result;}
 public android.os.PersistableBundle getExtras(){return extras==null?null:extras.deepCopy();}
 public long getLastChangedTimestamp(){return lastChangedTimestamp;}
 public android.os.UserHandle getUserHandle(){return android.os.UserHandle.of(android.os.UserHandle.myUserId());}
 public String getId(){return id;}public String getPackage(){return pkg;}public ComponentName getActivity(){return activity;}
 public CharSequence getShortLabel(){return label;}public CharSequence getLongLabel(){return longLabel;}public CharSequence getDisabledMessage(){return disabledMessage;}
 public Intent getIntent(){return intents==null?null:new Intent(intents[intents.length-1]);}public Intent[] getIntents(){return copy(intents);}
 public Set<String> getCategories(){return categories;}public Icon getIcon(){return icon;}public int getRank(){return rank;}
 public boolean isDynamic(){return dynamic;}public boolean isPinned(){return false;}public boolean isDeclaredInManifest(){return false;}public boolean isImmutable(){return false;}public boolean isEnabled(){return true;}public boolean hasKeyFieldsOnly(){return false;}
 ShortcutInfo published(){return new ShortcutInfo(new Builder(this),true);}
 boolean complete(){return label!=null&&!label.toString().isEmpty()&&intents!=null;}
 public static final class Builder {
  private android.os.PersistableBundle extras;
  public Builder setExtras(android.os.PersistableBundle value){extras=value==null?null:value.deepCopy();return this;}
  private final String id,pkg;private CharSequence label,longLabel,disabledMessage;private ComponentName activity;private Intent[] intents;private Set<String> categories;private Icon icon;private int rank;
  public Builder(Context context,String id){if(context==null)throw new NullPointerException("context");if(id==null||id.isEmpty()||id.length()>1024)throw new IllegalArgumentException("shortcut id");this.id=id;pkg=context.getPackageName();}
  private Builder(ShortcutInfo s){extras=s.extras;id=s.id;pkg=s.pkg;label=s.label;longLabel=s.longLabel;disabledMessage=s.disabledMessage;activity=s.activity;intents=s.intents;categories=s.categories;icon=s.icon;rank=s.rank;}
  private static String text(CharSequence value){if(value==null||value.length()==0||value.length()>16384)throw new IllegalArgumentException("shortcut label");return value.toString();}
  public Builder setShortLabel(CharSequence value){label=text(value);return this;}public Builder setLongLabel(CharSequence value){longLabel=text(value);return this;}public Builder setDisabledMessage(CharSequence value){disabledMessage=text(value);return this;}
  public Builder setActivity(ComponentName value){if(value==null)throw new NullPointerException("activity");if(!pkg.equals(value.getPackageName()))throw new IllegalArgumentException("foreign activity");activity=value;return this;}
  public Builder setIcon(Icon value){if(value==null)throw new NullPointerException("icon");icon=value;return this;}
  public Builder setRank(int value){if(value<0)throw new IllegalArgumentException("rank");rank=value;return this;}
  public Builder setCategories(Set<String> value){if(value!=null&&value.size()>128)throw new IllegalArgumentException("categories");categories=value==null?null:new HashSet<String>(value);return this;}
  public Builder setIntent(Intent value){return setIntents(new Intent[]{value});}
  public Builder setIntents(Intent[] value){if(value==null||value.length==0||value.length>32)throw new IllegalArgumentException("intents");for(Intent intent:value)if(intent==null||intent.getAction()==null)throw new IllegalArgumentException("shortcut intent action");intents=copy(value);return this;}
  public ShortcutInfo build(){return new ShortcutInfo(this,false);}
 }
}
