// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <stdio.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>
int main(int argc, char** argv) {
    if (argc != 4)
        return 1;
    int fd = open(argv[1], O_RDONLY);
    if (fd >= 0) {
        close(fd);
        fputs("sentinel read allowed\n", stderr);
        return 2;
    }
    if (errno != EPERM && errno != EACCES)
        return 3;
    fd = open(argv[2], O_RDONLY);
    char header[4];
    if (fd < 0 || read(fd, header, sizeof(header)) != 4 || header[0] != 'P' || header[1] != 'K')
        return 4;
    close(fd);
    fd = open("/dev/null", O_WRONLY);
    if (fd >= 0) {
        close(fd);
        fputs("outside write allowed\n", stderr);
        return 5;
    }
    if (errno != EPERM && errno != EACCES)
        return 6;
    fd = open(argv[3], O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fd < 0 || write(fd, "ok", 2) != 2)
        return 7;
    close(fd);
    fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0)
        return 8;
    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(9);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    int result = connect(fd, (struct sockaddr*)&addr, sizeof(addr));
    int failure = errno;
    close(fd);
    if (!result || (failure != EPERM && failure != EACCES))
        return 9;
    pid_t child = fork();
    if (!child)
        _exit(99);
    if (child > 0) {
        (void)waitpid(child, 0, 0);
        return 10;
    }
    if (errno != EPERM && errno != EACCES)
        return 11;
    puts("M5_OUTER_SANDBOX_PASS home=denied app_inputs=allowed outside_write=denied "
         "temp_write=allowed network=denied fork=denied");
    return 0;
}
