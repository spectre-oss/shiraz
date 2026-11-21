// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

/** Custom triangle or triangle-strip mesh with programmable AGSL uniforms. */
public class Mesh {
    public static final int TRIANGLES = 0;
    public static final int TRIANGLE_STRIP = 1;

    final MeshSpecification meshSpec;
    final int mode;
    final int vertexCount;
    final RectF bounds;
    private final Map<String, float[]> floatUniforms = new HashMap<String, float[]>();
    private final Map<String, int[]> intUniforms = new HashMap<String, int[]>();
    private final Map<String, Integer> colorUniforms = new HashMap<String, Integer>();

    public Mesh(
            MeshSpecification meshSpec,
            int mode,
            Buffer vertexBuffer,
            int vertexCount,
            RectF bounds) {
        this(meshSpec, mode, vertexBuffer, vertexCount, null, bounds, false);
    }

    public Mesh(
            MeshSpecification meshSpec,
            int mode,
            Buffer vertexBuffer,
            int vertexCount,
            ShortBuffer indexBuffer,
            RectF bounds) {
        this(meshSpec, mode, vertexBuffer, vertexCount, indexBuffer, bounds, true);
    }

    private Mesh(
            MeshSpecification meshSpec,
            int mode,
            Buffer vertexBuffer,
            int vertexCount,
            ShortBuffer indexBuffer,
            RectF bounds,
            boolean indexed) {
        if (meshSpec == null || vertexBuffer == null || bounds == null) {
            throw new NullPointerException("meshSpec/vertexBuffer/bounds");
        }
        if (indexed && indexBuffer == null) {
            throw new NullPointerException("indexBuffer");
        }
        if (mode != TRIANGLES && mode != TRIANGLE_STRIP) {
            throw new IllegalArgumentException("Invalid Mesh mode: " + mode);
        }
        if (vertexCount <= 0) {
            throw new IllegalArgumentException("vertexCount must be > 0");
        }
        this.meshSpec = meshSpec;
        this.mode = mode;
        this.vertexCount = vertexCount;
        this.bounds = new RectF(bounds);
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

    int shirazFallbackColor(Paint paint) {
        if (!colorUniforms.isEmpty()) {
            return colorUniforms.values().iterator().next().intValue();
        }
        return paint != null ? paint.getColor() : Color.BLACK;
    }
}
