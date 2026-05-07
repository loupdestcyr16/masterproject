@file:OptIn(ExperimentalMaterial3Api::class)

package fr.isen.selim.masterproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private lateinit var firebaseManager: FirebaseManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseManager = FirebaseManager()
        firebaseManager.startListening()
        setContent {
            SmartEnergyTheme {
                SmartEnergyApp(firebaseManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firebaseManager.disconnect()
    }
}

@Composable
fun SmartEnergyApp(firebaseManager: FirebaseManager) {
    val connectionState by firebaseManager.connectionState.collectAsState()
    val sensorData      by firebaseManager.sensorData.collectAsState()
    val isDemoMode      by firebaseManager.isDemoMode.collectAsState()

    when {
        !connectionState.isConnected ->
            ConnectingScreen(firebaseManager)
        else ->
            DashboardScreen(firebaseManager, connectionState, sensorData, isDemoMode)
    }
}

@Composable
fun ConnectingScreen(firebaseManager: FirebaseManager) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F4C5C), Color(0xFF1B5E7A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF00D4FF),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Connexion à Firebase...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Assurez-vous d'être connecté à Internet",
                color = Color(0xFFB0E0E6),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(40.dp))

            // Bouton Mode Démo
            Button(
                onClick = { firebaseManager.startDemo() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4FF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Lancer le mode démo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Le mode démo simule des données en temps réel",
                color = Color(0xFFB0E0E6),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DashboardScreen(
    firebaseManager: FirebaseManager,
    connectionState: ConnectionState,
    sensorData: SensorData,
    isDemoMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F4C5C), Color(0xFF1B5E7A))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HeaderSection(connectionState, firebaseManager, isDemoMode)
            Spacer(Modifier.height(24.dp))
            PersonCountCard(sensorData.personCount)
            Spacer(Modifier.height(16.dp))
            TemperatureSection(sensorData)
            Spacer(Modifier.height(16.dp))
            ACControlCard(sensorData)
            Spacer(Modifier.height(16.dp))
            EnergyConsumptionCard(sensorData)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeaderSection(
    connectionState: ConnectionState,
    firebaseManager: FirebaseManager,
    isDemoMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Smart Energy",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (connectionState.isConnected) Color(0xFF00D966)
                            else Color(0xFFFF6B6B)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connectionState.isConnected) "Connecté · ${connectionState.deviceName}"
                    else "Déconnecté",
                    color = Color(0xFFB0E0E6),
                    fontSize = 12.sp
                )
            }
        }

        // Bouton stop démo
        if (isDemoMode) {
            IconButton(onClick = { firebaseManager.stopDemo() }) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Arrêter la démo",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun PersonCountCard(personCount: Int) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1B5E7A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Nombre de personnes",
                    color = Color(0xFFB0E0E6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$personCount",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF00D4FF)
            )
        }
    }
}

@Composable
fun TemperatureSection(sensorData: SensorData) {
    Column {
        Text(
            "Température",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1B5E7A))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Actuelle", color = Color(0xFFB0E0E6), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${sensorData.currentTemp}°C",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.Thermostat,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color(0xFFFF9F43)
                    )
                }
            }
            ElevatedCard(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1B5E7A))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Seuil", color = Color(0xFFB0E0E6), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${sensorData.thresholdTemp}°C",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color(0xFF00D966)
                    )
                }
            }
        }
    }
}

@Composable
fun ACControlCard(sensorData: SensorData) {
    val shouldAdjust = sensorData.shouldAdjustTemp
    val statusColor  = if (shouldAdjust) Color(0xFFFF6B6B) else Color(0xFF00D966)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1B5E7A))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Climatisation",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (shouldAdjust) "ACTIF" else "ARRÊTÉ",
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Différence", color = Color(0xFFB0E0E6), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${"%.1f".format(sensorData.currentTemp - sensorData.thresholdTemp)}°C",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mode", color = Color(0xFFB0E0E6), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (shouldAdjust) "Refroidissement" else "Stand-by",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EnergyConsumptionCard(sensorData: SensorData) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1B5E7A))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Consommation d'énergie",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.EvStation,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF00D966)
                )
            }
            Spacer(Modifier.height(16.dp))
            EnergyMetric(
                label = "Consommation actuelle",
                value = "${sensorData.currentPower} W",
                percentage = (sensorData.currentPower.toFloat() / 5000f * 100).toInt().coerceIn(0, 100)
            )
            Spacer(Modifier.height(12.dp))
            EnergyMetric(
                label = "Consommation journalière",
                value = "${sensorData.dailyEnergy} kWh",
                percentage = (sensorData.dailyEnergy / 50f * 100).toInt().coerceIn(0, 100)
            )
        }
    }
}

@Composable
fun EnergyMetric(label: String, value: String, percentage: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFFB0E0E6), fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = percentage.toFloat() / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF00D966),
            trackColor = Color(0xFF0F4C5C)
        )
    }
}

@Composable
fun SmartEnergyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary    = Color(0xFF00D4FF),
            secondary  = Color(0xFF00D966),
            background = Color(0xFF0F4C5C),
            surface    = Color(0xFF1B5E7A)
        ),
        content = content
    )
}