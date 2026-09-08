/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>

#include <rtmp.h>
#include <android/log.h>

#define LOG_TAG "rtmp-native"
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)

class RtmpContext {
private:
    std::mutex mtx;
    std::condition_variable cv;
    std::queue<RTMPPacket> packet_queue;
    std::thread writer_thread;
    bool running = false;

public:
    RTMP *rtmp;
    std::vector<uint8_t> sps_video;
    std::vector<uint8_t> pps_video;
    std::vector<uint8_t> asc_audio;
    uint8_t format_audio = 0;

    RtmpContext(RTMP *rtmp_init) {
        rtmp = rtmp_init;
    }

    void startWriter() {
        if (writer_thread.joinable()) return;
        running = true;
        writer_thread = std::thread(&RtmpContext::writeLoop, this);
        LOGE("Writer started");
    }

    void stopWriter() {
        {
            std::lock_guard<std::mutex> lock(mtx);
            running = false;
            while (!packet_queue.empty()) {
                RTMPPacket_Free(&packet_queue.front());
                packet_queue.pop();
            }
        }
        cv.notify_one();
        if (writer_thread.joinable()) writer_thread.join();
        LOGE("Writer stopped");
    }

    bool enqueuePacket(RTMPPacket pkt) {
        std::lock_guard<std::mutex> lock(mtx);
        if (packet_queue.size() > 100) {
            LOGE("RTMP queue full (%zu), dropping packet", packet_queue.size());
            RTMPPacket_Free(&pkt);
            return false;
        }

        packet_queue.push(pkt);
        cv.notify_one();
        return true;
    }

private:
    void writeLoop() {
        while (running) {
            RTMPPacket p;
            {
                std::unique_lock<std::mutex> lock(mtx);
                cv.wait(lock, [&]{ return !packet_queue.empty() || !running; });
                if (!running && packet_queue.empty()) break;
                if (!packet_queue.empty()) {
                    p = packet_queue.front();
                    packet_queue.pop();
                } else {
                    continue;
                }
            }

            int sent = RTMP_SendPacket(rtmp, &p, FALSE);
            if (sent <= 0) {
                LOGE("RTMP_SendPacket failed: %d", sent);
                break;
            }
            RTMPPacket_Free(&p);
        }
    }
};

std::vector<uint8_t> buildAVCDecoderRecord(const std::vector<uint8_t>& sps, const std::vector<uint8_t>& pps) {
    std::vector<uint8_t> config;

    config.push_back(1);

    config.push_back(sps[1]);
    config.push_back(sps[2]);
    config.push_back(sps[3]);

    config.push_back(0xFF);

    config.push_back(0xE0 | 1);

    config.push_back((uint8_t)(sps.size() >> 8));
    config.push_back((uint8_t)(sps.size() & 0xFF));

    config.insert(config.end(), sps.begin(), sps.end());

    config.push_back((uint8_t)1);

    config.push_back((uint8_t)(pps.size() >> 8));
    config.push_back((uint8_t)(pps.size() & 0xFF));

    config.insert(config.end(), pps.begin(), pps.end());

    return config;
}

void sendAVCConfig(RtmpContext *ctx, const std::vector<uint8_t>& config) {
    RTMPPacket packet;
    RTMPPacket_Reset(&packet);
    RTMPPacket_Alloc(&packet, 5 + config.size());

    uint8_t* body = (uint8_t*)packet.m_body;
    body[0] = 0x17;
    body[1] = 0x00;
    body[2] = 0;
    body[3] = 0;
    body[4] = 0;

    memcpy(body + 5, config.data(), config.size());

    packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet.m_nBodySize = 5 + config.size();
    packet.m_nChannel = 0x04;
    packet.m_nTimeStamp = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;

    ctx->enqueuePacket(packet);
}

void sendHEVCConfig(RtmpContext *ctx, const std::vector<uint8_t>& config) {
    RTMPPacket packet;
    RTMPPacket_Reset(&packet);
    RTMPPacket_Alloc(&packet, 5 + config.size());

    uint8_t* body = (uint8_t*)packet.m_body;

    body[0] = 0x1C;
    body[1] = 0x00;
    body[2] = 0;
    body[3] = 0;
    body[4] = 0;
    memcpy(body + 5, config.data(), config.size());

    packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet.m_nBodySize = 5 + config.size();
    packet.m_nChannel = 0x04;
    packet.m_nTimeStamp = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;

    ctx->enqueuePacket(packet);
}

void sendAacConfig(RtmpContext *ctx, const std::vector<uint8_t>& config, uint8_t audioFormat) {
    if (config.size() < 2) return;

    std::vector<uint8_t> audioData;
    audioData.push_back(0x00);
    audioData.insert(audioData.end(), config.begin(), config.end());

    int bodyLen = 2 + config.size();

    RTMPPacket packet;
    RTMPPacket_Alloc(&packet, bodyLen);
    RTMPPacket_Reset(&packet);
    packet.m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet.m_nChannel = 0x05;
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_nInfoField2 = ctx->rtmp->m_stream_id;

    packet.m_nBodySize = bodyLen;
    char* body = packet.m_body;
    body[0] = audioFormat;
    memcpy(body + 1, audioData.data(), audioData.size());

    ctx->enqueuePacket(packet);
}

bool annexbToAvcc(const std::vector<uint8_t>& annexb, std::vector<uint8_t>& avcc_out) {
    avcc_out.clear();

    if (annexb.empty()) return true;

    std::vector<std::pair<size_t, size_t>> nalu_ranges;

    size_t i = 0;
    const size_t len = annexb.size();

    while (i < len) {
        while (i < len && annexb[i] == 0) {
            ++i;
        }

        if (i >= len) break;

        size_t sc_pos = i;
        size_t sc_len = 0;

        bool found = false;
        if (i >= 2 && annexb[i-2] == 0 && annexb[i-1] == 0 && annexb[i] == 1) {
            sc_pos = i - 2;
            sc_len = 3;
            found = true;
        } else if (i >= 3 && annexb[i-3] == 0 && annexb[i-2] == 0 && annexb[i-1] == 0 && annexb[i] == 1) {
            sc_pos = i - 3;
            sc_len = 4;
            found = true;
        }

        if (!found) {
            ++i;
            continue;
        }

        size_t nalu_start = sc_pos + sc_len;
        if (nalu_start >= len) break;

        size_t j = nalu_start;
        while (j < len) {
            if (j + 2 < len && annexb[j] == 0 && annexb[j+1] == 0 && annexb[j+2] == 1) {
                break;
            }
            if (j + 3 < len && annexb[j] == 0 && annexb[j+1] == 0 && annexb[j+2] == 0 && annexb[j+3] == 1) {
                break;
            }
            ++j;
        }

        size_t nalu_end = j;
        size_t nalu_size = nalu_end - nalu_start;

        if (nalu_size > 0) {
            nalu_ranges.emplace_back(nalu_start, nalu_end);
        }

        i = j;
    }

    avcc_out.reserve(4 * nalu_ranges.size() + len);

    for (const auto& [start, end] : nalu_ranges) {
        size_t nalu_len = end - start;
        if (nalu_len > UINT32_MAX) {
            continue;
        }

        uint32_t len32 = static_cast<uint32_t>(nalu_len);
        avcc_out.push_back((len32 >> 24) & 0xFF);
        avcc_out.push_back((len32 >> 16) & 0xFF);
        avcc_out.push_back((len32 >> 8) & 0xFF);
        avcc_out.push_back(len32 & 0xFF);

        avcc_out.insert(avcc_out.end(), annexb.begin() + start, annexb.begin() + end);
    }

    return !avcc_out.empty();
}

std::vector<std::pair<uint8_t, std::vector<uint8_t>>> parseHevcNalus(const std::vector<uint8_t>& csd) {
    std::vector<std::pair<uint8_t, std::vector<uint8_t>>> nalus;

    if (csd.empty()) {
        LOGE("CSD is empty!");
        return nalus;
    }

    size_t i = 0;
    const size_t len = csd.size();

    while (i < len) {
        if (i + 3 > len) break;

        bool is4Byte = false;
        size_t scLen = 0;

        if (csd[i] == 0 && csd[i+1] == 0 && csd[i+2] == 0x01) {
            scLen = 3;
        } else if (i + 4 <= len && csd[i] == 0 && csd[i+1] == 0 && csd[i+2] == 0 && csd[i+3] == 0x01) {
            scLen = 4;
            is4Byte = true;
        } else {
            ++i;
            continue;
        }

        i += scLen;

        if (i >= len) break;

        size_t j = i;
        while (j + 3 <= len) {
            size_t zeros = 0;
            while (j + zeros < len && csd[j + zeros] == 0) ++zeros;

            if (zeros >= 2 && j + zeros < len) {
                if ((zeros == 2 || zeros == 3) && csd[j + zeros] == 0x01) {
                    break;
                }
            }

            ++j;
        }

        if (j + 3 > len) {
            j = len;
        }

        size_t naluLen = j - i;

        if (naluLen > 0 && i + naluLen <= len) {
            uint8_t nalType = (csd[i] >> 1) & 0x3F;
            std::vector<uint8_t> nalu(csd.begin() + i, csd.begin() + i + naluLen);

            nalus.emplace_back(nalType, std::move(nalu));
        }

        i = j;
    }

    return nalus;
}

static inline void writeBE16(std::vector<uint8_t>& dst, uint16_t val) {
    dst.push_back((val >> 8) & 0xFF);
    dst.push_back(val & 0xFF);
}

std::vector<uint8_t> buildHevcConfigRecord(const std::vector<std::pair<uint8_t, std::vector<uint8_t>>>& nalus, int videoProfile, int videoTier, int videoLevel) {
    if (nalus.empty()) {
        LOGE("NALUs are empty!");
    }

    std::vector<uint8_t> vps, sps, pps;
    for (auto& [type, nalu] : nalus) {
        if (type == 32) vps = nalu;
        else if (type == 33) sps = nalu;
        else if (type == 34) pps = nalu;
    }

    LOGE("HEVC profile %d, tier %d and level %d", videoProfile, videoTier, videoLevel);

    std::vector<uint8_t> config;

    config.push_back(1);

    config.push_back((0 << 6) | (videoTier << 5) | videoProfile);
    config.insert(config.end(), 4, 0);

    config.insert(config.end(), 6, 0);

    config.push_back((uint8_t)videoLevel);

    config.push_back(0xF0);
    config.push_back(0xFF);

    config.push_back(0x00);

    config.push_back((1 << 6));

    config.push_back(0x00);

    config.push_back(0x00);

    writeBE16(config, 0);

    config.push_back(0x00);

    uint8_t array_count = 0;

    if (!vps.empty() && vps.size() > 0) array_count++;
    if (!sps.empty() && sps.size() > 0) array_count++;
    if (!pps.empty() && pps.size() > 0) array_count++;

    config.push_back(array_count);

    auto writeNALArray = [&](const uint8_t* nal, size_t len, uint8_t nal_type) {
        if (!nal || len == 0) return;

        config.push_back(0x80 | (nal_type & 0x3F));

        LOGE("Nal type: %d, size: %d", (nal_type & 0x3F), len);

        writeBE16(config, 1);

        writeBE16(config, (uint16_t)len);

        config.insert(config.end(), nal, nal + len);
    };

    if (!vps.empty() && vps.size() > 0) {
        writeNALArray(vps.data(), vps.size(), 32);
    } else {
        LOGE("VPS is empty");
    }
    if (!sps.empty() && sps.size() > 0) {
        writeNALArray(sps.data(), sps.size(), 33);
    } else {
        LOGE("SPS is empty");
    }
    if (!pps.empty() && pps.size() > 0) {
        writeNALArray(pps.data(), pps.size(), 34);
    } else {
        LOGE("PPS is empty");
    }

    return config;
}

bool annexbToRtmpHevcNalus(std::vector<uint8_t>& annexb, std::vector<uint8_t>& hevc_out) {
    hevc_out.clear();

    if (annexb.empty()) return true;

    uint8_t* data = annexb.data();
    size_t len = annexb.size();

    if (len < 3) return true;

    size_t i = 0;
    while (i < len) {
        if (i >= len - 2) break;

        bool is4ByteStart = false;
        size_t scOffset = 3;
        if (data[i] == 0x00 && data[i+1] == 0x00) {
            if (data[i+2] == 0x01) {
                scOffset = 3;
            } else if (i + 3 < len && data[i+2] == 0x00 && data[i+3] == 0x01) {
                scOffset = 4;
                is4ByteStart = true;
            } else {
                ++i;
                continue;
            }
        } else {
            ++i;
            continue;
        }

        size_t naluStart = i + scOffset;
        if (naluStart >= len) break;

        size_t j = naluStart;
        while (j < len - 2) {
            if (data[j] == 0x00 && data[j+1] == 0x00) {
                if (data[j+2] == 0x01) {
                    break;
                } else if (j + 3 < len && data[j+2] == 0x00 && data[j+3] == 0x01) {
                    break;
                }
            }
            ++j;
        }

        if (j >= len - 2) {
            j = len;
        }

        size_t naluLen = j - naluStart;

        uint32_t be_len = (uint32_t)naluLen;
        hevc_out.push_back((be_len >> 24) & 0xFF);
        hevc_out.push_back((be_len >> 16) & 0xFF);
        hevc_out.push_back((be_len >> 8) & 0xFF);
        hevc_out.push_back(be_len & 0xFF);

        if (naluLen > 0 && naluStart + naluLen <= len) {
            hevc_out.insert(hevc_out.end(), data + naluStart, data + naluStart + naluLen);
        }

        i = j;
    }

    return !hevc_out.empty();
}

extern "C" {

JNIEXPORT void JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeSetAACConfig(JNIEnv *env, jclass clazz, jlong jContext, jbyteArray asc, jint sampleRate, jint channelsCount) {
    RtmpContext* ctx = (RtmpContext*)jContext;
    if (!ctx || !ctx->rtmp) return;

    static const uint8_t SOUND_FORMAT_AAC = 10;

    int bitDepth = 16;

    int soundRate;
    if (sampleRate <= 5500) soundRate = 0;
    else if (sampleRate <= 11000) soundRate = 1;
    else if (sampleRate <= 22000) soundRate = 2;
    else soundRate = 3;

    int soundSize = (bitDepth == 16) ? 1 : 0;
    int soundType = (channelsCount == 2) ? 1 : 0;

    ctx->format_audio = (SOUND_FORMAT_AAC << 4) | (soundRate << 2) | (soundSize << 1) | soundType;

    jsize ascLen = env->GetArrayLength(asc);
    jbyte* ascData = env->GetByteArrayElements(asc, nullptr);
    if (!ascData) return;

    if (ascLen < 2) {
        env->ReleaseByteArrayElements(asc, ascData, JNI_ABORT);
        LOGE("ASC is too short");
        return;
    }

    ctx->asc_audio.assign(ascData, ascData + ascLen);

    sendAacConfig(ctx, ctx->asc_audio, ctx->format_audio);

    env->ReleaseByteArrayElements(asc, ascData, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeSetAVCConfig(JNIEnv *env, jclass clazz, jlong jContext, jbyteArray jSps, jbyteArray jPps) {
    RtmpContext* ctx = (RtmpContext*)jContext;
    if (!ctx || !ctx->rtmp) return;

    jsize spsLen = env->GetArrayLength(jSps);
    jbyte* spsData = env->GetByteArrayElements(jSps, nullptr);
    if (!spsData) return;

    if (spsLen < 5) {
        LOGE("SPS is too short");
        env->ReleaseByteArrayElements(jSps, spsData, JNI_ABORT);
        return;
    }

    ctx->sps_video.assign(spsData + 4, spsData + spsLen);

    jsize ppsLen = env->GetArrayLength(jPps);
    jbyte* ppsData = env->GetByteArrayElements(jPps, nullptr);
    if (!ppsData) return;

    ctx->pps_video.assign(ppsData + 4, ppsData + ppsLen);

    std::vector<uint8_t> config = buildAVCDecoderRecord(ctx->sps_video, ctx->pps_video);

    sendAVCConfig(ctx, config);

    env->ReleaseByteArrayElements(jSps, spsData, JNI_ABORT);
    env->ReleaseByteArrayElements(jPps, ppsData, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeSetHEVCConfig(JNIEnv* env, jclass clazz, jlong jContext, jbyteArray jCsd, int videoProfile, int videoTier, int videoLevel) {
    RtmpContext* ctx = (RtmpContext*)jContext;
    if (!ctx || !ctx->rtmp) return;

    jsize csdLen = env->GetArrayLength(jCsd);
    jbyte* csdData = env->GetByteArrayElements(jCsd, nullptr);
    if (!csdData) return;

    std::vector<uint8_t> nalusRaw(csdData, csdData + csdLen);

    auto nalus = parseHevcNalus(nalusRaw);

    std::vector<uint8_t> config = buildHevcConfigRecord(nalus, videoProfile, videoTier, videoLevel);

    sendHEVCConfig(ctx, config);

    env->ReleaseByteArrayElements(jCsd, csdData, JNI_ABORT);
}

JNIEXPORT jlong JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeCreate(JNIEnv *env, jclass clazz) {
    RTMP *rtmp = RTMP_Alloc();
    if (!rtmp) return 0;

    RTMP_Init(rtmp);
    RTMP_SetBufferMS(rtmp, 5 * 1000);

    RtmpContext *ctx = new RtmpContext(rtmp);
    ctx->startWriter();

    return (jlong)ctx;
}

JNIEXPORT jboolean JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeConnect(JNIEnv *env, jclass clazz, jlong jContext, jstring jUrl) {
    RtmpContext *ctx = (RtmpContext *)jContext;
    if (!ctx || !ctx->rtmp) return JNI_FALSE;

    const char *url = env->GetStringUTFChars(jUrl, nullptr);
    if (!url) return JNI_FALSE;

    int ret = RTMP_SetupURL(ctx->rtmp, (char*)url);
    if (!ret) {
        LOGE("Failed setting up URL");
        env->ReleaseStringUTFChars(jUrl, url);
        return JNI_FALSE;
    }

    RTMP_EnableWrite(ctx->rtmp);

    ret = RTMP_Connect(ctx->rtmp, nullptr);
    if (!ret) {
        env->ReleaseStringUTFChars(jUrl, url);
        return JNI_FALSE;
    }

    ret = RTMP_ConnectStream(ctx->rtmp, 0);
    if (!ret) {
        LOGE("Could not connectStream");
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(jUrl, url);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeIsTimedOut(JNIEnv *env, jclass clazz, jlong jContext) {
    RtmpContext *ctx = (RtmpContext *)jContext;
    if (!ctx || !ctx->rtmp) return JNI_FALSE;

    return (!RTMP_IsConnected(ctx->rtmp) || RTMP_IsTimedout(ctx->rtmp));
}

JNIEXPORT jboolean JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeWriteVideo(JNIEnv *env, jclass clazz, jlong jContext, jbyteArray jData, jlong jTimestampMs, jboolean isKeyFrame) {
    RtmpContext *ctx = (RtmpContext *)jContext;
    if (!ctx || !ctx->rtmp) return JNI_FALSE;

    if (!RTMP_IsConnected(ctx->rtmp) || RTMP_IsTimedout(ctx->rtmp)) {
        LOGE("RTMP not connected yet, skipping packet");
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(jData);
    jbyte *bytes = env->GetByteArrayElements(jData, nullptr);

    if (!bytes || len <= 0) {
        env->ReleaseByteArrayElements(jData, bytes, JNI_ABORT);
        return JNI_FALSE;
    }

    std::vector<uint8_t> annexb(len);
    std::memcpy(annexb.data(), bytes, len);

    std::vector<uint8_t> avcc;

    if (!annexbToAvcc(annexb, avcc)) {
        LOGE("Failed to convert Annex-B to AVCC");
        env->ReleaseByteArrayElements(jData, bytes, JNI_ABORT);
        return JNI_FALSE;
    }

    RTMPPacket packet = {};
    RTMPPacket_Alloc(&packet, 5 + avcc.size());
    RTMPPacket_Reset(&packet);

    uint8_t frameType = isKeyFrame ? 1 : 2;

    packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet.m_nChannel = 0x04;
    packet.m_nTimeStamp = (uint32_t)jTimestampMs;
    packet.m_hasAbsTimestamp = 1;
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;

    char *body = packet.m_body;

    body[0] = (frameType << 4) | 0x07;
    body[1] = 1;
    body[2] = 0;
    body[3] = 0;
    body[4] = 0;
    memcpy(body + 5, avcc.data(), avcc.size());

    packet.m_nBodySize = 5 + avcc.size();

    bool result = ctx->enqueuePacket(packet);

    env->ReleaseByteArrayElements(jData, bytes, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeWriteHEVCVideo(JNIEnv *env, jclass clazz, jlong jContext, jbyteArray jData, jlong jTimestampMs, jboolean isKeyFrame) {
    RtmpContext *ctx = (RtmpContext *)jContext;
    if (!ctx || !ctx->rtmp) return JNI_FALSE;

    if (!RTMP_IsConnected(ctx->rtmp) || RTMP_IsTimedout(ctx->rtmp)) {
        LOGE("RTMP not connected yet, skipping packet");
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(jData);
    jbyte *bytes = env->GetByteArrayElements(jData, nullptr);

    if (!bytes || len <= 0) {
        env->ReleaseByteArrayElements(jData, bytes, JNI_ABORT);
        return JNI_FALSE;
    }

    std::vector<uint8_t> annexb(len);
    std::memcpy(annexb.data(), bytes, len);

    std::vector<uint8_t> hevc;

    if (!annexbToRtmpHevcNalus(annexb, hevc)) {
        LOGE("Failed to convert Annex-B to HEVC");
        env->ReleaseByteArrayElements(jData, bytes, JNI_ABORT);
        return JNI_FALSE;
    }

    RTMPPacket packet = {};
    RTMPPacket_Alloc(&packet, 5 + hevc.size());
    RTMPPacket_Reset(&packet);

    uint8_t frameType = isKeyFrame ? 1 : 2;

    packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet.m_nChannel = 0x04;
    packet.m_nTimeStamp = (uint32_t)jTimestampMs;
    packet.m_hasAbsTimestamp = 1;
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;

    char *body = packet.m_body;

    body[0] = (frameType << 4) | 0x0C;
    body[1] = 1;
    body[2] = 0;
    body[3] = 0;
    body[4] = 0;

    memcpy(body + 5, hevc.data(), hevc.size());

    packet.m_nBodySize = 5 + hevc.size();

    bool result = ctx->enqueuePacket(packet);

    env->ReleaseByteArrayElements(jData, bytes, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeWriteAudio(JNIEnv *env, jclass clazz, jlong jContext, jbyteArray jData, jlong jTimestampMs) {
    RtmpContext* ctx = (RtmpContext*)jContext;
    if (!ctx || !ctx->rtmp) return JNI_FALSE;

    jsize len = env->GetArrayLength(jData);
    jbyte *data = env->GetByteArrayElements(jData, nullptr);
    if (!data) return JNI_FALSE;

    std::vector<uint8_t> frameData;
    frameData.push_back(0x01);
    frameData.insert(frameData.end(), data, data + len);

    int bodyLen = 2 + frameData.size();

    RTMPPacket packet = {};
    RTMPPacket_Alloc(&packet, bodyLen);
    RTMPPacket_Reset(&packet);

    packet.m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet.m_nChannel = 0x05;
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;

    packet.m_nTimeStamp = (uint32_t)jTimestampMs;
    packet.m_hasAbsTimestamp = 1;
    packet.m_nBodySize = bodyLen;

    char* body = packet.m_body;

    body[0] = ctx->format_audio;
    memcpy(body + 1, frameData.data(), frameData.size());

    bool result = ctx->enqueuePacket(packet);

    env->ReleaseByteArrayElements(jData, data, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeDisconnect(JNIEnv *env, jclass clazz, jlong jContext) {
    RtmpContext *ctx = (RtmpContext *)jContext;
    if (ctx && ctx->rtmp) {
        RTMP_Close(ctx->rtmp);
    }
}

JNIEXPORT void JNICALL Java_com_yepgoryo_CaptureCap_RtmpMuxer_nativeDestroy(JNIEnv *env, jclass clazz, jlong jContext) {
    RtmpContext *ctx = (RtmpContext *)jContext;
    if (ctx) {
        if (ctx->rtmp) {
            RTMP_Free(ctx->rtmp);
        }
        ctx->stopWriter();
        delete ctx;
    }
}

}