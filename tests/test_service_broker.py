# SPDX-License-Identifier: GPL-3.0-only
import base64
import json
import os
import select
import socket
import struct
import subprocess
import sys
import threading
import unittest
from unittest.mock import Mock, patch

import test_package_registry as fixtures

from shiraz.broker_wire import read_packet, read_payload, write_payload
from shiraz.service_broker import (
    MAX_FRAME,
    RetirementCleanupError,
    ServiceBroker,
    read_frame,
    write_frame,
)
from shiraz.service_start_state import ServiceStartState


class ServiceBrokerTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.PackageRegistryTests()
        self.fixture.setUp()
        self.caller = self.fixture.enroll('test.caller', permissions=['test.PRIVATE'])
        self.target = self.fixture.enroll(
            'test.target',
            services=[dict(name='test.target.Worker', exported='true', permission='test.PRIVATE')],
            defined_permissions=[dict(name='test.PRIVATE', protection=2)],
        )
        self.other = self.fixture.enroll('test.other', signer='b', permissions=['test.PRIVATE'])
        self.broker = ServiceBroker(self.fixture.registry)
        self.channels = []
        self.request = dict(
            op='resolve_service', package='test.target', component='test.target.Worker'
        )

    def test_process_termination_wire_validates_and_pins_caller(self):
        callback = Mock()
        self.broker.process_terminator = callback
        with self.broker.open_session(
            'test.caller', self.caller['apk_sha256'], process_name='test.caller'
        ) as channel:
            for request in (
                dict(op='terminate_process', pid=True),
                dict(op='terminate_process', pid=0),
                dict(op='terminate_process', pid=123, uid=10000),
            ):
                write_frame(channel, request)
                self.assertFalse(read_frame(channel)['ok'])
            callback.assert_not_called()
            write_frame(channel, dict(op='terminate_process', pid=123))
            self.assertTrue(read_frame(channel)['ok'])
            identity, pid = callback.call_args.args
            self.assertEqual(
                (identity.package, identity.sha256, identity.process_name, pid),
                ('test.caller', self.caller['apk_sha256'], 'test.caller', 123),
            )
            callback.side_effect = PermissionError('unowned')
            write_frame(channel, dict(op='terminate_process', pid=124))
            self.assertFalse(read_frame(channel)['ok'])

    def tearDown(self):
        for channel in self.channels:
            channel.close()
        self.broker.close()
        self.fixture.tearDown()

    def channel(self, record):
        channel = self.broker.open_session(record['package'], record['apk_sha256'])
        self.channels.append(channel)
        return channel

    def exchange(self, channel, request):
        write_frame(channel, request)
        return read_frame(channel, idle_timeout=3)

    def watch_accounts(self, channel):
        write_frame(channel, {'op': 'watch_accounts'})
        reply, descriptors = read_packet(channel, 3)
        self.assertTrue(reply['ok'], reply)
        self.assertEqual(set(reply), {'ok', 'lease', 'revision'})
        self.assertEqual(len(descriptors), 1)
        endpoint = socket.socket(fileno=descriptors[0])
        self.channels.append(endpoint)
        return reply, endpoint

    def test_account_watch_cross_session_delivery_isolation_and_cleanup(self):
        descriptor = dict(
            type='test.accounts',
            component='test.Auth',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            owner = self.fixture.enroll('test.auth', services=[dict(name='test.Auth')])
        writer, peer, denied = (
            self.channel(owner),
            self.channel(self.caller),
            self.channel(self.other),
        )
        opened, events = self.watch_accounts(peer)
        other, hidden_events = self.watch_accounts(denied)
        snapshot_request = dict(op='account_operation', request=dict(action='snapshot'))
        self.assertEqual(
            opened['revision'], self.exchange(peer, snapshot_request)['result']['revision']
        )
        self.assertFalse(self.exchange(peer, {'op': 'watch_accounts'})['ok'])
        self.assertFalse(
            self.exchange(denied, {'op': 'unwatch_accounts', 'lease': opened['lease']})['ok']
        )
        add = dict(
            op='account_operation',
            request=dict(
                action='add', type='test.accounts', name='fixture', password=None, extras={}
            ),
        )
        self.assertTrue(self.exchange(writer, add)['result'])
        notice = read_frame(events, idle_timeout=3)
        self.assertEqual(set(notice), {'revision'})
        self.assertNotEqual(notice['revision'], opened['revision'])
        self.assertEqual(
            notice['revision'], self.exchange(peer, snapshot_request)['result']['revision']
        )
        self.assertFalse(select.select([hidden_events], [], [], 0.2)[0])
        self.assertEqual(
            other['revision'], self.exchange(denied, snapshot_request)['result']['revision']
        )
        self.assertTrue(
            self.exchange(peer, {'op': 'unwatch_accounts', 'lease': opened['lease']})['ok']
        )
        with self.assertRaises(EOFError):
            read_frame(events, idle_timeout=3)
        self.assertFalse(
            self.exchange(peer, {'op': 'unwatch_accounts', 'lease': opened['lease']})['ok']
        )
        reopened, fresh = self.watch_accounts(peer)
        self.assertEqual(reopened['revision'], notice['revision'])
        # No broker condition notification: simulate a second host registry writer.
        self.fixture.registry.account_operation(
            owner['uid'],
            owner['apk_sha256'],
            dict(action='remove', type='test.accounts', name='fixture'),
        )
        external_notice = read_frame(fresh, idle_timeout=3)
        self.assertNotEqual(external_notice['revision'], reopened['revision'])
        self.assertEqual(
            external_notice['revision'], self.exchange(peer, snapshot_request)['result']['revision']
        )
        peer.shutdown(socket.SHUT_RDWR)
        with self.assertRaises(EOFError):
            read_frame(fresh, idle_timeout=3)

    def test_account_watch_capacity_stale_identity_and_inbound_rejection(self):
        first, second, third = (
            self.channel(self.caller),
            self.channel(self.caller),
            self.channel(self.caller),
        )
        with patch('shiraz.account_watch.AccountWatch.LIMIT', 2):
            lease, one = self.watch_accounts(first)
            _, two = self.watch_accounts(second)
            self.assertFalse(self.exchange(third, {'op': 'watch_accounts'})['ok'])
            one.sendall(b'invalid guest input')
            with self.assertRaises((EOFError, ConnectionResetError)):
                read_frame(one, idle_timeout=3)
            self.assertTrue(
                self.exchange(first, {'op': 'unwatch_accounts', 'lease': lease['lease']})['ok']
            )
            _, replacement = self.watch_accounts(third)
            self.fixture.enroll('test.caller', version=2)
            for endpoint in (two, replacement):
                with self.assertRaises(EOFError):
                    read_frame(endpoint, idle_timeout=3)

    def test_account_watch_broker_shutdown_and_start_failure(self):
        peer = self.channel(self.caller)
        with patch(
            'shiraz.account_watch.AccountWatch.start',
            side_effect=RuntimeError('fixture worker unavailable'),
        ):
            self.assertFalse(self.exchange(peer, {'op': 'watch_accounts'})['ok'])
        self.assertEqual(len(self.broker.account_watches), 0)
        _, endpoint = self.watch_accounts(peer)
        self.broker.close()
        with self.assertRaises(EOFError):
            read_frame(endpoint, idle_timeout=3)
        self.assertEqual(len(self.broker.account_watches), 0)

    def test_account_operations_use_session_identity_and_preserve_channel_after_denial(self):
        descriptor = dict(
            type='test.accounts',
            component='test.Auth',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            owner = self.fixture.enroll('test.auth', services=[dict(name='test.Auth')])
        owner_channel, peer, denied = (
            self.channel(owner),
            self.channel(self.caller),
            self.channel(self.other),
        )

        def request(action, **fields):
            return dict(
                op='account_operation',
                request=dict(action=action, type='test.accounts', name='fixture-user', **fields),
            )

        snapshot = dict(op='account_operation', request=dict(action='snapshot'))
        initial_snapshot = self.exchange(peer, snapshot)
        other_snapshot = self.exchange(denied, snapshot)
        self.assertTrue(initial_snapshot['ok'])
        self.assertEqual(initial_snapshot['result']['accounts'], [])
        add = request('add', password='fixture-pw', extras={})
        self.assertEqual(self.exchange(owner_channel, add), {'ok': True, 'result': True})
        self.assertEqual(self.exchange(peer, add), {'ok': True, 'result': False})
        changed_snapshot = self.exchange(peer, snapshot)
        self.assertEqual(
            changed_snapshot['result']['accounts'],
            [dict(name='fixture-user', type='test.accounts')],
        )
        self.assertNotEqual(
            changed_snapshot['result']['revision'], initial_snapshot['result']['revision']
        )
        self.assertEqual(self.exchange(owner_channel, snapshot), changed_snapshot)
        self.assertEqual(self.exchange(denied, snapshot), other_snapshot)
        self.assertEqual(
            self.exchange(peer, request('get_password')), {'ok': True, 'result': 'fixture-pw'}
        )
        for invalid in (
            request('get_password'),
            dict(request('get_password'), caller_uid=owner['uid']),
            dict(
                op='account_operation',
                request=dict(
                    action='get_password',
                    name='fixture-user',
                    type='test.accounts',
                    uid=owner['uid'],
                ),
            ),
        ):
            reply = self.exchange(denied, invalid)
            self.assertFalse(reply['ok'])
            self.assertNotIn('fixture-pw', str(reply))
        self.assertTrue(self.exchange(denied, dict(op='list_authenticators'))['ok'])
        prepared = self.exchange(peer, request('prepare_token', key='fixture-token-type'))['result']
        self.assertEqual(prepared['owner_uid'], owner['uid'])
        self.assertIsNone(prepared['token'])
        commit = request(
            'commit_token',
            key='fixture-token-type',
            value='fixture-cached-token',
            expected_uid=prepared['owner_uid'],
            expected_sha256=prepared['owner_sha256'],
            revision=prepared['revision'],
        )
        self.assertFalse(self.exchange(denied, commit)['ok'])
        self.assertEqual(self.exchange(peer, commit), {'ok': True, 'result': True})
        self.assertEqual(self.exchange(peer, commit), {'ok': True, 'result': False})
        self.assertEqual(
            self.exchange(owner_channel, request('prepare_token', key='fixture-token-type'))[
                'result'
            ]['token'],
            'fixture-cached-token',
        )
        self.assertEqual(
            self.exchange(
                peer, request('set_token', key='fixture-token-type', value='fixture-token')
            ),
            {'ok': True, 'result': None},
        )
        self.assertEqual(
            self.exchange(owner_channel, request('get_token', key='fixture-token-type'))['result'],
            'fixture-token',
        )
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(peer, request('get_password'))['ok'])
        self.assertFalse(self.exchange(peer, snapshot)['ok'])
        self.assertTrue(self.exchange(owner_channel, request('remove'))['result'])
        removed_snapshot = self.exchange(owner_channel, snapshot)
        self.assertEqual(removed_snapshot['result']['accounts'], [])
        self.assertNotEqual(
            removed_snapshot['result']['revision'], initial_snapshot['result']['revision']
        )
        self.assertIsNone(self.exchange(owner_channel, request('get_password'))['result'])

    @patch('shiraz.account_store.time.time_ns', return_value=1000 * 1000000)
    def test_custom_token_cache_is_bound_to_broker_caller(self, clock):
        descriptor = dict(
            type='test.accounts',
            component='test.Auth',
            custom_tokens=True,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            owner = self.fixture.enroll('test.auth', services=[dict(name='test.Auth')])
        owner_channel, peer, denied = (
            self.channel(owner),
            self.channel(self.caller),
            self.channel(self.other),
        )

        def request(action, **fields):
            return dict(
                op='account_operation',
                request=dict(action=action, type='test.accounts', name='fixture-user', **fields),
            )

        self.assertTrue(
            self.exchange(owner_channel, request('add', password=None, extras={}))['result']
        )
        prepare = request('prepare_custom_token', key='custom-type')
        prepared = self.exchange(peer, prepare)['result']
        commit = request(
            'commit_custom_token',
            key='custom-type',
            value='synthetic-custom-token',
            expires_ms=2000,
            expected_uid=prepared['owner_uid'],
            expected_sha256=prepared['owner_sha256'],
            revision=prepared['revision'],
        )
        self.assertFalse(self.exchange(denied, commit)['ok'])
        self.assertFalse(self.exchange(peer, dict(commit, caller_uid=owner['uid']))['ok'])
        self.assertEqual(self.exchange(peer, commit), dict(ok=True, result=True))
        self.assertEqual(self.exchange(peer, prepare)['result']['token'], 'synthetic-custom-token')
        self.assertIsNone(self.exchange(owner_channel, prepare)['result']['token'])
        self.assertIsNone(
            self.exchange(peer, request('prepare_token', key='custom-type'))['result']['token']
        )
        clock.return_value = 2000 * 1000000
        self.assertIsNone(self.exchange(peer, prepare)['result']['token'])

    def test_activity_admission_pins_identity_and_revokes_channel_leases(self):
        activity = dict(name='test.target.Login', exported='true')
        target = self.fixture.enroll('test.target', version=2, activities=[activity])
        caller, stranger = self.channel(self.caller), self.channel(self.other)
        peers = []

        def launch(identity, resolved):
            self.assertEqual(identity.uid, self.caller['uid'])
            self.assertEqual(identity.sha256, self.caller['apk_sha256'])
            self.assertEqual(resolved['apk_sha256'], target['apk_sha256'])
            left, right = socket.socketpair()
            right.settimeout(2)
            peers.append(right)
            self.addCleanup(right.close)
            return left

        self.broker.activity_launcher = Mock(side_effect=launch)
        request = dict(
            op='open_activity',
            package='test.target',
            component=activity['name'],
            expected_uid=target['uid'],
            expected_sha256=target['apk_sha256'],
        )
        for bad in (
            dict(request, caller_uid=self.caller['uid']),
            dict(request, expected_uid=True),
            dict(request, expected_sha256='0' * 64),
            dict(request, expected_uid=target['uid'] + 1),
        ):
            self.assertFalse(self.exchange(caller, bad)['ok'])
        self.broker.activity_launcher.assert_not_called()
        endpoints, leases = [], []
        for _ in range(4):
            write_frame(caller, request)
            reply, descriptors = read_packet(caller, 3)
            self.assertTrue(reply['ok'])
            self.assertEqual(len(descriptors), 1)
            endpoint = socket.socket(fileno=descriptors[0])
            self.addCleanup(endpoint.close)
            endpoints.append(endpoint)
            leases.append(reply['lease'])
        self.assertFalse(self.exchange(caller, request)['ok'])
        self.assertEqual(self.broker.activity_launcher.call_count, 4)
        self.assertFalse(
            self.exchange(stranger, dict(op='release_activity', lease=leases[0]))['ok']
        )
        endpoints[0].sendall(b'x')
        self.assertEqual(peers[0].recv(1), b'x')
        self.assertTrue(self.exchange(caller, dict(op='release_activity', lease=leases[0]))['ok'])
        self.assertEqual(peers[0].recv(1), b'')
        self.assertFalse(self.exchange(caller, dict(op='release_activity', lease=leases[0]))['ok'])
        caller.close()
        for peer in peers[1:]:
            self.assertEqual(peer.recv(1), b'')

    def test_activity_late_quiescence_revokes_unpublished_channel(self):
        target = self.fixture.enroll(
            'test.target', version=2, activities=[dict(name='test.target.Login', exported='true')]
        )
        left, right = socket.socketpair()
        self.addCleanup(right.close)
        right.settimeout(2)

        def launch(identity, resolved):
            self.broker.quiesced.add(target['package'])
            return left

        self.broker.activity_launcher = launch
        reply = self.exchange(
            self.channel(self.caller),
            dict(
                op='open_activity',
                package='test.target',
                component='test.target.Login',
                expected_uid=target['uid'],
                expected_sha256=target['apk_sha256'],
            ),
        )
        self.assertFalse(reply['ok'])
        self.assertEqual(right.recv(1), b'')
        self.assertEqual(left.fileno(), -1)

    def test_activity_resolution_authorizes_explicit_target_without_launch(self):
        activity = dict(
            name='test.target.Login',
            exported='true',
            permission='test.PRIVATE',
            process=':login',
            launch_mode=1,
        )
        fields = dict(
            activities=[activity], defined_permissions=[dict(name='test.PRIVATE', protection=2)]
        )
        target = self.fixture.enroll('test.target', version=2, **fields)
        caller, wrong = self.channel(self.caller), self.channel(self.other)
        request = dict(op='resolve_activity', package='test.target', component='test.target.Login')
        resolved = self.exchange(caller, request)
        self.assertEqual(
            resolved,
            dict(
                ok=True,
                activity=dict(
                    package='test.target',
                    uid=target['uid'],
                    apk_sha256=target['apk_sha256'],
                    requested_component=activity['name'],
                    component=activity['name'],
                    process=':login',
                    launch_mode=1,
                ),
            ),
        )
        self.assertFalse(self.exchange(wrong, request)['ok'])
        for invalid in (
            dict(request, caller_uid=target['uid']),
            dict(request, component='../Login'),
            dict(request, component='test.target.Missing'),
        ):
            self.assertFalse(self.exchange(caller, invalid)['ok'])
        for package in (self.caller['package'], target['package']):
            self.broker.quiesced.add(package)
            self.assertFalse(self.exchange(caller, request)['ok'])
            self.broker.quiesced.remove(package)
        activity['exported'] = 'false'
        target = self.fixture.enroll('test.target', version=3, **fields)
        self.assertFalse(self.exchange(caller, request)['ok'])
        own = self.channel(target)
        self.assertTrue(self.exchange(own, request)['ok'])
        activity['enabled'] = 'false'
        self.fixture.enroll('test.target', version=4, **fields)
        self.assertFalse(self.exchange(caller, request)['ok'])
        # An alias carries its own export/enabled/permission policy.
        fields['activity_aliases'] = [
            dict(
                name='test.target.LoginAlias',
                target=activity['name'],
                exported='true',
                permission='test.PRIVATE',
            )
        ]
        self.fixture.enroll('test.target', version=5, **fields)
        alias_request = dict(request, component='test.target.LoginAlias')
        alias = self.exchange(caller, alias_request)
        self.assertTrue(alias['ok'])
        self.assertEqual(alias['activity']['component'], activity['name'])
        self.assertEqual(alias['activity']['requested_component'], 'test.target.LoginAlias')
        self.assertFalse(self.exchange(wrong, alias_request)['ok'])
        fields['activity_aliases'][0].pop('permission')
        self.fixture.enroll('test.target', version=6, **fields)
        self.assertTrue(self.exchange(wrong, alias_request)['ok'])
        fields['activity_aliases'][0]['enabled'] = 'false'
        self.fixture.enroll('test.target', version=7, **fields)
        self.assertFalse(self.exchange(caller, alias_request)['ok'])
        fields['activity_aliases'][0]['enabled'] = 'true'
        fields['activity_aliases'][0]['target'] = 'test.target.Missing'
        self.fixture.enroll('test.target', version=8, **fields)
        self.assertFalse(self.exchange(caller, alias_request)['ok'])
        activity['enabled'] = 'true'
        activity['exported'] = 'true'
        self.fixture.enroll('test.target', version=9, application_enabled='false', **fields)
        self.assertFalse(self.exchange(caller, request)['ok'])
        self.fixture.enroll('test.target', version=10, **fields)
        self.assertTrue(self.exchange(caller, request)['ok'])
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(caller, request)['ok'])

    def test_job_definition_validation_is_authenticated_and_read_only(self):
        service = dict(
            name='test.jobs.Worker',
            exported='false',
            permission='android.permission.BIND_JOB_SERVICE',
        )
        owner = self.fixture.enroll('test.jobs', services=[service])
        definition = dict(
            version=1,
            id=17,
            component=service['name'],
            persisted=True,
            minimum_latency_ms=0,
            deadline_ms=None,
            interval_ms=900000,
            flex_ms=300000,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={'long': dict(type='long', value=(1 << 63) - 1)},
        )
        channel, peer = self.channel(owner), self.channel(self.caller)
        request = dict(op='validate_job', definition=definition)
        reply = self.exchange(channel, request)
        self.assertEqual(reply['definition'], definition)
        self.assertEqual(reply['job']['apk_sha256'], owner['apk_sha256'])
        self.assertFalse(self.exchange(peer, request)['ok'])
        for invalid in (
            dict(request, uid=owner['uid']),
            dict(request, definition=dict(definition, network=1)),
            dict(
                request, definition=dict(definition, extras={'bad': dict(type='int', value=True)})
            ),
        ):
            self.assertFalse(self.exchange(channel, invalid)['ok'])
        self.assertEqual(self.exchange(channel, request), reply)
        self.fixture.enroll('test.jobs', version=2, services=[service])
        self.assertFalse(self.exchange(channel, request)['ok'])
        self.assertEqual(len(self.broker.processes), 0)

    def test_job_enqueue_uses_authenticated_owner_and_atomic_payload(self):
        service = dict(name='test.jobs.Worker', permission='android.permission.BIND_JOB_SERVICE')
        owner = self.fixture.enroll('test.jobs', services=[service])
        channel, peer = self.channel(owner), self.channel(self.caller)
        definition = dict(
            version=1,
            id=19,
            component=service['name'],
            persisted=False,
            minimum_latency_ms=0,
            deadline_ms=None,
            interval_ms=0,
            flex_ms=0,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={},
        )
        request = dict(op='enqueue_job', definition=definition, payload='cGFyY2Vs')
        admitted = self.exchange(channel, request)
        self.assertTrue(admitted['ok'])
        self.assertEqual(self.exchange(channel, request)['revision'], admitted['revision'])
        self.assertFalse(self.exchange(peer, request)['ok'])
        for invalid in (
            dict(request, uid=owner['uid']),
            dict(request, payload='%%%'),
            dict(request, payload=''),
            dict(request, payload='YQ==='),
            dict(request, definition=dict(definition, persisted=True)),
        ):
            self.assertFalse(self.exchange(channel, invalid)['ok'])
        with self.fixture.registry.connect() as db:
            items = db.execute('SELECT owner_uid,payload FROM job_work_items').fetchall()
            self.assertEqual(
                [(row['owner_uid'], row['payload']) for row in items],
                [(owner['uid'], b'parcel')] * 2,
            )
        self.broker.quiesced.add(owner['package'])
        self.assertFalse(self.exchange(channel, request)['ok'])
        self.broker.quiesced.clear()

    def test_persisted_job_requests_are_owned_bounded_and_durable(self):
        service = dict(name='test.jobs.Worker', permission='android.permission.BIND_JOB_SERVICE')
        owner = self.fixture.enroll(
            'test.jobs',
            services=[service],
            permissions=['android.permission.RECEIVE_BOOT_COMPLETED'],
        )
        channel, peer = self.channel(owner), self.channel(self.caller)
        definition = dict(
            version=1,
            id=17,
            component=service['name'],
            persisted=True,
            minimum_latency_ms=0,
            deadline_ms=None,
            interval_ms=0,
            flex_ms=0,
            initial_backoff_ms=30000,
            backoff_policy=1,
            extras={},
        )
        request = dict(op='schedule_job', definition=definition)
        admitted = self.exchange(channel, request)
        self.assertTrue(admitted['ok'])
        self.assertFalse(admitted['cleanup_pending'])
        self.assertEqual(self.exchange(channel, dict(op='list_job_ids')), dict(ok=True, ids=[17]))
        self.assertEqual(
            self.exchange(channel, dict(op='get_job', id=17)), dict(ok=True, definition=definition)
        )
        self.assertEqual(
            self.exchange(peer, dict(op='get_job', id=17)), dict(ok=True, definition=None)
        )
        self.assertFalse(self.exchange(peer, request)['ok'])
        self.assertFalse(self.exchange(peer, dict(op='cancel_job', id=17))['removed'])
        for invalid in (
            dict(request, uid=owner['uid']),
            dict(op='cancel_job', id=True),
            dict(op='get_job', id=17, package=owner['package']),
        ):
            self.assertFalse(self.exchange(channel, invalid)['ok'])
        self.assertEqual(self.exchange(channel, dict(op='list_job_ids'))['ids'], [17])
        unicode_definition = dict(
            definition, extras={'value': dict(type='string', value='😀' * 7000)}
        )
        write_payload(
            channel,
            json.dumps(
                dict(op='schedule_job', definition=unicode_definition), ensure_ascii=False
            ).encode(),
        )
        self.assertTrue(read_frame(channel, idle_timeout=3)['ok'])
        self.assertEqual(
            self.exchange(channel, dict(op='get_job', id=17))['definition'], unicode_definition
        )
        self.broker.quiesced.add(owner['package'])
        self.assertFalse(self.exchange(channel, request)['ok'])
        self.broker.quiesced.clear()
        self.assertTrue(self.exchange(channel, dict(op='cancel_job', id=17))['removed'])
        self.assertIsNone(self.exchange(channel, dict(op='get_job', id=17))['definition'])
        updated = self.fixture.enroll('test.jobs', version=2, services=[service])
        self.assertFalse(self.exchange(channel, request)['ok'])
        updated_channel = self.channel(updated)
        self.assertFalse(self.exchange(updated_channel, request)['ok'])
        transient = dict(definition, persisted=False)
        self.assertTrue(
            self.exchange(updated_channel, dict(op='schedule_job', definition=transient))['ok']
        )
        self.assertEqual(
            self.exchange(updated_channel, dict(op='get_job', id=17))['definition'], transient
        )
        self.assertFalse(
            self.exchange(updated_channel, dict(op='cancel_all_jobs', uid=owner['uid']))['ok']
        )
        self.assertEqual(
            self.exchange(peer, dict(op='cancel_all_jobs')),
            dict(ok=True, removed=False, cleanup_pending=False),
        )
        self.assertIsNotNone(
            self.exchange(updated_channel, dict(op='get_job', id=17))['definition']
        )
        self.assertEqual(
            self.exchange(updated_channel, dict(op='cancel_all_jobs')),
            dict(ok=True, removed=True, cleanup_pending=False),
        )
        self.assertEqual(self.exchange(updated_channel, dict(op='list_job_ids'))['ids'], [])

    def test_job_resolution_uses_session_identity_and_current_generation(self):
        service = dict(
            name='test.jobs.Worker',
            exported='false',
            permission='android.permission.BIND_JOB_SERVICE',
            process=':jobs',
        )
        owner = self.fixture.enroll('test.jobs', services=[service])
        channel, peer = self.channel(owner), self.channel(self.caller)
        request = dict(op='resolve_job', component=service['name'])
        expected = dict(
            ok=True,
            job=dict(
                package=owner['package'],
                uid=owner['uid'],
                apk_sha256=owner['apk_sha256'],
                component=service['name'],
                process=':jobs',
            ),
        )
        self.assertEqual(self.exchange(channel, request), expected)
        self.assertFalse(self.exchange(peer, request)['ok'])
        for invalid in (
            dict(request, package=owner['package']),
            dict(request, uid=owner['uid']),
            dict(request, apk_sha256=owner['apk_sha256']),
            dict(request, component=None),
            dict(op='resolve_job'),
            dict(request, component='../Worker'),
        ):
            self.assertFalse(self.exchange(channel, invalid)['ok'])
        self.assertEqual(self.exchange(channel, request), expected)
        with self.broker.lock:
            self.broker.quiesced.add(owner['package'])
        self.assertFalse(self.exchange(channel, request)['ok'])
        with self.broker.lock:
            self.broker.quiesced.remove(owner['package'])
        replacement = self.fixture.enroll('test.jobs', version=2, services=[service])
        self.assertFalse(self.exchange(channel, request)['ok'])
        current = self.channel(replacement)
        self.assertEqual(
            self.exchange(current, request)['job']['apk_sha256'], replacement['apk_sha256']
        )
        self.fixture.registry.retire(
            'test.jobs', expected_uid=replacement['uid'], expected_sha256=replacement['apk_sha256']
        )
        self.assertFalse(self.exchange(current, request)['ok'])
        self.assertEqual(len(self.broker.processes), 0)

    def test_authenticator_resolution_uses_managed_owner_without_service_grant(self):
        descriptor = dict(
            type='test.accounts',
            component='test.auth.Service',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        service = dict(
            name='test.auth.Service',
            exported='true',
            permission='android.permission.BIND_ACCOUNT_AUTHENTICATOR',
            process=':accounts',
        )
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            owner = self.fixture.enroll('test.auth', services=[service])
            replacement = self.fixture.enroll('test.replacement', signer='b', services=[service])
        peer, other = self.channel(self.caller), self.channel(self.other)
        request = dict(op='resolve_authenticator', type='test.accounts')
        resolved = self.exchange(peer, request)
        self.assertEqual(
            resolved,
            dict(
                ok=True,
                authenticator=dict(
                    descriptor,
                    package=owner['package'],
                    uid=owner['uid'],
                    apk_sha256=owner['apk_sha256'],
                    process=':accounts',
                ),
            ),
        )
        self.assertFalse(self.exchange(other, request)['ok'])
        for invalid in (
            dict(request, caller_uid=owner['uid']),
            dict(request, package=replacement['package']),
            dict(request, type=None),
            dict(request, type=''),
            dict(request, type='missing'),
        ):
            self.assertFalse(self.exchange(peer, invalid)['ok'])
        # Resolution does not bypass ordinary service permissions or start a worker.
        self.assertFalse(
            self.exchange(
                peer, dict(op='open_service', package=owner['package'], component=service['name'])
            )['ok']
        )
        self.assertEqual(len(self.broker.processes), 0)
        with self.broker.lock:
            self.broker.quiesced.add(owner['package'])
        self.assertFalse(self.exchange(peer, request)['ok'])
        with self.broker.lock:
            self.broker.quiesced.remove(owner['package'])
        self.fixture.registry.retire(
            owner['package'], expected_uid=owner['uid'], expected_sha256=owner['apk_sha256']
        )
        self.assertFalse(self.exchange(peer, request)['ok'])
        self.assertEqual(self.exchange(other, request)['authenticator']['uid'], replacement['uid'])
        self.fixture.enroll('test.other', signer='b', version=2)
        self.assertFalse(self.exchange(other, request)['ok'])
        self.assertEqual(len(self.broker.processes), 0)

    def test_authenticator_enumeration_identity_enabled_state_and_conflicts(self):
        caller = self.channel(self.caller)
        request = dict(op='list_authenticators')
        self.assertEqual(self.exchange(caller, request), {'ok': True, 'authenticators': []})
        descriptor = dict(
            type='test.accounts',
            component='test.auth.Service',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        service = dict(name='test.auth.Service', enabled='true', process=':accounts')
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            enrolled = self.fixture.enroll('test.auth', services=[service])
        reply = self.exchange(caller, request)
        self.assertTrue(reply['ok'])
        self.assertEqual(
            reply['authenticators'],
            [
                dict(
                    descriptor,
                    package='test.auth',
                    uid=enrolled['uid'],
                    apk_sha256=enrolled['apk_sha256'],
                    process=':accounts',
                )
            ],
        )
        self.assertFalse(self.exchange(caller, dict(request, caller_uid=enrolled['uid']))['ok'])
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            self.fixture.enroll('test.auth', version=2, services=[dict(service, enabled='false')])
        self.assertEqual(self.exchange(caller, request)['authenticators'], [])
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            self.fixture.enroll(
                'test.auth', version=3, application_enabled='false', services=[service]
            )
        self.assertEqual(self.exchange(caller, request)['authenticators'], [])
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            self.fixture.enroll('test.auth', version=4, services=[service])
            self.fixture.enroll('test.conflict', services=[service])
        reply = self.exchange(caller, request)
        self.assertTrue(reply['ok'])
        self.assertEqual(reply['authenticators'][0]['package'], 'test.auth')
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(caller, request)['ok'])
        self.assertFalse(self.broker.processes)

    def test_provider_resolution_channel_identity_and_generation(self):
        declaration = dict(
            name='test.provider.Data',
            authorities='test.data;test.alias',
            exported='true',
            access_metadata_version=1,
            process=':data',
            writePermission='test.WRITE',
        )
        owner = self.fixture.enroll(
            'test.provider',
            providers=[declaration],
            defined_permissions=[dict(name='test.WRITE', protection=2)],
        )
        caller = self.channel(self.caller)
        request = dict(op='resolve_provider', authority='test.alias', access='read')
        reply = self.exchange(caller, request)
        self.assertEqual(
            reply,
            dict(
                ok=True,
                provider=dict(
                    package='test.provider',
                    uid=owner['uid'],
                    apk_sha256=owner['apk_sha256'],
                    authority='test.alias',
                    access='read',
                    component='test.provider.Data',
                    process=':data',
                ),
            ),
        )
        for invalid in (
            dict(request, uid=owner['uid']),
            dict(request, package='test.provider'),
            dict(request, access='call'),
            dict(request, authority='content://test.data'),
        ):
            self.assertFalse(self.exchange(caller, invalid)['ok'])
        self.assertFalse(self.exchange(caller, dict(request, access='write'))['ok'])
        self.assertTrue(self.exchange(caller, request)['ok'])
        with self.broker.lock:
            self.broker.quiesced.add('test.provider')
        self.assertFalse(self.exchange(caller, request)['ok'])
        with self.broker.lock:
            self.broker.quiesced.remove('test.provider')
        update = self.fixture.enroll(
            'test.provider',
            version=2,
            providers=[declaration],
            defined_permissions=[dict(name='test.WRITE', protection=2)],
        )
        self.assertEqual(
            self.exchange(caller, request)['provider']['apk_sha256'], update['apk_sha256']
        )
        self.fixture.registry.retire(
            'test.provider', expected_uid=update['uid'], expected_sha256=update['apk_sha256']
        )
        self.assertFalse(self.exchange(caller, request)['ok'])
        with self.broker.lock:
            self.broker.quiesced.add('test.caller')
        self.assertFalse(self.exchange(caller, request)['ok'])
        with self.broker.lock:
            self.broker.quiesced.remove('test.caller')
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(caller, request)['ok'])
        self.assertFalse(self.broker.processes)

    def test_sync_adapter_discovery_generation_policy_and_no_dispatch(self):
        descriptor = dict(
            component='test.Sync', authority='test.data', account_type='test.accounts'
        )
        service = dict(name='test.Sync', exported='false', process=':sync')
        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]):
            first = self.fixture.enroll('test.sync', services=[service])
            second = self.fixture.enroll('test.competitor', signer='b', services=[service])
        caller = self.channel(self.caller)
        request = dict(op='list_sync_adapters')
        reply = self.exchange(caller, request)
        self.assertTrue(reply['ok'])
        self.assertEqual(
            [item['package'] for item in reply['sync_adapters']], ['test.competitor', 'test.sync']
        )
        self.assertEqual(
            reply['sync_adapters'][1],
            dict(
                descriptor,
                package='test.sync',
                uid=first['uid'],
                apk_sha256=first['apk_sha256'],
                process=':sync',
            ),
        )
        selected_request = dict(op='list_selected_sync_adapters')
        self.assertEqual(
            [item['package'] for item in self.exchange(caller, selected_request)['sync_adapters']],
            ['test.sync'],
        )
        self.assertFalse(self.exchange(caller, dict(request, uid=first['uid']))['ok'])
        self.assertFalse(
            self.exchange(
                caller, dict(op='open_service', package='test.sync', component='test.Sync')
            )['ok']
        )
        with self.broker.lock:
            self.broker.quiesced.add('test.sync')
        self.assertEqual(len(self.exchange(caller, request)['sync_adapters']), 1)
        self.assertEqual(self.exchange(caller, selected_request)['sync_adapters'], [])
        with self.broker.lock:
            self.broker.quiesced.remove('test.sync')
        with patch('shiraz.package_registry.inspect_sync_adapters', return_value=[descriptor]):
            self.fixture.enroll('test.sync', version=2, services=[dict(service, enabled='false')])
        self.assertEqual(len(self.exchange(caller, request)['sync_adapters']), 1)
        self.assertEqual(
            [item['package'] for item in self.exchange(caller, selected_request)['sync_adapters']],
            ['test.competitor'],
        )
        self.fixture.registry.retire(
            'test.competitor', expected_uid=second['uid'], expected_sha256=second['apk_sha256']
        )
        self.assertEqual(self.exchange(caller, request)['sync_adapters'], [])
        with self.broker.lock:
            self.broker.quiesced.add('test.caller')
        self.assertFalse(self.exchange(caller, request)['ok'])
        with self.broker.lock:
            self.broker.quiesced.remove('test.caller')
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(caller, request)['ok'])
        self.assertFalse(self.broker.processes)

    def test_dynamic_receiver_registration_ownership_limits_and_disconnect(self):
        channel = self.broker.open_session(
            self.caller['package'], self.caller['apk_sha256'], process_name=':events'
        )
        self.channels.append(channel)
        key = (self.caller['package'], self.caller['apk_sha256'], 'test.caller:events')
        server = self.broker.live_processes[key]
        worker = self.broker.sessions[server]
        request = dict(
            op='register_dynamic_receiver',
            receiver='a' * 32,
            permission='test.PRIVATE',
            flags=2,
            filter=dict(action=['test.EVENT'], category=[], mime=[], scheme=[], priority=0),
        )
        self.assertFalse(self.exchange(self.channel(self.caller), request)['ok'])
        first = self.exchange(channel, request)
        second = self.exchange(
            channel, dict(request, filter=dict(request['filter'], action=['test.OTHER']))
        )
        self.assertTrue(first['ok'])
        self.assertNotEqual(first['registration'], second['registration'])
        self.assertEqual(len(self.broker.dynamic_receivers[server]), 2)
        remove = dict(op='unregister_dynamic_receiver', receiver=request['receiver'])
        self.assertFalse(self.exchange(self.channel(self.other), remove)['ok'])
        self.assertTrue(self.exchange(channel, remove)['ok'])
        self.assertNotIn(server, self.broker.dynamic_receivers)
        self.assertFalse(self.exchange(channel, remove)['ok'])
        for invalid in (
            dict(request, uid=self.caller['uid']),
            dict(request, flags=True),
            dict(request, flags=6),
            dict(request, permission='bad/permission'),
            dict(request, filter=dict(request['filter'], authority=['example.com'])),
            dict(request, filter=dict(request['filter'], mime=['invalid'])),
            dict(request, filter=dict(request['filter'], action=['same', 'same'])),
            dict(request, receiver='not-an-id'),
        ):
            self.assertFalse(self.exchange(channel, invalid)['ok'])
            self.assertNotIn(server, self.broker.dynamic_receivers)
        for number in range(256):
            self.assertTrue(self.exchange(channel, dict(request, receiver=f'{number:032x}'))['ok'])
        self.assertFalse(self.exchange(channel, dict(request, receiver='f' * 32))['ok'])
        self.assertTrue(self.exchange(channel, dict(remove, receiver='0' * 32))['ok'])
        self.assertTrue(self.exchange(channel, dict(request, receiver='f' * 32))['ok'])
        channel.close()
        worker.join(timeout=5)
        self.assertFalse(worker.is_alive())
        self.assertNotIn(server, self.broker.dynamic_receivers)

    def test_dynamic_receiver_registration_byte_budget_is_reclaimed(self):
        channel = self.broker.open_session(
            self.caller['package'], self.caller['apk_sha256'], process_name=':events'
        )
        self.channels.append(channel)
        request = dict(
            op='register_dynamic_receiver',
            receiver='a' * 32,
            permission=None,
            flags=0,
            filter=dict(
                action=[str(n) + 'x' * 2000 for n in range(8)],
                category=[],
                mime=[],
                scheme=[],
                priority=0,
            ),
        )
        accepted = 0
        for _ in range(32):
            if not self.exchange(channel, request)['ok']:
                break
            accepted += 1
        self.assertGreater(accepted, 0)
        self.assertLess(accepted, 32)
        self.assertTrue(
            self.exchange(channel, dict(op='unregister_dynamic_receiver', receiver='a' * 32))['ok']
        )
        self.assertTrue(self.exchange(channel, request)['ok'])

    def test_dynamic_receiver_discovery_policy_matching_and_deduplication(self):
        self.target = self.fixture.enroll(
            'test.target',
            version=2,
            permissions=['test.RECEIVE'],
            defined_permissions=[
                dict(name='test.PRIVATE', protection=2),
                dict(name='test.RECEIVE', protection=0),
            ],
        )
        target = self.broker.open_session(
            self.target['package'], self.target['apk_sha256'], process_name=':events'
        )
        self.channels.append(target)
        caller, wrong = self.channel(self.caller), self.channel(self.other)
        rule = dict(
            action=['test.EVENT'],
            category=['test.CAT'],
            mime=['text/*'],
            scheme=['content'],
            priority=1,
        )
        registration = dict(
            op='register_dynamic_receiver',
            receiver='a' * 32,
            filter=rule,
            permission='test.PRIVATE',
            flags=2,
        )
        first = self.exchange(target, registration)['registration']
        higher = self.exchange(target, dict(registration, filter=dict(rule, priority=7)))[
            'registration'
        ]
        self.exchange(
            target, dict(registration, filter=dict(rule, priority=99), permission='test.MISSING')
        )
        self.exchange(target, dict(registration, receiver='b' * 32, permission=None, flags=4))
        query = dict(
            op='query_dynamic_receivers',
            package='test.target',
            action='test.EVENT',
            categories=['test.CAT'],
            mime='text/plain',
            scheme='content',
            has_data=True,
        )
        reply = self.exchange(caller, query)
        self.assertTrue(reply['ok'])
        self.assertEqual(len(reply['receivers']), 1)
        self.assertEqual(reply['receivers'][0]['registration'], higher)
        self.assertNotEqual(first, higher)
        self.assertEqual(reply['receivers'][0]['priority'], 7)
        self.assertNotIn('permission', reply['receivers'][0])
        self.assertEqual(
            len(
                self.exchange(caller, dict(query, recipient_permission='test.RECEIVE'))['receivers']
            ),
            1,
        )
        self.assertEqual(self.exchange(wrong, query)['receivers'], [])
        self.assertEqual(self.exchange(target, query)['receivers'], [])
        # Same package in another process can receive a not-exported registration.
        sibling = self.broker.open_session(
            self.target['package'], self.target['apk_sha256'], process_name=':other'
        )
        self.channels.append(sibling)
        self.assertEqual(
            [item['receiver'] for item in self.exchange(sibling, query)['receivers']], ['b' * 32]
        )
        for changes in (
            dict(categories=['unknown']),
            dict(mime='image/png'),
            dict(scheme='file'),
            dict(action='other'),
            dict(package='test.absent'),
            dict(recipient_permission='test.PRIVATE'),
        ):
            self.assertEqual(self.exchange(caller, dict(query, **changes))['receivers'], [])
        global_query = dict(query)
        del global_query['package']
        self.assertEqual(len(self.exchange(caller, global_query)['receivers']), 1)
        self.assertFalse(self.exchange(caller, dict(query, caller_uid=self.target['uid']))['ok'])
        self.exchange(target, dict(op='unregister_dynamic_receiver', receiver='a' * 32))
        self.assertEqual(self.exchange(caller, query)['receivers'], [])
        self.fixture.enroll('test.target', version=3)
        self.assertEqual(self.exchange(sibling, query)['ok'], False)
        self.assertEqual(self.exchange(caller, query)['receivers'], [])
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(caller, global_query)['ok'])

    def test_dynamic_receiver_live_transport_and_unregister_revocation(self):
        with self.broker.open_session(
            self.target['package'], self.target['apk_sha256'], process_name=':events'
        ) as target:
            registration = dict(
                op='register_dynamic_receiver',
                receiver='a' * 32,
                permission='test.PRIVATE',
                flags=2,
                filter=dict(action=['test.EVENT'], category=[], mime=[], scheme=[], priority=0),
            )
            token = self.exchange(target, registration)['registration']
            sender = self.channel(self.caller)
            request = dict(
                op='open_dynamic_receiver',
                registration=token,
                expected_sha256=self.target['apk_sha256'],
                action='test.EVENT',
            )
            write_frame(target, dict(op='register_receiver_host'))
            _, descriptors = read_packet(target, 3)
            with (
                socket.socket(fileno=descriptors[0]) as inbox,
                patch(
                    'shiraz.receiver_process.ReceiverProcess.__init__',
                    side_effect=AssertionError('Unexpected worker spawn'),
                ) as spawn,
            ):
                original = self.broker._resolve_dynamic_receiver
                calls = []

                def revoke_after_lease(*args):
                    calls.append(True)
                    if len(calls) == 2:
                        raise PermissionError('Fixture registration revoked after lease')
                    return original(*args)

                with patch.object(
                    self.broker, '_resolve_dynamic_receiver', side_effect=revoke_after_lease
                ):
                    self.assertFalse(self.exchange(sender, request)['ok'])
                self.assertEqual(len(calls), 2)
                self.assertFalse(self.broker.processes)
                write_frame(sender, request)
                reply, descriptors = read_packet(sender, 3)
                self.assertTrue(reply['ok'])
                with socket.socket(fileno=descriptors[0]) as outbound:
                    label, descriptors = read_payload(inbox, 3)
                    self.assertEqual(label, ('dynamic:' + token).encode())
                    with socket.socket(fileno=descriptors[0]) as inbound:
                        write_payload(outbound, b'dynamic parcel fixture')
                        self.assertEqual(read_payload(inbound, 3)[0], b'dynamic parcel fixture')
                        write_frame(inbound, {'ok': True})
                        self.assertEqual(read_frame(outbound, 3), {'ok': True})
                self.assertTrue(
                    self.exchange(sender, dict(op='finish_receiver', lease=reply['lease']))['ok']
                )
                self.assertFalse(self.broker.processes)
                self.assertTrue(
                    self.exchange(
                        target, dict(op='unregister_dynamic_receiver', receiver='a' * 32)
                    )['ok']
                )
                self.assertFalse(self.exchange(sender, request)['ok'])
                spawn.assert_not_called()

    def test_dynamic_receiver_waits_for_readiness_and_rechecks_revocation(self):
        for mode in ('ready', 'unregister', 'disconnect', 'quiesce', 'deadline'):
            with self.subTest(mode=mode), ServiceBroker(self.fixture.registry) as broker:
                with (
                    broker.open_session(
                        self.target['package'], self.target['apk_sha256'], process_name=':events'
                    ) as target,
                    broker.open_session(
                        self.caller['package'], self.caller['apk_sha256']
                    ) as sender,
                ):
                    write_frame(
                        target,
                        dict(
                            op='register_dynamic_receiver',
                            receiver='a' * 32,
                            permission=None,
                            flags=2,
                            filter=dict(
                                action=['test.EVENT'], category=[], mime=[], scheme=[], priority=0
                            ),
                        ),
                    )
                    token = read_frame(target, 3)['registration']
                    waiting = threading.Event()
                    original_wait = broker.ready.wait

                    def wait(timeout):
                        waiting.set()
                        return original_wait(timeout)

                    with patch.object(broker.ready, 'wait', side_effect=wait):
                        write_frame(
                            sender,
                            dict(
                                op='open_dynamic_receiver',
                                registration=token,
                                expected_sha256=self.target['apk_sha256'],
                                action='test.EVENT',
                            ),
                        )
                        self.assertTrue(waiting.wait(3))
                        if mode == 'ready':
                            write_frame(target, dict(op='register_receiver_host'))
                            _, descriptors = read_packet(target, 3)
                            with socket.socket(fileno=descriptors[0]) as inbox:
                                reply, descriptors = read_packet(sender, 3)
                                self.assertTrue(reply['ok'])
                                with socket.socket(fileno=descriptors[0]) as outbound:
                                    label, descriptors = read_payload(inbox, 3)
                                    self.assertEqual(label, ('dynamic:' + token).encode())
                                    with socket.socket(fileno=descriptors[0]) as inbound:
                                        write_payload(outbound, b'ready')
                                        self.assertEqual(read_payload(inbound, 3)[0], b'ready')
                                        write_frame(inbound, {'ok': True})
                                        self.assertEqual(read_frame(outbound, 3), {'ok': True})
                                write_frame(
                                    sender, dict(op='finish_receiver', lease=reply['lease'])
                                )
                                self.assertTrue(read_frame(sender, 3)['ok'])
                        else:
                            if mode == 'unregister':
                                write_frame(
                                    target,
                                    dict(op='unregister_dynamic_receiver', receiver='a' * 32),
                                )
                                self.assertTrue(read_frame(target, 3)['ok'])
                            elif mode == 'disconnect':
                                target.close()
                            elif mode == 'quiesce':
                                broker.quiesce_package(
                                    self.target['package'],
                                    expected_sha256=self.target['apk_sha256'],
                                    expected_uid=self.target['uid'],
                                )
                            reply = read_frame(sender, 8 if mode == 'deadline' else 3)
                            self.assertFalse(reply['ok'])
                            if mode == 'deadline':
                                self.assertIn('deadline', reply['error'])
                        self.assertFalse(broker.processes)

    def test_dynamic_receiver_resolution_rechecks_discovery_and_session_policy(self):
        target = self.broker.open_session(
            self.target['package'], self.target['apk_sha256'], process_name=':events'
        )
        self.channels.append(target)
        registration = dict(
            op='register_dynamic_receiver',
            receiver='a' * 32,
            permission='test.PRIVATE',
            flags=2,
            filter=dict(action=['test.EVENT'], category=[], mime=[], scheme=[], priority=0),
        )
        token = self.exchange(target, registration)['registration']
        caller, wrong = self.channel(self.caller), self.channel(self.other)
        request = dict(
            op='resolve_dynamic_receiver',
            registration=token,
            expected_sha256=self.target['apk_sha256'],
            action='test.EVENT',
            package=self.target['package'],
        )
        reply = self.exchange(caller, request)
        self.assertTrue(reply['ok'])
        self.assertEqual(reply['receiver']['registration'], token)
        for channel, invalid in (
            (wrong, request),
            (caller, dict(request, expected_sha256='0' * 64)),
            (caller, dict(request, registration='0' * 32)),
            (caller, dict(request, caller_uid=self.target['uid'])),
            (caller, dict(request, action='test.OTHER')),
            (caller, dict(request, registration=True)),
            (caller, dict(request, recipient_permission='test.MISSING')),
        ):
            self.assertFalse(self.exchange(channel, invalid)['ok'])
        self.assertFalse(self.broker.processes)
        self.exchange(target, dict(op='unregister_dynamic_receiver', receiver='a' * 32))
        self.assertFalse(self.exchange(caller, request)['ok'])
        replacement = self.exchange(target, registration)['registration']
        self.assertNotEqual(token, replacement)
        self.assertFalse(self.exchange(caller, request)['ok'])
        current = dict(request, registration=replacement)
        self.assertTrue(self.exchange(caller, current)['ok'])
        self.fixture.enroll('test.target', version=2)
        self.assertFalse(self.exchange(caller, current)['ok'])
        self.assertFalse(self.broker.processes)
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(caller, current)['ok'])

    def test_dynamic_receiver_registration_generation_and_enabled_state(self):
        channel = self.broker.open_session(
            self.caller['package'], self.caller['apk_sha256'], process_name=':events'
        )
        self.channels.append(channel)
        request = dict(
            op='register_dynamic_receiver',
            receiver='a' * 32,
            permission=None,
            flags=0,
            filter=dict(action=['test.EVENT'], category=[], mime=[], scheme=[], priority=0),
        )
        self.assertTrue(self.exchange(channel, request)['ok'])
        self.broker.quiesce_package(
            self.caller['package'],
            expected_sha256=self.caller['apk_sha256'],
            expected_uid=self.caller['uid'],
        )
        self.assertFalse(self.broker.dynamic_receivers)
        self.broker.resume_package(
            self.caller['package'],
            expected_sha256=self.caller['apk_sha256'],
            expected_uid=self.caller['uid'],
        )
        channel = self.broker.open_session(
            self.caller['package'], self.caller['apk_sha256'], process_name=':events'
        )
        self.channels.append(channel)
        disabled = self.fixture.enroll('test.caller', version=2, application_enabled='false')
        self.assertFalse(self.exchange(channel, request)['ok'])
        current = self.broker.open_session(
            disabled['package'], disabled['apk_sha256'], process_name=':events'
        )
        self.channels.append(current)
        self.assertFalse(self.exchange(current, request)['ok'])
        self.assertFalse(self.broker.dynamic_receivers)

    def test_host_process_identity_is_unique_and_released_on_disconnect(self):
        channel = self.broker.open_session(
            self.caller['package'], self.caller['apk_sha256'], process_name=':events'
        )
        key = (
            self.caller['package'],
            self.caller['apk_sha256'],
            self.caller['package'] + ':events',
        )
        server = self.broker.live_processes[key]
        thread = self.broker.sessions[server]
        try:
            self.assertEqual(self.broker.identities[server].process_name, key[2])
            for process in (':events', key[2]):
                with self.assertRaisesRegex(RuntimeError, 'already has a live'):
                    self.broker.open_session(
                        self.caller['package'], self.caller['apk_sha256'], process_name=process
                    )
            self.assertFalse(
                self.exchange(channel, dict(self.request, process_name='test.target'))['ok']
            )
            with self.broker.open_session(
                self.caller['package'], self.caller['apk_sha256'], process_name=':other'
            ):
                self.assertIn((key[0], key[1], key[0] + ':other'), self.broker.live_processes)
        finally:
            channel.close()
        thread.join(timeout=5)
        self.assertFalse(thread.is_alive())
        self.assertNotIn(key, self.broker.live_processes)
        with self.broker.open_session(
            self.caller['package'], self.caller['apk_sha256'], process_name=':events'
        ):
            self.assertIn(key, self.broker.live_processes)
        for invalid in (
            '',
            ':',
            '../bad',
            'test.other:events',
            'test.caller:a:b',
            True,
            'x' * 1025,
        ):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                self.broker.open_session(
                    self.caller['package'], self.caller['apk_sha256'], process_name=invalid
                )

    def test_process_reservation_rolls_back_if_session_thread_cannot_start(self):
        with patch(
            'shiraz.service_broker.threading.Thread.start',
            side_effect=RuntimeError('fixture thread failure'),
        ):
            with self.assertRaisesRegex(RuntimeError, 'fixture thread failure'):
                self.broker.open_session(
                    self.caller['package'], self.caller['apk_sha256'], process_name=':events'
                )
        self.assertFalse(self.broker.live_processes)
        self.assertFalse(self.broker.sessions)
        self.assertFalse(self.broker.identities)

    def test_receiver_host_channel_is_bound_to_trusted_process_and_disconnect(self):
        request = dict(op='register_receiver_host')
        unassigned = self.channel(self.caller)
        self.assertFalse(self.exchange(unassigned, request)['ok'])
        channel = self.broker.open_session(
            self.caller['package'], self.caller['apk_sha256'], process_name=':events'
        )
        key = (
            self.caller['package'],
            self.caller['apk_sha256'],
            self.caller['package'] + ':events',
        )
        server = self.broker.live_processes[key]
        thread = self.broker.sessions[server]
        try:
            self.assertFalse(self.exchange(channel, dict(request, process='test.target'))['ok'])
            write_frame(channel, request)
            reply, descriptors = read_packet(channel, idle_timeout=3)
            self.assertEqual(reply, {'ok': True, 'process': key[2]})
            self.assertEqual(len(descriptors), 1)
            with socket.socket(fileno=descriptors[0]) as inbound:
                self.assertFalse(self.exchange(channel, request)['ok'])
                write_frame(self.broker.receiver_hosts[server], {'probe': 'private-channel'})
                self.assertEqual(read_frame(inbound, 3), {'probe': 'private-channel'})
                channel.close()
                thread.join(timeout=5)
                self.assertFalse(thread.is_alive())
                with self.assertRaises(EOFError):
                    read_frame(inbound, 3)
        finally:
            channel.close()
        self.assertNotIn(server, self.broker.receiver_hosts)
        self.assertNotIn(key, self.broker.live_processes)

    def test_live_receiver_relay_completes_without_stopping_target(self):
        target = self.fixture.enroll(
            'test.target', version=2, receivers=[dict(name='test.target.Receiver', exported='true')]
        )
        for mode in ('success', 'failure', 'quiesce'):
            with self.subTest(mode=mode), ServiceBroker(self.fixture.registry) as broker:
                with (
                    broker.open_session(
                        'test.target', target['apk_sha256'], process_name='test.target'
                    ) as target_control,
                    broker.open_session('test.caller', self.caller['apk_sha256']) as sender,
                ):
                    write_frame(target_control, {'op': 'register_receiver_host'})
                    registered, descriptors = read_packet(target_control, 3)
                    self.assertTrue(registered['ok'])
                    with socket.socket(fileno=descriptors[0]) as inbox:
                        with patch('shiraz.receiver_process.ReceiverProcess') as spawn:
                            write_frame(
                                sender,
                                dict(
                                    op='open_receiver',
                                    package='test.target',
                                    component='test.target.Receiver',
                                ),
                            )
                            admitted, descriptors = read_packet(sender, 3)
                            self.assertTrue(admitted['ok'], admitted)
                            spawn.assert_not_called()
                        delivery = next(iter(broker.processes))
                        with socket.socket(fileno=descriptors[0]) as outbound:
                            component, descriptors = read_payload(inbox, 3)
                            self.assertEqual(component, b'test.target.Receiver')
                            with socket.socket(fileno=descriptors[0]) as target_endpoint:
                                write_payload(outbound, b'opaque-intent-bytes')
                                self.assertEqual(
                                    read_payload(target_endpoint, 3), (b'opaque-intent-bytes', [])
                                )
                                if mode == 'success':
                                    write_frame(target_endpoint, {'ok': True})
                                    self.assertEqual(read_frame(outbound, 3), {'ok': True})
                                elif mode == 'failure':
                                    target_endpoint.close()
                                    with self.assertRaises(EOFError):
                                        read_frame(outbound, 3)
                                else:
                                    broker.quiesce_package(
                                        'test.target',
                                        expected_sha256=target['apk_sha256'],
                                        expected_uid=target['uid'],
                                    )
                                    with self.assertRaises(EOFError):
                                        read_frame(outbound, 3)
                        delivery.thread.join(timeout=5)
                        self.assertFalse(delivery.thread.is_alive())
                        self.assertTrue(delivery.cleanup_complete)
                        if mode != 'quiesce':
                            write_frame(sender, dict(op='finish_receiver', lease=admitted['lease']))
                            self.assertEqual(read_frame(sender, 3)['ok'], mode == 'success')
                            self.assertIn(
                                ('test.target', target['apk_sha256'], 'test.target'),
                                broker.live_processes,
                            )
                            write_frame(
                                target_control,
                                dict(
                                    op='resolve_receiver',
                                    package='test.target',
                                    component='test.target.Receiver',
                                ),
                            )
                            self.assertTrue(read_frame(target_control, 3)['ok'])

    def test_live_receiver_management_write_failure_revokes_channel(self):
        target = self.fixture.enroll(
            'test.target', version=2, receivers=[dict(name='test.target.Receiver', exported='true')]
        )
        with self.broker.open_session(
            'test.target', target['apk_sha256'], process_name='test.target'
        ) as target_control:
            write_frame(target_control, {'op': 'register_receiver_host'})
            _, descriptors = read_packet(target_control, 3)
            with socket.socket(fileno=descriptors[0]) as inbox:
                with patch(
                    'shiraz.live_receiver.write_payload',
                    side_effect=OSError('fixture partial frame'),
                ):
                    reply = self.exchange(
                        self.channel(self.caller),
                        dict(
                            op='open_receiver',
                            package='test.target',
                            component='test.target.Receiver',
                        ),
                    )
                self.assertFalse(reply['ok'])
                with self.assertRaises(EOFError):
                    read_frame(inbox, 3)
                self.assertFalse(self.broker.receiver_hosts)
                self.assertFalse(self.broker.processes)
                self.assertIn(
                    ('test.target', target['apk_sha256'], 'test.target'), self.broker.live_processes
                )

    def test_receiver_waits_for_reserved_process_readiness_or_quiescence(self):
        target = self.fixture.enroll(
            'test.target', version=2, receivers=[dict(name='test.target.Receiver', exported='true')]
        )
        for mode in ('ready', 'quiesce'):
            with self.subTest(mode=mode), ServiceBroker(self.fixture.registry) as broker:
                with (
                    broker.open_session(
                        'test.target', target['apk_sha256'], process_name='test.target'
                    ) as target_control,
                    broker.open_session('test.caller', self.caller['apk_sha256']) as sender,
                ):
                    waiting = threading.Event()
                    original_wait = broker.ready.wait

                    def wait(timeout):
                        waiting.set()
                        return original_wait(timeout)

                    with (
                        patch.object(broker.ready, 'wait', side_effect=wait),
                        patch(
                            'shiraz.receiver_process.ReceiverProcess.__init__',
                            side_effect=AssertionError('Unexpected spawn'),
                        ) as spawn,
                    ):
                        write_frame(
                            sender,
                            dict(
                                op='open_receiver',
                                package='test.target',
                                component='test.target.Receiver',
                            ),
                        )
                        self.assertTrue(waiting.wait(3), 'Admission did not wait for readiness')
                        if mode == 'quiesce':
                            broker.quiesce_package(
                                'test.target',
                                expected_sha256=target['apk_sha256'],
                                expected_uid=target['uid'],
                            )
                            self.assertFalse(read_frame(sender, 3)['ok'])
                            self.assertFalse(broker.processes)
                        else:
                            write_frame(target_control, {'op': 'register_receiver_host'})
                            _, descriptors = read_packet(target_control, 3)
                            with socket.socket(fileno=descriptors[0]) as inbox:
                                reply, descriptors = read_packet(sender, 3)
                                self.assertTrue(reply['ok'], reply)
                                with socket.socket(fileno=descriptors[0]) as outbound:
                                    _, descriptors = read_payload(inbox, 3)
                                    with socket.socket(fileno=descriptors[0]) as target_endpoint:
                                        write_payload(outbound, b'intent')
                                        self.assertEqual(
                                            read_payload(target_endpoint, 3), (b'intent', [])
                                        )
                                        write_frame(target_endpoint, {'ok': True})
                                        self.assertEqual(read_frame(outbound, 3), {'ok': True})
                                write_frame(
                                    sender, dict(op='finish_receiver', lease=reply['lease'])
                                )
                                self.assertEqual(read_frame(sender, 3), {'ok': True})
                        spawn.assert_not_called()

    def test_receiver_discovery_is_authenticated_and_pins_later_admission(self):
        target = self.fixture.enroll(
            'test.target',
            version=2,
            receivers=[
                dict(
                    name='test.target.Receiver',
                    exported='true',
                    permission='test.PRIVATE',
                    filters=[dict(action=['test.EVENT'])],
                )
            ],
            defined_permissions=[dict(name='test.PRIVATE', protection=2)],
        )
        request = dict(op='query_receivers', package='test.target', action='test.EVENT')
        trusted, other = self.channel(self.caller), self.channel(self.other)
        with patch('shiraz.receiver_process.ReceiverProcess') as spawn:
            result = self.exchange(trusted, request)
            self.assertTrue(result['ok'], result)
            self.assertEqual(
                [item['apk_sha256'] for item in result['receivers']], [target['apk_sha256']]
            )
            self.assertEqual(self.exchange(other, request), {'ok': True, 'receivers': []})
            for invalid in (
                dict(request, caller_uid=self.caller['uid']),
                dict(request, package=[]),
                dict(request, categories=True),
            ):
                self.assertFalse(self.exchange(trusted, invalid)['ok'])
            self.fixture.enroll(
                'test.target',
                version=3,
                receivers=[dict(name='test.target.Receiver', exported='true')],
            )
            opening = dict(
                op='open_receiver',
                package='test.target',
                component='test.target.Receiver',
                expected_sha256=target['apk_sha256'],
            )
            self.assertIn('changed after discovery', self.exchange(trusted, opening)['error'])
            self.assertFalse(self.exchange(trusted, dict(opening, expected_sha256='bad'))['ok'])
            spawn.assert_not_called()
        self.assertFalse(self.broker.processes)

    def test_identity_spoofing_and_generation(self):
        trusted = self.channel(self.caller)
        self.assertTrue(self.exchange(trusted, self.request)['ok'])
        other = self.channel(self.other)
        self.assertFalse(self.exchange(other, self.request)['ok'])
        self.assertFalse(self.exchange(other, dict(self.request, op='open_service'))['ok'])
        self.assertFalse(self.broker.processes)
        spoof = dict(self.request, caller_uid=self.caller['uid'])
        self.assertFalse(self.exchange(other, spoof)['ok'])
        spoof = dict(self.request, op='bind')
        self.assertFalse(self.exchange(trusted, spoof)['ok'])
        self.fixture.enroll('test.caller', version=2, permissions=['test.PRIVATE'])
        self.assertFalse(self.exchange(trusted, self.request)['ok'])
        with self.assertRaises(PermissionError):
            self.broker.open_session(self.caller['package'], self.caller['apk_sha256'])

    def test_receiver_resolution_authenticates_session_and_never_spawns(self):
        target = self.fixture.enroll(
            'test.target',
            version=2,
            receivers=[
                dict(
                    name='test.target.Receiver',
                    exported='true',
                    permission='test.PRIVATE',
                    process=':events',
                )
            ],
            defined_permissions=[dict(name='test.PRIVATE', protection=2)],
        )
        request = dict(
            op='resolve_receiver', package='test.target', component='test.target.Receiver'
        )
        trusted, other = self.channel(self.caller), self.channel(self.other)
        with patch('shiraz.service_process.ServiceProcess') as spawn:
            result = self.exchange(trusted, request)
            self.assertTrue(result['ok'], result)
            self.assertEqual(
                result['receiver'],
                dict(
                    package='test.target',
                    uid=target['uid'],
                    apk_sha256=target['apk_sha256'],
                    component='test.target.Receiver',
                    process=':events',
                ),
            )
            self.assertFalse(self.exchange(other, request)['ok'])
            self.assertFalse(
                self.exchange(other, dict(request, caller_uid=self.caller['uid']))['ok']
            )
            self.assertFalse(
                self.exchange(trusted, dict(request, recipient_permission='test.ABSENT'))['ok']
            )
            self.broker.quiesce_package(
                'test.target', expected_sha256=target['apk_sha256'], expected_uid=target['uid']
            )
            self.assertFalse(self.exchange(trusted, request)['ok'])
            self.broker.resume_package(
                'test.target', expected_sha256=target['apk_sha256'], expected_uid=target['uid']
            )
            self.fixture.enroll('test.caller', version=2, permissions=['test.PRIVATE'])
            self.assertFalse(self.exchange(trusted, request)['ok'])
            spawn.assert_not_called()
        self.assertFalse(self.broker.processes)

    def test_receiver_admission_denials_do_not_spawn(self):
        target = self.fixture.enroll(
            'test.target',
            version=2,
            receivers=[
                dict(name='test.target.Receiver', exported='true', permission='test.PRIVATE')
            ],
            defined_permissions=[dict(name='test.PRIVATE', protection=2)],
        )
        request = dict(op='open_receiver', package='test.target', component='test.target.Receiver')
        trusted, other = self.channel(self.caller), self.channel(self.other)
        with patch('shiraz.receiver_process.ReceiverProcess') as spawn:
            for channel, invalid, filtered in (
                (other, request, True),
                (trusted, dict(request, caller_uid=self.caller['uid']), False),
                (trusted, dict(request, recipient_permission='test.ABSENT'), True),
            ):
                reply = self.exchange(channel, invalid)
                self.assertFalse(reply['ok'])
                self.assertEqual(reply.get('filtered', False), filtered)
            self.broker.quiesce_package(
                'test.target', expected_sha256=target['apk_sha256'], expected_uid=target['uid']
            )
            reply = self.exchange(trusted, request)
            self.assertFalse(reply['ok'])
            self.assertNotIn('filtered', reply)
            self.broker.resume_package(
                'test.target', expected_sha256=target['apk_sha256'], expected_uid=target['uid']
            )
            self.fixture.enroll('test.caller', version=2, permissions=['test.PRIVATE'])
            reply = self.exchange(trusted, request)
            self.assertFalse(reply['ok'])
            self.assertNotIn('filtered', reply)
            spawn.assert_not_called()
        self.assertFalse(self.broker.processes)

    def test_unavailable_receivers_filter_only_authenticated_senders(self):
        request = dict(
            op='open_receiver', package='test.missing', component='test.missing.Receiver'
        )
        trusted = self.channel(self.caller)
        with patch('shiraz.receiver_process.ReceiverProcess') as spawn:
            reply = self.exchange(trusted, request)
            self.assertTrue(reply.get('filtered'))
            self.assertFalse(reply['ok'])
            reply = self.exchange(trusted, dict(request, expected_sha256='a' * 64))
            self.assertNotIn('filtered', reply)
            self.assertFalse(reply['ok'])
            reply = self.exchange(trusted, dict(request, package='../bad'))
            self.assertFalse(reply['ok'])
            self.assertNotIn('filtered', reply)
            for fields in (
                {},
                {'receivers': [dict(name='test.target.Receiver', enabled='false')]},
                {'application_enabled': 'false'},
            ):
                self.fixture.enroll('test.target', version=self.fixture.sequence + 10, **fields)
                reply = self.exchange(
                    trusted, dict(request, package='test.target', component='test.target.Receiver')
                )
                self.assertTrue(reply.get('filtered'))
                self.assertFalse(reply['ok'])
            disabled = self.fixture.enroll('test.caller', version=2, application_enabled='false')
            # Existing session is stale and must not become a recipient skip.
            reply = self.exchange(trusted, request)
            self.assertFalse(reply['ok'])
            self.assertNotIn('filtered', reply)
            reply = self.exchange(self.channel(disabled), request)
            self.assertFalse(reply['ok'])
            self.assertNotIn('filtered', reply)
            spawn.assert_not_called()

    def test_receiver_reauthorizes_after_generation_lease_before_spawn(self):
        from shiraz.receiver_process import ReceiverProcess
        from shiraz.service_broker import Identity

        self.fixture.enroll(
            'test.target',
            version=2,
            receivers=[
                dict(name='test.target.Receiver', exported='true', permission='test.PRIVATE')
            ],
            defined_permissions=[dict(name='test.PRIVATE', protection=2)],
        )
        identity = Identity(
            package=self.caller['package'], uid=self.caller['uid'], sha256=self.caller['apk_sha256']
        )
        request = dict(op='open_receiver', package='test.target', component='test.target.Receiver')
        receiver = self.broker._resolve_receiver(identity, request)
        with (
            patch.object(
                self.broker, '_resolve_receiver', return_value=dict(receiver, apk_sha256='0' * 64)
            ),
            patch('shiraz.service_process.subprocess.Popen') as spawn,
        ):
            with self.assertRaisesRegex(PermissionError, 'changed before launch'):
                ReceiverProcess(self.broker, receiver, identity, request)
            spawn.assert_not_called()
        # A failed admission releases its lease; a later generation can be enrolled.
        self.fixture.enroll('test.target', version=3)
        self.assertFalse(self.broker.sessions)

    def test_stop_admission_does_not_create_absent_worker(self):
        for operation in ('open_existing_service', 'open_service_for_stop'):
            request = dict(self.request, op=operation)
            with patch('shiraz.service_process.ServiceProcess') as spawn:
                reply = self.exchange(self.channel(self.caller), request)
                self.assertTrue(reply['ok'])
                self.assertTrue(reply['absent'])
                self.assertNotIn('lease', reply)
                self.assertFalse(self.exchange(self.channel(self.other), request)['ok'])
                spawn.assert_not_called()
        self.assertFalse(self.broker.processes)

    def test_stop_intent_survives_delivery_failure_and_late_completion(self):
        key = (self.target['package'], self.target['apk_sha256'], 'test.target.Worker')
        target = Mock(
            key=key,
            references=0,
            started=True,
            closed=False,
            restart_mode=1,
            last_start_id=1,
            last_accepted_start_id=0,
            restart_suppressed=False,
        )
        target.process.poll.return_value = None
        target.open_endpoint.side_effect = OSError('fixture endpoint unavailable')
        self.broker.targets[key] = target
        self.broker.processes.add(target)
        caller = self.channel(self.caller)
        denied = self.channel(self.other)
        worker = self.broker.open_session(key[0], key[1], worker=target)
        self.channels.append(worker)
        stop = dict(self.request, op='open_service_for_stop')
        self.assertFalse(self.exchange(denied, stop)['ok'])
        self.assertFalse(target.restart_suppressed)
        target.open_endpoint.assert_not_called()

        # Suppression precedes the attempt to deliver stop to the worker.
        def failed_delivery(uid):
            self.assertTrue(target.restart_suppressed)
            raise OSError('fixture endpoint unavailable')

        target.open_endpoint.side_effect = failed_delivery
        self.assertIn('fixture endpoint unavailable', self.exchange(caller, stop)['error'])
        self.assertTrue(target.restart_suppressed)
        self.assertTrue(target.started)  # Admission is not an acknowledgement of onDestroy.
        self.assertEqual(target.references, 0)
        completed = dict(op='service_start_completed', start_id=2, restart_mode=1)
        self.assertEqual(self.exchange(worker, completed), {'ok': True})
        self.assertTrue(target.restart_suppressed)
        # Mere binding/discovery and denied starts cannot undo a requested stop.
        target.open_endpoint.side_effect = OSError('fixture endpoint unavailable')
        for operation in ('open_service', 'open_existing_service'):
            self.assertFalse(self.exchange(caller, dict(self.request, op=operation))['ok'])
            self.assertTrue(target.restart_suppressed)
        start = dict(self.request, op='open_service_for_start')
        self.assertFalse(self.exchange(denied, start)['ok'])
        self.assertTrue(target.restart_suppressed)
        target.open_start_endpoint.side_effect = OSError('fixture endpoint unavailable')
        # A failed start admission cannot undo the stop request.
        self.assertFalse(self.exchange(caller, start)['ok'])
        self.assertTrue(target.restart_suppressed)
        self.assertEqual(target.references, 0)
        # A retained dead worker must also record the stop without spawning.
        target.process.poll.return_value = 1
        target.open_endpoint.reset_mock()
        with patch('shiraz.service_process.ServiceProcess') as spawn:
            reply = self.exchange(caller, stop)
            self.assertTrue(reply['ok'])
            self.assertTrue(reply['absent'])
            self.assertTrue(target.restart_suppressed)
            target.open_endpoint.assert_not_called()
            spawn.assert_not_called()

    def test_worker_finished_ids_require_current_accepted_start_and_are_monotonic(self):
        key = (self.target['package'], self.target['apk_sha256'], 'test.target.Worker')
        target = Mock(
            key=key,
            references=1,
            started=True,
            closed=False,
            last_start_id=1,
            last_accepted_start_id=3,
            last_finished_start_id=0,
            start_state=ServiceStartState(),
        )
        target.process.poll.return_value = None
        self.broker.targets[key] = target
        self.broker.processes.add(target)
        worker = self.broker.open_session(key[0], key[1], worker=target)
        self.channels.append(worker)
        request = dict(op='service_start_finished', start_id=2)
        self.assertFalse(self.exchange(self.channel(self.caller), request)['ok'])
        for invalid in (
            dict(request, start_id=True),
            dict(request, start_id=0),
            dict(request, start_id=-1),
            dict(request, start_id=4),
            dict(request, start_id=2147483648),
            dict(request, extra=1),
        ):
            self.assertFalse(self.exchange(worker, invalid)['ok'])
        self.assertEqual(target.last_finished_start_id, 0)
        # Work can finish before its onStartCommand return is reported.
        self.assertTrue(self.exchange(worker, request)['ok'])
        self.assertEqual(target.last_finished_start_id, 2)
        self.assertTrue(target.started)
        self.assertTrue(self.exchange(worker, dict(request, start_id=1))['ok'])
        self.assertTrue(self.exchange(worker, request)['ok'])
        self.assertEqual(target.last_finished_start_id, 2)
        # Quiescence prevents new work, not completion of already admitted work.
        self.broker.quiesced.add(key[0])
        self.assertTrue(self.exchange(worker, dict(request, start_id=3))['ok'])
        self.assertEqual(target.last_finished_start_id, 3)
        self.broker.quiesced.remove(key[0])
        target.started = False
        self.assertFalse(self.exchange(worker, request)['ok'])
        target.started = True
        with self.broker.lock:
            del self.broker.targets[key]
        self.assertFalse(self.exchange(worker, request)['ok'])

    def test_redelivery_snapshot_authorization_completion_and_stop(self):
        key = (self.target['package'], self.target['apk_sha256'], 'test.target.Worker')
        state = ServiceStartState()
        target = Mock(
            key=key,
            references=1,
            started=True,
            closed=False,
            start_state=state,
            last_start_id=0,
            last_accepted_start_id=3,
            last_finished_start_id=0,
        )
        target.process.poll.return_value = None
        self.broker.targets[key] = target
        self.broker.processes.add(target)
        worker = self.broker.open_session(key[0], key[1], worker=target)
        self.channels.append(worker)
        request = dict(op='service_start_completed', start_id=1, restart_mode=3, intent='AQ==')
        self.assertFalse(self.exchange(self.channel(self.caller), request)['ok'])
        for invalid in (
            dict(request, intent='AB=='),
            dict(request, intent=None),
            dict(request, restart_mode=2),
            {k: v for k, v in request.items() if k != 'intent'},
        ):
            self.assertFalse(self.exchange(worker, invalid)['ok'])
            self.assertEqual(target.last_start_id, 0)
            self.assertFalse(state.intents)
        self.assertTrue(self.exchange(worker, request)['ok'])
        self.assertEqual(state.intents, {1: b'\x01'})
        self.assertTrue(self.exchange(worker, dict(op='service_start_finished', start_id=2))['ok'])
        self.assertFalse(state.intents)
        self.assertTrue(self.exchange(worker, dict(request, start_id=2))['ok'])
        self.assertFalse(state.intents, 'Late callback resurrected completed work')
        self.assertTrue(self.exchange(worker, dict(request, start_id=3))['ok'])
        self.assertEqual(state.intents, {3: b'\x01'})
        self.assertTrue(self.exchange(worker, dict(op='service_started', started=False))['ok'])
        self.assertFalse(state.intents)
        self.assertIsNone(target.restart_mode)

    def test_start_admission_preserves_newer_stop(self):
        key = (self.target['package'], self.target['apk_sha256'], 'test.target.Worker')
        target = Mock(
            key=key,
            references=0,
            started=True,
            closed=False,
            restart_suppressed=True,
            restart_stop_token=None,
            last_start_id=0,
            last_accepted_start_id=0,
        )
        target.process.poll.return_value = None
        self.broker.targets[key] = target
        self.broker.processes.add(target)
        caller, stopper = self.channel(self.caller), self.channel(self.caller)
        worker = self.broker.open_session(key[0], key[1], worker=target)
        self.channels.append(worker)
        entered, resume = threading.Event(), threading.Event()
        peer, endpoint = socket.socketpair()
        self.channels.append(peer)

        def delayed_admission(uid, ticket):
            entered.set()
            if not resume.wait(3):
                raise RuntimeError('fixture admission was not resumed')
            return endpoint

        target.open_start_endpoint.side_effect = delayed_admission
        start = dict(self.request, op='open_service_for_start')
        write_frame(caller, start)
        try:
            self.assertTrue(entered.wait(3))
            self.assertTrue(target.restart_suppressed)
            # A second session records stop while the first admission is blocked.
            target.open_endpoint.side_effect = OSError('fixture stop delivery failed')
            reply = self.exchange(stopper, dict(self.request, op='open_service_for_stop'))
            self.assertFalse(reply['ok'])
        finally:
            resume.set()
        reply, descriptors = read_packet(caller, idle_timeout=3)
        try:
            self.assertTrue(reply['ok'], reply)
            self.assertEqual(len(descriptors), 1)
            self.assertTrue(target.restart_suppressed)
            ticket = target.open_start_endpoint.call_args.args[1]
            accepted = dict(op='service_start_accepted', ticket=ticket, start_id=ticket)
            self.assertFalse(self.exchange(caller, accepted)['ok'])
            self.assertTrue(self.exchange(worker, accepted)['ok'])
            self.assertTrue(target.restart_suppressed)
            self.assertIsNone(target.restart_mode)
            self.assertFalse(self.exchange(worker, accepted)['ok'])
        finally:
            for descriptor in descriptors:
                os.close(descriptor)
        self.assertTrue(
            self.exchange(caller, dict(op='release_service', lease=reply['lease']))['ok']
        )
        # A later successful admission can supersede this stop, unlike the old one.
        peer, endpoint = socket.socketpair()
        self.channels.append(peer)
        target.open_start_endpoint.side_effect = None
        target.open_start_endpoint.return_value = endpoint
        write_frame(caller, start)
        reply, descriptors = read_packet(caller, idle_timeout=3)
        try:
            self.assertTrue(reply['ok'], reply)
            self.assertTrue(target.restart_suppressed)
            ticket = target.open_start_endpoint.call_args.args[1]
            accepted = dict(op='service_start_accepted', ticket=ticket, start_id=ticket)
            for invalid in (
                dict(accepted, ticket=True),
                dict(accepted, ticket=0),
                dict(accepted, extra=1),
            ):
                self.assertFalse(self.exchange(worker, invalid)['ok'])
            self.assertTrue(self.exchange(worker, accepted)['ok'])
            self.assertFalse(target.restart_suppressed)
            self.assertTrue(
                self.exchange(
                    worker, dict(op='service_start_completed', start_id=1, restart_mode=1)
                )['ok']
            )
            self.assertIsNone(target.restart_mode, 'Older callback revived a pending start')
            self.assertTrue(
                self.exchange(
                    worker, dict(op='service_start_completed', start_id=2, restart_mode=2)
                )['ok']
            )
            self.assertEqual(target.restart_mode, 2)
        finally:
            for descriptor in descriptors:
                os.close(descriptor)
        self.assertTrue(
            self.exchange(caller, dict(op='release_service', lease=reply['lease']))['ok']
        )
        self.assertEqual(target.references, 0)
        self.assertFalse(self.broker.start_tickets)
        # Opening and releasing an endpoint without delivering start is not acceptance.
        target.restart_suppressed = True
        peer, endpoint = socket.socketpair()
        self.channels.append(peer)
        target.open_start_endpoint.return_value = endpoint
        write_frame(caller, start)
        reply, descriptors = read_packet(caller, idle_timeout=3)
        try:
            self.assertTrue(reply['ok'], reply)
            ticket = target.open_start_endpoint.call_args.args[1]
            self.assertTrue(target.restart_suppressed)
        finally:
            for descriptor in descriptors:
                os.close(descriptor)
        self.assertTrue(
            self.exchange(caller, dict(op='release_service', lease=reply['lease']))['ok']
        )
        self.assertFalse(
            self.exchange(
                worker, dict(op='service_start_accepted', ticket=ticket, start_id=ticket)
            )['ok']
        )
        self.assertTrue(target.restart_suppressed)
        self.assertFalse(self.broker.start_tickets)

    def test_sticky_restart_budget_and_modes(self):
        self.check_restart_budget(1)

    def test_redelivery_restart_budget_and_modes(self):
        self.check_restart_budget(3)

    def check_restart_budget(self, restart_mode):
        from shiraz.service_broker import Identity

        identity = Identity(self.target['package'], self.target['uid'], self.target['apk_sha256'])
        service = self.broker._resolve(identity, self.request)

        class Worker:
            def __init__(worker, broker, definition):
                worker.service = dict(definition)
                worker.key = (
                    definition['package'],
                    definition['apk_sha256'],
                    definition['component'],
                )
                worker.start_state = ServiceStartState()
                worker.started = True
                worker.restart_mode = 1
                worker.restart_suppressed = False
                worker.restart_attempt = 0
                worker.references = 0
                worker.closed = False

            def close(worker):
                worker.closed = True

            def restart_sticky(worker):
                self.assertTrue(previous.closed, 'Replacement preceded cleanup')
                worker.started = True
                worker.restart_mode = 1

            def restart_redelivery(worker, frame):
                self.assertTrue(previous.closed, 'Redelivery preceded cleanup')
                self.assertEqual(frame, previous.start_state.restart_frame())
                self.assertEqual(worker.last_accepted_start_id, 2)
                worker.started = True
                worker.restart_mode = 3
                worker.start_state.intents = dict(previous.start_state.intents)

        previous = Worker(self.broker, service)
        self.broker.targets[previous.key] = previous
        for mode in (0, 2, 3, None):
            previous.restart_mode = mode
            self.assertIsNone(self.broker._queue_service_restart(previous, identity))
        previous.restart_mode = restart_mode
        if restart_mode == 3:
            previous.start_state.retain(2, 'AQ==')
        previous.restart_suppressed = True
        self.assertIsNone(self.broker._queue_service_restart(previous, identity))
        previous.restart_suppressed = False
        self.broker.restart_delays = (0, 0, 0)
        with patch('shiraz.service_process.ServiceProcess', Worker):
            for attempt in (1, 2, 3):
                pending = self.broker._queue_service_restart(previous, identity)
                self.assertIsNotNone(pending)
                self.broker.targets.pop(previous.key)
                self.broker._restart_service(pending)
                replacement = self.broker.targets[previous.key]
                self.assertIsNot(replacement, previous)
                self.assertEqual(replacement.restart_attempt, attempt)
                self.assertFalse(self.broker.service_restarts)
                previous = replacement
            self.assertIsNone(self.broker._queue_service_restart(previous, identity))

    def test_pending_sticky_restart_cancellation_and_generation(self):
        self.check_pending_restart_cancellation(1)

    def test_pending_redelivery_restart_cancellation_and_generation(self):
        self.check_pending_restart_cancellation(3)

    def check_pending_restart_cancellation(self, restart_mode):
        from shiraz.service_broker import Identity

        identity = Identity(self.target['package'], self.target['uid'], self.target['apk_sha256'])
        service = self.broker._resolve(identity, self.request)
        key = (identity.package, identity.sha256, service['component'])
        old = Mock(
            key=key,
            service=service,
            started=True,
            restart_mode=restart_mode,
            start_state=ServiceStartState(),
            restart_suppressed=False,
            restart_attempt=0,
            references=0,
            closed=False,
        )
        old.start_state.retain(2, 'AQ==')
        self.broker.restart_delays = (0,)

        def queue():
            self.broker.targets[key] = old
            pending = self.broker._queue_service_restart(old, identity)
            self.assertIsNotNone(pending)
            self.broker.targets.pop(key)
            return pending

        caller = self.channel(self.caller)
        with patch('shiraz.service_process.ServiceProcess') as spawn:
            pending = queue()
            denied = self.exchange(
                self.channel(self.other), dict(self.request, op='open_service_for_stop')
            )
            self.assertFalse(denied['ok'])
            self.assertIs(self.broker.service_restarts[key], pending)
            reply = self.exchange(caller, dict(self.request, op='open_service_for_stop'))
            self.assertTrue(reply['ok'])
            self.assertTrue(reply['absent'])
            self.assertTrue(reply['restart_cancelled'])
            again = self.exchange(caller, dict(self.request, op='open_service_for_stop'))
            self.assertTrue(again['absent'])
            self.assertNotIn('restart_cancelled', again)
            self.broker._restart_service(pending)
            spawn.assert_not_called()
            pending = queue()
            self.broker.quiesce_package(
                identity.package, expected_sha256=identity.sha256, expected_uid=identity.uid
            )
            self.broker.resume_package(
                identity.package, expected_sha256=identity.sha256, expected_uid=identity.uid
            )
            self.broker._restart_service(pending)
            spawn.assert_not_called()
            pending = queue()
            self.fixture.enroll(
                'test.target',
                version=2,
                services=[dict(name='test.target.Worker', exported='true')],
            )
            self.broker._restart_service(pending)
            spawn.assert_not_called()
            self.assertFalse(self.broker.service_restarts)

    def test_sticky_backoff_wakes_on_stop_and_shutdown(self):
        from shiraz.service_broker import Identity

        identity = Identity(self.target['package'], self.target['uid'], self.target['apk_sha256'])
        service = self.broker._resolve(identity, self.request)
        key = (identity.package, identity.sha256, service['component'])
        caller = self.channel(self.caller)
        self.broker.restart_delays = (60,)
        for cancel in ('stop', 'shutdown'):
            cleaned = threading.Event()
            old = Mock(
                key=key,
                service=service,
                started=True,
                restart_mode=1,
                restart_suppressed=False,
                restart_attempt=0,
                references=0,
                closed=False,
            )
            old.close.side_effect = cleaned.set
            with self.broker.lock:
                self.broker.targets[key] = old
                pending = self.broker._queue_service_restart(old, identity)
                self.broker.targets.pop(key)
                thread = threading.Thread(
                    target=self.broker._restart_service, args=(pending,), daemon=True
                )
                self.broker.restart_threads.add(thread)
            with patch('shiraz.service_process.ServiceProcess') as spawn:
                thread.start()
                self.assertTrue(cleaned.wait(3))
                if cancel == 'stop':
                    self.assertTrue(
                        self.exchange(caller, dict(self.request, op='open_service_for_stop'))['ok']
                    )
                else:
                    self.broker.close()
                thread.join(timeout=3)
                self.assertFalse(thread.is_alive(), 'Backoff ignored lifecycle cancellation')
                spawn.assert_not_called()
                self.assertFalse(self.broker.service_restarts)
                self.assertFalse(self.broker.restart_threads)

    def test_rebind_waits_for_sticky_replacement_and_rechecks_generation(self):
        key = (self.target['package'], self.target['apk_sha256'], 'test.target.Worker')
        caller = self.channel(self.caller)
        for changed in (False, True):
            waiting = threading.Event()
            original_wait = self.broker.ready.wait

            def wait(timeout):
                waiting.set()
                return original_wait(timeout)

            self.broker.service_restarts[key] = object()
            with (
                patch.object(self.broker.ready, 'wait', side_effect=wait),
                patch('shiraz.service_process.ServiceProcess') as spawn,
            ):
                write_frame(caller, dict(self.request, op='open_service'))
                self.assertTrue(waiting.wait(3))
                spawn.assert_not_called()
                if changed:
                    self.fixture.enroll(
                        'test.target',
                        version=2,
                        services=[dict(name='test.target.Worker', exported='true')],
                    )
                else:
                    target = Mock(key=key, references=0, started=True, closed=False)
                    target.process.poll.return_value = None
                    peer, endpoint = socket.socketpair()
                    self.channels.append(peer)
                    target.open_endpoint.return_value = endpoint
                with self.broker.lock:
                    if not changed:
                        self.broker.targets[key] = target
                        self.broker.processes.add(target)
                    del self.broker.service_restarts[key]
                    self.broker.ready.notify_all()
                reply, descriptors = read_packet(caller, idle_timeout=3)
                try:
                    self.assertEqual(reply['ok'], not changed, reply)
                    self.assertEqual(len(descriptors), 0 if changed else 1)
                    if changed:
                        self.assertIn('generation changed', reply['error'])
                finally:
                    for descriptor in descriptors:
                        os.close(descriptor)
                spawn.assert_not_called()
                if not changed:
                    self.assertTrue(
                        self.exchange(caller, dict(op='release_service', lease=reply['lease']))[
                            'ok'
                        ]
                    )
                    with self.broker.lock:
                        self.broker.targets.pop(key)

    def test_unavailable_service_discovery_preserves_caller_authorization(self):
        caller = self.channel(self.caller)
        missing = [
            dict(self.request, package='test.missing'),
            dict(self.request, component='test.target.Missing'),
            dict(op='resolve_service_action', package='test.target', action='missing'),
            dict(op='resolve_service_action', package='test.missing', action='missing'),
        ]
        for op in ('open_service_for_start', 'open_service_for_stop'):
            missing.extend(
                [
                    dict(self.request, op=op, package='test.missing'),
                    dict(self.request, op=op, component='test.target.Missing'),
                ]
            )
        for request in missing:
            result = self.exchange(caller, request)
            self.assertFalse(result['ok'])
            self.assertIs(result.get('unavailable'), True, result)
            self.assertNotIn('service', result)
        for op in ('resolve_service', 'open_service_for_start', 'open_service_for_stop'):
            denied = self.exchange(self.channel(self.other), dict(self.request, op=op))
            self.assertFalse(denied['ok'])
            self.assertNotIn('unavailable', denied)
        # A stale caller cannot turn an authorization failure into absence,
        # even when the target itself is missing.
        self.fixture.enroll('test.caller', version=2)
        for request in missing:
            denied = self.exchange(caller, request)
            self.assertFalse(denied['ok'])
            self.assertNotIn('unavailable', denied)
            self.assertIn('generation changed', denied['error'])
        self.assertFalse(self.broker.processes)

    def test_action_discovery_preserves_authorization_and_rejects_ambiguity(self):
        service = dict(
            name='test.target.Worker',
            exported='true',
            permission='test.PRIVATE',
            filters=[dict(action=['test.BIND'], priority='4')],
        )
        self.fixture.enroll(
            'test.target',
            version=2,
            services=[service],
            defined_permissions=[dict(name='test.PRIVATE', protection=2)],
        )
        request = dict(op='resolve_service_action', package='test.target', action='test.BIND')
        trusted = self.channel(self.caller)
        result = self.exchange(trusted, request)
        self.assertTrue(result['ok'], result)
        self.assertEqual(result['service']['component'], 'test.target.Worker')
        self.assertFalse(self.exchange(self.channel(self.other), request)['ok'])
        self.assertFalse(self.broker.processes, 'Discovery must not start a service')
        for invalid in (
            dict(request, action=''),
            dict(request, action=True),
            dict(request, package='../bad'),
            dict(request, unknown=[]),
            dict(request, action='missing'),
        ):
            self.assertFalse(self.exchange(trusted, invalid)['ok'])
        competing = dict(service, name='test.target.Other')
        self.fixture.enroll('test.target', version=3, services=[service, competing])
        self.assertIn('Ambiguous', self.exchange(trusted, request)['error'])
        constrained = dict(service, filters=[dict(action=['test.BIND'], mime=['text/plain'])])
        self.fixture.enroll('test.target', version=4, services=[constrained])
        self.assertIn('No matching', self.exchange(trusted, request)['error'])
        # Reinstall the permission declaration so data matching reaches authorization.
        constrained = dict(
            service,
            filters=[
                dict(
                    action=['test.BIND'],
                    mime=['text/*'],
                    scheme=['content'],
                    category=['test.CATEGORY'],
                )
            ],
        )
        self.fixture.enroll(
            'test.target',
            version=5,
            services=[constrained],
            defined_permissions=[dict(name='test.PRIVATE', protection=2)],
        )
        typed = dict(
            request,
            mime='text/plain',
            scheme='content',
            has_data=True,
            categories=['test.CATEGORY'],
        )
        self.assertTrue(self.exchange(trusted, typed)['ok'])
        for invalid in (
            dict(typed, mime='image/png'),
            dict(typed, scheme='https'),
            dict(typed, categories=['wrong']),
            dict(typed, has_data=False),
            dict(typed, categories='test.CATEGORY'),
            dict(typed, has_data=1),
        ):
            self.assertFalse(self.exchange(trusted, invalid)['ok'])
        self.fixture.enroll('test.caller', version=2)
        self.assertIn('generation changed', self.exchange(trusted, request)['error'])

    def test_retirement_closes_owned_services_and_sessions_only(self):
        target_channel = self.channel(self.target)
        caller_channel = self.channel(self.caller)
        target = Mock(key=('test.target', self.target['apk_sha256'], 'test.target.Worker'))
        unrelated = Mock(key=('test.other', self.other['apk_sha256'], 'test.other.Worker'))
        self.broker.processes.update((target, unrelated))
        self.broker.targets.update({target.key: target, unrelated.key: unrelated})
        with self.assertRaisesRegex(fixtures.FormatError, 'changed since retirement'):
            self.broker.retire_package(
                'test.target', expected_sha256='0' * 64, expected_uid=self.target['uid']
            )
        target.close.assert_not_called()
        self.assertTrue(self.exchange(caller_channel, self.request)['ok'])
        self.assertTrue(
            self.broker.retire_package(
                'test.target',
                expected_sha256=self.target['apk_sha256'],
                expected_uid=self.target['uid'],
            )
        )
        target.close.assert_called_once()
        unrelated.close.assert_not_called()
        self.assertNotIn(target, self.broker.processes)
        self.assertNotIn(target.key, self.broker.targets)
        with self.assertRaises(EOFError):
            read_frame(target_channel, idle_timeout=3)
        self.assertFalse(self.exchange(caller_channel, self.request)['ok'])
        self.assertTrue(
            self.exchange(caller_channel, dict(op='lookup_package', package='test.caller'))['ok']
        )
        with self.assertRaises(PermissionError):
            self.channel(self.target)

    def test_retirement_between_resolution_and_worker_admission(self):
        resolved = threading.Event()
        resume = threading.Event()
        original = self.broker._resolve

        def paused(identity, request):
            result = original(identity, request)
            resolved.set()
            if not resume.wait(timeout=3):
                raise RuntimeError('Test admission barrier timed out')
            return result

        channel = self.channel(self.caller)
        with (
            patch.object(self.broker, '_resolve', side_effect=paused),
            patch('shiraz.service_process.ServiceProcess') as spawn,
        ):
            write_frame(channel, dict(self.request, op='open_service'))
            try:
                self.assertTrue(resolved.wait(timeout=3))
                self.broker.retire_package(
                    'test.target',
                    expected_sha256=self.target['apk_sha256'],
                    expected_uid=self.target['uid'],
                )
            finally:
                resume.set()
            self.assertFalse(read_frame(channel, idle_timeout=3)['ok'])
            spawn.assert_not_called()
        self.assertFalse(self.broker.processes)

    def test_retirement_failure_attempts_other_workers_and_retains_cleanup(self):
        failed = Mock(key=('test.target', self.target['apk_sha256'], 'test.target.One'))
        other = Mock(key=('test.target', self.target['apk_sha256'], 'test.target.Two'))
        failed.close.side_effect = RuntimeError('Controlled shutdown failure')
        self.broker.processes.update((failed, other))
        self.broker.targets.update({failed.key: failed, other.key: other})
        with self.assertRaises(RetirementCleanupError):
            self.broker.retire_package(
                'test.target',
                expected_sha256=self.target['apk_sha256'],
                expected_uid=self.target['uid'],
            )
        other.close.assert_called_once()
        self.assertFalse(self.broker.targets)
        self.assertEqual(list(self.broker.pending_retirements.values()), [{failed}])
        with self.assertRaisesRegex(RuntimeError, 'cleanup is incomplete'):
            self.channel(self.target)
        failed.close.side_effect = None
        self.assertFalse(
            self.broker.retire_package(
                'test.target',
                expected_sha256=self.target['apk_sha256'],
                expected_uid=self.target['uid'],
            )
        )
        self.assertFalse(self.broker.pending_retirements)
        self.assertEqual(failed.close.call_count, 2)

    def test_quiescence_preserves_identity_and_requires_explicit_resume(self):
        channel = self.channel(self.caller)
        old = self.fixture.registry.identity('test.target')
        failed = Mock(key=('test.target', old['sha256'], 'test.target.Worker'))
        failed.close.side_effect = RuntimeError('Controlled shutdown failure')
        self.broker.processes.add(failed)
        self.broker.targets[failed.key] = failed
        with self.assertRaisesRegex(PermissionError, 'identity changed'):
            self.broker.quiesce_package(
                'test.target', expected_sha256='0' * 64, expected_uid=old['uid']
            )
        failed.close.assert_not_called()
        with self.assertRaisesRegex(RuntimeError, 'cleanup is incomplete'):
            self.broker.quiesce_package(
                'test.target', expected_sha256=old['sha256'], expected_uid=old['uid']
            )
        self.assertEqual(self.fixture.registry.identity('test.target'), old)
        with self.assertRaisesRegex(RuntimeError, 'cleanup is incomplete'):
            self.broker.resume_package(
                'test.target', expected_sha256=old['sha256'], expected_uid=old['uid']
            )
        self.assertFalse(self.exchange(channel, self.request)['ok'])
        failed.close.side_effect = None
        self.assertTrue(
            self.broker.quiesce_package(
                'test.target', expected_sha256=old['sha256'], expected_uid=old['uid']
            )
        )
        with self.assertRaisesRegex(RuntimeError, 'quiesced'):
            self.channel(self.target)
        updated = self.fixture.enroll('test.target', version=2)
        self.assertEqual(updated['uid'], old['uid'])
        with self.assertRaisesRegex(PermissionError, 'identity changed'):
            self.broker.resume_package(
                'test.target', expected_sha256=old['sha256'], expected_uid=old['uid']
            )
        self.assertTrue(
            self.broker.resume_package(
                'test.target', expected_sha256=updated['apk_sha256'], expected_uid=old['uid']
            )
        )
        self.channel(updated)
        self.assertFalse(self.broker.quiesced)

    def test_authenticated_manifest_metadata_tracks_enrolled_generation(self):
        channel = self.channel(self.caller)
        request = dict(op='package_metadata', package='test.target')
        result = self.exchange(channel, request)
        self.assertTrue(result['ok'], result)
        facts = result['metadata']
        self.assertEqual(facts['package'], 'test.target')
        self.assertEqual(facts['uid'], str(self.target['uid']))
        self.assertEqual(facts['sha256'], self.target['apk_sha256'])
        self.assertEqual(facts['service.0.name'], 'test.target.Worker')
        self.assertEqual(facts['service.0.permission'], 'test.PRIVATE')
        self.assertNotIn('signers', facts)
        self.assertIsNone(self.exchange(channel, dict(request, package='test.absent'))['metadata'])
        for invalid in (dict(request, uid=self.other['uid']), dict(request, package='../bad')):
            self.assertFalse(self.exchange(channel, invalid)['ok'])
        updated = self.fixture.enroll('test.target', version=2, services=[])
        current = self.exchange(channel, request)['metadata']
        self.assertEqual(current['sha256'], updated['apk_sha256'])
        self.assertEqual(current['service.count'], '0')
        self.fixture.registry.retire(
            'test.target', expected_sha256=updated['apk_sha256'], expected_uid=updated['uid']
        )
        self.assertIsNone(self.exchange(channel, request)['metadata'])
        self.fixture.enroll('test.target', application_label='x' * 60000)
        self.assertIn('frame limit', self.exchange(channel, request)['error'])
        self.assertTrue(
            self.exchange(channel, dict(op='lookup_package', package='test.caller'))['ok']
        )
        self.fixture.enroll('test.caller', version=2)
        self.assertIn('generation changed', self.exchange(channel, request)['error'])
        self.assertFalse(self.broker.processes)

    def test_metadata_stream_preserves_large_snapshot_and_bounds(self):
        channel = self.channel(self.caller)
        request = dict(op='package_metadata_stream', package='test.target')
        extra = {f'field.{i}': 'é' * 100 for i in range(5000)}
        extra.update(package='test.target')
        with patch('shiraz.runtime.application_metadata', return_value=extra):
            write_frame(channel, request)
            collected, frames = {}, 0
            while True:
                reply = read_frame(channel)
                self.assertTrue(reply['ok'], reply)
                self.assertLess(len(json.dumps(reply).encode()), 60000)
                self.assertFalse(set(collected) & set(reply['metadata']))
                collected.update(reply['metadata'])
                frames += 1
                if not reply['more']:
                    break
            self.assertGreater(frames, 1)
            self.assertEqual(len(collected), 5003)
            self.assertEqual(collected['field.4999'], 'é' * 100)
            self.assertEqual(collected['sha256'], self.target['apk_sha256'])
        self.assertEqual(
            self.exchange(channel, dict(request, package='test.absent')),
            dict(ok=True, metadata=None, more=False),
        )
        for extra in ({'oversize': 'x' * 60000}, {f'k{i}': '' for i in range(65537)}):
            with patch('shiraz.runtime.application_metadata', return_value=extra):
                self.assertFalse(self.exchange(channel, request)['ok'])
            self.assertTrue(
                self.exchange(channel, dict(op='lookup_package', package='test.caller'))['ok']
            )
        self.fixture.enroll('test.caller', version=2)
        self.assertIn('generation changed', self.exchange(channel, request)['error'])

    def test_pending_intent_authenticated_wire(self):
        owner = self.channel(self.caller)
        other = self.channel(self.other)
        request = dict(
            op='pending_intent_obtain',
            kind=0,
            request_code=7,
            flags=0,
            identity=base64.b64encode(b'filter').decode(),
            payload=base64.b64encode(b'opaque-snapshot').decode(),
        )
        result = self.exchange(owner, request)
        self.assertTrue(result['ok'], result)
        facts = result['pending_intent']
        self.assertEqual(facts['package'], 'test.caller')
        self.assertEqual(facts['uid'], self.caller['uid'])
        self.assertNotIn('payload', facts)
        lookup = dict(op='pending_intent_inspect', token=facts['token'])
        self.assertEqual(self.exchange(other, lookup)['pending_intent'], facts)
        cancel = dict(lookup, op='pending_intent_cancel')
        self.assertFalse(self.exchange(other, cancel)['ok'])
        self.assertTrue(self.exchange(owner, cancel)['ok'])
        self.assertTrue(self.exchange(other, lookup)['canceled'])
        for bad in (
            dict(request, uid=self.other['uid']),
            dict(request, package='test.other'),
            dict(request, identity='Zg==\n'),
            dict(request, payload='Zh=='),
            dict(request, payload=''),
            dict(request, flags=True),
        ):
            self.assertFalse(self.exchange(owner, bad)['ok'])
        # Invalid input cannot desynchronize the authenticated channel.
        token = self.exchange(owner, request)['pending_intent']['token']
        self.fixture.enroll('test.caller', version=2)
        self.assertTrue(self.exchange(other, dict(lookup, token=token))['canceled'])
        self.assertFalse(self.exchange(owner, request)['ok'])

    def test_authenticated_package_lookup(self):
        channel = self.channel(self.caller)
        listing = self.exchange(channel, dict(op='list_packages'))
        self.assertTrue(listing['ok'])
        self.assertEqual(
            {row['package'] for row in listing['packages']},
            {'test.caller', 'test.target', 'test.other'},
        )
        self.assertFalse(
            self.exchange(channel, dict(op='list_packages', caller_uid=self.target['uid']))['ok']
        )
        expected = [{'package': self.target['package'], 'uid': self.target['uid']}]
        for selector in ({'uid': self.target['uid']}, {'package': self.target['package']}):
            self.assertEqual(
                self.exchange(channel, dict(op='lookup_package', **selector)),
                {'ok': True, 'packages': expected},
            )
        self.assertEqual(
            self.exchange(channel, dict(op='lookup_package', uid=0)), {'ok': True, 'packages': []}
        )
        for invalid in (
            {'uid': True},
            {'uid': -1},
            {'uid': '10000'},
            {'package': 'a/b'},
            {'uid': self.target['uid'], 'caller_uid': self.caller['uid']},
            {'uid': self.target['uid'], 'package': self.target['package']},
        ):
            self.assertFalse(self.exchange(channel, dict(op='lookup_package', **invalid))['ok'])
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(self.exchange(channel, dict(op='list_packages'))['ok'])
        self.assertFalse(
            self.exchange(channel, dict(op='lookup_package', uid=self.target['uid']))['ok']
        )
        disabled = self.fixture.enroll('test.caller', version=3, application_enabled='false')
        self.assertFalse(
            self.exchange(
                self.channel(disabled), dict(op='lookup_package', uid=self.target['uid'])
            )['ok']
        )
        self.assertFalse(self.broker.processes)

    def test_package_enumeration_retirement_and_reply_limit(self):
        channel = self.channel(self.caller)
        self.fixture.registry.retire(
            self.target['package'],
            expected_uid=self.target['uid'],
            expected_sha256=self.target['apk_sha256'],
        )
        listing = self.exchange(channel, dict(op='list_packages'))
        self.assertNotIn(self.target['package'], {row['package'] for row in listing['packages']})
        for index in range(70):
            self.fixture.enroll('test.' + 'a' * 900 + str(index))
        oversized = self.exchange(channel, dict(op='list_packages'))
        self.assertFalse(oversized['ok'])
        self.assertIn('reply size limit', oversized['error'])
        self.assertNotIn('packages', oversized)
        self.assertTrue(
            self.exchange(channel, dict(op='lookup_package', package='test.caller'))['ok']
        )
        self.assertFalse(self.broker.processes)

    def test_permission_queries(self):
        channel = self.channel(self.caller)
        for kind in ('package', 'uid'):
            for subject, expected in ((self.caller, 0), (self.other, -1), (self.target, -1)):
                self.assertEqual(
                    self.exchange(
                        channel,
                        dict(
                            op='check_permission',
                            permission='test.PRIVATE',
                            kind=kind,
                            subject=subject[kind],
                        ),
                    ),
                    {'ok': True, 'result': expected},
                )
        for changes in (
            {'kind': 'signers'},
            {'subject': True},
            {'permission': 'a/b'},
            {'caller_uid': 10000},
        ):
            request = dict(
                op='check_permission',
                permission='test.PRIVATE',
                kind='uid',
                subject=self.caller['uid'],
            )
            request.update(changes)
            self.assertFalse(self.exchange(channel, request)['ok'])
        self.fixture.enroll('test.caller', version=2, permissions=['test.PRIVATE'])
        self.assertFalse(
            self.exchange(
                channel,
                dict(
                    op='check_permission',
                    permission='test.PRIVATE',
                    kind='package',
                    subject='test.caller',
                ),
            )['ok']
        )

    def test_verified_signature_comparisons(self):
        channel = self.channel(self.caller)
        for kind in ('package', 'uid'):
            for first, second, expected in (
                (self.caller, self.target, 0),
                (self.caller, self.other, -3),
                (self.caller, self.caller, 0),
            ):
                reply = self.exchange(
                    channel,
                    dict(
                        op='compare_signatures', kind=kind, first=first[kind], second=second[kind]
                    ),
                )
                self.assertEqual(reply, {'ok': True, 'result': expected})
        self.assertEqual(
            self.exchange(
                channel,
                dict(op='compare_signatures', kind='uid', first=0, second=self.target['uid']),
            ),
            {'ok': True, 'result': -4},
        )
        for invalid in (
            {'kind': 'signers', 'first': 'a', 'second': 'a'},
            {'kind': 'uid', 'first': True, 'second': self.target['uid']},
            {'kind': 'package', 'first': 'test.caller', 'second': 'a/b'},
            {
                'kind': 'uid',
                'first': self.caller['uid'],
                'second': self.target['uid'],
                'caller_uid': self.caller['uid'],
            },
        ):
            self.assertFalse(self.exchange(channel, dict(op='compare_signatures', **invalid))['ok'])
        self.fixture.enroll('test.caller', version=2)
        self.assertFalse(
            self.exchange(
                channel,
                dict(
                    op='compare_signatures',
                    kind='package',
                    first='test.target',
                    second='test.target',
                ),
            )['ok']
        )

    def test_authenticator_admission_pinning_and_connection_revocation(self):
        self.check_authenticator_admission(False)

    def test_cross_signer_add_account_admission_and_revocation(self):
        self.check_authenticator_admission(True)

    def check_authenticator_admission(self, add_only):
        descriptor = dict(
            type='test.accounts',
            component='test.auth.Service',
            custom_tokens=False,
            label=0,
            icon=0,
            smallIcon=0,
            accountPreferences=0,
        )
        with patch('shiraz.package_registry.inspect_authenticators', return_value=[descriptor]):
            owner = self.fixture.enroll(
                'test.auth',
                services=[
                    dict(
                        name='test.auth.Service',
                        exported='true',
                        permission='android.permission.BIND_ACCOUNT_AUTHENTICATOR',
                    )
                ],
            )
        instances = []

        class Target:
            def __init__(target, broker, service):
                target.key = (service['package'], service['apk_sha256'], service['component'])
                target.references, target.started, target.closed = 0, False, False
                target.process, target.peers, target.uids = target, [], []
                instances.append(target)

            def poll(target):
                return 0 if target.closed else None

            def open_authenticator_endpoint(target, uid):
                peer, client = socket.socketpair()
                target.peers.append(peer)
                target.uids.append(uid)
                return client

            def open_add_account_endpoint(target, uid, package, account_type):
                self.assertEqual(
                    package, self.other['package'] if uid == self.other['uid'] else owner['package']
                )
                self.assertEqual(account_type, 'test.accounts')
                return target.open_authenticator_endpoint(uid)

            def open_endpoint(target, uid):
                raise AssertionError('Authenticator admitted through ordinary service endpoint')

            def close(target):
                target.closed = True
                for peer in target.peers:
                    peer.close()

        method = 'open_add_account_endpoint' if add_only else 'open_authenticator_endpoint'
        caller = self.other if add_only else self.caller
        request = dict(
            op='open_add_account' if add_only else 'open_authenticator',
            type='test.accounts',
            expected_uid=owner['uid'],
            expected_sha256=owner['apk_sha256'],
        )
        first, second, denied = self.channel(caller), self.channel(owner), self.channel(self.other)
        clients = []
        try:
            with patch('shiraz.service_process.ServiceProcess', Target):
                for bad in (
                    dict(request, expected_uid=owner['uid'] + 1),
                    dict(request, expected_sha256='0' * 64),
                    dict(request, caller_uid=owner['uid']),
                    dict(request, package=owner['package']),
                ):
                    self.assertFalse(self.exchange(first, bad)['ok'])
                self.assertFalse(
                    self.exchange(denied, dict(request, op='open_authenticator'))['ok']
                )
                self.assertFalse(instances)
                leases = []
                for channel in (first, second):
                    write_frame(channel, request)
                    reply, fds = read_packet(channel, idle_timeout=3)
                    self.assertTrue(reply['ok'])
                    self.assertEqual(len(fds), 1)
                    clients.append(socket.socket(fileno=fds[0]))
                    leases.append(reply['lease'])
                self.assertEqual(len(instances), 1)
                target = instances[0]
                self.assertEqual(target.uids, [caller['uid'], owner['uid']])
                self.assertFalse(
                    self.exchange(second, dict(op='release_service', lease=leases[0]))['ok']
                )
                clients[0].sendall(b'before-release')
                target.peers[0].settimeout(3)
                self.assertEqual(target.peers[0].recv(20), b'before-release')
                self.assertTrue(
                    self.exchange(first, dict(op='release_service', lease=leases[0]))['ok']
                )
                self.assertFalse(target.closed)
                self.assertEqual(target.references, 1)
                self.assertEqual(target.peers[0].recv(1), b'')
                with self.assertRaises(OSError):
                    clients[0].sendall(b'after-release')
                # A failed admitted connection must not consume a lease slot.
                with patch.object(Target, method, side_effect=OSError('fixture failure')):
                    for _ in range(5):
                        self.assertFalse(self.exchange(first, request)['ok'])
                    self.assertEqual(target.references, 1)
                # Revoke during admission: do not hand out the newly created endpoint.
                original = getattr(Target, method)

                def quiesce(target, uid, *args):
                    endpoint = original(target, uid, *args)
                    with self.broker.lock:
                        self.broker.quiesced.add(owner['package'])
                    return endpoint

                with patch.object(Target, method, quiesce):
                    self.assertFalse(self.exchange(first, request)['ok'])
                with self.broker.lock:
                    self.broker.quiesced.remove(owner['package'])
                self.assertEqual(target.references, 1)
                target.peers[-1].settimeout(3)
                self.assertEqual(target.peers[-1].recv(1), b'')
                # Keep the worker alive independently to prove session closure revokes
                # this endpoint rather than depending on process shutdown.
                target.started = True
                second.shutdown(socket.SHUT_RDWR)
                second.close()
                target.peers[1].settimeout(3)
                self.assertEqual(target.peers[1].recv(1), b'')
                self.assertFalse(target.closed)
        finally:
            for client in clients:
                client.close()

    def test_shared_target_keeps_authorization_per_caller(self):
        instances = []

        class Target:
            def __init__(target, registry, service):
                target.key = (service['package'], service['apk_sha256'], service['component'])
                target.start_state = ServiceStartState()
                target.references = 0
                target.started = False
                target.closed = False
                target.process = target
                target.lock = threading.Lock()
                target.peers = []
                target.uids = []
                instances.append(target)

            def poll(target):
                return 0 if target.closed else None

            def open_endpoint(target, uid):
                peer, client = socket.socketpair()
                target.peers.append(peer)
                target.uids.append(uid)
                return client

            def close(target):
                target.closed = True
                for peer in target.peers:
                    peer.close()

        second = self.fixture.enroll('test.second', permissions=['test.PRIVATE'])
        first_channel = self.channel(self.caller)
        second_channel = self.channel(second)
        request = dict(self.request, op='open_service')
        leases = []
        with patch('shiraz.service_process.ServiceProcess', Target):
            for channel in (first_channel, second_channel):
                write_frame(channel, request)
                reply, descriptors = read_packet(channel, idle_timeout=3)
                try:
                    self.assertTrue(reply['ok'])
                    self.assertEqual(len(descriptors), 1)
                    leases.append(reply['lease'])
                finally:
                    for descriptor in descriptors:
                        os.close(descriptor)
            self.assertEqual(len(instances), 1)
            self.assertEqual(instances[0].uids, [self.caller['uid'], second['uid']])
            self.assertEqual(instances[0].references, 2)
            self.assertFalse(self.exchange(self.channel(self.other), request)['ok'])
            self.fixture.enroll('test.caller', version=2, permissions=['test.PRIVATE'])
            self.assertFalse(self.exchange(first_channel, request)['ok'])
            self.assertEqual(instances[0].references, 2)
            self.assertFalse(
                self.exchange(second_channel, dict(op='release_service', lease=leases[0]))['ok']
            )
            self.assertEqual(instances[0].references, 2)
            self.assertFalse(
                self.exchange(second_channel, dict(op='service_started', started=True))['ok']
            )
            worker_channel = self.broker.open_session(
                self.target['package'], self.target['apk_sha256'], worker=instances[0]
            )
            self.channels.append(worker_channel)
            completion = dict(op='service_start_completed', start_id=1, restart_mode=1)
            self.assertFalse(self.exchange(second_channel, completion)['ok'])
            self.assertFalse(self.exchange(worker_channel, completion)['ok'])
            self.assertFalse(
                self.exchange(worker_channel, dict(op='service_started', started=1))['ok']
            )
            self.assertEqual(
                self.exchange(worker_channel, dict(op='service_started', started=True)),
                {'ok': True},
            )
            for invalid in (
                dict(completion, start_id=True),
                dict(completion, start_id=0),
                dict(completion, restart_mode=True),
                dict(completion, restart_mode=4),
                dict(completion, extra=1),
            ):
                self.assertFalse(self.exchange(worker_channel, invalid)['ok'])
            for mode in range(4):
                report = dict(completion, start_id=mode + 1, restart_mode=mode)
                if mode == 3:
                    report['intent'] = 'AQ=='
                self.assertEqual(self.exchange(worker_channel, report), {'ok': True})
                self.assertEqual(instances[0].last_start_id, mode + 1)
                self.assertEqual(instances[0].restart_mode, mode)
                self.assertFalse(self.exchange(worker_channel, report)['ok'])
            self.assertFalse(self.exchange(worker_channel, completion)['ok'])
            self.assertEqual(instances[0].restart_mode, 3)
            self.assertEqual(
                self.exchange(first_channel, dict(op='release_service', lease=leases[0])),
                {'ok': True},
            )
            self.assertFalse(
                self.exchange(first_channel, dict(op='release_service', lease=leases[0]))['ok']
            )
            self.assertEqual(instances[0].references, 1)
            self.assertFalse(instances[0].closed)
            self.assertEqual(
                self.exchange(second_channel, dict(op='release_service', lease=leases[1])),
                {'ok': True},
            )
            self.assertFalse(instances[0].closed)
            self.assertEqual(instances[0].references, 0)
            self.assertEqual(
                self.exchange(worker_channel, dict(op='service_started', started=False)),
                {'ok': True},
            )
            # Wait for retirement, which deliberately follows the reply.
            with instances[0].lock:
                pass
            for _ in range(100):
                if instances[0].closed:
                    break
                threading.Event().wait(0.01)
            self.assertTrue(instances[0].closed)
            self.assertIsNone(instances[0].restart_mode)
            self.assertFalse(
                self.exchange(worker_channel, dict(op='service_started', started=True))['ok']
            )
            # Failed endpoint admission must roll back its lease immediately.
            with patch.object(
                Target, 'open_endpoint', side_effect=OSError('fixture admission failure')
            ):
                for _ in range(6):
                    self.assertIn(
                        'fixture admission failure', self.exchange(second_channel, request)['error']
                    )
                    self.assertFalse(self.broker.processes)
                    self.assertFalse(self.broker.targets)
            self.broker.close()
        self.assertTrue(instances[0].closed)
        self.assertEqual(instances[0].references, 0)
        self.assertFalse(self.broker.targets)

    def test_fd_passed_to_child(self):
        channel = self.channel(self.caller)
        program = '''import json,socket,sys
from shiraz.service_broker import read_frame,write_frame
channel=socket.socket(fileno=int(sys.argv[1]))
write_frame(channel,json.loads(sys.argv[2]))
print(json.dumps(read_frame(channel,idle_timeout=3)))
channel.close()
'''
        result = subprocess.run(
            [sys.executable, '-c', program, str(channel.fileno()), json.dumps(self.request)],
            pass_fds=(channel.fileno(),),
            env=os.environ.copy(),
            capture_output=True,
            timeout=10,
            check=True,
        )
        reply = json.loads(result.stdout)
        self.assertTrue(reply['ok'])
        self.assertEqual(reply['service']['uid'], self.target['uid'])
        self.assertEqual(reply['service']['apk_sha256'], self.target['apk_sha256'])

    def test_malformed_transport_and_shutdown(self):
        channel = self.channel(self.caller)
        channel.sendall(struct.pack('<III', 0x315A4853, MAX_FRAME + 1, 0))
        channel.settimeout(3)
        self.assertEqual(channel.recv(1), b'')
        idle = self.channel(self.caller)
        partial = self.channel(self.caller)
        partial.sendall(b'\x00')
        self.broker.close()
        idle.settimeout(3)
        self.assertEqual(idle.recv(1), b'')
        partial.settimeout(3)
        self.assertEqual(partial.recv(1), b'')
        with self.assertRaises(RuntimeError):
            self.channel(self.caller)
        self.assertEqual(self.broker.sessions, {})


if __name__ == '__main__':
    unittest.main()
