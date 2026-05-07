package fr.isen.selim.masterproject

import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

class FirebaseManager {

    private val database = FirebaseDatabase.getInstance(
        "https://smartenergy-5a32d-default-rtdb.europe-west1.firebasedatabase.app"
    )
    private val ref = database.getReference("sensor_data")

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _connectionState = MutableStateFlow(ConnectionState(isConnected = false))
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode

    private var demoJob: Job? = null
    private var demoTick = 0

    fun startListening() {
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_isDemoMode.value) return

                // Si pas de données dans Firebase, reste sur écran connexion
                if (!snapshot.exists()) {
                    _connectionState.value = ConnectionState(isConnected = false)
                    return
                }

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

    fun startDemo() {
        _isDemoMode.value = true
        _connectionState.value = ConnectionState(isConnected = true, deviceName = "Mode Démo")
        demoJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                demoTick++
                val temp = 24f + (4f * sin(demoTick * 0.1f))
                val threshold = 24f
                val persons = ((demoTick / 5) % 8) + 1
                val acOn = temp > threshold
                val power = if (acOn) (1200 + persons * 150) else (200 + persons * 50)
                val dailyEnergy = (demoTick * 0.01f).coerceAtMost(50f)

                _sensorData.value = SensorData(
                    personCount      = persons,
                    currentTemp      = String.format("%.1f", temp).toFloat(),
                    thresholdTemp    = threshold,
                    shouldAdjustTemp = acOn,
                    currentPower     = power,
                    dailyEnergy      = String.format("%.1f", dailyEnergy).toFloat()
                )
                delay(2000)
            }
        }
    }

    fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
        _isDemoMode.value = false
        _connectionState.value = ConnectionState(isConnected = false)
    }

    fun disconnect() {
        stopDemo()
        _connectionState.value = ConnectionState(isConnected = false)
    }
}