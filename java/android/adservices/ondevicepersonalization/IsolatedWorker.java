// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.OutcomeReceiver;

/** Per-request handler created by {@link IsolatedService#onRequest}. */
public interface IsolatedWorker {
    default void onExecute(
            ExecuteInput input,
            OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
        if (receiver != null) {
            receiver.onResult(new ExecuteOutput.Builder().build());
        }
    }

    default void onDownloadCompleted(
            DownloadCompletedInput input,
            OutcomeReceiver<DownloadCompletedOutput, IsolatedServiceException> receiver) {
        if (receiver != null) {
            receiver.onResult(new DownloadCompletedOutput.Builder().build());
        }
    }

    default void onRender(
            RenderInput input,
            OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
        if (receiver != null) {
            receiver.onResult(new RenderOutput.Builder().build());
        }
    }

    default void onEvent(
            EventInput input,
            OutcomeReceiver<EventOutput, IsolatedServiceException> receiver) {
        if (receiver != null) {
            receiver.onResult(new EventOutput.Builder().build());
        }
    }

    default void onTrainingExamples(
            TrainingExamplesInput input,
            OutcomeReceiver<TrainingExamplesOutput, IsolatedServiceException> receiver) {
        if (receiver != null) {
            receiver.onResult(new TrainingExamplesOutput.Builder().build());
        }
    }

    default void onWebTrigger(
            WebTriggerInput input,
            OutcomeReceiver<WebTriggerOutput, IsolatedServiceException> receiver) {
        if (receiver != null) {
            receiver.onResult(new WebTriggerOutput.Builder().build());
        }
    }
}
