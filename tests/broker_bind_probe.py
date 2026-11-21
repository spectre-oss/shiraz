# SPDX-License-Identifier: GPL-3.0-only
"""Actual ART client binds a separately signed/enrolled APK through the host broker."""

import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from art_smoke import HOST, JARS, NATIVE, environment  # noqa: E402
from build_macos_fixture import append_aligned_dex  # noqa: E402

from shiraz.package_registry import PackageRegistry  # noqa: E402
from shiraz.service_broker import ServiceBroker  # noqa: E402
from shiraz.service_process import ServiceProcess  # noqa: E402


def main():
    work = ROOT / 'build/broker-bind-smoke'
    work.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work) as folder:
        temp = Path(folder)
        classes = temp / 'classes'
        classes.mkdir()
        core = (
            ROOT
            / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
        )
        subprocess.run(
            [
                'javac',
                '--release',
                '8',
                '-cp',
                str(ROOT / 'build/framework/classes') + ':' + str(core),
                '-d',
                str(classes),
                str(ROOT / 'tests/java/ServiceBrokerBindSmoke.java'),
            ],
            check=True,
            capture_output=True,
            timeout=30,
        )
        env = environment(temp)
        dex = temp / 'classes.dex'
        subprocess.run(
            [
                str(HOST / 'bin/dx'),
                '--dex',
                '--min-sdk-version=27',
                '--output=' + str(dex),
                str(classes),
            ],
            env=env,
            check=True,
            capture_output=True,
            timeout=30,
        )
        jar = temp / 'smoke.jar'
        with zipfile.ZipFile(jar, 'w'):
            pass
        append_aligned_dex(jar, dex)
        vm = HOST / 'bin/dalvikvm64'
        if not vm.is_file():
            vm = HOST / 'bin/dalvikvm'
        spawned = []

        def launch(broker, service):
            process = ServiceProcess(broker, service)
            spawned.append(process)
            return process

        with (
            patch('shiraz.service_process.ServiceProcess', side_effect=launch),
            ServiceBroker(PackageRegistry(sys.argv[1])) as broker,
        ):
            with broker.open_session(sys.argv[2], sys.argv[3]) as channel:
                command = [
                    str(vm),
                    '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                    '-Xcheck:jni',
                    '-Xusejit:false',
                    '-Xint',
                    '-Xnoimage-dex2oat',
                    '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'),
                    '-cp',
                    str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar),
                    'ServiceBrokerBindSmoke',
                    str(channel.fileno()),
                ]
                result = subprocess.run(
                    command,
                    env=env,
                    pass_fds=(channel.fileno(),),
                    capture_output=True,
                    timeout=45,
                    check=False,
                )
                (work / 'stdout.log').write_bytes(result.stdout)
                (work / 'stderr.log').write_bytes(result.stderr)
                assert result.returncode == 0, 'Inspect build/broker-bind-smoke/stderr.log'
                assert b'ART_CROSS_PACKAGE_BINDER_OK' in result.stdout
                assert b'ART_BROKER_START_FAILURE_REJECTED' in result.stdout

            # Closing the broker also closes endpoints, waits, and reaps the workers.
        children = [
            process for process in spawned if process.key[2] == 'org.shiraz.servicefixture.Worker'
        ]
        assert len(children) == 1 and children[0].process.poll() == 0, (
            'Service worker failed or survived broker close'
        )
        assert 'SERVICE_ONLY_DESTROYED' in (children[0].evidence / 'stdout.log').read_text()
        assert not broker.sessions and not broker.processes
        print('SIGNED_TARGET_ART_BINDER_AND_TEARDOWN_OK')


if __name__ == '__main__':
    main()
