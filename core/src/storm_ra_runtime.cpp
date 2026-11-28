#include "storm_gbca_core.h"
#include "storm_internal.hpp"

extern "C" {
#include "rc_runtime.h"
#include "rc_api_runtime.h"
#include "rc_api_user.h"
#include "rc_consoles.h"
#include "../third_party/rcheevos/src/runtime/rc_format.h"
#include "../third_party/rcheevos/src/runtime/rc_trigger.h"
}

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <deque>
#include <mutex>
#include <unordered_map>

namespace storm {

struct StormRaState {
    rc_runtime_t runtime{};
    std::deque<StormGbcaRaEvent> event_queue;
    std::unordered_map<uint32_t, int> lboard_formats;
    std::mutex mutex;
};

static constexpr size_t kMaxRaEventQueue = 256;
static thread_local StormRaState* tls_active_ra_state = nullptr;

struct TlsRaStateGuard {
    StormRaState* prev;
    explicit TlsRaStateGuard(StormRaState* next) : prev(tls_active_ra_state) {
        tls_active_ra_state = next;
    }
    ~TlsRaStateGuard() {
        tls_active_ra_state = prev;
    }
};

static uint32_t RC_CCONV storm_ra_peek(uint32_t address, uint8_t* buffer, uint32_t num_bytes, void* ud) {
    const auto* ctx = static_cast<const StormGbcaContext*>(ud);
    if (!ctx || !buffer || num_bytes == 0) return 0;
    return ra_peek_core_memory(ctx, address, buffer, num_bytes);
}

static void push_ra_event(StormRaState* st, const StormGbcaRaEvent& ev) {
    if (!st) return;
    if (st->event_queue.size() >= kMaxRaEventQueue) {
        st->event_queue.pop_front();
    }
    st->event_queue.push_back(ev);
}

static void RC_CCONV storm_ra_event_handler(const rc_runtime_event_t* ev) {
    StormRaState* st = tls_active_ra_state;
    if (!st || !ev) return;

    StormGbcaRaEvent out{};
    out.id = ev->id;
    out.value = ev->value;

    switch (ev->type) {
        case RC_RUNTIME_EVENT_ACHIEVEMENT_TRIGGERED:
            out.type = STORM_RA_EVENT_ACH_TRIGGERED;
            break;
        case RC_RUNTIME_EVENT_ACHIEVEMENT_PRIMED:
            out.type = STORM_RA_EVENT_ACH_PRIMED;
            break;
        case RC_RUNTIME_EVENT_ACHIEVEMENT_UNPRIMED:
            out.type = STORM_RA_EVENT_ACH_UNPRIMED;
            break;
        case RC_RUNTIME_EVENT_ACHIEVEMENT_PROGRESS_UPDATED: {
            out.type = STORM_RA_EVENT_ACH_MEASURED;
            unsigned val = 0, target = 0;
            rc_runtime_get_achievement_measured(&st->runtime, ev->id, &val, &target);
            out.value = static_cast<int32_t>(val);
            out.target = static_cast<int32_t>(target);
            rc_runtime_format_achievement_measured(&st->runtime, ev->id, out.formatted, sizeof(out.formatted));
            break;
        }
        case RC_RUNTIME_EVENT_ACHIEVEMENT_RESET:
            out.type = STORM_RA_EVENT_ACH_RESET;
            break;
        case RC_RUNTIME_EVENT_LBOARD_STARTED:
        case RC_RUNTIME_EVENT_LBOARD_UPDATED:
        case RC_RUNTIME_EVENT_LBOARD_CANCELED:
        case RC_RUNTIME_EVENT_LBOARD_TRIGGERED: {
            if (ev->type == RC_RUNTIME_EVENT_LBOARD_STARTED) out.type = STORM_RA_EVENT_LBOARD_STARTED;
            else if (ev->type == RC_RUNTIME_EVENT_LBOARD_UPDATED) out.type = STORM_RA_EVENT_LBOARD_UPDATED;
            else if (ev->type == RC_RUNTIME_EVENT_LBOARD_CANCELED) out.type = STORM_RA_EVENT_LBOARD_CANCELED;
            else out.type = STORM_RA_EVENT_LBOARD_TRIGGERED;

            int fmt = RC_FORMAT_VALUE;
            auto it = st->lboard_formats.find(ev->id);
            if (it != st->lboard_formats.end()) {
                fmt = it->second;
            }
            rc_runtime_format_lboard_value(out.formatted, static_cast<int>(sizeof(out.formatted)), ev->value, fmt);
            break;
        }
        default:
            return;
    }

    push_ra_event(st, out);
}

StormRaState* ra_create() {
    auto* st = new StormRaState();
    rc_runtime_init(&st->runtime);
    return st;
}

void ra_destroy(StormRaState* st) {
    if (!st) return;
    {
        std::lock_guard<std::mutex> lock(st->mutex);
        rc_runtime_destroy(&st->runtime);
        st->event_queue.clear();
        st->lboard_formats.clear();
    }
    delete st;
}

void ra_do_frame(StormRaState* st, StormGbcaContext* ctx) {
    if (!st || !ctx) return;
    std::lock_guard<std::mutex> lock(st->mutex);
    TlsRaStateGuard guard(st);
    rc_runtime_do_frame(&st->runtime, storm_ra_event_handler, storm_ra_peek, ctx, nullptr);
}

static void copy_cstr_trunc(char* dst, int cap, const char* src) {
    if (!dst || cap <= 0) return;
    if (!src) {
        dst[0] = '\0';
        return;
    }
    std::snprintf(dst, static_cast<size_t>(cap), "%s", src);
}

static int finalize_api_request(rc_api_request_t* req, int rc,
                                char* out_url, int url_cap,
                                char* out_post, int post_cap) {
    if (rc == RC_OK) {
        copy_cstr_trunc(out_url, url_cap, req->url);
        copy_cstr_trunc(out_post, post_cap, req->post_data ? req->post_data : "");
    } else {
        copy_cstr_trunc(out_url, url_cap, "");
        copy_cstr_trunc(out_post, post_cap, "");
    }
    rc_api_destroy_request(req);
    return rc;
}

} // namespace storm

extern "C" {

STORM_GBCA_API void storm_gbca_ra_reset(StormGbcaContext* ctx) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return;
    std::lock_guard<std::mutex> lock(st->mutex);
    rc_runtime_reset(&st->runtime);
    st->event_queue.clear();
}

STORM_GBCA_API void storm_gbca_ra_clear_all(StormGbcaContext* ctx) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return;
    std::lock_guard<std::mutex> lock(st->mutex);
    rc_runtime_destroy(&st->runtime);
    rc_runtime_init(&st->runtime);
    st->event_queue.clear();
    st->lboard_formats.clear();
}

STORM_GBCA_API int storm_gbca_ra_activate_achievement(StormGbcaContext* ctx, uint32_t id, const char* memaddr) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st || !memaddr) return -1;
    std::lock_guard<std::mutex> lock(st->mutex);
    return rc_runtime_activate_achievement(&st->runtime, id, memaddr, nullptr, 0);
}

STORM_GBCA_API void storm_gbca_ra_deactivate_achievement(StormGbcaContext* ctx, uint32_t id) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return;
    std::lock_guard<std::mutex> lock(st->mutex);
    rc_runtime_deactivate_achievement(&st->runtime, id);
}

STORM_GBCA_API int storm_gbca_ra_activate_leaderboard(StormGbcaContext* ctx, uint32_t id, const char* memaddr, const char* format) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st || !memaddr) return -1;
    int fmt = (format && format[0]) ? rc_parse_format(format) : RC_FORMAT_VALUE;
    std::lock_guard<std::mutex> lock(st->mutex);
    int rc = rc_runtime_activate_lboard(&st->runtime, id, memaddr, nullptr, 0);
    if (rc == RC_OK) {
        st->lboard_formats[id] = fmt;
    }
    return rc;
}

STORM_GBCA_API void storm_gbca_ra_deactivate_leaderboard(StormGbcaContext* ctx, uint32_t id) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return;
    std::lock_guard<std::mutex> lock(st->mutex);
    rc_runtime_deactivate_lboard(&st->runtime, id);
    st->lboard_formats.erase(id);
}

STORM_GBCA_API int storm_gbca_ra_activate_rich_presence(StormGbcaContext* ctx, const char* script) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return -1;
    std::lock_guard<std::mutex> lock(st->mutex);
    return rc_runtime_activate_richpresence(&st->runtime, script ? script : "", nullptr, 0);
}

STORM_GBCA_API int storm_gbca_ra_poll_event(StormGbcaContext* ctx, StormGbcaRaEvent* out_event) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st || !out_event) return 0;
    std::lock_guard<std::mutex> lock(st->mutex);
    if (st->event_queue.empty()) return 0;
    *out_event = st->event_queue.front();
    st->event_queue.pop_front();
    return 1;
}

STORM_GBCA_API int storm_gbca_ra_get_rich_presence(StormGbcaContext* ctx, char* out_buf, int buf_size) {
    if (!ctx || !out_buf || buf_size <= 0) return -1;
    std::lock_guard<std::recursive_mutex> core_lock(storm::core_mutex_from_ctx(ctx));
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) {
        out_buf[0] = '\0';
        return -1;
    }
    std::lock_guard<std::mutex> lock(st->mutex);
    int len = rc_runtime_get_richpresence(&st->runtime, out_buf, static_cast<size_t>(buf_size),
                                          storm::storm_ra_peek, ctx, nullptr);
    if (len < 0) {
        out_buf[0] = '\0';
        return len;
    }
    return len;
}

STORM_GBCA_API int storm_gbca_ra_get_achievement_measured(const StormGbcaContext* ctx, uint32_t id, uint32_t* out_val, uint32_t* out_target) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return -1;
    std::lock_guard<std::mutex> lock(st->mutex);
    unsigned val = 0, target = 0;
    int rc = rc_runtime_get_achievement_measured(&st->runtime, id, &val, &target);
    if (out_val) *out_val = static_cast<uint32_t>(val);
    if (out_target) *out_target = static_cast<uint32_t>(target);
    return rc;
}

STORM_GBCA_API int storm_gbca_ra_get_achievement_state(const StormGbcaContext* ctx, uint32_t id) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return RC_TRIGGER_STATE_INACTIVE;
    std::lock_guard<std::mutex> lock(st->mutex);
    const rc_trigger_t* trg = rc_runtime_get_achievement(&st->runtime, id);
    return trg ? static_cast<int>(trg->state) : RC_TRIGGER_STATE_INACTIVE;
}

STORM_GBCA_API int storm_gbca_ra_serialize_progress(const StormGbcaContext* ctx, uint8_t* dst, int capacity) {
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return -1;
    std::lock_guard<std::mutex> lock(st->mutex);
    uint32_t needed = rc_runtime_progress_size(&st->runtime, nullptr);
    if (needed == 0) return -1;
    if (!dst) return static_cast<int>(needed);
    if (capacity < static_cast<int>(needed)) return -1;
    int rc = rc_runtime_serialize_progress_sized(dst, static_cast<uint32_t>(capacity), &st->runtime, nullptr);
    return (rc == RC_OK) ? static_cast<int>(needed) : rc;
}

STORM_GBCA_API int storm_gbca_ra_deserialize_progress(StormGbcaContext* ctx, const uint8_t* src, int size) {
    if (!ctx || !src || size <= 0) return -1;
    std::lock_guard<std::recursive_mutex> core_lock(storm::core_mutex_from_ctx(ctx));
    if (storm::is_hardcore_from_ctx(ctx)) return STORM_ERR_HARDCORE_BLOCKED;
    storm::StormRaState* st = storm::ra_state_from_ctx(ctx);
    if (!st) return -1;
    std::lock_guard<std::mutex> lock(st->mutex);
    st->event_queue.clear();
    return rc_runtime_deserialize_progress_sized(&st->runtime, src, static_cast<uint32_t>(size), nullptr);
}

STORM_GBCA_API int storm_gbca_ra_build_login_request(const char* username, const char* password, const char* api_token,
                                                      char* out_url, int url_cap, char* out_post, int post_cap) {
    rc_api_login_request_t params{};
    params.username = username ? username : "";
    params.password = (password && password[0]) ? password : nullptr;
    params.api_token = (api_token && api_token[0]) ? api_token : nullptr;
    rc_api_request_t req{};
    int rc = rc_api_init_login_request(&req, &params);
    return storm::finalize_api_request(&req, rc, out_url, url_cap, out_post, post_cap);
}

STORM_GBCA_API int storm_gbca_ra_build_start_session_request(const char* username, const char* api_token, uint32_t game_id,
                                                              const char* game_hash, int hardcore,
                                                              char* out_url, int url_cap, char* out_post, int post_cap) {
    rc_api_start_session_request_t params{};
    params.username = username ? username : "";
    params.api_token = api_token ? api_token : "";
    params.game_id = game_id;
    params.game_hash = (game_hash && game_hash[0]) ? game_hash : nullptr;
    params.hardcore = hardcore ? 1u : 0u;
    rc_api_request_t req{};
    int rc = rc_api_init_start_session_request(&req, &params);
    return storm::finalize_api_request(&req, rc, out_url, url_cap, out_post, post_cap);
}

STORM_GBCA_API int storm_gbca_ra_build_award_request(const char* username, const char* api_token, uint32_t achievement_id,
                                                      int hardcore, const char* game_hash,
                                                      char* out_url, int url_cap, char* out_post, int post_cap) {
    rc_api_award_achievement_request_t params{};
    params.username = username ? username : "";
    params.api_token = api_token ? api_token : "";
    params.achievement_id = achievement_id;
    params.hardcore = hardcore ? 1u : 0u;
    params.game_hash = (game_hash && game_hash[0]) ? game_hash : nullptr;
    params.seconds_since_unlock = 0;
    rc_api_request_t req{};
    int rc = rc_api_init_award_achievement_request(&req, &params);
    return storm::finalize_api_request(&req, rc, out_url, url_cap, out_post, post_cap);
}

STORM_GBCA_API int storm_gbca_ra_build_lboard_request(const char* username, const char* api_token, uint32_t lboard_id,
                                                       int32_t score, const char* game_hash,
                                                       char* out_url, int url_cap, char* out_post, int post_cap) {
    rc_api_submit_lboard_entry_request_t params{};
    params.username = username ? username : "";
    params.api_token = api_token ? api_token : "";
    params.leaderboard_id = lboard_id;
    params.score = score;
    params.game_hash = (game_hash && game_hash[0]) ? game_hash : nullptr;
    params.seconds_since_completion = 0;
    rc_api_request_t req{};
    int rc = rc_api_init_submit_lboard_entry_request(&req, &params);
    return storm::finalize_api_request(&req, rc, out_url, url_cap, out_post, post_cap);
}

STORM_GBCA_API int storm_gbca_ra_build_ping_request(const char* username, const char* api_token, uint32_t game_id,
                                                     const char* rich_presence, const char* game_hash, int hardcore,
                                                     char* out_url, int url_cap, char* out_post, int post_cap) {
    rc_api_ping_request_t params{};
    params.username = username ? username : "";
    params.api_token = api_token ? api_token : "";
    params.game_id = game_id;
    params.rich_presence = (rich_presence && rich_presence[0]) ? rich_presence : nullptr;
    params.game_hash = (game_hash && game_hash[0]) ? game_hash : nullptr;
    params.hardcore = hardcore ? 1u : 0u;
    rc_api_request_t req{};
    int rc = rc_api_init_ping_request(&req, &params);
    return storm::finalize_api_request(&req, rc, out_url, url_cap, out_post, post_cap);
}

} // extern "C"
