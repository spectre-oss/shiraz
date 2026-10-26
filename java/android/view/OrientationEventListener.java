// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** Desktop hosts expose display rotation but no physical orientation sensor. */
public abstract class OrientationEventListener {
 public static final int ORIENTATION_UNKNOWN=-1;
 public OrientationEventListener(android.content.Context context){this(context,3);}
 public OrientationEventListener(android.content.Context context,int rate){if(context==null)throw new NullPointerException("context");}
 public boolean canDetectOrientation(){return false;}
 public void enable(){}
 public void disable(){}
 public abstract void onOrientationChanged(int orientation);
}
