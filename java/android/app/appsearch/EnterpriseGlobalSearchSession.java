// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class EnterpriseGlobalSearchSession {
    EnterpriseGlobalSearchSession() {
    }

    public SearchResults search(String queryExpression, SearchSpec searchSpec) {
        Objects.requireNonNull(queryExpression);
        Objects.requireNonNull(searchSpec);
        return new SearchResults(
                Collections.<SearchResult>emptyList(), searchSpec.getResultCountPerPage());
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
        AppSearchBatchResult.Builder<String, GenericDocument> b =
                new AppSearchBatchResult.Builder<String, GenericDocument>();
        for (String id : request.getIds()) {
            b.setFailure(
                    id,
                    AppSearchResult.RESULT_NOT_FOUND,
                    "Enterprise profile unavailable");
        }
        final AppSearchBatchResult<String, GenericDocument> res = b.build();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onResult(res);
            }
        });
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
        final AppSearchResult<GetSchemaResponse> res =
                AppSearchResult.newSuccessfulResult(new GetSchemaResponse.Builder().build());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(res);
            }
        });
    }
}
