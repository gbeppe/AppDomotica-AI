# Kotlin Serialization
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class com.domopi.app.data.** { *; }

# Paho MQTT
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

# Ktor & OkHttp
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn rx.**

# Compose
-keep class androidx.compose.compiler.plugins.kotlin.** { *; }

# General
-keepattributes Signature, Annotation, InnerClasses, EnclosingMethod
