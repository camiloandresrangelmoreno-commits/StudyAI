# StudyAI — Planificador Académico Adaptativo con Validación Activa

Aplicación móvil nativa para Android desarrollada en **Kotlin** y **Jetpack Compose** para la gestión inteligente del tiempo de estudio, prevención de la procrastinación y validación activa del aprendizaje (*Active Recall*).

## Funcionalidades Principales
- **Planificación Adaptativa con Hora Límite:** Distribuye temas según nivel de dominio (`Bajo`, `Medio`, `Alto`), horas libres diarias y fecha/hora exacta de evaluación.
- **Cierre Automático por Vencimiento:** Bloquea evaluaciones cuya fecha y hora límite ya expiraron (`🔒 Finalizado`).
- **Reajuste Dinámico (*"No alcancé hoy"*):** Reprograma sesiones pendientes hacia los días disponibles restantes recalculando duraciones para respetar la carga horaria diaria.
- **Sincronización sin Duplicados con Calendario Android:** Exporta todo el plan a `CalendarContract` mediante identificadores únicos por examen para actualizar horarios sin duplicar eventos.
- **Temporizador Pomodoro Persistente + Evaluación:** Reloj en segundo plano con descansos proporcionales, filtro de calidad de síntesis escrita y quiz con retroalimentación guiada.
- **Simulacro General y Exportación:** Evaluación integral calificada de `0.0` a `5.0` y exportación directa del plan vía WhatsApp / Intent nativo.

## Tecnologías Utilizadas
- **Lenguaje:** Kotlin
- **Interfaz:** Jetpack Compose + Material Design 3
- **Persistencia Local:** `SharedPreferences` + Serialización JSON (`Gson`)
- **APIs de Android:** `CalendarContract`, `RingtoneManager`, `TimePickerDialog`, `Intent.ACTION_SEND`