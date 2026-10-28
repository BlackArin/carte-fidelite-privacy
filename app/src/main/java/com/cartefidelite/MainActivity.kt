package com.cartefidelite

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.oned.Code128Writer
import java.io.File
import kotlin.random.Random

private val Violet = Color(0xFF6C4DFF)
private val MarbleBase = Color(0xFFF2F1EF)

private val Gradients = listOf(
    listOf(Color(0xFF6C4DFF), Color(0xFFB36BFF)),
    listOf(Color(0xFFFF5F6D), Color(0xFFFFB36B)),
    listOf(Color(0xFF00C6A7), Color(0xFF1E88E5)),
    listOf(Color(0xFFFF4ECD), Color(0xFF7B61FF)),
    listOf(Color(0xFF11998E), Color(0xFF9BE15D)),
)

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Violet, background = MarbleBase, surface = Color.White)
            ) { App() }
        }
    }
}

@Composable
fun MarbleBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFFD9D6D2), Color(0xFFBDB9B4), Color(0xFFCFCCC7), Color(0xFFA9A59F)),
                start = Offset.Zero, end = Offset(size.width, size.height)
            )
        )
        val rnd = Random(42)
        repeat(14) { n ->
            val path = Path()
            var x = rnd.nextFloat() * size.width
            var y = -20f
            path.moveTo(x, y)
            val steps = 7
            repeat(steps) {
                val nx = (x + (rnd.nextFloat() - 0.5f) * size.width * 0.45f).coerceIn(-50f, size.width + 50f)
                val ny = y + size.height / steps + 40f
                path.cubicTo(
                    x + (rnd.nextFloat() - 0.5f) * 160f, y + (ny - y) * 0.3f,
                    nx + (rnd.nextFloat() - 0.5f) * 160f, y + (ny - y) * 0.7f,
                    nx, ny
                )
                x = nx; y = ny
            }
            val major = n < 4
            drawPath(
                path,
                Color(0xFF3F3B38).copy(alpha = if (major) 0.35f else 0.16f),
                style = Stroke(width = if (major) 3f else 1.3f)
            )
        }
    }
}

@Composable
fun App(vm: CardsViewModel = viewModel()) {
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var pickerCardId by remember { mutableStateOf<String?>(null) }
    var reorder by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val current = vm.cards.firstOrNull { it.id == selected }

    Box(Modifier.fillMaxSize()) {
        MarbleBackground(Modifier.matchParentSize())
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.my_cards), fontSize = 34.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, letterSpacing = 1.sp, color = Color(0xFF1C1B1F))
                        Text(stringResource(R.string.subtitle), fontFamily = FontFamily.SansSerif, fontSize = 13.sp, letterSpacing = 0.5.sp, color = Color(0xFF55514D))
                    }
                    IconButton(onClick = { showLanguage = true }) {
                        Icon(Icons.Default.Language, stringResource(R.string.change_language), tint = Color(0xFF3F3B38))
                    }
                    IconButton(onClick = { reorder = !reorder }) {
                        Icon(Icons.Default.SwapVert, stringResource(R.string.change_order), tint = if (reorder) Violet else Color(0xFF3F3B38))
                    }
                    IconButton(onClick = { vm.toggleGrid() }) {
                        Icon(
                            if (vm.grid) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            stringResource(R.string.change_view), tint = Color(0xFF3F3B38)
                        )
                    }
                }
            },
            bottomBar = {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        "Created by Arin",
                        modifier = Modifier.align(Alignment.CenterStart),
                        fontSize = 11.sp, fontStyle = FontStyle.Italic, letterSpacing = 0.5.sp,
                        color = Color(0xFF3F3B38).copy(alpha = 0.55f)
                    )
                    ExtendedFloatingActionButton(
                        onClick = { showAdd = true },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        containerColor = Violet,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text(stringResource(R.string.new_card)) }
                    )
                }
            }
        ) { pad ->
            if (vm.cards.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💳", fontSize = 64.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.no_cards), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.tap_new_card), color = Color(0xFF55514D))
                    }
                }
            } else {
                val cols = if (vm.grid) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(vm.cards, key = { it.id }) { card ->
                        Box {
                            CardItem(card, compact = vm.grid) { if (!reorder) selected = card.id }
                            if (reorder) {
                                Row(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                                        .clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                                ) {
                                    IconButton({ vm.move(card.id, -1) }, Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ChevronLeft, stringResource(R.string.move_before), tint = Violet)
                                    }
                                    IconButton({ vm.move(card.id, 1) }, Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ChevronRight, stringResource(R.string.move_after), tint = Violet)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLanguage) LanguageDialog { showLanguage = false }

    if (showAdd) AddDialog(onDismiss = { showAdd = false }) { shop, color, code, format ->
        pickerCardId = vm.add(shop, color, code, format)
        showAdd = false
    }

    val pickerCard = vm.cards.firstOrNull { it.id == pickerCardId }
    if (pickerCard != null) ImagePickerDialog(pickerCard, vm) { pickerCardId = null }

    if (current != null) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            Detail(current, vm, onChangeImage = { pickerCardId = current.id }) { selected = null }
        }
    }
}

@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = remember { LocaleHelper.current(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.choose_language), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                LocaleHelper.languages.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onDismiss()
                                if (code != selected) {
                                    LocaleHelper.save(context, code)
                                    (context as? Activity)?.recreate()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, Modifier.weight(1f), fontWeight = if (code == selected) FontWeight.Bold else FontWeight.Normal)
                        if (code == selected) Icon(Icons.Default.Check, null, tint = Violet)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ImagePickerDialog(card: LoyaltyCard, vm: CardsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var images by remember(card.id) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(card.id) { images = vm.findImages(card.shop) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.choose_image), fontWeight = FontWeight.Bold) },
        text = {
            val list = images
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.searching), fontSize = 13.sp)
                    }
                }
                list.isEmpty() -> Text(stringResource(R.string.no_image_found, card.shop))
                else -> Column {
                    Text(stringResource(R.string.touch_image), fontSize = 12.sp, color = Color(0xFF55514D))
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(380.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list) { url ->
                            Box(
                                Modifier.aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)).background(Color.White)
                                    .border(1.dp, Color(0xFFDAD5F0), RoundedCornerShape(12.dp))
                                    .clickable { vm.setImage(card.id, url); onDismiss() }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(url)
                                        .addHeader("User-Agent", "CarteFidelite/1.01 (Android)")
                                        .crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.keep_image)) } }
    )
}

fun LoyaltyCard.imageModel(): Any? =
    if (imagePath.isNotEmpty() && File(imagePath).exists()) File(imagePath) else null

fun scannedFormatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    Barcode.FORMAT_PDF417 -> "PDF_417"
    else -> "CODE_128"
}

@Composable
fun BarcodeView(code: String, modifier: Modifier = Modifier, format: String = "CODE_128") {
    val matrix = remember(code, format) {
        val fmt = runCatching { BarcodeFormat.valueOf(format) }.getOrDefault(BarcodeFormat.CODE_128)
        val is2D = fmt == BarcodeFormat.QR_CODE || fmt == BarcodeFormat.AZTEC ||
            fmt == BarcodeFormat.DATA_MATRIX || fmt == BarcodeFormat.PDF_417
        val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
        val m = runCatching { MultiFormatWriter().encode(code, fmt, 0, 0, hints) }.getOrNull()
            ?: runCatching { Code128Writer().encode(code, BarcodeFormat.CODE_128, 0, 1) }.getOrNull()
        m to (is2D && m != null && m.height > 1)
    }
    val (bits, twoD) = matrix
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val canvasMod = if (twoD && bits != null && format != "PDF_417")
            Modifier.size(200.dp) else Modifier.fillMaxWidth().height(if (twoD) 100.dp else 80.dp)
        Canvas(canvasMod) {
            bits ?: return@Canvas
            val w = size.width / bits.width
            val h = if (twoD) size.height / bits.height else size.height
            for (y in 0 until (if (twoD) bits.height else 1)) {
                for (x in 0 until bits.width) {
                    if (bits.get(x, y)) {
                        drawRect(Color.Black, Offset(x * w, y * h), Size(w + 0.5f, h + 0.5f))
                    }
                }
            }
        }
        Text(code, letterSpacing = 3.sp, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CardItem(card: LoyaltyCard, compact: Boolean = false, onClick: () -> Unit) {
    val image = card.imageModel()
    var imageLoadFailed by remember(card.imagePath) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.586f)
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(10.dp)
            .background(Brush.horizontalGradient(Gradients[card.colorIndex % Gradients.size])))
        Column(
            Modifier.matchParentSize().padding(
                start = if (compact) 12.dp else 24.dp, end = if (compact) 12.dp else 24.dp,
                top = if (compact) 12.dp else 20.dp, bottom = if (compact) 14.dp else 26.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (image == null || imageLoadFailed) {
                    Text(
                        card.shop.trim().take(1).uppercase().ifEmpty { "?" },
                        fontSize = if (compact) 32.sp else 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Violet
                    )
                }
                if (image != null && !imageLoadFailed) {
                    AsyncImage(
                        model = image, contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                        onError = { imageLoadFailed = true }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                card.shop, fontSize = if (compact) 13.sp else 18.sp, fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif, letterSpacing = 0.5.sp, color = Color(0xFF1C1B1F),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.matchParentSize().border(1.dp, Color(0x22000000), RoundedCornerShape(18.dp)))
    }
}

@Composable
fun Detail(card: LoyaltyCard, vm: CardsViewModel, onChangeImage: () -> Unit, close: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename_card)) },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = { vm.rename(card.id, newName); renaming = false }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CardItem(card) {}
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                .border(1.dp, Color(0xFFDAD5F0), RoundedCornerShape(16.dp)).padding(16.dp)
        ) { BarcodeView(card.barcode, format = card.format) }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onChangeImage) {
            Icon(Icons.Default.Image, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.change_card_image))
        }
        TextButton(onClick = { newName = card.shop; renaming = true }) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.rename_card))
        }
        TextButton(onClick = { vm.delete(card.id); close() }) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.delete_card), color = Color(0xFFE53935))
        }
    }
}

@Composable
fun AddDialog(onDismiss: () -> Unit, onAdd: (String, Int, String, String) -> Unit) {
    var shop by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("CODE_128") }
    val color = 0
    var scanError by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scanError = null
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                BarcodeScanning.getClient().process(image)
                    .addOnSuccessListener { codes ->
                        val found = codes.firstOrNull { !it.rawValue.isNullOrBlank() }
                        if (found == null) {
                            scanError = R.string.err_no_code
                        } else {
                            barcode = found.rawValue.orEmpty()
                            format = scannedFormatName(found.format)
                        }
                    }
                    .addOnFailureListener { scanError = R.string.err_unreadable_image }
            }.onFailure { scanError = R.string.err_unreadable_image }
        }
    }
    val scan = {
        scanError = null
        GmsBarcodeScanning.getClient(context)
            .startScan()
            .addOnSuccessListener { result ->
                val value = result.rawValue
                if (value.isNullOrBlank()) {
                    scanError = R.string.err_unreadable_code
                } else {
                    barcode = value
                    format = scannedFormatName(result.format)
                }
            }
            .addOnFailureListener { e ->
                if (e !is MlKitException || e.errorCode != MlKitException.CODE_SCANNER_CANCELLED) {
                    scanError = R.string.err_scan_failed
                }
            }
        Unit
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.new_card), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(shop, { shop = it }, label = { Text(stringResource(R.string.shop)) }, singleLine = true)
                OutlinedButton(onClick = scan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_card))
                }
                OutlinedButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pick_code_image))
                }
                scanError?.let { Text(stringResource(it), color = Color(0xFFE53935), fontSize = 12.sp) }
                OutlinedTextField(
                    barcode, { barcode = it.filter { c -> c.code in 32..126 }; format = "CODE_128" },
                    label = { Text(stringResource(R.string.barcode_auto)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                Text(stringResource(R.string.auto_image_hint), fontSize = 12.sp, color = Color(0xFF55514D))
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(shop, color, barcode, format) },             enabled = shop.isNotBlank()) { Text(stringResource(R.string.create)) }
        },
                    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
