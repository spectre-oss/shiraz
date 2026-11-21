// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public class Sampler extends BaseObj {
    public enum Value {
        NEAREST,
        LINEAR,
        LINEAR_MIP_LINEAR,
        LINEAR_MIP_NEAREST,
        WRAP,
        CLAMP,
        MIRRORED_REPEAT
    }

    Value mMin;
    Value mMag;
    Value mWrapS;
    Value mWrapT;
    Value mWrapR;
    float mAniso;

    Sampler(long id, RenderScript rs) {
        super(id, rs);
    }

    public Value getMinification() {
        return mMin;
    }

    public Value getMagnification() {
        return mMag;
    }

    public Value getWrapS() {
        return mWrapS;
    }

    public Value getWrapT() {
        return mWrapT;
    }

    public float getAnisotropy() {
        return mAniso;
    }

    public static Sampler CLAMP_NEAREST(RenderScript rs) {
        if (rs.mSampler_CLAMP_NEAREST == null) {
            synchronized (rs) {
                if (rs.mSampler_CLAMP_NEAREST == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.NEAREST);
                    b.setMagnification(Value.NEAREST);
                    b.setWrapS(Value.CLAMP);
                    b.setWrapT(Value.CLAMP);
                    rs.mSampler_CLAMP_NEAREST = b.create();
                }
            }
        }
        return rs.mSampler_CLAMP_NEAREST;
    }

    public static Sampler CLAMP_LINEAR(RenderScript rs) {
        if (rs.mSampler_CLAMP_LINEAR == null) {
            synchronized (rs) {
                if (rs.mSampler_CLAMP_LINEAR == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.LINEAR);
                    b.setMagnification(Value.LINEAR);
                    b.setWrapS(Value.CLAMP);
                    b.setWrapT(Value.CLAMP);
                    rs.mSampler_CLAMP_LINEAR = b.create();
                }
            }
        }
        return rs.mSampler_CLAMP_LINEAR;
    }

    public static Sampler CLAMP_LINEAR_MIP_LINEAR(RenderScript rs) {
        if (rs.mSampler_CLAMP_LINEAR_MIP_LINEAR == null) {
            synchronized (rs) {
                if (rs.mSampler_CLAMP_LINEAR_MIP_LINEAR == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.LINEAR_MIP_LINEAR);
                    b.setMagnification(Value.LINEAR);
                    b.setWrapS(Value.CLAMP);
                    b.setWrapT(Value.CLAMP);
                    rs.mSampler_CLAMP_LINEAR_MIP_LINEAR = b.create();
                }
            }
        }
        return rs.mSampler_CLAMP_LINEAR_MIP_LINEAR;
    }

    public static Sampler WRAP_NEAREST(RenderScript rs) {
        if (rs.mSampler_WRAP_NEAREST == null) {
            synchronized (rs) {
                if (rs.mSampler_WRAP_NEAREST == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.NEAREST);
                    b.setMagnification(Value.NEAREST);
                    b.setWrapS(Value.WRAP);
                    b.setWrapT(Value.WRAP);
                    rs.mSampler_WRAP_NEAREST = b.create();
                }
            }
        }
        return rs.mSampler_WRAP_NEAREST;
    }

    public static Sampler WRAP_LINEAR(RenderScript rs) {
        if (rs.mSampler_WRAP_LINEAR == null) {
            synchronized (rs) {
                if (rs.mSampler_WRAP_LINEAR == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.LINEAR);
                    b.setMagnification(Value.LINEAR);
                    b.setWrapS(Value.WRAP);
                    b.setWrapT(Value.WRAP);
                    rs.mSampler_WRAP_LINEAR = b.create();
                }
            }
        }
        return rs.mSampler_WRAP_LINEAR;
    }

    public static Sampler WRAP_LINEAR_MIP_LINEAR(RenderScript rs) {
        if (rs.mSampler_WRAP_LINEAR_MIP_LINEAR == null) {
            synchronized (rs) {
                if (rs.mSampler_WRAP_LINEAR_MIP_LINEAR == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.LINEAR_MIP_LINEAR);
                    b.setMagnification(Value.LINEAR);
                    b.setWrapS(Value.WRAP);
                    b.setWrapT(Value.WRAP);
                    rs.mSampler_WRAP_LINEAR_MIP_LINEAR = b.create();
                }
            }
        }
        return rs.mSampler_WRAP_LINEAR_MIP_LINEAR;
    }

    public static Sampler MIRRORED_REPEAT_NEAREST(RenderScript rs) {
        if (rs.mSampler_MIRRORED_REPEAT_NEAREST == null) {
            synchronized (rs) {
                if (rs.mSampler_MIRRORED_REPEAT_NEAREST == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.NEAREST);
                    b.setMagnification(Value.NEAREST);
                    b.setWrapS(Value.MIRRORED_REPEAT);
                    b.setWrapT(Value.MIRRORED_REPEAT);
                    rs.mSampler_MIRRORED_REPEAT_NEAREST = b.create();
                }
            }
        }
        return rs.mSampler_MIRRORED_REPEAT_NEAREST;
    }

    public static Sampler MIRRORED_REPEAT_LINEAR(RenderScript rs) {
        if (rs.mSampler_MIRRORED_REPEAT_LINEAR == null) {
            synchronized (rs) {
                if (rs.mSampler_MIRRORED_REPEAT_LINEAR == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.LINEAR);
                    b.setMagnification(Value.LINEAR);
                    b.setWrapS(Value.MIRRORED_REPEAT);
                    b.setWrapT(Value.MIRRORED_REPEAT);
                    rs.mSampler_MIRRORED_REPEAT_LINEAR = b.create();
                }
            }
        }
        return rs.mSampler_MIRRORED_REPEAT_LINEAR;
    }

    public static Sampler MIRRORED_REPEAT_LINEAR_MIP_LINEAR(RenderScript rs) {
        if (rs.mSampler_MIRRORED_REPEAT_LINEAR_MIP_LINEAR == null) {
            synchronized (rs) {
                if (rs.mSampler_MIRRORED_REPEAT_LINEAR_MIP_LINEAR == null) {
                    Builder b = new Builder(rs);
                    b.setMinification(Value.LINEAR_MIP_LINEAR);
                    b.setMagnification(Value.LINEAR);
                    b.setWrapS(Value.MIRRORED_REPEAT);
                    b.setWrapT(Value.MIRRORED_REPEAT);
                    rs.mSampler_MIRRORED_REPEAT_LINEAR_MIP_LINEAR = b.create();
                }
            }
        }
        return rs.mSampler_MIRRORED_REPEAT_LINEAR_MIP_LINEAR;
    }

    public static class Builder {
        RenderScript mRS;
        Value mMin;
        Value mMag;
        Value mWrapS;
        Value mWrapT;
        Value mWrapR;
        float mAniso;

        public Builder(RenderScript rs) {
            rs.validate();
            mRS = rs;
            mMin = Value.NEAREST;
            mMag = Value.NEAREST;
            mWrapS = Value.WRAP;
            mWrapT = Value.WRAP;
            mWrapR = Value.WRAP;
            mAniso = 1.0f;
        }

        public void setMinification(Value v) {
            if (v == Value.NEAREST || v == Value.LINEAR
                    || v == Value.LINEAR_MIP_LINEAR || v == Value.LINEAR_MIP_NEAREST) {
                mMin = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setMagnification(Value v) {
            if (v == Value.NEAREST || v == Value.LINEAR) {
                mMag = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setWrapS(Value v) {
            if (v == Value.WRAP || v == Value.CLAMP || v == Value.MIRRORED_REPEAT) {
                mWrapS = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setWrapT(Value v) {
            if (v == Value.WRAP || v == Value.CLAMP || v == Value.MIRRORED_REPEAT) {
                mWrapT = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setAnisotropy(float v) {
            if (v >= 1.0f) {
                mAniso = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public Sampler create() {
            mRS.validate();
            Sampler s = new Sampler(0, mRS);
            s.mMin = mMin;
            s.mMag = mMag;
            s.mWrapS = mWrapS;
            s.mWrapT = mWrapT;
            s.mWrapR = mWrapR;
            s.mAniso = mAniso;
            return s;
        }
    }
}
