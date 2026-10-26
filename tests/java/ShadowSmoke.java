// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Canvas;
public final class ShadowSmoke {
    private static int reference(int src, int dst) {
        int a=src>>>24, d=dst>>>24, remainder=(d*(255-a)+127)/255;
        int outAlpha=a+remainder;
        if(outAlpha==0)return 0;
        int result=outAlpha<<24;
        for(int shift=0;shift<24;shift+=8)
            result|=(( ((src>>>shift)&255)*a+((dst>>>shift)&255)*remainder+outAlpha/2)/outAlpha)<<shift;
        return result;
    }
    public static void main(String[] args) throws Exception {
        org.shiraz.fixture.ShadowFixture.check();
        java.lang.reflect.Method over=Canvas.class.getDeclaredMethod("over",int.class,int.class);
        over.setAccessible(true);
        java.util.Random random=new java.util.Random(3012);
        // Every source alpha against transparent, opaque, and partial destinations.
        for(int a=0;a<256;a++)for(int i=0;i<32;i++) {
            int src=(random.nextInt()&0xffffff)|(a<<24);
            int dst=random.nextInt();
            if(i%3==0)dst|=0xff000000;
            if(i%3==1)dst&=0xffffff;
            if(((Integer)over.invoke(null,src,dst)).intValue()!=reference(src,dst))
                throw new AssertionError("Straight-alpha blend mismatch a="+a+" case="+i);
        }
        System.out.println("CANVAS_ALPHA_FAST_PATHS_OK");
    }
}
