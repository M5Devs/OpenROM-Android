# OpenROM-Android — release shrinking rules.
#
# The plugin and storage layers are reached through view binding and the JSON
# layer, which R8 cannot always see; keeping the model classes avoids subtle
# runtime failures in release builds.
-keep class com.m5dev.openrom.databinding.** { *; }
-keep class com.m5dev.openrom.plugin.** { *; }
-keep class com.m5dev.openrom.core.** { *; }
-keep class com.m5dev.openrom.storage.** { *; }

# org.json is a platform library; keep its accessor signatures.
-keepclassmembers class * {
    @org.json.** *;
}

-dontwarn kotlinx.coroutines.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
