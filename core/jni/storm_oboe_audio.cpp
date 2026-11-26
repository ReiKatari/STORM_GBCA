#ifdef ANDROID

#include <oboe/Oboe.h>
#include "storm_gbca_core.h"
#include <algorithm>
#include <atomic>
#include <mutex>
#include <vector>

namespace storm {

class OboeAudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    bool start(StormGbcaContext* ctx, int sample_rate = 48000) {
        std::lock_guard<std::mutex> lock(mu_);
        stop_locked();
        ctx_.store(ctx, std::memory_order_release);
        sample_rate_ = sample_rate;
        return open_and_start_locked();
    }

    void stop() {
        std::lock_guard<std::mutex> lock(mu_);
        stop_locked();
    }

    void set_paused(bool paused) {
        paused_.store(paused, std::memory_order_release);
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override {
        (void)stream;
        auto* dst = static_cast<int16_t*>(audioData);
        StormGbcaContext* ctx = ctx_.load(std::memory_order_acquire);
        if (!running_.load(std::memory_order_acquire) || paused_.load(std::memory_order_acquire) || !ctx) {
            std::fill_n(dst, numFrames * 2, int16_t(0));
            return oboe::DataCallbackResult::Continue;
        }
        int got = storm_gbca_read_audio(ctx, dst, numFrames);
        if (got < numFrames) {
            std::fill(dst + got * 2, dst + numFrames * 2, int16_t(0));
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override {
        (void)stream;
        (void)error;
        std::lock_guard<std::mutex> lock(mu_);
        if (!running_.load(std::memory_order_acquire) || !ctx_.load(std::memory_order_acquire)) {
            return;
        }
        stream_.reset();
        open_and_start_locked();
    }

private:
    bool open_and_start_locked() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Shared)
               ->setFormat(oboe::AudioFormat::I16)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setSampleRate(sample_rate_)
               ->setDataCallback(this)
               ->setErrorCallback(this);

        oboe::Result res = builder.openStream(stream_);
        if (res != oboe::Result::OK || !stream_) {
            builder.setPerformanceMode(oboe::PerformanceMode::None);
            res = builder.openStream(stream_);
            if (res != oboe::Result::OK || !stream_) return false;
        }
        res = stream_->requestStart();
        if (res != oboe::Result::OK) {
            stream_->close();
            stream_.reset();
            return false;
        }
        paused_.store(false, std::memory_order_release);
        running_.store(true, std::memory_order_release);
        return true;
    }

    void stop_locked() {
        running_.store(false, std::memory_order_release);
        ctx_.store(nullptr, std::memory_order_release);
        if (stream_) {
            stream_->stop();
            stream_->close();
            stream_.reset();
        }
    }

    std::mutex mu_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<StormGbcaContext*> ctx_{nullptr};
    int sample_rate_ = 48000;
    std::atomic<bool> running_{false};
    std::atomic<bool> paused_{false};
};

static OboeAudioEngine g_oboe;

bool android_audio_start(StormGbcaContext* ctx) { return g_oboe.start(ctx, 48000); }
void android_audio_stop() { g_oboe.stop(); }
void android_audio_pause(bool paused) { g_oboe.set_paused(paused); }

} // namespace storm

#endif // ANDROID
