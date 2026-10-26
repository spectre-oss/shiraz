// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Immutable snapshot of a color matrix; caller mutations cannot alter an installed filter. */
public class ColorMatrixColorFilter extends ColorFilter {
    private final float[] matrix;
    public ColorMatrixColorFilter(ColorMatrix value){this(value.getArray());}
    public ColorMatrixColorFilter(float[] value){ColorMatrix.valid(value);matrix=java.util.Arrays.copyOf(value,20);}
    public void getColorMatrix(ColorMatrix out){out.set(matrix);}
    public final float[] shirazMatrix(){return matrix.clone();}
}
