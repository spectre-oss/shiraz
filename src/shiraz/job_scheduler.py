# SPDX-License-Identifier: GPL-3.0-only
"""Broker-lifetime scheduling and invalidation of shared job definitions."""

import threading
from collections import deque

from . import job_clock, job_constraints, job_recovery
from .job_store import GLOBAL_LIMIT


class JobScheduler:
    def __init__(self, broker):
        self.broker = broker
        self.stop = threading.Event()
        self.failures = deque(maxlen=64)
        self.lock_collector = job_recovery.LockCollector(broker.registry)
        self.recovery_cursor = 0
        self.cursor = 0
        self.thread = threading.Thread(target=self._run, name='shiraz-job-scheduler', daemon=True)

    def reconcile_running(self):
        with self.broker.lock:
            if self.broker.closed or self.stop.is_set():
                return
            running = list(self.broker.job_dispatches)
        for run in running:
            if self.stop.is_set():
                return
            if run.done.is_set():
                continue
            claim = run.claim
            try:
                if not self.broker.registry.job_claim_is_current(
                    claim['owner_uid'], claim['id'], claim['token']
                ):
                    # Only the owning supervisor finalizes after terminal cleanup.
                    # Another host's deletion/replacement never grants takeover.
                    run.worker.close()
                else:
                    # Once expired during this invocation, a wall-clock rollback
                    # must not reintroduce a constraint already overridden.
                    run.deadline_expired = run.deadline_expired or job_constraints.deadline_expired(
                        claim, job_clock.elapsed_ms()
                    )
                    if not job_constraints.satisfied(
                        claim['definition'],
                        run.package,
                        override_deadline_expired=run.deadline_expired,
                    ):
                        run.stop_for_constraints()
            except Exception as error:
                self.failures.append(
                    dict(
                        package=None,
                        id=claim['id'],
                        error=type(error).__name__,
                        message=str(error)[:1024],
                    )
                )

    def recover_abandoned(self):
        rows = job_recovery.candidates(self.broker.registry)
        if not rows:
            self.recovery_cursor = 0
            return
        start = self.recovery_cursor % len(rows)
        for offset in range(min(64, len(rows))):
            if self.stop.is_set() or self.broker.closed:
                return
            row = rows[(start + offset) % len(rows)]
            self.recovery_cursor = (start + offset + 1) % len(rows)
            try:
                job_recovery.recover(
                    self.broker.registry, row['owner_uid'], row['job_id'], row['token']
                )
            except Exception as error:
                self.failures.append(
                    dict(
                        package=None,
                        id=row['job_id'],
                        error=type(error).__name__,
                        message=str(error)[:1024],
                    )
                )

    def tick(self):
        self.reconcile_running()
        self.recover_abandoned()
        if not self.stop.is_set() and not self.broker.closed:
            self.lock_collector.tick()
        # Full bounded discovery avoids starving later packages behind busy rows.
        rows = self.broker.registry.due_persisted_jobs(limit=GLOBAL_LIMIT)
        if not rows:
            self.cursor = 0
            return
        start = self.cursor % len(rows)
        for offset in range(min(64, len(rows))):
            if self.stop.is_set():
                return
            row = rows[(start + offset) % len(rows)]
            self.cursor = (start + offset + 1) % len(rows)
            with self.broker.lock:
                if self.broker.closed:
                    return
                if len(self.broker.job_dispatches) >= 4:
                    return
                if row['package'] in self.broker.quiesced:
                    continue
            try:
                self.broker.dispatch_persisted_job(
                    row['package'], row['owner_uid'], row['apk_sha256'], row['id'], row['revision']
                )
            except Exception as error:
                # Keep bounded host-visible evidence, never release an ambiguous
                # launch claim or let one package terminate the scheduler.
                self.failures.append(
                    dict(
                        package=row['package'],
                        id=row['id'],
                        error=type(error).__name__,
                        message=str(error)[:1024],
                    )
                )

    def _run(self):
        while not self.stop.is_set():
            try:
                self.tick()
            except Exception as error:
                self.failures.append(
                    dict(
                        package=None, id=None, error=type(error).__name__, message=str(error)[:1024]
                    )
                )
            # Elapsed eligibility is re-read each tick and after resume. This
            # wait does not itself wake a suspended machine.
            self.stop.wait(1)

    def close(self):
        self.stop.set()
        self.thread.join(15)
        if self.thread.is_alive():
            raise RuntimeError('Job scheduler did not stop')
        self.lock_collector.close()
