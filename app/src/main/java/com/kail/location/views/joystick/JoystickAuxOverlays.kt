package com.kail.location.views.joystick

import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLng
import com.kail.location.R
import com.kail.location.viewmodels.SettingsViewModel
import com.kail.location.views.history.HistoryActivity
import com.kail.location.views.locationpicker.LocationPickerActivity
import com.kail.location.viewmodels.LocationPickerViewModel
import androidx.preference.PreferenceManager

/**
 * 历史记录浮窗的组合函数。
 * 在悬浮窗中显示历史记录列表。
 *
 * @param historyRecords 要展示的历史记录列表。
 * @param onClose 点击关闭按钮的回调。
 * @param onWindowDrag 悬浮窗拖动回调（dx, dy）。
 * @param onSelectRecord 选中某条历史记录时的回调。
 * @param onSearch 搜索关键字变化时的回调。
 */
@Composable
fun JoyStickHistoryOverlay(
    historyRecords: List<Map<String, Any>>,
    onClose: () -> Unit,
    onWindowDrag: (Float, Float) -> Unit,
    onSelectRecord: (Map<String, Any>) -> Unit,
    onSearch: (String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    val winW = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_WIDTH, "300") ?: "300").toIntOrNull() ?: 300
    }.dp
    val winH = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_HEIGHT, "500") ?: "500").toIntOrNull() ?: 500
    }.dp

    val filteredRecords = remember(historyRecords, searchQuery) {
        if (searchQuery.isBlank()) historyRecords
        else historyRecords.filter { r ->
            val name = (r[HistoryActivity.KEY_LOCATION] as? String) ?: ""
            val time = (r[HistoryActivity.KEY_TIME] as? String) ?: ""
            name.contains(searchQuery, ignoreCase = true) || time.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .width(winW)
            .height(winH)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(R.string.joystick_history_tips),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text(stringResource(R.string.app_search_tips)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        // List
        if (filteredRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_idle), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filteredRecords) { record ->
                    val id = (record[HistoryActivity.KEY_ID] as? String) ?: ""
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectRecord(record) }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            HistoryItem(record = record, onClick = { onSelectRecord(record) })
                        }
                        val isFav = (record["isFavorite"] as? Boolean) ?: false
                        IconButton(onClick = { onToggleFavorite(id) }) {
                            Icon(Icons.Default.Star, contentDescription = "Favorite", tint = if (isFav) Color(0xFFFFB300) else Color.Gray, modifier = Modifier.graphicsLayer(alpha = if (isFav) 1f else 0.4f))
                        }
                        IconButton(onClick = { renameTarget = id; renameText = (record[HistoryActivity.KEY_LOCATION] as? String) ?: "" }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onDelete(id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.location_rename_title)) },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }) },
            confirmButton = { TextButton(onClick = { onRename(renameTarget!!, renameText); renameTarget = null }) { Text(stringResource(R.string.common_ok)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
fun HistoryItem(
    record: Map<String, Any>,
    onClick: () -> Unit
) {
    val name = (record[HistoryActivity.KEY_LOCATION] as? String) 
            ?: (record[LocationPickerViewModel.POI_NAME] as? String) 
            ?: "Unknown"
            
    val address = (record[HistoryActivity.KEY_TIME] as? String) 
        ?: (record[LocationPickerViewModel.POI_ADDRESS] as? String) 
        ?: ""
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        if (address.isNotEmpty()) {
            Text(text = address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

/**
 * 地图浮窗的组合函数。
 * 在悬浮窗中显示地图与搜索/传送等控制。
 */
@Composable
fun JoyStickMapOverlay(
    mapView: MapView,
    currentLocation: LatLng = LatLng(0.0, 0.0),
    onClose: () -> Unit,
    onWindowDrag: (Float, Float) -> Unit,
    onGo: () -> Unit,
    onBackToCurrent: () -> Unit,
    onSearch: (String) -> Unit,
    searchResults: List<Map<String, Any>>?,
    onSelectSearchResult: (Map<String, Any>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    val winW = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_WIDTH, "300") ?: "300").toIntOrNull() ?: 300
    }.dp
    val winH = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_HEIGHT, "500") ?: "500").toIntOrNull() ?: 500
    }.dp

    LaunchedEffect(Unit) {
        if (currentLocation.latitude != 0.0 || currentLocation.longitude != 0.0) {
            try {
                mapView.map.setMapStatus(MapStatusUpdateFactory.newLatLng(currentLocation))
                mapView.map.addOverlay(
                    MarkerOptions().position(currentLocation).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_position))
                )
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .width(winW)
            .height(winH)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(R.string.joystick_map_tips),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
                showSearchResults = it.isNotEmpty()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text(stringResource(R.string.app_search_tips)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // Map
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = onBackToCurrent,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_home_position), contentDescription = "Back to Current", modifier = Modifier.size(24.dp))
                }
                
                FloatingActionButton(
                    onClick = onGo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_position), contentDescription = "Go", modifier = Modifier.size(28.dp))
                }
            }
            
            // Search Results Overlay
            if (showSearchResults && !searchResults.isNullOrEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .heightIn(max = 250.dp)
                        .align(Alignment.TopCenter),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    LazyColumn {
                        items(searchResults) { item ->
                            HistoryItem(record = item, onClick = {
                                onSelectSearchResult(item)
                                showSearchResults = false
                                searchQuery = ""
                            })
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}
