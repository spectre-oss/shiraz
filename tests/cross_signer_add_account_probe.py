# SPDX-License-Identifier: GPL-3.0-only
"""Two independently signed APKs exercise public account-add without account ownership."""

import json
import os
import subprocess
import tempfile
from pathlib import Path
from unittest.mock import patch

from shiraz.apk_verifier import verify_signatures
from shiraz.host_session import HostSession
from shiraz.package_registry import PackageRegistry

ROOT = Path(__file__).resolve().parents[1]


def main():
    work = ROOT / 'build/cross-signer-add-account-probe'
    work.mkdir(exist_ok=True)
    (work / 'result.json').unlink(missing_ok=True)
    with tempfile.TemporaryDirectory(dir=work) as directory:
        temp = Path(directory)
        registry = PackageRegistry(temp / 'registry')
        identities, signers = [], []
        for fixture in ('authenticator', 'activity-caller'):
            store, signed = temp / (fixture + '.p12'), temp / (fixture + '.apk')
            password = temp / (fixture + '.password')
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
                    'CN=Shiraz ' + fixture,
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
                    str(ROOT / 'build/macos-apk' / (fixture + '.apk')),
                    'fixture',
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            signers.append(verify_signatures(signed)['verified_signers_sha256'])
            identities.append(registry.enroll(signed, expected_current='absent'))
        assert signers[0] != signers[1], 'Fixture signers must differ'
        owner, caller = identities
        with patch.dict(
            os.environ,
            SHIRAZ_TEST_CROSS_ADD='1',
            SDL_VIDEODRIVER='dummy',
            SDL_RENDER_DRIVER='software',
        ):
            with HostSession(registry.root) as host:
                host.start(caller['package'], expected_sha256=caller['apk_sha256'])
                result = host.wait(timeout=40)
        (work / 'session.json').write_text(json.dumps(result, indent=2))
        assert len(result['apps']) == 1
        event = result['apps'][0]
        assert event['package'] == caller['package'] and event['exit_status'] == 0, result
        output = Path(event['stdout']).read_bytes() + Path(event['stderr']).read_bytes()
        (work / 'caller.log').write_bytes(output)
        assert b'CROSS_SIGNER_ADD_ACCOUNT_OK' in output, 'Inspect caller.log'
        account = dict(type='org.shiraz.fixture.account', name='created-by-provider')
        owned = registry.account_operation(
            owner['uid'], owner['apk_sha256'], dict(action='list', type=account['type'])
        )
        assert owned == [account], owned
        assert (
            registry.account_operation(
                caller['uid'], caller['apk_sha256'], dict(action='list', type=account['type'])
            )
            == []
        )
        try:
            registry.resolve_authenticator(caller['uid'], caller['apk_sha256'], account['type'])
        except PermissionError:
            pass
        else:
            raise AssertionError('General authenticator access granted')
        ui_sessions = []
        for mode in ('result', 'cancel', 'foreign', 'grants', 'private'):
            with patch.dict(
                os.environ,
                SHIRAZ_TEST_ACCOUNT_UI=mode,
                SDL_VIDEODRIVER='dummy',
                SDL_RENDER_DRIVER='software',
            ):
                with HostSession(registry.root) as host:
                    host.start(caller['package'], expected_sha256=caller['apk_sha256'])
                    ui = host.wait(timeout=40)
            (work / ('ui-' + mode + '.json')).write_text(json.dumps(ui, indent=2))
            events = [event for event in ui['apps'] if event['package'] == caller['package']]
            assert len(events) == 1 and events[0]['exit_status'] == 0, ui
            output = Path(events[0]['stdout']).read_bytes() + Path(events[0]['stderr']).read_bytes()
            (work / ('ui-' + mode + '.log')).write_bytes(output)
            assert ('ACCOUNT_UI_CALLER_OK mode=' + mode).encode() in output
            if mode in ('result', 'cancel'):
                targets = [event for event in ui['apps'] if event['package'] == owner['package']]
                assert len(targets) == 1 and targets[0]['exit_status'] == 0, ui
                assert ('ACCOUNT_UI_ACTIVITY_OK mode=ui-' + mode).encode() in Path(
                    targets[0]['stdout']
                ).read_bytes()
            ui_sessions.append(ui['work'])
        (work / 'result.json').write_text(
            json.dumps(
                dict(
                    ok=True, distinct_signers=True, session=result['work'], ui_sessions=ui_sessions
                ),
                indent=2,
            )
        )
        print('CROSS_SIGNER_ADD_ACCOUNT_APK_OK', flush=True)


if __name__ == '__main__':
    main()
