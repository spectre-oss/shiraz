// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import java.io.*;
public final class TypefaceBuilderSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File file=new File(".local/share/shiraz/fonts/NotoSans-Regular.ttf");check(file.isFile(),"private font fixture");
        Typeface face=new Typeface.Builder(file).setWeight(500).setItalic(false).build();check(face!=null&&face.getWeight()==500&&!face.isItalic(),"file builder metadata");
        Paint paint=new Paint();paint.setTypeface(face);paint.setTextSize(24);check(paint.measureText("Telegram")>0,"actual registered font measurement");
        try(FileInputStream input=new FileInputStream(file)){input.read();Typeface descriptor=new Typeface.Builder(input.getFD()).build();check(descriptor!=null&&input.getChannel().position()==1,"descriptor builder preserves ownership and position");check(input.read()>=0,"descriptor remains open");}
        check(new Typeface.Builder("build/no-such-font.ttf").build()==null,"missing font fails");
        check(new Typeface.Builder("build/no-such-font.ttf").setFallback("sans-serif").build()!=null,"explicit fallback");
        File invalid=File.createTempFile("shiraz-font-",".bin",new File("build"));
        try{try(FileOutputStream output=new FileOutputStream(invalid)){output.write(new byte[]{1,2,3,4});}check(new Typeface.Builder(invalid).build()==null,"malformed font rejected before registration");}finally{invalid.delete();}
        try{new Typeface.Builder(file).setWeight(1001);throw new AssertionError("invalid weight");}catch(IllegalArgumentException expected){}
        System.out.println("TYPEFACE_BUILDER_OK");
    }
}
