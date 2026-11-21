// SPDX-License-Identifier: GPL-3.0-only
package android.content.om;

import android.content.res.AssetFileDescriptor;
import android.os.ParcelFileDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Dynamically constructed runtime resource overlay targeting a package's resources. */
public class FabricatedOverlay {
    private final String mOverlayName;
    private final String mTargetPackage;
    private String mTargetOverlayable;
    private String mOwningPackage = "android";
    final Map<String, Object> mEntries = new LinkedHashMap<String, Object>();

    public FabricatedOverlay(String overlayName, String targetPackage) {
        if (overlayName == null || overlayName.isEmpty()) {
            throw new IllegalArgumentException("overlayName must be non-empty");
        }
        if (targetPackage == null || targetPackage.isEmpty()) {
            throw new IllegalArgumentException("targetPackage must be non-empty");
        }
        mOverlayName = overlayName;
        mTargetPackage = targetPackage;
    }

    public OverlayIdentifier getIdentifier() {
        return new OverlayIdentifier(mOwningPackage, mOverlayName);
    }

    String getOverlayName() {
        return mOverlayName;
    }

    String getTargetPackage() {
        return mTargetPackage;
    }

    String getTargetOverlayable() {
        return mTargetOverlayable;
    }

    public void setTargetOverlayable(String targetOverlayable) {
        mTargetOverlayable = targetOverlayable;
    }

    public void setResourceValue(
            String resourceName, int dataType, int value, String configuration) {
        validateResourceName(resourceName);
        String key = configuration == null ? resourceName : (resourceName + "@" + configuration);
        mEntries.put(key, new int[] { dataType, value });
    }

    public void setResourceValue(
            String resourceName, int dataType, String value, String configuration) {
        validateResourceName(resourceName);
        Objects.requireNonNull(value, "value must not be null");
        String key = configuration == null ? resourceName : (resourceName + "@" + configuration);
        mEntries.put(key, value);
    }

    public void setResourceValue(
            String resourceName, ParcelFileDescriptor value, String configuration) {
        validateResourceName(resourceName);
        Objects.requireNonNull(value, "value must not be null");
        String key = configuration == null ? resourceName : (resourceName + "@" + configuration);
        mEntries.put(key, value);
    }

    public void setNinePatchResourceValue(
            String resourceName, ParcelFileDescriptor value, String configuration) {
        setResourceValue(resourceName, value, configuration);
    }

    public void setResourceValue(
            String resourceName, AssetFileDescriptor value, String configuration) {
        validateResourceName(resourceName);
        Objects.requireNonNull(value, "value must not be null");
        String key = configuration == null ? resourceName : (resourceName + "@" + configuration);
        mEntries.put(key, value);
    }

    private static void validateResourceName(String resourceName) {
        if (resourceName == null || resourceName.indexOf('/') <= 0) {
            throw new IllegalArgumentException(
                    "Resource name must have format [package:]type/entry: " + resourceName);
        }
    }
}
