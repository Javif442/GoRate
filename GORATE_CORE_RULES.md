# 🧠 Memoria Central de GoRate (Core Rules)
*Documentación técnica generada para mantener el estándar Senior en futuras actualizaciones.*

## 1. Arquitectura del Escáner (Overlay & Notificaciones)
- **Exclusividad:** El sistema está dedicado **100% a Uber**. Todo paquete que no contenga `uber` en `GoRateNotificationListener` se descarta automáticamente.
- **Anti-Duplicación:** El `TripRepositoryImpl` tiene un Mutex con filtros de tiempo (30s) y distancia (0.3km) para evitar que el OCR y el Push registren el mismo viaje dos veces.
- **Filtro Anti-Parpadeos (Push):** Se ignoran por completo los textos de estado de Uber como `"buscando"`, `"emparejando"`, `"emparejam"`, `"evaluando"`, para evitar notificaciones falsas. (Actualmente solucionado ignorando cualquier notificación que no contenga una cifra de precio mayor a cero `price == 0.0`).
- **Encendido de Pantalla:** Se implementó `wakeUpScreen()` en el `OverlayService` para que, al llegar una notificación de Uber con la pantalla bloqueada o apagada, esta se encienda automáticamente, dibuje la tarjeta flotante y reproduzca el sonido de alerta correspondiente, gracias a los flags `FLAG_SHOW_WHEN_LOCKED` y `PowerManager.SCREEN_BRIGHT_WAKE_LOCK`.

## 2. Motor de Extracción (UberParser)
- **Seguridad de Rango:** Solo se aceptan tarifas reales (`0.50` a `300.00`). Esto elimina los falsos `$0.01` del OCR.
- **Flexibilidad de Símbolos:** Las expresiones regulares (`Regex`) de precio y distancia detectan cualquier formato, ya sea con el símbolo o las letras de moneda al inicio o al final, o distancias con la unidad (`km`, `mi`) separada por espacio, además de procesar los avisos de adiciones como `+ $2.94`.
- **Heurística de Velocidad (Anti 64km):** Si el OCR omite un punto decimal (ej. lee `64` en vez de `6.4`) y la velocidad calculada supera los 130 km/h, el algoritmo inteligentemente divide la distancia para 10 para auto-corregirse.
- **Suma Inteligente:** Si la notificación muestra distancias desglosadas (ej. recogida + viaje), el algoritmo las suma, a menos que ya exista un "Total" explícito, evitando duplicar distancias.

## 3. Interfaz Visual (UI)
- **Burbuja Flotante:** Opacidad del `0.65` (65%) para no estorbar cuando está en reposo.
- **Tarjeta de Oferta:** Opacidad del `0.80` (80%) (Glassmorphism) para permitir ver el mapa de Uber por detrás.
- **Notificaciones Heads-Up:** Usan el color corporativo `brand_indigo`, tienen Prioridad Máxima (`PRIORITY_MAX`) para que bajen en la pantalla, y al tocarlas abren la aplicación (`PendingIntent`). Además, **cambian dinámicamente de color** al tono del semáforo asignado a la alerta (verde, amarillo, rojo).
- **Seguro Anti-Bucles:** El `switchService` en `MainFragment` exige que el usuario lo presione físicamente (`switchView.isPressed`). Se ignoran los cambios de estado hechos por el sistema al rotar o abrir la app.

## 4. Auditoría de Seguridad & Google Play
- **Indetectable para Uber:** GoRate funciona de forma 100% pasiva. No tiene rutinas de auto-aceptación de viajes (auto-clickers), no hace "spoofing" de GPS, no inyecta código, y opera solo leyendo la pantalla (`MediaProjection`) y notificaciones mediante APIs oficiales de Android.
- **Formularios en Play Store:** El equipo debe justificar el uso de `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` argumentando la necesidad de continuidad sobre apps de navegación y declarar apropiadamente `MediaProjection` en la sección de *Foreground Services* garantizando privacidad total del usuario (0 envío de la captura al exterior).