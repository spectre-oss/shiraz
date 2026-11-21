# SPDX-License-Identifier: GPL-3.0-only
"""Authenticator descriptors from verified APK service metadata."""

from .apk import FormatError
from .service_descriptors import inspect_service_descriptors

ACTION = 'android.accounts.AccountAuthenticator'
META = 'android.accounts.AccountAuthenticator'


def _decode(service, attr, resolve, resource_id):
    account_type = attr('accountType')
    if account_type and account_type.startswith('@'):
        kind, account_type = resolve(resource_id(account_type))
        if kind != 3:
            raise FormatError('Authenticator account type must be a string')
    if (
        not isinstance(account_type, str)
        or not account_type
        or len(account_type) > 1024
        or '\x00' in account_type
    ):
        raise FormatError('Invalid authenticator account type')
    custom = attr('customTokens') or 'false'
    if custom.startswith('@'):
        kind, value = resolve(resource_id(custom))
        if kind != 0x12:
            raise FormatError('Authenticator customTokens must be boolean')
        custom = 'true' if value else 'false'
    if custom not in ('true', 'false', '0', '1'):
        raise FormatError('Invalid authenticator customTokens')
    descriptor = dict(
        type=account_type, component=service['name'], custom_tokens=custom in ('true', '1')
    )
    for name in ('label', 'icon', 'smallIcon', 'accountPreferences'):
        value = attr(name)
        descriptor[name] = resource_id(value) if value else 0
    return descriptor


def inspect_authenticators(apk, manifest):
    return inspect_service_descriptors(
        apk,
        manifest,
        action=ACTION,
        meta_key=META,
        root_tag='account-authenticator',
        decode=_decode,
    )
