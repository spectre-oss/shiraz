// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Canvas;
import java.lang.ref.WeakReference;
/** Zero-sized placeholder replaced by a lazily inflated layout in its parent. */
public final class ViewStub extends View {
 public interface OnInflateListener {void onInflate(ViewStub stub,View inflated);}
 private int layoutResource,inflatedId=NO_ID;
 private LayoutInflater inflater;private OnInflateListener listener;private WeakReference<View> inflated;
 public ViewStub(Context context){this(context,0);}
 public ViewStub(Context context,int layout){this(context,(AttributeSet)null);layoutResource=layout;}
 public ViewStub(Context context,AttributeSet attrs){this(context,attrs,0);}
 public ViewStub(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public ViewStub(Context context,AttributeSet attrs,int style,int resource){
  super(context);shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);
  inflatedId=a.reference("inflatedId",NO_ID);layoutResource=a.reference("layout",0);setId(a.reference("id",NO_ID));
  super.setVisibility(GONE);setWillNotDraw(true);
 }
 public int getLayoutResource(){return layoutResource;}public void setLayoutResource(int value){layoutResource=value;}
 public int getInflatedId(){return inflatedId;}public void setInflatedId(int value){inflatedId=value;}
 public LayoutInflater getLayoutInflater(){return inflater;}public void setLayoutInflater(LayoutInflater value){inflater=value;}
 public void setOnInflateListener(OnInflateListener value){listener=value;}
 @Override protected void onMeasure(int width,int height){setMeasuredDimension(0,0);}
 @Override public void draw(Canvas canvas){}
 @Override protected void dispatchDraw(Canvas canvas){}
 @Override public void setVisibility(int visibility){
  if(inflated!=null){View target=inflated.get();if(target==null)throw new IllegalStateException("Inflated view is no longer referenced");target.setVisibility(visibility);}
  else{super.setVisibility(visibility);if(visibility==VISIBLE||visibility==INVISIBLE)inflate();}
 }
 public View inflate(){
  if(!(getParent() instanceof ViewGroup))throw new IllegalStateException("ViewStub requires a ViewGroup parent");
  if(layoutResource==0)throw new IllegalArgumentException("ViewStub requires a layout resource");
  ViewGroup parent=(ViewGroup)getParent();View replacement=(inflater==null?LayoutInflater.from(getContext()):inflater).inflate(layoutResource,parent,false);
  if(inflatedId!=NO_ID)replacement.setId(inflatedId);
  int index=parent.indexOfChild(this);ViewGroup.LayoutParams params=getLayoutParams();parent.removeView(this);
  if(params==null)parent.addView(replacement,index);else parent.addView(replacement,index,params);
  inflated=new WeakReference<View>(replacement);if(listener!=null)listener.onInflate(this,replacement);return replacement;
 }
}
