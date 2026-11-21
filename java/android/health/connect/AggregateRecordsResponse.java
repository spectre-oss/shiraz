// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.DataOrigin;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AggregateRecordsResponse<T> {
    private final Map<AggregationType<T>, T> mValues;
    private final Map<AggregationType<T>, ZoneOffset> mZoneOffsets;
    private final Map<AggregationType<T>, Set<DataOrigin>> mDataOrigins;

    AggregateRecordsResponse(Map<AggregationType<T>, T> values,
            Map<AggregationType<T>, ZoneOffset> zoneOffsets,
            Map<AggregationType<T>, Set<DataOrigin>> dataOrigins) {
        mValues = new HashMap<AggregationType<T>, T>(values);
        mZoneOffsets = new HashMap<AggregationType<T>, ZoneOffset>(zoneOffsets);
        mDataOrigins = new HashMap<AggregationType<T>, Set<DataOrigin>>(dataOrigins);
    }

    public T get(AggregationType<T> aggregationType) {
        Objects.requireNonNull(aggregationType, "aggregationType must not be null");
        return mValues.get(aggregationType);
    }

    public ZoneOffset getZoneOffset(AggregationType<T> aggregationType) {
        Objects.requireNonNull(aggregationType, "aggregationType must not be null");
        return mZoneOffsets.get(aggregationType);
    }

    public Set<DataOrigin> getDataOrigins(AggregationType<T> aggregationType) {
        Objects.requireNonNull(aggregationType, "aggregationType must not be null");
        Set<DataOrigin> origins = mDataOrigins.get(aggregationType);
        if (origins == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<DataOrigin>(origins));
    }
}
