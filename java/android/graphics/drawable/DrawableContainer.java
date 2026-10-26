// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.content.res.ColorStateList;
import java.util.ArrayList;
/** Local drawable selection with child lifecycle forwarding; timed fades are unsupported. */
public class DrawableContainer extends Drawable implements Drawable.Callback {
 protected final ArrayList<Drawable> children=new ArrayList<Drawable>();
 private Drawable current;
 private int index=-1,alpha=255;
 private ColorFilter filter;
 private ColorStateList tint;
 private PorterDuff.Mode tintMode;
 private boolean hasFilter,hasTint,hasTintMode,variablePadding,constantSize,mirrored;
 private Rect hotspot;
 protected final int addChild(Drawable child){if(children.size()>=256)throw new IllegalArgumentException("Drawable child limit");child.setVisible(false,true);child.setCallback(this);children.add(child);return children.size()-1;}
 public boolean selectDrawable(int which){
  if(which==index)return false;
  Drawable next=which>=0&&which<children.size()?children.get(which):null;
  if(next!=null){next.setAlpha(alpha);if(hasFilter)next.setColorFilter(filter);else{if(hasTintMode)next.setTintMode(tintMode);if(hasTint)next.setTintList(tint);}next.setAutoMirrored(mirrored);next.setLayoutDirection(getLayoutDirection());next.setState(getState());next.setLevel(getLevel());next.setBounds(getBounds());if(hotspot!=null)next.setHotspotBounds(hotspot.left,hotspot.top,hotspot.right,hotspot.bottom);}
  if(current!=null)current.setVisible(false,false);current=next;index=next==null?-1:which;if(next!=null)next.setVisible(isVisible(),true);invalidateSelf();return true;
 }
 public Drawable getCurrent(){return current;}
 public void draw(Canvas canvas){if(current!=null)current.draw(canvas);}
 protected void onBoundsChange(Rect bounds){if(current!=null)current.setBounds(bounds);}
 protected boolean onStateChange(int[] state){return current!=null&&current.setState(state);}
 protected boolean onLevelChange(int level){return current!=null&&current.setLevel(level);}
 public boolean hasFocusStateSpecified(){for(Drawable d:children)if(d.hasFocusStateSpecified())return true;return false;}
 public boolean isStateful(){for(Drawable d:children)if(d.isStateful())return true;return false;}
 public void setAlpha(int value){if(value<0||value>255)throw new IllegalArgumentException("Alpha");if(current!=null)current.setAlpha(value);alpha=value;invalidateSelf();}
 public int getAlpha(){return alpha;}
 public void setColorFilter(ColorFilter value){ColorFilter.shirazValidate(value);if(current!=null)current.setColorFilter(value);filter=value;hasFilter=true;invalidateSelf();}
 public ColorFilter getColorFilter(){return filter;}
 public void setTintList(ColorStateList value){if(current!=null&&!hasFilter)current.setTintList(value);tint=value;hasTint=true;refreshTintOwner();}
 public void setTintMode(PorterDuff.Mode value){if(current!=null&&!hasFilter)current.setTintMode(value);tintMode=value;hasTintMode=true;}
 public void setAutoMirrored(boolean value){if(current!=null)current.setAutoMirrored(value);mirrored=value;}
 public boolean isAutoMirrored(){return mirrored;}
 public boolean onLayoutDirectionChanged(int value){boolean changed=false;for(Drawable d:children)changed|=d.setLayoutDirection(value);return changed;}
 public boolean setVisible(boolean value,boolean restart){boolean changed=super.setVisible(value,restart);if(current!=null)changed|=current.setVisible(value,restart);return changed;}
 public void setHotspot(float x,float y){if(current!=null)current.setHotspot(x,y);}
 public void setHotspotBounds(int l,int t,int r,int b){if(hotspot==null)hotspot=new Rect();hotspot.set(l,t,r,b);if(current!=null)current.setHotspotBounds(l,t,r,b);}
 public void getHotspotBounds(Rect out){if(hotspot==null)super.getHotspotBounds(out);else out.set(hotspot);}
 public void jumpToCurrentState(){if(current!=null)current.jumpToCurrentState();}
 public void setEnterFadeDuration(int ms){if(ms<0)throw new IllegalArgumentException("Fade duration");if(ms!=0)throw new UnsupportedOperationException("Drawable selection fades are not implemented");}
 public void setExitFadeDuration(int ms){if(ms<0)throw new IllegalArgumentException("Fade duration");if(ms!=0)throw new UnsupportedOperationException("Drawable selection fades are not implemented");}
 public final void shirazSetSizing(boolean variable,boolean constant){variablePadding=variable;constantSize=constant;}
 private int size(int kind){int result=kind<2?-1:0;if(!constantSize)return current==null?result:dimension(current,kind);for(Drawable d:children)result=Math.max(result,dimension(d,kind));return result;}
 private static int dimension(Drawable d,int kind){switch(kind){case 0:return d.getIntrinsicWidth();case 1:return d.getIntrinsicHeight();case 2:return d.getMinimumWidth();default:return d.getMinimumHeight();}}
 public int getIntrinsicWidth(){return size(0);}public int getIntrinsicHeight(){return size(1);}public int getMinimumWidth(){return size(2);}public int getMinimumHeight(){return size(3);}
 public boolean getPadding(Rect out){out.set(0,0,0,0);if(variablePadding)return current!=null&&current.getPadding(out);Rect p=new Rect();boolean found=false;for(Drawable d:children){found|=d.getPadding(p);out.left=Math.max(out.left,p.left);out.top=Math.max(out.top,p.top);out.right=Math.max(out.right,p.right);out.bottom=Math.max(out.bottom,p.bottom);}return found;}
 public int getOpacity(){if(current==null||!current.isVisible())return PixelFormat.TRANSPARENT;int result=children.get(0).getOpacity();for(int i=1;i<children.size();i++)result=resolveOpacity(result,children.get(i).getOpacity());return result;}
 public int getChangingConfigurations(){int flags=super.getChangingConfigurations();for(Drawable d:children)flags|=d.getChangingConfigurations();return flags;}
 public void invalidateDrawable(Drawable who){if(who==current)invalidateSelf();}
 public void scheduleDrawable(Drawable who,Runnable task,long when){if(who==current)scheduleSelf(task,when);}
 public void unscheduleDrawable(Drawable who,Runnable task){if(who==current)unscheduleSelf(task);}
}
