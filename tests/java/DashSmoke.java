// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import shiraz.runtime.NativePath;
public final class DashSmoke {
    public static void main(String[] args){
        org.shiraz.fixture.DashFixture.check();
        int[] pixels=new int[64];java.util.Arrays.fill(pixels,0xff123456);float[] commands={1,0,4,2,8,4};float[] matrix={1,0,0,1,0,0};
        for(float[] bad:new float[][]{{},{1},{0,0},{-1,2},{Float.NaN,2},{Float.POSITIVE_INFINITY,2},{Float.MIN_VALUE,Float.MIN_VALUE}}){
            try{NativePath.drawDashed(pixels,8,8,commands,0,0xffff0000,1,2,0,0,4,false,matrix,0,0,8,8,bad,0);throw new AssertionError("invalid/unbounded native dash accepted");}catch(IllegalArgumentException expected){}
            for(int pixel:pixels)if(pixel!=0xff123456)throw new AssertionError("failed dash draw mutated pixels");
        }
        System.out.println("DASH_NATIVE_BOUNDS_AND_RENDERING_OK");
    }
}
