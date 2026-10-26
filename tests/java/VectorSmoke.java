// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.util.TypedValue;
import shiraz.runtime.BinaryXml;
import java.util.Arrays;
import java.util.Random;
public final class VectorSmoke {
    public static void main(String[] args) {
        Resources r=Resources.shirazOpen(args[0],96,160);
        try {
            org.shiraz.fixture.VectorFixture.check(r);
            android.graphics.drawable.Drawable vector=r.getDrawable(r.getIdentifier("vector_group","drawable","org.shiraz.fixture"));vector.setBounds(0,0,32,32);vector.setTint(0xff2266bb);
            android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888);
            for(int degrees:new int[]{0,45,90,135,180,270}){
                pixels.eraseColor(0);android.graphics.Canvas canvas=new android.graphics.Canvas(pixels);canvas.translate(48,48);canvas.rotate(degrees);canvas.translate(-16,-16);vector.draw(canvas);int count=0;for(int y=0;y<96;y++)for(int x=0;x<96;x++)if((pixels.getPixel(x,y)>>>24)!=0)count++;if(count<10)throw new AssertionError("Rotated tinted vector lost ink: "+degrees);
            }
            pixels.eraseColor(0);android.graphics.Canvas reflected=new android.graphics.Canvas(pixels);reflected.translate(64,0);android.graphics.Matrix reflection=new android.graphics.Matrix();reflection.setScale(-2,1);reflected.concat(reflection);vector.draw(reflected);
            if(pixels.getPixel(40,12)!=0xff2266bb||pixels.getPixel(60,12)!=0)throw new AssertionError("Reflected nonuniform vector cache/pixels");
            pixels.recycle();
            byte[] xml=r.shirazReadResourceFile(r.getIdentifier("vector_group","drawable","org.shiraz.fixture"),new TypedValue());
            if (!BinaryXml.parse(xml).name.equals("vector")) throw new AssertionError("compiled vector root");
            for (int n=0;n<xml.length;n++) { try { BinaryXml.parse(Arrays.copyOf(xml,n)); throw new AssertionError("truncated XML accepted "+n); } catch (IllegalArgumentException expected) { } }
            Random random=new Random(541002);
            for (int n=0;n<1000;n++) {
                byte[] changed=xml.clone(); int at=random.nextInt(changed.length); changed[at]^=(byte)(1+random.nextInt(255));
                try { BinaryXml.parse(changed); } catch (IllegalArgumentException expected) { } catch (UnsupportedOperationException expected) { }
            }
            System.out.println("VECTOR_COMPILED_XML_BOUNDS_OK");
        } finally { r.shirazClose(); }
    }
}
