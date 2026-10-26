#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise the native notification protocol on a private D-Bus session bus."""
import fcntl
import json
from pathlib import Path
import shutil
import subprocess
import struct
import sys
import threading
import zipfile
from art_smoke import environment, HOST, JARS

ROOT = Path(__file__).resolve().parents[1]
if '--bus' not in sys.argv:
    raise SystemExit(subprocess.run(['dbus-run-session', '--', sys.executable, __file__, '--bus']).returncode)
import dbus
import dbus.service
from dbus.mainloop.glib import DBusGMainLoop
from gi.repository import GLib
DBusGMainLoop(set_as_default=True)
work = ROOT / 'build/notification-smoke'
work.mkdir(parents=True, exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Notification smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
interface = 'org.freedesktop.Notifications'
trace = []


class Server(dbus.service.Object):
    def __init__(self, connection):
        self.name = dbus.service.BusName(interface, connection)
        super().__init__(self.name, '/org/freedesktop/Notifications')
        self.next_id = 0x80000000
        self.active = set()
        self.body = True
        self.expire_on_cancel = set()

    @dbus.service.method(interface, in_signature='', out_signature='as')
    def GetCapabilities(self):
        return (['body'] if self.body else []) + ['body-markup', 'sound']

    @dbus.service.method(interface, in_signature='susssasa{sv}i', out_signature='u', async_callbacks=('success', 'failure'))
    def Notify(self, application, replaces, icon, summary, body, actions, hints, timeout, success, failure):
        trace.append({'app': str(application), 'replaces': int(replaces), 'title': str(summary), 'body': str(body),
                      'actions': list(actions), 'urgency': int(hints['urgency']), 'quiet': bool(hints['suppress-sound']), 'timeout': int(timeout)})
        if summary == 'error':
            failure(dbus.exceptions.DBusException('fixture rejection', name=interface + '.FixtureError'))
            return
        if summary == 'timeout':
            return  # A genuinely unanswered method call must hit the client's deadline.
        if summary == 'zero':
            success(dbus.UInt32(0))
            return
        if replaces:
            if int(replaces) not in self.active:
                failure(dbus.exceptions.DBusException('unknown replacement', name=interface + '.UnknownId'))
                return
            identifier = int(replaces)
        else:
            self.next_id += 1
            identifier = self.next_id
            self.active.add(identifier)
        if summary == 'expire before cancel':
            self.expire_on_cancel.add(identifier)
        success(dbus.UInt32(identifier))
        if summary == 'drop owner':
            self.active.discard(identifier)
            GLib.timeout_add(10, lambda: (connection.release_name(interface), False)[1])
        if timeout == 20:
            def expire():
                self.active.discard(identifier)
                self.NotificationClosed(dbus.UInt32(identifier), dbus.UInt32(1))
                return False
            GLib.timeout_add(20, expire)

    @dbus.service.method(interface, in_signature='u', out_signature='')
    def CloseNotification(self, identifier):
        self.active.discard(int(identifier))
        if int(identifier) in self.expire_on_cancel:
            self.expire_on_cancel.remove(int(identifier))
            self.NotificationClosed(identifier, dbus.UInt32(1))
            raise dbus.exceptions.DBusException('already expired', name=interface + '.UnknownId')
        self.NotificationClosed(identifier, dbus.UInt32(3))

    @dbus.service.signal(interface, signature='uu')
    def NotificationClosed(self, identifier, reason):
        pass


connection = dbus.SessionBus()
server = Server(connection)
# Intercept one call below the typed Python service to send a genuinely wrong
# wire signature; using a decorated UInt32 method cannot produce that reply.
def malformed_reply(bus, message):
    if (message.get_type() == dbus.lowlevel.MESSAGE_TYPE_METHOD_CALL
            and message.get_interface() == interface and message.get_member() == 'Notify'
            and message.get_args_list()[3] == 'malformed'):
        reply = dbus.lowlevel.MethodReturnMessage(message)
        reply.append('wrong type', signature='s')
        bus.send_message(reply)
        return dbus.lowlevel.HANDLER_RESULT_HANDLED
    return dbus.lowlevel.HANDLER_RESULT_NOT_YET_HANDLED
connection.add_message_filter(malformed_reply)
loop = GLib.MainLoop()
thread = threading.Thread(target=loop.run, daemon=True)
thread.start()
try:
    classes = work / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    framework = ROOT / 'build/framework/classes'
    core = ROOT / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    classpath = str(framework) + ':' + str(core)
    subprocess.run(['javac', '--release', '8', '-cp', classpath, '-d', str(classes), str(ROOT / 'tests/java/DesktopNotificationsSmoke.java'), str(ROOT / 'tests/java/NotificationPolicySmoke.java')], check=True)
    commands = [('openjdk', ['java', '-Xcheck:jni', '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(classes) + ':' + classpath, 'DesktopNotificationsSmoke'])]
    dex = work / 'classes.dex'
    subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)], env=environment(work), check=True)
    jar = work / 'smoke.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        entry = zipfile.ZipInfo('classes.dex')
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack('<HH', 0xffff, padding) + b'\0' * padding
        archive.writestr(entry, dex.read_bytes())
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    commands.append(('art', [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
                            '-Xcheck:jni', '-Xusejit:false', '-Xint', '-Xnoimage-dex2oat', '-Xcompiler:' + str(HOST / 'bin/dex2oat'),
                            '-Djava.library.path=' + str(ROOT / 'build'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar), 'DesktopNotificationsSmoke']))
    policy_commands = [(runtime, [*command[:-1], 'NotificationPolicySmoke', str(work / (runtime + '-policy.db'))]) for runtime, command in commands]
    for runtime, command in policy_commands:
        result = subprocess.run([*command, 'true'], env=environment(work), capture_output=True, text=True, timeout=10)
        (work / (runtime + '-policy-available.log')).write_text(result.stdout + result.stderr)
        assert result.returncode == 0 and 'NOTIFICATION_POLICY_AVAILABILITY_OK' in result.stdout
    marker = 'DESKTOP_NOTIFICATION_PROTOCOL_AND_LIFETIME_OK'
    for runtime, command in commands:
        trace.clear()
        result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=30)
        (work / (runtime + '-stdout.log')).write_text(result.stdout)
        (work / (runtime + '-stderr.log')).write_text(result.stderr)
        (work / (runtime + '-protocol.json')).write_text(json.dumps(trace, ensure_ascii=False, indent=2) + '\n')
        if result.returncode or marker not in result.stdout.splitlines():
            raise RuntimeError('Notification smoke failed in ' + runtime + '; inspect build/notification-smoke/' + runtime + '-stderr.log')
        assert trace[0]['title'] == 'سلام 😀' and trace[0]['body'] == '&lt;b&gt;plain&lt;/b&gt; &amp; text 😀'
        assert trace[0]['quiet'] is True and trace[0]['urgency'] == 1 and trace[0]['timeout'] == -1 and trace[0]['actions'] == []
        assert trace[1]['replaces'] > 0 and trace[1]['quiet'] is False and trace[1]['urgency'] == 2 and trace[1]['timeout'] == 0
        assert not server.active
        print('DESKTOP_NOTIFICATIONS_OK ' + runtime)
    server.body = False
    for runtime, command in commands:
        no_body = subprocess.run([*command, 'no-body'], env=environment(work), capture_output=True, text=True, timeout=10)
        (work / (runtime + '-no-body.log')).write_text(no_body.stdout + no_body.stderr)
        assert no_body.returncode == 0 and 'NOTIFICATION_BODY_CAPABILITY_REJECTED' in no_body.stdout
    server.body = True
    for runtime, command in commands:
        lost = subprocess.run([*command, 'owner-loss'], env=environment(work), capture_output=True, text=True, timeout=10)
        (work / (runtime + '-owner-loss.log')).write_text(lost.stdout + lost.stderr)
        assert lost.returncode == 0 and 'NOTIFICATION_OWNER_LOSS_REJECTED' in lost.stdout
        connection.request_name(interface)
    loop.quit()
    thread.join(timeout=2)
    server.remove_from_connection()
    connection.release_name(interface)
    for runtime, command in policy_commands:
        result = subprocess.run([*command, 'false'], env=environment(work), capture_output=True, text=True, timeout=10)
        (work / (runtime + '-policy-unavailable.log')).write_text(result.stdout + result.stderr)
        assert result.returncode == 0 and 'NOTIFICATION_POLICY_AVAILABILITY_OK' in result.stdout
    # A live bus without a notification daemon must fail explicitly, too.
    for runtime, command in commands:
        missing = subprocess.run([*command, 'missing'], env=environment(work), capture_output=True, text=True, timeout=10)
        (work / (runtime + '-missing-service.log')).write_text(missing.stdout + missing.stderr)
        assert missing.returncode == 0 and 'NOTIFICATION_UNAVAILABLE_AND_LIFETIME_OK' in missing.stdout
        absent = subprocess.run([*command, 'missing'], env={**environment(work), 'DBUS_SESSION_BUS_ADDRESS': 'unix:path=' + str(work / 'missing-bus')}, capture_output=True, text=True, timeout=10)
        (work / (runtime + '-missing-bus.log')).write_text(absent.stdout + absent.stderr)
        assert absent.returncode == 0 and 'NOTIFICATION_UNAVAILABLE_AND_LIFETIME_OK' in absent.stdout
    (work / 'result.json').write_text(json.dumps({'runtimes': ['openjdk', 'art'], 'isolated_bus': True, 'missing_daemon_rejected': True}, indent=2) + '\n')
finally:
    loop.quit()
    thread.join(timeout=2)
