package fr.isen.selim.masterproject

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialiser le BLE Manager
        bleManager = BleManager(this)

        // Demander les permissions
        requestBluetoothPermissions()

        setContent {
            SmartEnergyTheme {
                SmartEnergyApp(bleManager)
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.values.forEach { granted ->
                if (!granted) {
                    // Handle permission denied
                }
            }
        }

        val allPermissionsGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allPermissionsGranted) {
            permissionLauncher.launch(permissions)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}

@Composable
fun SmartEnergyApp(bleManager: BleManager) {
    val connectionState by bleManager.connectionState.collectAsState()
    val sensorData by bleManager.sensorData.collectAsState()
    val devices by bleManager.foundDevices.collectAsState()

    LaunchedEffect(Unit) {
        bleManager.startScanning()
    }

    when {
        !connectionState.isConnected && devices.isEmpty() -> {
            ScanningScreen(bleManager)
        }
        !connectionState.isConnected && devices.isNotEmpty() -> {
            DeviceSelectionScreen(bleManager, devices)
        }
        else -> {
            DashboardScreen(bleManager, connectionState, sensorData)
        }
    }
}

@Composable
fun ScanningScreen(bleManager: BleManager) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F4C5C),
                        Color(0xFF1B5E7A)
                    )
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
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Recherche des appareils...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Assurez-vous que le Raspberry Pi est allumé",
                color = Color(0xFFB0E0E6),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun DeviceSelectionScreen(bleManager: BleManager, devices: List<BleDevice>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F4C5C),
                        Color(0xFF1B5E7A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Sélectionnez un appareil",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            devices.forEach { device ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color(0xFF1B5E7A)
                    ),
                    onClick = { bleManager.connectToDevice(device) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                device.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                device.address,
                                color = Color(0xFFB0E0E6),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF00D4FF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    bleManager: BleManager,
    connectionState: ConnectionState,
    sensorData: SensorData
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F4C5C),
                        Color(0xFF1B5E7A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header avec status
            HeaderSection(connectionState, bleManager)

            Spacer(modifier = Modifier.height(24.dp))

            // Nombre de personnes
            PersonCountCard(sensorData.personCount)

            Spacer(modifier = Modifier.height(16.dp))

            // Température actuelle et seuil
            TemperatureSection(sensorData)

            Spacer(modifier = Modifier.height(16.dp))

            // Ajustement AC
            ACControlCard(sensorData)

            Spacer(modifier = Modifier.height(16.dp))

            // Consommation d'énergie
            EnergyConsumptionCard(sensorData)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeaderSection(connectionState: ConnectionState, bleManager: BleManager) {
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (connectionState.isConnected) "Connecté" else "Déconnecté",
                    color = Color(0xFFB0E0E6),
                    fontSize = 12.sp
                )
            }
        }

        if (connectionState.isConnected) {
            IconButton(
                onClick = { bleManager.disconnect() }
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = "Déconnecter",
                    tint = Color(0xFFFF6B6B)
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
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF1B5E7A)
        )
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
                Spacer(modifier = Modifier.height(8.dp))
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
            // Température actuelle
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xFF1B5E7A)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Actuelle",
                        color = Color(0xFFB0E0E6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${sensorData.currentTemp}°C",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        Icons.Default.Thermostat,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color(0xFFFF9F43)
                    )
                }
            }

            // Température seuil
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xFF1B5E7A)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Seuil",
                        color = Color(0xFFB0E0E6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${sensorData.thresholdTemp}°C",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
    val acStatus = if (shouldAdjust) "ACTIF" else "ARRÊTÉ"
    val statusColor = if (shouldAdjust) Color(0xFFFF6B6B) else Color(0xFF00D966)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF1B5E7A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
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
                        .background(
                            color = statusColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        acStatus,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Différence",
                        color = Color(0xFFB0E0E6),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${String.format("%.1f", sensorData.currentTemp - sensorData.thresholdTemp)}°C",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Mode",
                        color = Color(0xFFB0E0E6),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF1B5E7A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                EnergyMetric(
                    label = "Consommation actuelle",
                    value = "${sensorData.currentPower} W",
                    percentage = (sensorData.currentPower.toFloat() / 5000f * 100).toInt()
                )

                Spacer(modifier = Modifier.height(12.dp))

                EnergyMetric(
                    label = "Consommation journalière",
                    value = "${sensorData.dailyEnergy} kWh",
                    percentage = (sensorData.dailyEnergy.toFloat() / 50f * 100).toInt().coerceAtMost(100)
                )
            }
        }
    }
}

@Composable
fun EnergyMetric(label: String, value: String, percentage: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
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
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF00D4FF),
        secondary = Color(0xFF00D966),
        background = Color(0xFF0F4C5C),
        surface = Color(0xFF1B5E7A)
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}
