# SPDX-License-Identifier: GPL-3.0-only
"""Validate networking probe assertions on the host JVM, separately from ART proof."""
import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class NetworkProbeAssertions(unittest.TestCase):
    def test_network_assertions_on_host_jvm(self):
        fixtures = module('probe_tls_fixtures')
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as directory:
            work = Path(directory)
            fixtures.generate(work / 'assets')
            subprocess.run(['javac', '--release', '8', '-d', str(work),
                            str(ROOT / 'tests/api-probe/NetworkChecks.java'),
                            str(ROOT / 'tests/api-probe/TlsNetworkChecks.java'),
                            str(ROOT / 'tests/api-probe-host/NetworkHostTest.java')],
                           check=True, timeout=60)
            subprocess.run(['java', '-cp', str(work), 'org.shiraz.apiprobe.NetworkHostTest',
                            str(work / 'assets')], check=True, timeout=45)


if __name__ == '__main__':
    unittest.main()
