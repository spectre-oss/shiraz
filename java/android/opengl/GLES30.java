// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;
import java.nio.IntBuffer;
/** Implemented GLES3 entry points on the caller's current EGL context. */
public class GLES30 extends GLES20 {
    public static final int GL_VERTEX_ARRAY_BINDING=0x85b5;
    public static native void glBindVertexArray(int array);
    public static native boolean glIsVertexArray(int array);
    public static native void glGenVertexArrays(int count,int[] arrays,int offset);
    public static native void glDeleteVertexArrays(int count,int[] arrays,int offset);
    private static int[] arrays(IntBuffer buffer,int count,boolean input){
        if(buffer==null||count<0||count>1048576||buffer.remaining()<count||(!input&&buffer.isReadOnly()))
            throw new IllegalArgumentException("Vertex array buffer range");
        int[] values=new int[count];if(input)buffer.duplicate().get(values);return values;
    }
    public static void glGenVertexArrays(int count,IntBuffer buffer){
        int[] values=arrays(buffer,count,false);glGenVertexArrays(count,values,0);buffer.duplicate().put(values);
    }
    public static void glDeleteVertexArrays(int count,IntBuffer buffer){
        int[] values=arrays(buffer,count,true);glDeleteVertexArrays(count,values,0);
    }
}
