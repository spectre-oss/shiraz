# SPDX-License-Identifier: GPL-3.0-only
"""Generate bounded, compressible native asset-stream test data."""
from pathlib import Path
import zipfile

path = Path('build/asset-stream-fixture.apk')
path.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(path, 'w') as archive:
    archive.writestr('assets/stored.txt', b'0123456789')
    archive.writestr('assets/compressed.txt', b'0123456789', compress_type=zipfile.ZIP_DEFLATED)
    chunk = b'x' * (1024 * 1024)
    for name, mib in [('large.bin', 128), ('oversized.bin', 384)]:
        entry = zipfile.ZipInfo('assets/' + name)
        entry.compress_type = zipfile.ZIP_DEFLATED
        with archive.open(entry, 'w') as output:
            for _ in range(mib):
                output.write(chunk)
            output.write(b'x')
