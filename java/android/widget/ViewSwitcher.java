// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Two factory-created views, with the next view available for content updates. */
public class ViewSwitcher extends ViewAnimator {
 public interface ViewFactory {View makeView();}
 private ViewFactory factory;
 public ViewSwitcher(Context context){super(context);}
 public ViewSwitcher(Context context,AttributeSet attrs){super(context,attrs);}
 @Override public void addView(View child,int index,ViewGroup.LayoutParams params){if(getChildCount()>=2)throw new IllegalStateException("ViewSwitcher can host only two views");super.addView(child,index,params);}
 public View getNextView(){return getChildAt(getDisplayedChild()==0?1:0);}
 public void setFactory(ViewFactory value){if(value==null)throw new NullPointerException("factory");factory=value;for(int i=0;i<2;i++){View view=factory.makeView();if(view==null)throw new IllegalStateException("ViewFactory returned null");addView(view);}}
 public void reset(){resetFirstView();for(int i=0;i<getChildCount();i++)getChildAt(i).setVisibility(GONE);}
}
