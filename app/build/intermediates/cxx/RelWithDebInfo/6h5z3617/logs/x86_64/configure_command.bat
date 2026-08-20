@echo off
"C:\\Users\\dvmrn\\AppData\\Local\\Temp\\codex-android-tools\\android-sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\dvmrn\\Repositórios\\ApenasDance-AiMotionTrackingDanceGame\\app\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=26" ^
  "-DANDROID_PLATFORM=android-26" ^
  "-DANDROID_ABI=x86_64" ^
  "-DCMAKE_ANDROID_ARCH_ABI=x86_64" ^
  "-DANDROID_NDK=C:\\Users\\dvmrn\\AppData\\Local\\Temp\\codex-android-tools\\android-sdk\\ndk\\26.3.11579264" ^
  "-DCMAKE_ANDROID_NDK=C:\\Users\\dvmrn\\AppData\\Local\\Temp\\codex-android-tools\\android-sdk\\ndk\\26.3.11579264" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Users\\dvmrn\\AppData\\Local\\Temp\\codex-android-tools\\android-sdk\\ndk\\26.3.11579264\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Users\\dvmrn\\AppData\\Local\\Temp\\codex-android-tools\\android-sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\dvmrn\\Repositórios\\ApenasDance-AiMotionTrackingDanceGame\\app\\build\\intermediates\\cxx\\RelWithDebInfo\\6h5z3617\\obj\\x86_64" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\dvmrn\\Repositórios\\ApenasDance-AiMotionTrackingDanceGame\\app\\build\\intermediates\\cxx\\RelWithDebInfo\\6h5z3617\\obj\\x86_64" ^
  "-DCMAKE_BUILD_TYPE=RelWithDebInfo" ^
  "-BC:\\Users\\dvmrn\\Repositórios\\ApenasDance-AiMotionTrackingDanceGame\\app\\.cxx\\RelWithDebInfo\\6h5z3617\\x86_64" ^
  -GNinja
