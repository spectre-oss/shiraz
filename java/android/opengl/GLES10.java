// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

public class GLES10 {
    private static final ThreadLocal<shiraz.runtime.HostGL10> HOST_GL10 = new ThreadLocal<shiraz.runtime.HostGL10>();
    private static shiraz.runtime.HostGL10 hostGl10() {
        shiraz.runtime.HostGL10 gl = HOST_GL10.get();
        if (gl == null) { gl = new shiraz.runtime.HostGL10(); HOST_GL10.set(gl); }
        return gl;
    }
    public static final int GL_ADD = 260;
    public static final int GL_ALIASED_LINE_WIDTH_RANGE = 33902;
    public static final int GL_ALIASED_POINT_SIZE_RANGE = 33901;
    public static final int GL_ALPHA = 6406;
    public static final int GL_ALPHA_BITS = 3413;
    public static final int GL_ALPHA_TEST = 3008;
    public static final int GL_ALWAYS = 519;
    public static final int GL_AMBIENT = 4608;
    public static final int GL_AMBIENT_AND_DIFFUSE = 5634;
    public static final int GL_AND = 5377;
    public static final int GL_AND_INVERTED = 5380;
    public static final int GL_AND_REVERSE = 5378;
    public static final int GL_BACK = 1029;
    public static final int GL_BLEND = 3042;
    public static final int GL_BLUE_BITS = 3412;
    public static final int GL_BYTE = 5120;
    public static final int GL_CCW = 2305;
    public static final int GL_CLAMP_TO_EDGE = 33071;
    public static final int GL_CLEAR = 5376;
    public static final int GL_COLOR_ARRAY = 32886;
    public static final int GL_COLOR_BUFFER_BIT = 16384;
    public static final int GL_COLOR_LOGIC_OP = 3058;
    public static final int GL_COLOR_MATERIAL = 2903;
    public static final int GL_COMPRESSED_TEXTURE_FORMATS = 34467;
    public static final int GL_CONSTANT_ATTENUATION = 4615;
    public static final int GL_COPY = 5379;
    public static final int GL_COPY_INVERTED = 5388;
    public static final int GL_CULL_FACE = 2884;
    public static final int GL_CW = 2304;
    public static final int GL_DECAL = 8449;
    public static final int GL_DECR = 7683;
    public static final int GL_DEPTH_BITS = 3414;
    public static final int GL_DEPTH_BUFFER_BIT = 256;
    public static final int GL_DEPTH_TEST = 2929;
    public static final int GL_DIFFUSE = 4609;
    public static final int GL_DITHER = 3024;
    public static final int GL_DONT_CARE = 4352;
    public static final int GL_DST_ALPHA = 772;
    public static final int GL_DST_COLOR = 774;
    public static final int GL_EMISSION = 5632;
    public static final int GL_EQUAL = 514;
    public static final int GL_EQUIV = 5385;
    public static final int GL_EXP = 2048;
    public static final int GL_EXP2 = 2049;
    public static final int GL_EXTENSIONS = 7939;
    public static final int GL_FALSE = 0;
    public static final int GL_FASTEST = 4353;
    public static final int GL_FIXED = 5132;
    public static final int GL_FLAT = 7424;
    public static final int GL_FLOAT = 5126;
    public static final int GL_FOG = 2912;
    public static final int GL_FOG_COLOR = 2918;
    public static final int GL_FOG_DENSITY = 2914;
    public static final int GL_FOG_END = 2916;
    public static final int GL_FOG_HINT = 3156;
    public static final int GL_FOG_MODE = 2917;
    public static final int GL_FOG_START = 2915;
    public static final int GL_FRONT = 1028;
    public static final int GL_FRONT_AND_BACK = 1032;
    public static final int GL_GEQUAL = 518;
    public static final int GL_GREATER = 516;
    public static final int GL_GREEN_BITS = 3411;
    public static final int GL_IMPLEMENTATION_COLOR_READ_FORMAT_OES = 35739;
    public static final int GL_IMPLEMENTATION_COLOR_READ_TYPE_OES = 35738;
    public static final int GL_INCR = 7682;
    public static final int GL_INVALID_ENUM = 1280;
    public static final int GL_INVALID_OPERATION = 1282;
    public static final int GL_INVALID_VALUE = 1281;
    public static final int GL_INVERT = 5386;
    public static final int GL_KEEP = 7680;
    public static final int GL_LEQUAL = 515;
    public static final int GL_LESS = 513;
    public static final int GL_LIGHT0 = 16384;
    public static final int GL_LIGHT1 = 16385;
    public static final int GL_LIGHT2 = 16386;
    public static final int GL_LIGHT3 = 16387;
    public static final int GL_LIGHT4 = 16388;
    public static final int GL_LIGHT5 = 16389;
    public static final int GL_LIGHT6 = 16390;
    public static final int GL_LIGHT7 = 16391;
    public static final int GL_LIGHTING = 2896;
    public static final int GL_LIGHT_MODEL_AMBIENT = 2899;
    public static final int GL_LIGHT_MODEL_TWO_SIDE = 2898;
    public static final int GL_LINEAR = 9729;
    public static final int GL_LINEAR_ATTENUATION = 4616;
    public static final int GL_LINEAR_MIPMAP_LINEAR = 9987;
    public static final int GL_LINEAR_MIPMAP_NEAREST = 9985;
    public static final int GL_LINES = 1;
    public static final int GL_LINE_LOOP = 2;
    public static final int GL_LINE_SMOOTH = 2848;
    public static final int GL_LINE_SMOOTH_HINT = 3154;
    public static final int GL_LINE_STRIP = 3;
    public static final int GL_LUMINANCE = 6409;
    public static final int GL_LUMINANCE_ALPHA = 6410;
    public static final int GL_MAX_ELEMENTS_INDICES = 33001;
    public static final int GL_MAX_ELEMENTS_VERTICES = 33000;
    public static final int GL_MAX_LIGHTS = 3377;
    public static final int GL_MAX_MODELVIEW_STACK_DEPTH = 3382;
    public static final int GL_MAX_PROJECTION_STACK_DEPTH = 3384;
    public static final int GL_MAX_TEXTURE_SIZE = 3379;
    public static final int GL_MAX_TEXTURE_STACK_DEPTH = 3385;
    public static final int GL_MAX_TEXTURE_UNITS = 34018;
    public static final int GL_MAX_VIEWPORT_DIMS = 3386;
    public static final int GL_MODELVIEW = 5888;
    public static final int GL_MODULATE = 8448;
    public static final int GL_MULTISAMPLE = 32925;
    public static final int GL_NAND = 5390;
    public static final int GL_NEAREST = 9728;
    public static final int GL_NEAREST_MIPMAP_LINEAR = 9986;
    public static final int GL_NEAREST_MIPMAP_NEAREST = 9984;
    public static final int GL_NEVER = 512;
    public static final int GL_NICEST = 4354;
    public static final int GL_NOOP = 5381;
    public static final int GL_NOR = 5384;
    public static final int GL_NORMALIZE = 2977;
    public static final int GL_NORMAL_ARRAY = 32885;
    public static final int GL_NOTEQUAL = 517;
    public static final int GL_NO_ERROR = 0;
    public static final int GL_NUM_COMPRESSED_TEXTURE_FORMATS = 34466;
    public static final int GL_ONE = 1;
    public static final int GL_ONE_MINUS_DST_ALPHA = 773;
    public static final int GL_ONE_MINUS_DST_COLOR = 775;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 771;
    public static final int GL_ONE_MINUS_SRC_COLOR = 769;
    public static final int GL_OR = 5383;
    public static final int GL_OR_INVERTED = 5389;
    public static final int GL_OR_REVERSE = 5387;
    public static final int GL_OUT_OF_MEMORY = 1285;
    public static final int GL_PACK_ALIGNMENT = 3333;
    public static final int GL_PALETTE4_R5_G6_B5_OES = 35730;
    public static final int GL_PALETTE4_RGB5_A1_OES = 35732;
    public static final int GL_PALETTE4_RGB8_OES = 35728;
    public static final int GL_PALETTE4_RGBA4_OES = 35731;
    public static final int GL_PALETTE4_RGBA8_OES = 35729;
    public static final int GL_PALETTE8_R5_G6_B5_OES = 35735;
    public static final int GL_PALETTE8_RGB5_A1_OES = 35737;
    public static final int GL_PALETTE8_RGB8_OES = 35733;
    public static final int GL_PALETTE8_RGBA4_OES = 35736;
    public static final int GL_PALETTE8_RGBA8_OES = 35734;
    public static final int GL_PERSPECTIVE_CORRECTION_HINT = 3152;
    public static final int GL_POINTS = 0;
    public static final int GL_POINT_FADE_THRESHOLD_SIZE = 33064;
    public static final int GL_POINT_SIZE = 2833;
    public static final int GL_POINT_SMOOTH = 2832;
    public static final int GL_POINT_SMOOTH_HINT = 3153;
    public static final int GL_POLYGON_OFFSET_FILL = 32823;
    public static final int GL_POLYGON_SMOOTH_HINT = 3155;
    public static final int GL_POSITION = 4611;
    public static final int GL_PROJECTION = 5889;
    public static final int GL_QUADRATIC_ATTENUATION = 4617;
    public static final int GL_RED_BITS = 3410;
    public static final int GL_RENDERER = 7937;
    public static final int GL_REPEAT = 10497;
    public static final int GL_REPLACE = 7681;
    public static final int GL_RESCALE_NORMAL = 32826;
    public static final int GL_RGB = 6407;
    public static final int GL_RGBA = 6408;
    public static final int GL_SAMPLE_ALPHA_TO_COVERAGE = 32926;
    public static final int GL_SAMPLE_ALPHA_TO_ONE = 32927;
    public static final int GL_SAMPLE_COVERAGE = 32928;
    public static final int GL_SCISSOR_TEST = 3089;
    public static final int GL_SET = 5391;
    public static final int GL_SHININESS = 5633;
    public static final int GL_SHORT = 5122;
    public static final int GL_SMOOTH = 7425;
    public static final int GL_SMOOTH_LINE_WIDTH_RANGE = 2850;
    public static final int GL_SMOOTH_POINT_SIZE_RANGE = 2834;
    public static final int GL_SPECULAR = 4610;
    public static final int GL_SPOT_CUTOFF = 4614;
    public static final int GL_SPOT_DIRECTION = 4612;
    public static final int GL_SPOT_EXPONENT = 4613;
    public static final int GL_SRC_ALPHA = 770;
    public static final int GL_SRC_ALPHA_SATURATE = 776;
    public static final int GL_SRC_COLOR = 768;
    public static final int GL_STACK_OVERFLOW = 1283;
    public static final int GL_STACK_UNDERFLOW = 1284;
    public static final int GL_STENCIL_BITS = 3415;
    public static final int GL_STENCIL_BUFFER_BIT = 1024;
    public static final int GL_STENCIL_TEST = 2960;
    public static final int GL_SUBPIXEL_BITS = 3408;
    public static final int GL_TEXTURE = 5890;
    public static final int GL_TEXTURE0 = 33984;
    public static final int GL_TEXTURE1 = 33985;
    public static final int GL_TEXTURE10 = 33994;
    public static final int GL_TEXTURE11 = 33995;
    public static final int GL_TEXTURE12 = 33996;
    public static final int GL_TEXTURE13 = 33997;
    public static final int GL_TEXTURE14 = 33998;
    public static final int GL_TEXTURE15 = 33999;
    public static final int GL_TEXTURE16 = 34000;
    public static final int GL_TEXTURE17 = 34001;
    public static final int GL_TEXTURE18 = 34002;
    public static final int GL_TEXTURE19 = 34003;
    public static final int GL_TEXTURE2 = 33986;
    public static final int GL_TEXTURE20 = 34004;
    public static final int GL_TEXTURE21 = 34005;
    public static final int GL_TEXTURE22 = 34006;
    public static final int GL_TEXTURE23 = 34007;
    public static final int GL_TEXTURE24 = 34008;
    public static final int GL_TEXTURE25 = 34009;
    public static final int GL_TEXTURE26 = 34010;
    public static final int GL_TEXTURE27 = 34011;
    public static final int GL_TEXTURE28 = 34012;
    public static final int GL_TEXTURE29 = 34013;
    public static final int GL_TEXTURE3 = 33987;
    public static final int GL_TEXTURE30 = 34014;
    public static final int GL_TEXTURE31 = 34015;
    public static final int GL_TEXTURE4 = 33988;
    public static final int GL_TEXTURE5 = 33989;
    public static final int GL_TEXTURE6 = 33990;
    public static final int GL_TEXTURE7 = 33991;
    public static final int GL_TEXTURE8 = 33992;
    public static final int GL_TEXTURE9 = 33993;
    public static final int GL_TEXTURE_2D = 3553;
    public static final int GL_TEXTURE_COORD_ARRAY = 32888;
    public static final int GL_TEXTURE_ENV = 8960;
    public static final int GL_TEXTURE_ENV_COLOR = 8705;
    public static final int GL_TEXTURE_ENV_MODE = 8704;
    public static final int GL_TEXTURE_MAG_FILTER = 10240;
    public static final int GL_TEXTURE_MIN_FILTER = 10241;
    public static final int GL_TEXTURE_WRAP_S = 10242;
    public static final int GL_TEXTURE_WRAP_T = 10243;
    public static final int GL_TRIANGLES = 4;
    public static final int GL_TRIANGLE_FAN = 6;
    public static final int GL_TRIANGLE_STRIP = 5;
    public static final int GL_TRUE = 1;
    public static final int GL_UNPACK_ALIGNMENT = 3317;
    public static final int GL_UNSIGNED_BYTE = 5121;
    public static final int GL_UNSIGNED_SHORT = 5123;
    public static final int GL_UNSIGNED_SHORT_4_4_4_4 = 32819;
    public static final int GL_UNSIGNED_SHORT_5_5_5_1 = 32820;
    public static final int GL_UNSIGNED_SHORT_5_6_5 = 33635;
    public static final int GL_VENDOR = 7936;
    public static final int GL_VERSION = 7938;
    public static final int GL_VERTEX_ARRAY = 32884;
    public static final int GL_XOR = 5382;
    public static final int GL_ZERO = 0;
    public GLES10() {}
    public static void glActiveTexture(int arg0) { throw new UnsupportedOperationException("GLES10.glActiveTexture is not backed by Shiraz host GL bridge"); }
    public static void glAlphaFunc(int arg0, float arg1) { throw new UnsupportedOperationException("GLES10.glAlphaFunc is not backed by Shiraz host GL bridge"); }
    public static void glAlphaFuncx(int arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glAlphaFuncx is not backed by Shiraz host GL bridge"); }
    public static void glBindTexture(int arg0, int arg1) { hostGl10().glBindTexture(arg0, arg1); }
    public static void glBlendFunc(int arg0, int arg1) { hostGl10().glBlendFunc(arg0, arg1); }
    public static void glClear(int arg0) { hostGl10().glClear(arg0); }
    public static void glClearColor(float arg0, float arg1, float arg2, float arg3) { hostGl10().glClearColor(arg0, arg1, arg2, arg3); }
    public static void glClearColorx(int arg0, int arg1, int arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glClearColorx is not backed by Shiraz host GL bridge"); }
    public static void glClearDepthf(float arg0) { throw new UnsupportedOperationException("GLES10.glClearDepthf is not backed by Shiraz host GL bridge"); }
    public static void glClearDepthx(int arg0) { throw new UnsupportedOperationException("GLES10.glClearDepthx is not backed by Shiraz host GL bridge"); }
    public static void glClearStencil(int arg0) { throw new UnsupportedOperationException("GLES10.glClearStencil is not backed by Shiraz host GL bridge"); }
    public static void glClientActiveTexture(int arg0) { throw new UnsupportedOperationException("GLES10.glClientActiveTexture is not backed by Shiraz host GL bridge"); }
    public static void glColor4f(float arg0, float arg1, float arg2, float arg3) { hostGl10().glColor4f(arg0, arg1, arg2, arg3); }
    public static void glColor4x(int arg0, int arg1, int arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glColor4x is not backed by Shiraz host GL bridge"); }
    public static void glColorMask(boolean arg0, boolean arg1, boolean arg2, boolean arg3) { throw new UnsupportedOperationException("GLES10.glColorMask is not backed by Shiraz host GL bridge"); }
    public static void glColorPointer(int arg0, int arg1, int arg2, java.nio.Buffer arg3) { throw new UnsupportedOperationException("GLES10.glColorPointer is not backed by Shiraz host GL bridge"); }
    public static void glCompressedTexImage2D(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, java.nio.Buffer arg7) { throw new UnsupportedOperationException("GLES10.glCompressedTexImage2D is not backed by Shiraz host GL bridge"); }
    public static void glCompressedTexSubImage2D(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7, java.nio.Buffer arg8) { throw new UnsupportedOperationException("GLES10.glCompressedTexSubImage2D is not backed by Shiraz host GL bridge"); }
    public static void glCopyTexImage2D(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7) { throw new UnsupportedOperationException("GLES10.glCopyTexImage2D is not backed by Shiraz host GL bridge"); }
    public static void glCopyTexSubImage2D(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7) { throw new UnsupportedOperationException("GLES10.glCopyTexSubImage2D is not backed by Shiraz host GL bridge"); }
    public static void glCullFace(int arg0) { throw new UnsupportedOperationException("GLES10.glCullFace is not backed by Shiraz host GL bridge"); }
    public static void glDeleteTextures(int arg0, int[] arg1, int arg2) { hostGl10().glDeleteTextures(arg0, arg1, arg2); }
    public static void glDeleteTextures(int arg0, java.nio.IntBuffer arg1) { throw new UnsupportedOperationException("GLES10.glDeleteTextures is not backed by Shiraz host GL bridge"); }
    public static void glDepthFunc(int arg0) { throw new UnsupportedOperationException("GLES10.glDepthFunc is not backed by Shiraz host GL bridge"); }
    public static void glDepthMask(boolean arg0) { throw new UnsupportedOperationException("GLES10.glDepthMask is not backed by Shiraz host GL bridge"); }
    public static void glDepthRangef(float arg0, float arg1) { throw new UnsupportedOperationException("GLES10.glDepthRangef is not backed by Shiraz host GL bridge"); }
    public static void glDepthRangex(int arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glDepthRangex is not backed by Shiraz host GL bridge"); }
    public static void glDisable(int arg0) { throw new UnsupportedOperationException("GLES10.glDisable is not backed by Shiraz host GL bridge"); }
    public static void glDisableClientState(int arg0) { throw new UnsupportedOperationException("GLES10.glDisableClientState is not backed by Shiraz host GL bridge"); }
    public static void glDrawArrays(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glDrawArrays is not backed by Shiraz host GL bridge"); }
    public static void glDrawElements(int arg0, int arg1, int arg2, java.nio.Buffer arg3) { hostGl10().glDrawElements(arg0, arg1, arg2, arg3); }
    public static void glEnable(int arg0) { hostGl10().glEnable(arg0); }
    public static void glEnableClientState(int arg0) { hostGl10().glEnableClientState(arg0); }
    public static void glFinish() { hostGl10().glFinish(); }
    public static void glFlush() { hostGl10().glFlush(); }
    public static void glFogf(int arg0, float arg1) { throw new UnsupportedOperationException("GLES10.glFogf is not backed by Shiraz host GL bridge"); }
    public static void glFogfv(int arg0, float[] arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glFogfv is not backed by Shiraz host GL bridge"); }
    public static void glFogfv(int arg0, java.nio.FloatBuffer arg1) { throw new UnsupportedOperationException("GLES10.glFogfv is not backed by Shiraz host GL bridge"); }
    public static void glFogx(int arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glFogx is not backed by Shiraz host GL bridge"); }
    public static void glFogxv(int arg0, int[] arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glFogxv is not backed by Shiraz host GL bridge"); }
    public static void glFogxv(int arg0, java.nio.IntBuffer arg1) { throw new UnsupportedOperationException("GLES10.glFogxv is not backed by Shiraz host GL bridge"); }
    public static void glFrontFace(int arg0) { throw new UnsupportedOperationException("GLES10.glFrontFace is not backed by Shiraz host GL bridge"); }
    public static void glFrustumf(float arg0, float arg1, float arg2, float arg3, float arg4, float arg5) { throw new UnsupportedOperationException("GLES10.glFrustumf is not backed by Shiraz host GL bridge"); }
    public static void glFrustumx(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5) { throw new UnsupportedOperationException("GLES10.glFrustumx is not backed by Shiraz host GL bridge"); }
    public static void glGenTextures(int arg0, int[] arg1, int arg2) { hostGl10().glGenTextures(arg0, arg1, arg2); }
    public static void glGenTextures(int arg0, java.nio.IntBuffer arg1) { throw new UnsupportedOperationException("GLES10.glGenTextures is not backed by Shiraz host GL bridge"); }
    public static int glGetError() { throw new UnsupportedOperationException("GLES10.glGetError is not backed by Shiraz host GL bridge"); }
    public static void glGetIntegerv(int arg0, int[] arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glGetIntegerv is not backed by Shiraz host GL bridge"); }
    public static void glGetIntegerv(int arg0, java.nio.IntBuffer arg1) { throw new UnsupportedOperationException("GLES10.glGetIntegerv is not backed by Shiraz host GL bridge"); }
    public static java.lang.String glGetString(int arg0) { return hostGl10().glGetString(arg0); }
    public static void glHint(int arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glHint is not backed by Shiraz host GL bridge"); }
    public static void glLightModelf(int arg0, float arg1) { throw new UnsupportedOperationException("GLES10.glLightModelf is not backed by Shiraz host GL bridge"); }
    public static void glLightModelfv(int arg0, float[] arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glLightModelfv is not backed by Shiraz host GL bridge"); }
    public static void glLightModelfv(int arg0, java.nio.FloatBuffer arg1) { throw new UnsupportedOperationException("GLES10.glLightModelfv is not backed by Shiraz host GL bridge"); }
    public static void glLightModelx(int arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glLightModelx is not backed by Shiraz host GL bridge"); }
    public static void glLightModelxv(int arg0, int[] arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glLightModelxv is not backed by Shiraz host GL bridge"); }
    public static void glLightModelxv(int arg0, java.nio.IntBuffer arg1) { throw new UnsupportedOperationException("GLES10.glLightModelxv is not backed by Shiraz host GL bridge"); }
    public static void glLightf(int arg0, int arg1, float arg2) { throw new UnsupportedOperationException("GLES10.glLightf is not backed by Shiraz host GL bridge"); }
    public static void glLightfv(int arg0, int arg1, float[] arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glLightfv is not backed by Shiraz host GL bridge"); }
    public static void glLightfv(int arg0, int arg1, java.nio.FloatBuffer arg2) { throw new UnsupportedOperationException("GLES10.glLightfv is not backed by Shiraz host GL bridge"); }
    public static void glLightx(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glLightx is not backed by Shiraz host GL bridge"); }
    public static void glLightxv(int arg0, int arg1, int[] arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glLightxv is not backed by Shiraz host GL bridge"); }
    public static void glLightxv(int arg0, int arg1, java.nio.IntBuffer arg2) { throw new UnsupportedOperationException("GLES10.glLightxv is not backed by Shiraz host GL bridge"); }
    public static void glLineWidth(float arg0) { throw new UnsupportedOperationException("GLES10.glLineWidth is not backed by Shiraz host GL bridge"); }
    public static void glLineWidthx(int arg0) { throw new UnsupportedOperationException("GLES10.glLineWidthx is not backed by Shiraz host GL bridge"); }
    public static void glLoadIdentity() { hostGl10().glLoadIdentity(); }
    public static void glLoadMatrixf(float[] arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glLoadMatrixf is not backed by Shiraz host GL bridge"); }
    public static void glLoadMatrixf(java.nio.FloatBuffer arg0) { throw new UnsupportedOperationException("GLES10.glLoadMatrixf is not backed by Shiraz host GL bridge"); }
    public static void glLoadMatrixx(int[] arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glLoadMatrixx is not backed by Shiraz host GL bridge"); }
    public static void glLoadMatrixx(java.nio.IntBuffer arg0) { throw new UnsupportedOperationException("GLES10.glLoadMatrixx is not backed by Shiraz host GL bridge"); }
    public static void glLogicOp(int arg0) { throw new UnsupportedOperationException("GLES10.glLogicOp is not backed by Shiraz host GL bridge"); }
    public static void glMaterialf(int arg0, int arg1, float arg2) { throw new UnsupportedOperationException("GLES10.glMaterialf is not backed by Shiraz host GL bridge"); }
    public static void glMaterialfv(int arg0, int arg1, float[] arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glMaterialfv is not backed by Shiraz host GL bridge"); }
    public static void glMaterialfv(int arg0, int arg1, java.nio.FloatBuffer arg2) { throw new UnsupportedOperationException("GLES10.glMaterialfv is not backed by Shiraz host GL bridge"); }
    public static void glMaterialx(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glMaterialx is not backed by Shiraz host GL bridge"); }
    public static void glMaterialxv(int arg0, int arg1, int[] arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glMaterialxv is not backed by Shiraz host GL bridge"); }
    public static void glMaterialxv(int arg0, int arg1, java.nio.IntBuffer arg2) { throw new UnsupportedOperationException("GLES10.glMaterialxv is not backed by Shiraz host GL bridge"); }
    public static void glMatrixMode(int arg0) { hostGl10().glMatrixMode(arg0); }
    public static void glMultMatrixf(float[] arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glMultMatrixf is not backed by Shiraz host GL bridge"); }
    public static void glMultMatrixf(java.nio.FloatBuffer arg0) { throw new UnsupportedOperationException("GLES10.glMultMatrixf is not backed by Shiraz host GL bridge"); }
    public static void glMultMatrixx(int[] arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glMultMatrixx is not backed by Shiraz host GL bridge"); }
    public static void glMultMatrixx(java.nio.IntBuffer arg0) { throw new UnsupportedOperationException("GLES10.glMultMatrixx is not backed by Shiraz host GL bridge"); }
    public static void glMultiTexCoord4f(int arg0, float arg1, float arg2, float arg3, float arg4) { throw new UnsupportedOperationException("GLES10.glMultiTexCoord4f is not backed by Shiraz host GL bridge"); }
    public static void glMultiTexCoord4x(int arg0, int arg1, int arg2, int arg3, int arg4) { throw new UnsupportedOperationException("GLES10.glMultiTexCoord4x is not backed by Shiraz host GL bridge"); }
    public static void glNormal3f(float arg0, float arg1, float arg2) { throw new UnsupportedOperationException("GLES10.glNormal3f is not backed by Shiraz host GL bridge"); }
    public static void glNormal3x(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glNormal3x is not backed by Shiraz host GL bridge"); }
    public static void glNormalPointer(int arg0, int arg1, java.nio.Buffer arg2) { throw new UnsupportedOperationException("GLES10.glNormalPointer is not backed by Shiraz host GL bridge"); }
    public static void glOrthof(float arg0, float arg1, float arg2, float arg3, float arg4, float arg5) { hostGl10().glOrthof(arg0, arg1, arg2, arg3, arg4, arg5); }
    public static void glOrthox(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5) { throw new UnsupportedOperationException("GLES10.glOrthox is not backed by Shiraz host GL bridge"); }
    public static void glPixelStorei(int arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glPixelStorei is not backed by Shiraz host GL bridge"); }
    public static void glPointSize(float arg0) { throw new UnsupportedOperationException("GLES10.glPointSize is not backed by Shiraz host GL bridge"); }
    public static void glPointSizex(int arg0) { throw new UnsupportedOperationException("GLES10.glPointSizex is not backed by Shiraz host GL bridge"); }
    public static void glPolygonOffset(float arg0, float arg1) { throw new UnsupportedOperationException("GLES10.glPolygonOffset is not backed by Shiraz host GL bridge"); }
    public static void glPolygonOffsetx(int arg0, int arg1) { throw new UnsupportedOperationException("GLES10.glPolygonOffsetx is not backed by Shiraz host GL bridge"); }
    public static void glPopMatrix() { throw new UnsupportedOperationException("GLES10.glPopMatrix is not backed by Shiraz host GL bridge"); }
    public static void glPushMatrix() { throw new UnsupportedOperationException("GLES10.glPushMatrix is not backed by Shiraz host GL bridge"); }
    public static void glReadPixels(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, java.nio.Buffer arg6) { hostGl10().glReadPixels(arg0, arg1, arg2, arg3, arg4, arg5, arg6); }
    public static void glRotatef(float arg0, float arg1, float arg2, float arg3) { throw new UnsupportedOperationException("GLES10.glRotatef is not backed by Shiraz host GL bridge"); }
    public static void glRotatex(int arg0, int arg1, int arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glRotatex is not backed by Shiraz host GL bridge"); }
    public static void glSampleCoverage(float arg0, boolean arg1) { throw new UnsupportedOperationException("GLES10.glSampleCoverage is not backed by Shiraz host GL bridge"); }
    public static void glSampleCoveragex(int arg0, boolean arg1) { throw new UnsupportedOperationException("GLES10.glSampleCoveragex is not backed by Shiraz host GL bridge"); }
    public static void glScalef(float arg0, float arg1, float arg2) { throw new UnsupportedOperationException("GLES10.glScalef is not backed by Shiraz host GL bridge"); }
    public static void glScalex(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glScalex is not backed by Shiraz host GL bridge"); }
    public static void glScissor(int arg0, int arg1, int arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glScissor is not backed by Shiraz host GL bridge"); }
    public static void glShadeModel(int arg0) { throw new UnsupportedOperationException("GLES10.glShadeModel is not backed by Shiraz host GL bridge"); }
    public static void glStencilFunc(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glStencilFunc is not backed by Shiraz host GL bridge"); }
    public static void glStencilMask(int arg0) { throw new UnsupportedOperationException("GLES10.glStencilMask is not backed by Shiraz host GL bridge"); }
    public static void glStencilOp(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glStencilOp is not backed by Shiraz host GL bridge"); }
    public static void glTexCoordPointer(int arg0, int arg1, int arg2, java.nio.Buffer arg3) { hostGl10().glTexCoordPointer(arg0, arg1, arg2, arg3); }
    public static void glTexEnvf(int arg0, int arg1, float arg2) { throw new UnsupportedOperationException("GLES10.glTexEnvf is not backed by Shiraz host GL bridge"); }
    public static void glTexEnvfv(int arg0, int arg1, float[] arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glTexEnvfv is not backed by Shiraz host GL bridge"); }
    public static void glTexEnvfv(int arg0, int arg1, java.nio.FloatBuffer arg2) { throw new UnsupportedOperationException("GLES10.glTexEnvfv is not backed by Shiraz host GL bridge"); }
    public static void glTexEnvx(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glTexEnvx is not backed by Shiraz host GL bridge"); }
    public static void glTexEnvxv(int arg0, int arg1, int[] arg2, int arg3) { throw new UnsupportedOperationException("GLES10.glTexEnvxv is not backed by Shiraz host GL bridge"); }
    public static void glTexEnvxv(int arg0, int arg1, java.nio.IntBuffer arg2) { throw new UnsupportedOperationException("GLES10.glTexEnvxv is not backed by Shiraz host GL bridge"); }
    public static void glTexImage2D(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7, java.nio.Buffer arg8) { throw new UnsupportedOperationException("GLES10.glTexImage2D is not backed by Shiraz host GL bridge"); }
    public static void glTexParameterf(int arg0, int arg1, float arg2) { hostGl10().glTexParameterf(arg0, arg1, arg2); }
    public static void glTexParameterx(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glTexParameterx is not backed by Shiraz host GL bridge"); }
    public static void glTexSubImage2D(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7, java.nio.Buffer arg8) { throw new UnsupportedOperationException("GLES10.glTexSubImage2D is not backed by Shiraz host GL bridge"); }
    public static void glTranslatef(float arg0, float arg1, float arg2) { throw new UnsupportedOperationException("GLES10.glTranslatef is not backed by Shiraz host GL bridge"); }
    public static void glTranslatex(int arg0, int arg1, int arg2) { throw new UnsupportedOperationException("GLES10.glTranslatex is not backed by Shiraz host GL bridge"); }
    public static void glVertexPointer(int arg0, int arg1, int arg2, java.nio.Buffer arg3) { hostGl10().glVertexPointer(arg0, arg1, arg2, arg3); }
    public static void glViewport(int arg0, int arg1, int arg2, int arg3) { hostGl10().glViewport(arg0, arg1, arg2, arg3); }
}
