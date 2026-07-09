#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>

#include "libyuv.h"

namespace {

uint8_t* DirectAddress(JNIEnv* env, jobject buffer) {
    return reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
}

jlong DirectCapacity(JNIEnv* env, jobject buffer) {
    return env->GetDirectBufferCapacity(buffer);
}

libyuv::RotationMode NormalizeRotation(jint rotation) {
    int normalized = rotation % 360;
    if (normalized < 0) {
        normalized += 360;
    }
    switch (normalized) {
        case 90:
            return libyuv::kRotate90;
        case 180:
            return libyuv::kRotate180;
        case 270:
            return libyuv::kRotate270;
        default:
            return libyuv::kRotate0;
    }
}

int I420Size(int width, int height) {
    const int chroma_width = (width + 1) / 2;
    const int chroma_height = (height + 1) / 2;
    return width * height + 2 * chroma_width * chroma_height;
}

uint8_t* PlaneU(uint8_t* i420, int width, int height) {
    return i420 + width * height;
}

uint8_t* PlaneV(uint8_t* i420, int width, int height) {
    return PlaneU(i420, width, height) + ((width + 1) / 2) * ((height + 1) / 2);
}

const uint8_t* PlaneUConst(const uint8_t* i420, int width, int height) {
    return i420 + width * height;
}

const uint8_t* PlaneVConst(const uint8_t* i420, int width, int height) {
    return PlaneUConst(i420, width, height) + ((width + 1) / 2) * ((height + 1) / 2);
}

void PutPixel(uint8_t* rgba, int width, int height, int stride, int x, int y, int r, int g, int b) {
    if (x < 0 || y < 0 || x >= width || y >= height) {
        return;
    }
    uint8_t* pixel = rgba + y * stride + x * 4;
    pixel[0] = static_cast<uint8_t>(r);
    pixel[1] = static_cast<uint8_t>(g);
    pixel[2] = static_cast<uint8_t>(b);
    pixel[3] = 255;
}

void DrawDisc(uint8_t* rgba, int width, int height, int stride, int cx, int cy, int radius, int r, int g, int b) {
    const int radius_sq = radius * radius;
    for (int dy = -radius; dy <= radius; ++dy) {
        for (int dx = -radius; dx <= radius; ++dx) {
            if (dx * dx + dy * dy <= radius_sq) {
                PutPixel(rgba, width, height, stride, cx + dx, cy + dy, r, g, b);
            }
        }
    }
}

void DrawLine(uint8_t* rgba, int width, int height, int stride, int x0, int y0, int x1, int y1, int radius, int r, int g, int b) {
    const int dx = std::abs(x1 - x0);
    const int sx = x0 < x1 ? 1 : -1;
    const int dy = -std::abs(y1 - y0);
    const int sy = y0 < y1 ? 1 : -1;
    int error = dx + dy;

    while (true) {
        DrawDisc(rgba, width, height, stride, x0, y0, radius, r, g, b);
        if (x0 == x1 && y0 == y1) {
            break;
        }
        const int e2 = 2 * error;
        if (e2 >= dy) {
            error += dy;
            x0 += sx;
        }
        if (e2 <= dx) {
            error += dx;
            y0 += sy;
        }
    }
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_sintonia_sincronia_processing_NativeLibyuvBridge_convertAndroid420ToRgba(
    JNIEnv* env,
    jobject,
    jobject y_buffer,
    jobject u_buffer,
    jobject v_buffer,
    jint y_row_stride,
    jint u_row_stride,
    jint v_row_stride,
    jint uv_pixel_stride,
    jint crop_left,
    jint crop_top,
    jint crop_width,
    jint crop_height,
    jint target_width,
    jint target_height,
    jint rotation_degrees,
    jobject temp_i420_a,
    jobject temp_i420_b,
    jobject rgba_buffer,
    jint rgba_stride) {
    uint8_t* y_base = DirectAddress(env, y_buffer);
    uint8_t* u_base = DirectAddress(env, u_buffer);
    uint8_t* v_base = DirectAddress(env, v_buffer);
    uint8_t* temp_a = DirectAddress(env, temp_i420_a);
    uint8_t* temp_b = DirectAddress(env, temp_i420_b);
    uint8_t* rgba = DirectAddress(env, rgba_buffer);
    if (!y_base || !u_base || !v_base || !temp_a || !temp_b || !rgba) {
        return -1;
    }
    if (crop_width <= 0 || crop_height <= 0 || target_width <= 0 || target_height <= 0) {
        return -2;
    }

    const libyuv::RotationMode rotation = NormalizeRotation(rotation_degrees);
    const int final_width = (rotation == libyuv::kRotate90 || rotation == libyuv::kRotate270)
        ? target_height
        : target_width;
    const int final_height = (rotation == libyuv::kRotate90 || rotation == libyuv::kRotate270)
        ? target_width
        : target_height;
    if (DirectCapacity(env, rgba_buffer) < static_cast<jlong>(rgba_stride) * final_height) {
        return -3;
    }

    const uint8_t* src_y = y_base + crop_top * y_row_stride + crop_left;
    const uint8_t* src_u = u_base + (crop_top / 2) * u_row_stride + (crop_left / 2) * uv_pixel_stride;
    const uint8_t* src_v = v_base + (crop_top / 2) * v_row_stride + (crop_left / 2) * uv_pixel_stride;

    const bool needs_scale = crop_width != target_width || crop_height != target_height;
    const bool needs_rotation = rotation != libyuv::kRotate0;
    if (!needs_scale && !needs_rotation) {
        return libyuv::Android420ToABGR(
            src_y,
            y_row_stride,
            src_u,
            u_row_stride,
            src_v,
            v_row_stride,
            uv_pixel_stride,
            rgba,
            rgba_stride,
            crop_width,
            crop_height);
    }

    const int max_i420_size = std::max(I420Size(crop_width, crop_height), I420Size(target_width, target_height));
    if (DirectCapacity(env, temp_i420_a) < max_i420_size || DirectCapacity(env, temp_i420_b) < max_i420_size) {
        return -4;
    }

    int result = libyuv::Android420ToI420Rotate(
        src_y,
        y_row_stride,
        src_u,
        u_row_stride,
        src_v,
        v_row_stride,
        uv_pixel_stride,
        temp_a,
        crop_width,
        PlaneU(temp_a, crop_width, crop_height),
        (crop_width + 1) / 2,
        PlaneV(temp_a, crop_width, crop_height),
        (crop_width + 1) / 2,
        crop_width,
        crop_height,
        libyuv::kRotate0);
    if (result != 0) {
        return result;
    }

    const uint8_t* current = temp_a;
    int current_width = crop_width;
    int current_height = crop_height;

    if (needs_scale) {
        result = libyuv::I420Scale(
            current,
            current_width,
            PlaneUConst(current, current_width, current_height),
            (current_width + 1) / 2,
            PlaneVConst(current, current_width, current_height),
            (current_width + 1) / 2,
            current_width,
            current_height,
            temp_b,
            target_width,
            PlaneU(temp_b, target_width, target_height),
            (target_width + 1) / 2,
            PlaneV(temp_b, target_width, target_height),
            (target_width + 1) / 2,
            target_width,
            target_height,
            libyuv::kFilterBilinear);
        if (result != 0) {
            return result;
        }
        current = temp_b;
        current_width = target_width;
        current_height = target_height;
    }

    if (needs_rotation) {
        uint8_t* rotation_dst = current == temp_a ? temp_b : temp_a;
        result = libyuv::I420Rotate(
            current,
            current_width,
            PlaneUConst(current, current_width, current_height),
            (current_width + 1) / 2,
            PlaneVConst(current, current_width, current_height),
            (current_width + 1) / 2,
            rotation_dst,
            final_width,
            PlaneU(rotation_dst, final_width, final_height),
            (final_width + 1) / 2,
            PlaneV(rotation_dst, final_width, final_height),
            (final_width + 1) / 2,
            current_width,
            current_height,
            rotation);
        if (result != 0) {
            return result;
        }
        current = rotation_dst;
        current_width = final_width;
        current_height = final_height;
    }

    return libyuv::I420ToABGR(
        current,
        current_width,
        PlaneUConst(current, current_width, current_height),
        (current_width + 1) / 2,
        PlaneVConst(current, current_width, current_height),
        (current_width + 1) / 2,
        rgba,
        rgba_stride,
        current_width,
        current_height);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sintonia_sincronia_processing_NativeLibyuvBridge_drawPoseSkeletonRgba(
    JNIEnv* env,
    jobject,
    jobject rgba_buffer,
    jint width,
    jint height,
    jint rgba_stride,
    jfloatArray landmarks,
    jint landmark_count,
    jintArray connections,
    jint connection_count,
    jfloat visibility_threshold,
    jint connection_red,
    jint connection_green,
    jint connection_blue,
    jint landmark_red,
    jint landmark_green,
    jint landmark_blue,
    jfloat connection_thickness,
    jfloat landmark_radius) {
    uint8_t* rgba = DirectAddress(env, rgba_buffer);
    if (!rgba) {
        return -1;
    }
    if (DirectCapacity(env, rgba_buffer) < static_cast<jlong>(rgba_stride) * height) {
        return -2;
    }
    jfloat* landmark_values = env->GetFloatArrayElements(landmarks, nullptr);
    jint* connection_values = env->GetIntArrayElements(connections, nullptr);
    if (!landmark_values || !connection_values) {
        if (landmark_values) {
            env->ReleaseFloatArrayElements(landmarks, landmark_values, JNI_ABORT);
        }
        if (connection_values) {
            env->ReleaseIntArrayElements(connections, connection_values, JNI_ABORT);
        }
        return -3;
    }

    const int line_radius = std::max(1, static_cast<int>(std::round(connection_thickness / 2.0f)));
    const int point_radius = std::max(1, static_cast<int>(std::round(landmark_radius)));

    for (int i = 0; i < connection_count; ++i) {
        const int start = connection_values[i * 2];
        const int end = connection_values[i * 2 + 1];
        if (start < 0 || end < 0 || start >= landmark_count || end >= landmark_count) {
            continue;
        }
        const float start_visibility = landmark_values[start * 3 + 2];
        const float end_visibility = landmark_values[end * 3 + 2];
        if (start_visibility < visibility_threshold || end_visibility < visibility_threshold) {
            continue;
        }
        const int x0 = static_cast<int>(std::round(landmark_values[start * 3] * width));
        const int y0 = static_cast<int>(std::round(landmark_values[start * 3 + 1] * height));
        const int x1 = static_cast<int>(std::round(landmark_values[end * 3] * width));
        const int y1 = static_cast<int>(std::round(landmark_values[end * 3 + 1] * height));
        DrawLine(rgba, width, height, rgba_stride, x0, y0, x1, y1, line_radius, connection_red, connection_green, connection_blue);
    }

    for (int i = 0; i < landmark_count; ++i) {
        if (landmark_values[i * 3 + 2] < visibility_threshold) {
            continue;
        }
        const int x = static_cast<int>(std::round(landmark_values[i * 3] * width));
        const int y = static_cast<int>(std::round(landmark_values[i * 3 + 1] * height));
        DrawDisc(rgba, width, height, rgba_stride, x, y, point_radius, landmark_red, landmark_green, landmark_blue);
    }

    env->ReleaseFloatArrayElements(landmarks, landmark_values, JNI_ABORT);
    env->ReleaseIntArrayElements(connections, connection_values, JNI_ABORT);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sintonia_sincronia_processing_NativeLibyuvBridge_rgbaToYuv420(
    JNIEnv* env,
    jobject,
    jobject rgba_buffer,
    jint width,
    jint height,
    jint rgba_stride,
    jobject output_buffer,
    jint output_capacity,
    jboolean planar) {
    const uint8_t* rgba = DirectAddress(env, rgba_buffer);
    uint8_t* output = DirectAddress(env, output_buffer);
    if (!rgba || !output) {
        return -1;
    }
    const int y_size = width * height;
    const int chroma_width = (width + 1) / 2;
    const int chroma_height = (height + 1) / 2;
    const int uv_size = chroma_width * chroma_height;
    const int required = y_size + 2 * uv_size;
    if (output_capacity < required || DirectCapacity(env, output_buffer) < required) {
        return -2;
    }

    if (planar) {
        return libyuv::ABGRToI420(
            rgba,
            rgba_stride,
            output,
            width,
            output + y_size,
            chroma_width,
            output + y_size + uv_size,
            chroma_width,
            width,
            height);
    }

    return libyuv::ABGRToNV12(
        rgba,
        rgba_stride,
        output,
        width,
        output + y_size,
        width,
        width,
        height);
}
