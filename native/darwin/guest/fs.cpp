// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/fs.hpp"
#include "guest_io.hpp"
#include <array>
#include <cstring>
#include <sys/mount.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <limits>

namespace shiraz::guest {
namespace {
namespace b = bionic;
template<class T, size_t Offset, size_t Width> void put(void *output, T value) {
  static_assert(sizeof(T) == Width);
  std::memcpy(static_cast<uint8_t *>(output) + Offset, &value, Width);
}
#define PUT(F,T,P,V) put<T,b::kStatfs_##F,b::kStatfs_##F##Width>(P,static_cast<T>(V))
int fail(int error) { errno = error; return -1; }
bool path_ok(const char *path) {
  if (!path) { errno = EFAULT; return false; }
  if (strnlen(path, 4096) == 4096) { errno = ENAMETOOLONG; return false; }
  for (const char *root : {"/proc", "/sys", "/system", "/data", "/dev/ashmem"}) {
    size_t size = std::strlen(root);
    if (!std::strncmp(path, root, size) && (!path[size] || path[size] == '/')) { errno = ENOSYS; return false; }
  }
  return true;
}
uint64_t magic(const char *name) {
  // Format identifiers from pinned android14 linux/magic.h. Darwin APFS, devfs,
  // smbfs and ambiguous hfs variants have no justified one-to-one Linux code.
  struct Type { const char *name; uint64_t magic; };
  static constexpr Type supported[] = {
    {"nfs", 0x6969}, {"msdos", 0x4d44}, {"exfat", 0x2011bab0},
    {"cd9660", 0x9660}, {"udf", 0x15013346}, {"ext2fs", 0xef53},
  };
  for (auto entry : supported) if (!std::strcmp(name, entry.name)) return entry.magic;
  return 0;
}
int serialize(const struct statfs &native, long maximum_name, void *output) {
  if (!output) return fail(EFAULT);
  if (strnlen(native.f_fstypename, sizeof(native.f_fstypename)) == sizeof(native.f_fstypename)) return fail(EIO);
  uint64_t type = magic(native.f_fstypename);
  if (!type) return fail(EOPNOTSUPP);
  if (maximum_name < 0 || !native.f_bsize) return fail(EOPNOTSUPP);
  static_assert(sizeof(native.f_blocks) == 8 && sizeof(native.f_files) == 8 && sizeof(native.f_fsid) == 8);
  std::array<uint8_t, b::kStatfsSize> result{};
  // Linux fs/statfs.c always marks valid f_flags with ST_VALID (0x20), defined
  // in include/linux/statfs.h. Other bits below describe actual mount flags.
  uint64_t flags = 0x20;
  struct Flag { uint64_t guest; uint32_t native; };
  for (auto entry : {Flag{uint64_t(b::kST_RDONLY), MNT_RDONLY}, Flag{uint64_t(b::kST_NOSUID), MNT_NOSUID},
        Flag{uint64_t(b::kST_NODEV), MNT_NODEV}, Flag{uint64_t(b::kST_NOEXEC), MNT_NOEXEC},
        Flag{uint64_t(b::kST_SYNCHRONOUS), MNT_SYNCHRONOUS}, Flag{uint64_t(b::kST_NOATIME), MNT_NOATIME}})
    if (native.f_flags & entry.native) flags |= entry.guest;
  uint64_t id = uint32_t(native.f_fsid.val[0]) | (uint64_t(uint32_t(native.f_fsid.val[1])) << 32);
  PUT(f_type, uint64_t, result.data(), type);
  PUT(f_bsize, uint64_t, result.data(), native.f_bsize);
  PUT(f_blocks, uint64_t, result.data(), native.f_blocks);
  PUT(f_bfree, uint64_t, result.data(), native.f_bfree);
  PUT(f_bavail, uint64_t, result.data(), native.f_bavail);
  PUT(f_files, uint64_t, result.data(), native.f_files);
  PUT(f_ffree, uint64_t, result.data(), native.f_ffree);
  PUT(f_fsid, uint64_t, result.data(), id);
  PUT(f_namelen, uint64_t, result.data(), maximum_name);
  // Counters are measured in native f_bsize allocation units; f_iosize is only
  // a transfer recommendation and must not silently rescale those counters.
  PUT(f_frsize, uint64_t, result.data(), native.f_bsize);
  PUT(f_flags, uint64_t, result.data(), flags);
  std::memcpy(output, result.data(), result.size()); return 0;
}
int path_stat(const char *path, void *output) {
  if (!output) return fail(EFAULT);
  if (!path_ok(path)) return -1;
  struct statfs native;
  if (::statfs(path, &native)) return -1;
  if (!magic(native.f_fstypename)) return fail(EOPNOTSUPP);
  errno = 0; long names = ::pathconf(path, _PC_NAME_MAX);
  if (names < 0) return errno ? -1 : fail(EOPNOTSUPP);
  return serialize(native, names, output);
}
int descriptor_stat(int fd, void *output) {
  if (!output) return fail(EFAULT);
  struct statfs native;
  if (::fstatfs(fd, &native)) return -1;
  if (!magic(native.f_fstypename)) return fail(EOPNOTSUPP);
  errno = 0; long names = ::fpathconf(fd, _PC_NAME_MAX);
  if (names < 0) return errno ? -1 : fail(EOPNOTSUPP);
  return serialize(native, names, output);
}
int directory(int fd){return fd==-100?AT_FDCWD:fd;}
ssize_t read_link_at(int fd,const char *path,char *output,size_t size){
  if(!path_ok(path))return -1;if(!output&&size)return fail(EFAULT);
  if(size>(64u<<20))return fail(EINVAL);return ::readlinkat(directory(fd),path,output,size);
}
int link_at(int from,const char *source,int to,const char *destination,int flags){
  if(!path_ok(source)||!path_ok(destination))return -1;
  if(flags & ~0x400)return fail(EINVAL);
  return ::linkat(directory(from),source,directory(to),destination,flags & 0x400?AT_SYMLINK_FOLLOW:0);
}
int symlink_at(const char *source,int fd,const char *destination){
  if(!path_ok(source)||!path_ok(destination))return -1;
  return ::symlinkat(source,directory(fd),destination);
}
int allocate_file(int fd,int64_t offset,int64_t length){
  if(offset<0||length<=0||offset>INT64_MAX-length)return errno_to_guest(EINVAL);
  struct stat info{};if(::fstat(fd,&info))return errno_to_guest(errno);
  if(!S_ISREG(info.st_mode))return errno_to_guest(ENODEV);
  int64_t end=offset+length;
  fstore_t store{};store.fst_flags=F_ALLOCATEALL;store.fst_posmode=F_PEOFPOSMODE;
  // Reserving the whole prefix is stronger than the requested interval and
  // preserves existing bytes. Only extend the logical file after reservation.
  store.fst_length=end;
  if(::fcntl(fd,F_PREALLOCATE,&store))return errno_to_guest(errno);
  if(end>info.st_size && ::ftruncate(fd,end))return errno_to_guest(errno);
  return 0;
}
int advise_file(int fd,int64_t offset,int64_t length,int advice){
  if(offset<0||length<0)return errno_to_guest(EINVAL);
  int result=-1;
  if(advice==0||advice==1||advice==2)result=::fcntl(fd,F_RDAHEAD,advice==1?0:1);
  else if(advice==3){if(length>INT_MAX)return errno_to_guest(EOVERFLOW);radvisory request{offset,int(length)};result=::fcntl(fd,F_RDADVISE,&request);}
  else if(advice==5)result=::fcntl(fd,F_NOCACHE,1);
  else return errno_to_guest(advice==4?ENOTSUP:EINVAL);
  return result?errno_to_guest(errno):0;
}
}
std::vector<ProviderExport> filesystem_exports() {
  // Android LP64 defines statfs/statfs64 from the same __STATFS64_BODY layout.
  return {fixed_export("statfs", "LIBC", &path_stat, ErrnoPolicy::MinusOne),
          fixed_export("statfs64", "LIBC", &path_stat, ErrnoPolicy::MinusOne),
          fixed_export("fstatfs", "LIBC", &descriptor_stat, ErrnoPolicy::MinusOne),
          fixed_export("fstatfs64", "LIBC", &descriptor_stat, ErrnoPolicy::MinusOne),
          fixed_export("readlinkat","LIBC",&read_link_at,ErrnoPolicy::MinusOne),
          fixed_export("linkat","LIBC",&link_at,ErrnoPolicy::MinusOne),
          fixed_export("symlinkat","LIBC",&symlink_at,ErrnoPolicy::MinusOne),
          fixed_export("posix_fallocate","LIBC",&allocate_file),
          fixed_export("posix_fadvise","LIBC",&advise_file)};
}
}
