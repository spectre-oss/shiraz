// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/** Color metadata and CIE XYZ / RGB color conversion; software raster bitmaps require sRGB. */
public abstract class ColorSpace {
    public static final float[] ILLUMINANT_A = {0.44757f, 0.40745f};
    public static final float[] ILLUMINANT_B = {0.34842f, 0.35161f};
    public static final float[] ILLUMINANT_C = {0.31006f, 0.31616f};
    public static final float[] ILLUMINANT_D50 = {0.34567f, 0.35850f};
    public static final float[] ILLUMINANT_D55 = {0.33242f, 0.34743f};
    public static final float[] ILLUMINANT_D60 = {0.32168f, 0.33767f};
    public static final float[] ILLUMINANT_D65 = {0.31271f, 0.32902f};
    public static final float[] ILLUMINANT_D75 = {0.29902f, 0.31485f};
    public static final float[] ILLUMINANT_E = {0.33333f, 0.33333f};

    public static final int MIN_ID = -1;
    public static final int MAX_ID = 63;

    private static final int HAL_DATASPACE_V0_SRGB = 142671872;

    private static final float[] SRGB_PRIMARIES = {
        0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f
    };

    private static final Rgb.TransferParameters SRGB_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(
                    1.0 / 1.055, 0.055 / 1.055, 1.0 / 12.92, 0.04045, 2.4);

    public enum Named {
        SRGB,
        LINEAR_SRGB,
        EXTENDED_SRGB,
        LINEAR_EXTENDED_SRGB,
        BT709,
        BT2020,
        DCI_P3,
        DISPLAY_P3,
        NTSC_1953,
        SMPTE_C,
        ADOBE_RGB,
        PRO_PHOTO_RGB,
        ACES,
        ACESCG,
        CIE_XYZ,
        CIE_LAB,
        BT2020_HLG,
        BT2020_PQ
    }

    public enum Model {
        RGB(3),
        XYZ(3),
        LAB(3),
        CMYK(4);

        private final int mComponentCount;

        Model(int componentCount) {
            mComponentCount = componentCount;
        }

        public int getComponentCount() {
            return mComponentCount;
        }
    }

    public enum RenderIntent {
        PERCEPTUAL,
        RELATIVE,
        SATURATION,
        ABSOLUTE
    }

    public enum Adaptation {
        BRADFORD(new float[]{
            0.8951f, -0.7502f, 0.0389f,
            0.2664f, 1.7135f, -0.0685f,
            -0.1614f, 0.0367f, 1.0296f
        }),
        VON_KRIES(new float[]{
            0.40024f, -0.22630f, 0.0f,
            0.70760f, 1.16532f, 0.0f,
            -0.08081f, 0.04570f, 0.91822f
        }),
        CIECAT02(new float[]{
            0.7328f, -0.7036f, 0.0030f,
            0.4296f, 1.6975f, 0.0136f,
            -0.1624f, 0.0061f, 0.9834f
        });

        final float[] mTransform;

        Adaptation(float[] transform) {
            mTransform = transform;
        }
    }

    private static final Rgb SRGB = new Rgb(
            "sRGB IEC61966-2.1",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            null,
            SRGB_TRANSFER_PARAMETERS,
            0);

    private static final ColorSpace CIE_XYZ_INTERNAL = new Xyz("Generic XYZ", 14);

    private final String mName;
    private final Model mModel;
    private final int mId;

    ColorSpace(String name, Model model, int id) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("The name of a color space cannot be null and "
                    + "must contain at least 1 character");
        }
        if (model == null) {
            throw new IllegalArgumentException("A color space must have a model");
        }
        if (id < MIN_ID || id > MAX_ID) {
            throw new IllegalArgumentException(
                    "The id must be between " + MIN_ID + " and " + MAX_ID);
        }
        mName = name;
        mModel = model;
        mId = id;
    }

    public static ColorSpace get(Named name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (name != Named.SRGB) {
            throw new UnsupportedOperationException(
                    "Software raster supports only sRGB: " + name);
        }
        return SRGB;
    }

    public static ColorSpace getFromDataSpace(int dataSpace) {
        if (dataSpace == HAL_DATASPACE_V0_SRGB) {
            return SRGB;
        }
        return null;
    }

    public int getDataSpace() {
        return this == SRGB ? HAL_DATASPACE_V0_SRGB : 0;
    }

    public static ColorSpace match(
            float[] toXYZD50, Rgb.TransferParameters function) {
        if (toXYZD50 == null) {
            throw new NullPointerException("toXYZD50");
        }
        if (toXYZD50.length < 9) {
            throw new IllegalArgumentException("toXYZD50 must have at least 9 elements");
        }
        if (function == null) {
            throw new NullPointerException("function");
        }
        if (SRGB_TRANSFER_PARAMETERS.equals(function)) {
            ColorSpace adaptedSrgb = adapt(SRGB, ILLUMINANT_D50);
            if (adaptedSrgb instanceof Rgb) {
                float[] srgbD50 = ((Rgb) adaptedSrgb).mTransform;
                if (compareMatrices(srgbD50, toXYZD50)) {
                    return SRGB;
                }
            }
        }
        return null;
    }

    private static boolean compareMatrices(float[] a, float[] b) {
        for (int i = 0; i < 9; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-3f) {
                return false;
            }
        }
        return true;
    }

    public String getName() {
        return mName;
    }

    public int getId() {
        return mId;
    }

    public Model getModel() {
        return mModel;
    }

    public int getComponentCount() {
        return mModel.getComponentCount();
    }

    public abstract boolean isWideGamut();

    public boolean isSrgb() {
        return false;
    }

    public abstract float getMinValue(int component);

    public abstract float getMaxValue(int component);

    public float[] toXyz(float r, float g, float b) {
        return toXyz(new float[]{r, g, b});
    }

    public abstract float[] toXyz(float[] v);

    public float[] fromXyz(float x, float y, float z) {
        float[] xyz = new float[mModel.getComponentCount()];
        xyz[0] = x;
        xyz[1] = y;
        xyz[2] = z;
        return fromXyz(xyz);
    }

    public abstract float[] fromXyz(float[] v);

    @Override
    public String toString() {
        return mName + " (id=" + mId + ", model=" + mModel + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ColorSpace that = (ColorSpace) o;
        return mId == that.mId && mName.equals(that.mName) && mModel == that.mModel;
    }

    @Override
    public int hashCode() {
        int result = mName.hashCode();
        result = 31 * result + mModel.hashCode();
        result = 31 * result + mId;
        return result;
    }

    public static Connector connect(ColorSpace source, ColorSpace destination) {
        return connect(source, destination, RenderIntent.PERCEPTUAL);
    }

    public static Connector connect(
            ColorSpace source, ColorSpace destination, RenderIntent intent) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (destination == null) {
            throw new NullPointerException("destination");
        }
        if (intent == null) {
            throw new NullPointerException("intent");
        }
        if (source.equals(destination)) {
            return Connector.identity(source);
        }
        if (source.getModel() == Model.RGB && destination.getModel() == Model.RGB) {
            return new Connector.RgbConnector((Rgb) source, (Rgb) destination, intent);
        }
        return new Connector(source, destination, intent);
    }

    public static Connector connect(ColorSpace source) {
        return connect(source, RenderIntent.PERCEPTUAL);
    }

    public static Connector connect(ColorSpace source, RenderIntent intent) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (intent == null) {
            throw new NullPointerException("intent");
        }
        if (source.isSrgb() && intent == RenderIntent.PERCEPTUAL) {
            return new Connector(source, CIE_XYZ_INTERNAL, intent);
        }
        return connect(source, CIE_XYZ_INTERNAL, intent);
    }

    public static ColorSpace adapt(ColorSpace colorSpace, float[] whitePoint) {
        return adapt(colorSpace, whitePoint, Adaptation.BRADFORD);
    }

    public static ColorSpace adapt(
            ColorSpace colorSpace, float[] whitePoint, Adaptation adaptation) {
        if (colorSpace == null) {
            throw new NullPointerException("colorSpace");
        }
        if (whitePoint == null) {
            throw new NullPointerException("whitePoint");
        }
        if (adaptation == null) {
            throw new NullPointerException("adaptation");
        }
        if (colorSpace.getModel() == Model.RGB) {
            Rgb rgb = (Rgb) colorSpace;
            if (compareFloats(rgb.mWhitePoint, whitePoint)) {
                return colorSpace;
            }
            float[] xyz = whitePoint.length == 3
                    ? Arrays.copyOf(whitePoint, 3)
                    : xyYToXyz(whitePoint);
            float[] adaptationTransform = chromaticAdaptation(
                    adaptation.mTransform, xyYToXyz(rgb.getWhitePoint()), xyz);
            float[] transform = mul3x3(adaptationTransform, rgb.mTransform);
            return new Rgb(rgb, transform, whitePoint);
        }
        return colorSpace;
    }

    public static float[] chromaticAdaptation(
            Adaptation adaptation, float[] srcWhitePoint, float[] dstWhitePoint) {
        if (adaptation == null) {
            throw new NullPointerException("adaptation");
        }
        if (srcWhitePoint == null || dstWhitePoint == null) {
            throw new NullPointerException("whitePoint");
        }
        if ((srcWhitePoint.length != 2 && srcWhitePoint.length != 3)
                || (dstWhitePoint.length != 2 && dstWhitePoint.length != 3)) {
            throw new IllegalArgumentException("A white point array must have 2 or 3 floats");
        }
        float[] srcXyz = srcWhitePoint.length == 3
                ? Arrays.copyOf(srcWhitePoint, 3)
                : xyYToXyz(srcWhitePoint);
        float[] dstXyz = dstWhitePoint.length == 3
                ? Arrays.copyOf(dstWhitePoint, 3)
                : xyYToXyz(dstWhitePoint);
        if (compareFloats(srcXyz, dstXyz)) {
            return new float[]{
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f
            };
        }
        return chromaticAdaptation(adaptation.mTransform, srcXyz, dstXyz);
    }

    public static float[] cctToXyz(int cct) {
        if (cct < 1) {
            throw new IllegalArgumentException(
                    "Temperature must be greater than 0K, got " + cct);
        }
        final float icct = 1e3f / cct;
        final float icct2 = icct * icct;
        final float x = cct <= 4000.0f
                ? 0.179910f + 0.8776956f * icct - 0.2343589f * icct2
                        - 0.2661239f * icct2 * icct
                : 0.240390f + 0.2226347f * icct + 2.1070379f * icct2
                        - 3.0258469f * icct2 * icct;

        final float x2 = x * x;
        final float y = cct <= 2222.0f
                ? -0.20219683f + 2.18555832f * x - 1.34811020f * x2
                        - 1.1063814f * x2 * x
                : cct <= 4000.0f
                        ? -0.16748867f + 2.09137015f * x - 1.37418593f * x2
                                - 0.9549476f * x2 * x
                        : -0.37001483f + 3.75112997f * x - 5.8733867f * x2
                                + 3.0817580f * x2 * x;

        return xyYToXyz(new float[]{x, y});
    }

    static float[] xyYToXyz(float[] xyY) {
        return new float[]{
            xyY[0] / xyY[1],
            1.0f,
            (1.0f - xyY[0] - xyY[1]) / xyY[1]
        };
    }

    static boolean compareFloats(float[] a, float[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Float.compare(a[i], b[i]) != 0 && Math.abs(a[i] - b[i]) > 1e-3f) {
                return false;
            }
        }
        return true;
    }

    static float[] chromaticAdaptation(
            float[] matrix, float[] srcWhitePoint, float[] dstWhitePoint) {
        float[] srcLMS = mul3x3Float3(matrix, Arrays.copyOf(srcWhitePoint, 3));
        float[] dstLMS = mul3x3Float3(matrix, Arrays.copyOf(dstWhitePoint, 3));
        float[] LMS = {
            dstLMS[0] / srcLMS[0], 0.0f, 0.0f,
            0.0f, dstLMS[1] / srcLMS[1], 0.0f,
            0.0f, 0.0f, dstLMS[2] / srcLMS[2]
        };
        return mul3x3(inverse3x3(matrix), mul3x3(LMS, matrix));
    }

    static float[] inverse3x3(float[] m) {
        float a = m[0];
        float b = m[1];
        float c = m[2];
        float d = m[3];
        float e = m[4];
        float f = m[5];
        float g = m[6];
        float h = m[7];
        float i = m[8];

        float xA = e * i - f * h;
        float xB = f * g - d * i;
        float xC = d * h - e * g;

        float det = a * xA + b * xB + c * xC;
        float[] inverted = new float[m.length];
        inverted[0] = xA / det;
        inverted[1] = (c * h - b * i) / det;
        inverted[2] = (b * f - c * e) / det;
        inverted[3] = xB / det;
        inverted[4] = (a * i - c * g) / det;
        inverted[5] = (c * d - a * f) / det;
        inverted[6] = xC / det;
        inverted[7] = (b * g - a * h) / det;
        inverted[8] = (a * e - b * d) / det;
        return inverted;
    }

    static float[] mul3x3(float[] lhs, float[] rhs) {
        float[] r = new float[9];
        r[0] = lhs[0] * rhs[0] + lhs[3] * rhs[1] + lhs[6] * rhs[2];
        r[1] = lhs[1] * rhs[0] + lhs[4] * rhs[1] + lhs[7] * rhs[2];
        r[2] = lhs[2] * rhs[0] + lhs[5] * rhs[1] + lhs[8] * rhs[2];
        r[3] = lhs[0] * rhs[3] + lhs[3] * rhs[4] + lhs[6] * rhs[5];
        r[4] = lhs[1] * rhs[3] + lhs[4] * rhs[4] + lhs[7] * rhs[5];
        r[5] = lhs[2] * rhs[3] + lhs[5] * rhs[4] + lhs[8] * rhs[5];
        r[6] = lhs[0] * rhs[6] + lhs[3] * rhs[7] + lhs[6] * rhs[8];
        r[7] = lhs[1] * rhs[6] + lhs[4] * rhs[7] + lhs[7] * rhs[8];
        r[8] = lhs[2] * rhs[6] + lhs[5] * rhs[7] + lhs[8] * rhs[8];
        return r;
    }

    static float[] mul3x3Float3(float[] lhs, float[] rhs) {
        float r0 = rhs[0];
        float r1 = rhs[1];
        float r2 = rhs[2];
        rhs[0] = lhs[0] * r0 + lhs[3] * r1 + lhs[6] * r2;
        rhs[1] = lhs[1] * r0 + lhs[4] * r1 + lhs[7] * r2;
        rhs[2] = lhs[2] * r0 + lhs[5] * r1 + lhs[8] * r2;
        return rhs;
    }

    static float[] mul3x3Diag(float[] lhs, float[] rhs) {
        return new float[]{
            lhs[0] * rhs[0], lhs[1] * rhs[1], lhs[2] * rhs[2],
            lhs[0] * rhs[3], lhs[1] * rhs[4], lhs[2] * rhs[5],
            lhs[0] * rhs[6], lhs[1] * rhs[7], lhs[2] * rhs[8]
        };
    }

    static double rcpResponse(
            double x, double a, double b, double c, double d, double g) {
        return x >= d * c ? (Math.pow(x, 1.0 / g) - b) / a : x / c;
    }

    static double response(
            double x, double a, double b, double c, double d, double g) {
        return x >= d ? Math.pow(a * x + b, g) : c * x;
    }

    static double rcpResponse(
            double x,
            double a,
            double b,
            double c,
            double d,
            double e,
            double f,
            double g) {
        return x >= d * c ? (Math.pow(x - e, 1.0 / g) - b) / a : (x - f) / c;
    }

    static double response(
            double x,
            double a,
            double b,
            double c,
            double d,
            double e,
            double f,
            double g) {
        return x >= d ? Math.pow(a * x + b, g) + e : c * x + f;
    }

    private static final class Xyz extends ColorSpace {
        Xyz(String name, int id) {
            super(name, Model.XYZ, id);
        }

        @Override
        public boolean isWideGamut() {
            return true;
        }

        @Override
        public float getMinValue(int component) {
            if (component < 0 || component >= 3) {
                throw new IllegalArgumentException("XYZ component: " + component);
            }
            return -2.0f;
        }

        @Override
        public float getMaxValue(int component) {
            if (component < 0 || component >= 3) {
                throw new IllegalArgumentException("XYZ component: " + component);
            }
            return 2.0f;
        }

        @Override
        public float[] toXyz(float[] v) {
            v[0] = clamp(v[0]);
            v[1] = clamp(v[1]);
            v[2] = clamp(v[2]);
            return v;
        }

        @Override
        public float[] fromXyz(float[] v) {
            v[0] = clamp(v[0]);
            v[1] = clamp(v[1]);
            v[2] = clamp(v[2]);
            return v;
        }

        private static float clamp(float x) {
            return x < -2.0f ? -2.0f : (x > 2.0f ? 2.0f : x);
        }
    }

    public static class Connector {
        private final ColorSpace mSource;
        private final ColorSpace mDestination;
        private final ColorSpace mTransformSource;
        private final ColorSpace mTransformDestination;
        private final RenderIntent mIntent;
        private final float[] mTransform;

        Connector(ColorSpace source, ColorSpace destination, RenderIntent intent) {
            this(
                    source,
                    destination,
                    source.getModel() == Model.RGB
                            ? adapt(source, ILLUMINANT_D50)
                            : source,
                    destination.getModel() == Model.RGB
                            ? adapt(destination, ILLUMINANT_D50)
                            : destination,
                    intent,
                    computeTransform(source, destination, intent));
        }

        private Connector(
                ColorSpace source,
                ColorSpace destination,
                ColorSpace transformSource,
                ColorSpace transformDestination,
                RenderIntent intent,
                float[] transform) {
            mSource = source;
            mDestination = destination;
            mTransformSource = transformSource;
            mTransformDestination = transformDestination;
            mIntent = intent;
            mTransform = transform;
        }

        private static float[] computeTransform(
                ColorSpace source, ColorSpace destination, RenderIntent intent) {
            if (intent != RenderIntent.ABSOLUTE) {
                return null;
            }
            boolean srcRGB = source.getModel() == Model.RGB;
            boolean dstRGB = destination.getModel() == Model.RGB;
            if (srcRGB && dstRGB) {
                return null;
            }
            if (srcRGB || dstRGB) {
                Rgb rgb = (Rgb) (srcRGB ? source : destination);
                float[] srcXYZ = srcRGB ? xyYToXyz(rgb.mWhitePoint) : xyYToXyz(ILLUMINANT_D50);
                float[] dstXYZ = dstRGB ? xyYToXyz(rgb.mWhitePoint) : xyYToXyz(ILLUMINANT_D50);
                return new float[]{
                    srcXYZ[0] / dstXYZ[0],
                    srcXYZ[1] / dstXYZ[1],
                    srcXYZ[2] / dstXYZ[2]
                };
            }
            return null;
        }

        static Connector identity(final ColorSpace source) {
            return new Connector(source, source, RenderIntent.RELATIVE) {
                @Override
                public float[] transform(float[] v) {
                    return v;
                }
            };
        }

        public ColorSpace getSource() {
            return mSource;
        }

        public ColorSpace getDestination() {
            return mDestination;
        }

        public RenderIntent getRenderIntent() {
            return mIntent;
        }

        public float[] transform(float r, float g, float b) {
            return transform(new float[]{r, g, b});
        }

        public float[] transform(float[] v) {
            float[] xyz = mTransformSource.toXyz(v);
            if (mTransform != null) {
                xyz[0] *= mTransform[0];
                xyz[1] *= mTransform[1];
                xyz[2] *= mTransform[2];
            }
            return mTransformDestination.fromXyz(xyz);
        }

        private static class RgbConnector extends Connector {
            private final Rgb mSourceRgb;
            private final Rgb mDestinationRgb;
            private final float[] mMatrix;

            RgbConnector(Rgb source, Rgb destination, RenderIntent intent) {
                super(source, destination, source, destination, intent, null);
                mSourceRgb = source;
                mDestinationRgb = destination;
                mMatrix = computeRgbTransform(source, destination, intent);
            }

            @Override
            public float[] transform(float[] v) {
                v[0] = (float) mSourceRgb.mClampedEotf.applyAsDouble(v[0]);
                v[1] = (float) mSourceRgb.mClampedEotf.applyAsDouble(v[1]);
                v[2] = (float) mSourceRgb.mClampedEotf.applyAsDouble(v[2]);
                mul3x3Float3(mMatrix, v);
                v[0] = (float) mDestinationRgb.mClampedOetf.applyAsDouble(v[0]);
                v[1] = (float) mDestinationRgb.mClampedOetf.applyAsDouble(v[1]);
                v[2] = (float) mDestinationRgb.mClampedOetf.applyAsDouble(v[2]);
                return v;
            }

            private static float[] computeRgbTransform(
                    Rgb source, Rgb destination, RenderIntent intent) {
                if (compareFloats(source.mWhitePoint, destination.mWhitePoint)) {
                    return mul3x3(destination.mInverseTransform, source.mTransform);
                }
                float[] transform = source.mTransform;
                float[] inverseTransform = destination.mInverseTransform;
                float[] srcXYZ = xyYToXyz(source.mWhitePoint);
                float[] dstXYZ = xyYToXyz(destination.mWhitePoint);

                if (!compareFloats(source.mWhitePoint, ILLUMINANT_D50)) {
                    float[] srcAdaptation = chromaticAdaptation(
                            Adaptation.BRADFORD.mTransform,
                            srcXYZ,
                            xyYToXyz(ILLUMINANT_D50));
                    transform = mul3x3(srcAdaptation, source.mTransform);
                }
                if (!compareFloats(destination.mWhitePoint, ILLUMINANT_D50)) {
                    float[] dstAdaptation = chromaticAdaptation(
                            Adaptation.BRADFORD.mTransform,
                            dstXYZ,
                            xyYToXyz(ILLUMINANT_D50));
                    inverseTransform = inverse3x3(mul3x3(dstAdaptation, destination.mTransform));
                }
                if (intent == RenderIntent.ABSOLUTE) {
                    transform = mul3x3Diag(new float[]{
                        srcXYZ[0] / dstXYZ[0],
                        srcXYZ[1] / dstXYZ[1],
                        srcXYZ[2] / dstXYZ[2]
                    }, transform);
                }
                return mul3x3(inverseTransform, transform);
            }
        }
    }

    public static class Rgb extends ColorSpace {
        public static class TransferParameters {
            public final double a;
            public final double b;
            public final double c;
            public final double d;
            public final double e;
            public final double f;
            public final double g;

            public TransferParameters(double a, double b, double c, double d, double g) {
                this(a, b, c, d, 0.0, 0.0, g);
            }

            public TransferParameters(
                    double a,
                    double b,
                    double c,
                    double d,
                    double e,
                    double f,
                    double g) {
                if (Double.isNaN(a)
                        || Double.isNaN(b)
                        || Double.isNaN(c)
                        || Double.isNaN(d)
                        || Double.isNaN(e)
                        || Double.isNaN(f)
                        || Double.isNaN(g)) {
                    throw new IllegalArgumentException("Parameters cannot be NaN");
                }
                if (!(d >= 0.0 && d <= 1.0f + Math.ulp(1.0f))) {
                    throw new IllegalArgumentException(
                            "Parameter d must be in the range [0..1], was " + d);
                }
                if (d == 0.0 && (a == 0.0 || g == 0.0)) {
                    throw new IllegalArgumentException(
                            "Parameter a or g is zero, the transfer function is constant");
                }
                if (d >= 1.0 && c == 0.0) {
                    throw new IllegalArgumentException(
                            "Parameter c is zero, the transfer function is constant");
                }
                if ((a == 0.0 || g == 0.0) && c == 0.0) {
                    throw new IllegalArgumentException(
                            "Parameter a or g is zero, and c is zero, "
                                    + "the transfer function is constant");
                }
                if (c < 0.0) {
                    throw new IllegalArgumentException("The transfer function must be increasing");
                }
                if (a < 0.0 || g < 0.0) {
                    throw new IllegalArgumentException(
                            "The transfer function must be positive or increasing");
                }
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
                this.e = e;
                this.f = f;
                this.g = g;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                TransferParameters that = (TransferParameters) o;
                return Double.compare(that.a, a) == 0
                        && Double.compare(that.b, b) == 0
                        && Double.compare(that.c, c) == 0
                        && Double.compare(that.d, d) == 0
                        && Double.compare(that.e, e) == 0
                        && Double.compare(that.f, f) == 0
                        && Double.compare(that.g, g) == 0;
            }

            @Override
            public int hashCode() {
                int result;
                long temp = Double.doubleToLongBits(a);
                result = (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(b);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(c);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(d);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(e);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(f);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(g);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                return result;
            }
        }

        final float[] mWhitePoint;
        final float[] mPrimaries;
        final float[] mTransform;
        final float[] mInverseTransform;
        final DoubleUnaryOperator mOetf;
        final DoubleUnaryOperator mEotf;
        final DoubleUnaryOperator mClampedOetf;
        final DoubleUnaryOperator mClampedEotf;
        private final float mMin;
        private final float mMax;
        private final boolean mIsWideGamut;
        private final boolean mIsSrgb;
        private final TransferParameters mTransferParameters;

        public Rgb(
                String name,
                float[] toXYZ,
                DoubleUnaryOperator oetf,
                DoubleUnaryOperator eotf) {
            this(
                    name,
                    computePrimaries(toXYZ),
                    computeWhitePoint(toXYZ),
                    null,
                    oetf,
                    eotf,
                    0.0f,
                    1.0f,
                    null,
                    MIN_ID);
        }

        public Rgb(
                String name,
                float[] primaries,
                float[] whitePoint,
                DoubleUnaryOperator oetf,
                DoubleUnaryOperator eotf,
                float min,
                float max) {
            this(
                    name,
                    primaries,
                    whitePoint,
                    null,
                    oetf,
                    eotf,
                    min,
                    max,
                    null,
                    MIN_ID);
        }

        public Rgb(String name, float[] toXYZ, TransferParameters function) {
            this(
                    name,
                    computePrimaries(toXYZ),
                    computeWhitePoint(toXYZ),
                    function,
                    MIN_ID);
        }

        public Rgb(
                String name,
                float[] primaries,
                float[] whitePoint,
                TransferParameters function) {
            this(name, primaries, whitePoint, function, MIN_ID);
        }

        Rgb(
                String name,
                float[] primaries,
                float[] whitePoint,
                TransferParameters function,
                int id) {
            this(name, primaries, whitePoint, null, function, id);
        }

        Rgb(
                String name,
                float[] primaries,
                float[] whitePoint,
                float[] transform,
                final TransferParameters function,
                int id) {
            this(
                    name,
                    primaries,
                    whitePoint,
                    transform,
                    makeOetf(function),
                    makeEotf(function),
                    0.0f,
                    1.0f,
                    function,
                    id);
        }

        public Rgb(String name, float[] toXYZ, double gamma) {
            this(
                    name,
                    computePrimaries(toXYZ),
                    computeWhitePoint(toXYZ),
                    gamma,
                    0.0f,
                    1.0f,
                    MIN_ID);
        }

        public Rgb(String name, float[] primaries, float[] whitePoint, double gamma) {
            this(name, primaries, whitePoint, gamma, 0.0f, 1.0f, MIN_ID);
        }

        private Rgb(
                String name,
                float[] primaries,
                float[] whitePoint,
                final double gamma,
                float min,
                float max,
                int id) {
            this(
                    name,
                    primaries,
                    whitePoint,
                    null,
                    gamma == 1.0
                            ? IDENTITY_OP
                            : new DoubleUnaryOperator() {
                                @Override
                                public double applyAsDouble(double x) {
                                    return Math.pow(x < 0.0 ? 0.0 : x, 1.0 / gamma);
                                }
                            },
                    gamma == 1.0
                            ? IDENTITY_OP
                            : new DoubleUnaryOperator() {
                                @Override
                                public double applyAsDouble(double x) {
                                    return Math.pow(x < 0.0 ? 0.0 : x, gamma);
                                }
                            },
                    min,
                    max,
                    checkGammaTransferParameters(gamma),
                    id);
        }

        private Rgb(
                String name,
                float[] primaries,
                float[] whitePoint,
                float[] transform,
                DoubleUnaryOperator oetf,
                DoubleUnaryOperator eotf,
                float min,
                float max,
                TransferParameters transferParameters,
                int id) {
            super(name, Model.RGB, id);
            if (primaries == null || (primaries.length != 6 && primaries.length != 9)) {
                throw new IllegalArgumentException(
                        "The color space's primaries must be defined as an array of 6 floats "
                                + "in xyY or 9 floats in XYZ");
            }
            if (whitePoint == null || (whitePoint.length != 2 && whitePoint.length != 3)) {
                throw new IllegalArgumentException(
                        "The color space's white point must be defined as an array of 2 floats "
                                + "in xyY or 3 float in XYZ");
            }
            if (oetf == null || eotf == null) {
                throw new IllegalArgumentException(
                        "The transfer functions of a color space cannot be null");
            }
            if (min >= max) {
                throw new IllegalArgumentException(
                        "Invalid range: min=" + min + ", max=" + max
                                + "; min must be strictly < max");
            }
            mWhitePoint = xyWhitePoint(whitePoint);
            mPrimaries = xyPrimaries(primaries);
            if (transform == null) {
                mTransform = computeXYZMatrix(mPrimaries, mWhitePoint);
            } else {
                if (transform.length != 9) {
                    throw new IllegalArgumentException(
                            "Transform must have 9 entries! Has " + transform.length);
                }
                mTransform = Arrays.copyOf(transform, 9);
            }
            mInverseTransform = inverse3x3(mTransform);
            mOetf = oetf;
            mEotf = eotf;
            mMin = min;
            mMax = max;

            mClampedOetf = new DoubleUnaryOperator() {
                @Override
                public double applyAsDouble(double x) {
                    return clampValue(mOetf.applyAsDouble(x));
                }
            };
            mClampedEotf = new DoubleUnaryOperator() {
                @Override
                public double applyAsDouble(double x) {
                    return mEotf.applyAsDouble(clampValue(x));
                }
            };
            mTransferParameters = transferParameters;
            mIsWideGamut = isWideGamut(mPrimaries, min, max);
            mIsSrgb = isSrgb(mPrimaries, mWhitePoint, oetf, eotf, min, max, id);
        }

        Rgb(Rgb colorSpace, float[] transform, float[] whitePoint) {
            this(
                    colorSpace.getName(),
                    colorSpace.mPrimaries,
                    whitePoint,
                    transform,
                    colorSpace.mOetf,
                    colorSpace.mEotf,
                    colorSpace.mMin,
                    colorSpace.mMax,
                    colorSpace.mTransferParameters,
                    MIN_ID);
        }

        private static final DoubleUnaryOperator IDENTITY_OP = new DoubleUnaryOperator() {
            @Override
            public double applyAsDouble(double x) {
                return x;
            }
        };

        private static TransferParameters checkGammaTransferParameters(double gamma) {
            if (Double.isNaN(gamma) || gamma <= 0.0) {
                throw new IllegalArgumentException(
                        "Gamma must be >= 0.0 and not NaN, got " + gamma);
            }
            return new TransferParameters(1.0, 0.0, 0.0, 0.0, gamma);
        }

        private static DoubleUnaryOperator makeOetf(final TransferParameters function) {
            if (function == null) {
                throw new IllegalArgumentException(
                        "The transfer functions of a color space cannot be null");
            }
            if (function.e == 0.0 && function.f == 0.0) {
                return new DoubleUnaryOperator() {
                    @Override
                    public double applyAsDouble(double x) {
                        return rcpResponse(
                                x, function.a, function.b, function.c, function.d, function.g);
                    }
                };
            }
            return new DoubleUnaryOperator() {
                @Override
                public double applyAsDouble(double x) {
                    return rcpResponse(
                            x,
                            function.a,
                            function.b,
                            function.c,
                            function.d,
                            function.e,
                            function.f,
                            function.g);
                }
            };
        }

        private static DoubleUnaryOperator makeEotf(final TransferParameters function) {
            if (function == null) {
                throw new IllegalArgumentException(
                        "The transfer functions of a color space cannot be null");
            }
            if (function.e == 0.0 && function.f == 0.0) {
                return new DoubleUnaryOperator() {
                    @Override
                    public double applyAsDouble(double x) {
                        return response(
                                x, function.a, function.b, function.c, function.d, function.g);
                    }
                };
            }
            return new DoubleUnaryOperator() {
                @Override
                public double applyAsDouble(double x) {
                    return response(
                            x,
                            function.a,
                            function.b,
                            function.c,
                            function.d,
                            function.e,
                            function.f,
                            function.g);
                }
            };
        }

        private double clampValue(double x) {
            return x < mMin ? mMin : (x > mMax ? mMax : x);
        }

        public float[] getWhitePoint(float[] whitePoint) {
            whitePoint[0] = mWhitePoint[0];
            whitePoint[1] = mWhitePoint[1];
            return whitePoint;
        }

        public float[] getWhitePoint() {
            return Arrays.copyOf(mWhitePoint, mWhitePoint.length);
        }

        public float[] getPrimaries(float[] primaries) {
            System.arraycopy(mPrimaries, 0, primaries, 0, mPrimaries.length);
            return primaries;
        }

        public float[] getPrimaries() {
            return Arrays.copyOf(mPrimaries, mPrimaries.length);
        }

        public float[] getTransform(float[] transform) {
            System.arraycopy(mTransform, 0, transform, 0, mTransform.length);
            return transform;
        }

        public float[] getTransform() {
            return Arrays.copyOf(mTransform, mTransform.length);
        }

        public float[] getInverseTransform(float[] inverseTransform) {
            System.arraycopy(
                    mInverseTransform, 0, inverseTransform, 0, mInverseTransform.length);
            return inverseTransform;
        }

        public float[] getInverseTransform() {
            return Arrays.copyOf(mInverseTransform, mInverseTransform.length);
        }

        public DoubleUnaryOperator getOetf() {
            return mClampedOetf;
        }

        public DoubleUnaryOperator getEotf() {
            return mClampedEotf;
        }

        public TransferParameters getTransferParameters() {
            return mTransferParameters;
        }

        @Override
        public boolean isSrgb() {
            return mIsSrgb;
        }

        @Override
        public boolean isWideGamut() {
            return mIsWideGamut;
        }

        @Override
        public float getMinValue(int component) {
            if (component < 0 || component >= 3) {
                throw new IllegalArgumentException("RGB component: " + component);
            }
            return mMin;
        }

        @Override
        public float getMaxValue(int component) {
            if (component < 0 || component >= 3) {
                throw new IllegalArgumentException("RGB component: " + component);
            }
            return mMax;
        }

        public float[] toLinear(float r, float g, float b) {
            return toLinear(new float[]{r, g, b});
        }

        public float[] toLinear(float[] v) {
            v[0] = (float) mClampedEotf.applyAsDouble(v[0]);
            v[1] = (float) mClampedEotf.applyAsDouble(v[1]);
            v[2] = (float) mClampedEotf.applyAsDouble(v[2]);
            return v;
        }

        public float[] fromLinear(float r, float g, float b) {
            return fromLinear(new float[]{r, g, b});
        }

        public float[] fromLinear(float[] v) {
            v[0] = (float) mClampedOetf.applyAsDouble(v[0]);
            v[1] = (float) mClampedOetf.applyAsDouble(v[1]);
            v[2] = (float) mClampedOetf.applyAsDouble(v[2]);
            return v;
        }

        @Override
        public float[] toXyz(float[] v) {
            v[0] = (float) mClampedEotf.applyAsDouble(v[0]);
            v[1] = (float) mClampedEotf.applyAsDouble(v[1]);
            v[2] = (float) mClampedEotf.applyAsDouble(v[2]);
            return mul3x3Float3(mTransform, v);
        }

        @Override
        public float[] fromXyz(float[] v) {
            mul3x3Float3(mInverseTransform, v);
            v[0] = (float) mClampedOetf.applyAsDouble(v[0]);
            v[1] = (float) mClampedOetf.applyAsDouble(v[1]);
            v[2] = (float) mClampedOetf.applyAsDouble(v[2]);
            return v;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            Rgb rgb = (Rgb) o;
            if (Float.compare(rgb.mMin, mMin) != 0 || Float.compare(rgb.mMax, mMax) != 0) {
                return false;
            }
            if (!Arrays.equals(mWhitePoint, rgb.mWhitePoint)) {
                return false;
            }
            if (!Arrays.equals(mPrimaries, rgb.mPrimaries)) {
                return false;
            }
            if (mTransferParameters != null) {
                return mTransferParameters.equals(rgb.mTransferParameters);
            }
            if (rgb.mTransferParameters == null) {
                return true;
            }
            return mOetf.equals(rgb.mOetf) && mEotf.equals(rgb.mEotf);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + Arrays.hashCode(mWhitePoint);
            result = 31 * result + Arrays.hashCode(mPrimaries);
            result = 31 * result + (mMin != 0.0f ? Float.floatToIntBits(mMin) : 0);
            result = 31 * result + (mMax != 0.0f ? Float.floatToIntBits(mMax) : 0);
            result = 31 * result
                    + (mTransferParameters != null ? mTransferParameters.hashCode() : 0);
            if (mTransferParameters == null) {
                result = 31 * result + mOetf.hashCode();
                result = 31 * result + mEotf.hashCode();
            }
            return result;
        }

        private static boolean isSrgb(
                float[] primaries,
                float[] whitePoint,
                DoubleUnaryOperator oetf,
                DoubleUnaryOperator eotf,
                float min,
                float max,
                int id) {
            if (id == 0) {
                return true;
            }
            if (!compareFloats(primaries, SRGB_PRIMARIES)) {
                return false;
            }
            if (!compareFloats(whitePoint, ILLUMINANT_D65)) {
                return false;
            }
            if (min != 0.0f || max != 1.0f) {
                return false;
            }
            for (double x = 0.0; x <= 1.0; x += 1.0 / 255.0) {
                double srgbOetf = rcpResponse(
                        x, 1.0 / 1.055, 0.055 / 1.055, 1.0 / 12.92, 0.04045, 2.4);
                if (Math.abs(oetf.applyAsDouble(x) - srgbOetf) > 1e-3) {
                    return false;
                }
                double srgbEotf = response(
                        x, 1.0 / 1.055, 0.055 / 1.055, 1.0 / 12.92, 0.04045, 2.4);
                if (Math.abs(eotf.applyAsDouble(x) - srgbEotf) > 1e-3) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isWideGamut(float[] primaries, float min, float max) {
            return (area(primaries) / area(SRGB_PRIMARIES) > 0.9f
                    && !compareFloats(primaries, SRGB_PRIMARIES))
                    || (min < 0.0f && max > 1.0f);
        }

        private static float area(float[] primaries) {
            float rx = primaries[0];
            float ry = primaries[1];
            float gx = primaries[2];
            float gy = primaries[3];
            float bx = primaries[4];
            float by = primaries[5];
            float det = rx * gy + ry * bx + gx * by - gy * bx - ry * gx - rx * by;
            float r = 0.5f * det;
            return r < 0.0f ? -r : r;
        }

        private static float[] xyWhitePoint(float[] whitePoint) {
            float[] xy = new float[2];
            if (whitePoint.length == 3) {
                float sum = whitePoint[0] + whitePoint[1] + whitePoint[2];
                xy[0] = whitePoint[0] / sum;
                xy[1] = whitePoint[1] / sum;
            } else {
                System.arraycopy(whitePoint, 0, xy, 0, 2);
            }
            return xy;
        }

        private static float[] xyPrimaries(float[] primaries) {
            float[] xy = new float[6];
            if (primaries.length == 9) {
                float sum = primaries[0] + primaries[1] + primaries[2];
                xy[0] = primaries[0] / sum;
                xy[1] = primaries[1] / sum;

                sum = primaries[3] + primaries[4] + primaries[5];
                xy[2] = primaries[3] / sum;
                xy[3] = primaries[4] / sum;

                sum = primaries[6] + primaries[7] + primaries[8];
                xy[4] = primaries[6] / sum;
                xy[5] = primaries[7] / sum;
            } else {
                System.arraycopy(primaries, 0, xy, 0, 6);
            }
            return xy;
        }

        private static float[] computePrimaries(float[] toXYZ) {
            if (toXYZ == null || toXYZ.length < 9) {
                throw new IllegalArgumentException(
                        "toXYZ must be a non-null array of at least 9 floats");
            }
            float[] r = mul3x3Float3(toXYZ, new float[]{1.0f, 0.0f, 0.0f});
            float[] g = mul3x3Float3(toXYZ, new float[]{0.0f, 1.0f, 0.0f});
            float[] b = mul3x3Float3(toXYZ, new float[]{0.0f, 0.0f, 1.0f});

            float rSum = r[0] + r[1] + r[2];
            float gSum = g[0] + g[1] + g[2];
            float bSum = b[0] + b[1] + b[2];

            return new float[]{
                r[0] / rSum, r[1] / rSum,
                g[0] / gSum, g[1] / gSum,
                b[0] / bSum, b[1] / bSum
            };
        }

        private static float[] computeWhitePoint(float[] toXYZ) {
            if (toXYZ == null || toXYZ.length < 9) {
                throw new IllegalArgumentException(
                        "toXYZ must be a non-null array of at least 9 floats");
            }
            float[] w = mul3x3Float3(toXYZ, new float[]{1.0f, 1.0f, 1.0f});
            float sum = w[0] + w[1] + w[2];
            return new float[]{w[0] / sum, w[1] / sum};
        }

        private static float[] computeXYZMatrix(float[] primaries, float[] whitePoint) {
            float rx = primaries[0];
            float ry = primaries[1];
            float gx = primaries[2];
            float gy = primaries[3];
            float bx = primaries[4];
            float by = primaries[5];
            float wx = whitePoint[0];
            float wy = whitePoint[1];

            float oneRxRy = (1.0f - rx) / ry;
            float oneGxGy = (1.0f - gx) / gy;
            float oneBxBy = (1.0f - bx) / by;
            float oneWxWy = (1.0f - wx) / wy;

            float rxRy = rx / ry;
            float gxGy = gx / gy;
            float bxBy = bx / by;
            float wxWy = wx / wy;

            float sB = ((oneWxWy - oneRxRy) * (gxGy - rxRy) - (wxWy - rxRy) * (oneGxGy - oneRxRy))
                    / ((oneBxBy - oneRxRy) * (gxGy - rxRy) - (bxBy - rxRy) * (oneGxGy - oneRxRy));
            float sG = (wxWy - rxRy - sB * (bxBy - rxRy)) / (gxGy - rxRy);
            float sR = 1.0f - sG - sB;

            float sRRy = sR / ry;
            float sGGy = sG / gy;
            float sBBy = sB / by;

            return new float[]{
                sRRy * rx, sR, sRRy * (1.0f - rx - ry),
                sGGy * gx, sG, sGGy * (1.0f - gx - gy),
                sBBy * bx, sB, sBBy * (1.0f - bx - by)
            };
        }
    }
}
