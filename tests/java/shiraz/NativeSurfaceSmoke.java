// SPDX-License-Identifier: GPL-3.0-only
package shiraz;
import java.util.Arrays;
import shiraz.runtime.NativeSurface;

public final class NativeSurfaceSmoke {
    private static native void finger(int action,long id,float x,float y);
    private static native void visibilityWindow(boolean value);
    private static native void focusWindow(boolean value);
    private static native void resizeWindow(int width,int height);
    public static void main(String[] args) throws Exception {
        try { new NativeSurface(0, 20); throw new AssertionError("Invalid size accepted"); }
        catch (IllegalArgumentException expected) { }
        NativeSurface closed;
        try (NativeSurface surface = new NativeSurface(3, 2)) {
            closed = surface;
            int[] pixels = {0xffff0000, 0xff00ff00, 0xff0000ff, 0x80123456, 0, 0xffffffff};
            surface.post(pixels);
            if (!Arrays.equals(surface.snapshot(), pixels)) throw new AssertionError("ARGB/RGBA round trip failed");
            pixels[0] = 0;
            if (surface.snapshot()[0] != 0xffff0000) throw new AssertionError("Posted data aliases Java array");
            try { surface.post(new int[2]); throw new AssertionError("Bad length accepted"); }
            catch (IllegalArgumentException expected) { }
            try { surface.post(null); throw new AssertionError("Null accepted"); }
            catch (NullPointerException expected) { }
            try { surface.present(0); throw new AssertionError("Invalid frames accepted"); }
            catch (IllegalArgumentException expected) { }
            surface.present(2);
        }
        System.loadLibrary("shiraz-test-input");
        try(final NativeSurface surface=new NativeSurface(2,4)) {
            try{surface.requestWindowSize(4,2);throw new AssertionError("inactive window request accepted");}catch(IllegalStateException expected){}
            try{surface.requestPointerIcon(1002);throw new AssertionError("Inactive cursor accepted");}catch(IllegalStateException expected){}
            final android.graphics.Bitmap cursorBitmap=android.graphics.Bitmap.createBitmap(new int[]{0xffff0000,0xff00ff00,0xff0000ff,0},2,2,android.graphics.Bitmap.Config.ARGB_8888);
            final android.view.PointerIcon custom=android.view.PointerIcon.create(cursorBitmap,1,0);
            try{surface.requestPointerIcon(custom);throw new AssertionError("Inactive custom cursor accepted");}catch(IllegalStateException expected){}
            final int[] resizes={0}, frames={0};final java.util.ArrayList<Boolean> visibility=new java.util.ArrayList<Boolean>();final java.util.ArrayList<Boolean> focus=new java.util.ArrayList<Boolean>();
            surface.presentDispatched(5,new NativeSurface.ResizeCallback(){
                public void onWindowVisibility(boolean value){visibility.add(value);}
                public void onWindowFocus(boolean value){focus.add(value);}
                public void onResize(int width,int height){surface.resize(width,height);int[] pixels=new int[width*height];Arrays.fill(pixels,0xff123456);surface.post(pixels);resizes[0]++;}
                public boolean onFrame(){surface.requestPointerIcon(custom);surface.requestPointerIcon(custom);surface.requestPointerIcon(frames[0]%2==0?1002:0);if(++frames[0]==1){surface.requestWindowSize(4,2);focusWindow(true);focusWindow(false);visibilityWindow(false);visibilityWindow(true);}if(frames[0]==3)surface.requestWindowSize(2,4);return true;}
                public void onKey(int a,int k,int s,int r,int m,int f,long d,long t){}
                public void onText(String text,long time){}
                public void onPointer(int action,float x,float y,int source,long time){}
            });
            if(!visibility.contains(false)||!visibility.get(visibility.size()-1))throw new AssertionError("Native hide/restore callback");
            if(!focus.contains(Boolean.TRUE)||focus.size()<3||focus.get(focus.size()-1))throw new AssertionError("SDL focus gained/lost callbacks");
            // Initial display geometry is delivered before the first frame, followed by two resizes.
            if(resizes[0]!=3||surface.getWidth()!=2||surface.getHeight()!=4||surface.snapshot().length!=8||surface.snapshot()[7]!=0xff123456)throw new AssertionError("dispatched resize geometry/pixels");
            try{surface.resize(0,4);throw new AssertionError("invalid resize accepted");}catch(IllegalArgumentException expected){}
            if(surface.getWidth()!=2||surface.snapshot()[7]!=0xff123456)throw new AssertionError("failed resize mutated buffer");
            try{surface.post(new int[7]);throw new AssertionError("resized length mismatch accepted");}catch(IllegalArgumentException expected){}
        }
        closed.close();
        try { closed.snapshot(); throw new AssertionError("Closed surface accepted"); }
        catch (IllegalStateException expected) { }
        java.lang.reflect.Method read = NativeSurface.class.getDeclaredMethod("readPixels", long.class);
        read.setAccessible(true);
        try { read.invoke(null, Long.MAX_VALUE); throw new AssertionError("Stale native ID accepted"); }
        catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IllegalStateException)) throw expected;
        }
        try(final NativeSurface touch=new NativeSurface(100,200)){
            final java.util.ArrayList<Integer> actions=new java.util.ArrayList<Integer>();
            touch.presentDispatched(4,new NativeSurface.FrameCallback(){
                int frames;
                public boolean onFrame(){if(frames++==0){finger(0,300,.25f,.5f);finger(0,900,.75f,.5f);finger(2,900,.9f,.5f);finger(1,300,.25f,.5f);focusWindow(false);finger(1,900,.9f,.5f);}return true;}
                public void onTouch(int action,int[] ids,float[] xs,float[] ys,float[] pressures,long time){
                    actions.add(action);int masked=action&255;
                    if(masked==5&&(action!=261||ids.length!=2||ids[0]!=0||ids[1]!=1||xs[0]!=25||xs[1]!=75||ys[1]!=100||pressures[1]!=.75f))throw new AssertionError("JNI pointer down snapshot");
                    if(masked==2&&(ids.length!=2||Math.abs(xs[1]-90)>.01))throw new AssertionError("JNI multi-contact movement");
                    if(masked==6&&(action!=6||ids.length!=2))throw new AssertionError("JNI pointer up retains lifted contact");
                    if(masked==3&&(ids.length!=1||ids[0]!=1))throw new AssertionError("JNI cancellation stable remaining ID");
                }
                public void onPointer(int a,float x,float y,int source,long time){if(a<4)throw new AssertionError("Touch collapsed to mouse");}
                public void onKey(int a,int k,int s,int r,int m,int f,long d,long t){}
                public void onText(String text,long time){}
            });
            if(!actions.equals(Arrays.asList(0,261,2,6,3)))throw new AssertionError("Touch lifecycle "+actions);
        }
        System.out.println("SHIRAZ_JNI_SURFACE_OK");
    }
}
