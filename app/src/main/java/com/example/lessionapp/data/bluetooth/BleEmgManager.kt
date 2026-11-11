package com.example.lessionapp.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estados de conexión BLE para el ESP32-C3 SuperMini (HU-03).
 */
enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Información de dispositivo BLE detectado.
 */
data class BleDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int,
    val advertisesEmgService: Boolean = false
)

data class RegisteredBleDevice(
    val address: String,
    val customName: String
)

data class BleEmgSample(
    val adcRaw: Int,
    val outputMillivolts: Double,
    val metric: String,
    val timestamp: Long
)

/**
 * Administrador de Bluetooth Low Energy (BLE) para la comunicación con el ESP32-C3 SuperMini (HU-03).
 */
@SuppressLint("MissingPermission")
class BleEmgManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceInfo>> = _scannedDevices.asStateFlow()

    private val _discoveredServices = MutableStateFlow<List<String>>(emptyList())
    val discoveredServices: StateFlow<List<String>> = _discoveredServices.asStateFlow()

    private val _connectionDetails = MutableStateFlow("Desconectado")
    val connectionDetails: StateFlow<String> = _connectionDetails.asStateFlow()

    private val _isEmgCompatible = MutableStateFlow(false)
    val isEmgCompatible: StateFlow<Boolean> = _isEmgCompatible.asStateFlow()

    private val _incomingEmgFlow = MutableSharedFlow<BleEmgSample>(replay = 0)
    val incomingEmgFlow: SharedFlow<BleEmgSample> = _incomingEmgFlow.asSharedFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var disconnectRequested = false
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val scanStopRunnable = Runnable { stopScan() }

    companion object {
        private const val TAG = "LessionBLE"
        val EMG_SERVICE_UUID: UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val EMG_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_PERIOD_MS = 10000L
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val advertisesEmgService = result.scanRecord?.serviceUuids
                ?.any { it.uuid == EMG_SERVICE_UUID } == true
            if (!advertisesEmgService) return

            val name = try {
                device.name ?: result.scanRecord?.deviceName ?: "Nombre no disponible"
            } catch (_: SecurityException) {
                result.scanRecord?.deviceName ?: "Nombre no disponible"
            }
            val deviceInfo = BleDeviceInfo(name, device.address, result.rssi, true)
            _scannedDevices.value = _scannedDevices.value
                .associateBy { it.address }
                .toMutableMap()
                .apply { put(device.address, deviceInfo) }
                .values
                .sortedByDescending { it.rssi }
            Log.d(TAG, "Dispositivo BLE detectado/actualizado: name=$name address=${device.address} rssi=${result.rssi} emgService=$advertisesEmgService")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Falló el escaneo BLE. errorCode=$errorCode")
            _connectionState.value = BleConnectionState.ERROR
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "Cambio de conexión GATT: address=${gatt.device.address} status=$status newState=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Conexión BLE establecida; descubriendo servicios.")
                _connectionDetails.value = "Conectado; comprobando servicios y características GATT."
                if (!gatt.discoverServices()) {
                    Log.e(TAG, "No se pudo iniciar el descubrimiento de servicios.")
                    _connectionDetails.value = "Conectado, pero no se pudieron consultar los servicios GATT."
                    _connectionState.value = BleConnectionState.ERROR
                    closeGatt()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Conexión GATT finalizada. status=$status disconnectRequested=$disconnectRequested")
                _isEmgCompatible.value = false
                _connectionDetails.value = if (disconnectRequested) "Desconectado." else "Se perdió la conexión BLE."
                _connectionState.value = if (disconnectRequested) {
                    BleConnectionState.DISCONNECTED
                } else {
                    BleConnectionState.ERROR
                }
                closeGatt()
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Error de conexión GATT. status=$status newState=$newState")
                _connectionDetails.value = "Falló la conexión BLE (código $status)."
                _connectionState.value = BleConnectionState.ERROR
                closeGatt()
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Falló el descubrimiento de servicios GATT. status=$status")
                _connectionState.value = BleConnectionState.ERROR
                closeGatt()
                return
            }
            _discoveredServices.value = gatt.services.map { it.uuid.toString() }
            Log.d(TAG, "Servicios GATT encontrados: ${_discoveredServices.value}")
            val characteristicDetails = gatt.services.flatMap { service ->
                service.characteristics.map { characteristic ->
                    "${service.uuid}/${characteristic.uuid} [${characteristicProperties(characteristic.properties)}]"
                }
            }
            _connectionDetails.value = if (characteristicDetails.isEmpty()) {
                "Conectado; el dispositivo no publicó características GATT."
            } else {
                "Conectado; GATT: ${characteristicDetails.joinToString()}"
            }
            val characteristic = gatt.getService(EMG_SERVICE_UUID)
                ?.getCharacteristic(EMG_CHARACTERISTIC_UUID)
            if (characteristic == null ||
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
            ) {
                _isEmgCompatible.value = false
                _connectionDetails.value =
                    "Conectado; dispositivo no compatible con EMG (no ofrece el servicio y característica de notificación requeridos)."
                Log.i(TAG, _connectionDetails.value)
                _connectionState.value = BleConnectionState.CONNECTED
                return
            }
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor == null || !gatt.setCharacteristicNotification(characteristic, true)) {
                _isEmgCompatible.value = false
                _connectionDetails.value = "Conectado; el dispositivo no permite habilitar notificaciones EMG."
                Log.w(TAG, _connectionDetails.value)
                _connectionState.value = BleConnectionState.CONNECTED
                return
            }
            val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
            if (!writeStarted) {
                _isEmgCompatible.value = false
                _connectionDetails.value = "Conectado; no se pudieron habilitar notificaciones EMG."
                Log.w(TAG, _connectionDetails.value)
                _connectionState.value = BleConnectionState.CONNECTED
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                Log.i(TAG, "Escritura del descriptor de notificaciones finalizada. status=$status")
                _isEmgCompatible.value = status == BluetoothGatt.GATT_SUCCESS
                _connectionState.value = BleConnectionState.CONNECTED
                _connectionDetails.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                    "Conectado; dispositivo compatible con EMG, recibiendo notificaciones."
                } else {
                    "Conectado, pero no se pudieron habilitar notificaciones EMG."
                }
            }
        }

        @Deprecated("Use the value overload on API 33 and newer.")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == EMG_CHARACTERISTIC_UUID) {
                characteristic.value?.let(::parseEmgNotification)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == EMG_CHARACTERISTIC_UUID) {
                parseEmgNotification(value)
            }
        }
    }

    private fun parseEmgNotification(payload: ByteArray) {
        val json = try {
            JSONObject(payload.toString(Charsets.UTF_8))
        } catch (error: org.json.JSONException) {
            Log.w(TAG, "Notificación BLE descartada: JSON inválido.", error)
            return
        }
        if (json.optString("type") != "emg" ||
            json.optString("sensor") != "emg_module" ||
            json.optString("status") != "valid" ||
            json.optString("unit") != "mV" ||
            json.optString("metric") != "emgEnvelope"
        ) {
            Log.w(TAG, "Notificación BLE ignorada: tipo, sensor, estado, unidad o métrica inesperados.")
            return
        }

        val adcRaw = json.optInt("adcRaw", -1)
        val value = json.optDouble("value", Double.NaN)
        if (adcRaw !in 0..4095 || !value.isFinite() || value < 0.0) {
            Log.w(TAG, "Notificación BLE ignorada: valores fuera de formato. adcRaw=$adcRaw value=$value")
            return
        }

        _incomingEmgFlow.tryEmit(
            BleEmgSample(
                adcRaw = adcRaw,
                outputMillivolts = value,
                metric = json.getString("metric"),
                timestamp = json.optLong("timestamp", 0L)
            )
        )
    }

    fun startScan() {
        if (isScanning) {
            Log.d(TAG, "Se ignoró startScan: ya hay un escaneo activo.")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "No se puede iniciar el escaneo: Bluetooth apagado o adaptador no disponible.")
            return
        }

        _scannedDevices.value = emptyList()
        _connectionState.value = BleConnectionState.SCANNING
        _connectionDetails.value = "Buscando dispositivos BLE cercanos."
        isScanning = true

        try {
            Log.i(TAG, "Iniciando escaneo BLE por ${SCAN_PERIOD_MS}ms.")
            val scanner = bluetoothAdapter?.bluetoothLeScanner
                ?: throw IllegalStateException("El escáner Bluetooth LE no está disponible.")
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
            handler.removeCallbacks(scanStopRunnable)
            handler.postDelayed(scanStopRunnable, SCAN_PERIOD_MS)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permiso denegado al iniciar el escaneo BLE.", e)
            _connectionState.value = BleConnectionState.ERROR
            isScanning = false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "No se pudo iniciar el escaneo BLE.", e)
            _connectionState.value = BleConnectionState.ERROR
            isScanning = false
        }
    }

    fun stopScan() {
        handler.removeCallbacks(scanStopRunnable)
        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.i(TAG, "Escaneo BLE detenido.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permiso denegado al detener el escaneo BLE.", e)
            _connectionState.value = BleConnectionState.ERROR
        }
        isScanning = false
        if (_connectionState.value == BleConnectionState.SCANNING) {
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    fun connect(address: String) {
        stopScan()
        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "No se puede conectar: adaptador Bluetooth no disponible.")
            _connectionState.value = BleConnectionState.ERROR
            return
        }
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            disconnectRequested = false
            _isEmgCompatible.value = false
            _discoveredServices.value = emptyList()
            _connectionState.value = BleConnectionState.CONNECTING
            _connectionDetails.value = "Conectando y consultando servicios GATT."
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            Log.i(TAG, "Iniciando conexión GATT con address=$address name=${device.name}")
            bluetoothGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            ) ?: run {
                _connectionDetails.value = "Android no pudo iniciar la conexión GATT."
                _connectionState.value = BleConnectionState.ERROR
                Log.e(TAG, _connectionDetails.value)
                return
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permiso denegado al conectar por GATT. address=$address", e)
            _connectionState.value = BleConnectionState.ERROR
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Dirección BLE inválida: $address", e)
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    fun disconnect() {
        stopScan()
        disconnectRequested = true
        try {
            Log.i(TAG, "Solicitando desconexión BLE.")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permiso denegado al desconectar BLE.", e)
            _connectionState.value = BleConnectionState.ERROR
        }
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        _isEmgCompatible.value = false
        _connectionDetails.value = "Desconectado."
    }

    private fun characteristicProperties(properties: Int): String = buildList {
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
    }.joinToString("|").ifEmpty { "sin propiedades estándar" }

    private fun closeGatt() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar la conexión GATT.", e)
        }
        bluetoothGatt = null
    }
}
