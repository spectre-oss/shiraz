// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MultiResolutionStreamConfigurationMap {
    private final Map<Integer, List<MultiResolutionStreamInfo>> mOutputConfigs;
    private final Map<Integer, List<MultiResolutionStreamInfo>> mInputConfigs;

    public MultiResolutionStreamConfigurationMap(
            Map<Integer, List<MultiResolutionStreamInfo>> outputConfigs,
            Map<Integer, List<MultiResolutionStreamInfo>> inputConfigs) {
        mOutputConfigs = copyConfigMap(outputConfigs);
        mInputConfigs = copyConfigMap(inputConfigs);
    }

    private static Map<Integer, List<MultiResolutionStreamInfo>> copyConfigMap(
            Map<Integer, List<MultiResolutionStreamInfo>> src) {
        Map<Integer, List<MultiResolutionStreamInfo>> dst =
                new LinkedHashMap<Integer, List<MultiResolutionStreamInfo>>();
        if (src != null) {
            for (Map.Entry<Integer, List<MultiResolutionStreamInfo>> e : src.entrySet()) {
                List<MultiResolutionStreamInfo> val = e.getValue();
                if (val != null) {
                    dst.put(e.getKey(),
                            Collections.unmodifiableList(new ArrayList<MultiResolutionStreamInfo>(val)));
                }
            }
        }
        return dst;
    }

    public int[] getOutputFormats() {
        return toIntArray(mOutputConfigs.keySet());
    }

    public int[] getInputFormats() {
        return toIntArray(mInputConfigs.keySet());
    }

    private static int[] toIntArray(Collection<Integer> keys) {
        int[] res = new int[keys.size()];
        int i = 0;
        for (Integer k : keys) {
            res[i++] = k.intValue();
        }
        return res;
    }

    public Collection<MultiResolutionStreamInfo> getOutputInfo(int format) {
        List<MultiResolutionStreamInfo> list = mOutputConfigs.get(Integer.valueOf(format));
        return list != null ? list : Collections.<MultiResolutionStreamInfo>emptyList();
    }

    public Collection<MultiResolutionStreamInfo> getInputInfo(int format) {
        List<MultiResolutionStreamInfo> list = mInputConfigs.get(Integer.valueOf(format));
        return list != null ? list : Collections.<MultiResolutionStreamInfo>emptyList();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MultiResolutionStreamConfigurationMap)) return false;
        MultiResolutionStreamConfigurationMap other = (MultiResolutionStreamConfigurationMap) obj;
        return Objects.equals(mOutputConfigs, other.mOutputConfigs)
                && Objects.equals(mInputConfigs, other.mInputConfigs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOutputConfigs, mInputConfigs);
    }

    @Override
    public String toString() {
        return "MultiResolutionStreamConfigurationMap(outputs=" + mOutputConfigs
                + ", inputs=" + mInputConfigs + ")";
    }
}
