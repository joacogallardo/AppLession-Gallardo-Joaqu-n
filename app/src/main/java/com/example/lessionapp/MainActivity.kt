package com.example.lessionapp

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lessionapp.data.bluetooth.BleConnectionState
import com.example.lessionapp.data.bluetooth.BleDeviceInfo
import com.example.lessionapp.data.bluetooth.BleEmgManager
import com.example.lessionapp.data.bluetooth.RegisteredBleDevice
import com.example.lessionapp.ui.components.ProaButton
import com.example.lessionapp.ui.components.ProaCard
import com.example.lessionapp.ui.components.ProaEmgChart
import com.example.lessionapp.ui.components.ProaMetricCard
import com.example.lessionapp.ui.components.ProaOutlinedButton
import com.example.lessionapp.ui.components.ProaSecondaryButton
import com.example.lessionapp.ui.components.ProaStatusBadge
import com.example.lessionapp.ui.components.ProaStatusState
import com.example.lessionapp.ui.components.ProaTopAppBar
import com.example.lessionapp.ui.screens.HistoryScreen
import com.example.lessionapp.ui.theme.LessionappTheme
import com.example.lessionapp.ui.theme.ProaBluePrimary
import com.example.lessionapp.ui.viewmodel.DataSourceMode
import com.example.lessionapp.ui.viewmodel.EmgViewModel
import com.example.lessionapp.ui.viewmodel.EmgViewModelFactory
import kotlinx.coroutines.launch

private enum class AppSection(val label: String) {
    TRAINING("Entrenamiento"),
    BLUETOOTH("Bluetooth"),
    HISTORY("Historial")
}

class MainActivity : ComponentActivity() {
    companion object {
        private const val BLE_LOG_TAG = "LessionBLE"
    }

    private val emgViewModel: EmgViewModel by viewModels {
        EmgViewModelFactory((application as LessionApplication).repository, applicationContext)
    }
    private var pendingExportSessionId: Long? = null

    private val exportSessionLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val sessionId = pendingExportSessionId
        pendingExportSessionId = null
        if (uri != null && sessionId != null) {
            lifecycleScope.launch {
                try {
                    val csv = emgViewModel.exportSessionCsv(sessionId)
                    val output = contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("No se pudo abrir el archivo de destino.")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                    Toast.makeText(this@MainActivity, "Entrenamiento descargado como CSV.", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo descargar el entrenamiento: ${error.localizedMessage ?: "error de escritura"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isBluetoothEnabled()) {
            Log.i(BLE_LOG_TAG, "Bluetooth activado por el usuario.")
            emgViewModel.startBleScan()
        } else {
            Log.w(BLE_LOG_TAG, "El usuario no activó Bluetooth; no se inició el escaneo.")
            Toast.makeText(this, "Activá Bluetooth para buscar dispositivos.", Toast.LENGTH_SHORT).show()
        }
    }

    private val bluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            Log.i(BLE_LOG_TAG, "Permisos Bluetooth concedidos.")
            enableBluetoothOrScan()
        } else {
            Log.e(BLE_LOG_TAG, "Permisos Bluetooth denegados: $permissions")
            Toast.makeText(
                this,
                "Se necesitan permisos de Bluetooth para buscar el ESP32.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LessionappTheme {
                LessionApp(
                    viewModel = emgViewModel,
                    onDownloadSession = { sessionId ->
                        pendingExportSessionId = sessionId
                        exportSessionLauncher.launch("lession_entrenamiento_$sessionId.csv")
                    },
                    onRequestBlePermissions = {
                        requestBluetoothAccess()
                    }
                )
            }
        }
    }

    private fun requestBluetoothAccess() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missingPermissions = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            Log.i(BLE_LOG_TAG, "Solicitando permisos requeridos: $missingPermissions")
            bluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.i(BLE_LOG_TAG, "Permisos requeridos ya concedidos.")
            enableBluetoothOrScan()
        }
    }

    private fun enableBluetoothOrScan() {
        if (isBluetoothEnabled()) {
            emgViewModel.startBleScan()
        } else {
            enableBluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }
}

@Composable
private fun LessionApp(
    viewModel: EmgViewModel,
    onDownloadSession: (Long) -> Unit,
    onRequestBlePermissions: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(AppSection.TRAINING) }
    val title = when (section) {
        AppSection.TRAINING -> "Lession · Entrenamiento"
        AppSection.BLUETOOTH -> "Lession · Bluetooth"
        AppSection.HISTORY -> "Lession · Historial"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ProaTopAppBar(
                title = title,
                subtitle = "EMG V3 · seguimiento de actividad muscular"
            )
        },
        bottomBar = {
            NavigationBar {
                AppSection.values().forEach { destination ->
                    val icon = when (destination) {
                        AppSection.TRAINING -> Icons.Default.Dashboard
                        AppSection.BLUETOOTH -> Icons.Default.Bluetooth
                        AppSection.HISTORY -> Icons.Default.History
                    }
                    NavigationBarItem(
                        selected = section == destination,
                        onClick = { section = destination },
                        icon = { androidx.compose.material3.Icon(icon, contentDescription = null) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (section) {
            AppSection.TRAINING -> TrainingScreen(
                viewModel = viewModel,
                onOpenBluetooth = { section = AppSection.BLUETOOTH },
                modifier = Modifier.padding(innerPadding)
            )
            AppSection.BLUETOOTH -> BluetoothScreen(
                viewModel = viewModel,
                onScanRequest = onRequestBlePermissions,
                modifier = Modifier.padding(innerPadding)
            )
            AppSection.HISTORY -> HistoryScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
                onDownloadSession = onDownloadSession
            )
        }
    }
}

@Composable
private fun TrainingScreen(
    viewModel: EmgViewModel,
    onOpenBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val sensorData by viewModel.latestSensorData.collectAsState()
    val hasSensorData by viewModel.hasSensorData.collectAsState()
    val formattedDuration by viewModel.formattedDuration.collectAsState()
    val chartDataPoints by viewModel.chartDataPoints.collectAsState()
    val dataSourceMode by viewModel.dataSourceMode.collectAsState()
    val bleState by viewModel.bleConnectionState.collectAsState()
    val esp32Timestamp by viewModel.latestEsp32TimestampMillis.collectAsState()
    val isInReferenceRange = hasSensorData && sensorData.voltageMv in 100.0..3000.0
    val canStartTraining = !isRecording &&
        (dataSourceMode == DataSourceMode.SIMULATION ||
            (bleState == BleConnectionState.CONNECTED && hasSensorData))
    var faqExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val wideLayout = maxWidth >= 800.dp
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProaCard {
                    Text(
                        text = "Fuente de datos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ProaBluePrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            dataSourceMode == DataSourceMode.REAL_BLE && bleState == BleConnectionState.CONNECTED ->
                                "ESP32 conectado; recibiendo datos ADC reales por BLE."
                            dataSourceMode == DataSourceMode.REAL_BLE && bleState == BleConnectionState.CONNECTING ->
                                "Conectando al ESP32; esperando una muestra real. La simulación está desactivada."
                            dataSourceMode == DataSourceMode.REAL_BLE && bleState == BleConnectionState.ERROR ->
                                "Error de conexión BLE. No se generan datos simulados."
                            dataSourceMode == DataSourceMode.REAL_BLE ->
                                "Bluetooth desconectado. No se generan datos simulados."
                            else -> "Modo simulación seleccionado; los datos comienzan al iniciar el entrenamiento."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    ProaOutlinedButton(
                        text = "Administrar Bluetooth",
                        onClick = onOpenBluetooth,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (dataSourceMode == DataSourceMode.REAL_BLE) {
                        Spacer(Modifier.height(8.dp))
                        if (bleState == BleConnectionState.CONNECTING ||
                            bleState == BleConnectionState.CONNECTED
                        ) {
                            ProaSecondaryButton(
                                text = if (bleState == BleConnectionState.CONNECTING) {
                                    "Cancelar conexión"
                                } else {
                                    "Desconectar dispositivo"
                                },
                                onClick = { viewModel.disconnectBleDevice() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        ProaOutlinedButton(
                            text = "Cambiar a simulación",
                            onClick = { viewModel.setDataSourceMode(DataSourceMode.SIMULATION) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                ProaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Señal EMG recibida",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ProaBluePrimary
                        )
                        ProaStatusBadge(
                            statusState = when {
                                !hasSensorData -> ProaStatusState.DISCONNECTED
                                isInReferenceRange -> ProaStatusState.NORMAL
                                else -> ProaStatusState.WARNING
                            },
                            customText = when {
                                !hasSensorData -> "Sin muestra"
                                isInReferenceRange -> "En referencia"
                                else -> "Fuera de referencia"
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ProaEmgChart(dataPoints = chartDataPoints, maxValue = 3300.0)
                    Text(
                        text = if (dataSourceMode == DataSourceMode.REAL_BLE) {
                            "ADC real recibido (0–4095); el gráfico y los mV son conversiones del ADC usando 3,3 V como referencia."
                        } else {
                            "Señal simulada; el voltaje se deriva del ADC y no es una medición muscular."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (dataSourceMode == DataSourceMode.REAL_BLE) {
                                    "ADC recibido · 12 bits"
                                } else {
                                    "ADC simulado"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (hasSensorData) "${sensorData.rawAdc} / 4095" else "Esperando datos",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = ProaBluePrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (dataSourceMode == DataSourceMode.REAL_BLE) {
                                    "Tiempo ESP32"
                                } else {
                                    "Voltaje derivado"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (dataSourceMode == DataSourceMode.REAL_BLE) {
                                    esp32Timestamp?.let { "$it ms" } ?: "—"
                                } else if (hasSensorData) {
                                    "%.1f mV".format(sensorData.voltageMv)
                                } else {
                                    "—"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = ProaBluePrimary
                            )
                        }
                    }
                }

                ProaCard {
                    Text(
                        text = "Control de entrenamiento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ProaBluePrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    if (wideLayout) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProaButton(
                                "Iniciar entrenamiento",
                                { viewModel.startRecordingSession("Entrenamiento EMG V3") },
                                enabled = canStartTraining,
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.weight(1f)
                            )
                            ProaSecondaryButton(
                                "Detener entrenamiento",
                                { viewModel.stopRecordingSession() },
                                enabled = isRecording,
                                icon = Icons.Default.Stop,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProaButton(
                                "Iniciar entrenamiento",
                                { viewModel.startRecordingSession("Entrenamiento EMG V3") },
                                enabled = canStartTraining,
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.fillMaxWidth()
                            )
                            ProaSecondaryButton(
                                "Detener entrenamiento",
                                { viewModel.stopRecordingSession() },
                                enabled = isRecording,
                                icon = Icons.Default.Stop,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                ProaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preguntas frecuentes sobre EMG",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ProaBluePrimary
                        )
                        androidx.compose.material3.IconButton(
                            onClick = { faqExpanded = !faqExpanded }
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = if (faqExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (faqExpanded) "Contraer preguntas" else "Expandir preguntas"
                            )
                        }
                    }
                    if (faqExpanded) {
                        SignalFaqItem(
                            "¿Qué es el voltaje de origen muscular?",
                            "La ESP32 transmite el conteo ADC de la entrada conectada al sensor (0–4095). La app no recibe directamente el voltaje diferencial original de los electrodos."
                        )
                        SignalFaqItem(
                            "¿Qué significa diferencia de potencial?",
                            "Es la diferencia de voltaje entre los electrodos de medición colocados sobre el antebrazo. El sensor la amplifica para obtener una señal útil. La app no guarda por separado esa diferencia original."
                        )
                        SignalFaqItem(
                            "¿Qué es la frecuencia de los impulsos?",
                            "Describe cambios eléctricos por segundo (Hz). El flujo actual es de aproximadamente 50 muestras por segundo; no alcanza para caracterizar correctamente el contenido de frecuencia típico de una señal EMG."
                        )
                        SignalFaqItem(
                            "¿Qué indica la amplitud?",
                            "Es el tamaño de las variaciones eléctricas. La app grafica los valores ADC recibidos; no son una lectura directa de fuerza ni de la señal diferencial original del músculo."
                        )
                        SignalFaqItem(
                            "¿Qué son el ruido y el electrodo de referencia?",
                            "El ambiente puede agregar interferencias eléctricas. El electrodo de referencia ayuda al circuito a estabilizar la medición, pero no entrega por sí solo un valor de ruido. La app no puede cuantificarlo con los datos actuales."
                        )
                        Text(
                            text = "El protocolo recibido es ASCII: tiempo_ms,valor_ADC. El valor ADC es la lectura directa; la conversión a mV que muestra la app se calcula usando 3,3 V como referencia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalFaqItem(question: String, answer: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(answer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BluetoothScreen(
    viewModel: EmgViewModel,
    onScanRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.bleConnectionState.collectAsState()
    val devices by viewModel.scannedBleDevices.collectAsState()
    val registeredDevices by viewModel.registeredBleDevices.collectAsState()
    val selected by viewModel.selectedBleDevice.collectAsState()
    val sensorData by viewModel.latestSensorData.collectAsState()
    val hasSensorData by viewModel.hasSensorData.collectAsState()
    val connectionDetails by viewModel.bleConnectionDetails.collectAsState()
    var renameTarget by remember { mutableStateOf<RegisteredBleDevice?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<RegisteredBleDevice?>(null) }
    val devicesByAddress = devices.associateBy { it.address }
    val status = when (state) {
        BleConnectionState.CONNECTED -> "Conectado"
        BleConnectionState.CONNECTING -> "Conectando"
        BleConnectionState.SCANNING -> "Buscando dispositivos"
        BleConnectionState.ERROR -> "Error de conexión"
        BleConnectionState.DISCONNECTED -> "Desconectado"
    }
    val statusColor = when (state) {
        BleConnectionState.CONNECTED -> ProaStatusState.NORMAL
        BleConnectionState.CONNECTING, BleConnectionState.SCANNING -> ProaStatusState.WARNING
        BleConnectionState.ERROR -> ProaStatusState.ALERT
        BleConnectionState.DISCONNECTED -> ProaStatusState.DISCONNECTED
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        ProaCard {
            Text(
                text = "Administrador Bluetooth BLE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Escaneo BLE activo: aparecen los dispositivos cercanos. ESP32 LESSION se identifica por nombre completo y servicio ${BleEmgManager.EMG_SERVICE_UUID}.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            ProaStatusBadge(statusState = statusColor, customText = status)
            Spacer(Modifier.height(6.dp))
            Text(connectionDetails, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            if (selected != null) {
                Text(
                    text = "Seleccionado: ${selected?.name} · ${selected?.address}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
            }
            ProaButton(
                "Buscar dispositivos Bluetooth",
                onScanRequest,
                enabled = state != BleConnectionState.SCANNING &&
                    state != BleConnectionState.CONNECTING &&
                    state != BleConnectionState.CONNECTED,
                icon = Icons.Default.Bluetooth,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ProaCard {
            Text(
                text = "Dispositivos BLE encontrados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text(
                    text = if (state == BleConnectionState.SCANNING) {
                        "Buscando…"
                    } else {
                        "No hay resultados todavía. Iniciá una búsqueda para encontrar dispositivos BLE cercanos."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                devices.forEach { device ->
                    BleDeviceRow(
                        device = device,
                        isSelected = selected?.address == device.address,
                        isRegistered = registeredDevices.any { it.address == device.address },
                        onSelect = { viewModel.selectBleDevice(device) },
                        onRegister = { viewModel.registerBleDevice(device) },
                        onConnect = {
                            viewModel.selectBleDevice(device)
                            viewModel.connectBleDevice(device)
                        }
                    )
                }
            }
        }

        ProaCard {
            Text(
                text = "Dispositivos registrados en Lession",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            Spacer(Modifier.height(8.dp))
            if (registeredDevices.isEmpty()) {
                Text(
                    "Registrá un dispositivo encontrado para conservarlo en la aplicación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                registeredDevices.forEach { registered ->
                    val liveDevice = devicesByAddress[registered.address]
                    RegisteredBleDeviceRow(
                        device = registered,
                        rssi = liveDevice?.rssi,
                        isEmgCompatible = liveDevice?.isEsp32Device == true,
                        isSelected = selected?.address == registered.address,
                        onSelect = {
                            viewModel.selectBleDevice(
                                liveDevice ?: BleDeviceInfo(
                                    name = registered.customName,
                                    address = registered.address,
                                    rssi = 0,
                                    advertisesEmgService = false
                                )
                            )
                        },
                        onConnect = {
                            val device = liveDevice ?: BleDeviceInfo(
                                name = registered.customName,
                                address = registered.address,
                                rssi = 0
                            )
                            viewModel.selectBleDevice(device)
                            viewModel.connectBleDevice(device)
                        },
                        onRename = {
                            renameValue = registered.customName
                            renameTarget = registered
                        },
                        onRemove = { removeTarget = registered }
                    )
                }
            }
        }

        ProaCard {
            Text(
                text = "Conexión BLE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            if (state == BleConnectionState.CONNECTED && hasSensorData) {
                Text(
                    text = "ADC recibido: ${sensorData.rawAdc} / 4095",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProaBluePrimary
                )
            }
            if (state == BleConnectionState.CONNECTING ||
                state == BleConnectionState.CONNECTED
            ) {
                Spacer(Modifier.height(8.dp))
                ProaSecondaryButton(
                    if (state == BleConnectionState.CONNECTING) "Cancelar conexión" else "Desconectar",
                    { viewModel.disconnectBleDevice() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Cambiar nombre") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Nombre personalizado") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameRegisteredBleDevice(target.address, renameValue)
                    renameTarget = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            }
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Eliminar dispositivo") },
            text = { Text("¿Querés quitar ${target.customName} del registro de Lession? No se desemparejará del sistema Android.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeRegisteredBleDevice(target.address)
                    removeTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun BleDeviceRow(
    device: BleDeviceInfo,
    isSelected: Boolean,
    isRegistered: Boolean,
    onSelect: () -> Unit,
    onRegister: () -> Unit,
    onConnect: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "ID: ${device.address} · RSSI ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = if (device.isEsp32Device) {
                    "ESP32 LESSION · nombre y servicio confirmados"
                } else {
                    "Dispositivo BLE"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProaOutlinedButton("Seleccionar", onSelect, modifier = Modifier.weight(1f))
                ProaButton("Conectar", onConnect, modifier = Modifier.weight(1f))
            }
            if (!isRegistered) {
                ProaOutlinedButton("Registrar en Lession", onRegister, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RegisteredBleDeviceRow(
    device: RegisteredBleDevice,
    rssi: Int?,
    isEmgCompatible: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(device.customName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("ID: ${device.address}", style = MaterialTheme.typography.bodySmall)
            Text(
                "RSSI: ${rssi?.let { "$it dBm" } ?: "no disponible"} · ${if (isEmgCompatible) "Compatible con EMG" else "BLE general"}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProaOutlinedButton("Seleccionar", onSelect, modifier = Modifier.weight(1f))
                ProaButton("Conectar", onConnect, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProaOutlinedButton("Renombrar", onRename, modifier = Modifier.weight(1f))
                ProaOutlinedButton("Quitar registro", onRemove, modifier = Modifier.weight(1f))
            }
        }
    }
}
