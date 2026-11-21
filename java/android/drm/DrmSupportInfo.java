// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

import java.util.ArrayList;
import java.util.Iterator;

/** Supported MIME types, file suffixes, and description of a DRM engine plugin. */
@Deprecated
public class DrmSupportInfo {
    private final ArrayList<String> mFileSuffixList = new ArrayList<String>();
    private final ArrayList<String> mMimeTypeList = new ArrayList<String>();
    private String mDescription = "";

    public DrmSupportInfo() {
    }

    public void addMimeType(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("mimeType cannot be null");
        }
        if (mimeType.isEmpty()) {
            throw new IllegalArgumentException("mimeType cannot be an empty string");
        }
        mMimeTypeList.add(mimeType);
    }

    public void addFileSuffix(String fileSuffix) {
        if (fileSuffix == null || fileSuffix.isEmpty()) {
            throw new IllegalArgumentException("fileSuffix cannot be null or an empty string");
        }
        mFileSuffixList.add(fileSuffix);
    }

    public Iterator<String> getMimeTypeIterator() {
        return mMimeTypeList.iterator();
    }

    public Iterator<String> getFileSuffixIterator() {
        return mFileSuffixList.iterator();
    }

    public void setDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("description cannot be null");
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description cannot be an empty string");
        }
        mDescription = description;
    }

    @Deprecated
    public String getDescriprition() {
        return mDescription;
    }

    public String getDescription() {
        return mDescription;
    }

    @Override
    public int hashCode() {
        return mFileSuffixList.hashCode() + mMimeTypeList.hashCode() + mDescription.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof DrmSupportInfo) {
            DrmSupportInfo info = (DrmSupportInfo) object;
            return mFileSuffixList.equals(info.mFileSuffixList)
                    && mMimeTypeList.equals(info.mMimeTypeList)
                    && mDescription.equals(info.mDescription);
        }
        return false;
    }
}
