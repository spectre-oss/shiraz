// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.InstantRecord;
import android.health.connect.datatypes.IntervalRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.os.OutcomeReceiver;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

public class HealthConnectManager {
    public static final String ACTION_MANAGE_HEALTH_PERMISSIONS =
            "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS";
    public static final String ACTION_REQUEST_EXERCISE_ROUTE =
            "android.health.connect.action.REQUEST_EXERCISE_ROUTE";
    public static final String CATEGORY_HEALTH_PERMISSIONS =
            "android.intent.category.HEALTH_PERMISSIONS";
    public static final String EXTRA_EXERCISE_ROUTE =
            "android.health.connect.extra.EXERCISE_ROUTE";
    public static final String EXTRA_SESSION_ID =
            "android.health.connect.extra.SESSION_ID";

    private static final class ChangeEvent {
        final long seq;
        final Record upserted;
        final String deletedId;
        final long deletedTimeMillis;
        final Class<? extends Record> recordClass;
        final DataOrigin dataOrigin;

        ChangeEvent(long seq, Record upserted, String deletedId, long deletedTimeMillis,
                Class<? extends Record> recordClass, DataOrigin dataOrigin) {
            this.seq = seq;
            this.upserted = upserted;
            this.deletedId = deletedId;
            this.deletedTimeMillis = deletedTimeMillis;
            this.recordClass = recordClass;
            this.dataOrigin = dataOrigin;
        }
    }

    private static final class TokenState {
        final long cursorSeq;
        final Set<Class<? extends Record>> recordTypes;
        final Set<DataOrigin> dataOriginFilters;

        TokenState(long cursorSeq, Set<Class<? extends Record>> recordTypes,
                Set<DataOrigin> dataOriginFilters) {
            this.cursorSeq = cursorSeq;
            this.recordTypes = new LinkedHashSet<Class<? extends Record>>(recordTypes);
            this.dataOriginFilters = new LinkedHashSet<DataOrigin>(dataOriginFilters);
        }
    }

    private final Object mLock = new Object();
    private final Map<String, Record> mRecordsById = new LinkedHashMap<String, Record>();
    private final List<ChangeEvent> mChangeEvents = new ArrayList<ChangeEvent>();
    private final Map<String, TokenState> mTokens = new HashMap<String, TokenState>();
    private long mNextRecordNum = 1L;
    private long mNextEventSeq = 1L;
    private long mNextTokenNum = 1L;

    public HealthConnectManager() {
    }

    private static <R> void deliverResult(Executor executor,
            final OutcomeReceiver<R, HealthConnectException> callback, final R result) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(result);
            }
        });
    }

    private static <R> void deliverError(Executor executor,
            final OutcomeReceiver<R, HealthConnectException> callback,
            final HealthConnectException error) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    public void insertRecords(List<Record> records, Executor executor,
            OutcomeReceiver<InsertRecordsResponse, HealthConnectException> callback) {
        Objects.requireNonNull(records, "records must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        final List<Record> inserted = new ArrayList<Record>(records.size());
        synchronized (mLock) {
            Instant now = Instant.now();
            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                if (record == null) {
                    deliverError(executor, callback, new HealthConnectException(
                            HealthConnectException.ERROR_INVALID_ARGUMENT, "Null record"));
                    return;
                }
                Metadata meta = record.getMetadata();
                String existingId = findByClientRecordIdLocked(record.getClass(), meta.getClientRecordId());
                String assignedId = existingId != null ? existingId
                        : (meta.getId() != null && !meta.getId().isEmpty()
                                ? meta.getId()
                                : "hc-rec-" + (mNextRecordNum++));
                Metadata updatedMeta = meta.shirazWithAssignedId(assignedId, now);
                record.shirazAssignMetadata(updatedMeta);
                mRecordsById.put(assignedId, record);
                mChangeEvents.add(new ChangeEvent(mNextEventSeq++, record, null, 0L,
                        record.getClass(), updatedMeta.getDataOrigin()));
                inserted.add(record);
            }
        }
        deliverResult(executor, callback, new InsertRecordsResponse(inserted));
    }

    public void updateRecords(List<Record> records, Executor executor,
            OutcomeReceiver<Void, HealthConnectException> callback) {
        Objects.requireNonNull(records, "records must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        synchronized (mLock) {
            Instant now = Instant.now();
            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                if (record == null) {
                    deliverError(executor, callback, new HealthConnectException(
                            HealthConnectException.ERROR_INVALID_ARGUMENT, "Null record"));
                    return;
                }
                Metadata meta = record.getMetadata();
                String targetId = meta.getId();
                if (targetId == null || targetId.isEmpty() || !mRecordsById.containsKey(targetId)) {
                    targetId = findByClientRecordIdLocked(record.getClass(), meta.getClientRecordId());
                }
                if (targetId == null || !mRecordsById.containsKey(targetId)) {
                    deliverError(executor, callback, new HealthConnectException(
                            HealthConnectException.ERROR_INVALID_ARGUMENT,
                            "Record to update does not exist"));
                    return;
                }
                Metadata updatedMeta = meta.shirazWithAssignedId(targetId, now);
                record.shirazAssignMetadata(updatedMeta);
                mRecordsById.put(targetId, record);
                mChangeEvents.add(new ChangeEvent(mNextEventSeq++, record, null, 0L,
                        record.getClass(), updatedMeta.getDataOrigin()));
            }
        }
        deliverResult(executor, callback, null);
    }

    private String findByClientRecordIdLocked(Class<? extends Record> recordClass,
            String clientRecordId) {
        if (clientRecordId == null || clientRecordId.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Record> entry : mRecordsById.entrySet()) {
            Record r = entry.getValue();
            if (r.getClass() == recordClass
                    && clientRecordId.equals(r.getMetadata().getClientRecordId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void deleteRecords(List<RecordIdFilter> recordIds, Executor executor,
            OutcomeReceiver<Void, HealthConnectException> callback) {
        Objects.requireNonNull(recordIds, "recordIds must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        synchronized (mLock) {
            long nowMillis = System.currentTimeMillis();
            for (int i = 0; i < recordIds.size(); i++) {
                RecordIdFilter filter = recordIds.get(i);
                if (filter == null) continue;
                String targetId = filter.getId();
                if (targetId == null) {
                    targetId = findByClientRecordIdLocked(
                            filter.getRecordType(), filter.getClientRecordId());
                }
                if (targetId != null) {
                    Record existing = mRecordsById.get(targetId);
                    if (existing != null && filter.getRecordType().isInstance(existing)) {
                        mRecordsById.remove(targetId);
                        mChangeEvents.add(new ChangeEvent(mNextEventSeq++, null, targetId, nowMillis,
                                existing.getClass(), existing.getMetadata().getDataOrigin()));
                    }
                }
            }
        }
        deliverResult(executor, callback, null);
    }

    public void deleteRecords(Class<? extends Record> recordType, TimeRangeFilter timeRangeFilter,
            Executor executor, OutcomeReceiver<Void, HealthConnectException> callback) {
        Objects.requireNonNull(recordType, "recordType must not be null");
        Objects.requireNonNull(timeRangeFilter, "timeRangeFilter must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        synchronized (mLock) {
            long nowMillis = System.currentTimeMillis();
            List<String> toDelete = new ArrayList<String>();
            for (Map.Entry<String, Record> entry : mRecordsById.entrySet()) {
                Record r = entry.getValue();
                if (recordType.isInstance(r) && matchesTimeRange(r, timeRangeFilter)) {
                    toDelete.add(entry.getKey());
                }
            }
            for (int i = 0; i < toDelete.size(); i++) {
                String id = toDelete.get(i);
                Record removed = mRecordsById.remove(id);
                if (removed != null) {
                    mChangeEvents.add(new ChangeEvent(mNextEventSeq++, null, id, nowMillis,
                            removed.getClass(), removed.getMetadata().getDataOrigin()));
                }
            }
        }
        deliverResult(executor, callback, null);
    }

    @SuppressWarnings("unchecked")
    public <T extends Record> void readRecords(ReadRecordsRequest<T> request, Executor executor,
            OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        Class<T> recordType = request.getRecordType();
        List<T> matched = new ArrayList<T>();
        long nextPageToken = -1L;

        synchronized (mLock) {
            if (request instanceof ReadRecordsRequestUsingIds) {
                ReadRecordsRequestUsingIds<T> idReq = (ReadRecordsRequestUsingIds<T>) request;
                List<RecordIdFilter> filters = idReq.getRecordIdFilters();
                for (int i = 0; i < filters.size(); i++) {
                    RecordIdFilter f = filters.get(i);
                    String id = f.getId();
                    if (id == null) {
                        id = findByClientRecordIdLocked(recordType, f.getClientRecordId());
                    }
                    if (id != null) {
                        Record r = mRecordsById.get(id);
                        if (recordType.isInstance(r)) {
                            matched.add((T) r);
                        }
                    }
                }
            } else if (request instanceof ReadRecordsRequestUsingFilters) {
                ReadRecordsRequestUsingFilters<T> filterReq =
                        (ReadRecordsRequestUsingFilters<T>) request;
                TimeRangeFilter timeFilter = filterReq.getTimeRangeFilter();
                Set<DataOrigin> origins = filterReq.getDataOrigins();
                for (Record r : mRecordsById.values()) {
                    if (!recordType.isInstance(r)) continue;
                    if (!origins.isEmpty() && !origins.contains(r.getMetadata().getDataOrigin())) {
                        continue;
                    }
                    if (timeFilter != null && !matchesTimeRange(r, timeFilter)) {
                        continue;
                    }
                    matched.add((T) r);
                }
                final boolean asc = filterReq.isAscending();
                Collections.sort(matched, new Comparator<T>() {
                    @Override
                    public int compare(T a, T b) {
                        Instant ta = primaryInstant(a);
                        Instant tb = primaryInstant(b);
                        int cmp = ta.compareTo(tb);
                        return asc ? cmp : -cmp;
                    }
                });
                int startIndex = filterReq.getPageToken() > 0
                        ? (int) filterReq.getPageToken() : 0;
                if (startIndex >= matched.size()) {
                    matched = Collections.emptyList();
                } else {
                    int endIndex = Math.min(matched.size(), startIndex + filterReq.getPageSize());
                    if (endIndex < matched.size()) {
                        nextPageToken = endIndex;
                    }
                    matched = new ArrayList<T>(matched.subList(startIndex, endIndex));
                }
            }
        }
        deliverResult(executor, callback, new ReadRecordsResponse<T>(matched, nextPageToken));
    }

    public void getChangeLogToken(ChangeLogTokenRequest request, Executor executor,
            OutcomeReceiver<ChangeLogTokenResponse, HealthConnectException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        String token;
        synchronized (mLock) {
            token = "hc-tok-" + (mNextTokenNum++);
            mTokens.put(token, new TokenState(mNextEventSeq,
                    request.getRecordTypes(), request.getDataOriginFilters()));
        }
        deliverResult(executor, callback, new ChangeLogTokenResponse(token));
    }

    public void getChangeLogs(ChangeLogsRequest request, Executor executor,
            OutcomeReceiver<ChangeLogsResponse, HealthConnectException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        ChangeLogsResponse response;
        synchronized (mLock) {
            TokenState state = mTokens.get(request.getToken());
            if (state == null) {
                deliverError(executor, callback, new HealthConnectException(
                        HealthConnectException.ERROR_INVALID_ARGUMENT, "Invalid change log token"));
                return;
            }
            Map<String, Record> upserts = new LinkedHashMap<String, Record>();
            Map<String, ChangeLogsResponse.DeletedLog> deletes =
                    new LinkedHashMap<String, ChangeLogsResponse.DeletedLog>();
            int count = 0;
            long lastSeq = state.cursorSeq;
            boolean hasMore = false;
            for (int i = 0; i < mChangeEvents.size(); i++) {
                ChangeEvent ev = mChangeEvents.get(i);
                if (ev.seq < state.cursorSeq) continue;
                if (!state.recordTypes.isEmpty() && !state.recordTypes.contains(ev.recordClass)) {
                    continue;
                }
                if (!state.dataOriginFilters.isEmpty()
                        && !state.dataOriginFilters.contains(ev.dataOrigin)) {
                    continue;
                }
                if (count >= request.getPageSize()) {
                    hasMore = true;
                    break;
                }
                count++;
                lastSeq = ev.seq + 1;
                if (ev.upserted != null) {
                    String id = ev.upserted.getMetadata().getId();
                    deletes.remove(id);
                    upserts.put(id, ev.upserted);
                } else if (ev.deletedId != null) {
                    upserts.remove(ev.deletedId);
                    deletes.put(ev.deletedId,
                            new ChangeLogsResponse.DeletedLog(ev.deletedId, ev.deletedTimeMillis));
                }
            }
            if (!hasMore) {
                lastSeq = mNextEventSeq;
            }
            String nextToken = "hc-tok-" + (mNextTokenNum++);
            mTokens.put(nextToken, new TokenState(lastSeq,
                    state.recordTypes, state.dataOriginFilters));
            response = new ChangeLogsResponse(
                    new ArrayList<Record>(upserts.values()),
                    new ArrayList<ChangeLogsResponse.DeletedLog>(deletes.values()),
                    nextToken,
                    hasMore);
        }
        deliverResult(executor, callback, response);
    }

    public <T> void aggregate(AggregateRecordsRequest<T> request, Executor executor,
            OutcomeReceiver<AggregateRecordsResponse<T>, HealthConnectException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        AggregateRecordsResponse<T> resp;
        synchronized (mLock) {
            resp = computeAggregateLocked(request.getAggregationTypes(),
                    request.getDataOriginsFilters(), request.getTimeRangeFilter());
        }
        deliverResult(executor, callback, resp);
    }

    public <T> void aggregateGroupByDuration(AggregateRecordsRequest<T> request, Duration duration,
            Executor executor,
            OutcomeReceiver<List<AggregateRecordsGroupedByDurationResponse<T>>, HealthConnectException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        if (duration.isZero() || duration.isNegative()) {
            deliverError(executor, callback, new HealthConnectException(
                    HealthConnectException.ERROR_INVALID_ARGUMENT, "duration must be positive"));
            return;
        }
        TimeRangeFilter filter = request.getTimeRangeFilter();
        Instant start;
        Instant end;
        if (filter instanceof TimeInstantRangeFilter) {
            TimeInstantRangeFilter tif = (TimeInstantRangeFilter) filter;
            start = tif.getStartTime() != null ? tif.getStartTime() : Instant.EPOCH;
            end = tif.getEndTime() != null ? tif.getEndTime() : Instant.now();
        } else if (filter instanceof LocalTimeRangeFilter) {
            LocalTimeRangeFilter ltf = (LocalTimeRangeFilter) filter;
            start = ltf.getStartTime() != null
                    ? ltf.getStartTime().toInstant(ZoneOffset.UTC) : Instant.EPOCH;
            end = ltf.getEndTime() != null
                    ? ltf.getEndTime().toInstant(ZoneOffset.UTC) : Instant.now();
        } else {
            start = Instant.EPOCH;
            end = Instant.now();
        }

        List<AggregateRecordsGroupedByDurationResponse<T>> groups =
                new ArrayList<AggregateRecordsGroupedByDurationResponse<T>>();
        synchronized (mLock) {
            Instant cursor = start;
            while (cursor.isBefore(end)) {
                Instant bucketEnd = cursor.plus(duration);
                if (bucketEnd.isAfter(end)) {
                    bucketEnd = end;
                }
                TimeInstantRangeFilter bucketFilter = new TimeInstantRangeFilter(cursor, bucketEnd);
                BucketData<T> bucket = computeBucketLocked(request.getAggregationTypes(),
                        request.getDataOriginsFilters(), bucketFilter);
                if (!bucket.values.isEmpty()) {
                    groups.add(new AggregateRecordsGroupedByDurationResponse<T>(
                            cursor, bucketEnd, bucket.values, bucket.zones, bucket.origins));
                }
                cursor = bucketEnd;
            }
        }
        deliverResult(executor, callback, groups);
    }

    public <T> void aggregateGroupByPeriod(AggregateRecordsRequest<T> request, Period period,
            Executor executor,
            OutcomeReceiver<List<AggregateRecordsGroupedByPeriodResponse<T>>, HealthConnectException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        if (period.isZero() || period.isNegative()) {
            deliverError(executor, callback, new HealthConnectException(
                    HealthConnectException.ERROR_INVALID_ARGUMENT, "period must be positive"));
            return;
        }
        TimeRangeFilter filter = request.getTimeRangeFilter();
        if (!(filter instanceof LocalTimeRangeFilter)) {
            deliverError(executor, callback, new HealthConnectException(
                    HealthConnectException.ERROR_INVALID_ARGUMENT,
                    "aggregateGroupByPeriod requires LocalTimeRangeFilter"));
            return;
        }
        LocalTimeRangeFilter ltf = (LocalTimeRangeFilter) filter;
        if (ltf.getStartTime() == null || ltf.getEndTime() == null) {
            deliverError(executor, callback, new HealthConnectException(
                    HealthConnectException.ERROR_INVALID_ARGUMENT,
                    "LocalTimeRangeFilter must be bounded"));
            return;
        }

        List<AggregateRecordsGroupedByPeriodResponse<T>> groups =
                new ArrayList<AggregateRecordsGroupedByPeriodResponse<T>>();
        synchronized (mLock) {
            LocalDateTime cursor = ltf.getStartTime();
            LocalDateTime end = ltf.getEndTime();
            while (cursor.isBefore(end)) {
                LocalDateTime bucketEnd = cursor.plus(period);
                if (bucketEnd.isAfter(end)) {
                    bucketEnd = end;
                }
                LocalTimeRangeFilter bucketFilter = new LocalTimeRangeFilter(cursor, bucketEnd);
                BucketData<T> bucket = computeBucketLocked(request.getAggregationTypes(),
                        request.getDataOriginsFilters(), bucketFilter);
                if (!bucket.values.isEmpty()) {
                    groups.add(new AggregateRecordsGroupedByPeriodResponse<T>(
                            cursor, bucketEnd, bucket.values, bucket.zones, bucket.origins));
                }
                cursor = bucketEnd;
            }
        }
        deliverResult(executor, callback, groups);
    }

    private static final class BucketData<T> {
        final Map<AggregationType<T>, T> values = new HashMap<AggregationType<T>, T>();
        final Map<AggregationType<T>, ZoneOffset> zones =
                new HashMap<AggregationType<T>, ZoneOffset>();
        final Map<AggregationType<T>, Set<DataOrigin>> origins =
                new HashMap<AggregationType<T>, Set<DataOrigin>>();
    }

    private <T> AggregateRecordsResponse<T> computeAggregateLocked(
            Set<AggregationType<T>> aggTypes, Set<DataOrigin> originFilters,
            TimeRangeFilter timeFilter) {
        BucketData<T> b = computeBucketLocked(aggTypes, originFilters, timeFilter);
        return new AggregateRecordsResponse<T>(b.values, b.zones, b.origins);
    }

    private <T> BucketData<T> computeBucketLocked(Set<AggregationType<T>> aggTypes,
            Set<DataOrigin> originFilters, TimeRangeFilter timeFilter) {
        BucketData<T> out = new BucketData<T>();
        for (AggregationType<T> aggType : aggTypes) {
            Object acc = AggregationType.shirazCreateAccumulator();
            Set<DataOrigin> matchedOrigins = new LinkedHashSet<DataOrigin>();
            ZoneOffset zone = null;
            Class<? extends Record> targetClass = aggType.shirazGetRecordClass();
            for (Record r : mRecordsById.values()) {
                if (!targetClass.isInstance(r)) continue;
                if (!originFilters.isEmpty()
                        && !originFilters.contains(r.getMetadata().getDataOrigin())) {
                    continue;
                }
                if (timeFilter != null && !matchesTimeRange(r, timeFilter)) {
                    continue;
                }
                aggType.shirazFeed(r, acc);
                matchedOrigins.add(r.getMetadata().getDataOrigin());
                if (zone == null) {
                    zone = recordZoneOffset(r);
                }
            }
            T value = aggType.shirazFinish(acc);
            if (value != null) {
                out.values.put(aggType, value);
                out.origins.put(aggType, matchedOrigins);
                out.zones.put(aggType, zone != null ? zone : ZoneOffset.UTC);
            }
        }
        return out;
    }

    private static Instant primaryInstant(Record r) {
        if (r instanceof InstantRecord) {
            return ((InstantRecord) r).getTime();
        }
        if (r instanceof IntervalRecord) {
            return ((IntervalRecord) r).getStartTime();
        }
        return Instant.EPOCH;
    }

    private static ZoneOffset recordZoneOffset(Record r) {
        if (r instanceof InstantRecord) {
            return ((InstantRecord) r).getZoneOffset();
        }
        if (r instanceof IntervalRecord) {
            return ((IntervalRecord) r).getStartZoneOffset();
        }
        return ZoneOffset.UTC;
    }

    private static boolean matchesTimeRange(Record r, TimeRangeFilter filter) {
        ZoneOffset offset = recordZoneOffset(r);
        if (offset == null) {
            offset = ZoneOffset.UTC;
        }
        Instant recStart;
        Instant recEnd;
        if (r instanceof InstantRecord) {
            recStart = ((InstantRecord) r).getTime();
            recEnd = recStart;
        } else if (r instanceof IntervalRecord) {
            recStart = ((IntervalRecord) r).getStartTime();
            recEnd = ((IntervalRecord) r).getEndTime();
        } else {
            return true;
        }

        Instant filterStart = null;
        Instant filterEnd = null;
        if (filter instanceof TimeInstantRangeFilter) {
            TimeInstantRangeFilter tif = (TimeInstantRangeFilter) filter;
            filterStart = tif.getStartTime();
            filterEnd = tif.getEndTime();
        } else if (filter instanceof LocalTimeRangeFilter) {
            LocalTimeRangeFilter ltf = (LocalTimeRangeFilter) filter;
            if (ltf.getStartTime() != null) {
                filterStart = ltf.getStartTime().toInstant(offset);
            }
            if (ltf.getEndTime() != null) {
                filterEnd = ltf.getEndTime().toInstant(offset);
            }
        }

        if (r instanceof InstantRecord) {
            if (filterStart != null && recStart.isBefore(filterStart)) return false;
            if (filterEnd != null && !recStart.isBefore(filterEnd)) return false;
            return true;
        } else {
            if (filterStart != null && !recEnd.isAfter(filterStart)) return false;
            if (filterEnd != null && !recStart.isBefore(filterEnd)) return false;
            return true;
        }
    }
}
