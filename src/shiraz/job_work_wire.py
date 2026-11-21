# SPDX-License-Identifier: GPL-3.0-only
"""Private job-worker messages. Claim identity is supplied only by the host."""

import base64

from . import job_work

OPERATIONS = {'work_dequeue', 'work_complete'}


def handle(registry, claim, request):
    if type(request) is not dict or request.get('op') not in OPERATIONS:
        raise ValueError('Invalid job work operation')
    op = request['op']
    expected = {'op'} if op == 'work_dequeue' else {'op', 'id'}
    if set(request) != expected:
        raise ValueError('Invalid job work fields')
    if claim is None:
        raise PermissionError('Job work requires a host-owned claim')
    uid, job_id, token = claim['owner_uid'], claim['id'], claim['token']
    if op == 'work_complete':
        complete = job_work.complete(registry, uid, job_id, token, request['id'])
        return dict(
            ok=True,
            completed=complete,
            drained=complete and job_work.seal_drained(registry, uid, job_id, token),
        )
    item = job_work.dequeue(registry, uid, job_id, token)
    if item is None:
        return dict(ok=True, item=None, drained=job_work.seal_drained(registry, uid, job_id, token))
    item = dict(item, payload=base64.b64encode(item['payload']).decode('ascii'))
    return dict(ok=True, item=item, drained=False)
