// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Configuration;
public class ConfigurationDiffSmoke {
 public static void main(String[] args){
  Configuration original=new Configuration();original.orientation=1;original.screenWidthDp=360;original.screenHeightDp=640;original.uiMode=0x11;
  Configuration delta=new Configuration();delta.orientation=2;delta.screenWidthDp=640;delta.screenHeightDp=360;
  if(original.diff(delta)!=0x480||original.orientation!=1||original.screenWidthDp!=360)throw new AssertionError("nonmutating diff");
  Configuration merged=new Configuration(original);if(merged.updateFrom(delta)!=original.diff(delta)||merged.orientation!=2)throw new AssertionError("merge parity");
  Configuration nightOnly=new Configuration();nightOnly.uiMode=0x10;
  if(original.diff(nightOnly)!=0)throw new AssertionError("unchanged partial mode");
  nightOnly.uiMode=0x20;if(original.diff(nightOnly)!=0x200)throw new AssertionError("night mode change");
  if(original.diff(new Configuration())!=0)throw new AssertionError("undefined delta");
  try{original.diff(null);throw new AssertionError("null");}catch(NullPointerException expected){}
  System.out.println("CONFIGURATION_DIFF_OK");
 }
}
