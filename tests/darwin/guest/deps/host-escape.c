// SPDX-License-Identifier: GPL-3.0-only
extern int getpid(void);
int host_escape(void) { return getpid(); }
