// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

public final class DynamicsProcessing extends AudioEffect {
    public static final int VARIANT_FAVOR_FREQUENCY_RESOLUTION = 0;
    public static final int VARIANT_FAVOR_TIME_RESOLUTION = 1;

    public static class Stage {
        private final boolean mInUse;
        private boolean mEnabled;

        public Stage(boolean inUse, boolean enabled) {
            mInUse = inUse;
            mEnabled = enabled;
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        public void setEnabled(boolean enabled) {
            mEnabled = enabled;
        }

        public boolean isInUse() {
            return mInUse;
        }

        @Override
        public String toString() {
            return "Stage[inUse=" + mInUse + ", enabled=" + mEnabled + "]";
        }
    }

    public static class BandStage extends Stage {
        private final int mBandCount;

        public BandStage(boolean inUse, boolean enabled, int bandCount) {
            super(inUse, enabled);
            mBandCount = isInUse() ? Math.max(0, bandCount) : 0;
        }

        public int getBandCount() {
            return mBandCount;
        }

        @Override
        public String toString() {
            return super.toString() + " BandStage[bandCount=" + mBandCount + "]";
        }
    }

    public static class BandBase {
        private boolean mEnabled;
        private float mCutoffFrequency;

        public BandBase(boolean enabled, float cutoffFrequency) {
            mEnabled = enabled;
            mCutoffFrequency = cutoffFrequency;
        }

        @Override
        public String toString() {
            return "BandBase[enabled=" + mEnabled
                    + ", cutoffFrequency=" + mCutoffFrequency + "]";
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        public void setEnabled(boolean enabled) {
            mEnabled = enabled;
        }

        public float getCutoffFrequency() {
            return mCutoffFrequency;
        }

        public void setCutoffFrequency(float frequency) {
            mCutoffFrequency = frequency;
        }
    }

    public static final class EqBand extends BandBase {
        private float mGain;

        public EqBand(boolean enabled, float cutoffFrequency, float gain) {
            super(enabled, cutoffFrequency);
            mGain = gain;
        }

        public EqBand(EqBand cfg) {
            super(cfg.isEnabled(), cfg.getCutoffFrequency());
            mGain = cfg.mGain;
        }

        @Override
        public String toString() {
            return super.toString() + " EqBand[gain=" + mGain + "]";
        }

        public float getGain() {
            return mGain;
        }

        public void setGain(float gain) {
            mGain = gain;
        }
    }

    public static final class MbcBand extends BandBase {
        private float mAttackTime;
        private float mReleaseTime;
        private float mRatio;
        private float mThreshold;
        private float mKneeWidth;
        private float mNoiseGateThreshold;
        private float mExpanderRatio;
        private float mPreGain;
        private float mPostGain;

        public MbcBand(
                boolean enabled,
                float cutoffFrequency,
                float attackTime,
                float releaseTime,
                float ratio,
                float threshold,
                float kneeWidth,
                float noiseGateThreshold,
                float expanderRatio,
                float preGain,
                float postGain) {
            super(enabled, cutoffFrequency);
            mAttackTime = attackTime;
            mReleaseTime = releaseTime;
            mRatio = ratio;
            mThreshold = threshold;
            mKneeWidth = kneeWidth;
            mNoiseGateThreshold = noiseGateThreshold;
            mExpanderRatio = expanderRatio;
            mPreGain = preGain;
            mPostGain = postGain;
        }

        public MbcBand(MbcBand cfg) {
            super(cfg.isEnabled(), cfg.getCutoffFrequency());
            mAttackTime = cfg.mAttackTime;
            mReleaseTime = cfg.mReleaseTime;
            mRatio = cfg.mRatio;
            mThreshold = cfg.mThreshold;
            mKneeWidth = cfg.mKneeWidth;
            mNoiseGateThreshold = cfg.mNoiseGateThreshold;
            mExpanderRatio = cfg.mExpanderRatio;
            mPreGain = cfg.mPreGain;
            mPostGain = cfg.mPostGain;
        }

        @Override
        public String toString() {
            return super.toString()
                    + " MbcBand[attack=" + mAttackTime
                    + ", release=" + mReleaseTime
                    + ", ratio=" + mRatio
                    + ", threshold=" + mThreshold
                    + ", knee=" + mKneeWidth
                    + ", noiseGate=" + mNoiseGateThreshold
                    + ", expander=" + mExpanderRatio
                    + ", preGain=" + mPreGain
                    + ", postGain=" + mPostGain + "]";
        }

        public float getAttackTime() {
            return mAttackTime;
        }

        public void setAttackTime(float attackTime) {
            mAttackTime = attackTime;
        }

        public float getReleaseTime() {
            return mReleaseTime;
        }

        public void setReleaseTime(float releaseTime) {
            mReleaseTime = releaseTime;
        }

        public float getRatio() {
            return mRatio;
        }

        public void setRatio(float ratio) {
            mRatio = ratio;
        }

        public float getThreshold() {
            return mThreshold;
        }

        public void setThreshold(float threshold) {
            mThreshold = threshold;
        }

        public float getKneeWidth() {
            return mKneeWidth;
        }

        public void setKneeWidth(float kneeWidth) {
            mKneeWidth = kneeWidth;
        }

        public float getNoiseGateThreshold() {
            return mNoiseGateThreshold;
        }

        public void setNoiseGateThreshold(float noiseGateThreshold) {
            mNoiseGateThreshold = noiseGateThreshold;
        }

        public float getExpanderRatio() {
            return mExpanderRatio;
        }

        public void setExpanderRatio(float expanderRatio) {
            mExpanderRatio = expanderRatio;
        }

        public float getPreGain() {
            return mPreGain;
        }

        public void setPreGain(float preGain) {
            mPreGain = preGain;
        }

        public float getPostGain() {
            return mPostGain;
        }

        public void setPostGain(float postGain) {
            mPostGain = postGain;
        }
    }

    public static final class Eq extends BandStage {
        private final EqBand[] mBands;

        public Eq(boolean inUse, boolean enabled, int bandCount) {
            super(inUse, enabled, bandCount);
            int count = getBandCount();
            mBands = new EqBand[count];
            for (int i = 0; i < count; i++) {
                float freq = 20000.0f;
                if (count > 1) {
                    freq = (float) Math.pow(10.0,
                            Math.log10(220.0) + i * (Math.log10(20000.0) - Math.log10(220.0))
                                    / (count - 1));
                }
                mBands[i] = new EqBand(true, freq, 0.0f);
            }
        }

        public Eq(Eq cfg) {
            super(cfg.isInUse(), cfg.isEnabled(), cfg.getBandCount());
            mBands = new EqBand[cfg.mBands.length];
            for (int i = 0; i < mBands.length; i++) {
                mBands[i] = new EqBand(cfg.mBands[i]);
            }
        }

        @Override
        public String toString() {
            return super.toString() + " Eq[bands=" + mBands.length + "]";
        }

        private void checkBand(int band) {
            if (band < 0 || band >= mBands.length) {
                throw new IllegalArgumentException("Band index " + band + " out of bounds");
            }
        }

        public void setBand(int band, EqBand bandCfg) {
            checkBand(band);
            mBands[band] = new EqBand(bandCfg);
        }

        public EqBand getBand(int band) {
            checkBand(band);
            return new EqBand(mBands[band]);
        }
    }

    public static final class Mbc extends BandStage {
        private final MbcBand[] mBands;

        public Mbc(boolean inUse, boolean enabled, int bandCount) {
            super(inUse, enabled, bandCount);
            int count = getBandCount();
            mBands = new MbcBand[count];
            for (int i = 0; i < count; i++) {
                float freq = 20000.0f;
                if (count > 1) {
                    freq = (float) Math.pow(10.0,
                            Math.log10(220.0) + i * (Math.log10(20000.0) - Math.log10(220.0))
                                    / (count - 1));
                }
                mBands[i] = new MbcBand(
                        true, freq, 3.0f, 80.0f, 1.0f, -45.0f, 0.0f, -90.0f, 1.0f, 0.0f, 0.0f);
            }
        }

        public Mbc(Mbc cfg) {
            super(cfg.isInUse(), cfg.isEnabled(), cfg.getBandCount());
            mBands = new MbcBand[cfg.mBands.length];
            for (int i = 0; i < mBands.length; i++) {
                mBands[i] = new MbcBand(cfg.mBands[i]);
            }
        }

        @Override
        public String toString() {
            return super.toString() + " Mbc[bands=" + mBands.length + "]";
        }

        private void checkBand(int band) {
            if (band < 0 || band >= mBands.length) {
                throw new IllegalArgumentException("Band index " + band + " out of bounds");
            }
        }

        public void setBand(int band, MbcBand bandCfg) {
            checkBand(band);
            mBands[band] = new MbcBand(bandCfg);
        }

        public MbcBand getBand(int band) {
            checkBand(band);
            return new MbcBand(mBands[band]);
        }
    }

    public static final class Limiter extends Stage {
        private int mLinkGroup;
        private float mAttackTime;
        private float mReleaseTime;
        private float mRatio;
        private float mThreshold;
        private float mPostGain;

        public Limiter(
                boolean inUse,
                boolean enabled,
                int linkGroup,
                float attackTime,
                float releaseTime,
                float ratio,
                float threshold,
                float postGain) {
            super(inUse, enabled);
            mLinkGroup = linkGroup;
            mAttackTime = attackTime;
            mReleaseTime = releaseTime;
            mRatio = ratio;
            mThreshold = threshold;
            mPostGain = postGain;
        }

        public Limiter(Limiter cfg) {
            super(cfg.isInUse(), cfg.isEnabled());
            mLinkGroup = cfg.mLinkGroup;
            mAttackTime = cfg.mAttackTime;
            mReleaseTime = cfg.mReleaseTime;
            mRatio = cfg.mRatio;
            mThreshold = cfg.mThreshold;
            mPostGain = cfg.mPostGain;
        }

        @Override
        public String toString() {
            return super.toString()
                    + " Limiter[linkGroup=" + mLinkGroup
                    + ", attack=" + mAttackTime
                    + ", release=" + mReleaseTime
                    + ", ratio=" + mRatio
                    + ", threshold=" + mThreshold
                    + ", postGain=" + mPostGain + "]";
        }

        public int getLinkGroup() {
            return mLinkGroup;
        }

        public void setLinkGroup(int linkGroup) {
            mLinkGroup = linkGroup;
        }

        public float getAttackTime() {
            return mAttackTime;
        }

        public void setAttackTime(float attackTime) {
            mAttackTime = attackTime;
        }

        public float getReleaseTime() {
            return mReleaseTime;
        }

        public void setReleaseTime(float releaseTime) {
            mReleaseTime = releaseTime;
        }

        public float getRatio() {
            return mRatio;
        }

        public void setRatio(float ratio) {
            mRatio = ratio;
        }

        public float getThreshold() {
            return mThreshold;
        }

        public void setThreshold(float threshold) {
            mThreshold = threshold;
        }

        public float getPostGain() {
            return mPostGain;
        }

        public void setPostGain(float postGain) {
            mPostGain = postGain;
        }
    }

    public static final class Channel {
        private float mInputGain;
        private Eq mPreEq;
        private Mbc mMbc;
        private Eq mPostEq;
        private Limiter mLimiter;

        public Channel(
                float inputGain,
                boolean preEqInUse,
                int preEqBandCount,
                boolean mbcInUse,
                int mbcBandCount,
                boolean postEqInUse,
                int postEqBandCount,
                boolean limiterInUse) {
            mInputGain = inputGain;
            mPreEq = new Eq(preEqInUse, true, preEqBandCount);
            mMbc = new Mbc(mbcInUse, true, mbcBandCount);
            mPostEq = new Eq(postEqInUse, true, postEqBandCount);
            mLimiter = new Limiter(limiterInUse, true, 0, 1.0f, 60.0f, 10.0f, -2.0f, 0.0f);
        }

        public Channel(Channel cfg) {
            mInputGain = cfg.mInputGain;
            mPreEq = new Eq(cfg.mPreEq);
            mMbc = new Mbc(cfg.mMbc);
            mPostEq = new Eq(cfg.mPostEq);
            mLimiter = new Limiter(cfg.mLimiter);
        }

        @Override
        public String toString() {
            return "Channel[inputGain=" + mInputGain
                    + ", preEq=" + mPreEq
                    + ", mbc=" + mMbc
                    + ", postEq=" + mPostEq
                    + ", limiter=" + mLimiter + "]";
        }

        public float getInputGain() {
            return mInputGain;
        }

        public void setInputGain(float inputGain) {
            mInputGain = inputGain;
        }

        public Eq getPreEq() {
            return new Eq(mPreEq);
        }

        public void setPreEq(Eq preEq) {
            if (preEq.isInUse() != mPreEq.isInUse()
                    || preEq.getBandCount() != mPreEq.getBandCount()) {
                throw new IllegalArgumentException("PreEq configuration mismatch");
            }
            mPreEq = new Eq(preEq);
        }

        public EqBand getPreEqBand(int band) {
            return mPreEq.getBand(band);
        }

        public void setPreEqBand(int band, EqBand preEqBand) {
            mPreEq.setBand(band, preEqBand);
        }

        public Mbc getMbc() {
            return new Mbc(mMbc);
        }

        public void setMbc(Mbc mbc) {
            if (mbc.isInUse() != mMbc.isInUse()
                    || mbc.getBandCount() != mMbc.getBandCount()) {
                throw new IllegalArgumentException("Mbc configuration mismatch");
            }
            mMbc = new Mbc(mbc);
        }

        public MbcBand getMbcBand(int band) {
            return mMbc.getBand(band);
        }

        public void setMbcBand(int band, MbcBand mbcBand) {
            mMbc.setBand(band, mbcBand);
        }

        public Eq getPostEq() {
            return new Eq(mPostEq);
        }

        public void setPostEq(Eq postEq) {
            if (postEq.isInUse() != mPostEq.isInUse()
                    || postEq.getBandCount() != mPostEq.getBandCount()) {
                throw new IllegalArgumentException("PostEq configuration mismatch");
            }
            mPostEq = new Eq(postEq);
        }

        public EqBand getPostEqBand(int band) {
            return mPostEq.getBand(band);
        }

        public void setPostEqBand(int band, EqBand postEqBand) {
            mPostEq.setBand(band, postEqBand);
        }

        public Limiter getLimiter() {
            return new Limiter(mLimiter);
        }

        public void setLimiter(Limiter limiter) {
            if (limiter.isInUse() != mLimiter.isInUse()) {
                throw new IllegalArgumentException("Limiter configuration mismatch");
            }
            mLimiter = new Limiter(limiter);
        }
    }

    public static final class Config {
        public static final class Builder {
            private final int mVariant;
            private final int mChannelCount;
            private final boolean mPreEqInUse;
            private final int mPreEqBandCount;
            private final boolean mMbcInUse;
            private final int mMbcBandCount;
            private final boolean mPostEqInUse;
            private final int mPostEqBandCount;
            private final boolean mLimiterInUse;
            private float mPreferredFrameDuration = 10.0f;
            private final Channel[] mChannels;

            public Builder(
                    int variant,
                    int channelCount,
                    boolean preEqInUse,
                    int preEqBandCount,
                    boolean mbcInUse,
                    int mbcBandCount,
                    boolean postEqInUse,
                    int postEqBandCount,
                    boolean limiterInUse) {
                if (channelCount <= 0) {
                    throw new IllegalArgumentException("channelCount must be > 0");
                }
                mVariant = variant;
                mChannelCount = channelCount;
                mPreEqInUse = preEqInUse;
                mPreEqBandCount = preEqInUse ? preEqBandCount : 0;
                mMbcInUse = mbcInUse;
                mMbcBandCount = mbcInUse ? mbcBandCount : 0;
                mPostEqInUse = postEqInUse;
                mPostEqBandCount = postEqInUse ? postEqBandCount : 0;
                mLimiterInUse = limiterInUse;
                mChannels = new Channel[channelCount];
                for (int i = 0; i < channelCount; i++) {
                    mChannels[i] = new Channel(
                            0.0f, preEqInUse, mPreEqBandCount,
                            mbcInUse, mMbcBandCount,
                            postEqInUse, mPostEqBandCount,
                            limiterInUse);
                }
            }

            private void checkChannel(int channelIndex) {
                if (channelIndex < 0 || channelIndex >= mChannels.length) {
                    throw new IllegalArgumentException("Channel index out of bounds");
                }
            }

            public Builder setPreferredFrameDuration(float frameDuration) {
                if (frameDuration < 0) {
                    throw new IllegalArgumentException("Expected positive frameDuration");
                }
                mPreferredFrameDuration = frameDuration;
                return this;
            }

            public Builder setInputGainByChannelIndex(int channelIndex, float inputGain) {
                checkChannel(channelIndex);
                mChannels[channelIndex].setInputGain(inputGain);
                return this;
            }

            public Builder setInputGainAllChannelsTo(float inputGain) {
                for (int i = 0; i < mChannels.length; i++) {
                    mChannels[i].setInputGain(inputGain);
                }
                return this;
            }

            public Builder setChannelTo(int channelIndex, Channel channel) {
                checkChannel(channelIndex);
                mChannels[channelIndex] = new Channel(channel);
                return this;
            }

            public Builder setAllChannelsTo(Channel channel) {
                for (int i = 0; i < mChannels.length; i++) {
                    mChannels[i] = new Channel(channel);
                }
                return this;
            }

            public Builder setPreEqByChannelIndex(int channelIndex, Eq preEq) {
                checkChannel(channelIndex);
                mChannels[channelIndex].setPreEq(preEq);
                return this;
            }

            public Builder setPreEqAllChannelsTo(Eq preEq) {
                for (int i = 0; i < mChannels.length; i++) {
                    mChannels[i].setPreEq(preEq);
                }
                return this;
            }

            public Builder setMbcByChannelIndex(int channelIndex, Mbc mbc) {
                checkChannel(channelIndex);
                mChannels[channelIndex].setMbc(mbc);
                return this;
            }

            public Builder setMbcAllChannelsTo(Mbc mbc) {
                for (int i = 0; i < mChannels.length; i++) {
                    mChannels[i].setMbc(mbc);
                }
                return this;
            }

            public Builder setPostEqByChannelIndex(int channelIndex, Eq postEq) {
                checkChannel(channelIndex);
                mChannels[channelIndex].setPostEq(postEq);
                return this;
            }

            public Builder setPostEqAllChannelsTo(Eq postEq) {
                for (int i = 0; i < mChannels.length; i++) {
                    mChannels[i].setPostEq(postEq);
                }
                return this;
            }

            public Builder setLimiterByChannelIndex(int channelIndex, Limiter limiter) {
                checkChannel(channelIndex);
                mChannels[channelIndex].setLimiter(limiter);
                return this;
            }

            public Builder setLimiterAllChannelsTo(Limiter limiter) {
                for (int i = 0; i < mChannels.length; i++) {
                    mChannels[i].setLimiter(limiter);
                }
                return this;
            }

            public Config build() {
                return new Config(
                        mVariant, mPreferredFrameDuration, mChannelCount,
                        mPreEqInUse, mPreEqBandCount,
                        mMbcInUse, mMbcBandCount,
                        mPostEqInUse, mPostEqBandCount,
                        mLimiterInUse, mChannels);
            }
        }

        private final int mVariant;
        private final float mPreferredFrameDuration;
        private final int mChannelCount;
        private final boolean mPreEqInUse;
        private final int mPreEqBandCount;
        private final boolean mMbcInUse;
        private final int mMbcBandCount;
        private final boolean mPostEqInUse;
        private final int mPostEqBandCount;
        private final boolean mLimiterInUse;
        private final Channel[] mChannels;

        Config(
                int variant,
                float frameDuration,
                int channelCount,
                boolean preEqInUse,
                int preEqBandCount,
                boolean mbcInUse,
                int mbcBandCount,
                boolean postEqInUse,
                int postEqBandCount,
                boolean limiterInUse,
                Channel[] channels) {
            mVariant = variant;
            mPreferredFrameDuration = frameDuration;
            mChannelCount = channelCount;
            mPreEqInUse = preEqInUse;
            mPreEqBandCount = preEqBandCount;
            mMbcInUse = mbcInUse;
            mMbcBandCount = mbcBandCount;
            mPostEqInUse = postEqInUse;
            mPostEqBandCount = postEqBandCount;
            mLimiterInUse = limiterInUse;
            mChannels = new Channel[channelCount];
            for (int i = 0; i < channelCount; i++) {
                mChannels[i] = new Channel(channels[i]);
            }
        }

        Config(Config cfg) {
            this(cfg.mVariant, cfg.mPreferredFrameDuration, cfg.mChannelCount,
                    cfg.mPreEqInUse, cfg.mPreEqBandCount,
                    cfg.mMbcInUse, cfg.mMbcBandCount,
                    cfg.mPostEqInUse, cfg.mPostEqBandCount,
                    cfg.mLimiterInUse, cfg.mChannels);
        }

        int getChannelCountInternal() {
            return mChannelCount;
        }

        private void checkChannel(int channelIndex) {
            if (channelIndex < 0 || channelIndex >= mChannels.length) {
                throw new IllegalArgumentException("Channel index out of bounds");
            }
        }

        @Override
        public String toString() {
            return "Config[variant=" + mVariant
                    + ", frameDuration=" + mPreferredFrameDuration
                    + ", channels=" + mChannelCount + "]";
        }

        public int getVariant() {
            return mVariant;
        }

        public float getPreferredFrameDuration() {
            return mPreferredFrameDuration;
        }

        public boolean isPreEqInUse() {
            return mPreEqInUse;
        }

        public int getPreEqBandCount() {
            return mPreEqBandCount;
        }

        public boolean isMbcInUse() {
            return mMbcInUse;
        }

        public int getMbcBandCount() {
            return mMbcBandCount;
        }

        public boolean isPostEqInUse() {
            return mPostEqInUse;
        }

        public int getPostEqBandCount() {
            return mPostEqBandCount;
        }

        public boolean isLimiterInUse() {
            return mLimiterInUse;
        }

        public Channel getChannelByChannelIndex(int channelIndex) {
            checkChannel(channelIndex);
            return new Channel(mChannels[channelIndex]);
        }

        public void setChannelTo(int channelIndex, Channel channel) {
            checkChannel(channelIndex);
            mChannels[channelIndex] = new Channel(channel);
        }

        public void setAllChannelsTo(Channel channel) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i] = new Channel(channel);
            }
        }

        public float getInputGainByChannelIndex(int channelIndex) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getInputGain();
        }

        public void setInputGainByChannelIndex(int channelIndex, float inputGain) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setInputGain(inputGain);
        }

        public void setInputGainAllChannelsTo(float inputGain) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setInputGain(inputGain);
            }
        }

        public Eq getPreEqByChannelIndex(int channelIndex) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getPreEq();
        }

        public void setPreEqByChannelIndex(int channelIndex, Eq preEq) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setPreEq(preEq);
        }

        public void setPreEqAllChannelsTo(Eq preEq) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setPreEq(preEq);
            }
        }

        public EqBand getPreEqBandByChannelIndex(int channelIndex, int band) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getPreEqBand(band);
        }

        public void setPreEqBandByChannelIndex(int channelIndex, int band, EqBand preEqBand) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setPreEqBand(band, preEqBand);
        }

        public void setPreEqBandAllChannelsTo(int band, EqBand preEqBand) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setPreEqBand(band, preEqBand);
            }
        }

        public Mbc getMbcByChannelIndex(int channelIndex) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getMbc();
        }

        public void setMbcByChannelIndex(int channelIndex, Mbc mbc) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setMbc(mbc);
        }

        public void setMbcAllChannelsTo(Mbc mbc) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setMbc(mbc);
            }
        }

        public MbcBand getMbcBandByChannelIndex(int channelIndex, int band) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getMbcBand(band);
        }

        public void setMbcBandByChannelIndex(int channelIndex, int band, MbcBand mbcBand) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setMbcBand(band, mbcBand);
        }

        public void setMbcBandAllChannelsTo(int band, MbcBand mbcBand) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setMbcBand(band, mbcBand);
            }
        }

        public Eq getPostEqByChannelIndex(int channelIndex) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getPostEq();
        }

        public void setPostEqByChannelIndex(int channelIndex, Eq postEq) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setPostEq(postEq);
        }

        public void setPostEqAllChannelsTo(Eq postEq) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setPostEq(postEq);
            }
        }

        public EqBand getPostEqBandByChannelIndex(int channelIndex, int band) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getPostEqBand(band);
        }

        public void setPostEqBandByChannelIndex(int channelIndex, int band, EqBand postEqBand) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setPostEqBand(band, postEqBand);
        }

        public void setPostEqBandAllChannelsTo(int band, EqBand postEqBand) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setPostEqBand(band, postEqBand);
            }
        }

        public Limiter getLimiterByChannelIndex(int channelIndex) {
            checkChannel(channelIndex);
            return mChannels[channelIndex].getLimiter();
        }

        public void setLimiterByChannelIndex(int channelIndex, Limiter limiter) {
            checkChannel(channelIndex);
            mChannels[channelIndex].setLimiter(limiter);
        }

        public void setLimiterAllChannelsTo(Limiter limiter) {
            for (int i = 0; i < mChannels.length; i++) {
                mChannels[i].setLimiter(limiter);
            }
        }
    }

    private final Config mConfig;

    public DynamicsProcessing(int audioSession) {
        this(0, audioSession, null);
    }

    public DynamicsProcessing(int priority, int audioSession, Config cfg) {
        super(EFFECT_TYPE_DYNAMICS_PROCESSING, null, priority, audioSession);
        if (cfg != null) {
            mConfig = new Config(cfg);
        } else {
            mConfig = new Config.Builder(
                    VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    true, 6, true, 6, true, 6, true).build();
        }
    }

    public Config getConfig() {
        checkInitialized();
        return new Config(mConfig);
    }

    public Channel getChannelByChannelIndex(int channelIndex) {
        checkInitialized();
        return mConfig.getChannelByChannelIndex(channelIndex);
    }

    public void setChannelTo(int channelIndex, Channel channel) {
        checkInitialized();
        mConfig.setChannelTo(channelIndex, channel);
    }

    public void setAllChannelsTo(Channel channel) {
        checkInitialized();
        mConfig.setAllChannelsTo(channel);
    }

    public float getInputGainByChannelIndex(int channelIndex) {
        checkInitialized();
        return mConfig.getInputGainByChannelIndex(channelIndex);
    }

    public void setInputGainbyChannel(int channelIndex, float inputGain) {
        checkInitialized();
        mConfig.setInputGainByChannelIndex(channelIndex, inputGain);
    }

    public void setInputGainAllChannelsTo(float inputGain) {
        checkInitialized();
        mConfig.setInputGainAllChannelsTo(inputGain);
    }

    public Eq getPreEqByChannelIndex(int channelIndex) {
        checkInitialized();
        return mConfig.getPreEqByChannelIndex(channelIndex);
    }

    public void setPreEqByChannelIndex(int channelIndex, Eq preEq) {
        checkInitialized();
        mConfig.setPreEqByChannelIndex(channelIndex, preEq);
    }

    public void setPreEqAllChannelsTo(Eq preEq) {
        checkInitialized();
        mConfig.setPreEqAllChannelsTo(preEq);
    }

    public EqBand getPreEqBandByChannelIndex(int channelIndex, int band) {
        checkInitialized();
        return mConfig.getPreEqBandByChannelIndex(channelIndex, band);
    }

    public void setPreEqBandByChannelIndex(int channelIndex, int band, EqBand preEqBand) {
        checkInitialized();
        mConfig.setPreEqBandByChannelIndex(channelIndex, band, preEqBand);
    }

    public void setPreEqBandAllChannelsTo(int band, EqBand preEqBand) {
        checkInitialized();
        mConfig.setPreEqBandAllChannelsTo(band, preEqBand);
    }

    public Mbc getMbcByChannelIndex(int channelIndex) {
        checkInitialized();
        return mConfig.getMbcByChannelIndex(channelIndex);
    }

    public void setMbcByChannelIndex(int channelIndex, Mbc mbc) {
        checkInitialized();
        mConfig.setMbcByChannelIndex(channelIndex, mbc);
    }

    public void setMbcAllChannelsTo(Mbc mbc) {
        checkInitialized();
        mConfig.setMbcAllChannelsTo(mbc);
    }

    public MbcBand getMbcBandByChannelIndex(int channelIndex, int band) {
        checkInitialized();
        return mConfig.getMbcBandByChannelIndex(channelIndex, band);
    }

    public void setMbcBandByChannelIndex(int channelIndex, int band, MbcBand mbcBand) {
        checkInitialized();
        mConfig.setMbcBandByChannelIndex(channelIndex, band, mbcBand);
    }

    public void setMbcBandAllChannelsTo(int band, MbcBand mbcBand) {
        checkInitialized();
        mConfig.setMbcBandAllChannelsTo(band, mbcBand);
    }

    public Eq getPostEqByChannelIndex(int channelIndex) {
        checkInitialized();
        return mConfig.getPostEqByChannelIndex(channelIndex);
    }

    public void setPostEqByChannelIndex(int channelIndex, Eq postEq) {
        checkInitialized();
        mConfig.setPostEqByChannelIndex(channelIndex, postEq);
    }

    public void setPostEqAllChannelsTo(Eq postEq) {
        checkInitialized();
        mConfig.setPostEqAllChannelsTo(postEq);
    }

    public EqBand getPostEqBandByChannelIndex(int channelIndex, int band) {
        checkInitialized();
        return mConfig.getPostEqBandByChannelIndex(channelIndex, band);
    }

    public void setPostEqBandByChannelIndex(int channelIndex, int band, EqBand postEqBand) {
        checkInitialized();
        mConfig.setPostEqBandByChannelIndex(channelIndex, band, postEqBand);
    }

    public void setPostEqBandAllChannelsTo(int band, EqBand postEqBand) {
        checkInitialized();
        mConfig.setPostEqBandAllChannelsTo(band, postEqBand);
    }

    public Limiter getLimiterByChannelIndex(int channelIndex) {
        checkInitialized();
        return mConfig.getLimiterByChannelIndex(channelIndex);
    }

    public void setLimiterByChannelIndex(int channelIndex, Limiter limiter) {
        checkInitialized();
        mConfig.setLimiterByChannelIndex(channelIndex, limiter);
    }

    public void setLimiterAllChannelsTo(Limiter limiter) {
        checkInitialized();
        mConfig.setLimiterAllChannelsTo(limiter);
    }

    public int getChannelCount() {
        checkInitialized();
        return mConfig.getChannelCountInternal();
    }
}
