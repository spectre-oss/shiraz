// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import shiraz.runtime.BinaryXml;
import java.io.*;
import java.util.zip.*;

/** Render APK-owned launcher artwork without loading or executing APK classes. */
public final class BuildDesktopIcon {
    static byte[] entry(ZipFile zip,String name) throws IOException {
        ZipEntry entry=zip.getEntry(name);
        if(entry==null||entry.getSize()<0||entry.getSize()>16*1024*1024)throw new IOException("Missing or oversized icon resource: "+name);
        try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192];int n;
            while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>16*1024*1024)throw new IOException("Icon resource limit");}
            return out.toByteArray();
        }
    }
    static void draw(Resources resources,ZipFile zip,BinaryXml.Value value,Canvas canvas,int depth) throws Exception {
        if(depth>8)throw new IOException("Icon reference depth");
        Drawable drawable;
        if(value.type>=28&&value.type<=31)drawable=new ColorDrawable(value.data);
        else if(value.type==1) {
            TypedValue resolved=new TypedValue();resources.getValue(value.data,resolved,true);
            if(resolved.string!=null&&resolved.string.toString().endsWith(".xml")) {
                BinaryXml.Node xml=BinaryXml.parse(entry(zip,resolved.string.toString()));
                if(xml.name.equals("adaptive-icon")) {
                    for(String layer:new String[]{"background","foreground"}) {
                        BinaryXml.Node found=null;
                        for(BinaryXml.Node child:xml.children)if(child.name.equals(layer))found=child;
                        if(found==null||found.android("drawable")==null)throw new IOException("Unsupported adaptive icon layer");
                        // Android adaptive artwork includes an extra quarter on every side.
                        int save=canvas.save();canvas.scale(1.5f,1.5f);canvas.translate(-256f/6,-256f/6);
                        draw(resources,zip,found.android("drawable"),canvas,depth+1);canvas.restoreToCount(save);
                    }
                    return;
                }
            }
            drawable=resources.getDrawable(value.data);
        } else throw new IOException("Unsupported launcher icon value");
        drawable.setBounds(0,0,256,256);drawable.draw(canvas);
    }
    public static void main(String[] args) throws Exception {
        try(ZipFile zip=new ZipFile(args[0]);OutputStream output=new FileOutputStream(args[1])) {
            BinaryXml.Node manifest=BinaryXml.parse(entry(zip,"AndroidManifest.xml"));
            BinaryXml.Value icon=null;
            for(BinaryXml.Node node:manifest.children)if(node.name.equals("application"))icon=node.android("icon");
            if(icon==null)throw new IOException("APK declares no application icon");
            Resources resources=Resources.shirazOpen(args[0],256,256);
            android.content.res.Configuration configuration=new android.content.res.Configuration(resources.getConfiguration());
            configuration.densityDpi=480;resources.updateConfiguration(configuration,null);
            Bitmap bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
            draw(resources,zip,icon,new Canvas(bitmap),0);
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("Cannot encode icon PNG");
        }
    }
}
