# SPDX-License-Identifier: GPL-3.0-only
"""Declared sync capabilities; discovery alone never authorizes execution."""

from .apk import FormatError
from .service_descriptors import inspect_service_descriptors

ACTION = 'android.content.SyncAdapter'


def _decode(service, attr, resolve, resource_id):
    def string(name, required=True):
        value = attr(name)
        if value is None and not required:
            return None
        if value and value.startswith('@'):
            kind, value = resolve(resource_id(value))
            if kind != 3:
                raise FormatError('Sync adapter string resource required')
        if not isinstance(value, str) or not value or len(value) > 1024 or '\x00' in value:
            raise FormatError('Invalid sync adapter ' + name)
        return value

    def boolean(name, default):
        value = attr(name)
        if value is None:
            return default
        if value.startswith('@'):
            kind, value = resolve(resource_id(value))
            if kind != 0x12:
                raise FormatError('Sync adapter boolean resource required')
            return bool(value)
        if value not in ('true', 'false', '0', '1'):
            raise FormatError('Invalid sync adapter ' + name)
        return value in ('true', '1')

    return dict(
        component=service['name'],
        authority=string('contentAuthority'),
        account_type=string('accountType'),
        user_visible=boolean('userVisible', True),
        supports_uploading=boolean('supportsUploading', True),
        always_syncable=boolean('isAlwaysSyncable', False),
        allow_parallel_syncs=boolean('allowParallelSyncs', False),
        settings_activity=string('settingsActivity', False),
    )


def inspect_sync_adapters(apk, manifest):
    return inspect_service_descriptors(
        apk, manifest, action=ACTION, meta_key=ACTION, root_tag='sync-adapter', decode=_decode
    )
