package com.example.studyai

import android.Manifest
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.studyai.ui.theme.StudyAITheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyAITheme {
                StudyAIApp()
            }
        }
    }
}

@Composable
fun StudyAIApp() {
    val context = LocalContext.current

    var examenes by remember { mutableStateOf(StudyEngine.cargarExamenes(context)) }
    var sesiones by remember { mutableStateOf(StudyEngine.cargarSesiones(context)) }
    var horasDiarias by remember { mutableFloatStateOf(StudyEngine.cargarHorasDiarias(context)) }
    var rachaDias by remember { mutableIntStateOf(StudyEngine.obtenerRacha(context)) }

    var pestanaActual by remember { mutableIntStateOf(0) }
    var mostrandoNuevoExamen by remember { mutableStateOf(false) }

    var sesionParaPomodoro by remember { mutableStateOf<SesionEstudio?>(null) }
    var esModoDescanso by remember { mutableStateOf(false) }
    var minutosBaseEstudio by remember { mutableIntStateOf(25) }
    var segundosRestantes by remember { mutableIntStateOf(25 * 60) }
    var relojEnMarcha by remember { mutableStateOf(false) }

    val duracionTotalSegundos = if (esModoDescanso) {
        (sesionParaPomodoro?.minutosDescanso ?: StudyEngine.calcularMinutosDescanso(minutosBaseEstudio)) * 60
    } else {
        minutosBaseEstudio * 60
    }

    LaunchedEffect(relojEnMarcha, segundosRestantes) {
        if (relojEnMarcha && segundosRestantes > 0) {
            delay(1000L)
            segundosRestantes--
        } else if (relojEnMarcha && segundosRestantes == 0) {
            relojEnMarcha = false
            try {
                val uriSonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, uriSonido)
                ringtone?.play()
            } catch (_: Exception) { }

            Toast.makeText(
                context,
                if (esModoDescanso) "🔔 ¡Descanso terminado! Hora de volver al estudio."
                else "🔔 ¡Tiempo de enfoque completado!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (mostrandoNuevoExamen) {
        PantallaNuevoExamen(
            horasActuales = horasDiarias,
            onGuardarPlan = { nuevoExamen, nuevasHoras ->
                val nuevasSesiones = StudyEngine.generarPlanConIA(nuevoExamen, nuevasHoras)
                examenes = examenes + nuevoExamen
                sesiones = sesiones + nuevasSesiones
                horasDiarias = nuevasHoras

                StudyEngine.guardarDatos(context, examenes, sesiones, horasDiarias)
                mostrandoNuevoExamen = false
                pestanaActual = 1
                Toast.makeText(
                    context,
                    "✨ Plan creado: ${nuevasSesiones.size} sesiones (${StudyEngine.formatearMinutosAHoras(nuevasSesiones.sumOf { it.duracionMinutos + it.minutosDescanso })})",
                    Toast.LENGTH_LONG
                ).show()
            },
            onCancelar = { mostrandoNuevoExamen = false }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = pestanaActual == 0,
                        onClick = { pestanaActual = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                        label = { Text("Inicio") }
                    )
                    NavigationBarItem(
                        selected = pestanaActual == 1,
                        onClick = { pestanaActual = 1 },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendario") },
                        label = { Text("Calendario") }
                    )
                    NavigationBarItem(
                        selected = pestanaActual == 2,
                        onClick = { pestanaActual = 2 },
                        icon = { Icon(Icons.Default.Timer, contentDescription = "Enfoque") },
                        label = { Text("Enfoque") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                when (pestanaActual) {
                    0 -> PantallaInicio(
                        examenes = examenes,
                        sesiones = sesiones,
                        rachaDias = rachaDias,
                        horasDiarias = horasDiarias,
                        relojEnMarcha = relojEnMarcha,
                        segundosRestantesGlobal = segundosRestantes,
                        onIrANuevoExamen = { mostrandoNuevoExamen = true },
                        onEliminarExamen = { examenId ->
                            examenes = examenes.filterNot { it.id == examenId }
                            sesiones = sesiones.filterNot { it.examenId == examenId }
                            StudyEngine.guardarDatos(context, examenes, sesiones, horasDiarias)
                            Toast.makeText(context, "Examen eliminado", Toast.LENGTH_SHORT).show()
                        },
                        onIrACalendario = { pestanaActual = 1 },
                        onEstudiarSesion = { sesion ->
                            sesionParaPomodoro = sesion
                            esModoDescanso = false
                            minutosBaseEstudio = sesion.duracionMinutos
                            segundosRestantes = sesion.duracionMinutos * 60
                            relojEnMarcha = false
                            pestanaActual = 2
                        },
                        onIrAEnfoqueLibre = { pestanaActual = 2 }
                    )

                    1 -> PantallaCalendario(
                        examenes = examenes,
                        sesiones = sesiones,
                        onIniciarPomodoro = { sesion ->
                            sesionParaPomodoro = sesion
                            esModoDescanso = false
                            minutosBaseEstudio = sesion.duracionMinutos
                            segundosRestantes = sesion.duracionMinutos * 60
                            relojEnMarcha = false
                            pestanaActual = 2
                        },
                        onReajustarPorDiaPerdido = {
                            val sesionesReajustadas = StudyEngine.reajustarPorDiaPerdido(sesiones, examenes)
                            sesiones = sesionesReajustadas
                            StudyEngine.guardarDatos(context, examenes, sesiones, horasDiarias)

                            // Si ya tiene permiso de calendario, actualiza automáticamente los eventos del celular sin duplicar
                            val tienePermiso = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                            if (tienePermiso) {
                                examenes.forEach { ex ->
                                    StudyEngine.sincronizarPlanEnCalendarioNativo(context, ex, sesionesReajustadas.filter { it.examenId == ex.id })
                                }
                                Toast.makeText(context, "🔄 Plan reajustado y actualizado en tu Calendario (sin duplicados)", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "🔄 Sesiones movidas a mañana y tiempos comprimidos a tu meta diaria", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    2 -> PantallaPomodoro(
                        sesionActiva = sesionParaPomodoro,
                        esModoDescanso = esModoDescanso,
                        minutosBaseEstudio = minutosBaseEstudio,
                        duracionTotalSegundos = duracionTotalSegundos,
                        segundosRestantes = segundosRestantes,
                        enMarcha = relojEnMarcha,
                        onAlternarMarcha = { relojEnMarcha = !relojEnMarcha },
                        onReiniciarReloj = {
                            relojEnMarcha = false
                            segundosRestantes = duracionTotalSegundos
                        },
                        onCambiarModoDescanso = { descanso ->
                            relojEnMarcha = false
                            esModoDescanso = descanso
                            segundosRestantes = if (descanso) {
                                (sesionParaPomodoro?.minutosDescanso ?: StudyEngine.calcularMinutosDescanso(minutosBaseEstudio)) * 60
                            } else {
                                minutosBaseEstudio * 60
                            }
                        },
                        onCambiarMinutosLibres = { nuevosMinutos ->
                            relojEnMarcha = false
                            esModoDescanso = false
                            minutosBaseEstudio = nuevosMinutos
                            segundosRestantes = nuevosMinutos * 60
                        },
                        onSalirAModoLibre = {
                            relojEnMarcha = false
                            sesionParaPomodoro = null
                            esModoDescanso = false
                            minutosBaseEstudio = 25
                            segundosRestantes = 25 * 60
                        },
                        onCompletarSesion = { sesionId, nota ->
                            relojEnMarcha = false
                            sesiones = sesiones.map {
                                if (it.id == sesionId) it.copy(completada = true, notaEstudiante = nota) else it
                            }
                            rachaDias = StudyEngine.registrarRachaHoy(context)
                            StudyEngine.guardarDatos(context, examenes, sesiones, horasDiarias)
                            Toast.makeText(
                                context,
                                "🎉 ¡Tema aprobado y guardado en tu historial!",
                                Toast.LENGTH_SHORT
                            ).show()
                            sesionParaPomodoro = null
                            pestanaActual = 0
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "App Principal StudyAI")
@Composable
fun PreviewAppCompleta() {
    StudyAITheme {
        StudyAIApp()
    }
}