// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import shiraz.runtime.NativeBitmap;
import java.util.Random;
public final class ColorMatrixSmoke {
    private static int reference(int color,float[] matrix){double[] input={color>>>24==0?0:(color>>>16)&255,color>>>24==0?0:(color>>>8)&255,color>>>24==0?0:color&255,color>>>24};int[] out=new int[4];for(int row=0;row<4;row++){double value=matrix[row*5+4];for(int col=0;col<4;col++)value+=matrix[row*5+col]*input[col];out[row]=(int)Math.max(0,Math.min(255,Math.round(value)));}return out[3]==0?0:(out[3]<<24)|(out[0]<<16)|(out[1]<<8)|out[2];}
    public static void main(String[] args){
        org.shiraz.fixture.ColorMatrixFixture.check();Random random=new Random(541002);
        for(int n=0;n<1000;n++){float[] matrix=new float[20];for(int j=0;j<20;j++)matrix[j]=(random.nextFloat()-.5f)*(j%5==4?512:4);int color=random.nextInt();if(NativeBitmap.filterColorMatrix(color,matrix)!=reference(color,matrix))throw new AssertionError("matrix reference mismatch");}
        for(float[] invalid:new float[][]{null,new float[19],new float[21]}){try{NativeBitmap.filterColorMatrix(0,invalid);throw new AssertionError("native matrix bounds accepted");}catch(IllegalArgumentException expected){}}
        float[] invalid=new ColorMatrix().getArray();invalid[4]=Float.NaN;try{new ColorMatrixColorFilter(invalid);throw new AssertionError("nonfinite Java matrix accepted");}catch(IllegalArgumentException expected){}
        int[] destination={0xff123456};try{NativeBitmap.drawEffects(destination,1,1,new int[]{0xffff0000},1,1,0,0,1,1,0,0,1,1,false,255,0,0,1,1,0,-2,invalid);throw new AssertionError("nonfinite native draw matrix accepted");}catch(IllegalArgumentException expected){}if(destination[0]!=0xff123456)throw new AssertionError("failed matrix draw changed destination");
        System.out.println("COLOR_MATRIX_REFERENCE_AND_NATIVE_BOUNDS_OK");
    }
}
