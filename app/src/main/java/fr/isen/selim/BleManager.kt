package fr.isen.selim.masterproject

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

data class BleDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice? = null
)

data class SensorData(
    val personCount: Int = 0,
    val currentTemp: Float = 0f,
    val thresholdTemp: Float = 22f,
    val shouldAdjustTemp: Boolean = false,
    val currentPower: Int = 0,
    val dailyEnergy: Float = 0f
)

data class ConnectionState(
    val isConnected: Boolean = false,
    val deviceName: String = ""
)

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null

    // État de connexion
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Données des capteurs
    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData

    // Appareils trouvés
    private val _foundDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val foundDevices: StateFlow<List<BleDevice>> = _foundDevices

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                val device = scanResult.device
                val deviceName = device.name ?: "Unknown Device"

                // Filtrer pour notre Raspberry Pi (adapter le filtre selon votre config)
                if (deviceName.contains("SmartEnergy", ignoreCase = true) ||
                    deviceName.contains("RaspberryPi", ignoreCase = true) ||
                    deviceName.contains("RPi", ignoreCase = true)) {

                    val bleDevice = BleDevice(
                        name = deviceName,
                        address = device.address,
                        device = device
                    )

                    // Ajouter si pas déjà présent
                    val currentList = _foundDevices.value
                    if (!currentList.any { it.address == device.address }) {
                        _foundDevices.value = currentList + bleDevice
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            println("Scan failed with error code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    println("Connected to GATT server")
                    _connectionState.value = ConnectionState(
                        isConnected = true,
                        deviceName = gatt?.device?.name ?: "Unknown"
                    )
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    println("Disconnected from GATT server")
                    _connectionState.value = ConnectionState(isConnected = false)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                println("Services discovered")
                startReadingCharacteristics(gatt)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.let { char ->
                    processCharacteristicData(char)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let { char ->
                processCharacteristicData(char)
            }
        }
    }

    fun startScanning() {
        _foundDevices.value = emptyList()
        bleScanner?.startScan(scanCallback)
    }

    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
    }

    fun connectToDevice(device: BleDevice) {
        stopScanning()
        if (device.device != null) {
            gatt = device.device.connectGatt(context, false, gattCallback)
        }
    }

    private fun startReadingCharacteristics(gatt: BluetoothGatt?) {
        gatt?.services?.forEach { service ->
            service.characteristics.forEach { characteristic ->
                // Demander les notifications ou lire les caractéristiques
                when {
                    (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0 -> {
                        gatt.setCharacteristicNotification(characteristic, true)

                        // Configurer le descripteur pour les notifications
                        characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                    (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) > 0 -> {
                        gatt.readCharacteristic(characteristic)
                    }
                }
            }
        }
    }

    private fun processCharacteristicData(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value

        // Parser les données selon votre protocole
        // Format exemple: "P:5,T:23.5,S:22.0,A:1,W:2500,E:15.3"
        // P: nombre de personnes
        // T: température actuelle
        // S: seuil température
        // A: AC actif (1) ou non (0)
        // W: puissance (Watts)
        // E: énergie journalière (kWh)

        val dataString = String(value)
        val parts = dataString.split(",")

        val parsedData = parts.associate { part ->
            val keyValue = part.split(":")
            if (keyValue.size == 2) {
                keyValue[0].trim() to keyValue[1].trim()
            } else {
                null
            }
        }.filterValues { it != null }

        val newData = SensorData(
            personCount = (parsedData["P"] as? String)?.toIntOrNull() ?: _sensorData.value.personCount,
            currentTemp = (parsedData["T"] as? String)?.toFloatOrNull() ?: _sensorData.value.currentTemp,
            thresholdTemp = (parsedData["S"] as? String)?.toFloatOrNull() ?: _sensorData.value.thresholdTemp,
            shouldAdjustTemp = (parsedData["A"] as? String)?.toIntOrNull() == 1,
            currentPower = (parsedData["W"] as? String)?.toIntOrNull() ?: _sensorData.value.currentPower,
            dailyEnergy = (parsedData["E"] as? String)?.toFloatOrNull() ?: _sensorData.value.dailyEnergy
        )

        _sensorData.value = newData
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState()
    }
}
