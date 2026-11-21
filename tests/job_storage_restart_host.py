# SPDX-License-Identifier: GPL-3.0-only
"""Disposable host for real APK pending-job restart acceptance; test use only."""

import json
import os
import queue
import sys
import threading
from unittest.mock import patch

from shiraz.job_constraints import StorageObservation
from shiraz.package_registry import PackageRegistry
from shiraz.service_broker import ServiceBroker

registry = PackageRegistry(sys.argv[1])
request = json.load(sys.stdin)
identity = registry.identity(request['package'])
uid, digest = identity['uid'], identity['sha256']
mode = request['mode']
if mode not in ('prepare', 'resume'):
    raise ValueError('Unknown storage host test mode')
observed = threading.Event()
state = [StorageObservation(registry.root, 100, 5, 5)]


def observe(_path):
    observed.set()
    return state[0]


with (
    patch('shiraz.job_constraints.observe_storage', side_effect=observe),
    ServiceBroker(registry) as broker,
):
    if mode == 'prepare':
        definition = dict(
            version=1,
            id=17,
            component=request['package'] + '.HeadlessJob',
            persisted=False,
            minimum_latency_ms=0,
            deadline_ms=None,
            interval_ms=0,
            flex_ms=0,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={'action': dict(type='string', value='submit-storage')},
        )
        source = broker.start_job(uid, digest, definition)
        assert source.wait(40) is False
        assert 'APK_PUBLIC_JOB_SUBMITTED' in (source.evidence / 'stdout.log').read_text()
    saved = registry.list_persisted_jobs(uid, digest)
    assert len(saved) == 1 and saved[0]['id'] == 18
    assert (
        saved[0]['definition']['persisted'] and saved[0]['definition']['requires_storage_not_low']
    )
    dispatched = queue.Queue(maxsize=2)
    dispatch = broker.dispatch_persisted_job

    def track(*args, **kwargs):
        run = dispatch(*args, **kwargs)
        if run is not None:
            dispatched.put_nowait(run)
        return run

    with patch.object(broker, 'dispatch_persisted_job', side_effect=track):
        scheduler = broker.start_job_scheduler()
        assert observed.wait(5), 'Scheduler did not evaluate low storage'
        try:
            dispatched.get(timeout=1.2)
            raise AssertionError('Low-storage job dispatched')
        except queue.Empty:
            pass
        with registry.connect() as db:
            assert db.execute('SELECT count(*) FROM persisted_job_claims').fetchone()[0] == 0
        result = dict(pid=os.getpid(), mode=mode, saved=saved[0], low_observed=True)
        if mode == 'resume':
            state[0] = StorageObservation(registry.root, 100, 50, 5)
            run = dispatched.get(timeout=30)
            assert run.wait(40) == dict(applied=True, retry=False)
            assert run.worker.terminal_cleanup_complete
            assert 'APK_PUBLIC_JOB_EXECUTED' in (run.worker.evidence / 'stdout.log').read_text()
            assert registry.list_persisted_jobs(uid, digest) == []
            result['worker_evidence'] = str(run.worker.evidence)
        assert not scheduler.failures, list(scheduler.failures)
# Report only after host shutdown has completed.
print(json.dumps(result), flush=True)
