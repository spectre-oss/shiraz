// SPDX-License-Identifier: GPL-3.0-only
// Link-only declarations. Every executable body traps.
.text
.global fopen
.type fopen, %function
fopen:
 brk #0
.size fopen, .-fopen
.global fopen64
.type fopen64, %function
fopen64:
 brk #0
.size fopen64, .-fopen64
.global fclose
.type fclose, %function
fclose:
 brk #0
.size fclose, .-fclose
.global fflush
.type fflush, %function
fflush:
 brk #0
.size fflush, .-fflush
.global fread
.type fread, %function
fread:
 brk #0
.size fread, .-fread
.global fwrite
.type fwrite, %function
fwrite:
 brk #0
.size fwrite, .-fwrite
.global fgets
.type fgets, %function
fgets:
 brk #0
.size fgets, .-fgets
.global fputs
.type fputs, %function
fputs:
 brk #0
.size fputs, .-fputs
.global fgetc
.type fgetc, %function
fgetc:
 brk #0
.size fgetc, .-fgetc
.global getc
.type getc, %function
getc:
 brk #0
.size getc, .-getc
.global fputc
.type fputc, %function
fputc:
 brk #0
.size fputc, .-fputc
.global putc
.type putc, %function
putc:
 brk #0
.size putc, .-putc
.global ungetc
.type ungetc, %function
ungetc:
 brk #0
.size ungetc, .-ungetc
.global ferror
.type ferror, %function
ferror:
 brk #0
.size ferror, .-ferror
.global feof
.type feof, %function
feof:
 brk #0
.size feof, .-feof
.global fileno
.type fileno, %function
fileno:
 brk #0
.size fileno, .-fileno
.global fseek
.type fseek, %function
fseek:
 brk #0
.size fseek, .-fseek
.global fseeko
.type fseeko, %function
fseeko:
 brk #0
.size fseeko, .-fseeko
.global fseeko64
.type fseeko64, %function
fseeko64:
 brk #0
.size fseeko64, .-fseeko64
.global ftell
.type ftell, %function
ftell:
 brk #0
.size ftell, .-ftell
.global ftello
.type ftello, %function
ftello:
 brk #0
.size ftello, .-ftello
.global ftello64
.type ftello64, %function
ftello64:
 brk #0
.size ftello64, .-ftello64
.global fgetwc
.type fgetwc, %function
fgetwc:
 brk #0
.size fgetwc, .-fgetwc
.global getwc
.type getwc, %function
getwc:
 brk #0
.size getwc, .-getwc
.global fputwc
.type fputwc, %function
fputwc:
 brk #0
.size fputwc, .-fputwc
.global ungetwc
.type ungetwc, %function
ungetwc:
 brk #0
.size ungetwc, .-ungetwc
.data
.global __sF
.type __sF, %object
.balign 8
__sF:
 .zero 456
.size __sF, .-__sF
.global stdin
.type stdin, %object
.balign 8
stdin:
 .zero 8
.size stdin, .-stdin
.global stdout
.type stdout, %object
.balign 8
stdout:
 .zero 8
.size stdout, .-stdout
.global stderr
.type stderr, %object
.balign 8
stderr:
 .zero 8
.size stderr, .-stderr
.text
.macro format_declaration name
.global \name
.type \name, %function
\name:
 brk #0
.endm
format_declaration snprintf
format_declaration vsnprintf
format_declaration sprintf
format_declaration vsprintf
format_declaration asprintf
format_declaration vasprintf
format_declaration fprintf
format_declaration vfprintf
format_declaration printf
format_declaration vprintf
format_declaration __cxa_atexit
format_declaration __cxa_finalize
format_declaration atexit

.text
format_declaration __memcpy_chk
format_declaration __memmove_chk
format_declaration __memset_chk
format_declaration __strlen_chk
format_declaration __strcpy_chk
format_declaration __strncpy_chk
format_declaration __strncpy_chk2
format_declaration __strcat_chk
format_declaration __vsnprintf_chk
format_declaration __vsprintf_chk
format_declaration __snprintf_chk
format_declaration __sprintf_chk
