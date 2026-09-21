# ==============================================================================
# Reglas de Seguridad, Anti-Hacking y Ofuscación Avanzada - GoRate Pro
# ==============================================================================

# 1. Ofuscación y Repaquetizado Agresivo (Anti-Ingeniería Inversa)
# Aplana y repaqueta todas las clases en el paquete raíz para destruir la estructura de paquetes original
-repackageclasses ''
-allowaccessmodification
-renamesourcefileattribute ""
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Supresión Total de Logs y Rastros en Producción
# Garantiza que ninguna información confidencial (precios, coordenadas, tokens) se fugue a Logcat
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(...);
}

-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# 3. Room Database y Persistencia Local
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep class com.gorate.app.data.local.** { *; }

# 4. Modelos de Dominio y Serialización (Firebase Firestore / Room)
-keep class com.gorate.app.domain.model.** { *; }
-keepclassmembers class com.gorate.app.domain.model.** {
    <fields>;
    <init>(...);
}

# 5. Firebase Core, Auth, Firestore y RemoteConfig
-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# 6. Google ML Kit Vision & Text Recognition
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# 7. ViewBinding y Vistas Personalizadas
-keep class com.gorate.app.databinding.** { *; }
-keep class com.gorate.app.presentation.custom.** { *; }
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 8. Coroutines y Dispatchers de Kotlin
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
