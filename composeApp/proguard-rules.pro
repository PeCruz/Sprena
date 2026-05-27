# ============================================================================
# Sprena — Proguard rules (F1.1 baseline)
# ============================================================================
# proguard-android-optimize.txt (do Android SDK) já cobre o básico.
# Aqui adicionamos regras específicas das libs do projeto.

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
-dontwarn kotlin.**
-keepclasseswithmembers class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation

# ---------------------------------------------------------------------------
# kotlinx.coroutines
# ---------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    public <init>();
}
-keepclassmembers class ** implements kotlinx.coroutines.internal.MainDispatcherFactory {
    public <init>();
}

# ---------------------------------------------------------------------------
# kotlinx.serialization (usado no módulo shared)
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class **.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if class **.*$Companion {
  kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class <1>.<2> {
    <1>.<2>$Companion Companion;
}

# ---------------------------------------------------------------------------
# Koin DI
# ---------------------------------------------------------------------------
-keep class org.koin.** { *; }
-keepclassmembers class * {
    public <init>(...);
}

# ---------------------------------------------------------------------------
# Firebase (Firestore + futuras libs F1.3/F1.4)
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore usa reflection para mapear DTOs ↔ documentos.
# Preservar TODOS os DTOs e modelos serializados.
-keep class br.com.sprena.shared.**.data.dto.** { *; }
-keep class br.com.sprena.shared.**.domain.model.** { *; }
-keepclassmembers class br.com.sprena.shared.**.data.dto.** {
    <init>(...);
    <fields>;
}

# ---------------------------------------------------------------------------
# Compose
# ---------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# AndroidX Lifecycle & Navigation
# ---------------------------------------------------------------------------
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**

# ---------------------------------------------------------------------------
# F1.2 — Napier
# ---------------------------------------------------------------------------
-keep class io.github.aakira.napier.** { *; }
-dontwarn io.github.aakira.napier.**

# ---------------------------------------------------------------------------
# F1.2 — Firebase Crashlytics
# ---------------------------------------------------------------------------
# Crashlytics SDK (já coberto pelo bloco geral firebase.**, reforçar para deobfuscation):
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ---------------------------------------------------------------------------
# F1.3 — Firebase Auth (já coberto pelo bloco geral firebase.**)
# ---------------------------------------------------------------------------
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ---------------------------------------------------------------------------
# F1.3 — Google Tink (cripto da sessão)
# ---------------------------------------------------------------------------
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
# Protocol buffers usados pelo Tink:
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ---------------------------------------------------------------------------
# F1.3 — AndroidX DataStore Preferences
# ---------------------------------------------------------------------------
-keep class androidx.datastore.preferences.** { *; }
-dontwarn androidx.datastore.preferences.**
