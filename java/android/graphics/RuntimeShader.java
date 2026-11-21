// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.HashMap;
import java.util.Map;

/** Programmable AGSL shader holding validated source, uniforms, and child shaders/buffers. */
public class RuntimeShader extends Shader {
    private final String sksl;
    private final Map<String, float[]> floatUniforms = new HashMap<String, float[]>();
    private final Map<String, int[]> intUniforms = new HashMap<String, int[]>();
    private final Map<String, Integer> colorUniforms = new HashMap<String, Integer>();
    private final Map<String, Shader> inputShaders = new HashMap<String, Shader>();
    private final Map<String, BitmapShader> inputBuffers = new HashMap<String, BitmapShader>();

    public RuntimeShader(String sksl) {
        if (sksl == null) {
            throw new NullPointerException("sksl");
        }
        if (sksl.trim().isEmpty()) {
            throw new IllegalArgumentException("RuntimeShader requires non-empty AGSL source");
        }
        this.sksl = sksl;
    }

    private static void checkName(String name) {
        if (name == null) {
            throw new NullPointerException("uniformName");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("uniformName must not be empty");
        }
    }

    public void setColorUniform(String uniformName, int color) {
        checkName(uniformName);
        colorUniforms.put(uniformName, Integer.valueOf(color));
    }

    public void setColorUniform(String uniformName, long color) {
        checkName(uniformName);
        colorUniforms.put(uniformName, Integer.valueOf(SweepGradient.packedColorToArgb(color)));
    }

    public void setColorUniform(String uniformName, Color color) {
        checkName(uniformName);
        if (color == null) {
            throw new NullPointerException("color");
        }
        colorUniforms.put(uniformName, Integer.valueOf(color.toArgb()));
    }

    public void setFloatUniform(String uniformName, float value) {
        setFloatUniform(uniformName, new float[]{value});
    }

    public void setFloatUniform(String uniformName, float value1, float value2) {
        setFloatUniform(uniformName, new float[]{value1, value2});
    }

    public void setFloatUniform(String uniformName, float value1, float value2, float value3) {
        setFloatUniform(uniformName, new float[]{value1, value2, value3});
    }

    public void setFloatUniform(
            String uniformName, float value1, float value2, float value3, float value4) {
        setFloatUniform(uniformName, new float[]{value1, value2, value3, value4});
    }

    public void setFloatUniform(String uniformName, float[] values) {
        checkName(uniformName);
        if (values == null) {
            throw new NullPointerException("values");
        }
        floatUniforms.put(uniformName, values.clone());
    }

    public void setIntUniform(String uniformName, int value) {
        setIntUniform(uniformName, new int[]{value});
    }

    public void setIntUniform(String uniformName, int value1, int value2) {
        setIntUniform(uniformName, new int[]{value1, value2});
    }

    public void setIntUniform(String uniformName, int value1, int value2, int value3) {
        setIntUniform(uniformName, new int[]{value1, value2, value3});
    }

    public void setIntUniform(
            String uniformName, int value1, int value2, int value3, int value4) {
        setIntUniform(uniformName, new int[]{value1, value2, value3, value4});
    }

    public void setIntUniform(String uniformName, int[] values) {
        checkName(uniformName);
        if (values == null) {
            throw new NullPointerException("values");
        }
        intUniforms.put(uniformName, values.clone());
    }

    public void setInputShader(String shaderName, Shader shader) {
        if (shaderName == null) {
            throw new NullPointerException("shaderName");
        }
        if (shaderName.isEmpty()) {
            throw new IllegalArgumentException("shaderName must not be empty");
        }
        if (shader == null) {
            throw new NullPointerException("shader");
        }
        inputShaders.put(shaderName, shader);
    }

    public void setInputBuffer(String shaderName, BitmapShader shader) {
        if (shaderName == null) {
            throw new NullPointerException("shaderName");
        }
        if (shaderName.isEmpty()) {
            throw new IllegalArgumentException("shaderName must not be empty");
        }
        if (shader == null) {
            throw new NullPointerException("shader");
        }
        inputBuffers.put(shaderName, shader);
    }

    String shirazSource() {
        return sksl;
    }

    Bitmap rasterFallback(int width, int height, float[] canvasMatrix) {
        if (!inputBuffers.isEmpty()) {
            BitmapShader first = inputBuffers.values().iterator().next();
            Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint();
            p.setShader(first);
            c.drawRect(0, 0, width, height, p);
            return out;
        }
        int fill = 0xff000000;
        if (!colorUniforms.isEmpty()) {
            fill = colorUniforms.values().iterator().next().intValue();
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.eraseColor(fill);
        return out;
    }
}
