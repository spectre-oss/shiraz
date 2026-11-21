# SPDX-License-Identifier: GPL-3.0-only
"""Authenticated shared-job operations; caller identity comes from the broker."""

import base64

from . import job_work
from .job_definition import integer

OPERATIONS = {
    'enqueue_job',
    'schedule_job',
    'cancel_job',
    'get_job',
    'list_job_ids',
    'cancel_all_jobs',
}


def handle(broker, identity, request):
    op = request['op']
    fields = (
        {'op', 'definition'}
        if op == 'schedule_job'
        else {'op'}
        if op in ('list_job_ids', 'cancel_all_jobs')
        else {'op', 'id'}
    )
    if op == 'enqueue_job':
        fields = {'op', 'definition', 'payload'}
    if op not in OPERATIONS or set(request) != fields:
        raise ValueError('Invalid persisted job request')
    job_id = integer(request['id'], -(1 << 31), (1 << 31) - 1) if 'id' in request else None
    with broker.lock:
        if broker.closed or identity.package in broker.quiesced:
            raise PermissionError('Job caller unavailable')
        if op in ('schedule_job', 'enqueue_job'):
            if op == 'enqueue_job':
                encoded = request['payload']
                if type(encoded) is not str or len(encoded) > 43692:
                    raise ValueError('Invalid job work payload')
                payload = base64.b64decode(encoded, validate=True)
                if base64.b64encode(payload).decode('ascii') != encoded:
                    raise ValueError('Noncanonical job work payload')
                stored = job_work.enqueue(
                    broker.registry, identity.uid, identity.sha256, request['definition'], payload
                )
            else:
                stored = broker.registry.store_shared_job(
                    identity.uid, identity.sha256, request['definition']
                )
            job_id = stored['id']
            reply = dict(ok=True, revision=stored['revision'])
            revision = stored['revision']
        elif op == 'cancel_all_jobs':
            removed, canceled_claims = broker.registry.cancel_all_shared_jobs(
                identity.uid, identity.sha256
            )
            reply = dict(ok=True, removed=removed)
            revision = None
        elif op == 'cancel_job':
            removed = broker.registry.cancel_persisted_job(identity.uid, identity.sha256, job_id)
            reply = dict(ok=True, removed=removed)
            revision = None
        else:
            jobs = broker.registry.list_persisted_jobs(identity.uid, identity.sha256)
            if op == 'list_job_ids':
                return dict(ok=True, ids=[job['id'] for job in jobs])
            job = next((job for job in jobs if job['id'] == job_id), None)
            return dict(ok=True, definition=None if job is None else job['definition'])
        obsolete = [
            run
            for run in broker.job_dispatches
            if run.claim['owner_uid'] == identity.uid
            and (
                run.claim['token'] in canceled_claims
                if op == 'cancel_all_jobs'
                else run.claim['id'] == job_id
                and run.claim['apk_sha256'] == identity.sha256
                and run.claim['revision'] != revision
            )
        ]
    # The mutation is committed. Teardown failure must never be reported as a
    # failed write or trigger an implicit retry. Its old claim stays reserved.
    cleanup_pending = False
    for run in obsolete:
        try:
            run.worker.close()
            cleanup_pending |= not run.worker.terminal_cleanup_complete
        except Exception:
            cleanup_pending = True
    try:
        with broker.registry.connect() as db:
            if op == 'cancel_all_jobs':
                remaining = {
                    row['token']
                    for row in db.execute(
                        'SELECT token FROM persisted_job_claims WHERE owner_uid=?', (identity.uid,)
                    )
                }
                cleanup_pending |= bool(remaining & canceled_claims)
            else:
                claim = db.execute(
                    'SELECT revision FROM persisted_job_claims WHERE owner_uid=? AND job_id=?',
                    (identity.uid, job_id),
                ).fetchone()
                cleanup_pending |= claim is not None and claim['revision'] != revision
    except Exception:
        # Even an unavailable post-write observation cannot undo the mutation.
        cleanup_pending = True
    reply['cleanup_pending'] = cleanup_pending
    return reply
