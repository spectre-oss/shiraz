// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.view.Gravity;

/** Ordered drawable composition with per-layer bounds, gravity and callback propagation. */
public class LayerDrawable extends Drawable implements Drawable.Callback {
    public static final int PADDING_MODE_NEST=0,PADDING_MODE_STACK=1;
    private static final class Layer {
        Drawable child; int id=-1,left,top,right,bottom,start=Integer.MIN_VALUE,end=Integer.MIN_VALUE,width=-1,height=-1,gravity;
    }
    private final Layer[] layers;
    private int paddingMode;
    private ColorFilter filter;
    public LayerDrawable(Drawable[] values) {
        if(values==null)throw new NullPointerException("layers"); if(values.length>256)throw new IllegalArgumentException("Layer count");
        layers=new Layer[values.length];
        for(int i=0;i<values.length;i++) {layers[i]=new Layer();layers[i].child=values[i];if(values[i]!=null)values[i].setCallback(this);}
    }
    private Layer layer(int index) {if(index<0||index>=layers.length)throw new IndexOutOfBoundsException("layer");return layers[index];}
    public int getNumberOfLayers(){return layers.length;}
    public Drawable getDrawable(int index){return layer(index).child;}
    public int getId(int index){return layer(index).id;}
    public void setId(int index,int value){layer(index).id=value;}
    public Drawable findDrawableByLayerId(int id){int index=findIndexByLayerId(id);return index<0?null:layers[index].child;}
    public int findIndexByLayerId(int id){for(int i=layers.length-1;i>=0;i--)if(layers[i].id==id)return i;return -1;}
    private static void inset(int value){if(value< -8192||value>8192)throw new IllegalArgumentException("layer inset bound");}
    public void setLayerInset(int index,int left,int top,int right,int bottom){inset(left);inset(top);inset(right);inset(bottom);Layer l=layer(index);l.left=left;l.top=top;l.right=right;l.bottom=bottom;l.start=l.end=Integer.MIN_VALUE;update();}
    public void setLayerInsetRelative(int index,int start,int top,int end,int bottom){if(start!=Integer.MIN_VALUE)inset(start);inset(top);if(end!=Integer.MIN_VALUE)inset(end);inset(bottom);Layer l=layer(index);l.start=start;l.end=end;l.top=top;l.bottom=bottom;update();}
    public void setLayerSize(int index,int width,int height){if(width< -1||height< -1||width>8192||height>8192)throw new IllegalArgumentException("layer size");Layer l=layer(index);l.width=width;l.height=height;update();}
    public int getLayerWidth(int index){return layer(index).width;}
    public int getLayerHeight(int index){return layer(index).height;}
    public void setLayerGravity(int index,int gravity){layer(index).gravity=gravity;update();}
    public int getLayerGravity(int index){return layer(index).gravity;}
    public int getPaddingMode(){return paddingMode;}
    public void setPaddingMode(int value){if(value!=0&&value!=1)throw new IllegalArgumentException("padding mode");if(value!=paddingMode){paddingMode=value;update();}}
    private int left(Layer l){int value=getLayoutDirection()==1?l.end:l.start;return value==Integer.MIN_VALUE?l.left:value;}
    private int right(Layer l){int value=getLayoutDirection()==1?l.start:l.end;return value==Integer.MIN_VALUE?l.right:value;}
    private void update(){onBoundsChange(getBounds());invalidateSelf();}
    protected void onBoundsChange(Rect bounds){
        int pl=0,pt=0,pr=0,pb=0;Rect padding=new Rect(),available=new Rect(),target=new Rect();
        for(Layer l:layers)if(l.child!=null){
            available.set(bounds.left+left(l)+pl,bounds.top+l.top+pt,bounds.right-right(l)-pr,bounds.bottom-l.bottom-pb);
            int width=l.width>=0?l.width:l.child.getIntrinsicWidth(),height=l.height>=0?l.height:l.child.getIntrinsicHeight(),gravity=l.gravity;
            if(gravity==Gravity.NO_GRAVITY)gravity=(l.width<0?Gravity.FILL_HORIZONTAL:Gravity.LEFT)|(l.height<0?Gravity.FILL_VERTICAL:Gravity.TOP);
            if(width<0)width=Math.max(0,available.width());if(height<0)height=Math.max(0,available.height());
            Gravity.apply(gravity,width,height,available,target,getLayoutDirection());l.child.setBounds(target);
            if(paddingMode==PADDING_MODE_NEST){l.child.getPadding(padding);pl+=padding.left;pt+=padding.top;pr+=padding.right;pb+=padding.bottom;}
        }
    }
    public boolean getPadding(Rect out){out.set(0,0,0,0);Rect p=new Rect();for(Layer l:layers)if(l.child!=null){l.child.getPadding(p);if(paddingMode==0){out.left+=p.left;out.top+=p.top;out.right+=p.right;out.bottom+=p.bottom;}else{out.left=Math.max(out.left,p.left);out.top=Math.max(out.top,p.top);out.right=Math.max(out.right,p.right);out.bottom=Math.max(out.bottom,p.bottom);}}return out.left!=0||out.top!=0||out.right!=0||out.bottom!=0;}
    private int intrinsic(boolean horizontal){int result=-1,before=0,after=0;Rect p=new Rect();for(Layer l:layers)if(l.child!=null){int size=horizontal?l.width:l.height;if(size<0)size=horizontal?l.child.getIntrinsicWidth():l.child.getIntrinsicHeight();if(size>=0)result=Math.max(result,size+(horizontal?left(l)+right(l):l.top+l.bottom)+before+after);if(paddingMode==0){l.child.getPadding(p);before+=horizontal?p.left:p.top;after+=horizontal?p.right:p.bottom;}}return result;}
    public int getIntrinsicWidth(){return intrinsic(true);}
    public int getIntrinsicHeight(){return intrinsic(false);}
    public void draw(Canvas canvas){for(Layer l:layers)if(l.child!=null)l.child.draw(canvas);}
    public void setAlpha(int value){if(value<0||value>255)throw new IllegalArgumentException("alpha");for(Layer l:layers)if(l.child!=null)l.child.setAlpha(value);invalidateSelf();}
    public int getAlpha(){for(Layer l:layers)if(l.child!=null)return l.child.getAlpha();return 255;}
    public void setColorFilter(ColorFilter value){ColorFilter.shirazValidate(value);filter=value;for(Layer l:layers)if(l.child!=null)l.child.setColorFilter(value);invalidateSelf();}
    public void setTintList(android.content.res.ColorStateList value){for(Layer l:layers)if(l.child!=null)l.child.setTintList(value);refreshTintOwner();}
    public void setTintMode(PorterDuff.Mode value){for(Layer l:layers)if(l.child!=null)l.child.setTintMode(value);invalidateSelf();}
    public ColorFilter getColorFilter(){return filter;}
    public int getOpacity(){int result=PixelFormat.TRANSPARENT;boolean first=true;for(Layer l:layers)if(l.child!=null){result=first?l.child.getOpacity():resolveOpacity(result,l.child.getOpacity());first=false;}return result;}
    public boolean hasFocusStateSpecified(){for(Layer l:layers)if(l.child!=null&&l.child.hasFocusStateSpecified())return true;return false;}
    public boolean isStateful(){for(Layer l:layers)if(l.child!=null&&l.child.isStateful())return true;return false;}
    protected boolean onStateChange(int[] state){boolean changed=false;for(Layer l:layers)if(l.child!=null)changed|=l.child.setState(state);if(changed)update();return changed;}
    protected boolean onLevelChange(int value){boolean changed=false;for(Layer l:layers)if(l.child!=null)changed|=l.child.setLevel(value);if(changed)update();return changed;}
    public boolean onLayoutDirectionChanged(int value){for(Layer l:layers)if(l.child!=null)l.child.setLayoutDirection(value);update();return true;}
    public boolean setVisible(boolean value,boolean restart){boolean changed=super.setVisible(value,restart);for(Layer l:layers)if(l.child!=null)changed|=l.child.setVisible(value,restart);return changed;}
    public void setHotspot(float x,float y){for(Layer l:layers)if(l.child!=null)l.child.setHotspot(x,y);}
    public void setHotspotBounds(int left,int top,int right,int bottom){for(Layer l:layers)if(l.child!=null)l.child.setHotspotBounds(left,top,right,bottom);}
    public void jumpToCurrentState(){for(Layer l:layers)if(l.child!=null)l.child.jumpToCurrentState();}
    public int getChangingConfigurations(){int value=super.getChangingConfigurations();for(Layer l:layers)if(l.child!=null)value|=l.child.getChangingConfigurations();return value;}
    public void invalidateDrawable(Drawable who){invalidateSelf();}
    public void scheduleDrawable(Drawable who,Runnable task,long when){scheduleSelf(task,when);}
    public void unscheduleDrawable(Drawable who,Runnable task){unscheduleSelf(task);}
}
