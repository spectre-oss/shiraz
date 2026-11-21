# SPDX-License-Identifier: GPL-3.0-only
"""Trusted host due-job dispatch. Guest admission and crash recovery remain separate."""

import threading

from . import job_clock, job_constraints, job_lifetime, job_recovery
from .job_definition import integer


class JobLaunchError(RuntimeError):
    def __init__(self, claim):
        super().__init__(
            'Job launch failed; reservation retained until worker cleanup is established'
        )
        self.claim = claim


class JobDispatch:
    """Broker-owned finalization; wait only observes the recorded outcome.

    Uncertain cleanup or database failure retains the reservation and supervisor
    for inspection. No timeout, caller disappearance or retry frees a claim.
    """

    def __init__(self, broker, claim, worker, lifetime=None, *, package, deadline_expired=False):
        self.broker = broker
        self.registry = broker.registry
        self.claim = claim
        self.worker = worker
        self.lifetime = lifetime
        self.package = package
        self.deadline_expired = deadline_expired
        self.completion_lock = threading.Lock()
        self.constraint_stopped = False
        self.foreground_stopped = False
        self.done = threading.Event()
        self.result = None
        self.error = None
        self.thread = threading.Thread(
            target=self._finalize, name='shiraz-job-completion', daemon=True
        )
        broker.job_dispatches.add(self)
        try:
            self.thread.start()
        except BaseException as error:
            self.error = error
            self.done.set()
            raise

    def _finalize(self):
        try:
            failure = None
            try:
                retry = self.worker.wait()
            except RuntimeError as error:
                failure = error
                retry = True
            if not self.worker.terminal_cleanup_complete:
                raise RuntimeError('Job cleanup is incomplete; reservation retained') from failure
            with self.completion_lock:
                retry = retry or (
                    self.constraint_stopped and getattr(self.worker, 'stop_reason', 0) != 9
                )
                retry = retry or (
                    self.foreground_stopped and getattr(self.worker, 'stop_reason', 0) == 14
                )
                applied = self.registry.complete_persisted_job_claim(
                    self.claim['owner_uid'], self.claim['id'], self.claim['token'], retry=retry
                )
                self.result = dict(applied=applied, retry=retry)
            if failure is not None:
                self.error = RuntimeError(
                    'Job execution failed; terminal claim transition recorded'
                )
                self.error.__cause__ = failure
        except BaseException as error:
            self.error = error
        finally:
            with self.broker.lock:
                if self.result is not None:
                    if self.lifetime is not None:
                        self.lifetime.close()
                    self.broker.job_dispatches.discard(self)
                self.done.set()

    def stop_for_constraints(self):
        # Serialize stop intent with the durable terminal transition. A worker
        # completing concurrently cannot discard a job already stopped by us.
        with self.completion_lock:
            if self.result is not None or self.done.is_set():
                return
            self.constraint_stopped = True
        stop = getattr(self.worker, 'stop_for_constraints', self.worker.close)
        stop()

    def stop_for_foreground(self):
        # Foreground admission is an explicit scheduler decision, not an
        # observation timeout. Natural completion racing this request wins.
        with self.completion_lock:
            if self.result is not None or self.done.is_set():
                return
            self.foreground_stopped = True
        self.worker.stop_for_foreground()

    def wait(self, timeout=None):
        if not self.done.wait(timeout):
            raise TimeoutError('Job completion is still pending')
        if self.error is not None:
            raise self.error
        return dict(self.result)

    def drain(self, timeout=15):
        """Shutdown requires recorded state, but a recorded worker failure is normal."""
        if not self.done.wait(timeout):
            raise TimeoutError('Job completion supervisor did not stop')
        if self.result is None:
            raise RuntimeError('Job completion failed; reservation retained') from self.error


def start(broker, package, owner_uid, apk_sha256, job_id, revision, *, timeout=60):
    """Pin generation across due claim and launch; return None for stale/not-due work.

    Startup failures retain their claim because a failed constructor may have
    spawned a worker whose teardown failed. JobLaunchError exposes that claim
    for explicit recovery; never infer safe takeover merely from elapsed time.
    """
    integer(timeout, 1, 600)
    with broker.lock:
        if broker.closed or package in broker.quiesced:
            raise RuntimeError('Job broker closed or package quiesced')
        with broker.registry.lease(package, apk_sha256) as identity:
            if identity['uid'] != owner_uid:
                raise PermissionError('Job owner identity mismatch')
            if (
                len(broker.processes) >= 64
                or len(broker.job_dispatches) >= 64
                or any(key[0] == package for key in broker.pending_retirements)
            ):
                return None
            rows = broker.registry.list_persisted_jobs(owner_uid, apk_sha256)
            current = next(
                (row for row in rows if row['id'] == job_id and row['revision'] == revision), None
            )
            if current is None:
                return None
            deadline_expired = job_constraints.deadline_expired(current, job_clock.elapsed_ms())
            if not job_constraints.satisfied(
                current['definition'], package, override_deadline_expired=deadline_expired
            ):
                return None
            service = broker.registry.authorize_job(
                owner_uid, apk_sha256, current['definition']['component']
            )['service']
            process = service.get('process') or package
            if process.startswith(':'):
                process = package + process
            if (package, apk_sha256, process) in broker.live_processes or (
                package,
                apk_sha256,
                process,
            ) in broker.foreground_admissions:
                return None
            claim = broker.registry.claim_due_persisted_job(owner_uid, apk_sha256, job_id, revision)
            if claim is None:
                return None
            lifetime = None
            try:
                lifetime = job_lifetime.create(broker.registry.root, claim['token'])
                job_recovery.register(broker.registry, claim, lifetime)
                worker = broker.start_job(
                    owner_uid,
                    apk_sha256,
                    claim['definition'],
                    timeout=timeout,
                    lifetime_fd=lifetime.fileno(),
                    deadline_expired=deadline_expired,
                    work_claim=claim,
                )
                return JobDispatch(
                    broker,
                    claim,
                    worker,
                    lifetime,
                    package=package,
                    deadline_expired=deadline_expired,
                )
            except BaseException as error:
                if lifetime is not None:
                    lifetime.close()  # Any spawned descendants retain their inherited holders.
                raise JobLaunchError(claim) from error
