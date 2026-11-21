// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/admission_return.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <set>
#include <utility>
namespace shiraz::guest {
thread_local AdmissionDomain::Invocation* AdmissionDomain::Invocation::current_ = nullptr;
thread_local AdmissionDomain::StagedExtension* AdmissionDomain::StagedExtension::current_ = nullptr;
AdmissionDomain::Invocation::Invocation(std::shared_ptr<AdmissionDomain> domain)
    : domain_(std::move(domain)) {
    if (!domain_)
        throw ElfError("missing guest admission domain");
    {
        std::lock_guard lock(domain_->mutex_);
        if (!domain_->sealed_ || domain_->closing_ || domain_->closed_ ||
            domain_->active_ >= (1u << 20))
            throw ElfError("guest admission invocation unavailable");
        ++domain_->active_;
    }
    previous_ = current_;
    current_ = this;
}
AdmissionDomain::Reservation::~Reservation() {
    if (!counted_)
        return;
    std::lock_guard lock(domain_->mutex_);
    if (!domain_->active_)
        std::abort();
    --domain_->active_;
    if (!domain_->active_)
        domain_->drained_.notify_all();
}
std::unique_ptr<AdmissionDomain::Reservation> AdmissionDomain::reserve_current() {
    auto domain = current_invocation();
    if (!domain || (StagedExtension::current_ && StagedExtension::current_->domain_ == domain))
        return {};
    auto reservation = std::unique_ptr<Reservation>(new Reservation(domain));
    std::lock_guard lock(domain->mutex_);
    if (!domain->sealed_ || domain->closing_ || domain->closed_ || domain->active_ >= (1u << 20))
        return {};
    ++domain->active_;
    reservation->counted_ = true;
    return reservation;
}
AdmissionDomain::Invocation::Invocation(std::unique_ptr<Reservation> reservation) {
    if (!reservation || !reservation->counted_ || !reservation->domain_)
        throw ElfError("invalid guest admission reservation");
    domain_ = std::move(reservation->domain_);
    reservation->counted_ = false;
    previous_ = current_;
    current_ = this;
}
AdmissionDomain::Invocation::~Invocation() {
    if (current_ != this)
        std::abort();
    current_ = previous_;
    std::lock_guard lock(domain_->mutex_);
    if (!domain_->active_)
        std::abort();
    --domain_->active_;
    if (!domain_->active_)
        domain_->drained_.notify_all();
}
void AdmissionDomain::add_image(std::shared_ptr<AdmissionSession> session,
                                std::shared_ptr<void> lease) {
    std::lock_guard lock(mutex_);
    if (sealed_ || closing_ || closed_ || !session || !lease ||
        lease.get() != reinterpret_cast<void*>(session->image_base()) || !session->ready() ||
        images_.size() >= 128)
        throw ElfError("invalid guest admission image");
    uintptr_t begin = session->image_base(), size = session->image_span();
    if (!size || begin > UINTPTR_MAX - size)
        throw ElfError("invalid guest admission image range");
    for (const auto& image : images_) {
        uintptr_t other = image.session->image_base(), length = image.session->image_span();
        if (begin < other + length && other < begin + size)
            throw ElfError("overlapping guest admission images");
    }
    images_.push_back({std::move(lease), std::move(session)});
}
void AdmissionDomain::allow_closure(void* entry) {
    std::lock_guard lock(mutex_);
    if (sealed_ || closing_ || closed_ || !known_guest_closure(entry) || closures_.size() >= 65536)
        throw ElfError("invalid guest admission host closure");
    auto value = reinterpret_cast<uintptr_t>(entry);
    if (std::find(closures_.begin(), closures_.end(), value) != closures_.end())
        throw ElfError("duplicate guest admission host closure");
    closures_.push_back(value);
}
void AdmissionDomain::allow_provider_entry(void* entry) {
    std::lock_guard lock(mutex_);
    if (sealed_ || closing_ || closed_ || !known_guest_provider_entry(entry) ||
        closures_.size() >= 65536)
        throw ElfError("invalid guest admission provider entry");
    auto value = reinterpret_cast<uintptr_t>(entry);
    if (std::find(closures_.begin(), closures_.end(), value) != closures_.end())
        throw ElfError("duplicate guest admission provider entry");
    closures_.push_back(value);
}
bool AdmissionDomain::register_closure(void* entry) {
    require_invocation();
    if (!known_guest_closure(entry))
        throw ElfError("invalid guest admission host closure");
    auto value = reinterpret_cast<uintptr_t>(entry);
    if (auto* stage = StagedExtension::current_; stage && stage->domain_.get() == this) {
        auto current = stage->private_policy_;
        if (std::find(current->closures.begin(), current->closures.end(), value) !=
            current->closures.end())
            return false;
        auto published = std::atomic_load_explicit(&policy_, std::memory_order_acquire);
        if (published && std::find(published->closures.begin(), published->closures.end(), value) !=
                             published->closures.end())
            return false;
        if (current->closures.size() >= 65536)
            throw ElfError("guest admission host closure limit");
        auto next = std::make_shared<Policy>(*current);
        next->closures.push_back(value);
        stage->entries_.push_back(value);
        stage->private_policy_ = std::move(next);
        return true;
    }
    std::lock_guard lock(mutex_);
    if (!sealed_ || closed_)
        throw ElfError("guest admission domain is unavailable");
    auto current = std::atomic_load_explicit(&policy_, std::memory_order_acquire);
    if (!current)
        throw ElfError("guest admission policy is unavailable");
    if (std::find(current->closures.begin(), current->closures.end(), value) !=
        current->closures.end())
        return false;
    if (current->closures.size() >= 65536)
        throw ElfError("guest admission host closure limit");
    auto next = std::make_shared<Policy>(*current);
    next->closures.push_back(value);
    std::atomic_store_explicit(&policy_, std::shared_ptr<const Policy>(std::move(next)),
                               std::memory_order_release);
    return true;
}
void AdmissionDomain::seal() {
    std::lock_guard lock(mutex_);
    if (sealed_ || closing_ || closed_ || images_.empty())
        throw ElfError("invalid guest admission domain seal");
    auto initial = std::make_shared<Policy>();
    initial->images = images_;
    initial->closures = closures_;
    std::atomic_store_explicit(&policy_, std::shared_ptr<const Policy>(std::move(initial)),
                               std::memory_order_release);
    sealed_ = true;
}
std::vector<uintptr_t>
AdmissionDomain::validate_extension(const std::vector<ImageRegistration>& images,
                                    const std::vector<void*>& closures,
                                    const std::vector<void*>& provider_entries) {
    if (images.empty() || images.size() > 128 || closures.size() > 65536 ||
        provider_entries.size() > 65536)
        throw ElfError("invalid guest admission extension size");
    for (const auto& image : images) {
        if (!image.session || !image.mapping_lease ||
            image.mapping_lease.get() != reinterpret_cast<void*>(image.session->image_base()) ||
            !image.session->ready())
            throw ElfError("invalid guest admission extension image");
        uintptr_t base = image.session->image_base();
        uint64_t span = image.session->image_span();
        if (!span || base > UINTPTR_MAX - span)
            throw ElfError("invalid guest admission extension range");
    }
    std::set<uintptr_t> entries;
    for (void* entry : closures) {
        if (!known_guest_closure(entry) ||
            !entries.insert(reinterpret_cast<uintptr_t>(entry)).second)
            throw ElfError("invalid guest admission extension closure");
    }
    for (void* entry : provider_entries) {
        if (!known_guest_provider_entry(entry) ||
            !entries.insert(reinterpret_cast<uintptr_t>(entry)).second)
            throw ElfError("invalid guest admission extension provider");
    }
    return {entries.begin(), entries.end()};
}
std::shared_ptr<const AdmissionDomain::Policy>
AdmissionDomain::merge_extension(const std::shared_ptr<const Policy>& current,
                                 const std::vector<ImageRegistration>& images,
                                 const std::vector<uintptr_t>& entries) {
    if (!current || current->images.size() + images.size() > 128)
        throw ElfError("guest admission extension image limit");
    auto next = std::make_shared<Policy>(*current);
    next->images.reserve(current->images.size() + images.size());
    for (const auto& image : images) {
        uintptr_t begin = image.session->image_base(), length = image.session->image_span();
        for (const auto& existing : next->images) {
            uintptr_t other = existing.session->image_base(), size = existing.session->image_span();
            if (begin < other + size && other < begin + length)
                throw ElfError("overlapping guest admission extension images");
        }
        next->images.push_back({image.mapping_lease, image.session});
    }
    for (uintptr_t entry : entries) {
        if (std::find(next->closures.begin(), next->closures.end(), entry) != next->closures.end())
            continue;
        if (next->closures.size() >= 65536)
            throw ElfError("guest admission host closure limit");
        next->closures.push_back(entry);
    }
    return next;
}
void AdmissionDomain::extend(const std::vector<ImageRegistration>& images,
                             const std::vector<void*>& closures,
                             const std::vector<void*>& provider_entries) {
    auto entries = validate_extension(images, closures, provider_entries);
    std::lock_guard lock(mutex_);
    if (!sealed_ || closing_ || closed_ || staging_)
        throw ElfError("guest admission extension unavailable");
    auto current = std::atomic_load_explicit(&policy_, std::memory_order_acquire);
    auto next = merge_extension(current, images, entries);
    std::atomic_store_explicit(&policy_, std::move(next), std::memory_order_release);
}
AdmissionDomain::StagedExtension::StagedExtension(std::shared_ptr<AdmissionDomain> domain,
                                                  const std::vector<ImageRegistration>& images,
                                                  const std::vector<void*>& closures,
                                                  const std::vector<void*>& provider_entries)
    : domain_(std::move(domain)), images_(images),
      entries_(AdmissionDomain::validate_extension(images, closures, provider_entries)) {
    if (!domain_ || current_)
        throw ElfError("invalid nested guest admission staging");
    std::lock_guard lock(domain_->mutex_);
    if (!domain_->sealed_ || domain_->closing_ || domain_->closed_ || domain_->staging_ ||
        domain_->active_ >= (1u << 20))
        throw ElfError("guest admission staging unavailable");
    auto published = std::atomic_load_explicit(&domain_->policy_, std::memory_order_acquire);
    private_policy_ = AdmissionDomain::merge_extension(published, images_, entries_);
    domain_->staging_ = true;
    ++domain_->active_;
    current_ = this;
}
void AdmissionDomain::StagedExtension::commit() {
    if (finished_ || current_ != this)
        throw ElfError("guest admission staging is not current");
    std::lock_guard lock(domain_->mutex_);
    if (domain_->closing_ || domain_->closed_ || !domain_->staging_)
        throw ElfError("guest admission staging cannot commit");
    auto published = std::atomic_load_explicit(&domain_->policy_, std::memory_order_acquire);
    auto next = AdmissionDomain::merge_extension(published, images_, entries_);
    std::atomic_store_explicit(&domain_->policy_, std::move(next), std::memory_order_release);
    domain_->staging_ = false;
    if (!domain_->active_)
        std::abort();
    --domain_->active_;
    if (!domain_->active_)
        domain_->drained_.notify_all();
    current_ = nullptr;
    finished_ = true;
}
AdmissionDomain::StagedExtension::~StagedExtension() {
    if (finished_)
        return;
    if (current_ != this)
        std::abort();
    std::lock_guard lock(domain_->mutex_);
    if (!domain_->staging_ || !domain_->active_)
        std::abort();
    domain_->staging_ = false;
    --domain_->active_;
    if (!domain_->active_)
        domain_->drained_.notify_all();
    current_ = nullptr;
}
std::shared_ptr<const AdmissionDomain::Policy> AdmissionDomain::selected_policy() const noexcept {
    auto* stage = StagedExtension::current_;
    if (stage && stage->domain_.get() == this)
        return stage->private_policy_;
    return std::atomic_load_explicit(&policy_, std::memory_order_acquire);
}
std::shared_ptr<AdmissionDomain> AdmissionDomain::current_invocation() noexcept {
    return Invocation::current_ ? Invocation::current_->domain_
                                : std::shared_ptr<AdmissionDomain>{};
}
void AdmissionDomain::require_invocation() const {
    if (!Invocation::current_ || Invocation::current_->domain_.get() != this)
        throw ElfError("guest admission requires current invocation");
}
AdmissionSession* AdmissionDomain::image_for(const Policy& policy, uintptr_t address) noexcept {
    for (const auto& image : policy.images) {
        uintptr_t begin = image.session->image_base();
        if (address >= begin && address - begin < image.session->image_span())
            return image.session.get();
    }
    return nullptr;
}
void AdmissionDomain::admit_entry(uintptr_t target) {
    require_invocation();
    auto policy = selected_policy();
    auto* image = policy ? image_for(*policy, target) : nullptr;
    if (!image)
        throw ElfError("guest entry outside admission domain");
    (void)image->admit(target);
}
bool AdmissionDomain::owns_session(const AdmissionSession* session) {
    std::lock_guard lock(mutex_);
    if (!session || !sealed_ || closing_ || closed_)
        return false;
    auto policy = selected_policy();
    return policy && std::any_of(policy->images.begin(), policy->images.end(),
                                 [&](const auto& image) { return image.session.get() == session; });
}
AdmissionDomain::Decision AdmissionDomain::route(uintptr_t site, uintptr_t target, uintptr_t sp) {
    require_invocation();
    if ((sp & 15) || !prepared_guest_stack_contains(sp))
        throw ElfError("guest transfer stack is not prepared");
    auto policy = selected_policy();
    auto* source = policy ? image_for(*policy, site) : nullptr;
    if (!source)
        throw ElfError("guest transfer source outside admission domain");
    auto transfer = source->transfer_site(site);
    if (auto* destination = image_for(*policy, target)) {
        bool newly = destination->admit(target);
        return {target, newly};
    }
    if (AdmissionReturnScope::allows(this, transfer, target, sp))
        return {target, false};
    bool eligible = transfer.kind == IndirectTransferKind::Branch ||
                    transfer.kind == IndirectTransferKind::Call;
    bool allowed = eligible && std::find(policy->closures.begin(), policy->closures.end(),
                                         target) != policy->closures.end();
    if (eligible && !allowed && StagedExtension::current_ &&
        StagedExtension::current_->domain_.get() == this) {
        auto published = std::atomic_load_explicit(&policy_, std::memory_order_acquire);
        allowed = published && std::find(published->closures.begin(), published->closures.end(),
                                         target) != published->closures.end();
    }
    if (allowed && (known_guest_closure(reinterpret_cast<const void*>(target)) ||
                    known_guest_provider_entry(reinterpret_cast<const void*>(target))))
        return {target, false};
    throw ElfError("guest transfer target outside admission domain");
}
void AdmissionDomain::close() {
    if (StagedExtension::current_ && StagedExtension::current_->domain_.get() == this)
        throw ElfError("cannot close guest domain during staging");
    for (auto* frame = Invocation::current_; frame; frame = frame->previous_)
        if (frame->domain_.get() == this)
            throw ElfError("cannot close guest domain during its invocation");
    std::unique_lock lock(mutex_);
    if (closed_)
        return;
    if (closing_) {
        drained_.wait(lock, [&] { return closed_; });
        return;
    }
    closing_ = true;
    drained_.wait(lock, [&] { return active_ == 0; });
    closed_ = true;
    drained_.notify_all();
}
} // namespace shiraz::guest
