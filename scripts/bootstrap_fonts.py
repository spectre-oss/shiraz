#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Install hash-checked OFL Noto fonts privately; never modify host font configuration."""
from pathlib import Path
import hashlib
import base64
import urllib.request
import urllib.parse
import urllib.error
import time

ROOT = Path(__file__).resolve().parents[1]
REVISION = 'ffebf8c1ee449e544955a7e813c54f9b73848eac'
FILES = {
    'NotoSerif-LICENSE.txt': '5e0da210fb04058a8c0087985d2d456b931c2579811a49655721d3cf0c36b6d6',
    'Roboto-Regular.ttf': '9ca9debb09459bf4e3e7f826f5cd0f35f253902b85684921fce2ba3f28dd0f50',
    'Roboto-LICENSE.txt': 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30',
    'DroidSansMono.ttf': '12b552de765dc1265d64f9f5566649930dde4dba07da0251d9f92801e70a1047',
    'Android-Fonts-NOTICE.txt': '38751245389e1e23f73e6f5384b5cbe7fa972cc4410c5adc9c04b082a0b9561a',
    'NotoSerif-Regular.ttf': '1e65d9bbb6864fd4a32b96a8f0de3739b667c9eac793ec2d6d00bab7adb5a965',
    'NotoSerif-Bold.ttf': 'd245c6187216b7e2f943431525beb29a22c2141dd1c9e4a9de14f3899e6ab699',
    'NotoSerif-Italic.ttf': 'bb2ad30a8b95b367ed9576cea42bc085b7e69a6f81e6fe53ac3db6f10c7ff5f3',
    'NotoSerif-BoldItalic.ttf': 'e9421c1ca461ff0e5c9c3149e05a9880f92378def342dd09a567ee104330eef5',
    'NotoNaskhArabic-Regular.ttf': '2f4b88e6ee50fa82c617e2d1d4ba18281cb1c6cd71c3af3ec64970c23995db4b',
    'NotoNaskhArabic-Bold.ttf': '7432f2350487d9f784b47a24526d9fc48f2712bb6c2ff602898b104b0c26ee80',

    'NotoSansArabic-Regular.ttf': 'ceea25b464a656dc3b26849bab9356740401af62aedf1bfa8b7f0d9b75925b1b',
    'NotoSansArabic-Bold.ttf': 'ed2711b387750ae991b6a980b8dd16fdd65e6702b97e9f52adf3a8edf09ef4df',
    'NotoSansHebrew-Regular.ttf': 'a7fa16fffb27bedb060a0866267c29e9859aeb9c21cc33f5b3aaf6eb062eca85',
    'NotoSansHebrew-Bold.ttf': 'a30243d1c625eaf63bb889f036fe9de97e81d9248976f89e6e4252f9668c832e',
    'NotoSansLao-Regular.ttf': '4a64d40850990992913d4be44b2e87a93d52f76882c1f0c0755edcb12ec57aa2',
    'NotoSansLao-Bold.ttf': 'b6275642ccc7f773b59f67b3240f939342d89ddadc98e0a6c3ebca2e2ce10e19',
    'NotoSans-Regular.ttf': 'b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5',
    'NotoSans-Bold.ttf': 'c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a',
    'NotoColorEmoji.ttf': 'c2f19f6a404baa7da7a710b018c2892d7b51386983ddca146811f76aea0b6861',
    'NotoEmoji-OFL.txt': '500bb1ccf43df7bbb522112f9133a52b16e1c35e809632f5d8609b179152de5b',
    'LICENSE': '0dab92d0544f7b233403f14b84a663bdbfa746982eda629e7f4f9ffe1b036feb',
}

def download(url, name):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(url, timeout=60) as response:
                return response.read(16 * 1024 * 1024 + 1)
        except urllib.error.HTTPError as error:
            if error.code not in (429, 500, 502, 503, 504) or attempt == 4:
                raise
        except urllib.error.URLError:
            if attempt == 4:
                raise
        delay = 2 ** attempt
        print(f'Retrying font download {name} in {delay}s', flush=True)
        time.sleep(delay)

def main():
    target = ROOT / '.local/share/shiraz/fonts'
    target.mkdir(parents=True, exist_ok=True)
    (target / 'NotoEmoji[wght].ttf').unlink(missing_ok=True)
    for name, digest in FILES.items():
        output = target / name
        if output.exists():
            data = output.read_bytes()
        else:
            path = 'LICENSE' if name == 'LICENSE' else f'hinted/ttf/{name.split("-")[0]}/{name}'
            url = f'https://raw.githubusercontent.com/notofonts/noto-fonts/{REVISION}/{path}'
            if name == 'NotoColorEmoji.ttf':
                url = 'https://raw.githubusercontent.com/googlefonts/noto-emoji/v2.042/fonts/NotoColorEmoji.ttf'
            elif name.startswith('NotoEmoji'):
                filename = 'OFL.txt' if name.endswith('.txt') else name
                url = 'https://raw.githubusercontent.com/google/fonts/e7db6ca603fd4862010383ff17ffd6cc4ffac1c0/ofl/notoemoji/' + urllib.parse.quote(filename)
            android = name.startswith('NotoSerif-') or name in ('Roboto-Regular.ttf','Roboto-LICENSE.txt','DroidSansMono.ttf','Android-Fonts-NOTICE.txt')
            if name in ('Roboto-Regular.ttf','Roboto-LICENSE.txt'):
                file = 'NOTICE' if name.endswith('.txt') else name
                url = f'https://android.googlesource.com/platform/external/roboto-fonts/+/android-14.0.0_r1/{file}?format=TEXT'
            elif name.startswith('NotoSerif-'):
                file = 'NOTICE' if name.endswith('.txt') else name
                url = f'https://android.googlesource.com/platform/external/noto-fonts/+/android-14.0.0_r1/notoserif/{file}?format=TEXT'
            elif android:
                file = 'NOTICE' if name.endswith('.txt') else name
                url = f'https://android.googlesource.com/platform/frameworks/base/+/android-4.4_r1/data/fonts/{file}?format=TEXT'
            data = download(url, name)
            if android:
                data = base64.b64decode(data, validate=True)
        if hashlib.sha256(data).hexdigest() != digest:
            raise SystemExit(f'Noto font checksum mismatch: {name}')
        if not output.exists():
            temporary = output.with_suffix(output.suffix + '.tmp')
            temporary.write_bytes(data)
            temporary.replace(output)

if __name__ == '__main__':
    main()
