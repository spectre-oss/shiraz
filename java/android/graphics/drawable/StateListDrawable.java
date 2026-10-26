// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import java.util.ArrayList;
/** Ordered positive/negative state matching with wildcard fallback. */
public class StateListDrawable extends DrawableContainer {
 private final ArrayList<int[]> states=new ArrayList<int[]>();
 public StateListDrawable(){}
 public void addState(int[] specification,Drawable drawable){if(drawable==null)return;if(specification==null)throw new NullPointerException("states");if(specification.length>1024)throw new IllegalArgumentException("State limit");int[] copy=specification.clone();addChild(drawable);states.add(copy);onStateChange(getState());}
 public boolean hasFocusStateSpecified(){for(int[] spec:states)for(int state:spec)if(state==16842908||state==-16842908)return true;return false;}
 public boolean isStateful(){return true;}
 private static boolean matches(int[] spec,int[] state){for(int item:spec){if(item==0)break;boolean present=false;for(int actual:state){if(actual==0)break;if(actual==(item>0?item:-item)){present=true;break;}}if(present!=(item>0))return false;}return true;}
 protected boolean onStateChange(int[] state){boolean changed=super.onStateChange(state);int selected=-1;for(int i=0;i<states.size();i++)if(matches(states.get(i),state)){selected=i;break;}if(selected<0)for(int i=0;i<states.size();i++)if(matches(states.get(i),new int[0])){selected=i;break;}return selectDrawable(selected)||changed;}
}
