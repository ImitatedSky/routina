# kotlinx.serialization：保留 @Serializable 資料模型的序列化器。
# 資料模型只在 JSON 檔與備份檔裡出現，R8 看不到它們被反射用到。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.routina.bite.model.**$$serializer { *; }
-keepclassmembers class com.routina.bite.model.** {
    *** Companion;
}

# 由系統以反射建立的元件
-keep class com.routina.bite.BiteApp
-keep class com.routina.bite.MainActivity
-keep class com.routina.bite.CapabilityActivity
