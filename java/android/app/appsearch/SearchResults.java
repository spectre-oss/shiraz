// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class SearchResults implements Closeable {
    private final List<SearchResult> mAllResults;
    private final int mPageSize;
    private int mCursor;
    private boolean mClosed;

    SearchResults(List<SearchResult> allResults, int pageSize) {
        mAllResults = allResults != null
                ? new ArrayList<SearchResult>(allResults)
                : Collections.<SearchResult>emptyList();
        mPageSize = pageSize <= 0 ? mAllResults.size() : pageSize;
    }

    public synchronized void getNextPage(
            Executor executor,
            final Consumer<AppSearchResult<List<SearchResult>>> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchResult<List<SearchResult>> result;
        if (mClosed) {
            result = AppSearchResult.newFailedResult(
                    AppSearchResult.RESULT_INTERNAL_ERROR, "SearchResults has already been closed");
        } else if (mCursor >= mAllResults.size() || mPageSize == 0) {
            result = AppSearchResult.newSuccessfulResult(Collections.<SearchResult>emptyList());
        } else {
            int end = Math.min(mAllResults.size(), mCursor + mPageSize);
            List<SearchResult> page = new ArrayList<SearchResult>(mAllResults.subList(mCursor, end));
            mCursor = end;
            result = AppSearchResult.newSuccessfulResult(page);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(result);
            }
        });
    }

    @Override
    public synchronized void close() {
        mClosed = true;
    }
}
