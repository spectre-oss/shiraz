// SPDX-License-Identifier: GPL-3.0-only
package android.content.om;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Atomic batch of register/unregister operations for OverlayManager. */
public final class OverlayManagerTransaction implements Parcelable {
    static final class Request {
        static final int TYPE_REGISTER = 1;
        static final int TYPE_UNREGISTER = 2;

        final int type;
        final OverlayIdentifier identifier;
        final String targetPackage;
        final String targetOverlayable;

        Request(int type, OverlayIdentifier identifier, String targetPackage, String targetOverlayable) {
            this.type = type;
            this.identifier = identifier;
            this.targetPackage = targetPackage;
            this.targetOverlayable = targetOverlayable;
        }
    }

    final List<Request> mRequests = new ArrayList<Request>();

    private OverlayManagerTransaction() {}

    OverlayManagerTransaction(Parcel in) {
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            int type = in.readInt();
            OverlayIdentifier id = OverlayIdentifier.CREATOR.createFromParcel(in);
            String targetPkg = in.readString();
            String targetOvl = in.readString();
            mRequests.add(new Request(type, id, targetPkg, targetOvl));
        }
    }

    public static OverlayManagerTransaction newInstance() {
        return new OverlayManagerTransaction();
    }

    public void registerFabricatedOverlay(FabricatedOverlay overlay) {
        Objects.requireNonNull(overlay);
        mRequests.add(new Request(
                Request.TYPE_REGISTER,
                overlay.getIdentifier(),
                overlay.getTargetPackage(),
                overlay.getTargetOverlayable()));
    }

    public void unregisterFabricatedOverlay(OverlayIdentifier overlay) {
        Objects.requireNonNull(overlay);
        mRequests.add(new Request(Request.TYPE_UNREGISTER, overlay, null, null));
    }

    @Override
    public String toString() {
        return "OverlayManagerTransaction{requests=" + mRequests.size() + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRequests.size());
        for (Request r : mRequests) {
            dest.writeInt(r.type);
            r.identifier.writeToParcel(dest, flags);
            dest.writeString(r.targetPackage);
            dest.writeString(r.targetOverlayable);
        }
    }

    public static final Creator<OverlayManagerTransaction> CREATOR =
            new Creator<OverlayManagerTransaction>() {
                @Override
                public OverlayManagerTransaction createFromParcel(Parcel source) {
                    return new OverlayManagerTransaction(source);
                }

                @Override
                public OverlayManagerTransaction[] newArray(int size) {
                    return new OverlayManagerTransaction[size];
                }
            };
}
