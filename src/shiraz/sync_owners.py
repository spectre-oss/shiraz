# SPDX-License-Identifier: GPL-3.0-only
"""Persistent host sync ownership; no system-image preference or execution grant."""

import json

from .apk import FormatError


def candidates(db, boolean):
    result = {}
    for row in db.execute('SELECT * FROM packages ORDER BY uid'):
        manifest = json.loads(row['manifest'])
        if not boolean(manifest.get('application_enabled'), True):
            continue
        seen = set()
        for descriptor in manifest.get('sync_adapters', []):
            services = [
                item
                for item in manifest.get('services', [])
                if item['name'] == descriptor['component']
            ]
            if len(services) != 1:
                raise FormatError('Sync adapter service identity is ambiguous or absent')
            service = services[0]
            if not boolean(service.get('enabled'), True):
                continue
            key = (descriptor['authority'], descriptor['account_type'])
            if key in seen:
                raise FormatError('Duplicate sync authority/account pair in one package')
            seen.add(key)
            result.setdefault(key, []).append((row, service, descriptor))
    return result


def reconcile(db, boolean):
    """Keep the available package/UID/signer owner, otherwise choose the oldest live UID."""
    available = candidates(db, boolean)
    previous = {
        (row['authority'], row['account_type']): row
        for row in db.execute('SELECT * FROM sync_adapter_owners')
    }
    for key, entries in available.items():
        prior = previous.get(key)
        selected = entries[0][0]
        if prior is not None:
            selected = next(
                (
                    row
                    for row, _, _ in entries
                    if all(row[field] == prior[field] for field in ('package', 'uid', 'signers'))
                ),
                selected,
            )
        db.execute(
            'INSERT OR REPLACE INTO sync_adapter_owners VALUES(?,?,?,?,?)',
            (*key, selected['package'], selected['uid'], selected['signers']),
        )
    for key in previous.keys() - available.keys():
        db.execute('DELETE FROM sync_adapter_owners WHERE authority=? AND account_type=?', key)


def selected(db, boolean):
    owners = {
        (row['authority'], row['account_type']): row
        for row in db.execute('SELECT * FROM sync_adapter_owners')
    }
    result = []
    for key, entries in sorted(candidates(db, boolean).items()):
        owner = owners.get(key)
        matching = [
            (row, service, descriptor)
            for row, service, descriptor in entries
            if owner is not None
            and all(row[field] == owner[field] for field in ('package', 'uid', 'signers'))
        ]
        if len(matching) != 1:
            raise FormatError('Sync adapter owner unavailable or ambiguous')
        row, service, descriptor = matching[0]
        result.append(
            dict(
                descriptor,
                package=row['package'],
                uid=row['uid'],
                apk_sha256=row['sha256'],
                process=service.get('process') or row['package'],
            )
        )
    return result
