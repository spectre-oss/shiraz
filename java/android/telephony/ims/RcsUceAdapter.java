// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import android.content.Context;

public class RcsUceAdapter {
    private final Context mContext;
    private final int mSubId;

    RcsUceAdapter(Context context, int subId) {
        mContext = context;
        mSubId = subId;
    }

    public boolean isUceSettingEnabled() throws ImsException {
        return false;
    }
}
