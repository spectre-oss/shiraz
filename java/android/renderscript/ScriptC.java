// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import android.content.res.Resources;

public class ScriptC extends Script {
    protected ScriptC(int id, RenderScript rs) {
        super(id, rs);
    }

    protected ScriptC(long id, RenderScript rs) {
        super(id, rs);
    }

    protected ScriptC(RenderScript rs, Resources resources, int resourceID) {
        super(0, rs);
        if (resources == null) {
            throw new RSIllegalArgumentException("resources cannot be null.");
        }
    }

    protected ScriptC(RenderScript rs, String resName, byte[] bitcode32, byte[] bitcode64) {
        super(0, rs);
        if (resName == null) {
            throw new RSIllegalArgumentException("resName cannot be null.");
        }
    }
}
