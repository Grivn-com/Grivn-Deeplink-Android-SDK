# WF-2 #14: keep Gson-serialized model classes + their fields so R8/ProGuard
# minification in a consuming app's release build doesn't rename them and break
# JSON (de)serialization. Gson matches by field name / @SerializedName, which
# minification would otherwise mangle.
-keepclassmembers,allowobfuscation class com.osdl.dynamiclinks.network.** {
    <fields>;
}
-keep class com.osdl.dynamiclinks.network.** { *; }
-keep class com.osdl.dynamiclinks.DynamicLinkShortenResponse { *; }

# Gson's own generic-type machinery (TypeToken) under R8.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature, *Annotation*
