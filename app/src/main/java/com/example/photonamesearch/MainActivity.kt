package com.example.photonamesearch

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "photos", indices = [Index(value = ["name"])])
data class PhotoEntry(@PrimaryKey val uri: String, val name: String, val addedAt: Long = System.currentTimeMillis())

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE name LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY name COLLATE NOCASE")
    fun search(query: String): Flow<List<PhotoEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(entry: PhotoEntry)
    @Query("DELETE FROM photos WHERE uri = :uri") suspend fun delete(uri: String)
}

@Database(entities = [PhotoEntry::class], version = 1, exportSchema = false)
abstract class PhotoDb : RoomDatabase() {
    abstract fun dao(): PhotoDao
    companion object {
        @Volatile private var INSTANCE: PhotoDb? = null
        fun get(context: android.content.Context): PhotoDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, PhotoDb::class.java, "photo_names.db").build().also { INSTANCE = it }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = PhotoDb.get(this).dao()
        setContent { PhotoSearchScreen(dao) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSearchScreen(dao: PhotoDao) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var editEntry by remember { mutableStateOf<PhotoEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val pendingUris = remember { mutableStateListOf<Uri>() }
    val photos by dao.search(query).collectAsState(initial = emptyList())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            pendingUris.clear()
            pendingUris.addAll(uris)
            selectedUri = pendingUris.removeAt(0)
            name = ""
            editEntry = null
            showEditor = true
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Photo Name Search") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search by name") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { picker.launch("image/*") }) { Text("Add photos") }
                Text("${photos.size} matching photo(s)", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "Add photos and assign names to start." else "No photos found for ‘$query’. ")
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.uri }) { item ->
                        Card {
                            Column(Modifier.padding(8.dp)) {
                                AsyncImage(model = item.uri, contentDescription = item.name, modifier = Modifier.fillMaxWidth().height(130.dp))
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { editEntry = item; selectedUri = Uri.parse(item.uri); name = item.name; showEditor = true }) { Text("Edit") }
                                    TextButton(onClick = { (context as? ComponentActivity)?.lifecycleScope?.launch { dao.delete(item.uri) } }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showEditor && selectedUri != null) {
        AlertDialog(
            onDismissRequest = {
                showEditor = false; selectedUri = null; editEntry = null; pendingUris.clear()
            },
            title = { Text(if (editEntry == null) "Name this photo" else "Edit photo name") },
            text = {
                Column {
                    AsyncImage(model = selectedUri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name or keywords") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = selectedUri
                    if (uri != null && name.isNotBlank()) {
                        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        (context as? ComponentActivity)?.lifecycleScope?.launch { dao.save(PhotoEntry(uri.toString(), name.trim())) }
                    }
                    editEntry = null
                    if (pendingUris.isNotEmpty()) {
                        selectedUri = pendingUris.removeAt(0)
                        name = ""
                    } else {
                        showEditor = false; selectedUri = null
                    }
                }) { Text("Save & next") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false; selectedUri = null; editEntry = null; pendingUris.clear() }) { Text("Cancel") } }
        )
    }
}
