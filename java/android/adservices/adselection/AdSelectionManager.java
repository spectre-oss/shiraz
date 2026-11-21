// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Protected Audience ad selection manager; reports AdServices disabled on the host. */
public class AdSelectionManager {
    private final Context mContext;
    private final TestAdSelectionManager mTestManager = new TestAdSelectionManager();

    AdSelectionManager(Context context) {
        this.mContext = context;
    }

    public static AdSelectionManager get(Context context) {
        Objects.requireNonNull(context, "context");
        return new AdSelectionManager(context);
    }

    public TestAdSelectionManager getTestAdSelectionManager() {
        return mTestManager;
    }

    public void getAdSelectionData(
            GetAdSelectionDataRequest parameters,
            Executor executor,
            OutcomeReceiver<GetAdSelectionDataOutcome, Exception> receiver) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        TestAdSelectionManager.failDisabled(executor, receiver);
    }

    public void persistAdSelectionResult(
            PersistAdSelectionResultRequest parameters,
            Executor executor,
            OutcomeReceiver<AdSelectionOutcome, Exception> receiver) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        TestAdSelectionManager.failDisabled(executor, receiver);
    }

    public void selectAds(
            AdSelectionConfig adSelectionConfig,
            Executor executor,
            OutcomeReceiver<AdSelectionOutcome, Exception> receiver) {
        Objects.requireNonNull(adSelectionConfig, "adSelectionConfig");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        TestAdSelectionManager.failDisabled(executor, receiver);
    }

    public void selectAds(
            AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            Executor executor,
            OutcomeReceiver<AdSelectionOutcome, Exception> receiver) {
        Objects.requireNonNull(
                adSelectionFromOutcomesConfig, "adSelectionFromOutcomesConfig");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        TestAdSelectionManager.failDisabled(executor, receiver);
    }

    public void reportImpression(
            ReportImpressionRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        TestAdSelectionManager.failDisabled(executor, receiver);
    }

    public void reportEvent(
            ReportEventRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        TestAdSelectionManager.failDisabled(executor, receiver);
    }

    public void setAppInstallAdvertisers(
            SetAppInstallAdvertisersRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        TestAdSelectionManager.failDisabled(executor, receiver);
    }

    public void updateAdCounterHistogram(
            UpdateAdCounterHistogramRequest updateAdCounterHistogramRequest,
            Executor executor,
            OutcomeReceiver<Object, Exception> outcomeReceiver) {
        Objects.requireNonNull(
                updateAdCounterHistogramRequest, "updateAdCounterHistogramRequest");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(outcomeReceiver, "outcomeReceiver");
        TestAdSelectionManager.failDisabled(executor, outcomeReceiver);
    }
}
