// SPDX-License-Identifier: GPL-3.0-only
// Test-runner supervision only. Never infer a signal from a shell exit code.
#include "bionic_reference_child.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifndef SHIRAZ_REFERENCE_CHILD_CORE_ONLY
#include <jni.h>
#endif
extern char **environ;

static int64_t milliseconds(void) {
  struct timespec value;
  if (clock_gettime(CLOCK_MONOTONIC, &value)) return -1;
  return (int64_t)value.tv_sec * 1000 + value.tv_nsec / 1000000;
}
static void reap_owned(pid_t pid) {
  int status;
  // The dedicated runner must have exclusive wait ownership. Never signal
  // a PID that a nonblocking ownership check already says is gone/reaped.
  pid_t waited;
  do { waited = waitpid(pid, &status, WNOHANG); } while (waited < 0 && errno == EINTR);
  if (waited != 0) return;
  (void)kill(pid, SIGKILL);
  while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
}
int shiraz_reference_collect(char *const argv[], unsigned deadline_ms,
                            struct shiraz_reference_child *result) {
  size_t total = 0, count = 0;
  if (!argv || !argv[0] || argv[0][0] != '/' || !result ||
      !deadline_ms || deadline_ms > 120000) { errno = EINVAL; return -1; }
  while (argv[count]) {
    if (count == 32) { errno = E2BIG; return -1; }
    size_t length = strnlen(argv[count], 4097);
    if (length > 4096 || total + length + 1 > 32768) { errno = E2BIG; return -1; }
    total += length + 1;
    ++count;
  }
  struct sigaction disposition;
  if (sigaction(SIGCHLD, NULL, &disposition)) return -1;
  if (disposition.sa_handler != SIG_DFL || (disposition.sa_flags & SA_NOCLDWAIT)) {
    errno = ENOTSUP; return -1;
  }
  memset(result, 0, sizeof(*result));
  int64_t started = milliseconds();
  if (started < 0) return -1;
  int pipefd[2];
  if (pipe(pipefd)) return -1;
  int flags = fcntl(pipefd[0], F_GETFL);
  if (flags < 0 || fcntl(pipefd[0], F_SETFL, flags | O_NONBLOCK) ||
      fcntl(pipefd[0], F_SETFD, FD_CLOEXEC) ||
      (pipefd[1] > STDERR_FILENO && fcntl(pipefd[1], F_SETFD, FD_CLOEXEC))) {
    int error = errno; close(pipefd[0]); close(pipefd[1]); errno = error; return -1;
  }
  pid_t pid = fork();
  if (pid < 0) {
    int error = errno; close(pipefd[0]); close(pipefd[1]); errno = error; return -1;
  }
  if (!pid) {
    close(pipefd[0]);
    if (dup2(pipefd[1], STDOUT_FILENO) < 0 || dup2(pipefd[1], STDERR_FILENO) < 0) _exit(126);
    if (pipefd[1] != STDOUT_FILENO && pipefd[1] != STDERR_FILENO) close(pipefd[1]);
    execve(argv[0], argv, environ);
    _exit(127);
  }
  close(pipefd[1]);
  int status = 0, reaped = 0, eof = 0, error = 0;
  for (;;) {
    if (!eof) {
      unsigned char buffer[4096];
      for (;;) {
        ssize_t length = read(pipefd[0], buffer, sizeof(buffer));
        if (length > 0) {
          if ((size_t)length > sizeof(result->output) - result->output_size) {
            error = EOVERFLOW; break;
          }
          memcpy(result->output + result->output_size, buffer, (size_t)length);
          result->output_size += (size_t)length;
        } else if (!length) { eof = 1; close(pipefd[0]); pipefd[0] = -1; break; }
        else if (errno == EINTR) continue;
        else if (errno == EAGAIN || errno == EWOULDBLOCK) break;
        else { error = errno; break; }
      }
    }
    if (error) break;
    if (!reaped) {
      pid_t waited;
      do { waited = waitpid(pid, &status, WNOHANG); } while (waited < 0 && errno == EINTR);
      if (waited == pid) reaped = 1;
      else if (waited < 0) { error = errno; break; }
    }
    if (reaped && eof) break;
    int64_t now = milliseconds();
    if (now < 0) { error = errno; break; }
    int64_t remaining = (int64_t)deadline_ms - (now - started);
    if (remaining <= 0) {
      result->timed_out = 1;
      if (!reaped) {
        (void)kill(pid, SIGKILL);
        pid_t waited;
        do { waited = waitpid(pid, &status, 0); } while (waited < 0 && errno == EINTR);
        if (waited != pid) { error = errno; break; }
        reaped = 1;
      }
      break;
    }
    struct pollfd descriptor = {pipefd[0], POLLIN | POLLHUP, 0};
    int wait_ms = remaining < 10 ? (int)remaining : 10;
    int polled = poll(eof ? NULL : &descriptor, eof ? 0 : 1, wait_ms);
    if (polled < 0 && errno != EINTR) { error = errno; break; }
  }
  if (pipefd[0] >= 0) close(pipefd[0]);
  if (error) {
    if (!reaped && error != ECHILD) reap_owned(pid);
    errno = error;
    return -1;
  }
  if (!reaped || (!WIFEXITED(status) && !WIFSIGNALED(status))) {
    if (!reaped) reap_owned(pid);
    errno = ECHILD; return -1;
  }
  result->raw_wait_status = status;
  result->returncode = WIFSIGNALED(status) ? -WTERMSIG(status) : WEXITSTATUS(status);
  return 0;
}

#ifndef SHIRAZ_REFERENCE_CHILD_CORE_ONLY
static void failure(JNIEnv *env, const char *message) {
  jclass type = (*env)->FindClass(env, "java/io/IOException");
  if (type) (*env)->ThrowNew(env, type, message);
}
JNIEXPORT jstring JNICALL Java_BionicReferenceRunner_nChild(JNIEnv *env, jclass type,
                                                           jobjectArray arguments) {
  (void)type;
  char *argv[33] = {0};
  char *storage = NULL, *json = NULL;
  struct shiraz_reference_child *result = NULL;
  jstring answer = NULL;
  if (!arguments) { failure(env, "reference child argv is required"); return NULL; }
  jsize count = (*env)->GetArrayLength(env, arguments);
  if (count < 1 || count > 32) { failure(env, "reference child argv count bound"); return NULL; }
  storage = malloc(32768);
  result = malloc(sizeof(*result));
  if (!storage || !result) { failure(env, "reference collector allocation failed"); goto done; }
  size_t used = 0;
  for (jsize index = 0; index < count; ++index) {
    jstring argument = (jstring)(*env)->GetObjectArrayElement(env, arguments, index);
    if ((*env)->ExceptionCheck(env)) goto done;
    if (!argument) { failure(env, "reference child null argument"); goto done; }
    jsize bytes = (*env)->GetStringUTFLength(env, argument);
    if (bytes < 0 || bytes > 4096 || used + (size_t)bytes + 1 > 32768) {
      (*env)->DeleteLocalRef(env, argument); failure(env, "reference child argv byte bound"); goto done;
    }
    const char *text = (*env)->GetStringUTFChars(env, argument, NULL);
    if (!text) { (*env)->DeleteLocalRef(env, argument); goto done; }
    // Embedded Java NUL is modified-UTF8 C0 80; reject it rather than changing argv semantics.
    if (strstr(text, "\xc0\x80")) {
      (*env)->ReleaseStringUTFChars(env, argument, text);
      (*env)->DeleteLocalRef(env, argument); failure(env, "reference child embedded NUL"); goto done;
    }
    argv[index] = storage + used;
    memcpy(argv[index], text, (size_t)bytes + 1);
    used += (size_t)bytes + 1;
    (*env)->ReleaseStringUTFChars(env, argument, text);
    (*env)->DeleteLocalRef(env, argument);
  }
  if (strcmp(argv[0], "/system/bin/app_process64")) {
    failure(env, "reference collector only launches /system/bin/app_process64"); goto done;
  }
  if (shiraz_reference_collect(argv, 120000, result)) {
    failure(env, "reference child collection failed (overflow/I/O or wait ownership)"); goto done;
  }
  size_t capacity = 256 + 2 * result->output_size;
  json = malloc(capacity);
  if (!json) { failure(env, "reference collector JSON allocation failed"); goto done; }
  int prefix = snprintf(json, capacity,
      "{\"returncode\":%d,\"raw_wait_status\":%d,\"timed_out\":%s,\"output_hex\":\"",
      result->returncode, result->raw_wait_status, result->timed_out ? "true" : "false");
  if (prefix < 0 || (size_t)prefix + 3 + 2 * result->output_size >= capacity) {
    failure(env, "reference collector JSON bound"); goto done;
  }
  static const char digits[] = "0123456789abcdef";
  size_t position = (size_t)prefix;
  for (size_t index = 0; index < result->output_size; ++index) {
    json[position++] = digits[result->output[index] >> 4];
    json[position++] = digits[result->output[index] & 15];
  }
  json[position++] = '"'; json[position++] = '}'; json[position] = 0;
  answer = (*env)->NewStringUTF(env, json);
 done:
  free(json); free(result); free(storage);
  return answer;
}
#endif
