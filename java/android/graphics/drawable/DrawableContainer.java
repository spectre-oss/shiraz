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
 private BlendMode tintBlendMode;
 private DrawableContainerState containerState;
 private boolean hasFilter,hasTint,hasTintMode,hasTintBlendMode,dither=true,variablePadding,constantSize,mirrored;
 private Rect hotspot;
 protected final int addChild(Drawable child){if(children.size()>=256)throw new IllegalArgumentException("Drawable child limit");child.setVisible(false,true);child.setCallback(this);children.add(child);return children.size()-1;}
 public boolean selectDrawable(int which){
  if(which==index)return false;
  Drawable next=which>=0&&which<children.size()?children.get(which):null;
  if(next!=null){next.setAlpha(alpha);next.setDither(dither);if(hasFilter)next.setColorFilter(filter);else{if(hasTintBlendMode)next.setTintMode(tintBlendMode==null?null:tintBlendMode.shirazPorterDuff());else if(hasTintMode)next.setTintMode(tintMode);if(hasTint)next.setTintList(tint);}next.setAutoMirrored(mirrored);next.setLayoutDirection(getLayoutDirection());next.setState(getState());next.setLevel(getLevel());next.setBounds(getBounds());if(hotspot!=null)next.setHotspotBounds(hotspot.left,hotspot.top,hotspot.right,hotspot.bottom);}
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
 @Override public Insets getOpticalInsets(){return current==null?Insets.NONE:current.getOpticalInsets();}
 public boolean getPadding(Rect out){out.set(0,0,0,0);if(variablePadding)return current!=null&&current.getPadding(out);Rect p=new Rect();boolean found=false;for(Drawable d:children){found|=d.getPadding(p);out.left=Math.max(out.left,p.left);out.top=Math.max(out.top,p.top);out.right=Math.max(out.right,p.right);out.bottom=Math.max(out.bottom,p.bottom);}return found;}
 public void getOutline(Outline outline){if(current!=null)current.getOutline(outline);else super.getOutline(outline);}
 public void setDither(boolean value){dither=value;if(current!=null)current.setDither(value);}
 public void setTintBlendMode(BlendMode value){tintBlendMode=value;hasTintBlendMode=true;hasTintMode=false;if(current!=null&&!hasFilter)current.setTintMode(value==null?null:value.shirazPorterDuff());}
 public int getOpacity(){if(current==null||!current.isVisible())return PixelFormat.TRANSPARENT;int result=children.get(0).getOpacity();for(int i=1;i<children.size();i++)result=resolveOpacity(result,children.get(i).getOpacity());return result;}
 public int getChangingConfigurations(){int flags=super.getChangingConfigurations();if(containerState!=null)flags|=containerState.getChangingConfigurations();for(Drawable d:children)flags|=d.getChangingConfigurations();return flags;}
 public ConstantState getConstantState(){if(containerState!=null&&containerState.canConstantState()){containerState.changingConfigurations=getChangingConfigurations();return containerState;}return null;}
 protected void setConstantState(DrawableContainerState state){
  containerState=state;
  if(state!=null){
   variablePadding=state.variablePadding;
   constantSize=state.constantSize;
   children.clear();
   int count=state.getChildCount();
   for(int i=0;i<count;i++){
    Drawable child=state.getChild(i);
    if(child!=null){child.setVisible(false,true);child.setCallback(this);children.add(child);}
   }
  }
 }
 public void invalidateDrawable(Drawable who){if(who==current)invalidateSelf();}
 public void scheduleDrawable(Drawable who,Runnable task,long when){if(who==current)scheduleSelf(task,when);}
 public void unscheduleDrawable(Drawable who,Runnable task){if(who==current)unscheduleSelf(task);}

 public abstract static class DrawableContainerState extends ConstantState {
  final DrawableContainer owner;
  int changingConfigurations;
  int childrenChangingConfigurations;
  Drawable[] drawables;
  int numChildren;
  boolean variablePadding;
  Rect constantPadding;
  boolean checkedPadding;
  boolean constantSize;
  boolean computedConstantSize;
  int constantWidth;
  int constantHeight;
  int constantMinimumWidth;
  int constantMinimumHeight;
  int enterFadeDuration;
  int exitFadeDuration;
  boolean checkedOpacity;
  int opacity;
  boolean checkedStateful;
  boolean stateful;
  boolean checkedConstantState;
  boolean canConstantState;

  protected DrawableContainerState(DrawableContainerState orig,DrawableContainer owner,android.content.res.Resources res){
   this.owner=owner;
   if(orig!=null){
    changingConfigurations=orig.changingConfigurations;
    childrenChangingConfigurations=orig.childrenChangingConfigurations;
    variablePadding=orig.variablePadding;
    constantSize=orig.constantSize;
    enterFadeDuration=orig.enterFadeDuration;
    exitFadeDuration=orig.exitFadeDuration;
    numChildren=orig.numChildren;
    drawables=new Drawable[orig.drawables.length];
    for(int i=0;i<numChildren;i++){
     Drawable d=orig.drawables[i];
     if(d!=null){
      ConstantState cs=d.getConstantState();
      drawables[i]=cs!=null?(res!=null?cs.newDrawable(res):cs.newDrawable()):d;
     }
    }
   }else{
    drawables=new Drawable[10];
    numChildren=0;
   }
  }

  @Override
  public int getChangingConfigurations(){return changingConfigurations|childrenChangingConfigurations;}

  public final int addChild(Drawable dr){
   int pos=numChildren;
   if(pos>=drawables.length)growArray(pos,pos+10);
   if(dr!=null){
    dr.setVisible(false,true);
    if(owner!=null)dr.setCallback(owner);
    childrenChangingConfigurations|=dr.getChangingConfigurations();
   }
   drawables[pos]=dr;
   numChildren++;
   checkedOpacity=false;
   checkedStateful=false;
   checkedPadding=false;
   computedConstantSize=false;
   return pos;
  }

  public final int getChildCount(){return numChildren;}
  public final Drawable[] getChildren(){return drawables;}
  public final Drawable getChild(int index){return index>=0&&index<numChildren?drawables[index]:null;}

  @Override
  public boolean canApplyTheme(){
   for(int i=0;i<numChildren;i++)if(drawables[i]!=null&&drawables[i].canApplyTheme())return true;
   return false;
  }

  public final void setVariablePadding(boolean variable){variablePadding=variable;}

  public final Rect getConstantPadding(){
   if(variablePadding)return null;
   if(constantPadding!=null||checkedPadding)return constantPadding;
   Rect r=null;
   Rect t=new Rect();
   for(int i=0;i<numChildren;i++){
    if(drawables[i]!=null&&drawables[i].getPadding(t)){
     if(r==null)r=new Rect(0,0,0,0);
     if(t.left>r.left)r.left=t.left;
     if(t.top>r.top)r.top=t.top;
     if(t.right>r.right)r.right=t.right;
     if(t.bottom>r.bottom)r.bottom=t.bottom;
    }
   }
   checkedPadding=true;
   constantPadding=r;
   return r;
  }

  public final void setConstantSize(boolean constant){constantSize=constant;}
  public final boolean isConstantSize(){return constantSize;}

  public final int getConstantWidth(){if(!computedConstantSize)computeConstantSize();return constantWidth;}
  public final int getConstantHeight(){if(!computedConstantSize)computeConstantSize();return constantHeight;}
  public final int getConstantMinimumWidth(){if(!computedConstantSize)computeConstantSize();return constantMinimumWidth;}
  public final int getConstantMinimumHeight(){if(!computedConstantSize)computeConstantSize();return constantMinimumHeight;}

  protected void computeConstantSize(){
   computedConstantSize=true;
   constantWidth=constantHeight=-1;
   constantMinimumWidth=constantMinimumHeight=0;
   for(int i=0;i<numChildren;i++){
    Drawable dr=drawables[i];
    if(dr==null)continue;
    int s=dr.getIntrinsicWidth();if(s>constantWidth)constantWidth=s;
    s=dr.getIntrinsicHeight();if(s>constantHeight)constantHeight=s;
    s=dr.getMinimumWidth();if(s>constantMinimumWidth)constantMinimumWidth=s;
    s=dr.getMinimumHeight();if(s>constantMinimumHeight)constantMinimumHeight=s;
   }
  }

  public final void setEnterFadeDuration(int duration){enterFadeDuration=duration;}
  public final int getEnterFadeDuration(){return enterFadeDuration;}
  public final void setExitFadeDuration(int duration){exitFadeDuration=duration;}
  public final int getExitFadeDuration(){return exitFadeDuration;}

  public final int getOpacity(){
   if(checkedOpacity)return opacity;
   int op=numChildren>0&&drawables[0]!=null?drawables[0].getOpacity():PixelFormat.TRANSPARENT;
   for(int i=1;i<numChildren;i++)if(drawables[i]!=null)op=Drawable.resolveOpacity(op,drawables[i].getOpacity());
   opacity=op;
   checkedOpacity=true;
   return op;
  }

  public final boolean isStateful(){
   if(checkedStateful)return stateful;
   boolean st=false;
   for(int i=0;i<numChildren;i++)if(drawables[i]!=null&&drawables[i].isStateful()){st=true;break;}
   stateful=st;
   checkedStateful=true;
   return st;
  }

  public void growArray(int oldSize,int newSize){
   Drawable[] newDrawables=new Drawable[newSize];
   System.arraycopy(drawables,0,newDrawables,0,Math.min(oldSize,newSize));
   drawables=newDrawables;
  }

  public synchronized boolean canConstantState(){
   if(checkedConstantState)return canConstantState;
   checkedConstantState=true;
   for(int i=0;i<numChildren;i++){
    if(drawables[i]!=null&&drawables[i].getConstantState()==null){canConstantState=false;return false;}
   }
   canConstantState=true;
   return true;
  }
 }
}
