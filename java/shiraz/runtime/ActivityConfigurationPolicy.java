// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.util.Properties;
/** Loaded activity's manifest configuration ownership, before lifecycle dispatch. */
public final class ActivityConfigurationPolicy {
 private final int handled, orientation;
 private int[] horizontal,vertical,smallest;
 public ActivityConfigurationPolicy(Properties metadata,String activity,int targetSdk) {
  if(metadata==null||activity==null)throw new NullPointerException();
  int count=Integer.parseInt(metadata.getProperty("activity.count","0"));
  if(count<0||count>256)throw new IllegalArgumentException("Activity declaration limit");
  int flags=0,requested=-1;boolean found=false;
  for(int i=0;i<count;i++)if(activity.equals(metadata.getProperty("activity."+i+".name"))) {
   if(found)throw new IllegalArgumentException("Duplicate activity metadata");found=true;
   flags=Integer.parseInt(metadata.getProperty("activity."+i+".config_changes","0"));
   requested=Integer.parseInt(metadata.getProperty("activity."+i+".screen_orientation","-1"));
  }
  if(!found)throw new IllegalArgumentException("Missing activity configuration metadata: "+activity);
  if(requested< -1||requested>14)throw new IllegalArgumentException("Activity orientation range");
  // Before API 13 applications implicitly handle the newly introduced size bits.
  handled=flags|(targetSdk<13?0xc00:0);orientation=requested;
 }
 /** Resource qualifiers are reported independently of the current selected configuration. */
 public void setSizeConfigurations(android.content.res.Configuration[] configurations){
  if(configurations==null){horizontal=vertical=smallest=null;return;}
  if(configurations.length>32768)throw new IllegalArgumentException("Size configuration limit");
  java.util.TreeSet<Integer> widths=new java.util.TreeSet<Integer>(),heights=new java.util.TreeSet<Integer>(),minimums=new java.util.TreeSet<Integer>();
  for(android.content.res.Configuration config:configurations){if(config==null)throw new NullPointerException("size configuration");add(widths,config.screenWidthDp);add(heights,config.screenHeightDp);add(minimums,config.smallestScreenWidthDp);}
  horizontal=array(widths);vertical=array(heights);smallest=array(minimums);
 }
 private static void add(java.util.TreeSet<Integer> values,int size){if(size<0||size>65535)throw new IllegalArgumentException("Resource size qualifier range");if(size!=0)values.add(size);}
 private static int[] array(java.util.TreeSet<Integer> values){int[] result=new int[values.size()];int i=0;for(Integer value:values)result[i++]=value;return result;}
 private static boolean crosses(int[] thresholds,int before,int after){for(int value:thresholds)if((before<value&&after>=value)||(before>=value&&after<value))return true;return false;}
 public int significantChanges(android.content.res.Configuration before,android.content.res.Configuration after,int changes){
  if(before==null||after==null)throw new NullPointerException("configuration");
  if(horizontal==null)return changes;
  if((changes&0x400)!=0&&!crosses(horizontal,before.screenWidthDp,after.screenWidthDp)&&!crosses(vertical,before.screenHeightDp,after.screenHeightDp))changes&=~0x400;
  if((changes&0x800)!=0&&!crosses(smallest,before.smallestScreenWidthDp,after.smallestScreenWidthDp))changes&=~0x800;
  return changes;
 }
 public int requestedOrientation(){return orientation;}
 public boolean requiresRecreation(int changed){return (changed&~handled)!=0;}
}
