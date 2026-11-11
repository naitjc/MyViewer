package com.chen.myviewer.ui

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chen.myviewer.source.SourcePreference
import com.chen.myviewer.source.SourceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
internal fun SourceSettingsScreen(repository: SourceRepository, sourceId: String, dismiss: () -> Unit) {
    var settings by remember { mutableStateOf<List<SourcePreference>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sourceId) {
        try { settings = repository.settings(sourceId) }
        catch (exception: CancellationException) { throw exception }
        catch (exception: Exception) { error = exception.message }
        finally { loading = false }
    }
    fun update(setting: SourcePreference, value: Any) {
        val replacement = setting.copy(value = value)
        scope.launch {
            try {
                repository.setPreference(sourceId, replacement)
                settings = settings.map { if (it.key == setting.key) replacement else it }
                error = null
            } catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) { error = exception.message }
        }
    }
    BackHandler(onBack = dismiss)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("漫画源设置", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = dismiss) { Text("完成") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            settings.forEach { setting ->
                when (setting.kind) {
                    "switch" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(setting.title, Modifier.weight(1f))
                        Switch(setting.value as Boolean, { update(setting, it) })
                    }
                    "text" -> {
                        var value by remember(setting.key) { mutableStateOf(setting.value.toString()) }
                        OutlinedTextField(value, { value = it }, label = { Text(setting.title) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        TextButton(onClick = { update(setting, value) }, enabled = value != setting.value.toString()) { Text("保存${setting.title}") }
                    }
                    "select", "multi-select" -> {
                        var expanded by remember { mutableStateOf(false) }
                        Text(setting.title, style = MaterialTheme.typography.titleSmall)
                        if (setting.kind == "select") Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(setting.choices.firstOrNull { it.second == setting.value }?.first ?: setting.value.toString())
                            }
                            DropdownMenu(expanded, { expanded = false }) { setting.choices.forEach { (label, value) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; update(setting, value) })
                            } }
                        } else {
                            val selected = (setting.value as JSONArray).let { array -> List(array.length()) { array.getString(it) } }
                            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(selected.joinToString(" / ").ifBlank { "选择语言" }) }
                            if (expanded) Column { setting.choices.forEach { (label, value) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(value.toString() in selected, { enabled -> update(setting, JSONArray(if (enabled) selected + value.toString() else selected - value.toString())) })
                                    Text(label)
                                }
                            } }
                        }
                    }
                }
            }
        }
    }
}
