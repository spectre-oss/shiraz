// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
/** Non-drawing layout spacer with minimum-size wrap-content measurement. */
public final class Space extends View {
 public Space(Context context){this(context,null);}
 public Space(Context context,AttributeSet attrs){this(context,attrs,0);}
 public Space(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public Space(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);if(getVisibility()==VISIBLE)setVisibility(INVISIBLE);}
 @Override public void draw(android.graphics.Canvas canvas){}
 @Override protected void onMeasure(int width,int height){setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(),width),resolveSize(getSuggestedMinimumHeight(),height));}
}
