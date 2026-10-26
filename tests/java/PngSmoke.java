// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.graphics.*;
import android.util.TypedValue;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
public final class PngSmoke {
    private static void rejected(Method method,Class<?> type,Object... args) throws Exception {
        try { method.invoke(null,args); throw new AssertionError("invalid native resource-file request accepted"); }
        catch (InvocationTargetException failure) { if (!type.isInstance(failure.getCause())) throw new AssertionError(failure.getCause()); }
    }
    public static void main(String[] args) throws Exception {
        Resources resources=Resources.shirazOpen(args[0],96,160);
        try {
            org.shiraz.fixture.PngFixture.check(resources);
            Field field=Resources.class.getDeclaredField("handle"); field.setAccessible(true); long handle=field.getLong(resources);
            Method file=Resources.class.getDeclaredMethod("nativeResourceFile",long.class,int.class,byte[].class); file.setAccessible(true);
            rejected(file,IllegalArgumentException.class,handle,99,"res/color/primary_text_light.xml".getBytes(StandardCharsets.UTF_8));
            rejected(file,IllegalArgumentException.class,handle,1,"res/../../outside".getBytes(StandardCharsets.UTF_8));
            rejected(file,IllegalStateException.class,0L,1,"res/x".getBytes(StandardCharsets.UTF_8));
            try { shiraz.runtime.NativeImage.png(new byte[8],0,8,false,new int[1]); throw new AssertionError("native PNG output bounds accepted"); } catch (IllegalArgumentException expected) { }
        } finally { resources.shirazClose(); }
        long beforeFD=ResourceLifetime.descriptors(args[0]),beforeMaps=ResourceLifetime.mappings(args[0]);
        for (int n=0;n<32;n++) {
            resources=Resources.shirazOpen(args[0],96,160); java.io.InputStream stream; Bitmap image;
            try {
                int id=resources.getIdentifier("png_grid","drawable","org.shiraz.fixture"); image=BitmapFactory.decodeResource(resources,id); stream=resources.openRawResource(id);
            } finally { resources.shirazClose(); }
            try (java.io.InputStream owned=stream) { Bitmap afterClose=BitmapFactory.decodeStream(owned); if (afterClose.getPixel(0,0) != Color.RED || image.getPixel(0,0) != Color.RED) throw new AssertionError("decoded pixels/raw stream outlive resource owner"); afterClose.recycle(); }
            image.recycle();
        }
        if (ResourceLifetime.descriptors(args[0]) != beforeFD || ResourceLifetime.mappings(args[0]) != beforeMaps) throw new AssertionError("PNG loading leaked APK descriptors or mappings");
        System.out.println("PNG_NATIVE_ARGUMENTS_AND_LIFETIME_OK");
    }
}
