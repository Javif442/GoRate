# Reglas de Producción GoRate Pro

# Eliminar Logs en versión release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Mantener atributos de anotaciones (Room, Moshi, Gson, Firebase)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Room Database y Entidades
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep class com.gorate.app.data.local.** { *; }

# Modelos de Dominio y Datos (necesario para serialización Firebase y Room)
-keep class com.gorate.app.domain.model.** { *; }

# Google ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }

# ViewBinding y vistas personalizadas
-keep class com.gorate.app.databinding.** { *; }
-keep class com.gorate.app.presentation.custom.** { *; }
