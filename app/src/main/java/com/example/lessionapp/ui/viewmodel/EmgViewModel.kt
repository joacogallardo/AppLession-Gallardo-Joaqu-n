package com.example.lessionapp.ui.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lessionapp.data.bluetooth.BleConnectionState
import com.example.lessionapp.data.bluetooth.BleDeviceInfo
import com.example.lessionapp.data.bluetooth.BleEmgManager
import com.example.lessionapp.data.bluetooth.RegisteredBleDevice
import com.example.lessionapp.data.local.entity.EmgRecordEntity
import com.example.lessionapp.data.local.entity.EmgSessionEntity
import com.example.lessionapp.data.repository.EmgRepository
import com.example.lessionapp.data.sensor.AlertsManager
import com.example.lessionapp.data.sensor.CalibrationManager
import com.example.lessionapp.data.sensor.EmgAlertConfig
import com.example.lessionapp.data.sensor.EmgCalibrationData
import com.example.lessionapp.data.sensor.EmgSensorData
import com.example.lessionapp.data.sensor.EmgSignalProcessor
import com.example.lessionapp.data.sensor.EmgSimulationGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import android.util.Log
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class DataSourceMode {
    SIMULATION,
    REAL_BLE
}

/**
 * ViewModel que gestiona procesamiento, gráficos, alertas, Bluetooth, tiempo y calibración (HU-02).
 */
class EmgViewModel(
    private val repository: EmgRepository,
    context: Context
) : ViewModel() {

    private val signalProcessor = EmgSignalProcessor()
    private val calibrationManager = CalibrationManager(context)
    private val alertsManager = AlertsManager(context)
    private val simulationGenerator = EmgSimulationGenerator()
    val bleManager = BleEmgManager(context)
    private val bluetoothPreferences =
        context.getSharedPreferences("registered_ble_devices", Context.MODE_PRIVATE)

    private val _dataSourceMode = MutableStateFlow(DataSourceMode.SIMULATION)
    val dataSourceMode: StateFlow<DataSourceMode> = _dataSourceMode.asStateFlow()

    val bleConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val scannedBleDevices: StateFlow<List<BleDeviceInfo>> = bleManager.scannedDevices

    private val _registeredBleDevices = MutableStateFlow(loadRegisteredBleDevices())
    val registeredBleDevices: StateFlow<List<RegisteredBleDevice>> = _registeredBleDevices.asStateFlow()

    private val _selectedBleDevice = MutableStateFlow<BleDeviceInfo?>(null)
    val selectedBleDevice: StateFlow<BleDeviceInfo?> = _selectedBleDevice.asStateFlow()
    val bleConnectionDetails: StateFlow<String> = bleManager.connectionDetails

    private val _calibrationData = MutableStateFlow(calibrationManager.loadCalibration())
    val calibrationData: StateFlow<EmgCalibrationData> = _calibrationData.asStateFlow()

    // --- ESTADO Y CONFIGURACIÓN DE ALERTAS (HU-02) ---
    private val _alertConfig = MutableStateFlow(alertsManager.loadConfig())
    val alertConfig: StateFlow<EmgAlertConfig> = _alertConfig.asStateFlow()

    private val _isAlertActive = MutableStateFlow(false)
    val isAlertActive: StateFlow<Boolean> = _isAlertActive.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _latestSensorData = MutableStateFlow(EmgSensorData(0, 0.0, 0.0, 0.0))
    val latestSensorData: StateFlow<EmgSensorData> = _latestSensorData.asStateFlow()

    private val _hasSensorData = MutableStateFlow(false)
    val hasSensorData: StateFlow<Boolean> = _hasSensorData.asStateFlow()

    private val _chartDataPoints = MutableStateFlow<List<Double>>(emptyList())
    val chartDataPoints: StateFlow<List<Double>> = _chartDataPoints.asStateFlow()

    private val _recordingDurationMillis = MutableStateFlow(0L)
    val recordingDurationMillis: StateFlow<Long> = _recordingDurationMillis.asStateFlow()

    private val _formattedDuration = MutableStateFlow("00:00:00")
    val formattedDuration: StateFlow<String> = _formattedDuration.asStateFlow()

    val allSessions: StateFlow<List<EmgSessionEntity>> = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    private val _selectedSessionRecords = MutableStateFlow<List<EmgRecordEntity>>(emptyList())
    val selectedSessionRecords: StateFlow<List<EmgRecordEntity>> = _selectedSessionRecords.asStateFlow()

    private var timerJob: Job? = null
    private var simulationJob: Job? = null
    private var selectedRecordsJob: Job? = null

    private var sampleCount = 0
    private var totalVoltageSum = 0.0
    private var maxVoltage = 0.0
    private var minVoltage = Double.MAX_VALUE
    private var sessionStartTime = 0L

    init {
        viewModelScope.launch {
            bleManager.incomingEmgFlow.collect { sample ->
                if (_dataSourceMode.value == DataSourceMode.REAL_BLE &&
                    bleManager.connectionState.value == BleConnectionState.CONNECTED
                ) {
                    processAndRecordEmgData(
                        rawAdc = sample.adcRaw,
                        measuredOutputMv = sample.outputMillivolts
                    )
                }
            }
        }
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    BleConnectionState.CONNECTED -> {
                        if (bleManager.isEmgCompatible.value) {
                            setDataSourceMode(DataSourceMode.REAL_BLE)
                        }
                    }
                    BleConnectionState.DISCONNECTED, BleConnectionState.ERROR -> {
                        if (_dataSourceMode.value == DataSourceMode.REAL_BLE) {
                            stopRecordingSession()
                            clearLiveData()
                        }
                    }
                    BleConnectionState.SCANNING, BleConnectionState.CONNECTING -> Unit
                }
            }
        }
    }

    fun setDataSourceMode(mode: DataSourceMode) {
        if (_dataSourceMode.value == mode && mode == DataSourceMode.SIMULATION) return
        _dataSourceMode.value = mode
        stopRecordingSession()
        clearLiveData()
        if (mode == DataSourceMode.REAL_BLE) {
            simulationJob?.cancel()
            simulationJob = null
            bleManager.stopScan()
        }
    }

    fun startBleScan() { bleManager.startScan() }
    fun stopBleScan() { bleManager.stopScan() }
    fun selectBleDevice(device: BleDeviceInfo) { _selectedBleDevice.value = device }
    fun registerBleDevice(device: BleDeviceInfo) {
        val current = _registeredBleDevices.value.filterNot { it.address == device.address }
        updateRegisteredBleDevices(current + RegisteredBleDevice(device.address, device.name))
    }
    fun renameRegisteredBleDevice(address: String, customName: String) {
        val name = customName.trim()
        if (name.isEmpty()) return
        updateRegisteredBleDevices(
            _registeredBleDevices.value.map {
                if (it.address == address) it.copy(customName = name) else it
            }
        )
    }
    fun removeRegisteredBleDevice(address: String) {
        updateRegisteredBleDevices(_registeredBleDevices.value.filterNot { it.address == address })
    }
    fun connectSelectedBleDevice() {
        val device = _selectedBleDevice.value ?: return
        connectBleDevice(device)
    }
    fun connectBleDevice(device: BleDeviceInfo) {
        if (bleManager.connectionState.value == BleConnectionState.CONNECTING ||
            bleManager.connectionState.value == BleConnectionState.CONNECTED
        ) return
        _selectedBleDevice.value = device
        bleManager.connect(device.address)
    }
    fun disconnectBleDevice() { bleManager.disconnect() }

    private fun loadRegisteredBleDevices(): List<RegisteredBleDevice> {
        val saved = bluetoothPreferences.getString("devices", null) ?: return emptyList()
        return try {
            val json = JSONArray(saved)
            (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val address = item.optString("address").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val name = item.optString("name").ifBlank { address }
                RegisteredBleDevice(address, name)
            }
        } catch (error: JSONException) {
            Log.e("LessionBLE", "No se pudo leer el registro local de dispositivos BLE.", error)
            emptyList()
        }
    }

    private fun updateRegisteredBleDevices(devices: List<RegisteredBleDevice>) {
        val json = JSONArray()
        devices.forEach { device ->
            json.put(
                JSONObject()
                    .put("address", device.address)
                    .put("name", device.customName)
            )
        }
        bluetoothPreferences.edit {
            putString("devices", json.toString())
        }
        _registeredBleDevices.value = devices
    }

    fun calibrateRestLevel() {
        val currentMv = _latestSensorData.value.filteredVoltageMv
        calibrationManager.saveMinVoltage(currentMv)
        _calibrationData.value = calibrationManager.loadCalibration()
    }

    fun calibrateMaxLevel() {
        val currentMv = _latestSensorData.value.filteredVoltageMv
        calibrationManager.saveMaxVoltage(currentMv)
        _calibrationData.value = calibrationManager.loadCalibration()
    }

    fun resetCalibration() {
        calibrationManager.resetCalibration()
        _calibrationData.value = calibrationManager.loadCalibration()
    }

    // --- GESTIÓN DE ALERTAS (HU-02) ---
    fun updateAlertConfig(minMv: Double, maxMv: Double, enabled: Boolean) {
        val newConfig = EmgAlertConfig(
            minThresholdMv = minMv.coerceAtLeast(0.0),
            maxThresholdMv = maxMv.coerceAtLeast(minMv + 10.0),
            isAlertEnabled = enabled
        )
        alertsManager.saveConfig(newConfig)
        _alertConfig.value = newConfig
    }

    fun startRecordingSession(title: String = "Sesión EMG V3") {
        if (_isRecording.value) return
        if (_dataSourceMode.value == DataSourceMode.REAL_BLE &&
            bleManager.connectionState.value != BleConnectionState.CONNECTED
        ) return

        viewModelScope.launch {
            simulationJob?.cancel()
            signalProcessor.resetFilter()
            simulationGenerator.reset()
            _hasSensorData.value = false
            _latestSensorData.value = EmgSensorData(0, 0.0, 0.0, 0.0)
            sessionStartTime = System.currentTimeMillis()
            sampleCount = 0
            totalVoltageSum = 0.0
            maxVoltage = 0.0
            minVoltage = Double.MAX_VALUE
            _chartDataPoints.value = emptyList()
            _isAlertActive.value = false

            val newSession = EmgSessionEntity(
                sessionTitle = title,
                startTime = sessionStartTime
            )

            val sessionId = repository.insertSession(newSession)
            _currentSessionId.value = sessionId
            _isRecording.value = true
            _recordingDurationMillis.value = 0L
            _formattedDuration.value = "00:00:00"

            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (_isRecording.value) {
                    delay(100)
                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    _recordingDurationMillis.value = elapsed
                    _formattedDuration.value = formatDuration(elapsed)
                }
            }
            if (_dataSourceMode.value == DataSourceMode.SIMULATION) {
                simulationJob = viewModelScope.launch {
                    while (_isRecording.value && _dataSourceMode.value == DataSourceMode.SIMULATION) {
                        delay(100)
                        processAndRecordEmgData(simulationGenerator.generateNextAdcSample())
                    }
                }
            }
        }
    }

    private fun clearLiveData() {
        _hasSensorData.value = false
        _latestSensorData.value = EmgSensorData(0, 0.0, 0.0, 0.0)
        _chartDataPoints.value = emptyList()
        signalProcessor.resetFilter()
    }

    /**
     * Procesa la lectura y evalúa si supera los umbrales de alerta configurados (HU-02).
     */
    fun processAndRecordEmgData(rawAdc: Int, measuredOutputMv: Double? = null) {
        val cal = _calibrationData.value
        val config = _alertConfig.value

        val processedData = signalProcessor.processSample(
            rawAdc = rawAdc,
            minVoltageMv = cal.minVoltageMv,
            maxVoltageMv = cal.maxVoltageMv,
            measuredOutputMv = measuredOutputMv
        )
        _latestSensorData.value = processedData
        _hasSensorData.value = true

        // Detección de alerta basada en los umbrales definidos
        val isAlert = config.isAlertEnabled && (
                processedData.filteredVoltageMv > config.maxThresholdMv ||
                        processedData.filteredVoltageMv < config.minThresholdMv
                )
        _isAlertActive.value = isAlert

        // Actualizar búfer de puntos para el gráfico
        val currentPoints = _chartDataPoints.value.toMutableList()
        currentPoints.add(processedData.voltageMv)
        if (currentPoints.size > 40) {
            currentPoints.removeAt(0)
        }
        _chartDataPoints.value = currentPoints

        val activeSessionId = _currentSessionId.value ?: return
        if (!_isRecording.value) return

        sampleCount++
        totalVoltageSum += processedData.voltageMv
        maxVoltage = max(maxVoltage, processedData.voltageMv)
        minVoltage = min(minVoltage, processedData.voltageMv)

        val durationSeconds = _recordingDurationMillis.value / 1000

        viewModelScope.launch {
            val record = EmgRecordEntity(
                sessionId = activeSessionId,
                timestamp = System.currentTimeMillis(),
                voltageMv = processedData.voltageMv,
                rawAdc = rawAdc,
                isAlert = isAlert
            )
            repository.insertRecord(record)

            val avgVoltage = if (sampleCount > 0) totalVoltageSum / sampleCount else 0.0
            val updatedSession = EmgSessionEntity(
                sessionId = activeSessionId,
                sessionTitle = if (_dataSourceMode.value == DataSourceMode.SIMULATION) {
                    "Sesión simulada EMG"
                } else {
                    "Sesión BLE ESP32-C3 · emgEnvelope SIG"
                },
                startTime = sessionStartTime,
                endTime = System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                avgVoltageMv = avgVoltage,
                maxVoltageMv = maxVoltage,
                minVoltageMv = if (minVoltage == Double.MAX_VALUE) 0.0 else minVoltage,
                totalSamples = sampleCount
            )
            repository.updateSession(updatedSession)
        }
    }

    fun stopRecordingSession() {
        if (!_isRecording.value) return
        timerJob?.cancel()
        simulationJob?.cancel()
        simulationJob = null
        _isRecording.value = false
        _currentSessionId.value = null
        _isAlertActive.value = false
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun selectSession(sessionId: Long) {
        _selectedSessionId.value = sessionId
        selectedRecordsJob?.cancel()
        _selectedSessionRecords.value = emptyList()
        selectedRecordsJob = viewModelScope.launch {
            repository.getRecordsForSession(sessionId).collect { records ->
                _selectedSessionRecords.value = records
            }
        }
    }

    suspend fun exportSessionCsv(sessionId: Long): String {
        val session = repository.getSessionById(sessionId).first()
            ?: throw IllegalStateException("El entrenamiento ya no está disponible.")
        val records = repository.getRecordsForSession(sessionId).first()
        return buildString {
            appendLine("Entrenamiento,${csvField(session.sessionTitle)}")
            appendLine("Inicio (epoch ms),${session.startTime}")
            appendLine("Duración (s),${session.durationSeconds}")
            appendLine("Cantidad de muestras,${session.totalSamples}")
            appendLine()
            appendLine("Marca de tiempo (epoch ms),ADC recibido (cuentas),Valor mV almacenado (mV),Alerta")
            records.forEach { record ->
                appendLine(
                    "${record.timestamp},${record.rawAdc},${String.format(Locale.US, "%.4f", record.voltageMv)},${record.isAlert}"
                )
            }
        }
    }

    private fun csvField(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    fun deleteSession(session: EmgSessionEntity) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (_selectedSessionId.value == session.sessionId) {
                _selectedSessionId.value = null
                _selectedSessionRecords.value = emptyList()
            }
        }
    }

    fun clearAllHistory() {
        selectedRecordsJob?.cancel()
        viewModelScope.launch {
            repository.clearAllData()
            _selectedSessionId.value = null
            _selectedSessionRecords.value = emptyList()
        }
    }

    override fun onCleared() {
        bleManager.disconnect()
    }
}

class EmgViewModelFactory(
    private val repository: EmgRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EmgViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EmgViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Clase ViewModel desconocida")
    }
}
