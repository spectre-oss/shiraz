// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.os.OutcomeReceiver;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Test manager for ad selection remote info overrides. */
public class TestAdSelectionManager {
    TestAdSelectionManager() {
    }

    public void overrideAdSelectionConfigRemoteInfo(
            AddAdSelectionOverrideRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void removeAdSelectionConfigRemoteInfoOverride(
            RemoveAdSelectionOverrideRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void resetAllAdSelectionConfigRemoteOverrides(
            Executor executor, OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void overrideAdSelectionFromOutcomesConfigRemoteInfo(
            AddAdSelectionFromOutcomesOverrideRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
            RemoveAdSelectionFromOutcomesOverrideRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
            Executor executor, OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    static <T> void failDisabled(
            Executor executor, final OutcomeReceiver<T, Exception> receiver) {
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        receiver.onError(
                                new IllegalStateException("AdServices is disabled on this host"));
                    }
                });
    }
}
