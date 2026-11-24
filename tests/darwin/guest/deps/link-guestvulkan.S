// SPDX-License-Identifier: GPL-3.0-only
// Link-only declaration; execution traps.
.text
.global vkGetInstanceProcAddr
.type vkGetInstanceProcAddr, %function
vkGetInstanceProcAddr:
 brk #0
.global vkGetDeviceProcAddr
.type vkGetDeviceProcAddr, %function
vkGetDeviceProcAddr:
 brk #0
