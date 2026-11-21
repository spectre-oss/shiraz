// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.content.Context;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class AppSearchManager {
    private final String mPackageName;

    public AppSearchManager() {
        this("shiraz.app");
    }

    public AppSearchManager(Context context) {
        this(context != null && context.getPackageName() != null
                ? context.getPackageName()
                : "shiraz.app");
    }

    public AppSearchManager(String packageName) {
        mPackageName = Objects.requireNonNull(packageName);
    }

    public void createSearchSession(
            final SearchContext searchContext,
            Executor executor,
            final Consumer<AppSearchResult<AppSearchSession>> callback) {
        Objects.requireNonNull(searchContext);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final AppSearchSession session =
                new AppSearchSession(mPackageName, searchContext.getDatabaseName());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(AppSearchResult.newSuccessfulResult(session));
            }
        });
    }

    public void createGlobalSearchSession(
            Executor executor,
            final Consumer<AppSearchResult<GlobalSearchSession>> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final GlobalSearchSession session = new GlobalSearchSession();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(AppSearchResult.newSuccessfulResult(session));
            }
        });
    }

    public void createEnterpriseGlobalSearchSession(
            Executor executor,
            final Consumer<AppSearchResult<EnterpriseGlobalSearchSession>> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final EnterpriseGlobalSearchSession session = new EnterpriseGlobalSearchSession();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(AppSearchResult.newSuccessfulResult(session));
            }
        });
    }

    public static final class SearchContext {
        private final String mDatabaseName;

        SearchContext(String databaseName) {
            mDatabaseName = Objects.requireNonNull(databaseName);
        }

        public String getDatabaseName() {
            return mDatabaseName;
        }

        public static final class Builder {
            private final String mDatabaseName;

            public Builder(String databaseName) {
                Objects.requireNonNull(databaseName);
                if (databaseName.contains("/")) {
                    throw new IllegalArgumentException(
                            "Database name cannot contain '/' character");
                }
                mDatabaseName = databaseName;
            }

            public SearchContext build() {
                return new SearchContext(mDatabaseName);
            }
        }
    }
}
