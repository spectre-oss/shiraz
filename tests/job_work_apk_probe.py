# SPDX-License-Identifier: GPL-3.0-only
"""Actual signed APK: public enqueue -> independent ART worker -> ack -> drain."""

import json
import subprocess
import tempfile
import time
from pathlib import Path

from shiraz.package_registry import PackageRegistry
from shiraz.service_broker import ServiceBroker

ROOT = Path(__file__).resolve().parents[1]


def main():
    work = ROOT / 'build/job-work-apk-probe'
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work) as directory:
        temp = Path(directory)
        store, password, signed = temp / 'fixture.p12', temp / 'password', temp / 'signed.apk'
        password.write_text('local-fixture-only\n')
        subprocess.run(
            [
                'keytool',
                '-genkeypair',
                '-alias',
                'fixture',
                '-keyalg',
                'RSA',
                '-keysize',
                '2048',
                '-validity',
                '2',
                '-dname',
                'CN=Shiraz job work fixture',
                '-keystore',
                str(store),
                '-storepass:file',
                str(password),
            ],
            check=True,
            capture_output=True,
            timeout=30,
        )
        subprocess.run(
            [
                'jarsigner',
                '-keystore',
                str(store),
                '-storepass:file',
                str(password),
                '-signedjar',
                str(signed),
                str(ROOT / 'build/macos-apk/job-work.apk'),
                'fixture',
            ],
            check=True,
            capture_output=True,
            timeout=30,
        )
        registry = PackageRegistry(temp / 'registry')
        identity = registry.enroll(signed, expected_current='absent')
        uid, sha, package = identity['uid'], identity['apk_sha256'], identity['package']
        definition = dict(
            version=1,
            id=1,
            component=package + '.Worker',
            persisted=False,
            minimum_latency_ms=0,
            deadline_ms=None,
            interval_ms=0,
            flex_ms=0,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={},
        )
        first = registry.store_shared_job(uid, sha, definition)
        evidence = []
        with ServiceBroker(registry) as broker:
            dispatch = broker.dispatch_persisted_job(
                package, uid, sha, 1, first['revision'], timeout=20
            )
            assert dispatch is not None
            result = dispatch.wait(30)
            assert result == dict(applied=True, retry=False), result
            evidence.append(str(dispatch.worker.evidence))
            jobs = registry.list_persisted_jobs(uid, sha)
            assert len(jobs) == 1 and jobs[0]['id'] == 2, jobs
            with registry.connect() as db:
                assert db.execute('SELECT count(*) FROM job_work_items').fetchone()[0] == 2
            # Only wait for a known scheduled job's 1ms minimum latency.
            deadline = time.monotonic() + 5
            while True:
                dispatch = broker.dispatch_persisted_job(
                    package, uid, sha, 2, jobs[0]['revision'], timeout=20
                )
                if dispatch is not None:
                    break
                assert time.monotonic() < deadline, 'Queued job did not become eligible'
                time.sleep(0.01)
            result = dispatch.wait(30)
            assert result == dict(applied=True, retry=False), result
            evidence.append(str(dispatch.worker.evidence))
            assert registry.list_persisted_jobs(uid, sha) == []
            with registry.connect() as db:
                for table in ('job_work_items', 'job_work_drains', 'persisted_job_claims'):
                    assert db.execute('SELECT count(*) FROM ' + table).fetchone()[0] == 0, table
        first_output = (Path(evidence[0]) / 'stdout.log').read_text()
        second_output = (Path(evidence[1]) / 'stdout.log').read_text()
        assert 'JOB_WORK_APK_ENQUEUED' in first_output
        assert 'JOB_WORK_APK_ACKNOWLEDGED count=2' in second_output
        assert all('JOB_WORK_APK_DESTROYED' in output for output in (first_output, second_output))
        (work / 'result.json').write_text(
            json.dumps(dict(result='passed', evidence=evidence), indent=2) + '\n'
        )
        print('JOB_WORK_APK_OK', json.dumps(evidence), flush=True)


if __name__ == '__main__':
    main()
