// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Maps buyer {@link AdTechIdentifier}s to overridden {@link DecisionLogic} scripts. */
public final class PerBuyerDecisionLogic implements Parcelable {
    public static final PerBuyerDecisionLogic EMPTY =
            new PerBuyerDecisionLogic(Collections.<AdTechIdentifier, DecisionLogic>emptyMap());

    private final Map<AdTechIdentifier, DecisionLogic> mPerBuyerLogicMap;

    public PerBuyerDecisionLogic(Map<AdTechIdentifier, DecisionLogic> perBuyerLogicMap) {
        Objects.requireNonNull(perBuyerLogicMap, "perBuyerLogicMap");
        Map<AdTechIdentifier, DecisionLogic> copy =
                new LinkedHashMap<AdTechIdentifier, DecisionLogic>(perBuyerLogicMap.size());
        for (Map.Entry<AdTechIdentifier, DecisionLogic> entry : perBuyerLogicMap.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "buyer"),
                    Objects.requireNonNull(entry.getValue(), "decisionLogic"));
        }
        this.mPerBuyerLogicMap = Collections.unmodifiableMap(copy);
    }

    public Map<AdTechIdentifier, DecisionLogic> getPerBuyerLogicMap() {
        return mPerBuyerLogicMap;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mPerBuyerLogicMap.size());
        for (Map.Entry<AdTechIdentifier, DecisionLogic> entry : mPerBuyerLogicMap.entrySet()) {
            dest.writeTypedObject(entry.getKey(), flags);
            dest.writeTypedObject(entry.getValue(), flags);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPerBuyerLogicMap);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PerBuyerDecisionLogic)) {
            return false;
        }
        PerBuyerDecisionLogic that = (PerBuyerDecisionLogic) o;
        return Objects.equals(mPerBuyerLogicMap, that.mPerBuyerLogicMap);
    }

    public static final Parcelable.Creator<PerBuyerDecisionLogic> CREATOR =
            new Parcelable.Creator<PerBuyerDecisionLogic>() {
                @Override
                public PerBuyerDecisionLogic createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    int size = in.readInt();
                    Map<AdTechIdentifier, DecisionLogic> map =
                            new LinkedHashMap<AdTechIdentifier, DecisionLogic>(size);
                    for (int i = 0; i < size; i++) {
                        AdTechIdentifier buyer = in.readTypedObject(AdTechIdentifier.CREATOR);
                        DecisionLogic logic = in.readTypedObject(DecisionLogic.CREATOR);
                        map.put(buyer, logic);
                    }
                    return new PerBuyerDecisionLogic(map);
                }

                @Override
                public PerBuyerDecisionLogic[] newArray(int size) {
                    return new PerBuyerDecisionLogic[size];
                }
            };
}
