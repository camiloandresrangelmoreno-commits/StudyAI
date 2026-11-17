package com.example.studyai

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val IndigoPrimario = Color(0xFF3949AB)
private val IndigoOscuro = Color(0xFF1A237E)
private val CianAcento = Color(0xFF00838F)
private val VerdeAprobado = Color(0xFF2E7D32)
private val FondoVerdeSuave = Color(0xFFE8F5E9)

fun compartirPorWhatsAppOGeneral(context: Context, texto: String) {
    val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    try {
        context.startActivity(whatsappIntent)
    } catch (e: Exception) {
        val generalIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        Toast.makeText(context, "Abriendo menú para compartir plan...", Toast.LENGTH_SHORT).show()
        context.startActivity(Intent.createChooser(generalIntent, "Compartir plan de estudio"))
    }
}

// ==========================================
// 1. PANTALLA INICIO + SINCRONIZACIÓN MASIVA + SIMULACRO NOTA 0.0-5.0
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(
    examenes: List<Examen>,
    sesiones: List<SesionEstudio>,
    rachaDias: Int,
    horasDiarias: Float,
    relojEnMarcha: Boolean,
    segundosRestantesGlobal: Int,
    onIrANuevoExamen: () -> Unit,
    onEliminarExamen: (String) -> Unit,
    onIrACalendario: () -> Unit,
    onEstudiarSesion: (SesionEstudio) -> Unit,
    onIrAEnfoqueLibre: () -> Unit
) {
    val context = LocalContext.current
    val ahora = LocalDateTime.now()
    val completadas = sesiones.count { it.completada }
    val totalSesiones = sesiones.size
    var examenSeleccionadoDetalle by remember { mutableStateOf<Examen?>(null) }
    var examenParaSimulacro by remember { mutableStateOf<Examen?>(null) }

    var pendienteSincronizarExamen by remember { mutableStateOf<Examen?>(null) }
    val permisoCalendarioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val concedido = permisos[Manifest.permission.WRITE_CALENDAR] == true &&
                permisos[Manifest.permission.READ_CALENDAR] == true
        val ex = pendienteSincronizarExamen
        if (concedido && ex != null) {
            val delExamen = sesiones.filter { it.examenId == ex.id }
            val total = StudyEngine.sincronizarPlanEnCalendarioNativo(context, ex, delExamen)
            Toast.makeText(context, "✅ $total sesiones sincronizadas en tu Calendario (sin duplicados)", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Activa el permiso de Calendario para sincronizar todo el plan", Toast.LENGTH_LONG).show()
        }
    }

    examenParaSimulacro?.let { examen ->
        val preguntasSimulacro = remember(examen.id) {
            sesiones.filter { it.examenId == examen.id }.flatMap { it.quizValidacion }.take(5)
        }
        val respuestas = remember(examen.id) { mutableStateMapOf<Int, Int>() }
        var notaFinalCalculada by remember { mutableStateOf<Float?>(null) }

        AlertDialog(
            onDismissRequest = { examenParaSimulacro = null },
            title = {
                Text("🎓 Simulacro Parcial: ${examen.materia}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (notaFinalCalculada != null) {
                        val aprobada = notaFinalCalculada!! >= 3.0f
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (aprobada) FondoVerdeSuave else Color(0xFFFFEBEE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Nota obtenida: ${String.format(Locale.getDefault(), "%.1f", notaFinalCalculada)} / 5.0 — ${if (aprobada) "¡APROBADO!" else "REFORZAR TEMAS"}",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (aprobada) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    preguntasSimulacro.forEachIndexed { idx, preg ->
                        Text("${idx + 1}. ${preg.pregunta}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        preg.opciones.forEachIndexed { optIdx, opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        respuestas[idx] = optIdx
                                        notaFinalCalculada = null
                                    }
                            ) {
                                RadioButton(
                                    selected = respuestas[idx] == optIdx,
                                    onClick = {
                                        respuestas[idx] = optIdx
                                        notaFinalCalculada = null
                                    }
                                )
                                Text(opt, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (preguntasSimulacro.isEmpty()) return@Button
                    val correctas = preguntasSimulacro.mapIndexed { idx, p ->
                        if (respuestas[idx] == p.respuestaCorrectaIndex) 1 else 0
                    }.sum()
                    notaFinalCalculada = (correctas.toFloat() / preguntasSimulacro.size) * 5.0f
                }) {
                    Text("Calcular Nota (0.0 - 5.0)")
                }
            },
            dismissButton = {
                TextButton(onClick = { examenParaSimulacro = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    examenSeleccionadoDetalle?.let { examen ->
        val sesionesDelExamen = sesiones.filter { it.examenId == examen.id }
        val totalConDescanso = sesionesDelExamen.sumOf { it.duracionMinutos + it.minutosDescanso }
        val vencido = examen.estaVencido(ahora)

        AlertDialog(
            onDismissRequest = { examenSeleccionadoDetalle = null },
            title = {
                Column {
                    Text(examen.materia, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(
                        if (vencido) "🔒 Evaluación cerrada (${examen.fechaLimite} ${examen.horaLimiteSegura()})"
                        else "Total: ${StudyEngine.formatearMinutosAHoras(totalConDescanso)} • Cierra: ${examen.fechaLimite} ${examen.horaLimiteSegura()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vencido) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                val resumen = StudyEngine.construirResumenExportable(examen, sesionesDelExamen)
                                compartirPorWhatsAppOGeneral(context, resumen)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp", fontSize = 11.sp)
                        }

                        if (!vencido) {
                            FilledTonalButton(
                                onClick = {
                                    val tienePermiso = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                    if (tienePermiso) {
                                        val total = StudyEngine.sincronizarPlanEnCalendarioNativo(context, examen, sesionesDelExamen)
                                        Toast.makeText(context, "✅ $total sesiones guardadas en tu Calendario (sin duplicados)", Toast.LENGTH_LONG).show()
                                    } else {
                                        pendienteSincronizarExamen = examen
                                        permisoCalendarioLauncher.launch(
                                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Default.EventRepeat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Calendario", fontSize = 11.sp)
                            }
                        }
                    }
                    if (!vencido) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                examenSeleccionadoDetalle = null
                                examenParaSimulacro = examen
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Presentar Simulacro General (Nota 0.0 - 5.0)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 330.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sesionesDelExamen.forEach { s ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    s.completada -> FondoVerdeSuave
                                    vencido -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        s.temaEspecifico,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (s.completada) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${s.fecha} • ${s.duracionMinutos}m est. + ${s.minutosDescanso}m desc.",
                                        fontSize = 11.sp,
                                        color = if (s.completada) VerdeAprobado else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (vencido && !s.completada) {
                                    Text("Cerrado", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                } else {
                                    FilledTonalButton(
                                        onClick = {
                                            examenSeleccionadoDetalle = null
                                            onEstudiarSesion(s)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(if (s.completada) "Repasar" else "Estudiar", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { examenSeleccionadoDetalle = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(IndigoPrimario, CianAcento))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("StudyAI", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                            Text(
                                "Hoy: ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEE dd MMM", Locale("es", "ES")))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFFCC80)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = "Racha",
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$rachaDias días",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onIrANuevoExamen,
                containerColor = IndigoPrimario,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo Plan IA", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp)
        ) {
            if (relojEnMarcha) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA)),
                        border = BorderStroke(1.dp, CianAcento),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIrAEnfoqueLibre() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = CianAcento)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Temporizador activo en segundo plano",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF006064),
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                String.format(Locale.getDefault(), "%02d:%02d", segundosRestantesGlobal / 60, segundosRestantesGlobal % 60),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF006064),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.horizontalGradient(listOf(IndigoOscuro, IndigoPrimario, CianAcento)))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Rendimiento Académico",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "$completadas de $totalSesiones temas certificados",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            FilledTonalButton(
                                onClick = onIrACalendario,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Calendario", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        val progresoGlobal = if (totalSesiones > 0) completadas.toFloat() / totalSesiones else 0f
                        LinearProgressIndicator(
                            progress = { progresoGlobal },
                            color = Color(0xFF80DEEA),
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Meta diaria: ${String.format(Locale.getDefault(), "%.1f", horasDiarias)} h (incl. descansos)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                "${(progresoGlobal * 100).toInt()}% completado",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Evaluaciones Programadas (${examenes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (examenes.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(28.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Sin evaluaciones registradas aún",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Presiona '+ Nuevo Plan IA' para ingresar tus materias y hora exacta del parcial.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(examenes, key = { it.id }) { examen ->
                    val sesionesExamen = sesiones.filter { it.examenId == examen.id }
                    val hechas = sesionesExamen.count { it.completada }
                    val porcentaje = if (sesionesExamen.isNotEmpty()) (hechas * 100) / sesionesExamen.size else 0
                    val minutosTotales = sesionesExamen.sumOf { it.duracionMinutos + it.minutosDescanso }
                    val vencido = examen.estaVencido(ahora)
                    val horasRestantes = ChronoUnit.HOURS.between(ahora, examen.fechaHoraLimite())

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (vencido) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (vencido) 0.dp else 3.dp),
                        border = BorderStroke(
                            1.dp,
                            if (vencido) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { examenSeleccionadoDetalle = examen }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        examen.materia,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (vencido) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${sesionesExamen.size} bloques (${StudyEngine.formatearMinutosAHoras(minutosTotales)}) • Toca para gestionar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (vencido) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(onClick = { onEliminarExamen(examen.id) }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Eliminar",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AssistChip(
                                    onClick = { examenSeleccionadoDetalle = examen },
                                    label = {
                                        Text(
                                            when {
                                                vencido -> "🔒 Finalizado (${examen.fechaLimite} ${examen.horaLimiteSegura()})"
                                                horasRestantes < 24 -> "⏳ Cierra en ${horasRestantes.coerceAtLeast(1)}h (${examen.horaLimiteSegura()})"
                                                else -> "📅 ${examen.fechaLimite} • ${examen.horaLimiteSegura()}"
                                            },
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                                Text(
                                    "$porcentaje% listo",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        porcentaje == 100 -> VerdeAprobado
                                        vencido -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { porcentaje / 100f },
                                color = if (porcentaje == 100) VerdeAprobado else if (vencido) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. PANTALLA CREAR EXAMEN CON FECHA Y HORA EXACTA DE CIERRE
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaNuevoExamen(
    horasActuales: Float,
    onGuardarPlan: (Examen, Float) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    var materia by remember { mutableStateOf("") }
    var tema by remember { mutableStateOf("") }
    var nivelSeleccionado by remember { mutableStateOf(NivelDominio.MEDIO) }
    var horasDia by remember { mutableFloatStateOf(horasActuales) }
    var fechaSeleccionada by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var horaSeleccionada by remember { mutableStateOf(LocalTime.of(14, 0)) }
    var mostrarDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = fechaSeleccionada
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    )

    if (mostrarDatePicker) {
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        fechaSeleccionada = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                    }
                    mostrarDatePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Plan de Estudio IA", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancelar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = materia,
                    onValueChange = { materia = it },
                    label = { Text("Nombre de la materia") },
                    placeholder = { Text("Ej. Bases de Datos, Programación Web") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = tema,
                    onValueChange = { tema = it },
                    label = { Text("Temas a estudiar (sepáralos por comas)") },
                    placeholder = { Text("Ej. HTML, CSS, JavaScript, APIs REST") },
                    supportingText = {
                        Text("Se repartirán entre hoy y el día del parcial antes de la hora límite.")
                    },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Fecha y hora exacta del parcial / entrega", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { mostrarDatePicker = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                fechaSeleccionada.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("es", "ES"))),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    horaSeleccionada = LocalTime.of(hourOfDay, minute)
                                },
                                horaSeleccionada.hour,
                                horaSeleccionada.minute,
                                true
                            ).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                horaSeleccionada.format(DateTimeFormatter.ofPattern("HH:mm")),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            item {
                Text("Nivel de conocimiento actual", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NivelDominio.entries.forEach { nivel ->
                        FilterChip(
                            selected = nivelSeleccionado == nivel,
                            onClick = { nivelSeleccionado = nivel },
                            label = { Text(nivel.etiqueta) },
                            leadingIcon = if (nivelSeleccionado == nivel) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Horas libres al día (estudio + descanso)",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.1f", horasDia)} h/día",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Slider(
                    value = horasDia,
                    onValueChange = { horasDia = it },
                    valueRange = 0.5f..6.0f,
                    steps = 10
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val fechaHoraElegida = LocalDateTime.of(fechaSeleccionada, horaSeleccionada)
                        when {
                            materia.isBlank() || tema.isBlank() ->
                                Toast.makeText(context, "Escribe la materia y los temas", Toast.LENGTH_SHORT).show()
                            fechaHoraElegida.isBefore(LocalDateTime.now()) ->
                                Toast.makeText(context, "La hora del examen ya pasó; elige una hora futura", Toast.LENGTH_LONG).show()
                            else -> {
                                val nuevoExamen = Examen(
                                    materia = materia.trim(),
                                    tema = tema.trim(),
                                    fechaLimite = fechaSeleccionada.toString(),
                                    horaLimite = horaSeleccionada.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    nivel = nivelSeleccionado,
                                    horasDiariasAsignadas = horasDia
                                )
                                onGuardarPlan(nuevoExamen, horasDia)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimario),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generar Cronograma Exacto", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ==========================================
// 3. PANTALLA CALENDARIO CON REAJUSTE POR DÍA PERDIDO ("NO ALCANCÉ HOY")
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCalendario(
    examenes: List<Examen>,
    sesiones: List<SesionEstudio>,
    onIniciarPomodoro: (SesionEstudio) -> Unit,
    onReajustarPorDiaPerdido: () -> Unit
) {
    val context = LocalContext.current
    val hoy = LocalDate.now()
    val ahora = LocalDateTime.now()
    var fechaElegida by remember { mutableStateOf(hoy) }

    val diasCalendario = remember(hoy) {
        (0..30).map { hoy.plusDays(it.toLong()) }
    }

    val sesionesDelDia = sesiones.filter { it.fecha == fechaElegida.toString() }
    val examenesQueCierranHoy = examenes.filter { it.fechaLimite == fechaElegida.toString() }
    val minutosTotalesDia = sesionesDelDia.sumOf { it.duracionMinutos + it.minutosDescanso }

    val hayPendientesReajustables = sesiones.any { s ->
        val ex = examenes.find { it.id == s.examenId }
        !s.completada &&
                ex != null &&
                !ex.estaVencido(ahora) &&
                ex.fechaHoraLimite().toLocalDate().isAfter(hoy) &&
                !LocalDate.parse(s.fecha).isAfter(hoy)
    }

    val nombreMesActual = fechaElegida.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES")))
        .replaceFirstChar { it.uppercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Calendario • $nombreMesActual", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (sesionesDelDia.isNotEmpty()) {
                            Text(
                                "Tiempo hoy: ${StudyEngine.formatearMinutosAHoras(minutosTotalesDia)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    if (hayPendientesReajustables) {
                        FilledTonalButton(
                            onClick = onReajustarPorDiaPerdido,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("No alcancé hoy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(diasCalendario) { dia ->
                    val esSeleccionado = dia == fechaElegida
                    val tieneSesiones = sesiones.any { it.fecha == dia.toString() }
                    val esDiaEntrega = examenes.any { it.fechaLimite == dia.toString() }
                    val nombreDia = dia.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es", "ES")).uppercase()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                esSeleccionado -> IndigoPrimario
                                esDiaEntrega -> Color(0xFFFFEBEE)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        ),
                        border = if (esDiaEntrega && !esSeleccionado) BorderStroke(1.5.dp, Color(0xFFE53935)) else null,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { fechaElegida = dia }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (dia == hoy) "HOY" else nombreDia,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    esSeleccionado -> Color.White
                                    esDiaEntrega -> Color(0xFFC62828)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dia.dayOfMonth.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    esSeleccionado -> Color.White
                                    esDiaEntrega -> Color(0xFFB71C1C)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (tieneSesiones) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (esSeleccionado) Color.White else MaterialTheme.colorScheme.primary)
                                    )
                                }
                                if (esDiaEntrega) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (esSeleccionado) Color(0xFFFFCDD2) else Color(0xFFD32F2F))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (examenesQueCierranHoy.isNotEmpty()) {
                examenesQueCierranHoy.forEach { ex ->
                    val vencido = ex.estaVencido(ahora)
                    Surface(
                        color = if (vencido) Color(0xFFEEEEEE) else Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, if (vencido) Color(0xFFBDBDBD) else Color(0xFFEF9A9A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (vencido) Icons.Default.Lock else Icons.Default.Flag,
                                contentDescription = null,
                                tint = if (vencido) Color(0xFF616161) else Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (vencido)
                                    "🔒 Evaluación finalizada: ${ex.materia} (Cerró a las ${ex.horaLimiteSegura()})"
                                else
                                    "📌 Cierre / Parcial de ${ex.materia} a las ${ex.horaLimiteSegura()}",
                                fontWeight = FontWeight.Bold,
                                color = if (vencido) Color(0xFF424242) else Color(0xFFB71C1C),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (sesionesDelDia.isEmpty() && examenesQueCierranHoy.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Día libre de sesiones programadas",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sesionesDelDia, key = { it.id }) { sesion ->
                        val examenPadre = examenes.find { it.id == sesion.examenId }
                        val sesionVencida = examenPadre?.estaVencido(ahora) == true && !sesion.completada

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    sesion.completada -> FondoVerdeSuave
                                    sesionVencida -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    sesion.completada -> VerdeAprobado.copy(alpha = 0.4f)
                                    sesionVencida -> MaterialTheme.colorScheme.outlineVariant
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (sesionVencida) 0.dp else 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                "${sesion.materia} • ${sesion.duracionMinutos}m est. + ${sesion.minutosDescanso}m desc.",
                                                fontSize = 11.sp
                                            )
                                        }
                                    )
                                    when {
                                        sesion.completada -> Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Verified, contentDescription = null, tint = VerdeAprobado, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Validada", color = VerdeAprobado, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        sesionVencida -> Text("🔒 Vencida", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        else -> Text("Pendiente", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    sesion.tituloSesion,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (sesion.completada) Color(0xFF1B5E20) else if (sesionVencida) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    sesion.guiaEstudioIA,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sesion.completada) VerdeAprobado else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { onIniciarPomodoro(sesion) },
                                        enabled = !sesionVencida,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (sesion.completada) VerdeAprobado else IndigoPrimario
                                        ),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            if (sesionVencida) Icons.Default.Lock else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            when {
                                                sesionVencida -> "Parcial Finalizado"
                                                sesion.completada -> "Repasar (Tiempo Libre)"
                                                else -> "Iniciar Sesión"
                                            },
                                            color = Color.White
                                        )
                                    }
                                    if (!sesionVencida) {
                                        OutlinedButton(
                                            onClick = { exportarAlCalendarioDelCelular(context, sesion) },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Event, contentDescription = "Guardar en Google Calendar", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun exportarAlCalendarioDelCelular(context: Context, sesion: SesionEstudio) {
    try {
        val fecha = LocalDate.parse(sesion.fecha)
        val inicioMillis = fecha.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val finMillis = inicioMillis + (sesion.duracionMinutos * 60 * 1000L)

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "StudyAI: ${sesion.materia} - ${sesion.temaEspecifico}")
            putExtra(CalendarContract.Events.DESCRIPTION, sesion.guiaEstudioIA)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, inicioMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, finMillis)
        }
        Toast.makeText(context, "Toca 'Guardar' en tu calendario para confirmar", Toast.LENGTH_LONG).show()
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No se encontró una app de calendario", Toast.LENGTH_SHORT).show()
    }
}

// ==========================================
// 4. PANTALLA POMODORO PROFESIONAL + QUIZ CON PISTA
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPomodoro(
    sesionActiva: SesionEstudio?,
    esModoDescanso: Boolean,
    minutosBaseEstudio: Int,
    duracionTotalSegundos: Int,
    segundosRestantes: Int,
    enMarcha: Boolean,
    onAlternarMarcha: () -> Unit,
    onReiniciarReloj: () -> Unit,
    onCambiarModoDescanso: (Boolean) -> Unit,
    onCambiarMinutosLibres: (Int) -> Unit,
    onSalirAModoLibre: () -> Unit,
    onCompletarSesion: (String, String) -> Unit
) {
    val context = LocalContext.current
    val puedeElegirTiempo = sesionActiva == null || sesionActiva.completada
    val minutosDescansoCalculados = sesionActiva?.minutosDescanso ?: StudyEngine.calcularMinutosDescanso(minutosBaseEstudio)

    var apunteEstudiante by remember(sesionActiva?.id) { mutableStateOf(sesionActiva?.notaEstudiante ?: "") }
    var mostrarQuizDialog by remember { mutableStateOf(false) }
    val respuestasSeleccionadas = remember(sesionActiva?.id) { mutableStateMapOf<Int, Int>() }
    var quizEvaluado by remember { mutableStateOf(false) }
    var quizAprobado by remember { mutableStateOf(false) }

    val segundosTranscurridos = (duracionTotalSegundos - segundosRestantes).coerceAtLeast(0)
    val mitadTiempoCumplida = !esModoDescanso && segundosTranscurridos >= (duracionTotalSegundos / 2)

    if (mostrarQuizDialog && sesionActiva != null) {
        AlertDialog(
            onDismissRequest = {
                mostrarQuizDialog = false
                quizEvaluado = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Evaluación: ${sesionActiva.temaEspecifico}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (quizEvaluado) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (quizAprobado) FondoVerdeSuave else Color(0xFFFFEBEE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (quizAprobado)
                                    "🎉 ¡Excelente! Aprobaste la evaluación. Ya puedes guardar tu avance."
                                else
                                    "⚠️ Revisa las tarjetas en rojo: lee la pista para encontrar la respuesta correcta.",
                                color = if (quizAprobado) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    sesionActiva.quizValidacion.forEachIndexed { qIndex, pregunta ->
                        val elegida = respuestasSeleccionadas[qIndex]
                        val esCorrecta = elegida == pregunta.respuestaCorrectaIndex

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    !quizEvaluado -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    esCorrecta -> FondoVerdeSuave
                                    else -> Color(0xFFFFEBEE)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "${qIndex + 1}. ${pregunta.pregunta}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                pregunta.opciones.forEachIndexed { optIndex, opcion ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                respuestasSeleccionadas[qIndex] = optIndex
                                                quizEvaluado = false
                                                quizAprobado = false
                                            }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        RadioButton(
                                            selected = elegida == optIndex,
                                            onClick = {
                                                respuestasSeleccionadas[qIndex] = optIndex
                                                quizEvaluado = false
                                                quizAprobado = false
                                            }
                                        )
                                        Text(opcion, fontSize = 13.sp)
                                    }
                                }

                                if (quizEvaluado) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (esCorrecta) "✓ Correcto: ${pregunta.explicacion}"
                                        else "✗ Pista: ${pregunta.pistaError}",
                                        color = if (esCorrecta) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (quizAprobado) {
                    Button(
                        onClick = {
                            mostrarQuizDialog = false
                            quizEvaluado = false
                            quizAprobado = false
                            onCompletarSesion(sesionActiva.id, apunteEstudiante.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VerdeAprobado)
                    ) {
                        Text("Continuar y Guardar", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            val todasRespondidas = sesionActiva.quizValidacion.indices.all { respuestasSeleccionadas.containsKey(it) }
                            if (!todasRespondidas) {
                                Toast.makeText(context, "Responde todas las preguntas primero", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            quizEvaluado = true
                            quizAprobado = sesionActiva.quizValidacion.mapIndexed { idx, p ->
                                respuestasSeleccionadas[idx] == p.respuestaCorrectaIndex
                            }.all { it }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimario)
                    ) {
                        Text("Calificar Respuestas", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    mostrarQuizDialog = false
                    quizEvaluado = false
                }) {
                    Text("Cerrar")
                }
            }
        )
    }

    val progresoAnimado by animateFloatAsState(
        targetValue = if (duracionTotalSegundos > 0) segundosRestantes.toFloat() / duracionTotalSegundos.toFloat() else 1f,
        label = "progreso_pomodoro"
    )

    val minutos = segundosRestantes / 60
    val segundos = segundosRestantes % 60
    val textoReloj = String.format(Locale.getDefault(), "%02d:%02d", minutos, segundos)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (sesionActiva == null) "Pomodoro Libre" else "Sesión de Enfoque",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (sesionActiva == null) "Concentración independiente sin tareas"
                            else "${sesionActiva.materia} • ${sesionActiva.temaEspecifico}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (sesionActiva != null) {
                        TextButton(onClick = onSalirAModoLibre) {
                            Text("Modo Libre", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = !esModoDescanso,
                    onClick = { onCambiarModoDescanso(false) },
                    label = { Text("Estudio ($minutosBaseEstudio min)", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = esModoDescanso,
                    onClick = { onCambiarModoDescanso(true) },
                    label = { Text("Descanso ($minutosDescansoCalculados min)", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (puedeElegirTiempo && !esModoDescanso) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = minutosBaseEstudio == mins,
                            onClick = { onCambiarMinutosLibres(mins) },
                            label = { Text("${mins}m", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .padding(8.dp)
            ) {
                val colorActivo = when {
                    esModoDescanso -> CianAcento
                    mitadTiempoCumplida -> VerdeAprobado
                    else -> MaterialTheme.colorScheme.primary
                }
                val colorPista = colorActivo.copy(alpha = 0.15f)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = colorActivo.copy(alpha = 0.06f),
                        radius = size.minDimension / 2f
                    )
                    drawArc(
                        color = colorPista,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = colorActivo,
                        startAngle = -90f,
                        sweepAngle = 360f * progresoAnimado,
                        useCenter = false,
                        style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (esModoDescanso) "DESCANSO" else "ENFOQUE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorActivo
                    )
                    Text(
                        text = textoReloj,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = when {
                            esModoDescanso -> "Recupera energía"
                            sesionActiva == null -> "Sesión libre activa"
                            mitadTiempoCumplida -> "✓ Validación habilitada"
                            else -> "Tiempo fijo IA • Meta 50%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (mitadTiempoCumplida && !esModoDescanso) VerdeAprobado else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAlternarMarcha,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (esModoDescanso) CianAcento else IndigoPrimario
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Icon(if (enMarcha) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (enMarcha) "Pausar" else if (esModoDescanso) "Iniciar Descanso" else "Iniciar Estudio",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                OutlinedButton(
                    onClick = onReiniciarReloj,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reiniciar")
                }
            }

            if (sesionActiva != null) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = sesionActiva.tituloSesion,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = sesionActiva.guiaEstudioIA,
                            style = MaterialTheme.typography.bodySmall
                        )
                        HorizontalDivider()
                        Text(
                            "Síntesis técnica aprendida (se evalúa calidad):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = apunteEstudiante,
                            onValueChange = { apunteEstudiante = it },
                            placeholder = { Text("Resume lo aprendido sobre ${sesionActiva.temaEspecifico}...") },
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val (apunteValido, mensajeApunte) = StudyEngine.validarCalidadApunte(apunteEstudiante, sesionActiva.temaEspecifico)
                val puedeValidar = (mitadTiempoCumplida || sesionActiva.completada) && apunteValido

                Button(
                    onClick = {
                        if (!apunteValido) {
                            Toast.makeText(context, mensajeApunte, Toast.LENGTH_LONG).show()
                        } else {
                            mostrarQuizDialog = true
                        }
                    },
                    enabled = (mitadTiempoCumplida || sesionActiva.completada),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeAprobado),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        if (puedeValidar) Icons.Default.Verified else Icons.Default.Lock,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            !mitadTiempoCumplida && !sesionActiva.completada -> "Cumple al menos el 50% del tiempo"
                            !apunteValido -> mensajeApunte
                            else -> "Presentar Evaluación IA"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "1. Vista Inicio")
@Composable
fun PreviewPantallaInicio() {
    val examenDemo = Examen(
        materia = "Arquitectura de Software",
        tema = "Patrón MVC, Microservicios, APIs REST",
        fechaLimite = LocalDate.now().plusDays(2).toString(),
        horaLimite = "14:00",
        nivel = NivelDominio.MEDIO
    )
    val sesionesDemo = StudyEngine.generarPlanConIA(examenDemo, 2.0f)
    PantallaInicio(
        examenes = listOf(examenDemo),
        sesiones = sesionesDemo,
        rachaDias = 5,
        horasDiarias = 2.0f,
        relojEnMarcha = false,
        segundosRestantesGlobal = 1500,
        onIrANuevoExamen = {},
        onEliminarExamen = {},
        onIrACalendario = {},
        onEstudiarSesion = {},
        onIrAEnfoqueLibre = {}
    )
}