#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Package native APK libraries using Shiraz's shared bounded extractor."""
from shiraz.apk import MAX_NATIVE_ENTRY
from shiraz.native_payload import RESERVED, extract_native as _extract_native


def extract_native(apk, destination, architecture='x86_64'):
    return _extract_native(apk, destination, architecture, max_native_entry=MAX_NATIVE_ENTRY)
