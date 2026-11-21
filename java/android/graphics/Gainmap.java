// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

/** HDR gainmap metadata and backing luminance/RGB gain bitmap (Ultra HDR / ISO 21496-1). */
public final class Gainmap implements Parcelable {
    private Bitmap contents;
    private final float[] ratioMin = {1.0f, 1.0f, 1.0f};
    private final float[] ratioMax = {2.0f, 2.0f, 2.0f};
    private final float[] gamma = {1.0f, 1.0f, 1.0f};
    private final float[] epsilonSdr = {1.0f / 64.0f, 1.0f / 64.0f, 1.0f / 64.0f};
    private final float[] epsilonHdr = {1.0f / 64.0f, 1.0f / 64.0f, 1.0f / 64.0f};
    private float displayRatioForFullHdr = 2.0f;
    private float minDisplayRatioForHdrTransition = 1.0f;

    public Gainmap(Bitmap gainmapContents) {
        setGainmapContents(gainmapContents);
    }

    public Gainmap(Gainmap gainmap, Bitmap gainmapContents) {
        if (gainmap == null) {
            throw new NullPointerException("gainmap");
        }
        setGainmapContents(gainmapContents);
        System.arraycopy(gainmap.ratioMin, 0, this.ratioMin, 0, 3);
        System.arraycopy(gainmap.ratioMax, 0, this.ratioMax, 0, 3);
        System.arraycopy(gainmap.gamma, 0, this.gamma, 0, 3);
        System.arraycopy(gainmap.epsilonSdr, 0, this.epsilonSdr, 0, 3);
        System.arraycopy(gainmap.epsilonHdr, 0, this.epsilonHdr, 0, 3);
        this.displayRatioForFullHdr = gainmap.displayRatioForFullHdr;
        this.minDisplayRatioForHdrTransition = gainmap.minDisplayRatioForHdrTransition;
    }

    public Bitmap getGainmapContents() {
        return contents;
    }

    public void setGainmapContents(Bitmap bitmap) {
        if (bitmap == null) {
            throw new NullPointerException("gainmapContents");
        }
        if (bitmap.isRecycled()) {
            throw new IllegalArgumentException("Gainmap contents bitmap is recycled");
        }
        this.contents = bitmap;
    }

    public void setRatioMin(float r, float g, float b) {
        ratioMin[0] = r;
        ratioMin[1] = g;
        ratioMin[2] = b;
    }

    public float[] getRatioMin() {
        return ratioMin.clone();
    }

    public void setRatioMax(float r, float g, float b) {
        ratioMax[0] = r;
        ratioMax[1] = g;
        ratioMax[2] = b;
    }

    public float[] getRatioMax() {
        return ratioMax.clone();
    }

    public void setGamma(float r, float g, float b) {
        gamma[0] = r;
        gamma[1] = g;
        gamma[2] = b;
    }

    public float[] getGamma() {
        return gamma.clone();
    }

    public void setEpsilonSdr(float r, float g, float b) {
        epsilonSdr[0] = r;
        epsilonSdr[1] = g;
        epsilonSdr[2] = b;
    }

    public float[] getEpsilonSdr() {
        return epsilonSdr.clone();
    }

    public void setEpsilonHdr(float r, float g, float b) {
        epsilonHdr[0] = r;
        epsilonHdr[1] = g;
        epsilonHdr[2] = b;
    }

    public float[] getEpsilonHdr() {
        return epsilonHdr.clone();
    }

    public void setDisplayRatioForFullHdr(float max) {
        if (!Float.isFinite(max) || max < 1.0f) {
            throw new IllegalArgumentException("setDisplayRatioForFullHdr must be >= 1.0f, got " + max);
        }
        this.displayRatioForFullHdr = max;
    }

    public float getDisplayRatioForFullHdr() {
        return displayRatioForFullHdr;
    }

    public void setMinDisplayRatioForHdrTransition(float min) {
        if (!Float.isFinite(min) || min < 1.0f) {
            throw new IllegalArgumentException(
                    "setMinDisplayRatioForHdrTransition must be >= 1.0f, got " + min);
        }
        this.minDisplayRatioForHdrTransition = min;
    }

    public float getMinDisplayRatioForHdrTransition() {
        return minDisplayRatioForHdrTransition;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (dest == null) {
            throw new NullPointerException("dest");
        }
        dest.writeFloatArray(ratioMin);
        dest.writeFloatArray(ratioMax);
        dest.writeFloatArray(gamma);
        dest.writeFloatArray(epsilonSdr);
        dest.writeFloatArray(epsilonHdr);
        dest.writeFloat(displayRatioForFullHdr);
        dest.writeFloat(minDisplayRatioForHdrTransition);
        int w = contents.getWidth();
        int h = contents.getHeight();
        dest.writeInt(w);
        dest.writeInt(h);
        dest.writeInt(contents.getConfig().ordinal());
        dest.writeInt(contents.hasAlpha() ? 1 : 0);
        dest.writeInt(contents.isMutable() ? 1 : 0);
        dest.writeInt(contents.getDensity());
        int[] pixels = new int[w * h];
        contents.getPixels(pixels, 0, w, 0, 0, w, h);
        dest.writeIntArray(pixels);
    }

    public static final Parcelable.Creator<Gainmap> CREATOR =
            new Parcelable.Creator<Gainmap>() {
                @Override
                public Gainmap createFromParcel(Parcel in) {
                    float[] rMin = in.createFloatArray();
                    float[] rMax = in.createFloatArray();
                    float[] gam = in.createFloatArray();
                    float[] eSdr = in.createFloatArray();
                    float[] eHdr = in.createFloatArray();
                    float fullHdr = in.readFloat();
                    float minHdr = in.readFloat();
                    int w = in.readInt();
                    int h = in.readInt();
                    int configOrd = in.readInt();
                    boolean hasAlpha = in.readInt() != 0;
                    boolean mutable = in.readInt() != 0;
                    int density = in.readInt();
                    int[] pixels = in.createIntArray();
                    Bitmap.Config[] configs = Bitmap.Config.values();
                    Bitmap.Config config =
                            (configOrd >= 0 && configOrd < configs.length)
                                    ? configs[configOrd]
                                    : Bitmap.Config.ARGB_8888;
                    Bitmap bmp = Bitmap.createBitmap(w, h, config);
                    bmp.setHasAlpha(hasAlpha);
                    bmp.setDensity(density);
                    bmp.setPixels(pixels, 0, w, 0, 0, w, h);
                    if (!mutable) {
                        bmp.freeze();
                    }
                    Gainmap gm = new Gainmap(bmp);
                    if (rMin != null && rMin.length == 3) gm.setRatioMin(rMin[0], rMin[1], rMin[2]);
                    if (rMax != null && rMax.length == 3) gm.setRatioMax(rMax[0], rMax[1], rMax[2]);
                    if (gam != null && gam.length == 3) gm.setGamma(gam[0], gam[1], gam[2]);
                    if (eSdr != null && eSdr.length == 3) gm.setEpsilonSdr(eSdr[0], eSdr[1], eSdr[2]);
                    if (eHdr != null && eHdr.length == 3) gm.setEpsilonHdr(eHdr[0], eHdr[1], eHdr[2]);
                    gm.setDisplayRatioForFullHdr(fullHdr);
                    gm.setMinDisplayRatioForHdrTransition(minHdr);
                    return gm;
                }

                @Override
                public Gainmap[] newArray(int size) {
                    return new Gainmap[size];
                }
            };
}
