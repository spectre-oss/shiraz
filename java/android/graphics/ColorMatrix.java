// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.util.Arrays;
/** Mutable row-major RGBA affine transform; translations are in 8-bit channel units. */
public class ColorMatrix {
    private final float[] values=new float[20];
    public ColorMatrix(){reset();}
    public ColorMatrix(float[] source){set(source);}
    public ColorMatrix(ColorMatrix source){set(source);}
    static void valid(float[] source){if(source.length<20)throw new ArrayIndexOutOfBoundsException("20 matrix values required");for(int i=0;i<20;i++)if(!Float.isFinite(source[i])||Math.abs(source[i])>10000000)throw new IllegalArgumentException("Color matrix coefficient bound");}
    public final float[] getArray(){return values;}
    public void reset(){Arrays.fill(values,0);for(int i=0;i<4;i++)values[i*6]=1;}
    public void set(ColorMatrix source){set(source.values);}
    public void set(float[] source){valid(source);System.arraycopy(source,0,values,0,20);}
    public void setScale(float r,float g,float b,float a){float[] out=new float[20];out[0]=r;out[6]=g;out[12]=b;out[18]=a;set(out);}
    public void setRotate(int axis,float degrees){
        if(axis<0||axis>2||!Float.isFinite(degrees))throw new IllegalArgumentException("Color rotation axis/angle");
        float c=(float)Math.cos(Math.toRadians(degrees)),s=(float)Math.sin(Math.toRadians(degrees));ColorMatrix out=new ColorMatrix();int first=(axis+1)%3,second=(axis+2)%3;
        out.values[first*5+first]=c;out.values[first*5+second]=s;out.values[second*5+first]=-s;out.values[second*5+second]=c;set(out);
    }
    public void setConcat(ColorMatrix a,ColorMatrix b){
        valid(a.values);valid(b.values);float[] out=new float[20];for(int row=0;row<4;row++)for(int col=0;col<5;col++){double sum=col==4?a.values[row*5+4]:0;for(int k=0;k<4;k++)sum+=(double)a.values[row*5+k]*b.values[k*5+col];out[row*5+col]=(float)sum;}set(out);
    }
    public void preConcat(ColorMatrix before){setConcat(this,before);}
    public void postConcat(ColorMatrix after){setConcat(after,this);}
    public void setSaturation(float value){float[] out=new float[20];float[] weights={.213f,.715f,.072f};for(int r=0;r<3;r++)for(int c=0;c<3;c++)out[r*5+c]=(1-value)*weights[c]+(r==c?value:0);out[18]=1;set(out);}
    public void setRGB2YUV(){set(new float[]{.299f,.587f,.114f,0,0,-.16874f,-.33126f,.5f,0,0,.5f,-.41869f,-.08131f,0,0,0,0,0,1,0});}
    public void setYUV2RGB(){set(new float[]{1,0,1.402f,0,0,1,-.34414f,-.71414f,0,0,1,1.772f,0,0,0,0,0,0,1,0});}
    public boolean equals(Object other){if(!(other instanceof ColorMatrix))return false;float[] b=((ColorMatrix)other).values;for(int i=0;i<20;i++)if(values[i]!=b[i])return false;return true;}
    public int hashCode(){int hash=1;for(float v:values)hash=31*hash+Float.floatToIntBits(v==0?0:v);return hash;}
}
