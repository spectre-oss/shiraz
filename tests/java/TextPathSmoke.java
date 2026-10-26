// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import java.util.Arrays;
public final class TextPathSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static Bitmap bitmap(){return Bitmap.createBitmap(360,280,Bitmap.Config.ARGB_8888);}
 static int[] pixels(Bitmap b){int[] p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return p;}
 static int ink(Bitmap b){int n=0;for(int p:pixels(b))if(p!=0)n++;return n;}
 static int[] bounds(Bitmap b){int[] box={b.getWidth(),b.getHeight(),0,0};for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(b.getPixel(x,y)!=0){box[0]=Math.min(box[0],x);box[1]=Math.min(box[1],y);box[2]=Math.max(box[2],x+1);box[3]=Math.max(box[3],y+1);}return box;}
 static Path line(float x,float y,float endX,float endY){Path p=new Path();p.moveTo(x,y);p.lineTo(endX,endY);return p;}
 static void equal(Bitmap a,Bitmap b,String why){int[] ap=pixels(a),bp=pixels(b);int differences=0,first=-1;for(int i=0;i<ap.length;i++)if(ap[i]!=bp[i]){differences++;if(first<0)first=i;}check(differences==0,why+" differences="+differences+" first="+(first%a.getWidth())+","+(first/a.getWidth()));a.recycle();b.recycle();}
 static void quantized(Bitmap a,Bitmap b,String why){
  int[] ap=pixels(a),bp=pixels(b);for(int pass=0;pass<2;pass++){int[] from=pass==0?ap:bp,to=pass==0?bp:ap;for(int i=0;i<from.length;i++)if(from[i]!=0){boolean match=false;int x=i%a.getWidth(),y=i/a.getWidth();for(int dx=-1;dx<=1;dx++){int nx=x+dx;if(nx>=0&&nx<a.getWidth()&&to[y*a.getWidth()+nx]==from[i])match=true;}check(match,why+" exceeds one horizontal pixel of float quantization");}}a.recycle();b.recycle();
 }
 static void horizontal(String text,Paint p){
  for(Paint.Align align:Paint.Align.values()){
   p.setTextAlign(align);Bitmap a=bitmap(),b=bitmap();new Canvas(a).drawTextOnPath(text,line(20,90,320,90),0,-3,p);
   float anchor=20+(align==Paint.Align.CENTER?150:align==Paint.Align.RIGHT?300:0);new Canvas(b).drawText(text,anchor,87,p);if(p.getTextScaleX()!=1||p.getTextSkewX()!=0)quantized(a,b,"scaled/skewed horizontal shaping "+align);else equal(a,b,"horizontal shared shaping "+align+" "+p.getStyle());
  }p.setTextAlign(Paint.Align.LEFT);
 }
 public static void main(String[] args){
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(25);p.setColor(Color.BLACK);
  horizontal("Hafez حافظ ۱۲۳",p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);horizontal("حَافِظ road",p);p.setStyle(Paint.Style.FILL_AND_STROKE);horizontal("abc",p);p.setStyle(Paint.Style.FILL);
  p.setTextScaleX(1.2f);p.setTextSkewX(-.15f);p.setFakeBoldText(true);horizontal("حافظ ABC",p);p.setTextScaleX(1);p.setTextSkewX(0);p.setFakeBoldText(false);
  Bitmap offset=bitmap(),offsetStraight=bitmap();new Canvas(offset).drawTextOnPath("offset",line(20,90,320,90),13,7,p);new Canvas(offsetStraight).drawText("offset",33,97,p);equal(offset,offsetStraight,"horizontal and normal offsets");
  Bitmap rotated=bitmap(),vertical=bitmap();Canvas c=new Canvas(rotated);c.translate(170,20);c.rotate(90);c.drawTextOnPath("Road 123",line(0,0,200,0),10,4,p);new Canvas(vertical).drawTextOnPath("Road 123",line(170,20,170,220),10,4,p);equal(rotated,vertical,"path and canvas tangent rotation agree");
  Bitmap scaled=bitmap(),unscaled=bitmap();p.setHinting(0);c=new Canvas(scaled);c.scale(2,3);c.drawTextOnPath("I",line(10,45,110,45),0,0,p);new Canvas(unscaled).drawTextOnPath("I",line(10,45,110,45),0,0,p);int[] sb=bounds(scaled),ub=bounds(unscaled);check(ink(scaled)>0&&ink(unscaled)>0,"scaled path ink");for(int i=0;i<4;i++)check(Math.abs(sb[i]-ub[i]*(i%2==0?2:3))<=3,"nonuniform affine glyph bounds");scaled.recycle();unscaled.recycle();p.setHinting(1);
  // Ordinary text must transform the same shaped outlines as a straight text path.
  for(Paint.Style style:Paint.Style.values())for(float sx:new float[]{.5f,2,-1}){
   p.setStyle(style);p.setStrokeWidth(2);p.setTextAlign(Paint.Align.LEFT);Bitmap direct=bitmap(),pathText=bitmap();Canvas dc=new Canvas(direct),pc=new Canvas(pathText);dc.translate(180,30);pc.translate(180,30);Matrix stretch=new Matrix();stretch.setScale(sx,1.5f);dc.concat(stretch);pc.concat(stretch);dc.drawText("I",0,70,p);pc.drawTextOnPath("I",line(0,70,100,70),0,0,p);equal(direct,pathText,"ordinary transformed outline "+style+" "+sx);
  }
  p.setStyle(Paint.Style.FILL);p.setHinting(0);
  for(Paint.Align align:Paint.Align.values()){p.setTextAlign(align);Bitmap direct=bitmap(),reference=bitmap();Canvas dc=new Canvas(direct),rc=new Canvas(reference);dc.translate(160,20);rc.translate(160,20);dc.rotate(90);rc.rotate(90);float anchor=align==Paint.Align.LEFT?0:align==Paint.Align.CENTER?100:200;dc.drawText("I",anchor,0,p);rc.drawTextOnPath("I",line(0,0,200,0),0,0,p);equal(direct,reference,"rotated alignment "+align);}
  p.setTextAlign(Paint.Align.LEFT);Bitmap mixed=bitmap();Canvas mc=new Canvas(mixed);mc.scale(.6f,.7f);Path roundClip=new Path();roundClip.addCircle(90,90,70,Path.Direction.CW);mc.clipPath(roundClip);p.setUnderlineText(true);p.setShadowLayer(2,2,2,Color.RED);mc.drawText("حافظ Hafez ۱۲۳",20,90,p);check(ink(mixed)>0,"scaled mixed shaping clip underline shadow");p.clearShadowLayer();p.setUnderlineText(false);mixed.recycle();p.setHinting(1);
  Path curve=new Path();curve.moveTo(20,180);curve.cubicTo(70,10,210,10,300,180);Bitmap curved=bitmap(),chord=bitmap();new Canvas(curved).drawTextOnPath("Hafez حافظ 123",curve,20,0,p);new Canvas(chord).drawTextOnPath("Hafez حافظ 123",line(20,180,300,180),20,0,p);check(ink(curved)>0&&!Arrays.equals(pixels(curved),pixels(chord)),"curved arc length and tangents differ from chord");curved.recycle();chord.recycle();
  Bitmap clipped=bitmap();c=new Canvas(clipped);Path clip=new Path();clip.addRect(40,30,90,100,Path.Direction.CW);c.clipPath(clip);c.drawTextOnPath("Hafez حافظ",line(20,90,320,90),0,0,p);check(ink(clipped)>0,"path clip retains ink");for(int y=0;y<280;y++)for(int x=0;x<360;x++)if(x<40||x>=90||y<30||y>=100)check(clipped.getPixel(x,y)==0,"path clip contains all coverage");clipped.recycle();
  Bitmap empty=bitmap(),fallback=bitmap();new Canvas(empty).drawTextOnPath("abc",new Path(),20,40,p);new Canvas(fallback).drawText("abc",20,80,p);equal(empty,fallback,"AOSP empty-contour placement fallback");
  Bitmap shortPath=bitmap();new Canvas(shortPath).drawTextOnPath("abcdef",line(20,90,21,90),-5,0,p);check(ink(shortPath)>0,"short path clamps distances to endpoints");shortPath.recycle();
  Bitmap chars=bitmap(),string=bitmap();new Canvas(chars).drawTextOnPath("XabcY".toCharArray(),1,3,line(20,90,320,90),0,0,p);new Canvas(string).drawTextOnPath("abc",line(20,90,320,90),0,0,p);equal(chars,string,"char array selected slice");
  Bitmap shadow=bitmap();p.setShadowLayer(2,3,3,Color.RED);new Canvas(shadow).drawTextOnPath("Road",curve,20,0,p);check(ink(shadow)>0,"path shadow pipeline");p.clearShadowLayer();shadow.recycle();
  Bitmap limits=bitmap();try{new Canvas(limits).drawTextOnPath("a",curve,Float.NaN,0,p);throw new AssertionError("NaN offset accepted");}catch(IllegalArgumentException expected){}try{new Canvas(limits).drawTextOnPath(new String(new char[32769]).replace('\0','a'),curve,0,0,p);throw new AssertionError("Text limit accepted");}catch(IllegalArgumentException expected){}check(ink(limits)==0,"failed draws leave destination unchanged");limits.recycle();
  System.out.println("TEXT_PATH_SHAPING_TRANSFORM_OK");
 }
}
