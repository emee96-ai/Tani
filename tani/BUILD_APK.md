# Build Tani APK

بعد تجهيز Codespace مرة واحدة، بناء نسخة Debug واختبارها يتم بأمر واحد من جذر مشروع `tani`:

```bash
./scripts/build_apk.sh
```

السكربت:
1. يشغل `qa/run_static_checks.sh`.
2. يبني `assembleDebug` باستخدام Gradle Wrapper 8.9.
3. ينسخ الناتج إلى `release/Tani-debug.apk`.
4. ينشئ SHA256 بجانب الـAPK.

إذا توقف أول تشغيل عند تنزيل Gradle، فالمطلوب فقط أن يكون الـCodespace قادراً على الوصول إلى `services.gradle.org`. لا تحتاج لتثبيت Gradle يدوياً لأن الـWrapper وJAR موجودان داخل المشروع.
