// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/native_image_catalog.hpp"
#include "shiraz/darwin/guest/internal_map_resolver.hpp"
#include <cassert>
#include <cstdio>
using namespace shiraz::guest;
int main(int argc, char** argv) {
    assert(argc == 2 || (argc == 3 && !std::strcmp(argv[2], "--expect-library-rejection")));
    using Range = const void* (*)(size_t*);
    auto get_range = reinterpret_cast<Range>(dlsym(RTLD_DEFAULT, "_dyld_get_shared_cache_range"));
    assert(get_range);
    size_t size = 0;
    auto start = reinterpret_cast<uintptr_t>(get_range(&size));
    assert(start && size);
    assert(snapshot_native_images(0, size).status != NativeImageCatalogStatus::Complete);
    char bad[16];
    assert(!native_image_detail::read(0, bad, sizeof(bad)));
    std::string bad_path;
    assert(!native_image_detail::path(nullptr, bad_path));
    assert(!native_image_detail::read(UINTPTR_MAX - 1, bad, sizeof(bad)));
    void* library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (argc == 3) {
        assert(!library);
        auto current = snapshot_native_images(start, size);
        assert(current.status == NativeImageCatalogStatus::Complete && !current.images.empty());
        std::puts(
            "NATIVE_IMAGE_CATALOG_HARDENED_PASS library_validation_rejection=1 current_images=1");
        return 0;
    }
    assert(library);
    auto mutate = reinterpret_cast<int* (*)()>(dlsym(library, "shiraz_native_catalog_mutate"));
    assert(mutate);
    int* value = mutate();
    assert(*value == 8);
    Dl_info info{};
    assert(dladdr(reinterpret_cast<void*>(mutate), &info) && info.dli_fbase);
    auto library_header = reinterpret_cast<uintptr_t>(info.dli_fbase);
    auto catalog = snapshot_native_images(start, size);
    assert(catalog.status == NativeImageCatalogStatus::Complete && catalog.images.size() >= 2);
    assert(!dlclose(library)); // Registration now owns the remaining native reference.
    assert(*mutate() == 9);
    auto host = snapshot_process_regions(CurrentProcessRegionQuery{});
    assert(host.status == RegionSnapshotStatus::Complete);
    unsigned code = 0, data = 0;
    for (const auto& r : host.regions) {
        if ((r.start <= library_header && library_header < r.end) ||
            (r.start <= reinterpret_cast<uintptr_t>(value) &&
             reinterpret_cast<uintptr_t>(value) < r.end)) {
            ProcMapRecord record;
            if (r.external_pager) {
                assert(catalog.resolve(r, record) && record.name_kind == ProcMapName::File &&
                       record.inode);
            } else {
                // dyld can replace data with internal memory. Loaded-image provenance
                // must not turn that current anonymous backing into a file mapping.
                assert(!catalog.resolve(r, record));
                assert(resolve_internal_mapping(r, record) &&
                       record.name_kind == ProcMapName::Anonymous);
            }
            code += r.start <= library_header && library_header < r.end;
            data += r.start <= reinterpret_cast<uintptr_t>(value) &&
                    reinterpret_cast<uintptr_t>(value) < r.end;
        }
    }
    assert(code && data);
    auto count = catalog.images.size();
    catalog = {}; // Balanced reference release must permit unloading this fixture.
    void* remaining = dlopen(argv[1], RTLD_NOLOAD | RTLD_LAZY);
    assert(!remaining);
    for (int i = 0; i < 5; ++i) {
        auto fresh = snapshot_native_images(start, size);
        assert(fresh.status == NativeImageCatalogStatus::Complete &&
               fresh.images.size() == count - 1);
    }
    std::printf("NATIVE_IMAGE_CATALOG_PASS images=%zu pinned_after_close=1 dirty_data=1 "
                "unloaded_after_release=1\n",
                count);
}
