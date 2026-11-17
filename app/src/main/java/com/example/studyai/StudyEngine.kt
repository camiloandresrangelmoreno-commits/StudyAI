package com.example.studyai

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import java.util.UUID

enum class NivelDominio(val etiqueta: String) {
    BAJO("Bajo (Explicación detallada y fundamentos)"),
    MEDIO("Medio (Repaso estructurado y práctica)"),
    ALTO("Alto (Ejercicios avanzados y autoevaluación)")
}

data class Examen(
    val id: String = UUID.randomUUID().toString(),
    val materia: String,
    val tema: String,
    val fechaLimite: String,
    val horaLimite: String? = "18:00",
    val nivel: NivelDominio,
    val horasDiariasAsignadas: Float = 2.0f
) {
    fun horaLimiteSegura(): String = horaLimite ?: "18:00"

    fun fechaHoraLimite(): LocalDateTime {
        val fecha = try {
            LocalDate.parse(fechaLimite)
        } catch (e: Exception) {
            LocalDate.now().plusDays(1)
        }
        val hora = try {
            LocalTime.parse(horaLimiteSegura())
        } catch (e: Exception) {
            LocalTime.of(18, 0)
        }
        return LocalDateTime.of(fecha, hora)
    }

    fun estaVencido(ahora: LocalDateTime = LocalDateTime.now()): Boolean {
        return ahora.isAfter(fechaHoraLimite())
    }
}

data class PreguntaQuiz(
    val pregunta: String,
    val opciones: List<String>,
    val respuestaCorrectaIndex: Int,
    val pistaError: String,
    val explicacion: String
)

data class SesionEstudio(
    val id: String = UUID.randomUUID().toString(),
    val examenId: String,
    val materia: String,
    val temaEspecifico: String,
    val tituloSesion: String,
    val guiaEstudioIA: String,
    val quizValidacion: List<PreguntaQuiz>,
    val fecha: String,
    val duracionMinutos: Int,
    val minutosDescanso: Int = 1,
    val notaEstudiante: String = "",
    val completada: Boolean = false
)

object StudyEngine {

    private const val PREFS_NAME = "study_ai_prefs_final"
    private const val KEY_EXAMENES = "examenes"
    private const val KEY_SESIONES = "sesiones"
    private const val KEY_HORAS_DIARIAS = "horas_diarias"
    private const val KEY_RACHA = "racha_dias"
    private const val KEY_ULTIMO_DIA_ESTUDIO = "ultimo_dia_estudio"

    private val gson = Gson()

    fun formatearMinutosAHoras(minutosTotales: Int): String {
        val horas = minutosTotales / 60
        val mins = minutosTotales % 60
        return if (horas > 0) "${horas}h ${mins}m ($minutosTotales min)" else "$minutosTotales min"
    }

    fun calcularMinutosDescanso(minutosEstudio: Int): Int {
        return when {
            minutosEstudio <= 5 -> 1
            minutosEstudio <= 15 -> 2
            minutosEstudio <= 30 -> 5
            minutosEstudio <= 45 -> 10
            else -> 15
        }
    }

    fun validarCalidadApunte(texto: String, tema: String): Pair<Boolean, String> {
        val limpio = texto.trim()
        val palabras = limpio.split("\\s+".toRegex()).filter { it.length >= 2 }
        val palabrasUnicas = palabras.map { it.lowercase() }.distinct()
        val frasesRelleno = listOf("no se que poner", "asdf", "qwerty", "nada", "prueba", "1234", "xxxx")

        return when {
            limpio.length < 20 -> false to "Tu síntesis es muy corta (mínimo 20 caracteres)."
            palabrasUnicas.size < 4 -> false to "Escribe una idea completa con al menos 4 palabras distintas."
            frasesRelleno.any { limpio.lowercase().contains(it) } ->
                false to "Escribe un concepto real aprendido sobre '$tema'."
            else -> true to "Síntesis válida"
        }
    }

    fun construirResumenExportable(examen: Examen, sesiones: List<SesionEstudio>): String {
        val sb = StringBuilder()
        sb.appendLine("📚 *PLAN DE ESTUDIO — ${examen.materia.uppercase()}*")
        sb.appendLine("⏰ Parcial/Entrega: ${examen.fechaLimite} a las ${examen.horaLimiteSegura()}")
        sb.appendLine("⚡ Meta diaria: ${examen.horasDiariasAsignadas} h/día")
        sb.appendLine("----------------------------------------")
        sesiones.groupBy { it.fecha }.forEach { (fecha, delDia) ->
            sb.appendLine("📅 *Día $fecha:*")
            delDia.forEachIndexed { idx, s ->
                val estado = if (s.completada) "✅" else "⏳"
                sb.appendLine("  $estado ${idx + 1}. ${s.temaEspecifico} (${s.duracionMinutos}m est. + ${s.minutosDescanso}m desc.)")
                if (s.notaEstudiante.isNotBlank()) {
                    sb.appendLine("     📝 Apunte: ${s.notaEstudiante}")
                }
            }
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("Generado con StudyAI")
        return sb.toString()
    }

    // Sincroniza TODO el plan en el calendario del celular y borra eventos previos del mismo examen para evitar duplicados
    fun sincronizarPlanEnCalendarioNativo(
        context: Context,
        examen: Examen,
        sesiones: List<SesionEstudio>
    ): Int {
        var calendarId = 1L
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                calendarId = cursor.getLong(0)
                do {
                    if (cursor.getInt(1) == 1) {
                        calendarId = cursor.getLong(0)
                        break
                    }
                } while (cursor.moveToNext())
            }
        }

        val marcadorUnico = "[StudyAI-ID:${examen.id}]"
        // Elimina cualquier evento anterior de este plan (por ejemplo si se reajustó el horario) -> CERO DUPLICADOS
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf("%$marcadorUnico%")
        )

        var insertados = 0
        val tz = TimeZone.getDefault().id
        val ahora = LocalDateTime.now()

        sesiones.filter { !it.completada }.groupBy { it.fecha }.forEach { (fechaStr, listaDelDia) ->
            val fecha = LocalDate.parse(fechaStr)
            var cursorHora = fecha.atTime(18, 0)
            if (fecha == LocalDate.now() && ahora.isAfter(cursorHora)) {
                cursorHora = ahora.plusMinutes(10)
            }

            listaDelDia.forEach { sesion ->
                val inicioMillis = cursorHora.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val finMillis = inicioMillis + (sesion.duracionMinutos * 60 * 1000L)

                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, inicioMillis)
                    put(CalendarContract.Events.DTEND, finMillis)
                    put(CalendarContract.Events.TITLE, "StudyAI: ${sesion.materia} - ${sesion.temaEspecifico}")
                    put(CalendarContract.Events.DESCRIPTION, "${sesion.guiaEstudioIA}\n\n$marcadorUnico")
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, tz)
                }
                if (context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null) {
                    insertados++
                }
                cursorHora = cursorHora.plusMinutes((sesion.duracionMinutos + sesion.minutosDescanso).toLong())
            }
        }

        // También agrega el hito de cierre/parcial
        val cierreMillis = examen.fechaHoraLimite().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val valuesCierre = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, cierreMillis)
            put(CalendarContract.Events.DTEND, cierreMillis + (60 * 60 * 1000L))
            put(CalendarContract.Events.TITLE, "🚨 PARCIAL / ENTREGA: ${examen.materia}")
            put(CalendarContract.Events.DESCRIPTION, "Evaluación final de ${examen.materia}\n\n$marcadorUnico")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, valuesCierre)

        return insertados
    }

    fun generarPlanConIA(
        examen: Examen,
        horasDisponiblesDia: Float
    ): List<SesionEstudio> {
        val hoy = LocalDate.now()
        val fechaHoraExamen = examen.fechaHoraLimite()
        val fechaExamen = fechaHoraExamen.toLocalDate()
        val horaExamen = fechaHoraExamen.toLocalTime()

        val diffDias = ChronoUnit.DAYS.between(hoy, fechaExamen).toInt().coerceAtLeast(0)

        val diasEfectivosEstudio = when {
            diffDias == 0 -> 1
            horaExamen.hour >= 10 -> diffDias + 1
            else -> diffDias
        }.coerceAtLeast(1)

        val temasSeparados = examen.tema
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(examen.tema.trim()) }

        val listaFinalTemas = if (temasSeparados.size >= 2) {
            temasSeparados
        } else {
            val base = temasSeparados.first()
            listOf(
                "$base — Fundamentos",
                "$base — Práctica Guiada",
                "$base — Simulacro Final"
            )
        }

        val presupuestoTotalMinutos = (diasEfectivosEstudio * horasDisponiblesDia * 60)
            .toInt()
            .coerceAtLeast(listaFinalTemas.size * 2)
        val minutosTotalesPorBloque = (presupuestoTotalMinutos / listaFinalTemas.size).coerceAtLeast(2)

        val minutosDescansoBloque = if (minutosTotalesPorBloque <= 4) 1 else (minutosTotalesPorBloque * 0.2f).toInt().coerceAtLeast(1)
        val minutosEstudioBloque = (minutosTotalesPorBloque - minutosDescansoBloque).coerceAtLeast(1)

        val sesionesGeneradas = mutableListOf<SesionEstudio>()

        listaFinalTemas.forEachIndexed { index, subtema ->
            val desplazamientoDias = (index * diasEfectivosEstudio) / listaFinalTemas.size
            val fechaSesion = hoy.plusDays(desplazamientoDias.toLong()).let {
                if (it.isAfter(fechaExamen)) fechaExamen else it
            }
            val contenidoYQuiz = construirContenidoAlineado(examen.materia, subtema, minutosEstudioBloque, examen.nivel)

            sesionesGeneradas.add(
                SesionEstudio(
                    examenId = examen.id,
                    materia = examen.materia,
                    temaEspecifico = subtema,
                    tituloSesion = "Sesión ${index + 1}: $subtema",
                    guiaEstudioIA = contenidoYQuiz.first,
                    quizValidacion = contenidoYQuiz.second,
                    fecha = fechaSesion.toString(),
                    duracionMinutos = minutosEstudioBloque,
                    minutosDescanso = minutosDescansoBloque
                )
            )
        }
        return sesionesGeneradas
    }

    // Reajusta todas las sesiones pendientes desde MAÑANA (si hoy no alcanzaste) respetando el límite de horas diarias
    fun reajustarPorDiaPerdido(
        sesiones: List<SesionEstudio>,
        examenes: List<Examen>
    ): List<SesionEstudio> {
        val hoy = LocalDate.now()
        val manana = hoy.plusDays(1)
        val ahora = LocalDateTime.now()

        val resultado = sesiones.toMutableList()

        examenes.forEach { examen ->
            if (examen.estaVencido(ahora)) return@forEach

            val indicesPendientes = resultado.indices.filter { idx ->
                resultado[idx].examenId == examen.id && !resultado[idx].completada
            }
            if (indicesPendientes.isEmpty()) return@forEach

            val fechaExamen = examen.fechaHoraLimite().toLocalDate()
            val horaExamen = examen.fechaHoraLimite().toLocalTime()

            // Si el examen es hoy mismo, no se puede mover a mañana; se mantiene hoy
            val fechaInicioReajuste = if (fechaExamen.isAfter(hoy)) manana else hoy
            val diffDias = ChronoUnit.DAYS.between(fechaInicioReajuste, fechaExamen).toInt().coerceAtLeast(0)
            val diasRestantes = when {
                diffDias == 0 -> 1
                horaExamen.hour >= 10 -> diffDias + 1
                else -> diffDias
            }.coerceAtLeast(1)

            // Recalcula el presupuesto estricto para los días restantes sin pasarse de horasDiariasAsignadas
            val presupuestoRestanteMin = (diasRestantes * examen.horasDiariasAsignadas * 60)
                .toInt()
                .coerceAtLeast(indicesPendientes.size * 2)
            val bloqueTotalMin = (presupuestoRestanteMin / indicesPendientes.size).coerceAtLeast(2)
            val nuevoDescanso = if (bloqueTotalMin <= 4) 1 else (bloqueTotalMin * 0.2f).toInt().coerceAtLeast(1)
            val nuevoEstudio = (bloqueTotalMin - nuevoDescanso).coerceAtLeast(1)

            indicesPendientes.forEachIndexed { pos, idxLista ->
                val sesionActual = resultado[idxLista]
                val saltoDias = (pos * diasRestantes) / indicesPendientes.size
                val nuevaFecha = fechaInicioReajuste.plusDays(saltoDias.toLong()).let {
                    if (it.isAfter(fechaExamen)) fechaExamen else it
                }
                val guiaActualizada = construirContenidoAlineado(
                    examen.materia,
                    sesionActual.temaEspecifico,
                    nuevoEstudio,
                    examen.nivel
                ).first

                resultado[idxLista] = sesionActual.copy(
                    fecha = nuevaFecha.toString(),
                    duracionMinutos = nuevoEstudio,
                    minutosDescanso = nuevoDescanso,
                    guiaEstudioIA = guiaActualizada
                )
            }
        }
        return resultado
    }

    private fun construirContenidoAlineado(
        materia: String,
        subtema: String,
        minutosEstudio: Int,
        nivel: NivelDominio
    ): Pair<String, List<PreguntaQuiz>> {
        val esSesionCorta = minutosEstudio <= 12
        val t = subtema.lowercase().trim()

        return when {
            t == "html" || (t.contains("html") && !t.contains("css")) -> {
                val guia = if (esSesionCorta) {
                    "Ficha rápida ($minutosEstudio min): En HTML5, <article> envuelve contenido independiente y <section> agrupa bloques temáticos. Usa 'defer' en <script> para cargar JS sin bloquear el HTML."
                } else {
                    "Guía profunda ($minutosEstudio min): 1) Semántica HTML5: <article> define piezas independientes, <section> divide bloques temáticos. 2) Rendimiento: <script defer> descarga el JS en paralelo y lo ejecuta al terminar el DOM."
                }
                val quiz = listOf(
                    PreguntaQuiz(
                        pregunta = "Según tu ficha de HTML5, ¿cuándo debes usar <article> en lugar de <section>?",
                        opciones = listOf(
                            "Cuando el contenido tiene sentido independiente por sí mismo (ej. una publicación)",
                            "Únicamente para dar estilo en negrita a un párrafo",
                            "Para reemplazar la etiqueta <head> del documento"
                        ),
                        respuestaCorrectaIndex = 0,
                        pistaError = "Piensa en qué etiqueta representa una pieza autocontenida que podrías publicar sola.",
                        explicacion = "<article> representa contenido independiente; <section> agrupa temas dentro de una página."
                    )
                )
                guia to quiz
            }

            t == "css" || (t.contains("css") && !t.contains("html")) -> {
                val guia = if (esSesionCorta) {
                    "Ficha rápida ($minutosEstudio min): Con 'box-sizing: border-box', padding y border se incluyen dentro del width total. En especificidad CSS, ID (#) pesa más que Clase (.) y Etiqueta."
                } else {
                    "Guía profunda ($minutosEstudio min): 1) Box Model: 'box-sizing: border-box' evita que padding y border aumenten el width. 2) Especificidad: estilo en línea > ID (#) > Clase (.) > Etiqueta."
                }
                val quiz = listOf(
                    PreguntaQuiz(
                        pregunta = "Según tu ficha de CSS, ¿qué logra 'box-sizing: border-box'?",
                        opciones = listOf(
                            "Incluye padding y border dentro del width y height declarados",
                            "Elimina automáticamente todos los márgenes externos",
                            "Centra todos los textos de la página"
                        ),
                        respuestaCorrectaIndex = 0,
                        pistaError = "Fíjate en qué pasa con el ancho total cuando agregas relleno (padding) y borde.",
                        explicacion = "Con border-box el tamaño total de la caja se mantiene exacto al width definido."
                    )
                )
                guia to quiz
            }

            t.contains("pl/sql") || t.contains("sql") || t.contains("base de datos") || t.contains("cursor") || t.contains("control") -> {
                val guia = if (esSesionCorta) {
                    "Ficha rápida ($minutosEstudio min): En '$subtema' ($materia), separa DECLARE, BEGIN...END y EXCEPTION. Usa %TYPE para heredar el tipo de columna y cursores explícitos (OPEN, FETCH, CLOSE) para recorrer filas."
                } else {
                    "Guía profunda ($minutosEstudio min): 1) Estructura en '$subtema': DECLARE para variables/cursores, BEGIN para control de flujo y EXCEPTION para errores. 2) Tipos anclados %TYPE y atributos de cursor (%FOUND, %ROWCOUNT)."
                }
                val quiz = listOf(
                    PreguntaQuiz(
                        pregunta = "En PL/SQL ($subtema), ¿qué secuencia sigue el ciclo de vida de un cursor explícito?",
                        opciones = listOf(
                            "OPEN -> FETCH -> CLOSE",
                            "SELECT -> DROP -> COMMIT",
                            "BEGIN -> ROLLBACK -> ALTER"
                        ),
                        respuestaCorrectaIndex = 0,
                        pistaError = "Primero se abre la consulta, luego se leen filas y finalmente se libera memoria.",
                        explicacion = "Un cursor explícito se abre (OPEN), recupera registros (FETCH) y se cierra (CLOSE)."
                    )
                )
                guia to quiz
            }

            else -> {
                val guia = if (esSesionCorta) {
                    "Ficha rápida ($minutosEstudio min) sobre '$subtema' ($materia): Identifica la definición central de '$subtema', su regla principal y resuelve 1 ejercicio práctico clave."
                } else {
                    "Guía profunda ($minutosEstudio min) sobre '$subtema' ($materia - Nivel ${nivel.name}): 1) Comprende los fundamentos de '$subtema'. 2) Analiza un ejemplo paso a paso. 3) Resuelve ejercicios aplicados."
                }
                val quiz = listOf(
                    PreguntaQuiz(
                        pregunta = "De acuerdo con tu sesión de '$subtema' en $materia, ¿qué confirma el dominio real del tema?",
                        opciones = listOf(
                            "Comprender su regla principal y resolver un caso práctico aplicado sin apoyo",
                            "Leer únicamente el título sin realizar ningún ejercicio",
                            "Saltar directamente al siguiente tema sin verificar errores"
                        ),
                        respuestaCorrectaIndex = 0,
                        pistaError = "Recuerda los pasos indicados en tu ficha de estudio para '$subtema'.",
                        explicacion = "Aplicar la regla principal en un caso práctico confirma la comprensión del tema."
                    )
                )
                guia to quiz
            }
        }
    }

    fun guardarDatos(
        context: Context,
        examenes: List<Examen>,
        sesiones: List<SesionEstudio>,
        horasDiarias: Float
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_EXAMENES, gson.toJson(examenes))
            .putString(KEY_SESIONES, gson.toJson(sesiones))
            .putFloat(KEY_HORAS_DIARIAS, horasDiarias)
            .apply()
    }

    fun cargarExamenes(context: Context): List<Examen> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_EXAMENES, null) ?: return emptyList()
        val type = object : TypeToken<List<Examen>>() {}.type
        return gson.fromJson(json, type)
    }

    fun cargarSesiones(context: Context): List<SesionEstudio> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESIONES, null) ?: return emptyList()
        val type = object : TypeToken<List<SesionEstudio>>() {}.type
        return gson.fromJson(json, type)
    }

    fun cargarHorasDiarias(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_HORAS_DIARIAS, 2.0f)
    }

    fun registrarRachaHoy(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hoy = LocalDate.now().toString()
        val ayer = LocalDate.now().minusDays(1).toString()
        val ultimo = prefs.getString(KEY_ULTIMO_DIA_ESTUDIO, "")
        var racha = prefs.getInt(KEY_RACHA, 0)

        if (ultimo != hoy) {
            racha = if (ultimo == ayer) racha + 1 else 1
            prefs.edit()
                .putString(KEY_ULTIMO_DIA_ESTUDIO, hoy)
                .putInt(KEY_RACHA, racha)
                .apply()
        }
        return racha
    }

    fun obtenerRacha(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RACHA, 0)
    }
}