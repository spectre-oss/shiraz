// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public interface VolumeAutomation {
    VolumeShaper createVolumeShaper(VolumeShaper.Configuration configuration);
}
