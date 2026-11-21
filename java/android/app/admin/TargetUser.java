// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import java.util.Objects;

public final class TargetUser {
    static final int LOCAL_USER_ID = -1;
    static final int PARENT_USER_ID = -2;
    static final int GLOBAL_USER_ID = -3;
    static final int UNKNOWN_USER_ID = -4;

    public static final TargetUser LOCAL_USER = new TargetUser(LOCAL_USER_ID);
    public static final TargetUser PARENT_USER = new TargetUser(PARENT_USER_ID);
    public static final TargetUser GLOBAL = new TargetUser(GLOBAL_USER_ID);
    public static final TargetUser UNKNOWN_USER = new TargetUser(UNKNOWN_USER_ID);

    private final int mUserId;

    private TargetUser(int userId) {
        mUserId = userId;
    }

    static TargetUser fromId(int id) {
        switch (id) {
            case LOCAL_USER_ID: return LOCAL_USER;
            case PARENT_USER_ID: return PARENT_USER;
            case GLOBAL_USER_ID: return GLOBAL;
            default: return UNKNOWN_USER;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetUser)) return false;
        TargetUser other = (TargetUser) o;
        return mUserId == other.mUserId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mUserId));
    }
}
