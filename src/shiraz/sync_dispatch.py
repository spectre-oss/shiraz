# SPDX-License-Identifier: GPL-3.0-only
"""Trusted host sync dispatch; automatic scheduling/retry policy is separate."""

import select
import time

from .broker_wire import read_packet
from .service_process import ServiceProcess


class SyncDispatchError(RuntimeError):
    def __init__(self, claim, *, retained):
        super().__init__(
            'Sync execution failed; claim '
            + ('retained pending worker cleanup' if retained else 'released after worker cleanup')
        )
        self.claim = claim
        self.retained = retained


def outcome(reply):
    fields = {
        'ok',
        'canceled',
        'hard_error',
        'soft_error',
        'full_sync_requested',
        'more_records',
        'partial_sync_unavailable',
        'delay_until',
        'already_in_progress',
    }
    if not isinstance(reply, dict) or set(reply) != fields or reply['ok'] is not True:
        raise ValueError('Invalid or uncertain sync completion')
    if any(type(reply[key]) is not bool for key in fields - {'delay_until'}):
        raise ValueError('Invalid sync result flags')
    if type(reply['delay_until']) is not int or not 0 <= reply['delay_until'] <= 0x7FFFFFFFFFFFFFFF:
        raise ValueError('Invalid sync delay')
    if reply['canceled']:
        return 'canceled'
    if reply['hard_error']:
        return 'failed'
    if any(
        reply[key]
        for key in (
            'soft_error',
            'full_sync_requested',
            'more_records',
            'partial_sync_unavailable',
            'already_in_progress',
        )
    ):
        return 'retry'
    return 'succeeded'


def run(broker, request_id, *, timeout=60):
    """Run one queued request, retaining uncertain ownership until cleanup is proven.

    The caller is a trusted host coordinator, never an app-selected identity.
    This does not start an automatic retry loop or imply connectivity/account readiness.
    """
    if type(timeout) not in (int, float) or not 0 < timeout <= 600:
        raise ValueError('Invalid sync dispatch timeout')
    worker = None
    with broker.lock:
        if broker.closed:
            raise RuntimeError('Sync broker closed')
        pending = next(
            (
                item
                for item in broker.registry.pending_sync_requests()
                if item['request_id'] == request_id
            ),
            None,
        )
        if pending is None:
            return None
        adapter = pending['adapter']
        if adapter['package'] in broker.quiesced or any(
            key[0] == adapter['package'] for key in broker.pending_retirements
        ):
            return None
        key = (adapter['package'], adapter['apk_sha256'], adapter['component'])
        worker = broker.targets.get(key)
        if worker is not None and worker.closed:
            worker = None
        if worker is None:
            process_name = adapter['process']
            if process_name.startswith(':'):
                process_name = adapter['package'] + process_name
            if (
                adapter['package'],
                adapter['apk_sha256'],
                process_name,
            ) in broker.live_processes or len(broker.processes) >= 64:
                return None
        elif worker.references >= 64:
            return None
        claim = broker.registry.claim_sync_request(request_id)
        if claim is None:
            return None
        if claim['adapter'] != adapter:
            broker.registry.finish_sync_request(request_id, claim['claim_token'], success=False)
            return None
        try:
            if worker is None:
                worker = ServiceProcess(broker, adapter)
                broker.targets[key] = worker
                broker.processes.add(worker)
            worker.references += 1
        except BaseException as error:
            # A constructor may have spawned a descendant before failing.
            raise SyncDispatchError(claim, retained=True) from error
    channel = None
    result = None
    failure = None
    try:
        channel = worker.dispatch_sync(claim)
        deadline = time.monotonic() + timeout
        while True:
            if broker.closed or not any(
                item['request_id'] == request_id for item in broker.registry.pending_sync_requests()
            ):
                raise RuntimeError('Sync operation invalidated during execution')
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError('Sync completion deadline')
            if select.select([channel], [], [], min(1, remaining))[0]:
                reply, _ = read_packet(channel, idle_timeout=remaining, max_descriptors=0)
                break
        result = dict(status=outcome(reply), delay_until=reply['delay_until'])
    except (OSError, ValueError, RuntimeError, EOFError) as error:
        failure = error
    finally:
        if channel is not None:
            channel.close()
        try:
            broker._release(worker)
        except BaseException as error:
            raise SyncDispatchError(claim, retained=True) from error
    if failure is not None:
        retained = not worker.cleanup_complete
        if not retained:
            broker.registry.finish_sync_request(request_id, claim['claim_token'], success=False)
        raise SyncDispatchError(claim, retained=retained) from failure
    applied = broker.registry.finish_sync_request(
        request_id,
        claim['claim_token'],
        success=result['status'] == 'succeeded',
        status=result['status'],
        delay_until=result['delay_until'],
    )
    return dict(result, applied=applied)
