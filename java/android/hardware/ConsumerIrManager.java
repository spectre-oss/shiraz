// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

public final class ConsumerIrManager {
    public ConsumerIrManager() {
    }

    public boolean hasIrEmitter() {
        return false;
    }

    public void transmit(int carrierFrequency, int[] pattern) {
        if (carrierFrequency <= 0) {
            throw new IllegalArgumentException("Invalid carrier frequency");
        }
        if (pattern == null || pattern.length == 0) {
            throw new IllegalArgumentException("Invalid pattern");
        }
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] <= 0) {
                throw new IllegalArgumentException("Non-positive pulse duration");
            }
        }
    }

    public CarrierFrequencyRange[] getCarrierFrequencies() {
        return new CarrierFrequencyRange[0];
    }

    public final class CarrierFrequencyRange {
        private final int mMinFrequency;
        private final int mMaxFrequency;

        public CarrierFrequencyRange(int min, int max) {
            mMinFrequency = min;
            mMaxFrequency = max;
        }

        public int getMinFrequency() {
            return mMinFrequency;
        }

        public int getMaxFrequency() {
            return mMaxFrequency;
        }
    }
}
