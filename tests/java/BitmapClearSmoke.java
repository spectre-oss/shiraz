// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Bitmap;
import shiraz.runtime.NativeBitmap;

public final class BitmapClearSmoke {
    public static void main(String[] args) {
        for(Bitmap.Config config:new Bitmap.Config[]{Bitmap.Config.ARGB_8888,Bitmap.Config.ALPHA_8}) {
            Bitmap bitmap=Bitmap.createBitmap(17,19,config);
            try {
                for(int color:new int[]{0xff123456,0x00123456,0x80123456,0,0xffffffff}) {
                    bitmap.eraseColor(color);
                    int expected=config==Bitmap.Config.ALPHA_8?color&0xff000000:color;
                    for(int y=0;y<19;y++)for(int x=0;x<17;x++)
                        if(bitmap.getPixel(x,y)!=expected)throw new AssertionError("eraseColor replacement/config mismatch");
                }
                Bitmap immutable=bitmap.copy(config,false);
                boolean rejected=false;
                try{immutable.eraseColor(0);}catch(IllegalStateException expected){rejected=true;}
                if(!rejected)throw new AssertionError("immutable erase accepted");
                immutable.recycle();
            } finally {bitmap.recycle();}
            boolean rejected=false;
            try{bitmap.eraseColor(0);}catch(IllegalStateException expected){rejected=true;}
            if(!rejected)throw new AssertionError("recycled erase accepted");
        }
        int[] tail=new int[4097]; NativeBitmap.clear(tail,0x00123456);
        for(int color:tail)if(color!=0x00123456)throw new AssertionError("native block/tail mismatch");
        NativeBitmap.clear(new int[0],42);
        boolean rejected=false;
        try{NativeBitmap.clear(null,42);}catch(NullPointerException expected){rejected=true;}
        if(!rejected)throw new AssertionError("null clear accepted");
        int[] pixel={0x80123456};
        boolean invalid=false;
        try{NativeBitmap.outlineShadow(pixel,1,1,0,0,1,1,Float.NaN,1,0,0,0,0,1,1,0,false,1,0,.3f);}
        catch(IllegalArgumentException expected){invalid=true;}
        if(!invalid || pixel[0]!=0x80123456)throw new AssertionError("invalid shadow mutated destination");
        NativeBitmap.outlineShadow(pixel,1,1,0,0,1,1,1,1,0,0,0,0,1,1,0,false,1,0,.3f);
        double coverage=1-.5*Math.exp(-.25/2);
        int sa=(int)Math.round(255*.3f*coverage),remainder=(128*(255-sa)+127)/255,alpha=sa+remainder;
        int expected=alpha<<24;
        for(int shift=0;shift<24;shift+=8)expected|=((((0x80123456>>>shift)&255)*remainder+alpha/2)/alpha)<<shift;
        if(pixel[0]!=expected)throw new AssertionError("translucent outline shadow mismatch");
        Bitmap frame=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);
        int[] reference=new int[360*640];
        long javaBegin=System.nanoTime();
        for(int i=0;i<20;i++)java.util.Arrays.fill(reference,i);
        System.out.println("JAVA_FILL_360X640_AVERAGE_NS="+((System.nanoTime()-javaBegin)/20));
        if(reference[reference.length-1]!=19)throw new AssertionError("reference clear tail");
        long begin=System.nanoTime();
        for(int i=0;i<20;i++)frame.eraseColor(i);
        System.out.println("CLEAR_360X640_AVERAGE_NS="+((System.nanoTime()-begin)/20));
        if(frame.getPixel(359,639)!=19)throw new AssertionError("full-size clear tail"); frame.recycle();
        System.out.println("BITMAP_CLEAR_OK");
    }
}
