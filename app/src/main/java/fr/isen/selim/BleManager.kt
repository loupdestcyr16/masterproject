package fr.isen.selim.masterproject

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _foundDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val foundDevices: StateFlow<List<BleDevice>> = _foundDevices

    // UUID du descripteur standard pour activer les notifications
    private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val deviceName = device.name ?: "Appareil Inconnu"

                // On ajoute l'appareil s'il n'est pas déjà dans la liste
                val currentList = _foundDevices.value
                if (!currentList.any { it.address == device.address }) {
                    // Optionnel : Tu peux remettre ton filtre ici si tu as trop d'appareils autour
                    val bleDevice = BleDevice(name = deviceName, address = device.address, device = device)
                    _foundDevices.value = currentList + bleDevice
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState(true, gatt?.device?.name ?: "Raspberry Pi")
                // Indispensable pour découvrir ce que le Pi propose
                Handler(Looper.getMainLooper()).postDelayed({ gatt?.discoverServices() }, 500)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupNotifications(gatt)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            processCharacteristicData(characteristic)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                processCharacteristicData(characteristic)
            }
        }
    }

    fun startScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            _foundDevices.value = emptyList()
            bleScanner?.startScan(scanCallback)
        }
    }

    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
    }

    fun connectToDevice(device: BleDevice) {
        stopScanning()
        gatt = device.device?.connectGatt(context, false, gattCallback)
    }

    private fun setupNotifications(gatt: BluetoothGatt?) {
        gatt?.services?.forEach { service ->
            service.characteristics.forEach { characteristic ->
                // Si la caractéristique permet les notifications
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    gatt.setCharacteristicNotification(characteristic, true)

                    // Activation cruciale du descripteur
                    val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }
    }

    private fun processCharacteristicData(characteristic: BluetoothGattCharacteristic) {
        val data = characteristic.value
        if (data == null || data.isEmpty()) return

        val dataString = String(data)
        // Format attendu du RPi : "P:5,T:22.5,S:20.0,A:1,W:450,E:1.2"
        try {
            val parsedData = dataString.split(",").associate {
                val parts = it.split(":")
                parts[0].trim() to parts[1].trim()
            }

            _sensorData.value = _sensorData.value.copy(
                personCount = parsedData["P"]?.toIntOrNull() ?: _sensorData.value.personCount,
                currentTemp = parsedData["T"]?.toFloatOrNull() ?: _sensorData.value.currentTemp,
                thresholdTemp = parsedData["S"]?.toFloatOrNull() ?: _sensorData.value.thresholdTemp,
                shouldAdjustTemp = parsedData["A"] == "1",
                currentPower = parsedData["W"]?.toIntOrNull() ?: _sensorData.value.currentPower,
                dailyEnergy = parsedData["E"]?.toFloatOrNull() ?: _sensorData.value.dailyEnergy
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState()
    }
}