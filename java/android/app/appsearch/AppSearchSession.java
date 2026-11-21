// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.observer.SchemaChangeInfo;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class AppSearchSession implements Closeable {
    static final Map<String, DatabaseState> DATABASES = new LinkedHashMap<String, DatabaseState>();
    static final List<ObserverRegistration> OBSERVERS = new ArrayList<ObserverRegistration>();

    static final class ObserverRegistration {
        final String targetPackage;
        final ObserverSpec spec;
        final Executor executor;
        final ObserverCallback callback;

        ObserverRegistration(
                String targetPackage,
                ObserverSpec spec,
                Executor executor,
                ObserverCallback callback) {
            this.targetPackage = targetPackage;
            this.spec = spec;
            this.executor = executor;
            this.callback = callback;
        }
    }

    static final class DocEntry {
        GenericDocument doc;
        int usageCount;
        long lastUsedTimestamp;

        DocEntry(GenericDocument doc) {
            this.doc = doc;
        }

        boolean isExpired(long nowMillis) {
            long ttl = doc.getTtlMillis();
            return ttl > 0 && (doc.getCreationTimestampMillis() + ttl) < nowMillis;
        }
    }

    static final class DatabaseState {
        final String packageName;
        final String databaseName;
        int version = 0;
        final Map<String, AppSearchSchema> schemas = new LinkedHashMap<String, AppSearchSchema>();
        SetSchemaRequest lastSchemaRequest;
        final Map<String, Map<String, DocEntry>> docsByNamespace =
                new LinkedHashMap<String, Map<String, DocEntry>>();

        DatabaseState(String packageName, String databaseName) {
            this.packageName = packageName;
            this.databaseName = databaseName;
        }
    }

    static synchronized DatabaseState getOrCreateDatabase(String packageName, String databaseName) {
        String key = packageName + "$" + databaseName;
        DatabaseState db = DATABASES.get(key);
        if (db == null) {
            db = new DatabaseState(packageName, databaseName);
            DATABASES.put(key, db);
        }
        return db;
    }

    static synchronized List<DatabaseState> snapshotDatabases() {
        return new ArrayList<DatabaseState>(DATABASES.values());
    }

    static synchronized void notifySchemaChanged(
            String pkg, String dbName, Set<String> changedSchemas) {
        if (changedSchemas.isEmpty()) return;
        final SchemaChangeInfo info = new SchemaChangeInfo(pkg, dbName, changedSchemas);
        for (final ObserverRegistration reg : OBSERVERS) {
            if (!reg.targetPackage.equals(pkg)) continue;
            Set<String> filter = reg.spec.getFilterSchemas();
            if (!filter.isEmpty() && Collections.disjoint(filter, changedSchemas)) continue;
            reg.executor.execute(new Runnable() {
                @Override
                public void run() {
                    reg.callback.onSchemaChanged(info);
                }
            });
        }
    }

    static synchronized void notifyDocChanged(
            String pkg, String dbName, String namespace, String schemaName, Set<String> docIds) {
        if (docIds.isEmpty()) return;
        final DocumentChangeInfo info =
                new DocumentChangeInfo(pkg, dbName, namespace, schemaName, docIds);
        for (final ObserverRegistration reg : OBSERVERS) {
            if (!reg.targetPackage.equals(pkg)) continue;
            Set<String> filter = reg.spec.getFilterSchemas();
            if (!filter.isEmpty() && !filter.contains(schemaName)) continue;
            reg.executor.execute(new Runnable() {
                @Override
                public void run() {
                    reg.callback.onDocumentChanged(info);
                }
            });
        }
    }

    private final DatabaseState mDb;
    private boolean mClosed;

    AppSearchSession(String packageName, String databaseName) {
        mDb = getOrCreateDatabase(packageName, databaseName);
    }

    public void setSchema(
            final SetSchemaRequest request,
            Executor workExecutor,
            Executor callbackExecutor,
            final Consumer<AppSearchResult<SetSchemaResponse>> callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(workExecutor);
        Objects.requireNonNull(callbackExecutor);
        Objects.requireNonNull(callback);
        final AppSearchResult<SetSchemaResponse> res;
        Set<String> changedSchemas = new LinkedHashSet<String>();
        synchronized (AppSearchSession.class) {
            if (mClosed) {
                res = AppSearchResult.newFailedResult(
                        AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
            } else {
                SetSchemaResponse.Builder resp = new SetSchemaResponse.Builder();
                int oldVersion = mDb.version;
                int newVersion = request.getVersion();
                Map<String, AppSearchSchema> incoming =
                        new LinkedHashMap<String, AppSearchSchema>();
                for (AppSearchSchema s : request.getSchemas()) {
                    incoming.put(s.getSchemaType(), s);
                }
                Set<String> removed = new LinkedHashSet<String>(mDb.schemas.keySet());
                removed.removeAll(incoming.keySet());
                if (!removed.isEmpty() && !request.isForceOverride()
                        && !request.getMigrators().keySet().containsAll(removed)) {
                    resp.addIncompatibleTypes(removed);
                    res = AppSearchResult.newFailedResult(
                            AppSearchResult.RESULT_INVALID_SCHEMA,
                            "Incompatible schema removal without forceOverride: " + removed);
                } else {
                    for (String del : removed) {
                        resp.addDeletedType(del);
                        changedSchemas.add(del);
                        for (Map<String, DocEntry> nsDocs : mDb.docsByNamespace.values()) {
                            List<String> toDelete = new ArrayList<String>();
                            for (Map.Entry<String, DocEntry> e : nsDocs.entrySet()) {
                                if (del.equals(e.getValue().doc.getSchemaType())) {
                                    toDelete.add(e.getKey());
                                }
                            }
                            for (String id : toDelete) nsDocs.remove(id);
                        }
                    }
                    for (Map.Entry<String, Migrator> me : request.getMigrators().entrySet()) {
                        String schemaType = me.getKey();
                        Migrator migrator = me.getValue();
                        if (oldVersion > 0 && oldVersion != newVersion
                                && migrator.shouldMigrate(oldVersion, newVersion)) {
                            resp.addMigratedType(schemaType);
                            for (Map<String, DocEntry> nsDocs : mDb.docsByNamespace.values()) {
                                List<DocEntry> entries =
                                        new ArrayList<DocEntry>(nsDocs.values());
                                for (DocEntry entry : entries) {
                                    if (!schemaType.equals(entry.doc.getSchemaType())) continue;
                                    try {
                                        GenericDocument migrated = newVersion > oldVersion
                                                ? migrator.onUpgrade(
                                                        oldVersion, newVersion, entry.doc)
                                                : migrator.onDowngrade(
                                                        oldVersion, newVersion, entry.doc);
                                        nsDocs.remove(entry.doc.getId());
                                        nsDocs.put(migrated.getId(), new DocEntry(migrated));
                                    } catch (RuntimeException ex) {
                                        resp.addMigrationFailure(
                                                new SetSchemaResponse.MigrationFailure(
                                                        entry.doc.getNamespace(),
                                                        entry.doc.getId(),
                                                        schemaType,
                                                        AppSearchResult.newFailedResult(
                                                                AppSearchResult.RESULT_INTERNAL_ERROR,
                                                                ex.getMessage())));
                                    }
                                }
                            }
                        }
                    }
                    changedSchemas.addAll(incoming.keySet());
                    mDb.schemas.clear();
                    mDb.schemas.putAll(incoming);
                    mDb.version = newVersion;
                    mDb.lastSchemaRequest = request;
                    res = AppSearchResult.newSuccessfulResult(resp.build());
                }
            }
        }
        if (res.isSuccess()) {
            notifySchemaChanged(mDb.packageName, mDb.databaseName, changedSchemas);
        }
        callbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    public void getSchema(
            Executor executor,
            final Consumer<AppSearchResult<GetSchemaResponse>> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchResult<GetSchemaResponse> res = buildSchemaResponse(mDb, mClosed);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    static synchronized AppSearchResult<GetSchemaResponse> buildSchemaResponse(
            DatabaseState db, boolean closed) {
        if (closed) {
            return AppSearchResult.newFailedResult(
                    AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
        }
        GetSchemaResponse.Builder b = new GetSchemaResponse.Builder().setVersion(db.version);
        for (AppSearchSchema s : db.schemas.values()) {
            b.addSchema(s);
        }
        if (db.lastSchemaRequest != null) {
            for (String h : db.lastSchemaRequest.getSchemasNotDisplayedBySystem()) {
                b.addSchemaTypeNotDisplayedBySystem(h);
            }
            for (Map.Entry<String, Set<PackageIdentifier>> e :
                    db.lastSchemaRequest.getSchemasVisibleToPackages().entrySet()) {
                b.setSchemaTypeVisibleToPackages(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, Set<Set<Integer>>> e :
                    db.lastSchemaRequest.getRequiredPermissionsForSchemaTypeVisibility().entrySet()) {
                b.setRequiredPermissionsForSchemaTypeVisibility(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, PackageIdentifier> e :
                    db.lastSchemaRequest.getPubliclyVisibleSchemas().entrySet()) {
                b.setPubliclyVisibleSchema(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, Set<SchemaVisibilityConfig>> e :
                    db.lastSchemaRequest.getSchemasVisibleToConfigs().entrySet()) {
                b.setSchemaTypeVisibleToConfigs(e.getKey(), e.getValue());
            }
        }
        return AppSearchResult.newSuccessfulResult(b.build());
    }

    public void getNamespaces(
            Executor executor,
            final Consumer<AppSearchResult<Set<String>>> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchResult<Set<String>> res;
        synchronized (AppSearchSession.class) {
            if (mClosed) {
                res = AppSearchResult.newFailedResult(
                        AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
            } else {
                Set<String> namespaces = new LinkedHashSet<String>();
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Map<String, DocEntry>> e :
                        mDb.docsByNamespace.entrySet()) {
                    for (DocEntry de : e.getValue().values()) {
                        if (!de.isExpired(now)) {
                            namespaces.add(e.getKey());
                            break;
                        }
                    }
                }
                res = AppSearchResult.newSuccessfulResult(namespaces);
            }
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    public void put(
            PutDocumentsRequest request,
            Executor executor,
            final BatchResultCallback<String, Void> callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchBatchResult.Builder<String, Void> batch =
                new AppSearchBatchResult.Builder<String, Void>();
        List<GenericDocument> all = new ArrayList<GenericDocument>();
        all.addAll(request.getGenericDocuments());
        all.addAll(request.getTakenActionGenericDocuments());
        synchronized (AppSearchSession.class) {
            for (GenericDocument doc : all) {
                if (mClosed) {
                    batch.setFailure(
                            doc.getId(),
                            AppSearchResult.RESULT_INTERNAL_ERROR,
                            "Session is closed");
                } else if (!mDb.schemas.containsKey(doc.getSchemaType())) {
                    batch.setFailure(
                            doc.getId(),
                            AppSearchResult.RESULT_NOT_FOUND,
                            "Schema type not found: " + doc.getSchemaType());
                } else {
                    Map<String, DocEntry> nsMap = mDb.docsByNamespace.get(doc.getNamespace());
                    if (nsMap == null) {
                        nsMap = new LinkedHashMap<String, DocEntry>();
                        mDb.docsByNamespace.put(doc.getNamespace(), nsMap);
                    }
                    nsMap.put(doc.getId(), new DocEntry(doc));
                    batch.setSuccess(doc.getId(), null);
                    notifyDocChanged(
                            mDb.packageName,
                            mDb.databaseName,
                            doc.getNamespace(),
                            doc.getSchemaType(),
                            Collections.singleton(doc.getId()));
                }
            }
        }
        final AppSearchBatchResult<String, Void> built = batch.build();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(built);
            }
        });
    }

    public void getByDocumentId(
            GetByDocumentIdRequest request,
            Executor executor,
            final BatchResultCallback<String, GenericDocument> callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchBatchResult<String, GenericDocument> built =
                getByIdFromDatabase(mDb, mClosed, request);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(built);
            }
        });
    }

    static synchronized AppSearchBatchResult<String, GenericDocument> getByIdFromDatabase(
            DatabaseState db, boolean closed, GetByDocumentIdRequest request) {
        AppSearchBatchResult.Builder<String, GenericDocument> batch =
                new AppSearchBatchResult.Builder<String, GenericDocument>();
        long now = System.currentTimeMillis();
        Map<String, DocEntry> nsMap = db.docsByNamespace.get(request.getNamespace());
        for (String id : request.getIds()) {
            if (closed) {
                batch.setFailure(
                        id, AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
                continue;
            }
            DocEntry de = nsMap != null ? nsMap.get(id) : null;
            if (de == null || de.isExpired(now)) {
                batch.setFailure(
                        id,
                        AppSearchResult.RESULT_NOT_FOUND,
                        "Document (" + request.getNamespace() + ", " + id + ") not found");
            } else {
                batch.setSuccess(id, de.doc);
            }
        }
        return batch.build();
    }

    public SearchResults search(String queryExpression, SearchSpec searchSpec) {
        Objects.requireNonNull(queryExpression);
        Objects.requireNonNull(searchSpec);
        List<SearchResult> results = mClosed
                ? Collections.<SearchResult>emptyList()
                : executeSearch(Collections.singletonList(mDb), queryExpression, searchSpec);
        return new SearchResults(results, searchSpec.getResultCountPerPage());
    }

    static synchronized List<SearchResult> executeSearch(
            List<DatabaseState> dbs, String queryExpression, final SearchSpec spec) {
        String trimmed = queryExpression.trim();
        String[] rawTokens = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        long now = System.currentTimeMillis();
        List<ScoredResult> matched = new ArrayList<ScoredResult>();
        for (DatabaseState db : dbs) {
            if (!spec.getFilterPackageNames().isEmpty()
                    && !spec.getFilterPackageNames().contains(db.packageName)) {
                continue;
            }
            for (Map.Entry<String, Map<String, DocEntry>> nsEntry :
                    db.docsByNamespace.entrySet()) {
                if (!spec.getFilterNamespaces().isEmpty()
                        && !spec.getFilterNamespaces().contains(nsEntry.getKey())) {
                    continue;
                }
                for (DocEntry de : nsEntry.getValue().values()) {
                    if (de.isExpired(now)) continue;
                    GenericDocument doc = de.doc;
                    if (!spec.getFilterSchemas().isEmpty()
                            && !spec.getFilterSchemas().contains(doc.getSchemaType())) {
                        continue;
                    }
                    List<SearchResult.MatchInfo> matches =
                            matchDocument(doc, rawTokens, spec.getTermMatch());
                    if (matches == null) continue;
                    double signal = computeSignal(de, matches.size(), spec.getRankingStrategy());
                    SearchResult.Builder rb =
                            new SearchResult.Builder(db.packageName, db.databaseName)
                                    .setGenericDocument(doc)
                                    .setRankingSignal(signal);
                    for (SearchResult.MatchInfo mi : matches) {
                        rb.addMatchInfo(mi);
                    }
                    matched.add(new ScoredResult(rb.build(), signal));
                }
            }
        }
        Collections.sort(matched, new Comparator<ScoredResult>() {
            @Override
            public int compare(ScoredResult a, ScoredResult b) {
                int cmp = Double.compare(a.signal, b.signal);
                return spec.getOrder() == SearchSpec.ORDER_ASCENDING ? cmp : -cmp;
            }
        });
        List<SearchResult> out = new ArrayList<SearchResult>(matched.size());
        for (ScoredResult sr : matched) {
            out.add(sr.result);
        }
        return out;
    }

    private static final class ScoredResult {
        final SearchResult result;
        final double signal;

        ScoredResult(SearchResult result, double signal) {
            this.result = result;
            this.signal = signal;
        }
    }

    private static double computeSignal(DocEntry de, int matchCount, int strategy) {
        switch (strategy) {
            case SearchSpec.RANKING_STRATEGY_DOCUMENT_SCORE:
                return de.doc.getScore();
            case SearchSpec.RANKING_STRATEGY_CREATION_TIMESTAMP:
                return de.doc.getCreationTimestampMillis();
            case SearchSpec.RANKING_STRATEGY_USAGE_COUNT:
            case SearchSpec.RANKING_STRATEGY_SYSTEM_USAGE_COUNT:
                return de.usageCount;
            case SearchSpec.RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP:
            case SearchSpec.RANKING_STRATEGY_SYSTEM_USAGE_LAST_USED_TIMESTAMP:
                return de.lastUsedTimestamp;
            case SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE:
                return matchCount;
            default:
                return de.doc.getScore();
        }
    }

    private static List<SearchResult.MatchInfo> matchDocument(
            GenericDocument doc, String[] rawTokens, int termMatchType) {
        List<SearchResult.MatchInfo> infos = new ArrayList<SearchResult.MatchInfo>();
        if (rawTokens.length == 0) {
            return infos;
        }
        boolean prefix = (termMatchType == SearchSpec.TERM_MATCH_PREFIX);
        for (String raw : rawTokens) {
            if ("AND".equals(raw) || "OR".equals(raw)) continue;
            String targetProp = null;
            String term = raw;
            int colon = raw.indexOf(':');
            if (colon > 0 && colon < raw.length() - 1) {
                targetProp = raw.substring(0, colon);
                term = raw.substring(colon + 1);
            }
            String lowerTerm = term.toLowerCase(Locale.US);
            SearchResult.MatchInfo tokenMatch =
                    findMatchInDoc(doc, targetProp, lowerTerm, prefix);
            if (tokenMatch == null) {
                return null;
            }
            infos.add(tokenMatch);
        }
        return infos;
    }

    private static SearchResult.MatchInfo findMatchInDoc(
            GenericDocument doc, String targetProp, String lowerTerm, boolean prefix) {
        Set<String> props = targetProp != null
                ? Collections.singleton(targetProp)
                : doc.getPropertyNames();
        for (String prop : props) {
            String[] values = doc.getPropertyStringArray(prop);
            if (values == null) continue;
            for (String text : values) {
                if (text == null) continue;
                SearchResult.MatchRange range = findWordMatch(text, lowerTerm, prefix);
                if (range != null) {
                    SearchResult.MatchRange subRange = new SearchResult.MatchRange(
                            range.getStart(),
                            Math.min(range.getEnd(), range.getStart() + lowerTerm.length()));
                    return new SearchResult.MatchInfo.Builder(prop)
                            .setExactMatchRange(range)
                            .setSubmatchRange(subRange)
                            .setSnippetRange(new SearchResult.MatchRange(0, text.length()))
                            .build();
                }
            }
        }
        return null;
    }

    private static SearchResult.MatchRange findWordMatch(
            String text, String lowerTerm, boolean prefix) {
        String lowerText = text.toLowerCase(Locale.US);
        int n = lowerText.length();
        int i = 0;
        while (i < n) {
            while (i < n && !Character.isLetterOrDigit(lowerText.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && Character.isLetterOrDigit(lowerText.charAt(i))) i++;
            String word = lowerText.substring(start, i);
            if (prefix ? word.startsWith(lowerTerm) : word.equals(lowerTerm)) {
                return new SearchResult.MatchRange(start, i);
            }
        }
        return null;
    }

    public void searchSuggestion(
            String suggestionQueryExpression,
            SearchSuggestionSpec searchSuggestionSpec,
            Executor executor,
            final Consumer<AppSearchResult<List<SearchSuggestionResult>>> callback) {
        Objects.requireNonNull(suggestionQueryExpression);
        Objects.requireNonNull(searchSuggestionSpec);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchResult<List<SearchSuggestionResult>> res;
        String prefix = suggestionQueryExpression.trim().toLowerCase(Locale.US);
        if (prefix.isEmpty()) {
            res = AppSearchResult.newFailedResult(
                    AppSearchResult.RESULT_INVALID_ARGUMENT,
                    "suggestionQueryExpression cannot be empty");
        } else {
            synchronized (AppSearchSession.class) {
                Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Map<String, DocEntry>> nsEntry :
                        mDb.docsByNamespace.entrySet()) {
                    if (!searchSuggestionSpec.getFilterNamespaces().isEmpty()
                            && !searchSuggestionSpec.getFilterNamespaces()
                                    .contains(nsEntry.getKey())) {
                        continue;
                    }
                    for (DocEntry de : nsEntry.getValue().values()) {
                        if (de.isExpired(now)) continue;
                        GenericDocument doc = de.doc;
                        if (!searchSuggestionSpec.getFilterSchemas().isEmpty()
                                && !searchSuggestionSpec.getFilterSchemas()
                                        .contains(doc.getSchemaType())) {
                            continue;
                        }
                        for (String prop : doc.getPropertyNames()) {
                            String[] vals = doc.getPropertyStringArray(prop);
                            if (vals == null) continue;
                            for (String text : vals) {
                                if (text == null) continue;
                                String lower = text.toLowerCase(Locale.US);
                                String[] words = lower.split("\\W+");
                                for (String w : words) {
                                    if (!w.isEmpty() && w.startsWith(prefix)) {
                                        Integer prev = counts.get(w);
                                        counts.put(w, Integer.valueOf(prev == null ? 1 : prev + 1));
                                    }
                                }
                            }
                        }
                    }
                }
                List<Map.Entry<String, Integer>> entries =
                        new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
                Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
                    @Override
                    public int compare(
                            Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                        return b.getValue().compareTo(a.getValue());
                    }
                });
                int limit = Math.min(entries.size(), searchSuggestionSpec.getMaximumResultCount());
                List<SearchSuggestionResult> suggestions =
                        new ArrayList<SearchSuggestionResult>(limit);
                for (int i = 0; i < limit; i++) {
                    suggestions.add(
                            new SearchSuggestionResult.Builder()
                                    .setSuggestedResult(entries.get(i).getKey())
                                    .build());
                }
                res = AppSearchResult.newSuccessfulResult(suggestions);
            }
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    public void reportUsage(
            ReportUsageRequest request,
            Executor executor,
            final Consumer<AppSearchResult<Void>> callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchResult<Void> res = applyUsage(
                mDb, mClosed, request.getNamespace(), request.getDocumentId(),
                request.getUsageTimestampMillis());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    static synchronized AppSearchResult<Void> applyUsage(
            DatabaseState db, boolean closed, String namespace, String docId, long timestamp) {
        if (closed) {
            return AppSearchResult.newFailedResult(
                    AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
        }
        Map<String, DocEntry> nsMap = db.docsByNamespace.get(namespace);
        DocEntry de = nsMap != null ? nsMap.get(docId) : null;
        if (de == null) {
            return AppSearchResult.newFailedResult(
                    AppSearchResult.RESULT_NOT_FOUND, "Document not found: " + docId);
        }
        de.usageCount++;
        de.lastUsedTimestamp = Math.max(de.lastUsedTimestamp, timestamp);
        return AppSearchResult.newSuccessfulResult(null);
    }

    public void remove(
            RemoveByDocumentIdRequest request,
            Executor executor,
            final BatchResultCallback<String, Void> callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        AppSearchBatchResult.Builder<String, Void> batch =
                new AppSearchBatchResult.Builder<String, Void>();
        synchronized (AppSearchSession.class) {
            Map<String, DocEntry> nsMap = mDb.docsByNamespace.get(request.getNamespace());
            for (String id : request.getIds()) {
                if (mClosed) {
                    batch.setFailure(
                            id, AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
                    continue;
                }
                DocEntry removed = nsMap != null ? nsMap.remove(id) : null;
                if (removed == null) {
                    batch.setFailure(
                            id, AppSearchResult.RESULT_NOT_FOUND, "Document not found: " + id);
                } else {
                    batch.setSuccess(id, null);
                    notifyDocChanged(
                            mDb.packageName,
                            mDb.databaseName,
                            request.getNamespace(),
                            removed.doc.getSchemaType(),
                            Collections.singleton(id));
                }
            }
        }
        final AppSearchBatchResult<String, Void> built = batch.build();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(built);
            }
        });
    }

    public void remove(
            String queryExpression,
            SearchSpec searchSpec,
            Executor executor,
            final Consumer<AppSearchResult<Void>> callback) {
        Objects.requireNonNull(queryExpression);
        Objects.requireNonNull(searchSpec);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchResult<Void> res;
        synchronized (AppSearchSession.class) {
            if (mClosed) {
                res = AppSearchResult.newFailedResult(
                        AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
            } else {
                List<SearchResult> matches = executeSearch(
                        Collections.singletonList(mDb), queryExpression, searchSpec);
                for (SearchResult sr : matches) {
                    GenericDocument doc = sr.getGenericDocument();
                    Map<String, DocEntry> nsMap = mDb.docsByNamespace.get(doc.getNamespace());
                    if (nsMap != null && nsMap.remove(doc.getId()) != null) {
                        notifyDocChanged(
                                mDb.packageName,
                                mDb.databaseName,
                                doc.getNamespace(),
                                doc.getSchemaType(),
                                Collections.singleton(doc.getId()));
                    }
                }
                res = AppSearchResult.newSuccessfulResult(null);
            }
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    public void getStorageInfo(
            Executor executor,
            final Consumer<AppSearchResult<StorageInfo>> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchResult<StorageInfo> res;
        synchronized (AppSearchSession.class) {
            if (mClosed) {
                res = AppSearchResult.newFailedResult(
                        AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
            } else {
                int aliveDocs = 0;
                int aliveNamespaces = 0;
                long sizeBytes = 0L;
                long now = System.currentTimeMillis();
                for (Map<String, DocEntry> nsMap : mDb.docsByNamespace.values()) {
                    boolean nsHasAlive = false;
                    for (DocEntry de : nsMap.values()) {
                        if (!de.isExpired(now)) {
                            aliveDocs++;
                            nsHasAlive = true;
                            sizeBytes += 128L + de.doc.getId().length() * 2L;
                        }
                    }
                    if (nsHasAlive) aliveNamespaces++;
                }
                StorageInfo info = new StorageInfo.Builder()
                        .setAliveDocumentsCount(aliveDocs)
                        .setAliveNamespacesCount(aliveNamespaces)
                        .setSizeBytes(sizeBytes)
                        .build();
                res = AppSearchResult.newSuccessfulResult(info);
            }
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    @Override
    public synchronized void close() {
        mClosed = true;
    }
}
