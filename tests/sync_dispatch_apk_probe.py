# SPDX-License-Identifier: GPL-3.0-only
"""Signed fixture APK acceptance for queue -> ART adapter -> callback -> completion."""

import json
import subprocess
import tempfile
import time
from pathlib import Path

from shiraz.package_registry import PackageRegistry
from shiraz.service_broker import ServiceBroker

ROOT = Path(__file__).resolve().parents[1]


def main():
    work = ROOT / 'build/sync-dispatch-apk-probe'
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
                'CN=Shiraz sync test fixture',
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
                str(ROOT / 'build/macos-apk/sync-dispatch.apk'),
                'fixture',
            ],
            check=True,
            capture_output=True,
            timeout=30,
        )
        registry = PackageRegistry(temp / 'registry')
        record = registry.enroll(signed, expected_current='absent')
        registry.account_operation(
            record['uid'],
            record['apk_sha256'],
            dict(
                action='add',
                name='fixture-user',
                type='org.shiraz.syncdispatchfixture.account',
                password=None,
                extras={},
            ),
        )
        results = []
        before = set((ROOT / 'build/service-processes').iterdir())
        with ServiceBroker(registry) as broker:
            for retry in (False, True):
                ids = registry.request_sync(
                    record['uid'],
                    record['apk_sha256'],
                    dict(
                        account=None,
                        authority=None,
                        extras={'retry': dict(type='boolean', value=retry)},
                    ),
                )
                assert len(ids) == 1
                result = broker.dispatch_sync_request(ids[0], timeout=20)
                assert result == dict(
                    status='retry' if retry else 'succeeded', delay_until=0, applied=True
                ), result
                assert len(registry.pending_sync_requests()) == int(retry)
                with registry.connect() as db:
                    assert db.execute('SELECT COUNT(*) FROM sync_request_claims').fetchone()[0] == 0
                results.append(result)
            automatic = registry.request_sync(
                record['uid'],
                record['apk_sha256'],
                dict(
                    account=None,
                    authority=None,
                    extras={'automatic': dict(type='boolean', value=True)},
                ),
            )[0]
            scheduler = broker.start_sync_scheduler()
            deadline = time.monotonic() + 20
            while any(item['request_id'] == automatic for item in registry.pending_sync_requests()):
                assert time.monotonic() < deadline, 'Automatic sync dispatch did not finish'
                time.sleep(0.05)
            assert not scheduler.failures, list(scheduler.failures)
            results.append(dict(status='automatic-succeeded'))
        evidence = sorted(set((ROOT / 'build/service-processes').iterdir()) - before)
        completed = []
        for path in evidence:
            output = (path / 'stdout.log').read_text()
            if 'SYNC_DISPATCH_APK_EXECUTED' in output and 'SYNC_DISPATCH_APK_UNBOUND' in output:
                completed.append(str(path))
        assert len(completed) == 3, 'Three real APK execution/unbind markers required'
        (work / 'result.json').write_text(
            json.dumps(dict(results=results, evidence=completed), indent=2) + '\n'
        )
        print('SYNC_DISPATCH_APK_OK', json.dumps(results), flush=True)


if __name__ == '__main__':
    main()
