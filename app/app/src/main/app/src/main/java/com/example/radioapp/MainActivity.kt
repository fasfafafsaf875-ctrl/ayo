package com.example.radioapp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

data class RadioStation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val frequency: String,
    val webUrl: String,
    val streamUrl: String,
    val colorHex: String = "#1E88E5",
    val isCustom: Boolean = false
) {
    val backgroundColor: Color
        get() = try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            Color(0xFF1E88E5)
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    RadioScreen()
                }
            }
        }
    }
}

// Obsługa zapisywania stacji w SharedPreferences
object StationStorage {
    private const val PREFS_NAME = "radio_prefs"
    private const val KEY_STATIONS = "user_stations"

    fun saveStations(context: Context, stations: List<RadioStation>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(stations)
        prefs.edit().putString(KEY_STATIONS, json).apply()
    }

    fun loadStations(context: Context): List<RadioStation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_STATIONS, null) ?: return defaultStations()
        val type = object : TypeToken<List<RadioStation>>() {}.type
        return try {
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            defaultStations()
        }
    }

    private fun defaultStations() = listOf(
        RadioStation(name = "RMF FM", frequency = "96.0 FM", webUrl = "https://www.rmf.fm", streamUrl = "https://rs102-krk.rmftrx.net/RMFFM48", colorHex = "#E53935"),
        RadioStation(name = "Radio ZET", frequency = "101.0 FM", webUrl = "https://www.radiozet.pl", streamUrl = "https://stream.radiozet.pl/zet01.mp3", colorHex = "#1E88E5"),
        RadioStation(name = "ESKA", frequency = "105.6 FM", webUrl = "https://www.eska.pl", streamUrl = "https://ic2.smcdn.pl/2380-1.mp3", colorHex = "#FB8C00"),
        RadioStation(name = "Antyradio", frequency = "106.8 FM", webUrl = "https://www.antyradio.pl", streamUrl = "https://stream.antyradio.pl/antyradio.mp3", colorHex = "#43A047")
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioScreen() {
    val context = LocalContext.current
    var stations by remember { mutableStateOf(StationStorage.loadStations(context)) }
    var showAddDialog by remember { mutableStateOf(false) }

    val player = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = ProgressiveMediaSource.Factory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPlayingId by remember { mutableStateOf<String?>(null) }

    val pagerState = rememberPagerState(pageCount = { stations.size })
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Przycisk dodawania nowej stacji w górnym prawym rogu
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Dodaj stację")
        }

        if (stations.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            ) { page ->
                val station = stations.getOrNull(page) ?: return@HorizontalPager
                val isThisStationPlaying = isPlaying && currentPlayingId == station.id

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = station.backgroundColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(horizontal = 24.dp)
                        .clickable {
                            if (station.webUrl.isNotBlank()) {
                                try {
                                    val intent = CustomTabsIntent.Builder().build()
                                    intent.launchUrl(context, Uri.parse(station.webUrl))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Błędny adres www", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Przycisk usuwania dla własnych stacji
                        if (station.isCustom) {
                            IconButton(
                                onClick = {
                                    if (isThisStationPlaying) {
                                        player.stop()
                                        isPlaying = false
                                        currentPlayingId = null
                                    }
                                    val updatedList = stations.toMutableList().apply { removeAt(page) }
                                    stations = updatedList
                                    StationStorage.saveStations(context, updatedList)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Usuń stację",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = station.name,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = station.frequency,
                                    fontSize = 18.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            // Przycisk PLAY/STOP
                            IconButton(
                                onClick = {
                                    if (isThisStationPlaying) {
                                        player.stop()
                                        isPlaying = false
                                        currentPlayingId = null
                                    } else {
                                        player.stop()
                                        try {
                                            val mediaItem = MediaItem.fromUri(station.streamUrl)
                                            player.setMediaItem(mediaItem)
                                            player.prepare()
                                            player.play()
                                            isPlaying = true
                                            currentPlayingId = station.id
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Nie można odtworzyć strumienia", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isThisStationPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isThisStationPlaying) "Zatrzymaj" else "Odtwarzaj",
                                    tint = station.backgroundColor,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Text(
                                text = if (station.webUrl.isNotBlank()) "Dotknij karty, aby otworzyć stronę www" else "",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Strzałka LEWO
            IconButton(
                onClick = {
                    if (pagerState.currentPage > 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(
                        color = if (pagerState.currentPage > 0) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Poprzednia",
                    tint = if (pagerState.currentPage > 0) Color.White else Color.Gray
                )
            }

            // Strzałka PRAWO
            IconButton(
                onClick = {
                    if (pagerState.currentPage < stations.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                enabled = pagerState.currentPage < stations.size - 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(
                        color = if (pagerState.currentPage < stations.size - 1) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Następna",
                    tint = if (pagerState.currentPage < stations.size - 1) Color.White else Color.Gray
                )
            }
        }

        // Okienko dialogowe dodawania stacji
        if (showAddDialog) {
            AddStationDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { newStation ->
                    val updatedList = stations.toMutableList().apply { add(newStation) }
                    stations = updatedList
                    StationStorage.saveStations(context, updatedList)
                    showAddDialog = false
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(updatedList.size - 1)
                    }
                }
            )
        }
    }
}

@Composable
fun AddStationDialog(
    onDismiss: () -> Unit,
    onAdd: (RadioStation) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("") }
    var webUrl by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj nową stację") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa stacji *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Częstotliwość/Opis (np. 100 FM)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("URL strumienia audio (MP3/AAC) *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = webUrl,
                    onValueChange = { webUrl = it },
                    label = { Text("Adres strony WWW (opcjonalnie)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && streamUrl.isNotBlank()) {
                        var formattedWebUrl = webUrl.trim()
                        if (formattedWebUrl.isNotBlank() && !formattedWebUrl.startsWith("http://") && !formattedWebUrl.startsWith("https://")) {
                            formattedWebUrl = "https://$formattedWebUrl"
                        }

                        var formattedStreamUrl = streamUrl.trim()
                        if (!formattedStreamUrl.startsWith("http://") && !formattedStreamUrl.startsWith("https://")) {
                            formattedStreamUrl = "http://$formattedStreamUrl"
                        }

                        val newStation = RadioStation(
                            name = name.trim(),
                            frequency = if (frequency.isBlank()) "Internet" else frequency.trim(),
                            webUrl = formattedWebUrl,
                            streamUrl = formattedStreamUrl,
                            colorHex = "#3F51B5", // Domyślna ciemnoniebieska karta dla własnych stacji
                            isCustom = true
                        )
                        onAdd(newStation)
                    }
                }
            ) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}
