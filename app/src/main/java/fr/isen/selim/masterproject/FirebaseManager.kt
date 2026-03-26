package fr.isen.selim.masterproject

import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FirebaseManager {

    private val database = FirebaseDatabase.getInstance(
        "https://smartenergy-5a32d-default-rtdb.europe-west1.firebasedatabase.app"
    )
    private val ref = database.getReference("sensor_data")

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _connectionState = MutableStateFlow(ConnectionState(isConnected = false))
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun startListening() {
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = SensorData(
                    personCount      = snapshot.child("persons").getValue(Int::class.java) ?: 0,
                    currentTemp      = snapshot.child("temp").getValue(Double::class.java)?.toFloat() ?: 0f,
                    thresholdTemp    = snapshot.child("threshold").getValue(Double::class.java)?.toFloat() ?: 22f,
                    shouldAdjustTemp = snapshot.child("ac_on").getValue(Boolean::class.java) ?: false,
                    currentPower     = snapshot.child("power").getValue(Int::class.java) ?: 0,
                    dailyEnergy      = snapshot.child("daily_kwh").getValue(Double::class.java)?.toFloat() ?: 0f
                )
                _sensorData.value = data
                _connectionState.value = ConnectionState(isConnected = true, deviceName = "Firebase")
            }

            override fun onCancelled(error: DatabaseError) {
                _connectionState.value = ConnectionState(isConnected = false)
            }
        })
    }

    fun disconnect() {
        _connectionState.value = ConnectionState(isConnected = false)
    }
}