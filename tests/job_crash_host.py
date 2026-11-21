# SPDX-License-Identifier: GPL-3.0-only
"""Disposable host for signed-APK abrupt-death integration; test invocation only."""

import json
import sys
import time

from shiraz.package_registry import PackageRegistry
from shiraz.service_broker import ServiceBroker

registry = PackageRegistry(sys.argv[1])
request = json.load(sys.stdin)
identity = registry.identity(request['package'])
with ServiceBroker(registry) as broker:
    saved = registry.store_shared_job(identity['uid'], identity['sha256'], request['definition'])
    run = broker.dispatch_persisted_job(
        request['package'], identity['uid'], identity['sha256'], saved['id'], saved['revision']
    )
    marker = (
        'APK_CRASH_RETRY_FIRST'
        if request['definition']['extras']['action']['value'] == 'crash-retry'
        else 'HEADLESS_JOB_STARTED'
    )
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        output = run.worker.evidence / 'stdout.log'
        if output.exists() and marker in output.read_text():
            print(
                json.dumps(dict(pid=run.worker.process.pid, evidence=str(run.worker.evidence))),
                flush=True,
            )
            break
        if run.done.is_set():
            raise RuntimeError('Crash fixture stopped before APK onStartJob')
        time.sleep(0.05)
    else:
        raise TimeoutError('APK did not start')
    time.sleep(60)
    raise TimeoutError('Test host was not terminated')
