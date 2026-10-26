// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.content.res.ColorStateList;

/** CPU shape drawable with cached gradient fills. ConstantState recreation is not implemented. */
public class GradientDrawable extends Drawable {
 public static final int RECTANGLE=0,OVAL=1,LINE=2,RING=3,LINEAR_GRADIENT=0,RADIAL_GRADIENT=1,SWEEP_GRADIENT=2;
 public enum Orientation {TOP_BOTTOM,TR_BL,RIGHT_LEFT,BR_TL,BOTTOM_TOP,BL_TR,LEFT_RIGHT,TL_BR}
 private int shape=RECTANGLE,type=LINEAR_GRADIENT,alpha=255,width=-1,height=-1,strokeWidth;
 private ColorStateList fill=ColorStateList.valueOf(0),stroke=ColorStateList.valueOf(0),tint;
 private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;private ColorFilter filter;
 private Orientation orientation=Orientation.TOP_BOTTOM;
 private float radius,centerX=.5f,centerY=.5f,gradientRadius,dashWidth,dashGap;
 private int radiusType;
 private int innerRadius=-1,thickness=-1;private float innerRadiusRatio=3,thicknessRatio=9;private boolean useLevelForShape=true;
 private float[] radii,positions;private int[] colors;private boolean useLevel,dither;
 private final Rect padding=new Rect();private Bitmap cached;private int cachedW,cachedH;
 public GradientDrawable(){}
 public GradientDrawable(Orientation direction,int[] colors){setOrientation(direction);setColors(colors);}
 private static void dimension(float value){if(!Float.isFinite(value)||value<0||value>8192)throw new IllegalArgumentException("Shape dimension");}
 private void clearCache(){if(cached!=null){cached.recycle();cached=null;}}
 private void changed(){clearCache();invalidateSelf();}
 public void setShape(int value){if(value<0||value>3)throw new IllegalArgumentException("Shape");shape=value;changed();}
 public int getShape(){return shape;}
 private static void ratio(float value){if(!Float.isFinite(value)||value<=0)throw new IllegalArgumentException("Ring ratio must be positive and finite");}
 public void setInnerRadius(int value){dimension(value);innerRadius=value;changed();}public int getInnerRadius(){return innerRadius;}
 public void setThickness(int value){dimension(value);thickness=value;changed();}public int getThickness(){return thickness;}
 public void setInnerRadiusRatio(float value){ratio(value);innerRadiusRatio=value;changed();}public float getInnerRadiusRatio(){return innerRadiusRatio;}
 public void setThicknessRatio(float value){ratio(value);thicknessRatio=value;changed();}public float getThicknessRatio(){return thicknessRatio;}
 public void shirazSetUseLevelForShape(boolean value){useLevelForShape=value;changed();}

 public void setColor(int value){setColor(ColorStateList.valueOf(value));}
 public void setColor(ColorStateList value){fill=value==null?ColorStateList.valueOf(0):value;colors=null;positions=null;changed();}
 public ColorStateList getColor(){return fill;}
 public void setColors(int[] values){setColors(values,null);}
 public void setColors(int[] values,float[] stops){if(values==null){colors=null;positions=null;changed();return;}if(values.length<2||values.length>256)throw new IllegalArgumentException("Gradient color count");if(stops!=null){if(stops.length!=values.length)throw new IllegalArgumentException("Gradient stop count");float previous=-1;for(float stop:stops){if(!Float.isFinite(stop)||stop<0||stop>1||stop<previous)throw new IllegalArgumentException("Gradient stops");previous=stop;}}colors=values.clone();positions=stops==null?null:stops.clone();fill=null;changed();}
 public int[] getColors(){return colors==null?null:colors.clone();}
 public void setOrientation(Orientation value){if(value==null)throw new NullPointerException("orientation");orientation=value;changed();}public Orientation getOrientation(){return orientation;}
 public void setGradientType(int value){if(value<0||value>2)throw new IllegalArgumentException("Gradient type");type=value;changed();}public int getGradientType(){return type;}
 public void setGradientCenter(float x,float y){if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x)>8192||Math.abs(y)>8192)throw new IllegalArgumentException("Gradient center");centerX=x;centerY=y;changed();}
 public void setGradientRadius(float value){dimension(value);gradientRadius=value;radiusType=0;changed();}public void shirazSetGradientRadiusFraction(float value,boolean parent){dimension(value);gradientRadius=value;radiusType=parent?2:1;changed();}public float getGradientRadius(){float w=Math.max(0,getBounds().width()-strokeWidth),h=Math.max(0,getBounds().height()-strokeWidth);if(radiusType==1){if(width>=0)w=width;if(height>=0)h=height;}float resolved=radiusType==0?gradientRadius:gradientRadius*Math.min(w,h);return useLevel?resolved*Math.max(0,Math.min(10000,getLevel()))/10000f:resolved;}
 public void setUseLevel(boolean value){useLevel=value;changed();}public boolean getUseLevel(){return useLevel;}
 public void setCornerRadius(float value){dimension(value);radius=value;radii=null;changed();}public float getCornerRadius(){return radius;}
 public void setCornerRadii(float[] value){if(value!=null){if(value.length!=8)throw new IllegalArgumentException("Eight corner radii required");for(float r:value)dimension(r);}radii=value==null?null:value.clone();radius=0;changed();}public float[] getCornerRadii(){return radii==null?null:radii.clone();}
 public void setStroke(int width,int color){setStroke(width,ColorStateList.valueOf(color),0,0);}public void setStroke(int width,ColorStateList color){setStroke(width,color,0,0);}public void setStroke(int width,int color,float dash,float gap){setStroke(width,ColorStateList.valueOf(color),dash,gap);}
 public void setStroke(int value,ColorStateList color,float dash,float gap){dimension(value);dimension(dash);dimension(gap);if(dash>0&&gap<=0)throw new IllegalArgumentException("Stroke dash gap");strokeWidth=value;stroke=color==null?ColorStateList.valueOf(0):color;dashWidth=dash;dashGap=gap;changed();}
 public void setSize(int w,int h){if(w< -1||h< -1||w>8192||h>8192)throw new IllegalArgumentException("Shape size");width=w;height=h;changed();}
 public int getIntrinsicWidth(){return width;}public int getIntrinsicHeight(){return height;}
 public void shirazSetPadding(int l,int t,int r,int b){if(l<0||t<0||r<0||b<0)throw new IllegalArgumentException("Shape padding");padding.set(l,t,r,b);invalidateSelf();}
 @Override public boolean getPadding(Rect out){out.set(padding);return (padding.left|padding.top|padding.right|padding.bottom)!=0;}
 public void setAlpha(int value){if(value<0||value>255)throw new IllegalArgumentException("alpha");alpha=value;invalidateSelf();}public int getAlpha(){return alpha;}
 public void setDither(boolean value){dither=value;invalidateSelf();}
 public void setColorFilter(ColorFilter value){ColorFilter.shirazValidate(value);filter=value;changed();}public ColorFilter getColorFilter(){return filter;}
 public void setTintList(ColorStateList value){tint=value;refreshTintOwner();}public void setTintMode(PorterDuff.Mode value){tintMode=value;changed();}
 public boolean isStateful(){return fill!=null&&fill.isStateful()||stroke.isStateful()||tint!=null&&tint.isStateful();}
 protected boolean onStateChange(int[] state){if(isStateful()){changed();return true;}return false;}
 protected boolean onLevelChange(int level){if(useLevel||shape==RING&&useLevelForShape){changed();return true;}return false;}
 protected void onBoundsChange(Rect bounds){if(cached!=null&&(bounds.width()!=cachedW||bounds.height()!=cachedH))clearCache();}
 private int color(ColorStateList list){return list==null?0:list.getColorForState(getState(),list.getDefaultColor());}
 private ColorFilter effect(){return filter!=null?filter:tint==null||tintMode==null?null:new PorterDuffColorFilter(color(tint),tintMode);}
 public int getOpacity(){if(alpha==0)return PixelFormat.TRANSPARENT;if(alpha!=255||filter!=null||tint!=null||shape!=RECTANGLE||radius!=0||radii!=null||strokeWidth>0&&((color(stroke)>>>24)!=255||dashWidth>0))return PixelFormat.TRANSLUCENT;if(colors!=null){for(int c:colors)if((c>>>24)!=255)return PixelFormat.TRANSLUCENT;return PixelFormat.OPAQUE;}return (color(fill)>>>24)==255?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT;}
 public void getOutline(Outline out){Rect b=getBounds();if(shape==OVAL)out.setOval(b);else if(shape==RECTANGLE&&radii==null)out.setRoundRect(b,radius);else{out.setEmpty();return;}float opacity=alpha/255f;if(colors==null)opacity*=(color(fill)>>>24)/255f;else for(int c:colors)if((c>>>24)!=255)opacity=0;if(filter!=null||tint!=null||strokeWidth>0&&(color(stroke)>>>24)!=255)opacity=0;out.setAlpha(opacity);}
 private Path path(float w,float h){Path path=new Path();float inset=strokeWidth/2f,l=inset,t=inset,r=Math.max(l,w-inset),b=Math.max(t,h-inset);if(shape==RING)return ring(r-l,w/2f,h/2f);if(shape==LINE){path.moveTo(0,h/2);path.lineTo(w,h/2);return path;}if(shape==OVAL){path.addOval(l,t,r,b,Path.Direction.CW);return path;}if(radii==null){float corner=Math.min(radius,Math.min(r-l,b-t)/2);path.addRoundRect(l,t,r,b,corner,corner,Path.Direction.CW);return path;}
  float[] q=radii.clone();float scale=1;float rw=r-l,rh=b-t;float[] sums={q[0]+q[2],q[6]+q[4],q[1]+q[7],q[3]+q[5]};for(int i=0;i<4;i++)if(sums[i]>0)scale=Math.min(scale,(i<2?rw:rh)/sums[i]);for(int i=0;i<8;i++)q[i]*=scale;float k=.55228475f;
  path.moveTo(l+q[0],t);path.lineTo(r-q[2],t);path.cubicTo(r-q[2]+k*q[2],t,r,t+q[3]-k*q[3],r,t+q[3]);path.lineTo(r,b-q[5]);path.cubicTo(r,b-q[5]+k*q[5],r-q[4]+k*q[4],b,r-q[4],b);path.lineTo(l+q[6],b);path.cubicTo(l+q[6]-k*q[6],b,l,b-q[7]+k*q[7],l,b-q[7]);path.lineTo(l,t+q[1]);path.cubicTo(l,t+q[1]-k*q[1],l+q[0]-k*q[0],t,l+q[0],t);path.close();return path;
 }
 // Construct bounded circular arcs with cubic segments of at most 90 degrees.
 private static void arc(Path path,float cx,float cy,float radius,double start,double sweep){
  int count=(int)Math.ceil(Math.abs(sweep)/(Math.PI/2));if(count==0)return;
  double step=sweep/count;
  for(int i=0;i<count;i++){double a=start+i*step,b=a+step,k=4.0/3*Math.tan(step/4),ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
   path.cubicTo((float)(cx+radius*(ca-k*sa)),(float)(cy+radius*(sa+k*ca)),(float)(cx+radius*(cb+k*sb)),(float)(cy+radius*(sb-k*cb)),(float)(cx+radius*cb),(float)(cy+radius*sb));}
 }
 private Path ring(float w,float cx,float cy){
  float inner=innerRadius<0?w/innerRadiusRatio:innerRadius,thick=thickness<0?w/thicknessRatio:thickness;dimension(inner);dimension(thick);float outer=inner+thick;dimension(outer);
  Path path=new Path();if(thick==0)return path;double sweep=useLevelForShape?2*Math.PI*getLevel()/10000.0:2*Math.PI;
  if(Math.abs(sweep)>=2*Math.PI){path.addOval(cx-outer,cy-outer,cx+outer,cy+outer,Path.Direction.CW);if(inner>0)path.addOval(cx-inner,cy-inner,cx+inner,cy+inner,Path.Direction.CCW);}
  else if(sweep!=0){path.setFillType(Path.FillType.EVEN_ODD);path.moveTo(cx+inner,cy);path.lineTo(cx+outer,cy);arc(path,cx,cy,outer,0,sweep);path.lineTo((float)(cx+inner*Math.cos(sweep)),(float)(cy+inner*Math.sin(sweep)));arc(path,cx,cy,inner,sweep,-sweep);path.close();}
  return path;
 }
 private int sample(double value){double t=Math.max(0,Math.min(1,value));int end=1;while(end<colors.length-1&&t>(positions==null?(double)end/(colors.length-1):positions[end]))end++;double a=positions==null?(double)(end-1)/(colors.length-1):positions[end-1],b=positions==null?(double)end/(colors.length-1):positions[end];double f=b==a?1:Math.max(0,Math.min(1,(t-a)/(b-a)));double firstAlpha=colors[end-1]>>>24,lastAlpha=colors[end]>>>24,alpha=firstAlpha*(1-f)+lastAlpha*f;int out=((int)Math.round(alpha))<<24;if(alpha>0)for(int shift=0;shift<24;shift+=8)out|=((int)Math.round((((colors[end-1]>>>shift)&255)*firstAlpha*(1-f)+((colors[end]>>>shift)&255)*lastAlpha*f)/alpha))<<shift;return out;}
 private Bitmap gradient(int w,int h,Path path){if(cached!=null)return cached;if((long)w*h>4*1024*1024)throw new IllegalArgumentException("Gradient bitmap exceeds 4M pixels");Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);try{Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);mask.setColor(Color.WHITE);new Canvas(image).drawPath(path,mask);int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);ColorFilter colorFilter=effect();byte[] coverageMask=colorFilter==null?null:new byte[pixels.length];double level=useLevel?Math.max(0,Math.min(10000,getLevel()))/10000.0:1;double l=strokeWidth/2.0,t=l,r=Math.max(l,w-l),bottom=Math.max(t,h-t),sx=l,sy=t,ex=l,ey=bottom;
  switch(orientation){case TR_BL:sx=r;break;case RIGHT_LEFT:sx=r;ey=t;break;case BR_TL:sx=r;sy=bottom;ey=t;break;case BOTTOM_TOP:sy=bottom;ey=t;break;case BL_TR:sy=bottom;ex=r;ey=t;break;case LEFT_RIGHT:ex=r;ey=t;break;case TL_BR:ex=r;break;default:break;}ex=sx+(ex-sx)*level;ey=sy+(ey-sy)*level;double dx=ex-sx,dy=ey-sy,length=dx*dx+dy*dy;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x,coverage=pixels[i]>>>24;if(coverage==0)continue;double value;if(type==LINEAR_GRADIENT)value=length==0?1:((x+.5-sx)*dx+(y+.5-sy)*dy)/length;else if(type==RADIAL_GRADIENT)value=Math.hypot(x+.5-(l+centerX*(r-l)),y+.5-(t+centerY*(bottom-t)))/Math.max(.001,getGradientRadius());else{double angle=Math.atan2(y+.5-(t+centerY*(bottom-t)),x+.5-(l+centerX*(r-l)));if(angle<0)angle+=Math.PI*2;value=level==0?1:angle/(Math.PI*2*level);}int c=sample(value);if(coverageMask==null)pixels[i]=(c&0xffffff)|((((c>>>24)*coverage+127)/255)<<24);else{coverageMask[i]=(byte)coverage;pixels[i]=c;}}if(colorFilter!=null){int[] filtered=new int[pixels.length];shiraz.runtime.NativeBitmap.drawEffects(filtered,w,h,pixels,w,h,0,0,w,h,0,0,w,h,false,255,0,0,w,h,ColorFilter.shirazColor(colorFilter),ColorFilter.shirazMode(colorFilter),ColorFilter.shirazMatrix(colorFilter));for(int i=0;i<filtered.length;i++){int coverageValue=coverageMask[i]&255;filtered[i]=coverageValue==0?0:(filtered[i]&0xffffff)|((((filtered[i]>>>24)*coverageValue+127)/255)<<24);}pixels=filtered;}image.setPixels(pixels,0,w,0,0,w,h);cached=image;cachedW=w;cachedH=h;return image;}catch(RuntimeException|Error error){image.recycle();throw error;}
 }
 public void draw(Canvas canvas){Rect b=getBounds();if(b.isEmpty()||alpha==0)return;long lw=(long)b.right-b.left,lh=(long)b.bottom-b.top;if(lw>8192||lh>8192)throw new IllegalArgumentException("Shape bounds exceed 8192 pixels");int w=(int)lw,h=(int)lh;Path path=path(w,h);int saved=canvas.save();try{canvas.translate(b.left,b.top);if(alpha!=255)canvas.saveLayerAlpha(new RectF(0,0,w,h),alpha);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setDither(dither);paint.setColorFilter(effect());if(shape!=LINE){if(colors!=null){paint.setColorFilter(null);paint.setFilterBitmap(true);canvas.drawBitmap(gradient(w,h,path),null,new Rect(0,0,w,h),paint);paint.setColorFilter(effect());}else{paint.setColor(color(fill));canvas.drawPath(path,paint);}}if(strokeWidth>0){paint.setColor(color(stroke));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(strokeWidth);if(dashWidth>0)paint.setPathEffect(new DashPathEffect(new float[]{dashWidth,dashGap},0));canvas.drawPath(path,paint);}}finally{canvas.restoreToCount(saved);}}
}
