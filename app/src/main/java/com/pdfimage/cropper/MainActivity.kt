package com.pdfimage.cropper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pdfimage.cropper.ui.AppTheme
import com.pdfimage.cropper.ui.AppViewModel
import com.pdfimage.cropper.ui.EditorScreen
import com.pdfimage.cropper.ui.ExportSettingsScreen
import com.pdfimage.cropper.ui.GalleryScreen
import com.pdfimage.cropper.ui.HelpScreen
import com.pdfimage.cropper.ui.HomeScreen
import com.pdfimage.cropper.ui.PagesScreen
import com.pdfimage.cropper.ui.ProgressScreen
import com.pdfimage.cropper.ui.ResultScreen
import com.pdfimage.cropper.ui.ReviewScreen
import com.pdfimage.cropper.ui.Screen
import com.pdfimage.cropper.ui.SettingsScreen

/** OpenDocument contract that also requests a persistable read grant (for the recent-PDFs list). */
class OpenPdfContract : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppTheme(vm.theme) { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { vm.openPdf(it) }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val picker = rememberLauncherForActivityResult(OpenPdfContract()) { uri -> if (uri != null) vm.openPdf(uri) }
    val launchPicker = { picker.launch(arrayOf("application/pdf")) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (vm.screen) {
            Screen.HOME -> HomeScreen(vm, launchPicker)
            Screen.PAGES -> PagesScreen(vm)
            Screen.EDITOR -> EditorScreen(vm)
            Screen.REVIEW -> ReviewScreen(vm)
            Screen.EXPORT -> ExportSettingsScreen(vm)
            Screen.PROGRESS -> ProgressScreen(vm)
            Screen.RESULT -> ResultScreen(vm, launchPicker)
            Screen.GALLERY -> GalleryScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
            Screen.HELP -> HelpScreen(vm)
        }
        if (vm.loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
    }
}
