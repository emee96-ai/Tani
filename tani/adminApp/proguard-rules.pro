# Tani admin app — release shrinker rules.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep @kotlinx.serialization.Serializable class ** { *; }

-dontwarn io.ktor.**
-dontwarn org.slf4j.**
