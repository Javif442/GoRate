# Renombrado a CFanalizer — qué más ajustar en Android Studio

Ya está listo en el código fuente (paquete `com.gorate.app` en todas las clases).
Faltan 2 ajustes que viven en tu proyecto de Android Studio, no en estos archivos sueltos:

1. **`app/build.gradle(.kts)`** — cambia el `applicationId`:
   ```kotlin
   defaultConfig {
       applicationId = "com.gorate.app"
       ...
   }
   ```

2. **Nombre visible de la app** — ya agregado en `strings_snippet.xml`:
   ```xml
   <string name="app_name">CFanalizer</string>
   ```
   Confirma que tu `AndroidManifest.xml` use `android:label="@string/app_name"` en el tag `<application>`.

3. *(Opcional pero recomendado)* Si ya tenías el proyecto creado con otro paquete,
   en Android Studio usa **Refactor → Rename** sobre la carpeta del paquete en vez
   de mover archivos a mano — evita romper referencias en el `AndroidManifest.xml`
   generado automáticamente por el IDE.
