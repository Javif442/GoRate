# Informe Completo — CFanalizer

**Uso:** personal | **Plataforma:** Android (Kotlin) | **Estado general:** 🟡 En construcción (núcleo de lectura completo, faltan overlay + motor de cálculo + config)

---

## 1. Estructura del proyecto (completa, incluyendo lo que falta)

```
trip-assistant/
├── AndroidManifest_snippet.xml          ✅ Listo
├── strings_snippet.xml                  ✅ Listo
└── app/src/main/
    ├── java/com/cfanalizer/app/
    │   ├── model/
    │   │   └── TripData.kt              ✅ Listo
    │   ├── parser/
    │   │   ├── TripDataParser.kt        ✅ Listo (interfaz Strategy)
    │   │   ├── UberParser.kt            ✅ Listo
    │   │   └── InDriveParser.kt         ✅ Listo
    │   ├── accessibility/
    │   │   └── TripAccessibilityService.kt  ✅ Listo (solo lectura)
    │   ├── calculator/                  🔲 PENDIENTE
    │   │   ├── ProfitCalculator.kt          → motor de cálculo (rentabilidad, veredicto)
    │   │   └── CalculatorConfig.kt          → parámetros configurables (tarifa/km, gasto fijo, factor tiempo)
    │   ├── overlay/                     🔲 PENDIENTE
    │   │   ├── OverlayService.kt            → foreground service que dibuja la ventana flotante
    │   │   └── OverlayView.kt / .xml        → tarjeta flotante (precio, distancia, ganancia, veredicto)
    │   ├── settings/                    🔲 PENDIENTE
    │   │   ├── SettingsActivity.kt          → pantalla de configuración
    │   │   └── PreferencesRepository.kt     → wrapper sobre SharedPreferences
    │   ├── history/                     🔲 PENDIENTE (opcional, "extra" del doc original)
    │   │   ├── TripHistoryDb.kt             → Room DB local
    │   │   └── TripHistoryDao.kt
    │   └── MainActivity.kt              🔲 PENDIENTE → onboarding + pedir permisos (overlay + accesibilidad)
    └── res/
        ├── xml/
        │   └── accessibility_service_config.xml  ✅ Listo
        ├── layout/                      🔲 PENDIENTE (overlay_card.xml, activity_settings.xml)
        └── values/
            ├── strings.xml              🟡 Falta fusionar strings_snippet.xml
            └── colors.xml               🔲 PENDIENTE (paleta: #22c55e, #f59e0b, #ef4444, #FAFAFA, #1F2937)
```

**Leyenda:** ✅ Código entregado y funcional · 🟡 Parcial · 🔲 No iniciado

---

## 2. Principio de diseño transversal (aplica a TODO el proyecto)

> **Solo lectura, siempre.** Ningún componente de esta app debe llamar a `performAction()`, inyectar gestos, interceptar red, ni modificar el comportamiento de Uber/InDrive. Esto es lo que mantiene el riesgo de bloqueo de cuenta en su nivel más bajo posible (ver análisis de riesgo de mensajes anteriores). Cualquier código nuevo que se agregue debe respetar esta regla.

---

## 3. Permisos requeridos (resumen)

| Permiso | Para qué | Riesgo de Play Store |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Leer texto en pantalla de Uber/InDrive | Alto si se sube a Play Store; bajo en sideload personal |
| `SYSTEM_ALERT_WINDOW` | Mostrar la ventana flotante | Medio — requiere que el usuario lo active manualmente en Ajustes |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Mantener el servicio vivo en segundo plano (Android 14+) | Bajo |
| ~~INTERNET~~ | No se usa — app 100% local | N/A |

---

## 4. Código completo entregado hasta ahora

### 4.1 `model/TripData.kt`
```kotlin
package com.gorate.app.model

data class TripData(
    val source: TripSource,
    val price: Double? = null,
    val distanceKm: Double? = null,
    val estimatedTimeMin: Double? = null,
    val rawTexts: List<String> = emptyList()
) {
    val isComplete: Boolean
        get() = price != null && distanceKm != null && estimatedTimeMin != null
}

enum class TripSource { UBER, INDRIVE, UNKNOWN }
```

### 4.2 `parser/TripDataParser.kt` (interfaz Strategy)
```kotlin
package com.gorate.app.parser

import android.view.accessibility.AccessibilityNodeInfo
import com.gorate.app.model.TripData

interface TripDataParser {
    val targetPackageName: String
    fun parse(rootNode: AccessibilityNodeInfo): TripData

    fun collectAllText(node: AccessibilityNodeInfo?, acc: MutableList<String> = mutableListOf()): List<String> {
        if (node == null) return acc
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { acc.add(it) }
        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), acc)
        }
        return acc
    }
}
```

### 4.3 `parser/UberParser.kt`
```kotlin
package com.gorate.app.parser

import android.view.accessibility.AccessibilityNodeInfo
import com.gorate.app.model.TripData
import com.gorate.app.model.TripSource

class UberParser : TripDataParser {
    override val targetPackageName = "com.ubercab.driver"

    private val priceRegex = Regex("""\$\s?(\d+[.,]?\d*)""")
    private val distanceRegex = Regex("""(\d+[.,]?\d*)\s?km""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""(\d+)\s?min""", RegexOption.IGNORE_CASE)

    override fun parse(rootNode: AccessibilityNodeInfo): TripData {
        val texts = collectAllText(rootNode)
        val joined = texts.joinToString(" | ")

        val price = priceRegex.find(joined)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        val distance = distanceRegex.find(joined)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        val time = timeRegex.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()

        return TripData(TripSource.UBER, price, distance, time, texts)
    }
}
```

### 4.4 `parser/InDriveParser.kt`
```kotlin
package com.gorate.app.parser

import android.view.accessibility.AccessibilityNodeInfo
import com.gorate.app.model.TripData
import com.gorate.app.model.TripSource

class InDriveParser : TripDataParser {
    override val targetPackageName = "sinet.startup.inDriver"

    private val priceRegex = Regex("""\$\s?(\d+[.,]?\d*)""")
    private val distanceRegex = Regex("""(\d+[.,]?\d*)\s?km""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""(\d+)\s?min""", RegexOption.IGNORE_CASE)

    override fun parse(rootNode: AccessibilityNodeInfo): TripData {
        val texts = collectAllText(rootNode)
        val joined = texts.joinToString(" | ")

        val price = priceRegex.find(joined)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        val distance = distanceRegex.find(joined)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        val time = timeRegex.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()

        return TripData(TripSource.INDRIVE, price, distance, time, texts)
    }
}
```

### 4.5 `accessibility/TripAccessibilityService.kt`
```kotlin
package com.gorate.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.gorate.app.model.TripData
import com.gorate.app.parser.InDriveParser
import com.gorate.app.parser.TripDataParser
import com.gorate.app.parser.UberParser

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TripAccessibilityService"
    }

    private val parsers: List<TripDataParser> = listOf(UberParser(), InDriveParser())

    var onTripDataUpdated: ((TripData) -> Unit)? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val parser = parsers.find { it.targetPackageName == packageName } ?: return

        val relevantEventTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        if (event.eventType !in relevantEventTypes) return

        val root = rootInActiveWindow ?: return
        try {
            val tripData = parser.parse(root)
            if (tripData.isComplete) {
                onTripDataUpdated?.invoke(tripData)
            } else {
                Log.d(TAG, "Lectura incompleta para ${tripData.source}: ${tripData.rawTexts}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando datos de ${parser.targetPackageName}", e)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Servicio de accesibilidad interrumpido por el sistema")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Servicio de accesibilidad conectado (modo solo lectura)")
    }
}
```

### 4.6 `res/xml/accessibility_service_config.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="200"
    android:packageNames="com.ubercab.driver,sinet.startup.inDriver"
    android:description="@string/accessibility_service_description" />
```

### 4.7 `AndroidManifest_snippet.xml`
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<application>
    <service
        android:name=".accessibility.TripAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="false"
        android:foregroundServiceType="specialUse">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="trip_profitability_overlay" />
    </service>
</application>
```

### 4.8 `strings_snippet.xml`
```xml
<string name="accessibility_service_description">
    Lee de forma local el precio, distancia y tiempo estimado mostrados en pantalla
    por apps de viajes compatibles, para calcular tu ganancia neta por viaje.
    No modifica ni interactúa con dichas apps, y no envía datos a internet.
</string>
```

---

## 5. Lo que falta por construir (próximos pasos, en orden recomendado)

1. **`ProfitCalculator.kt` + `CalculatorConfig.kt`** — toma un `TripData` completo y devuelve precio/km, ganancia neta y veredicto (✅/⚠️/❌) usando los parámetros configurables (tarifa mínima, gasto fijo diario, factor corrector de tiempo).
2. **`OverlayService.kt` + layout de la tarjeta flotante** — foreground service que dibuja la ventana sobre Uber/InDrive usando `WindowManager`, escucha `onTripDataUpdated` del servicio de accesibilidad y actualiza la UI en tiempo real.
3. **`MainActivity.kt`** — pantalla de bienvenida que guía al usuario a activar accesibilidad + permiso de overlay (ambos requieren activación manual del usuario, no se pueden pedir por código como un permiso normal).
4. **`SettingsActivity.kt` + `PreferencesRepository.kt`** — pantalla para editar los parámetros del cálculo, con `SharedPreferences`.
5. *(Opcional)* **Historial con Room** — si quieres guardar viajes para exportar a CSV más adelante.

---

## 6. Riesgo y alcance (recordatorio)

- Este proyecto es para **uso personal**, sin publicación en Play Store ni distribución pública.
- El diseño actual es **estrictamente de solo lectura**: no automatiza acciones, no modifica tráfico de red, no hace spoofing de GPS.
- Aun así, **no existe garantía cero** de que Uber/InDrive no detecten el uso de accesibilidad/overlay — mantener el scope como está descrito minimiza, pero no elimina, ese riesgo.
