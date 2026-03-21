//
// Created by fuqiuluo on 2024/10/15.
//
#include <dobby.h>
#include <unistd.h>
#include "sensor_hook.h"
#include "logging.h"
#include "elf_util.h"
#include "dobby_hook.h"

#define LIBSF_PATH_64 "/system/lib64/libsensorservice.so"
#define LIBSF_PATH_32 "/system/lib/libsensorservice.so"

extern bool enableSensorHook;

// _ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventm
OriginalSensorEventQueueWriteType OriginalSensorEventQueueWrite = nullptr;

OriginalConvertToSensorEventType OriginalConvertToSensorEvent = nullptr;

int64_t SensorEventQueueWrite(void *tube, void *events, int64_t numEvents) {
    if (enableSensorHook) {
        LOGD("SensorEventQueueWrite called");
    }
    return OriginalSensorEventQueueWrite(tube, events, numEvents);
}

void ConvertToSensorEvent(void *src, void *dst) {
    if (enableSensorHook) {
        auto a = *(int32_t *)((char*)src + 4);
        auto b = *(int32_t *)((char*)src + 8);
        auto c = *(int64_t *)((char*)src + 16);

        LOGD("ConvertToSensorEvent: handle=%d, type=%d, timestamp=%lld", a, b, (long long)c);

        // 打印原始数据 (src 内存中的前 64 字节)
        char raw_hex[256] = {0};
        for (int i = 0; i < 64; i++) {
            sprintf(raw_hex + i * 3, "%02X ", *((unsigned char*)src + i));
        }
        LOGD("Raw SRC Data (HEX): %s", raw_hex);

        // 尝试以 float 解释常见的数据段 (通常数据在 24 字节之后)
        float v0 = *(float *)((char*)src + 24);
        float v1 = *(float *)((char*)src + 28);
        float v2 = *(float *)((char*)src + 32);
        LOGD("Raw SRC Data (Float Interpretation): v[0]=%.4f, v[1]=%.4f, v[2]=%.4f", v0, v1, v2);

        *(int64_t *)((char*)dst + 16) = 0LL;
        *(int32_t *)((char*)dst + 24) = 0;
        *(int64_t *)((char*)dst) = c;
        *(int32_t *)((char*)dst + 8) = a;
        *(int32_t *)((char*)dst + 12) = b;
        *(int8_t *)((char*)dst + 28) = b;

        if (b == 18) {
            *(float *)((char*)dst + 16) = -1.0;
            LOGD("Sensor Data (Type 18): step_count=-1.0");
        } else if (b == 19) {
            *(int64_t *)((char*)dst + 16) = -1;
            LOGD("Sensor Data (Type 19): step_detector=-1");
        } else {
            *(float *)((char*)dst + 16) = -1.0;
            *(float *)((char*)dst + 24) = -1.0;
            *(int8_t *)((char*)dst + 28) = *(int8_t *)((char*)src + 36);
            LOGD("Sensor Data (Type %d): data[0]=-1.0, data[1]=-1.0", b);
        }
    } else {
        OriginalConvertToSensorEvent(src, dst);
    }
}

void doSensorHook() {
    const char* path = LIBSF_PATH_64;
    if (access(path, F_OK) != 0) {
        path = LIBSF_PATH_32;
    }

    if (access(path, F_OK) != 0) {
        // 在非系统服务进程中，这个报错是正常的，改为日志打印
        LOGD("libsensorservice.so not found in standard paths, skipping hook (likely not in system_server)");
        return;
    }

    SandHook::ElfImg sensorService(path);

    if (!sensorService.isValid()) {
        // 在应用进程中，没有权限读取 /proc/self/maps 或系统库是正常的
        LOGD("failed to load %s via ElfImg (likely missing permissions or not loaded in this process)", path);
        return;
    }

    auto sensorWrite = sensorService.getSymbolAddress<void*>("_ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventm");
    if (sensorWrite == nullptr) {
        sensorWrite = sensorService.getSymbolAddress<void*>("_ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventj");
    }
    if (sensorWrite == nullptr) {
        sensorWrite = sensorService.getSymbolAddress<void*>("_ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventy");
    }
    // 前缀查找 fallback: 如果不同 Android 版本的符号修饰不同，尝试模糊查找
    if (sensorWrite == nullptr) {
        sensorWrite = sensorService.getSymbolAddressByPrefix<void*>("_ZN7android16SensorEventQueue5write");
    }

    auto convertToSensorEvent = sensorService.getSymbolAddress<void*>("_ZN7android8hardware7sensors4V1_014implementation20convertToSensorEventERKNS2_5EventEP15sensors_event_t");
    if (convertToSensorEvent == nullptr) {
        convertToSensorEvent = sensorService.getSymbolAddressByPrefix<void*>("_ZN7android8hardware7sensors4V1_014implementation20convertToSensorEvent");
    }

    LOGD("Dobby SensorEventQueue::write found at %p", sensorWrite);
    LOGD("Dobby convertToSensorEvent found at %p", convertToSensorEvent);

    if (sensorWrite != nullptr) {
        OriginalSensorEventQueueWrite = (OriginalSensorEventQueueWriteType)InlineHook(sensorWrite, (void *)SensorEventQueueWrite);
    }

    if (convertToSensorEvent != nullptr) {
        OriginalConvertToSensorEvent = (OriginalConvertToSensorEventType)InlineHook(convertToSensorEvent, (void *)ConvertToSensorEvent);
    }
}
