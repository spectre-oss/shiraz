# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import Barrier
from unittest.mock import patch

from shiraz.apk import FormatError
from shiraz.package_registry import PackageRegistry


class PackageRegistryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.registry = PackageRegistry(self.root / 'registry')
        self.sequence = 0

    def tearDown(self):
        self.temporary.cleanup()

    def test_sync_descriptors_are_recorded_only_after_successful_inspection(self):
        descriptor = dict(
            component='test.sync.Adapter', authority='test.data', account_type='test.account'
        )
        with patch(
            'shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]
        ) as inspect:
            record = self.enroll('test.sync', services=[dict(name='test.sync.Adapter')])
            inspected = inspect.call_args.args[0]
            self.assertNotEqual(inspected, self.root / 'input.apk')
        with self.registry.connect() as db:
            row = db.execute(
                'SELECT manifest FROM packages WHERE package=?', ('test.sync',)
            ).fetchone()
        self.assertEqual(json.loads(row['manifest'])['sync_adapters'], [descriptor])
        with patch(
            'shiraz.package_registry.inspect_sync_adapters',
            side_effect=FormatError('Invalid sync XML'),
        ):
            with self.assertRaisesRegex(FormatError, 'Invalid sync XML'):
                self.enroll('test.sync', version=2)
        self.assertEqual(self.registry.identity('test.sync')['sha256'], record['apk_sha256'])

    def test_sync_enumeration_limits_and_disabled_identity(self):
        caller = self.enroll('test.caller')
        descriptor = dict(
            component='test.Sync', authority='test.data', account_type='test.accounts'
        )
        service = dict(name='test.Sync')

        def query():
            return self.registry.list_sync_adapters(caller['uid'], caller['apk_sha256'])

        with patch(
            'shiraz.package_registry.inspect_sync_adapters',
            return_value=[dict(descriptor, authority=f"test.data{i}") for i in range(257)],
        ):
            self.enroll('test.sync', services=[service])
        with self.assertRaisesRegex(FormatError, 'count limit'):
            query()
        with patch(
            'shiraz.package_registry.inspect_sync_adapters',
            return_value=[dict(descriptor, authority='x' * 1020 + str(i)) for i in range(64)],
        ):
            self.enroll('test.sync', version=2, services=[service])
        with self.assertRaisesRegex(FormatError, 'reply size limit'):
            query()
        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]):
            self.enroll('test.sync', version=3, services=[service], application_enabled='false')
        self.assertEqual(query(), [])
        disabled = self.enroll('test.disabled', application_enabled='false')
        with self.assertRaisesRegex(PermissionError, 'disabled'):
            self.registry.list_sync_adapters(disabled['uid'], disabled['apk_sha256'])
        with self.assertRaises(PermissionError):
            self.registry.list_sync_adapters(99999, caller['apk_sha256'])
        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]):
            with self.assertRaisesRegex(FormatError, 'identity'):
                self.enroll('test.sync', version=4, services=[])

    def test_sync_owner_survives_update_restart_and_competition(self):
        caller = self.enroll('test.caller')
        descriptor = dict(
            component='test.Sync', authority='test.data', account_type='test.accounts'
        )
        service = dict(name='test.Sync')

        def query():
            return self.registry.list_sync_adapters(
                caller['uid'], caller['apk_sha256'], selected_only=True
            )

        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]):
            original = self.enroll('test.original', services=[service])
            replacement = self.enroll('test.replacement', signer='b', services=[service])
            updated = self.enroll('test.original', version=2, services=[service])
        self.assertEqual(query()[0]['apk_sha256'], updated['apk_sha256'])
        self.assertEqual(query()[0]['uid'], original['uid'])
        self.registry = PackageRegistry(self.root / 'registry')
        self.assertEqual(query()[0]['package'], 'test.original')
        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]):
            self.enroll('test.original', version=3, services=[dict(service, enabled='false')])
        self.assertEqual(query()[0]['package'], 'test.replacement')
        # Re-enabling the older UID must not steal the now-available owner.
        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]):
            self.enroll('test.original', version=4, services=[service])
        self.registry = PackageRegistry(self.root / 'registry')
        self.assertEqual(query()[0]['package'], 'test.replacement')
        self.registry.retire(
            'test.replacement',
            expected_uid=replacement['uid'],
            expected_sha256=replacement['apk_sha256'],
        )
        self.assertEqual(query()[0]['package'], 'test.original')
        # Ambiguous updates must roll back both package generation and owner state.
        previous = self.registry.identity('test.original')
        with patch(
            'shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor, descriptor]
        ):
            with self.assertRaisesRegex(FormatError, 'Duplicate sync'):
                self.enroll('test.original', version=5, services=[service])
        self.assertEqual(query()[0]['apk_sha256'], previous['sha256'])
        # Schema 13 migration reconstructs ownership deterministically.
        with self.registry.connect() as db:
            db.execute('DROP TABLE sync_adapter_owners')
            db.execute('PRAGMA user_version=13')
        self.registry = PackageRegistry(self.root / 'registry')
        self.assertEqual(query()[0]['package'], 'test.original')
        with self.registry.connect() as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 20)

    def enroll(
        self, package, signer='a', version=1, expected_package=None, expected_sha256=None, **extra
    ):
        # Registry unit tests isolate verifier/parser; real signatures have separate integration coverage.
        self.sequence += 1
        data = str(self.sequence).encode()
        apk = self.root / 'input.apk'
        apk.write_bytes(data)
        digest = hashlib.sha256(data).hexdigest()
        manifest = dict(package=package, version_code=str(version), **extra)
        with (
            patch(
                'shiraz.package_registry.verify_signatures',
                return_value={'apk_sha256': digest, 'verified_signers_sha256': [signer * 64]},
            ),
            patch(
                'shiraz.package_registry.inspect_apk',
                return_value={'apk_sha256': digest, 'manifest': manifest},
            ),
        ):
            return self.registry.enroll(
                apk, expected_package=expected_package, expected_sha256=expected_sha256
            )

    def test_job_admission_is_own_package_and_generation_bound(self):
        service = dict(
            name='test.jobs.Worker',
            exported='false',
            permission='android.permission.BIND_JOB_SERVICE',
            process=':jobs',
        )
        owner = self.enroll('test.jobs', services=[service])
        peer = self.enroll('test.peer', signer='a')
        result = self.registry.authorize_job(owner['uid'], owner['apk_sha256'], service['name'])
        self.assertEqual(
            result,
            dict(
                package='test.jobs',
                uid=owner['uid'],
                apk_sha256=owner['apk_sha256'],
                service=service,
            ),
        )
        with self.assertRaises(PermissionError):
            self.registry.authorize_job(peer['uid'], peer['apk_sha256'], service['name'])
        with self.assertRaises(PermissionError):
            self.registry.authorize_job(owner['uid'], peer['apk_sha256'], service['name'])
        replacement = self.enroll('test.jobs', version=2, services=[service])
        self.assertEqual(replacement['uid'], owner['uid'])
        with self.assertRaises(PermissionError):
            self.registry.authorize_job(owner['uid'], owner['apk_sha256'], service['name'])
        self.registry.authorize_job(replacement['uid'], replacement['apk_sha256'], service['name'])
        self.registry.retire(
            'test.jobs', expected_sha256=replacement['apk_sha256'], expected_uid=replacement['uid']
        )
        with self.assertRaises(PermissionError):
            self.registry.authorize_job(
                replacement['uid'], replacement['apk_sha256'], service['name']
            )

    def test_job_admission_rejects_invalid_or_unavailable_services(self):
        for index, flags in enumerate(
            (
                {},
                {'permission': 'test.OTHER'},
                {'permission': 'android.permission.BIND_JOB_SERVICE', 'enabled': 'false'},
                {'permission': 'android.permission.BIND_JOB_SERVICE', 'isolated': 'true'},
                {'permission': 'android.permission.BIND_JOB_SERVICE', 'externalService': 'true'},
            )
        ):
            package = 'test.job' + str(index)
            item = self.enroll(package, services=[dict(name=package + '.Worker', **flags)])
            with self.assertRaises((PermissionError, FormatError)):
                self.registry.authorize_job(item['uid'], item['apk_sha256'], package + '.Worker')
        disabled = self.enroll(
            'test.disabled',
            application_enabled='false',
            services=[
                dict(name='test.disabled.Worker', permission='android.permission.BIND_JOB_SERVICE')
            ],
        )
        with self.assertRaises(PermissionError):
            self.registry.authorize_job(
                disabled['uid'], disabled['apk_sha256'], 'test.disabled.Worker'
            )
        for uid, digest, component in (
            (True, 'a' * 64, 'test.Job'),
            (0, 'a' * 64, 'test.Job'),
            (10000, None, 'test.Job'),
            (10000, 'A' * 64, 'test.Job'),
            (10000, 'a' * 64, '../Job'),
            (10000, 'a' * 64, 'x' * 1025),
        ):
            with self.assertRaises(ValueError):
                self.registry.authorize_job(uid, digest, component)

    def test_enrollment_preconditions_preserve_registry_and_objects(self):
        first = self.enroll(
            'test.one',
            expected_package='test.one',
            expected_sha256=hashlib.sha256(b'1').hexdigest(),
        )
        objects = set(self.registry.objects.iterdir())
        for expected in ({'expected_package': 'test.other'}, {'expected_sha256': '0' * 64}):
            with self.assertRaisesRegex(FormatError, 'differs from enrollment request'):
                self.enroll('test.one', version=2, **expected)
            self.assertEqual(self.registry.identity('test.one')['sha256'], first['apk_sha256'])
            self.assertEqual(set(self.registry.objects.iterdir()), objects)
        with self.assertRaises(PermissionError):
            self.registry.identity('test.other')
        for expected in (
            {'expected_package': '../bad'},
            {'expected_sha256': 'bad'},
            {'expected_sha256': 'A' * 64},
            {'expected_current': ''},
            {'expected_current': 'unknown'},
        ):
            with self.assertRaisesRegex(FormatError, 'Invalid expected'):
                self.registry.enroll(self.root / 'missing.apk', **expected)

    def test_competing_enrollments_compare_current_state_inside_transaction(self):
        first = self.enroll('test.one')
        barrier = Barrier(2)
        apks = [self.root / 'two.apk', self.root / 'three.apk']
        for number, apk in enumerate(apks, 2):
            apk.write_bytes(str(number).encode())

        def verify(apk):
            barrier.wait(timeout=5)
            return dict(
                apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                verified_signers_sha256=['a' * 64],
            )

        def inspect(apk):
            return dict(
                apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                manifest=dict(package='test.one', version_code=apk.read_text()),
            )

        def enroll(apk):
            try:
                return self.registry.enroll(apk, expected_current=first['apk_sha256'])
            except FormatError as error:
                return str(error)

        with (
            patch('shiraz.package_registry.verify_signatures', side_effect=verify),
            patch('shiraz.package_registry.inspect_apk', side_effect=inspect),
            ThreadPoolExecutor(max_workers=2) as pool,
        ):
            results = list(pool.map(enroll, apks))
        winners = [result for result in results if isinstance(result, dict)]
        self.assertEqual(len(winners), 1)
        self.assertIn('Registered package changed since enrollment was prepared', results)
        self.assertEqual(self.registry.identity('test.one')['sha256'], winners[0]['apk_sha256'])
        self.assertEqual(winners[0]['uid'], first['uid'])
        self.assertEqual(len(list(self.registry.objects.iterdir())), 2)

    def test_permission_query_grants(self):
        owner = self.enroll(
            'test.owner',
            defined_permissions=[
                dict(name='test.NORMAL', protection=0),
                dict(name='test.SIGNATURE', protection=2),
                dict(name='test.DANGEROUS', protection=1),
                dict(name='test.PRIVILEGED', protection=18),
            ],
        )
        permissions = [
            'test.NORMAL',
            'test.SIGNATURE',
            'test.DANGEROUS',
            'test.PRIVILEGED',
            'test.UNKNOWN',
            'android.permission.INTERNET',
        ]
        match = self.enroll('test.match', permissions=permissions)
        other = self.enroll('test.other', signer='b', permissions=permissions)
        disabled = self.enroll(
            'test.disabled', permissions=permissions, application_enabled='false'
        )
        for kind in ('package', 'uid'):
            for subject in (owner, match, other, disabled):
                for permission in permissions:
                    expected = (
                        0
                        if subject in (match, other)
                        and (
                            permission == 'test.NORMAL'
                            or permission == 'test.SIGNATURE'
                            and subject == match
                        )
                        else -1
                    )
                    self.assertEqual(
                        self.registry.check_permission(
                            match['uid'], match['apk_sha256'], permission, kind, subject[kind]
                        ),
                        expected,
                    )
        self.assertEqual(
            self.registry.check_permission(
                match['uid'], match['apk_sha256'], 'test.NORMAL', 'uid', 0
            ),
            -1,
        )
        self.enroll('test.owner', version=2)  # removed definitions revoke queries too
        self.assertEqual(
            self.registry.check_permission(
                match['uid'], match['apk_sha256'], 'test.NORMAL', 'package', 'test.match'
            ),
            -1,
        )
        with self.assertRaisesRegex(PermissionError, 'disabled'):
            self.registry.check_permission(
                disabled['uid'], disabled['apk_sha256'], 'test.NORMAL', 'uid', match['uid']
            )

    def test_platform_declarations_do_not_become_package_grants(self):
        reserved = [
            'android.permission.INTERNET',
            'android.server.checkin.CHECKIN.permission.C2D_MESSAGE',
        ]
        definitions = [dict(name=name, protection=0) for name in reserved]
        first = self.enroll('test.companion', defined_permissions=definitions, permissions=reserved)
        second = self.enroll(
            'test.other', signer='b', defined_permissions=definitions, permissions=reserved
        )
        for subject in (first, second):
            for name in reserved:
                self.assertEqual(
                    self.registry.check_permission(
                        subject['uid'], subject['apk_sha256'], name, 'uid', subject['uid']
                    ),
                    -1,
                )
        with self.assertRaisesRegex(FormatError, 'duplicate'):
            self.enroll('test.duplicate', defined_permissions=definitions + definitions)

    def test_retirement_revokes_identity_and_permissions_without_reusing_uid(self):
        caller = self.enroll('test.caller', permissions=['test.NORMAL'])
        owner = self.enroll(
            'test.owner', defined_permissions=[dict(name='test.NORMAL', protection=0)]
        )
        self.assertEqual(
            self.registry.check_permission(
                caller['uid'], caller['apk_sha256'], 'test.NORMAL', 'uid', caller['uid']
            ),
            0,
        )
        objects = set(self.registry.objects.iterdir())
        for invalid in (True, None, 9999, 20000, '10001'):
            with self.assertRaisesRegex(FormatError, 'Invalid expected package UID'):
                self.registry.retire(
                    'test.owner', expected_uid=invalid, expected_sha256=owner['apk_sha256']
                )
        with self.assertRaisesRegex(FormatError, 'changed since retirement'):
            self.registry.retire('test.owner', expected_uid=owner['uid'], expected_sha256='0' * 64)
        self.assertEqual(self.registry.identity('test.owner')['uid'], owner['uid'])
        self.assertTrue(
            self.registry.retire(
                'test.owner', expected_uid=owner['uid'], expected_sha256=owner['apk_sha256']
            )
        )
        self.assertFalse(
            self.registry.retire(
                'test.owner', expected_uid=owner['uid'], expected_sha256=owner['apk_sha256']
            )
        )
        self.assertEqual(set(self.registry.objects.iterdir()), objects)
        with self.assertRaises(PermissionError):
            self.registry.identity('test.owner')
        with self.assertRaises(PermissionError):
            self.registry.lookup_package(owner['uid'], owner['apk_sha256'], package='test.caller')
        self.assertEqual(
            self.registry.lookup_package(caller['uid'], caller['apk_sha256'], uid=owner['uid']), []
        )
        self.assertEqual(
            self.registry.check_permission(
                caller['uid'], caller['apk_sha256'], 'test.NORMAL', 'uid', caller['uid']
            ),
            -1,
        )
        self.registry = PackageRegistry(self.registry.root)
        self.sequence -= (
            1  # Reinstall identical bytes, so the digest cannot detect the new identity.
        )
        replacement = self.enroll('test.owner')
        self.assertEqual(replacement['apk_sha256'], owner['apk_sha256'])
        self.assertGreater(replacement['uid'], owner['uid'])
        with self.assertRaisesRegex(FormatError, 'changed since retirement'):
            self.registry.retire(
                'test.owner', expected_uid=owner['uid'], expected_sha256=owner['apk_sha256']
            )
        self.assertEqual(self.registry.identity('test.owner')['uid'], replacement['uid'])

    def test_registry_v1_migration_preserves_existing_identity(self):
        original = self.enroll('test.original')
        with self.registry.connect() as db:
            db.execute('DROP TABLE retired_uids')
            db.execute('PRAGMA user_version=1')
        self.registry = PackageRegistry(self.registry.root)
        self.assertEqual(self.registry.identity('test.original')['uid'], original['uid'])
        self.assertTrue(
            self.registry.retire(
                'test.original',
                expected_uid=original['uid'],
                expected_sha256=original['apk_sha256'],
            )
        )
        self.assertGreater(self.enroll('test.next')['uid'], original['uid'])

    def test_uid_update_and_rejection(self):
        first = self.enroll('test.one')
        second = self.enroll('test.two')
        self.assertNotEqual(first['uid'], second['uid'])
        updated = self.enroll('test.one', version=2)
        self.assertEqual(updated['uid'], first['uid'])
        for kwargs in ({'signer': 'b', 'version': 3}, {'version': 1}, {'version': 2}):
            with self.assertRaises(FormatError):
                self.enroll('test.one', **kwargs)
        with self.registry.connect() as db:
            row = db.execute('SELECT * FROM packages WHERE package=?', ('test.one',)).fetchone()
            self.assertEqual(row['sha256'], updated['apk_sha256'])
            self.assertEqual(row['version'], 2)
        self.assertTrue(self.registry.launch_apk('test.one', updated['apk_sha256']).is_file())
        with self.assertRaises(FormatError):
            self.registry.launch_apk('test.one', first['apk_sha256'])
        reopened = PackageRegistry(self.root / 'registry')
        with reopened.connect() as db:
            self.assertEqual(
                db.execute('SELECT uid FROM packages WHERE package=?', ('test.one',)).fetchone()[0],
                first['uid'],
            )

    def test_generation_lease_blocks_replacement_but_allows_identical_enrollment(self):
        first = self.enroll('test.one')
        objects = set(self.registry.objects.iterdir())
        with self.registry.lease('test.one', first['apk_sha256']) as identity:
            self.assertEqual(identity['uid'], first['uid'])
            with self.registry.lease('test.one', first['apk_sha256']):
                with self.assertRaisesRegex(FormatError, 'generation is in use'):
                    self.enroll('test.one', version=2)
            self.assertEqual(self.registry.identity('test.one')['sha256'], first['apk_sha256'])
            self.assertEqual(set(self.registry.objects.iterdir()), objects)
            self.sequence = 0
            self.assertEqual(self.enroll('test.one'), first)
            self.registry.retire(
                'test.one', expected_sha256=first['apk_sha256'], expected_uid=first['uid']
            )
            with self.assertRaisesRegex(FormatError, 'generation is in use'):
                self.enroll('test.one')
        replacement = self.enroll('test.one')
        self.assertGreater(replacement['uid'], first['uid'])
        with self.assertRaisesRegex(FormatError, 'Lease APK does not match'):
            with self.registry.lease('test.one', first['apk_sha256']):
                self.fail('Stale lease accepted')
        self.enroll('test.one', version=2)

    def test_generation_lease_is_cross_process_and_released_after_owner_exit(self):
        first = self.enroll('test.one')
        code = '''import sys
from shiraz.package_registry import PackageRegistry
with PackageRegistry(sys.argv[1]).lease('test.one', sys.argv[2]):
    print('READY', flush=True)
    sys.stdin.read()
'''
        env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parents[1] / 'src'))
        child = subprocess.Popen(
            [sys.executable, '-c', code, str(self.registry.root), first['apk_sha256']],
            env=env,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        try:
            self.assertEqual(child.stdout.readline(), b'READY\n')
            with self.assertRaisesRegex(FormatError, 'generation is in use'):
                self.enroll('test.one', version=2)
            child.kill()
            child.wait(timeout=5)
            self.assertEqual(self.enroll('test.one', version=2)['uid'], first['uid'])
        finally:
            if child.poll() is None:
                child.kill()
            child.communicate(timeout=5)

    def test_launcher_authorization_exact_generations_and_policy(self):
        activity = dict(
            name='test.target.Main', enabled='true', exported='true', permission='test.LAUNCH'
        )
        fields = dict(
            activities=[activity],
            launcher_activities=[dict(name=activity['name'], target=activity['name'])],
            defined_permissions=[dict(name='test.LAUNCH', protection=2)],
        )
        target = self.enroll('test.target', **fields)
        caller = self.enroll('test.caller', permissions=['test.LAUNCH'])
        wrong = self.enroll('test.wrong', signer='b', permissions=['test.LAUNCH'])

        def authorize(source=caller, destination=target):
            return self.registry.authorize_launcher(
                source['uid'],
                'test.target',
                caller_sha256=source['apk_sha256'],
                target_sha256=destination['apk_sha256'],
            )

        self.assertEqual(authorize()['component'], activity['name'])
        with self.assertRaises(PermissionError):
            authorize(wrong)
        with self.assertRaisesRegex(PermissionError, 'generation changed'):
            authorize(dict(caller, apk_sha256='0' * 64))
        with self.assertRaisesRegex(PermissionError, 'generation changed'):
            authorize(destination=dict(target, apk_sha256='0' * 64))
        activity['exported'] = 'false'
        private = self.enroll('test.target', version=2, **fields)
        with self.assertRaisesRegex(PermissionError, 'not exported'):
            authorize(destination=private)
        self.assertEqual(authorize(private, private)['component'], activity['name'])
        activity['enabled'] = 'false'
        disabled = self.enroll('test.target', version=3, **fields)
        with self.assertRaisesRegex(PermissionError, 'unavailable'):
            authorize(disabled, disabled)
        activity['enabled'] = 'true'
        fields['launcher_activities'][0]['name'] = 'test.target.Alias'
        alias = self.enroll('test.target', version=4, **fields)
        with self.assertRaisesRegex(FormatError, 'alias policy'):
            authorize(destination=alias)
        fields['activity_aliases'] = [
            dict(name='test.target.Alias', target=activity['name'], exported='true')
        ]
        activity['enabled'] = 'false'
        public_alias = self.enroll('test.target', version=5, **fields)
        self.assertEqual(authorize(wrong, public_alias)['component'], activity['name'])
        fields['activity_aliases'][0]['permission'] = 'test.LAUNCH'
        protected_alias = self.enroll('test.target', version=6, **fields)
        with self.assertRaises(PermissionError):
            authorize(wrong, protected_alias)
        self.assertEqual(authorize(destination=protected_alias)['component'], activity['name'])
        fields['activity_aliases'][0]['enabled'] = 'false'
        disabled_alias = self.enroll('test.target', version=7, **fields)
        with self.assertRaisesRegex(PermissionError, 'unavailable'):
            authorize(destination=disabled_alias)
        absent = self.enroll('test.target', version=8)
        with self.assertRaisesRegex(PermissionError, 'no launcher'):
            authorize(destination=absent)

    def test_authenticator_owner_persists_and_replacement_has_distinct_identity(self):
        descriptor = dict(
            type='test.accounts',
            component='test.Auth',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        service = dict(name='test.Auth', enabled='true')
        caller = self.enroll('test.caller')

        def enroll_auth(package, **kwargs):
            with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
                return self.enroll(package, services=[service], **kwargs)

        def owner():
            return self.registry.list_authenticators(caller['uid'], caller['apk_sha256'])[0]

        first = enroll_auth('test.first')
        second = enroll_auth('test.second', signer='b')
        self.assertEqual(owner()['uid'], first['uid'])
        self.registry = PackageRegistry(self.root / 'registry')
        self.assertEqual(owner()['uid'], first['uid'])
        updated = enroll_auth('test.first', version=2)
        self.assertEqual(owner()['apk_sha256'], updated['apk_sha256'])
        disabled = enroll_auth('test.first', version=3, application_enabled='false')
        self.assertEqual(owner()['uid'], second['uid'])
        restored = enroll_auth('test.first', version=4)
        self.assertEqual(owner()['uid'], second['uid'])
        self.registry.retire(
            'test.second', expected_sha256=second['apk_sha256'], expected_uid=second['uid']
        )
        self.assertEqual(owner()['uid'], restored['uid'])
        self.assertNotEqual(owner()['uid'], second['uid'])
        self.assertNotEqual(disabled['apk_sha256'], owner()['apk_sha256'])
        # Removal of the descriptor vacates the type; an old APK hash cannot authorize it.
        self.enroll('test.first', version=5)
        self.assertEqual(self.registry.list_authenticators(caller['uid'], caller['apk_sha256']), [])

    def test_authenticator_owner_schema_migration_and_duplicate_rollback(self):
        descriptor = dict(
            type='test.accounts',
            component='test.Auth',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        service = dict(name='test.Auth')
        caller = self.enroll('test.caller')
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            original = self.enroll('test.auth', services=[service])
        with self.registry.connect() as db:
            db.execute('DROP TABLE authenticator_owners')
            db.execute('PRAGMA user_version=2')
        self.registry = PackageRegistry(self.root / 'registry')
        listed = self.registry.list_authenticators(caller['uid'], caller['apk_sha256'])
        self.assertEqual(listed[0]['uid'], original['uid'])
        with patch(
            'shiraz.package_registry.inspect_authenticators', return_value=[descriptor, descriptor]
        ):
            with self.assertRaisesRegex(FormatError, 'Duplicate authenticator'):
                self.enroll('test.auth', version=2, services=[service])
        self.assertEqual(self.registry.identity('test.auth')['sha256'], original['apk_sha256'])
        self.assertEqual(
            self.registry.list_authenticators(caller['uid'], caller['apk_sha256']), listed
        )

    def test_receiver_authorization_exact_identity_and_both_permission_directions(self):
        receiver = dict(
            name='test.target.Receiver', enabled='true', exported='true', permission='test.SEND'
        )
        fields = dict(
            receivers=[receiver],
            permissions=['test.RECEIVE'],
            defined_permissions=[dict(name='test.SEND', protection=2)],
        )
        target = self.enroll('test.target', **fields)
        caller = self.enroll(
            'test.caller',
            permissions=['test.SEND'],
            defined_permissions=[dict(name='test.RECEIVE', protection=2)],
        )
        wrong = self.enroll('test.wrong', signer='b', permissions=['test.SEND'])

        def authorize(source=caller, destination=target, permission=None):
            return self.registry.authorize_receiver(
                source['uid'],
                'test.target',
                receiver['name'],
                caller_sha256=source['apk_sha256'],
                target_sha256=destination['apk_sha256'],
                recipient_permission=permission,
            )

        self.assertEqual(authorize(permission='test.RECEIVE')['uid'], target['uid'])
        with self.assertRaises(PermissionError):
            authorize(wrong)
        for source, destination in (
            (dict(caller, apk_sha256='0' * 64), target),
            (caller, dict(target, apk_sha256='0' * 64)),
        ):
            with self.assertRaisesRegex(PermissionError, 'generation changed'):
                authorize(source, destination)
        with self.assertRaises(PermissionError):
            authorize(permission='test.MISSING')
        receiver['exported'] = 'false'
        private = self.enroll('test.target', version=2, **fields)
        with self.assertRaisesRegex(PermissionError, 'not exported'):
            authorize(destination=private)
        self.assertEqual(authorize(private, private)['receiver']['name'], receiver['name'])
        receiver['enabled'] = 'false'
        disabled = self.enroll('test.target', version=3, **fields)
        with self.assertRaisesRegex(PermissionError, 'unavailable'):
            authorize(disabled, disabled)
        receiver['enabled'] = '@bool/receiver_enabled'
        with self.assertRaises(FormatError):
            self.enroll('test.target', version=4, **fields)
        self.assertEqual(self.registry.identity('test.target')['sha256'], disabled['apk_sha256'])

    def test_receiver_discovery_filters_fanout_and_permission_directions(self):
        rule = dict(
            action=['test.EVENT'],
            category=['test.CAT'],
            scheme=['content'],
            mime=['text/*'],
            priority='4',
        )
        receivers = [
            dict(
                name='test.target.First',
                exported='true',
                permission='test.SEND',
                filters=[rule, rule],
            ),
            dict(name='test.target.Second', exported='true', filters=[dict(rule, priority='9')]),
            dict(name='test.target.Private', exported='false', filters=[rule]),
            dict(name='test.target.Disabled', exported='true', enabled='false', filters=[rule]),
        ]
        fields = dict(
            receivers=receivers,
            permissions=['test.RECEIVE'],
            defined_permissions=[dict(name='test.SEND', protection=2)],
        )
        target = self.enroll('test.target', **fields)
        caller = self.enroll(
            'test.caller',
            permissions=['test.SEND'],
            defined_permissions=[dict(name='test.RECEIVE', protection=2)],
        )
        other = self.enroll('test.other', signer='b', permissions=['test.SEND'])

        def query(source=caller, package='test.target', **changes):
            args = dict(mime='text/plain', scheme='content', has_data=True, categories=['test.CAT'])
            args.update(changes)
            return self.registry.query_receivers(
                source['uid'], package, 'test.EVENT', caller_sha256=source['apk_sha256'], **args
            )

        result = query(recipient_permission='test.RECEIVE')
        self.assertEqual(
            [item['component'] for item in result], ['test.target.Second', 'test.target.First']
        )
        self.assertTrue(all(item['apk_sha256'] == target['apk_sha256'] for item in result))
        self.assertEqual([item['component'] for item in query(other)], ['test.target.Second'])
        self.assertEqual(
            [item['component'] for item in query(other, recipient_permission='test.RECEIVE')],
            ['test.target.Second'],
        )
        self.assertEqual(query(recipient_permission='test.ABSENT'), [])
        for changes in (
            dict(categories=['test.OTHER']),
            dict(mime='image/png'),
            dict(scheme='https'),
        ):
            self.assertEqual(query(**changes), [])
        self.assertEqual(query(package='test.missing'), [])
        self.assertIn('test.target.Private', [item['component'] for item in query(target)])
        fields['permissions'] = []
        self.enroll('test.target', version=2, **fields)
        self.assertEqual(query(recipient_permission='test.RECEIVE'), [])
        rule['unsupported_data'] = True
        self.enroll('test.target', version=3, **fields)
        with self.assertRaisesRegex(FormatError, 'authority/path'):
            query()
        self.enroll('test.caller', version=2)
        with self.assertRaisesRegex(PermissionError, 'generation changed'):
            query()

    def test_exported_signature_and_permission_owner(self):
        service = dict(
            name='test.target.Service', enabled='true', exported='true', permission='test.SIGNATURE'
        )
        target = self.enroll(
            'test.target',
            services=[service],
            defined_permissions=[dict(name='test.SIGNATURE', protection=2)],
        )
        matching = self.enroll('test.match', permissions=['test.SIGNATURE'])
        wrong = self.enroll('test.wrong', signer='b', permissions=['test.SIGNATURE'])
        absent = self.enroll('test.absent')
        self.assertEqual(
            self.registry.authorize_service(matching['uid'], 'test.target', service['name'])['uid'],
            target['uid'],
        )
        for uid in (wrong['uid'], absent['uid'], 999):
            with self.assertRaises(PermissionError):
                self.registry.authorize_service(uid, 'test.target', service['name'])
        with self.assertRaises(FormatError):
            self.enroll(
                'test.hijack', defined_permissions=[dict(name='test.SIGNATURE', protection=0)]
            )
        service['exported'] = 'false'
        self.enroll(
            'test.target',
            version=2,
            services=[service],
            defined_permissions=[dict(name='test.SIGNATURE', protection=2)],
        )
        with self.assertRaises(PermissionError):
            self.registry.authorize_service(matching['uid'], 'test.target', service['name'])
        self.registry.authorize_service(target['uid'], 'test.target', service['name'])

    def test_unknown_dangerous_and_resource_flags_fail_closed(self):
        caller = self.enroll('test.caller', permissions=['test.GRANT'])
        service = dict(name='test.target.Service', exported='true', permission='test.GRANT')
        self.enroll('test.target', services=[service])
        with self.assertRaises(PermissionError):
            self.registry.authorize_service(caller['uid'], 'test.target', service['name'])
        self.enroll(
            'test.target',
            version=2,
            services=[service],
            defined_permissions=[dict(name='test.GRANT', protection=1)],
        )
        with self.assertRaises(PermissionError):
            self.registry.authorize_service(caller['uid'], 'test.target', service['name'])
        service['enabled'] = '@bool/enabled'
        previous = self.registry.identity('test.target')
        with self.assertRaises(FormatError):
            self.enroll('test.target', version=3, services=[service])
        self.assertEqual(self.registry.identity('test.target'), previous)


if __name__ == '__main__':
    unittest.main()
