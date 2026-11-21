// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.app.appsearch.exceptions.AppSearchException;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.ObserverSpec;
import java.io.Closeable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class GlobalSearchSession implements Closeable {
    private boolean mClosed;

    GlobalSearchSession() {
    }

    public void getByDocumentId(
            String packageName,
            String databaseName,
            GetByDocumentIdRequest request,
            Executor executor,
            final BatchResultCallback<String, GenericDocument> callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(databaseName);
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        AppSearchSession.DatabaseState db =
                AppSearchSession.getOrCreateDatabase(packageName, databaseName);
        final AppSearchBatchResult<String, GenericDocument> res =
                AppSearchSession.getByIdFromDatabase(db, mClosed, request);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(res);
            }
        });
    }

    public SearchResults search(String queryExpression, SearchSpec searchSpec) {
        Objects.requireNonNull(queryExpression);
        Objects.requireNonNull(searchSpec);
        List<SearchResult> results = mClosed
                ? Collections.<SearchResult>emptyList()
                : AppSearchSession.executeSearch(
                        AppSearchSession.snapshotDatabases(), queryExpression, searchSpec);
        return new SearchResults(results, searchSpec.getResultCountPerPage());
    }

    public void getSchema(
            String packageName,
            String databaseName,
            Executor executor,
            final Consumer<AppSearchResult<GetSchemaResponse>> callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(databaseName);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        AppSearchSession.DatabaseState db =
                AppSearchSession.getOrCreateDatabase(packageName, databaseName);
        final AppSearchResult<GetSchemaResponse> res =
                AppSearchSession.buildSchemaResponse(db, mClosed);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    public void reportSystemUsage(
            ReportSystemUsageRequest request,
            Executor executor,
            final Consumer<AppSearchResult<Void>> callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        AppSearchSession.DatabaseState db = AppSearchSession.getOrCreateDatabase(
                request.getPackageName(), request.getDatabaseName());
        final AppSearchResult<Void> res = AppSearchSession.applyUsage(
                db,
                mClosed,
                request.getNamespace(),
                request.getDocumentId(),
                request.getUsageTimestampMillis());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }

    public void registerObserverCallback(
            String targetPackageName,
            ObserverSpec spec,
            Executor executor,
            ObserverCallback observer) throws AppSearchException {
        Objects.requireNonNull(targetPackageName);
        Objects.requireNonNull(spec);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(observer);
        synchronized (AppSearchSession.class) {
            if (mClosed) {
                throw new AppSearchException(
                        AppSearchResult.RESULT_INTERNAL_ERROR, "Session is closed");
            }
            AppSearchSession.OBSERVERS.add(
                    new AppSearchSession.ObserverRegistration(
                            targetPackageName, spec, executor, observer));
        }
    }

    public void unregisterObserverCallback(
            String targetPackageName, ObserverCallback observer) throws AppSearchException {
        Objects.requireNonNull(targetPackageName);
        Objects.requireNonNull(observer);
        synchronized (AppSearchSession.class) {
            Iterator<AppSearchSession.ObserverRegistration> it =
                    AppSearchSession.OBSERVERS.iterator();
            while (it.hasNext()) {
                AppSearchSession.ObserverRegistration reg = it.next();
                if (reg.targetPackage.equals(targetPackageName) && reg.callback == observer) {
                    it.remove();
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        mClosed = true;
    }
}
