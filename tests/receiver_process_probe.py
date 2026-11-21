# SPDX-License-Identifier: GPL-3.0-only
"""Signed receiver admission and actual worker lifetime integration evidence."""

import os
import socket
import struct
from unittest.mock import patch

from shiraz.broker_wire import HEADER, MAGIC, read_packet
from shiraz.receiver_process import ReceiverProcess
from shiraz.service_broker import ServiceBroker, read_frame, write_frame


def payload(component, action='complete'):
    def string(value):
        if value is None:
            return struct.pack('<i', -1)
        data = value.encode('utf-16-le')
        return struct.pack('<i', len(data) // 2) + data + b'\0' * (len(data) % 4)

    return (
        struct.pack('<i', 1)
        + b''.join(string(value) for value in (action, None, None, None, component))
        + struct.pack('<iiiii', 0, -1, 0, 0, 0)
    )


def verify(case, registry):
    evidence = []
    for mode in ('complete', 'cross-app', 'failed', 'idle', 'quiesce'):
        target = 'org.shiraz.sessiontwo' if mode == 'cross-app' else 'org.shiraz.backgroundfixture'
        caller = 'org.shiraz.sessionone' if mode == 'cross-app' else target
        component = (
            'org.shiraz.sessionfixture.ExportedReceiver'
            if mode == 'cross-app'
            else target + '.HeadlessReceiver'
        )
        record = registry.identity(target)
        with (
            ServiceBroker(registry) as broker,
            broker.open_session(caller, registry.identity(caller)['sha256']) as client,
        ):
            with patch.object(ReceiverProcess, 'lifetime', 1 if mode == 'idle' else 30):
                write_frame(client, dict(op='open_receiver', package=target, component=component))
                reply, descriptors = read_packet(client, 10)
                case.assertTrue(reply['ok'], (mode, reply))
                case.assertEqual(len(descriptors), 1)
                case.assertEqual(reply['receiver']['apk_sha256'], record['sha256'])
                worker = next(iter(broker.processes))
                process_name = reply['receiver']['process']
                if process_name.startswith(':'):
                    process_name = target + process_name
                process_key = (target, record['sha256'], process_name)
                server = broker.live_processes[process_key]
                session_thread = broker.sessions[server]
                case.assertEqual(broker.identities[server].process_name, process_name)
                with socket.socket(fileno=descriptors[0]) as endpoint:
                    if mode in ('complete', 'cross-app'):
                        data = payload(target + '/' + component)
                        endpoint.sendall(HEADER.pack(MAGIC, len(data), 0) + data)
                        case.assertEqual(read_packet(endpoint, 20), ({'ok': True}, []))
                        case.assertEqual(
                            worker.process.wait(timeout=20),
                            0,
                            worker.evidence.joinpath('stderr.log').read_text(),
                        )
                    elif mode == 'failed':
                        data = payload(target + '/' + component, 'throw')
                        endpoint.sendall(HEADER.pack(MAGIC, len(data), 0) + data)
                        with case.assertRaises((EOFError, ConnectionResetError)):
                            read_packet(endpoint, 20)
                        case.assertNotEqual(worker.process.wait(timeout=10), 0)
                    elif mode == 'quiesce':
                        broker.quiesce_package(
                            target, expected_sha256=record['sha256'], expected_uid=record['uid']
                        )
                    else:
                        case.assertNotEqual(worker.process.wait(timeout=10), 0)
                    worker.reaper.join(timeout=8)
                    case.assertFalse(worker.reaper.is_alive())
                    case.assertTrue(worker.cleanup_complete)
                    session_thread.join(timeout=5)
                    case.assertFalse(session_thread.is_alive())
                    case.assertNotIn(process_key, broker.live_processes)
                    if mode in ('complete', 'cross-app'):
                        output = worker.evidence.joinpath('stdout.log').read_text()
                        case.assertIn(
                            'EXPORTED_RECEIVER_DELIVERED'
                            if mode == 'cross-app'
                            else 'HEADLESS_RECEIVER_ASYNC_FINISHED',
                            output,
                        )
                        case.assertIn('SHIRAZ_RUNTIME_CLEANUP_COMPLETE', output)
                    if mode != 'quiesce':
                        operation = 'release_receiver' if mode == 'idle' else 'finish_receiver'
                        write_frame(client, dict(op=operation, lease=reply['lease']))
                        completion = read_frame(client, 10)
                        if mode == 'failed':
                            case.assertFalse(completion['ok'])
                            case.assertIn('exited unsuccessfully', completion['error'])
                            write_frame(
                                client,
                                dict(op='resolve_receiver', package=target, component=component),
                            )
                            case.assertTrue(read_frame(client, 5)['ok'])
                        else:
                            case.assertEqual(completion, {'ok': True})
                evidence.append(
                    dict(
                        mode=mode,
                        worker=str(worker.evidence),
                        status=worker.process.returncode,
                        cleanup_complete=worker.cleanup_complete,
                    )
                )
    # A private receiver cannot be admitted by a different enrolled app.
    with (
        ServiceBroker(registry) as broker,
        broker.open_session(
            'org.shiraz.sessionone', registry.identity('org.shiraz.sessionone')['sha256']
        ) as client,
    ):
        write_frame(
            client,
            dict(
                op='open_receiver',
                package='org.shiraz.backgroundfixture',
                component='org.shiraz.backgroundfixture.HeadlessReceiver',
            ),
        )
        reply, descriptors = read_packet(client, 5)
        for fd in descriptors:
            os.close(fd)
        case.assertFalse(reply['ok'])
        case.assertFalse(descriptors)
        case.assertFalse(broker.processes)
    return evidence
