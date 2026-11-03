package com.mominul.infinitytunnel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------- palette ----------
object Palette {
    val BgDeep = Color(0xFF020617)
    val BgTop = Color(0xFF061A3A)
    val Card = Color(0xFF0A1E42)
    val CardSoft = Color(0xFF081630)
    val Stroke = Color(0xFF1D3A66)
    val Accent = Color(0xFF38BDF8)
    val Accent2 = Color(0xFF2563EB)
    val Violet = Color(0xFF7C3AED)
    val TextMain = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF9DB4D8)
    val TextFaint = Color(0xFF7288AB)
    val Green = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFF87171)
    val LogGreen = Color(0xFF7EE2A8)
}

val AccentGradient = Brush.linearGradient(listOf(Palette.Accent, Palette.Accent2, Palette.Violet))
val ScreenGradient = Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgDeep, Palette.BgDeep))

enum class ConnState { Disconnected, Connecting, Connected }
enum class Screen { Home, Config, Logs }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogBus.add("Infinity Tunnel started")
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Palette.Accent,
                    secondary = Palette.Violet,
                    background = Palette.BgDeep,
                    surface = Palette.Card,
                    onBackground = Palette.TextMain,
                    onSurface = Palette.TextMain
                )
            ) {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { ConfigStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var conn by remember { mutableStateOf(ConnState.Disconnected) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        TunnelEngine.onStateChanged = { s ->
            conn = when (s) {
                TunnelEngine.State.Running -> ConnState.Connected
                TunnelEngine.State.Starting -> ConnState.Connecting
                else -> ConnState.Disconnected
            }
        }
        while (true) {
            tick = LogBus.version
            delay(700)
        }
    }

    fun toggleConnect() {
        if (conn == ConnState.Disconnected) {
            val missing = config.missingFields()
            if (missing.isNotEmpty()) {
                LogBus.add("Config incomplete — missing: ${missing.joinToString(", ")}")
                Toast.makeText(context, "Fill in the config first: ${missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
                screen = Screen.Config
                return
            }
            TunnelEngine.start(context, config)
        } else {
            TunnelEngine.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGradient)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (screen) {
                Screen.Home -> HomeScreen(config, conn, onToggle = { toggleConnect() })
                Screen.Config -> ConfigScreen(
                    initial = config,
                    onSave = {
                        config = it
                        store.save(it)
                        LogBus.add("Configuration saved")
                        Toast.makeText(context, "Configuration saved", Toast.LENGTH_SHORT).show()
                    }
                )
                Screen.Logs -> LogsScreen(tick)
            }
        }
        BottomBar(screen) { screen = it }
    }
}

// ---------- home ----------
@Composable
fun HomeScreen(config: TunnelConfig, conn: ConnState, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Text("∞", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Infinity Tunnel", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.TextMain)
                Text("DNSTT • SlowDNS Client", fontSize = 12.sp, color = Palette.TextMuted)
            }
        }

        Spacer(Modifier.height(26.dp))
        StatusPill(conn)
        Spacer(Modifier.height(30.dp))
        ConnectButton(conn, onToggle)
        Spacer(Modifier.height(24.dp))
        Text(
            when (conn) {
                ConnState.Disconnected -> "Tap to connect"
                ConnState.Connecting -> "Connecting…"
                ConnState.Connected -> "SOCKS5 ready • 127.0.0.1:${config.socksPort}"
            },
            color = Palette.TextMuted, fontSize = 14.sp
        )

        Spacer(Modifier.height(34.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard("Server", config.serverName.ifBlank { "Not set" }, Modifier.weight(1f))
            InfoCard("Resolver", config.resolver, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard("SSH", if (config.sshHost.isBlank()) "Not set" else "${config.sshHost}:${config.sshPort}", Modifier.weight(1f))
            InfoCard("UDPGW", "127.0.0.1:${config.udpgwPort}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun StatusPill(conn: ConnState) {
    val (dot, label) = when (conn) {
        ConnState.Disconnected -> Palette.Red to "Disconnected"
        ConnState.Connecting -> Palette.Amber to "Connecting"
        ConnState.Connected -> Palette.Green to "Connected"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Palette.CardSoft)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Palette.TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ConnectButton(conn: ConnState, onToggle: () -> Unit) {
    val active = conn != ConnState.Disconnected
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulseScale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f, targetValue = 0.05f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulseAlpha"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
        if (active) {
            Box(
                Modifier
                    .size(178.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(AccentGradient)
            )
        }
        Box(
            modifier = Modifier
                .size(178.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF123B7A), Color(0xFF081A3D))
                    )
                )
                .border(
                    3.dp,
                    if (active) AccentGradient else Brush.linearGradient(listOf(Palette.Stroke, Palette.Stroke)),
                    CircleShape
                )
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("∞", color = Color.White, fontSize = 62.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (conn == ConnState.Disconnected) "CONNECT" else "DISCONNECT",
                    color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label.uppercase(), color = Palette.TextFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Palette.TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- config ----------
@Composable
fun ConfigScreen(initial: TunnelConfig, onSave: (TunnelConfig) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(26.dp))
        Text("Configuration", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.TextMain)
        Text("Your DNSTT server and SSH account", color = Palette.TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        SectionTitle("DNSTT Server")
        ConfigField("Server name (nameserver)", draft.serverName) { draft = draft.copy(serverName = it) }
        ConfigField("Public key", draft.publicKey) { draft = draft.copy(publicKey = it) }
        Spacer(Modifier.height(6.dp))
        Text("Resolver (UDP DNS)", color = Palette.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1.1.1.1:53", "8.8.8.8:53").forEach { r ->
                FilterChip(
                    selected = draft.resolver == r,
                    onClick = { draft = draft.copy(resolver = r) },
                    label = { Text(r) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Palette.Accent2,
                        selectedLabelColor = Color.White,
                        containerColor = Palette.CardSoft,
                        labelColor = Palette.TextMuted
                    )
                )
            }
        }
        ConfigField("Custom resolver", draft.resolver) { draft = draft.copy(resolver = it) }

        Spacer(Modifier.height(18.dp))
        SectionTitle("SSH Account")
        ConfigField("SSH host", draft.sshHost) { draft = draft.copy(sshHost = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                ConfigField("Port", draft.sshPort.toString(), keyboard = KeyboardType.Number) {
                    draft = draft.copy(sshPort = it.toIntOrNull() ?: draft.sshPort)
                }
            }
            Box(Modifier.weight(1f)) {
                ConfigField("Username", draft.sshUser) { draft = draft.copy(sshUser = it) }
            }
        }
        ConfigPasswordField(
            label = "Password",
            value = draft.sshPassword,
            visible = showPassword,
            onToggleVisibility = { showPassword = !showPassword }
        ) { draft = draft.copy(sshPassword = it) }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Advanced")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                ConfigField("UDPGW port", draft.udpgwPort.toString(), keyboard = KeyboardType.Number) {
                    draft = draft.copy(udpgwPort = it.toIntOrNull() ?: draft.udpgwPort)
                }
            }
            Box(Modifier.weight(1f)) {
                ConfigField("SOCKS port", draft.socksPort.toString(), keyboard = KeyboardType.Number) {
                    draft = draft.copy(socksPort = it.toIntOrNull() ?: draft.socksPort)
                }
            }
        }
        ConfigField("MTU", draft.mtu.toString(), keyboard = KeyboardType.Number) {
            draft = draft.copy(mtu = it.toIntOrNull() ?: draft.mtu)
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent2)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Save configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Your details are stored only on this phone — nothing is built into the app.",
            color = Palette.TextFaint, fontSize = 12.sp
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun ConfigField(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.Stroke,
            focusedContainerColor = Palette.CardSoft,
            unfocusedContainerColor = Palette.CardSoft,
            focusedTextColor = Palette.TextMain,
            unfocusedTextColor = Palette.TextMain,
            focusedLabelColor = Palette.Accent,
            unfocusedLabelColor = Palette.TextFaint,
            cursorColor = Palette.Accent
        )
    )
}

@Composable
fun ConfigPasswordField(
    label: String,
    value: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.5.sp) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle password visibility",
                    tint = Palette.TextFaint
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.Stroke,
            focusedContainerColor = Palette.CardSoft,
            unfocusedContainerColor = Palette.CardSoft,
            focusedTextColor = Palette.TextMain,
            unfocusedTextColor = Palette.TextMain,
            focusedLabelColor = Palette.Accent,
            unfocusedLabelColor = Palette.TextFaint,
            cursorColor = Palette.Accent
        )
    )
}

// ---------- logs ----------
@Composable
fun LogsScreen(tick: Int) {
    val lines = remember(tick) { LogBus.snapshot() }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Logs", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.TextMain)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { LogBus.clear() }) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear logs", tint = Palette.TextFaint)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF010409))
                .border(1.dp, Palette.Stroke, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            LazyColumn(state = listState) {
                items(lines) { line ->
                    Text(
                        line,
                        color = Palette.LogGreen,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------- bottom bar ----------
@Composable
fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF030A1C))
            .border(1.dp, Palette.Stroke)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomItem(Icons.Filled.Home, "Home", current == Screen.Home) { onSelect(Screen.Home) }
        BottomItem(Icons.Filled.Settings, "Config", current == Screen.Config) { onSelect(Screen.Config) }
        BottomItem(Icons.Filled.List, "Logs", current == Screen.Logs) { onSelect(Screen.Logs) }
    }
}

@Composable
fun BottomItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Palette.Accent2 else Color.Transparent
    val fg = if (selected) Color.White else Palette.TextFaint
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        if (selected) {
            Spacer(Modifier.width(7.dp))
            Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
