// SPDX-License-Identifier: GPL-3.0-only
// Independent LP64 ABI fixture for Android 14 zlib. The runtime provider must
// marshal callbacks; these guest function pointers cannot be called as Apple C.
#include <stddef.h>
#include <stdint.h>
typedef struct {
  unsigned char *next_in;
  unsigned avail_in;
  unsigned long total_in;
  unsigned char *next_out;
  unsigned avail_out;
  unsigned long total_out;
  const char *msg;
  void *state;
  void *(*zalloc)(void *, unsigned, unsigned);
  void (*zfree)(void *, void *);
  void *opaque;
  int data_type;
  unsigned long adler, reserved;
} Stream;
_Static_assert(sizeof(Stream) == 112 && offsetof(Stream, state) == 56 &&
               offsetof(Stream, zalloc) == 64 && offsetof(Stream, adler) == 96,
               "Android LP64 z_stream ABI");
#if defined(ZLIB_LINK_ONLY)
// Link-only export declarations; no body may execute in the runtime test.
#define TRAP __builtin_trap()
int deflateInit2_(Stream *s, int a, int b, int c, int d, int e, const char *v, int n) {
  (void)s; (void)a; (void)b; (void)c; (void)d; (void)e; (void)v; (void)n; TRAP;
}
int inflateInit2_(Stream *s, int w, const char *v, int n) { (void)s; (void)w; (void)v; (void)n; TRAP; }
#define TWO(N) int N(Stream *s, int n) { (void)s; (void)n; TRAP; }
#define ONE(N) int N(Stream *s) { (void)s; TRAP; }
TWO(deflate) TWO(inflate) TWO(inflateReset2)
ONE(deflateEnd) ONE(inflateEnd) ONE(deflateReset) ONE(inflateReset)
unsigned long adler32(unsigned long v, const unsigned char *p, unsigned n) { (void)v; (void)p; (void)n; TRAP; }
unsigned long crc32(unsigned long v, const unsigned char *p, unsigned n) { (void)v; (void)p; (void)n; TRAP; }
#else
extern int deflateInit2_(Stream *, int, int, int, int, int, const char *, int);
extern int inflateInit2_(Stream *, int, const char *, int);
extern int deflate(Stream *, int), inflate(Stream *, int), inflateReset2(Stream *, int);
extern int deflateEnd(Stream *), inflateEnd(Stream *), deflateReset(Stream *), inflateReset(Stream *);
extern unsigned long adler32(unsigned long, const unsigned char *, unsigned);
extern unsigned long crc32(unsigned long, const unsigned char *, unsigned);
extern void *calloc(size_t, size_t);
extern void free(void *);
extern int memcmp(const void *, const void *, size_t);
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
struct Counters { unsigned allocations, frees; int invalid; Stream *stream; };
static void *allocate(void *opaque, unsigned count, unsigned size) {
  struct Counters *c = opaque;
  ++c->allocations;
  if (!count || !size) c->invalid = 1;
  if (deflateReset(c->stream) != -2) c->invalid = 1;
  return calloc(count, size);
}
static void release(void *opaque, void *address) {
  struct Counters *c = opaque;
  ++c->frees;
  free(address);
}
int run_zlib(void) {
  const unsigned char input[] = "123456789";
  unsigned char packed[256], output[64];
  CHECK(adler32(1, input, 9) == 0x091e01deul);
  CHECK(crc32(0, input, 9) == 0xcbf43926ul);
  struct Counters counters = {0};
  Stream encoder = {0};
  counters.stream = &encoder;
  encoder.zalloc = allocate; encoder.zfree = release; encoder.opaque = &counters;
  CHECK(deflateInit2_(&encoder, 6, 8, 15, 8, 0, "1.2.13", sizeof(Stream)) == 0);
  CHECK(encoder.state && encoder.zalloc == allocate && encoder.zfree == release && encoder.opaque == &counters);
  encoder.next_in = (unsigned char *)input; encoder.avail_in = 9;
  encoder.next_out = packed; encoder.avail_out = sizeof(packed);
  CHECK(deflate(&encoder, 4) == 1 && encoder.total_in == 9 && encoder.total_out > 9);
  unsigned packed_size = encoder.total_out;
  CHECK(deflateReset(&encoder) == 0 && encoder.total_in == 0 && encoder.total_out == 0);
  CHECK(deflateEnd(&encoder) == 0 && !encoder.state);
  CHECK(counters.allocations > 0 && counters.allocations == counters.frees && !counters.invalid);
  Stream decoder = {0};
  CHECK(inflateInit2_(&decoder, 15, "1.3.1", sizeof(Stream)) == 0);
  decoder.next_in = packed; decoder.avail_in = packed_size;
  decoder.next_out = output; decoder.avail_out = sizeof(output);
  CHECK(inflate(&decoder, 4) == 1 && decoder.total_out == 9 && !memcmp(output, input, 9));
  CHECK(inflateReset(&decoder) == 0 && decoder.total_out == 0);
  CHECK(inflateReset2(&decoder, 15) == 0);
  CHECK(inflateEnd(&decoder) == 0 && !decoder.state);
  CHECK(inflateEnd(&decoder) == -2);
  Stream bad = {0};
  CHECK(inflateInit2_(&bad, 15, "2.0", sizeof(Stream)) == -6);
  CHECK(inflateInit2_(&bad, 15, "1.2.13", 108) == -6);
  bad.zalloc = allocate;
  CHECK(inflateInit2_(&bad, 15, "1.2.13", sizeof(Stream)) == -2);
  bad.zfree = release; bad.zalloc = (void *(*)(void *, unsigned, unsigned))(uintptr_t)0x12345;
  CHECK(inflateInit2_(&bad, 15, "1.2.13", sizeof(Stream)) == -2);
  return 0;
}
#endif
