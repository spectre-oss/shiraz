// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.app.appsearch.exceptions.AppSearchException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PutDocumentsRequest {
    private final List<GenericDocument> mDocuments;
    private final List<GenericDocument> mTakenActions;

    private PutDocumentsRequest(
            List<GenericDocument> documents, List<GenericDocument> takenActions) {
        mDocuments = Collections.unmodifiableList(new ArrayList<GenericDocument>(documents));
        mTakenActions = Collections.unmodifiableList(new ArrayList<GenericDocument>(takenActions));
    }

    public List<GenericDocument> getGenericDocuments() {
        return mDocuments;
    }

    public List<GenericDocument> getTakenActionGenericDocuments() {
        return mTakenActions;
    }

    public static final class Builder {
        private final List<GenericDocument> mDocuments = new ArrayList<GenericDocument>();
        private final List<GenericDocument> mTakenActions = new ArrayList<GenericDocument>();

        public Builder() {
        }

        public Builder addGenericDocuments(GenericDocument... documents) {
            Objects.requireNonNull(documents);
            return addGenericDocuments(Arrays.asList(documents));
        }

        public Builder addGenericDocuments(Collection<? extends GenericDocument> documents) {
            Objects.requireNonNull(documents);
            for (GenericDocument doc : documents) {
                Objects.requireNonNull(doc);
                mDocuments.add(doc);
            }
            return this;
        }

        public Builder addTakenActionGenericDocuments(GenericDocument... takenActions)
                throws AppSearchException {
            Objects.requireNonNull(takenActions);
            return addTakenActionGenericDocuments(Arrays.asList(takenActions));
        }

        public Builder addTakenActionGenericDocuments(
                Collection<? extends GenericDocument> takenActions) throws AppSearchException {
            Objects.requireNonNull(takenActions);
            for (GenericDocument doc : takenActions) {
                Objects.requireNonNull(doc);
                mTakenActions.add(doc);
            }
            return this;
        }

        public PutDocumentsRequest build() {
            return new PutDocumentsRequest(mDocuments, mTakenActions);
        }
    }
}
