package com.example.lessionapp.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Estado de la conexión Bluetooth con el ESP32.
 */
enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Información básica de un dispositivo Bluetooth vinculado.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String
)

/**
 * Administrador de conexión Bluetooth Clásico (SPP) para recibir datos en tiempo real desde el ESP32.
 */
class BluetoothEmgManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter
    }

    private val _connectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _incomingAdcFlow = MutableSharedFlow<Int>(replay = 1)
    val incomingAdcFlow: SharedFlow<Int> = _incomingAdcFlow.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var isConnected = false

    // UUID Estándar SPP (Serial Port Profile) para comunicación serie con el ESP32
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Comprueba si el Bluetooth está habilitado en el dispositivo móvil.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Obtiene la lista de dispositivos Bluetooth previamente vinculados.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return try {
            adapter.bondedDevices?.map { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: "ESP32 Desconocido",
                    address = device.address
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Inicia la conexión por socket Bluetooth RFCOMM con la dirección MAC del ESP32.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = BluetoothConnectionState.ERROR
            return@withContext false
        }

        try {
            _connectionState.value = BluetoothConnectionState.CONNECTING
            val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)

            socket?.close()
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            adapter.cancelDiscovery()

            socket?.connect()
            isConnected = true
            _connectionState.value = BluetoothConnectionState.CONNECTED

            // Iniciar escucha del flujo de entrada de datos del ESP32
            startReadingStream()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _connectionState.value = BluetoothConnectionState.ERROR
            disconnect()
            false
        }
    }

    /**
     * Bucle continuo de lectura de datos provenientes del ESP32 a través del socket Bluetooth.
     */
    private suspend fun startReadingStream() = withContext(Dispatchers.IO) {
        val inputStream = socket?.inputStream ?: return@withContext
        val reader = BufferedReader(InputStreamReader(inputStream))

        try {
            while (isConnected && socket?.isConnected == true) {
                val line = reader.readLine() ?: break
                val trimmedLine = line.trim()
                if (trimmedLine.isNotEmpty()) {
                    val rawAdc = trimmedLine.toIntOrNull()
                    if (rawAdc != null) {
                        _incomingAdcFlow.emit(rawAdc.coerceIn(0, 4095))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isConnected) {
                _connectionState.value = BluetoothConnectionState.ERROR
            }
        } finally {
            disconnect()
        }
    }

    /**
     * Cierra la conexión Bluetooth de forma segura.
     */
    fun disconnect() {
        isConnected = false
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        socket = null
        _connectionState.value = BluetoothConnectionState.DISCONNECTED
    }
}
