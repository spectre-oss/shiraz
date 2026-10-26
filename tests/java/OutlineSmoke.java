// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class OutlineSmoke {
 private static int edge(float value){return (int)Math.max(0,Math.min(64,Math.ceil((double)value-.5)));}
 private static void pixels() throws Exception {
  java.lang.reflect.Method pass=Canvas.class.getDeclaredMethod("shadowPass",Outline.class,float.class,float.class,float.class);pass.setAccessible(true);
  for(boolean oval:new boolean[]{false,true})for(float radius:new float[]{0,3,12})for(float sigma:new float[]{.5f,2.3f,9}){
   Outline o=new Outline();if(oval)o.setOval(12,16,52,44);else o.setRoundRect(12,16,52,44,radius);
   Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);b.eraseColor(0xffffffff);
   float offset=1.25f,opacity=.24f;pass.invoke(new Canvas(b),o,sigma,offset,opacity);
   double spread=3.0*sigma;
   int l=edge((float)(12-spread)),r=edge((float)(52+spread)),t=edge((float)(16+offset-spread)),bottom=edge((float)(44+offset+spread));
   for(int y=0;y<64;y++)for(int x=0;x<64;x++){
    int expected=0xffffffff;
    if(x>=l&&x<r&&y>=t&&y<bottom){
     double dx=Math.abs(x+.5-32),dy=Math.abs(y+.5-offset-30),d;
     if(oval)d=(Math.sqrt(dx*dx/400+dy*dy/196)-1)*14;
     else{double qx=dx-(20-radius),qy=dy-(14-radius);d=Math.hypot(Math.max(0,qx),Math.max(0,qy))+Math.min(Math.max(qx,qy),0)-radius;}
     double c=d>=0?.5*Math.exp(-d*d/(2*sigma*sigma)):1-.5*Math.exp(-d*d/(2*sigma*sigma));
     int a=(int)Math.round(255*opacity*c),v=255-a;expected=0xff000000|(v<<16)|(v<<8)|v;
    }
    if(b.getPixel(x,y)!=expected)throw new AssertionError("Outline pixel mismatch "+x+","+y+" radius="+radius+" sigma="+sigma+" oval="+oval);
   }
   b.recycle();
  }
  System.out.println("OUTLINE_REFERENCE_PIXELS_OK");
 }
 public static void main(String[] args) throws Exception {org.shiraz.fixture.OutlineFixture.check(new android.content.ContextWrapper(null));pixels();}
}
