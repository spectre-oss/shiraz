// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativeBitmap;
public final class ColorFilterSmoke {
    public static void main(String[] args) {
        org.shiraz.fixture.ColorFilterFixture.check();
        org.shiraz.fixture.ColorFilterFixture.checkView(new android.content.ContextWrapper(null));
        int[] pixel={0xff123456};
        try { NativeBitmap.filterColor(0,0,18); throw new AssertionError("invalid filter accepted"); } catch (IllegalArgumentException expected) { }
        try { NativeBitmap.drawStyled(pixel,1,1,pixel,1,1,0,0,1,1,0,0,1,1,false,0,0,0,1,1,0,-2); throw new AssertionError("invalid bitmap filter accepted"); } catch (IllegalArgumentException expected) { }
        if (pixel[0] != 0xff123456) throw new AssertionError("invalid filter mutated target");
        int[] frame=new int[720*1280],tile=new int[256*256]; java.util.Arrays.fill(tile,0xff123456); long[] times=new long[5];
        for (int n=-2;n<5;n++) {
            long start=System.nanoTime(); NativeBitmap.drawStyled(frame,720,1280,tile,256,256,0,0,256,256,0,0,720,1280,true,255,0,0,720,1280,0xffff0000,5);
            if (n >= 0) times[n]=System.nanoTime()-start;
        }
        if (frame[0] != 0xffff0000 || frame[frame.length-1] != 0xffff0000) throw new AssertionError("full-frame filtered sampling");
        java.util.Arrays.sort(times); System.out.println("FILTER_SRC_IN_720X1280_MEDIAN_NS="+times[2]);
    }
}
