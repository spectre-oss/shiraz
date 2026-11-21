// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

public class AccessControlProfileId {
    private final int mId;

    public AccessControlProfileId(int id) {
        mId = id;
    }

    public int getId() {
        return mId;
    }
}
