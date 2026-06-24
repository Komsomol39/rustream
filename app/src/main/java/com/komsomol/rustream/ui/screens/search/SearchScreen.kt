package com.komsomol.rustream.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState  by viewModel.uiState.collectAsState()
    val query    by viewModel.query.collectAsState()
    val category by viewModel.category.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск торрентов...") },
            trailingIcon = {
                IconButton(onClick = viewModel::search) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                }
            },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContentCategory.values().forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { viewModel.onCategoryChange(cat) },
                    label = { Text(cat.displayName) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (val state = uiState) {
            is SearchUiState.Idle -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Введите запрос для поиска",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is SearchUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Поиск...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is SearchUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is SearchUiState.Success -> {
                // Группируем по источнику для статистики
                val bySource = state.results.groupBy { it.source }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bySource.forEach { (src, list) ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("${src.displayName}: ${list.size}") }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results) { result ->
                        SearchResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(result: SearchResult) {
    val sourceColor = when (result.source) {
        SearchSource.RUTOR     -> MaterialTheme.colorScheme.primary
        SearchSource.RUTRACKER -> MaterialTheme.colorScheme.tertiary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Бейдж источника
                Surface(
                    color = sourceColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = result.source.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = sourceColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = formatSize(result.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("▲ ${result.seeders}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
                Text("▼ ${result.leechers}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
                if (result.uploadDate.isNotEmpty()) {
                    Text(result.uploadDate, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024          -> "%.1f KB".format(bytes / 1024.0)
        else                   -> "$bytes B"
    }
}
