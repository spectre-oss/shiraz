# SPDX-License-Identifier: GPL-3.0-only
.PHONY: pending-intent-service-smoke
ifeq ($(shell uname -s),Darwin)
pending-intent-service-smoke: macos-framework
else
pending-intent-service-smoke: framework
endif
pending-intent-service-smoke:
	uv run --no-project python scripts/java_framework_smoke.py PendingIntentServiceSmoke --marker PENDING_INTENT_SERVICE_DISPATCH_OK

.PHONY: alarm-delivery-smoke
ifeq ($(shell uname -s),Darwin)
alarm-delivery-smoke: macos-framework
else
alarm-delivery-smoke: framework
endif
alarm-delivery-smoke:
	uv run --no-project python scripts/java_framework_smoke.py AlarmDeliverySmoke --marker ALARM_DELIVERY_OK
	uv run --no-project python scripts/java_framework_smoke.py AlarmListenerSmoke --marker ALARM_LISTENER_OK

.PHONY: background-service-smoke
ifeq ($(shell uname -s),Darwin)
background-service-smoke: macos-framework
else
background-service-smoke: framework
endif
background-service-smoke:
	uv run --no-project python scripts/java_framework_smoke.py BackgroundServiceSmoke --marker BACKGROUND_SERVICE_LIFETIME_OK

.PHONY: service-rebind-smoke
ifeq ($(shell uname -s),Darwin)
service-rebind-smoke: macos-framework
else
service-rebind-smoke: framework
endif
service-rebind-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ServiceRebindSmoke --marker SERVICE_REBIND_OK

.PHONY: background-apk-fixture
background-apk-fixture:
	uv run --no-project python scripts/build_macos_fixture.py --fixture background

.PHONY: background-apk-smoke
background-apk-smoke: background-apk-fixture macos-framework
	uv run --no-project python scripts/background_apk_smoke.py

.PHONY: job-execution-smoke
ifeq ($(shell uname -s),Darwin)
job-execution-smoke: macos-framework
else
job-execution-smoke: framework
endif
job-execution-smoke:
	uv run --no-project python scripts/java_framework_smoke.py JobExecutionSmoke --marker JOB_EXECUTION_OK

.PHONY: admitted-job-smoke
.PHONY: foreground-invalidation-smoke
.PHONY: temporary-detach-touch-smoke
.PHONY: text-alignment-smoke
ifeq ($(shell uname -s),Darwin)
text-alignment-smoke: macos-framework
else
text-alignment-smoke: framework
endif
text-alignment-smoke:
	uv run --no-project python scripts/java_framework_smoke.py TextAlignmentSmoke --marker TEXT_ALIGNMENT_OK --framework-res build/framework-res/framework-res.apk

ifeq ($(shell uname -s),Darwin)
temporary-detach-touch-smoke: macos-framework
else
temporary-detach-touch-smoke: framework
endif
temporary-detach-touch-smoke:
	uv run --no-project python scripts/java_framework_smoke.py TemporaryDetachTouchSmoke --marker TEMPORARY_DETACH_TOUCH_OK

ifeq ($(shell uname -s),Darwin)
foreground-invalidation-smoke: macos-framework
else
foreground-invalidation-smoke: framework
endif
foreground-invalidation-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ForegroundInvalidationSmoke --marker FOREGROUND_INVALIDATION_OK

ifeq ($(shell uname -s),Darwin)
admitted-job-smoke: macos-framework
else
admitted-job-smoke: framework
endif
admitted-job-smoke:
	uv run --no-project python scripts/java_framework_smoke.py AdmittedJobSmoke --marker ADMITTED_JOB_OK

.PHONY: job-definition-smoke
ifeq ($(shell uname -s),Darwin)
job-definition-smoke: macos-framework
else
job-definition-smoke: framework
endif
job-definition-smoke:
	uv run --no-project python scripts/java_framework_smoke.py JobDefinitionSmoke --marker JOB_DEFINITION_OK

.PHONY: job-reply-smoke
ifeq ($(shell uname -s),Darwin)
job-reply-smoke: macos-framework
else
job-reply-smoke: framework
endif
job-reply-smoke:
	uv run --no-project python scripts/java_framework_smoke.py JobReplySmoke --marker JOB_REPLY_OK

.PHONY: job-process-art-smoke
ifeq ($(shell uname -s),Darwin)
job-process-art-smoke: macos-framework
else
job-process-art-smoke: framework
endif
job-process-art-smoke: apksig-deps background-apk-fixture
	SHIRAZ_TEST_JOB_PROCESS=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: apksig-deps apk-signature-smoke
apksig-deps:
	uv run --no-project python scripts/bootstrap_apksig.py
apk-signature-smoke: apksig-deps background-apk-fixture
	uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: package-registry-test
package-registry-test:
	uv run --no-project python -m unittest discover -s tests -p test_package_registry.py -v

.PHONY: host-control-smoke
host-control-smoke: apksig-deps background-apk-fixture
	uv run --no-project python -m unittest discover -s tests -p test_launch_settings.py -v
	uv run --no-project python -m unittest discover -s tests -p test_host_control.py -v
	SHIRAZ_TEST_HOST_CONTROL=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: host-client-smoke
host-client-smoke: apksig-deps background-apk-fixture
	SHIRAZ_TEST_HOST_SWIFT=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: service-broker-test
service-broker-test:
	uv run --no-project python -m unittest discover -s tests -p test_service_broker.py -v

.PHONY: service-broker-art-smoke
ifeq ($(shell uname -s),Darwin)
service-broker-art-smoke: macos-framework
else
service-broker-art-smoke: framework
endif
service-broker-art-smoke:
	uv run --no-project python -m unittest discover -s tests -p test_service_broker_art.py -v

.PHONY: registered-launch-smoke
ifeq ($(shell uname -s),Darwin)
registered-launch-smoke: macos-framework
else
registered-launch-smoke: framework
endif
registered-launch-smoke: apksig-deps background-apk-fixture
	SHIRAZ_TEST_REGISTERED_LAUNCH=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: registered-launch-unit-test
registered-launch-unit-test:
	uv run --no-project python -m unittest discover -s tests -p test_runtime_launch.py -v

.PHONY: service-entry-smoke
ifeq ($(shell uname -s),Darwin)
service-entry-smoke: macos-framework
else
service-entry-smoke: framework
endif
service-entry-smoke: background-apk-fixture
	SHIRAZ_TEST_SERVICE_ENTRY=1 uv run --no-project python -m unittest discover -s tests -p test_service_launch.py -v

.PHONY: registered-service-smoke service-only-apk-fixture
service-only-apk-fixture:
	uv run --no-project python scripts/build_macos_fixture.py --fixture service-only
ifeq ($(shell uname -s),Darwin)
registered-service-smoke: macos-framework
else
registered-service-smoke: framework
endif
registered-service-smoke: apksig-deps background-apk-fixture service-only-apk-fixture
	SHIRAZ_TEST_REGISTERED_SERVICE=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: broker-wire-test
broker-wire-test:
	uv run --no-project python -m unittest discover -s tests -p test_broker_wire.py -v

.PHONY: broker-bind-smoke
ifeq ($(shell uname -s),Darwin)
broker-bind-smoke: macos-framework
else
broker-bind-smoke: framework
endif
broker-bind-smoke: apksig-deps background-apk-fixture service-only-apk-fixture
	SHIRAZ_TEST_BROKER_BIND=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: context-broker-smoke broker-caller-apk-fixture
broker-caller-apk-fixture:
	uv run --no-project python scripts/build_macos_fixture.py --fixture broker-caller
ifeq ($(shell uname -s),Darwin)
context-broker-smoke: macos-framework
else
context-broker-smoke: framework
endif
context-broker-smoke: apksig-deps background-apk-fixture service-only-apk-fixture broker-caller-apk-fixture
	SHIRAZ_TEST_CONTEXT_BIND=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: logical-uid-smoke
ifeq ($(shell uname -s),Darwin)
context-broker-smoke background-apk-smoke: build/macos/libshiraz-host.so
else
context-broker-smoke background-apk-smoke: build/libshiraz-host.so
endif
logical-uid-smoke: context-broker-smoke background-apk-smoke background-service-smoke service-rebind-smoke

.PHONY: shared-service-smoke
ifeq ($(shell uname -s),Darwin)
shared-service-smoke: macos-framework build/macos/libshiraz-host.so
else
shared-service-smoke: framework build/libshiraz-host.so
endif
shared-service-smoke: apksig-deps background-apk-fixture service-only-apk-fixture broker-caller-apk-fixture
	SHIRAZ_TEST_SHARED_SERVICE=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: session-apk-fixtures host-session-smoke
session-apk-fixtures:
	uv run --no-project python scripts/build_macos_fixture.py --fixture session-one
	uv run --no-project python scripts/build_macos_fixture.py --fixture session-two
ifeq ($(shell uname -s),Darwin)
host-session-smoke: macos-framework build/macos/libshiraz-host.so
else
host-session-smoke: framework build/libshiraz-host.so
endif
host-session-smoke: apksig-deps background-apk-fixture service-only-apk-fixture session-apk-fixtures
	SHIRAZ_TEST_HOST_SESSION=1 uv run --no-project python -m unittest discover -s tests -p test_apk_verifier.py -v

.PHONY: launch-admission-test
launch-admission-test:
	uv run --no-project python -m unittest discover -s tests -p test_launch_admission.py -v

.PHONY: macos-guest-heap-capacity-check
macos-guest-heap-capacity-check: macos-guest-personality-check
	SHIRAZ_TEST_HEAP_CAPACITY=1 $(MACOS_OUT)/m5/personality-probe $(MACOS_GUEST_DEP_DIR)
	SHIRAZ_TEST_HEAP_CAPACITY=1 $(MACOS_OUT)/m5/personality-probe-hardened $(MACOS_GUEST_DEP_DIR)

.PHONY: macos-runtime-speech
macos-runtime-speech: build/macos/runtime-speech.stamp
build/macos/runtime-speech.stamp: scripts/bootstrap_darwin_speech.py scripts/bootstrap_runtime.py
	uv run --no-project python scripts/bootstrap_darwin_speech.py
	mkdir -p build/macos
	touch $@

$(MACOS_OUT)/speech.exports: scripts/darwin_jni_exports.py java/shiraz/runtime/NativeSpeech.java
	uv run --no-project python scripts/darwin_jni_exports.py java/shiraz/runtime/NativeSpeech.java > $@
$(MACOS_OUT)/libshiraz-speech.so: native/speech.cpp native/speech_output.cpp build/macos/runtime-speech.stamp $(MACOS_OUT)/speech.exports
	$(MACOS_CXX) $(MACOS_JNI_CFLAGS) -I$(CURDIR)/.local/include -std=c++17 -O2 -g -Wall -Wextra -Werror -fPIC $(MACOS_DYLIB) -Wl,-exported_symbols_list,$(MACOS_OUT)/speech.exports native/speech.cpp native/speech_output.cpp $(CURDIR)/.local/lib/libespeak-ng.a $(CURDIR)/.deps/darwin-speech/src/speechPlayer/libspeechPlayer.a $(CURDIR)/.deps/darwin-speech/src/ucd-tools/libucd.a -framework AudioToolbox -pthread -o $@.fresh
	mv -f $@.fresh $@
macos-runtime-libs macos-jvm-native: $(MACOS_OUT)/libshiraz-speech.so
.PHONY: macos-speech-smoke
macos-speech-smoke: macos-framework macos-jvm-native
	ln -sfn ../libshiraz-speech.so $(MACOS_JVM_NATIVE)/libshiraz-speech.dylib
	uv run --no-project python scripts/speech_smoke.py
	uv run --no-project python scripts/speech_playback_smoke.py

.PHONY: macos-tts-smoke
macos-tts-smoke: macos-speech-smoke
	uv run --no-project python scripts/tts_smoke.py

.PHONY: macos-service-lifecycle-smoke
macos-service-lifecycle-smoke: macos-framework macos-jvm-native
	uv run --no-project python scripts/service_smoke.py

.PHONY: notification-recovery-smoke
ifeq ($(shell uname -s),Darwin)
notification-recovery-smoke: macos-framework
else
notification-recovery-smoke: framework
endif
notification-recovery-smoke:
	uv run --no-project python scripts/java_framework_smoke.py NotificationRecoverySmoke --marker NOTIFICATION_RECOVERY_OK

.PHONY: notification-shared-policy-smoke
ifeq ($(shell uname -s),Darwin)
notification-shared-policy-smoke: macos-framework macos-jvm-native
else
notification-shared-policy-smoke: framework
endif
notification-shared-policy-smoke:
	uv run --no-project python scripts/java_framework_smoke.py NotificationSharedPolicySmoke --marker NOTIFICATION_SHARED_POLICY_OK

.PHONY: installer-session-lifecycle-smoke
ifeq ($(shell uname -s),Darwin)
installer-session-lifecycle-smoke: macos-framework
else
installer-session-lifecycle-smoke: framework
endif
installer-session-lifecycle-smoke:
	uv run --no-project python scripts/java_framework_smoke.py InstallerSessionLifecycleSmoke --marker INSTALLER_SESSION_LIFECYCLE_OK

.PHONY: macos-installer-smoke
macos-installer-smoke: macos-framework macos-jvm-native
	uv run --no-project python scripts/installer_smoke.py

.PHONY: service-filter-smoke
ifeq ($(shell uname -s),Darwin)
service-filter-smoke: macos-framework
else
service-filter-smoke: framework
endif
service-filter-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ServiceFilterSmoke --marker SERVICE_FILTER_MATCHING_OK

.PHONY: local-accounts-smoke
ifeq ($(shell uname -s),Darwin)
local-accounts-smoke: macos-framework
else
local-accounts-smoke: framework
endif
local-accounts-smoke:
	uv run --no-project python scripts/java_framework_smoke.py AccountsSmoke --marker LOCAL_ACCOUNT_LISTENERS_OK

.PHONY: job-periodic-interval-smoke
.PHONY: job-definition-unit-test
job-definition-unit-test:
	uv run --no-project python -m unittest discover -s tests -p test_job_definition.py -v

.PHONY: job-store-unit-test
job-store-unit-test:
	uv run --no-project python -m unittest discover -s tests -p test_job_store.py -v

ifeq ($(shell uname -s),Darwin)
job-periodic-interval-smoke: macos-framework
else
job-periodic-interval-smoke: framework
endif
job-periodic-interval-smoke:
	uv run --no-project python scripts/java_framework_smoke.py JobPeriodicIntervalSmoke --marker JOB_PERIODIC_REAL_INTERVAL_OK --runtime art --timeout 960

.PHONY: job-scheduler-unit-test
job-scheduler-unit-test:
	uv run --no-project python -m unittest discover -s tests -p test_job_scheduler.py -v

.PHONY: job-constraint-snapshot-smoke
ifeq ($(shell uname -s),Darwin)
job-constraint-snapshot-smoke: macos-framework
else
job-constraint-snapshot-smoke: framework
endif
job-constraint-snapshot-smoke:
	uv run --no-project python scripts/java_framework_smoke.py JobConstraintSnapshotSmoke --marker JOB_CONSTRAINT_SNAPSHOT_OK

.PHONY: network-capability-smoke
ifeq ($(shell uname -s),Darwin)
network-capability-smoke: macos-framework
else
network-capability-smoke: framework
endif
network-capability-smoke:
	uv run --no-project python scripts/java_framework_smoke.py NetworkCapabilitySmoke --marker NETWORK_CAPABILITY_OK

.PHONY: connectivity-transitions-smoke
ifeq ($(shell uname -s),Darwin)
connectivity-transitions-smoke: macos-framework
else
connectivity-transitions-smoke: framework
endif
connectivity-transitions-smoke:
	uv run --no-project python scripts/java_framework_smoke.py android.net.ConnectivityTransitionsSmoke --marker CONNECTIVITY_TRANSITIONS_OK

.PHONY: frame-measure-pass-smoke
ifeq ($(shell uname -s),Darwin)
frame-measure-pass-smoke: macos-framework
else
frame-measure-pass-smoke: framework
endif
frame-measure-pass-smoke:
	uv run --no-project python scripts/java_framework_smoke.py FrameMeasurePassSmoke --marker FRAME_MEASURE_PASS_OK

.PHONY: list-saved-state-smoke
ifeq ($(shell uname -s),Darwin)
list-saved-state-smoke: macos-framework
else
list-saved-state-smoke: framework
endif
list-saved-state-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ListSavedStateSmoke --marker LIST_SAVED_STATE_OK --framework-res build/framework-res/framework-res.apk

.PHONY: download-network-policy-smoke
ifeq ($(shell uname -s),Darwin)
download-network-policy-smoke: macos-framework
else
download-network-policy-smoke: framework
endif
download-network-policy-smoke:
	uv run --no-project python scripts/java_framework_smoke.py shiraz.runtime.DownloadNetworkPolicySmoke --marker DOWNLOAD_NETWORK_POLICY_OK

.PHONY: socket-peer-close-smoke
ifeq ($(shell uname -s),Darwin)
socket-peer-close-smoke: macos-framework
socket-peer-close-smoke:
	SHIRAZ_TEST_DARWIN_SOCKET=1 uv run --no-project python scripts/java_framework_smoke.py SocketPeerCloseSmoke --marker SOCKET_PEER_CLOSE_OK
else
socket-peer-close-smoke: framework
socket-peer-close-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SocketPeerCloseSmoke --marker SOCKET_PEER_CLOSE_OK
endif

# Prompt transport abort is verified against the pinned ART HTTP implementation.
.PHONY: download-cancel-blocked-art-smoke
ifeq ($(shell uname -s),Darwin)
download-cancel-blocked-art-smoke: macos-framework
else
download-cancel-blocked-art-smoke: framework
endif
download-cancel-blocked-art-smoke:
	uv run --no-project python scripts/java_framework_smoke.py DownloadCancelBlockedSmoke --marker DOWNLOAD_CANCEL_BLOCKED_OK --runtime art

.PHONY: download-blocked-policy-art-smoke
ifeq ($(shell uname -s),Darwin)
download-blocked-policy-art-smoke: macos-framework
else
download-blocked-policy-art-smoke: framework
endif
download-blocked-policy-art-smoke:
	uv run --no-project python scripts/java_framework_smoke.py DownloadBlockedPolicySmoke --marker DOWNLOAD_BLOCKED_POLICY_OK --runtime art

.PHONY: service-connection-cleanup-smoke
ifeq ($(shell uname -s),Darwin)
service-connection-cleanup-smoke: macos-framework
else
service-connection-cleanup-smoke: framework
endif
service-connection-cleanup-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ServiceConnectionCleanupSmoke --marker SERVICE_CONNECTION_CLEANUP_OK

.PHONY: service-start-result-smoke
ifeq ($(shell uname -s),Darwin)
service-start-result-smoke: macos-framework
else
service-start-result-smoke: framework
endif
service-start-result-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ServiceStartResultSmoke --marker SERVICE_START_RESULT_OK

.PHONY: margin-resolution-smoke
ifeq ($(shell uname -s),Darwin)
margin-resolution-smoke: macos-framework
else
margin-resolution-smoke: framework
endif
margin-resolution-smoke:
	uv run --no-project python scripts/java_framework_smoke.py MarginResolutionSmoke --marker MARGIN_RESOLUTION_OK

.PHONY: sync-result-smoke
ifeq ($(shell uname -s),Darwin)
sync-result-smoke: macos-framework
else
sync-result-smoke: framework
endif
sync-result-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SyncResultSmoke --marker SYNC_RESULT_CONTRACT_OK

.PHONY: sync-context-smoke
ifeq ($(shell uname -s),Darwin)
sync-context-smoke: macos-framework
else
sync-context-smoke: framework
endif
sync-context-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SyncContextSmoke --marker SYNC_CONTEXT_WIRE_OK

.PHONY: sync-adapter-wire-smoke
ifeq ($(shell uname -s),Darwin)
sync-adapter-wire-smoke: macos-framework
else
sync-adapter-wire-smoke: framework
endif
sync-adapter-wire-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SyncAdapterWireSmoke --marker SYNC_ADAPTER_WIRE_OK

.PHONY: threaded-sync-smoke
ifeq ($(shell uname -s),Darwin)
threaded-sync-smoke: macos-framework
else
threaded-sync-smoke: framework
endif
threaded-sync-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ThreadedSyncSmoke --marker THREADED_SYNC_LIFECYCLE_OK

.PHONY: provider-client-lifecycle-smoke
.PHONY: canvas-clip-out-smoke
.PHONY: compose-shader-smoke
ifeq ($(shell uname -s),Darwin)
compose-shader-smoke: macos-framework
else
compose-shader-smoke: framework
endif
compose-shader-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ComposeShaderSmoke --marker COMPOSE_SHADER_OK

ifeq ($(shell uname -s),Darwin)
canvas-clip-out-smoke: macos-framework
else
canvas-clip-out-smoke: framework
endif
canvas-clip-out-smoke:
	uv run --no-project python scripts/java_framework_smoke.py CanvasClipOutSmoke --marker CANVAS_CLIP_OUT_OK

ifeq ($(shell uname -s),Darwin)
provider-client-lifecycle-smoke: macos-framework
else
provider-client-lifecycle-smoke: framework
endif
provider-client-lifecycle-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ProviderClientLifecycleSmoke --marker PROVIDER_CLIENT_LIFECYCLE_OK

.PHONY: sync-owner-smoke
ifeq ($(shell uname -s),Darwin)
sync-owner-smoke: macos-framework
else
sync-owner-smoke: framework
endif
sync-owner-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SyncOwnerSmoke --marker SYNC_OWNER_SHUTDOWN_OK

.PHONY: sync-owner-apk-fixture sync-owner-apk-smoke
sync-owner-apk-fixture:
	uv run --no-project python scripts/build_macos_fixture.py --fixture sync-owner

sync-owner-apk-smoke: sync-owner-apk-fixture
ifeq ($(shell uname -s),Darwin)
sync-owner-apk-smoke: macos-framework build/macos/libshiraz-host.so
else
sync-owner-apk-smoke: framework build/libshiraz-host.so
endif
sync-owner-apk-smoke:
	uv run --no-project python scripts/sync_owner_apk_smoke.py

.PHONY: sync-adapter-type-smoke
ifeq ($(shell uname -s),Darwin)
sync-adapter-type-smoke: macos-framework
else
sync-adapter-type-smoke: framework
endif
sync-adapter-type-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SyncAdapterTypeSmoke --marker SYNC_ADAPTER_TYPE_OK

.PHONY: sync-extras-smoke
ifeq ($(shell uname -s),Darwin)
sync-extras-smoke: macos-framework
else
sync-extras-smoke: framework
endif
sync-extras-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SyncExtrasSmoke --marker SYNC_EXTRAS_VALIDATION_OK

.PHONY: content-observer-delivery-smoke
ifeq ($(shell uname -s),Darwin)
content-observer-delivery-smoke: macos-framework
else
content-observer-delivery-smoke: framework
endif
content-observer-delivery-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ContentObserverDeliverySmoke --marker CONTENT_OBSERVER_DELIVERY_OK

.PHONY: cursor-notification-smoke
ifeq ($(shell uname -s),Darwin)
cursor-notification-smoke: macos-framework
else
cursor-notification-smoke: framework
endif
cursor-notification-smoke:
	uv run --no-project python scripts/java_framework_smoke.py CursorNotificationSmoke --marker CURSOR_NOTIFICATION_OK

.PHONY: content-observable-smoke
ifeq ($(shell uname -s),Darwin)
content-observable-smoke: macos-framework
else
content-observable-smoke: framework
endif
content-observable-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ContentObservableSmoke --marker CONTENT_OBSERVABLE_OK

.PHONY: sync-request-wire-smoke
ifeq ($(shell uname -s),Darwin)
sync-request-wire-smoke: macos-framework
else
sync-request-wire-smoke: framework
endif
sync-request-wire-smoke:
	uv run --no-project python scripts/java_framework_smoke.py shiraz.runtime.SyncRequestWireSmoke --marker SYNC_REQUEST_WIRE_OK

.PHONY: local-sync-execution-smoke
ifeq ($(shell uname -s),Darwin)
local-sync-execution-smoke: macos-framework
else
local-sync-execution-smoke: framework
endif
local-sync-execution-smoke:
	uv run --no-project python scripts/java_framework_smoke.py shiraz.runtime.LocalSyncExecutionSmoke --marker LOCAL_SYNC_EXECUTION_OK

.PHONY: sync-dispatch-session-smoke
ifeq ($(shell uname -s),Darwin)
sync-dispatch-session-smoke: macos-framework
else
sync-dispatch-session-smoke: framework
endif
sync-dispatch-session-smoke:
	uv run --no-project python scripts/java_framework_smoke.py shiraz.runtime.SyncDispatchSessionSmoke --marker SYNC_DISPATCH_SESSION_OK

.PHONY: sync-dispatch-apk-fixture
sync-dispatch-apk-fixture:
	uv run --no-project python scripts/build_macos_fixture.py --fixture sync-dispatch

.PHONY: sync-dispatch-apk-smoke
ifeq ($(shell uname -s),Darwin)
sync-dispatch-apk-smoke: macos-framework
else
sync-dispatch-apk-smoke: framework
endif
sync-dispatch-apk-smoke: apksig-deps sync-dispatch-apk-fixture
	PYTHONPATH=src uv run --no-project python tests/sync_dispatch_apk_probe.py

.PHONY: notification-group-smoke
ifeq ($(shell uname -s),Darwin)
notification-group-smoke: macos-framework macos-jvm-native
else
notification-group-smoke: framework
endif
notification-group-smoke:
	uv run --no-project python scripts/java_framework_smoke.py NotificationGroupSmoke --marker NOTIFICATION_GROUP_OK

.PHONY: restrictions-smoke
ifeq ($(shell uname -s),Darwin)
restrictions-smoke: macos-framework
else
restrictions-smoke: framework
endif
restrictions-smoke:
	uv run --no-project python scripts/java_framework_smoke.py RestrictionsSmoke --marker RESTRICTIONS_OK

.PHONY: user-identity-smoke
ifeq ($(shell uname -s),Darwin)
user-identity-smoke: macos-framework
else
user-identity-smoke: framework
endif
user-identity-smoke:
	uv run --no-project python scripts/java_framework_smoke.py UserIdentitySmoke --marker USER_IDENTITY_OK

.PHONY: service-declaration-smoke
ifeq ($(shell uname -s),Darwin)
service-declaration-smoke: macos-framework
else
service-declaration-smoke: framework
endif
service-declaration-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ServiceDeclarationSmoke --marker SERVICE_DECLARATION_OK

.PHONY: list-activity-smoke
ifeq ($(shell uname -s),Darwin)
list-activity-smoke: macos-framework framework-resources
else
list-activity-smoke: framework framework-resources
endif
list-activity-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ListActivitySmoke --marker LIST_ACTIVITY_OK --framework-res build/framework-res/framework-res.apk

.PHONY: preference-screen-smoke
ifeq ($(shell uname -s),Darwin)
preference-screen-smoke: macos-framework framework-resources
else
preference-screen-smoke: framework framework-resources
endif
preference-screen-smoke:
	uv run --no-project python scripts/java_framework_smoke.py PreferenceScreenSmoke --marker PREFERENCE_SCREEN_OK --framework-res build/framework-res/framework-res.apk

.PHONY: search-recent-smoke
ifeq ($(shell uname -s),Darwin)
search-recent-smoke: macos-framework macos-jvm-native
else
search-recent-smoke: framework
endif
search-recent-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SearchRecentSuggestionsSmoke --marker SEARCH_RECENT_OK

.PHONY: file-size-formatter-smoke
ifeq ($(shell uname -s),Darwin)
file-size-formatter-smoke: macos-framework framework-resources
else
file-size-formatter-smoke: framework framework-resources
endif
file-size-formatter-smoke:
	uv run --no-project python scripts/java_framework_smoke.py FileSizeFormatterSmoke --marker FILE_SIZE_FORMATTER_OK --framework-res build/framework-res/framework-res.apk

.PHONY: battery-manager-smoke
ifeq ($(shell uname -s),Darwin)
battery-manager-smoke: macos-framework
else
battery-manager-smoke: framework
endif
battery-manager-smoke:
	uv run --no-project python scripts/java_framework_smoke.py BatteryManagerSmoke --marker BATTERY_MANAGER_UNAVAILABLE_OK

.PHONY: application-status-smoke
ifeq ($(shell uname -s),Darwin)
application-status-smoke: macos-framework
else
application-status-smoke: framework
endif
application-status-smoke:
	uv run --no-project python scripts/java_framework_smoke.py ApplicationStatusSmoke --marker APPLICATION_STATUS_OK

.PHONY: subscription-listener-smoke
ifeq ($(shell uname -s),Darwin)
subscription-listener-smoke: macos-framework
else
subscription-listener-smoke: framework
endif
subscription-listener-smoke:
	uv run --no-project python scripts/java_framework_smoke.py SubscriptionListenerSmoke --marker SUBSCRIPTION_LISTENER_OK

.PHONY: storage-stats-smoke
ifeq ($(shell uname -s),Darwin)
storage-stats-smoke: macos-framework
else
storage-stats-smoke: framework
endif
storage-stats-smoke:
	uv run --no-project python scripts/java_framework_smoke.py StorageStatsSmoke --marker STORAGE_STATS_OK

.PHONY: intent-filter-identity-smoke
ifeq ($(shell uname -s),Darwin)
intent-filter-identity-smoke: macos-framework
else
intent-filter-identity-smoke: framework
endif
intent-filter-identity-smoke:
	uv run --no-project python scripts/java_framework_smoke.py IntentFilterIdentitySmoke --marker INTENT_FILTER_IDENTITY_OK

.PHONY: traffic-uid-smoke
ifeq ($(shell uname -s),Darwin)
traffic-uid-smoke: macos-framework
else
traffic-uid-smoke: framework
endif
traffic-uid-smoke:
	uv run --no-project python scripts/java_framework_smoke.py TrafficUidSmoke --marker TRAFFIC_UID_OK
