package co.chinho.readabilityreader.ui.saved

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.chinho.readabilityreader.ui.components.ArticleRow
import co.chinho.readabilityreader.ui.theme.LocalEInkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedArticlesScreen(
    onArticleClick: (Long) -> Unit,
    onCloseClick: (() -> Unit)? = null,
    viewModel: SavedArticlesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEInk = LocalEInkMode.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Articles") },
                actions = {
                    if (onCloseClick != null) {
                        IconButton(onClick = onCloseClick) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close saved articles",
                            )
                        }
                    }
                },
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = uiState) {
                is SavedArticlesUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isEInk) {
                            Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is SavedArticlesUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is SavedArticlesUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(state.articles, key = { it.id }) { article ->
                                ArticleRow(
                                    article = article,
                                    onClick = { onArticleClick(article.id) },
                                    showFeedMetadata = true,
                                )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
