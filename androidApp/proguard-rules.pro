# kotlinx.serialization keeps generated serializers reachable through @Serializable.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.athena.reader.** {
    *** Companion;
}
-keepclasseswithmembers class com.athena.reader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# secp256k1 JNI bindings are reached from native code.
-keep class fr.acinq.secp256k1.** { *; }

# Room's generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

-dontwarn org.slf4j.**

# pdfbox-android references an optional JPEG2000 decoder (com.gemalto.jp2)
# that isn't bundled; only needed if a PDF embeds JPX images.
-dontwarn com.gemalto.jp2.**
