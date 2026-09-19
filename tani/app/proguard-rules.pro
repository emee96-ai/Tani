# Tani customer app — release shrinker rules.
# Keep metadata used by Kotlin serialization and Room-generated code.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep @kotlinx.serialization.Serializable class ** { *; }
-keep @androidx.room.Entity class ** { *; }

# Ktor may reference optional engines/features that are not packaged in Android.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
