package fr.isen.selim.masterproject

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice? = null
)

data class ConnectionState(
    val isConnected: Boolean = false,
    val deviceName: String = ""
)

data class SensorData(
    val personCount: Int = 0,
    val currentTemp: Float = 0f,
    val thresholdTemp: Float = 22f,
    val shouldAdjustTemp: Boolean = false,
    val currentPower: Int = 0,
    val dailyEnergy: Float = 0f
)




