# SPDX-License-Identifier: GPL-3.0-only
"""Private parent-owned stdio control for a long-lived shared service host."""

import json
import re

from .apk import FormatError
from .host_session import HostSession
from .launch_settings import normalize_settings
from .package_registry import NAME


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate control field')
        result[key] = value
    return result


def serve(registry, source, sink, host_factory=HostSession, library=None):
    # No listening socket or app-provided classpath. The launcher owns the pipes.
    def reply(value):
        sink.write(json.dumps(value, separators=(',', ':')) + '\n')
        sink.flush()

    with (
        host_factory(registry) if library is None else host_factory(registry, library=library)
    ) as host:
        reply({'ready': True, 'protocol': 1})
        while True:
            line = source.readline(4097)
            if not line:
                return
            if len(line) > 4096 or not line.endswith(b'\n'):
                reply({'ok': False, 'error': 'Invalid control frame'})
                return  # Do not interpret a suffix of an oversized request.
            try:
                request = json.loads(line, object_pairs_hook=unique_object)
                if not isinstance(request, dict):
                    raise ValueError('Control request must be an object')
                op = request.get('op')
                fields = (
                    {'op', 'package', 'sha256'}
                    if op == 'start'
                    else ({'op', 'package', 'launch_id'} if op == 'stop' else {'op'})
                )
                if op in ('retire', 'quiesce', 'resume'):
                    fields = {'op', 'package', 'sha256', 'uid'}
                if op == 'identity':
                    fields = {'op', 'package'}
                if op == 'start' and 'requester' in request:
                    fields.add('requester')
                    requester = request['requester']
                    if (
                        not isinstance(requester, dict)
                        or set(requester) != {'package', 'launch_id'}
                        or not isinstance(requester['package'], str)
                        or len(requester['package']) > 1024
                        or not NAME.fullmatch(requester['package'])
                        or not isinstance(requester['launch_id'], str)
                        or not re.fullmatch('[0-9a-f]{32}', requester['launch_id'])
                    ):
                        raise ValueError('Invalid requester identity')
                if op == 'start' and 'settings' in request:
                    fields.add('settings')
                if (
                    op
                    not in (
                        'start',
                        'stop',
                        'retire',
                        'quiesce',
                        'resume',
                        'identity',
                        'status',
                        'shutdown',
                    )
                    or set(request) != fields
                ):
                    raise ValueError('Unsupported control request')
                if op in ('start', 'stop', 'retire', 'quiesce', 'resume', 'identity'):
                    package = request['package']
                    if (
                        not isinstance(package, str)
                        or len(package) > 1024
                        or not NAME.fullmatch(package)
                    ):
                        raise ValueError('Invalid package')
                if op == 'start':
                    settings = normalize_settings(request.get('settings'))
                if op in ('start', 'retire', 'quiesce', 'resume'):
                    digest = request['sha256']
                    if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
                        raise ValueError('Invalid APK digest')
                if op in ('retire', 'quiesce', 'resume') and (
                    type(request['uid']) is not int or not 10000 <= request['uid'] <= 19999
                ):
                    raise ValueError('Invalid package UID')
                if op == 'stop' and (
                    not isinstance(request['launch_id'], str)
                    or not re.fullmatch('[0-9a-f]{32}', request['launch_id'])
                ):
                    raise ValueError('Invalid launch identity')
            except (ValueError, UnicodeError, FormatError):
                reply({'ok': False, 'error': 'Invalid control request'})
                continue
            events = host.collect()
            try:
                if op == 'start':
                    options = {'requester': request['requester']} if 'requester' in request else {}
                    process = host.start(
                        package, expected_sha256=digest, settings=settings, **options
                    )
                    result = {'pid': process.pid, 'launch_id': host.launch_ids[process]}
                    work = host.apps[package][1]
                    result.update(stdout=str(work / 'stdout.log'), stderr=str(work / 'stderr.log'))
                    if library is not None:
                        result['inbox'] = str(host.inboxes[process])
                elif op == 'stop':
                    result = {'stopped': host.stop(package, request['launch_id'])}
                elif op == 'retire':
                    result = {
                        'retired': host.retire(
                            package, expected_sha256=digest, expected_uid=request['uid']
                        )
                    }
                elif op in ('quiesce', 'resume'):
                    operation = host.quiesce if op == 'quiesce' else host.resume
                    result = {
                        'quiesced' if op == 'quiesce' else 'resumed': operation(
                            package, expected_sha256=digest, expected_uid=request['uid']
                        )
                    }
                elif op == 'identity':
                    with host.broker.lock:
                        if any(key[0] == package for key in host.broker.pending_retirements):
                            raise RuntimeError('Package retirement cleanup is incomplete')
                    try:
                        identity = host.registry.identity(package)
                    except PermissionError:
                        identity = None
                    result = {
                        'found': identity is not None,
                        'identity': identity,
                        'quiesced': package in host.broker.quiesced,
                    }
                elif op == 'shutdown':
                    host.close()
                    reply({'ok': True, 'finished': events})
                    return
                else:
                    result = {'running': list(host.apps)}
                reply({'ok': True, 'finished': events, **result})
            except (FormatError, PermissionError, OSError, RuntimeError) as error:
                reply({'ok': False, 'finished': events, 'error': str(error)[:512]})
