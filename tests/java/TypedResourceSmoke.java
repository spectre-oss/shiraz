// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import android.util.TypedValue;
import java.lang.reflect.*;
public final class TypedResourceSmoke {
    private static void rejected(Method method,Class<?> type,Object... args) throws Exception {
        try { method.invoke(null,args); throw new AssertionError("invalid native resource lookup accepted"); }
        catch (InvocationTargetException failure) { if (!type.isInstance(failure.getCause())) throw new AssertionError(failure.getCause()); }
    }
    public static void main(String[] args) throws Exception {
        Resources resources=Resources.shirazOpen(args[0],96,160);
        TypedValue retained=new TypedValue();
        try {
            org.shiraz.fixture.TypedResourceFixture.check(resources);
            int value=resources.getIdentifier("welcome","string","org.shiraz.fixture");
            resources.getValue(value,retained,true);
            Field field=Resources.class.getDeclaredField("handle"); field.setAccessible(true); long handle=field.getLong(resources);
            Method method=Resources.class.getDeclaredMethod("nativeValue",long.class,int.class,int.class,boolean.class,boolean.class,int[].class,String[].class); method.setAccessible(true);
            rejected(method,NullPointerException.class,handle,value,0,true,false,null,new String[1]);
            rejected(method,IllegalArgumentException.class,handle,value,0,true,false,new int[5],new String[1]);
            rejected(method,IllegalArgumentException.class,handle,value,65536,true,false,new int[6],new String[1]);
            rejected(method,IllegalStateException.class,0L,value,0,true,false,new int[6],new String[1]);
        } finally { resources.shirazClose(); }
        if (!"Hello café 🌍".contentEquals(retained.string)) throw new AssertionError("closed manager invalidated owned string");
        try { resources.getValue(retained.resourceId,retained,true); throw new AssertionError("closed resources accepted"); } catch (IllegalStateException expected) { }
        if (!"Hello café 🌍".contentEquals(retained.string)) throw new AssertionError("closed lookup mutated output");
        System.out.println("TYPED_RESOURCE_NATIVE_FAILURES_AND_OWNERSHIP_OK");
    }
}
