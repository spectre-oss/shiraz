// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
/** View-owned scrollbar geometry and frame-time fading; no separate window or widget. */
final class ScrollBars {
 final View view;
 boolean horizontal,vertical,fading=true,alwaysHorizontal,alwaysVertical;
 int size=4,style,position,delay=300,duration=250;
 long awakened=android.view.animation.AnimationUtils.currentAnimationTimeMillis();
 Drawable horizontalThumb=new ColorDrawable(0x80000000),verticalThumb=new ColorDrawable(0x80000000),horizontalTrack,verticalTrack;
 ScrollBars(View owner){view=owner;}
 boolean inset(){return (style&0x01000000)!=0;}
 boolean left(){return position==1||position==0&&view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;}
 int leftInset(){return inset()&&vertical&&left()?size:0;}int rightInset(){return inset()&&vertical&&!left()?size:0;}int bottomInset(){return inset()&&horizontal?size:0;}
 void wake(int wait){awakened=android.view.animation.AnimationUtils.currentAnimationTimeMillis()+(long)wait-delay;view.invalidate();view.postInvalidateOnAnimation();}
 void configure(shiraz.runtime.XmlAttributes a){int flags=a.integer("scrollbars",0);if((flags&~0x300)!=0)throw new IllegalArgumentException("Scrollbar flags");horizontal=(flags&0x100)!=0;vertical=(flags&0x200)!=0;view.setScrollBarStyle(a.integer("scrollbarStyle",0));view.setScrollBarSize(a.dimension("scrollbarSize",ViewConfiguration.get(view.getContext()).getScaledScrollBarSize(),false));fading=a.bool("fadeScrollbars",true);view.setScrollBarDefaultDelayBeforeFade(a.integer("scrollbarDefaultDelayBeforeFade",300));view.setScrollBarFadeDuration(a.integer("scrollbarFadeDuration",250));alwaysHorizontal=a.bool("scrollbarAlwaysDrawHorizontalTrack",false);alwaysVertical=a.bool("scrollbarAlwaysDrawVerticalTrack",false);if(a.has("scrollbarThumbHorizontal"))horizontalThumb=a.drawable("scrollbarThumbHorizontal");if(a.has("scrollbarThumbVertical"))verticalThumb=a.drawable("scrollbarThumbVertical");if(a.has("scrollbarTrackHorizontal"))horizontalTrack=a.drawable("scrollbarTrackHorizontal");if(a.has("scrollbarTrackVertical"))verticalTrack=a.drawable("scrollbarTrackVertical");wake(delay);}
 private void paint(Canvas canvas,Drawable drawable,int l,int t,int r,int b,int alpha){if(drawable==null||l>=r||t>=b)return;int saved=canvas.getSaveCount();try{drawable.setState(view.getDrawableState());drawable.setLayoutDirection(view.getLayoutDirection());drawable.setBounds(l,t,r,b);if(alpha!=255)canvas.saveLayerAlpha(new android.graphics.RectF(l,t,r,b),alpha);drawable.draw(canvas);}finally{canvas.restoreToCount(saved);}}
 void draw(Canvas canvas){if(!horizontal&&!vertical||size==0)return;long age=android.view.animation.AnimationUtils.currentAnimationTimeMillis()-awakened;int alpha=!fading||age<=delay?255:duration==0?0:(int)Math.max(0,255*(1-(age-delay)/(double)duration));if(alpha==0)return;if(fading)view.postInvalidateOnAnimation();boolean outside=(style&0x02000000)!=0;
  int left=outside?0:view.getPaddingLeft()-leftInset(),right=outside?view.getWidth():view.getWidth()-view.getPaddingRight()+rightInset(),top=outside?0:view.getPaddingTop(),bottom=outside?view.getHeight():view.getHeight()-view.getPaddingBottom()+bottomInset();
  if(vertical){int x=left()?left:Math.max(left,right-size);axis(canvas,true,x,top,Math.min(right,x+size),bottom-(horizontal?size:0),view.computeVerticalScrollRange(),view.computeVerticalScrollExtent(),view.computeVerticalScrollOffset(),alpha);}
  if(horizontal){int x=left+(vertical&&left()?size:0);axis(canvas,false,x,Math.max(top,bottom-size),right-(vertical&&!left()?size:0),bottom,view.computeHorizontalScrollRange(),view.computeHorizontalScrollExtent(),view.computeHorizontalScrollOffset(),alpha);}
 }
 private void axis(Canvas canvas,boolean vertical,int l,int t,int r,int b,int range,int extent,int offset,int alpha){int length=vertical?b-t:r-l;if(length<=0||extent<=0||range<=0)return;boolean scrolling=range>extent;Drawable track=vertical?verticalTrack:horizontalTrack,thumb=vertical?verticalThumb:horizontalThumb;if(scrolling||(vertical?alwaysVertical:alwaysHorizontal))paint(canvas,track,l,t,r,b,alpha);if(!scrolling)return;int thumbLength=Math.min(length,Math.max(Math.min(length,2*size),(int)Math.round(length*(double)extent/range)));int travel=length-thumbLength;int start=(int)Math.round(travel*Math.max(0,Math.min((long)range-extent,offset))/(double)(range-extent));if(vertical)paint(canvas,thumb,l,t+start,r,t+start+thumbLength,alpha);else paint(canvas,thumb,l+start,t,l+start+thumbLength,b,alpha);}
}
