// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import java.util.List;

@Deprecated
public class FragmentManagerNonConfig {
    private final List<Fragment> mFragments;
    private final List<FragmentManagerNonConfig> mChildNonConfigs;

    FragmentManagerNonConfig(List<Fragment> fragments, List<FragmentManagerNonConfig> childNonConfigs) {
        mFragments = fragments;
        mChildNonConfigs = childNonConfigs;
    }

    List<Fragment> getFragments() {
        return mFragments;
    }

    List<FragmentManagerNonConfig> getChildNonConfigs() {
        return mChildNonConfigs;
    }
}
