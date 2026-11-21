// SPDX-License-Identifier: GPL-3.0-only
package android.content.om;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** System service managing Runtime Resource Overlays (RROs) and fabricated overlays. */
public class OverlayManager {
    private final Map<OverlayIdentifier, OverlayInfo> mRegistered =
            new LinkedHashMap<OverlayIdentifier, OverlayInfo>();

    public OverlayManager() {}

    public synchronized void commit(OverlayManagerTransaction transaction) {
        Objects.requireNonNull(transaction);
        for (OverlayManagerTransaction.Request req : transaction.mRequests) {
            if (req.type == OverlayManagerTransaction.Request.TYPE_REGISTER) {
                OverlayInfo info = new OverlayInfo(
                        req.identifier.getPackageName(),
                        req.identifier.getOverlayName(),
                        req.targetPackage,
                        req.targetOverlayable);
                mRegistered.put(req.identifier, info);
            } else if (req.type == OverlayManagerTransaction.Request.TYPE_UNREGISTER) {
                mRegistered.remove(req.identifier);
            }
        }
    }

    public synchronized List<OverlayInfo> getOverlayInfosForTarget(String targetPackageName) {
        Objects.requireNonNull(targetPackageName);
        List<OverlayInfo> out = new ArrayList<OverlayInfo>();
        for (OverlayInfo info : mRegistered.values()) {
            if (targetPackageName.equals(info.getTargetPackageName())) {
                out.add(info);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
