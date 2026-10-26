// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** Android sound identifiers used by keyboard focus navigation. */
public class SoundEffectConstants {
 public static final int CLICK=0,NAVIGATION_LEFT=1,NAVIGATION_UP=2,NAVIGATION_RIGHT=3,NAVIGATION_DOWN=4;
 public static final int NAVIGATION_REPEAT_LEFT=5,NAVIGATION_REPEAT_UP=6,NAVIGATION_REPEAT_RIGHT=7,NAVIGATION_REPEAT_DOWN=8;
 SoundEffectConstants(){}
 public static int getContantForFocusDirection(int direction){return getConstantForFocusDirection(direction,false);}
 public static int getConstantForFocusDirection(int direction,boolean repeating){
  int sound;
  switch(direction){
   case View.FOCUS_LEFT:sound=NAVIGATION_LEFT;break;
   case View.FOCUS_BACKWARD:case View.FOCUS_UP:sound=NAVIGATION_UP;break;
   case View.FOCUS_RIGHT:sound=NAVIGATION_RIGHT;break;
   case View.FOCUS_FORWARD:case View.FOCUS_DOWN:sound=NAVIGATION_DOWN;break;
   default:throw new IllegalArgumentException("Invalid focus direction: "+direction);
  }
  return sound+(repeating?4:0);
 }
}
