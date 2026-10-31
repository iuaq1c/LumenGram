LumenGram for Android 1.3.0 - an unofficial Telegram client.
License: GNU GPL v2 or later (see LICENSE), like Telegram for Android it is based on.

Based on Telegram for Android 13.0.3:
  https://github.com/DrKLO/Telegram  commit 0aae959806c57be8ad457980f2a5c6277ff18635
Submodules, unchanged, at these commits:
 022d60202e446ad1287b9fb68e687c8a0760788b TMessagesProj/jni/td (022d602)
 03d7b8662512af7528f476c02094862c1a37177d TMessagesProj/jni/tgcalls (03d7b86)
 54fac219c4ef0bc379dfffb0b8098725d77ac81b TMessagesProj/jni/third_party/absl (54fac21)
 4cbcd3078e6ae10d05124379623a1bf03fcb9350 TMessagesProj/jni/third_party/boost-regex (4cbcd30)
 2b44a3701a4788e1ef866ddc7f143060a3d196c9 TMessagesProj/jni/third_party/boringssl (2b44a37)
 54706fc6bc0cdecab7e9593974a4039cc038fca7 TMessagesProj/jni/third_party/dav1d (54706fc)
 45f1910444f34b02621f9f0426ea1a538a613c41 TMessagesProj/jni/third_party/ffmpeg (45f1910)
 31a5d6fbbef8136e4a2b3adaf7b95e2ccaa7436d TMessagesProj/jni/third_party/libprisma (31a5d6f)
 1024874c5919305883187e2953de8fcb4c3d7fa6 TMessagesProj/jni/third_party/libvpx (1024874)
 28ce69c2744a6aafdb58564e7b884aec3f66be5f TMessagesProj/jni/third_party/libyuv (28ce69c)
 652bdb7719f30b52b08e506645a7322ff1b2cc6f TMessagesProj/jni/third_party/openh264 (652bdb7)
 25bd7eb63e828e4bd242cc9b38d260b4b31c6605 TMessagesProj/jni/third_party/wamr (25bd7eb)
 be05b13e98b048f0b5a0f5fa8ce514d56db5f822 TMessagesProj/jni/third_party/xiph/ogg (be05b13)
 22244de5a79bd1d6d623c32e72bf1954b56235be TMessagesProj/jni/third_party/xiph/opus (22244de)
 a55c164e9891a9326188b7d4d216ec9a88373739 TMessagesProj/jni/third_party/xiph/opusfile (a55c164)
 31f1b542f88e7b4be9a01e749920d857535fc715 TMessagesProj/jni/tlottie (31f1b54)
 5c65e47cb8d68727c20d89382d0bc51292486b9a TMessagesProj/jni/wallet-engine (heads/master)
 919e50b2f6f64b04b712cdb13d558ff9ecf9c8ed TMessagesProj/lib/jlatexmath (heads/android)
 09b6c97c0d691c50cd4541e1573995d4066d6f5c TMessagesProj_Modules/media (09b6c97)

How to get the complete source:
  git clone https://github.com/DrKLO/Telegram.git LumenGram-android
  cd LumenGram-android && git checkout 0aae959806c57be8ad457980f2a5c6277ff18635
  git submodule update --init --recursive
  copy the "files" folder of this archive over it (it holds every changed and new file),
  and delete the files listed in DELETED.txt.
The same changes as a patch: lumengram.patch (git apply --binary lumengram.patch, new files are in "files").

LumenGram code: TMessagesProj/src/main/java/org/telegram/lumen, generators: tools/lumen.
Build: set LUMEN_API_ID and LUMEN_API_HASH to your own values from my.telegram.org, then
  ./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
Requires JDK 17+, Android SDK 36, NDK 27.2.12479018, CMake 3.22.1.
