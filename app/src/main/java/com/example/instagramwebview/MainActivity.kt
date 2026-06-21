package com.example.instagramwebview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingFileChooserParams: WebChromeClient.FileChooserParams? = null
    private var cameraImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        configureSystemBars()
        createWebView()
        setContentView(webView)
        registerBackNavigationForAndroid13Plus()

        if (savedInstanceState == null) {
            webView.loadUrl(LOCAL_PAGE_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true

                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // false means WebView handles navigation itself; http/https/file links do not open a browser.
                    return false
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    this@MainActivity.pendingFileChooserParams = fileChooserParams
                    requestPermissionsAndOpenFileChooser()
                    return true
                }
            }
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Status bar is transparent for true edge-to-edge; the HTML header/background is white below it.
        // With dark icons this visually behaves as a white Instagram-like status bar.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun registerBackNavigationForAndroid13Plus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                handleBackPressed()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPressed()
    }

    private fun handleBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    private fun requestPermissionsAndOpenFileChooser() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            openFileChooser(
                includeGallery = true,
                includeCamera = acceptsImages(pendingFileChooserParams)
            )
            return
        }

        val permissionsToRequest = buildList {
            if (!hasGalleryPermission()) {
                addAll(galleryReadPermissionsToRequest())
            }
            if (acceptsImages(pendingFileChooserParams) && !hasPermission(Manifest.permission.CAMERA)) {
                add(Manifest.permission.CAMERA)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            openFileChooser(
                includeGallery = true,
                includeCamera = acceptsImages(pendingFileChooserParams)
            )
        } else {
            requestPermissions(
                permissionsToRequest.toTypedArray(),
                REQUEST_FILE_CHOOSER_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_FILE_CHOOSER_PERMISSIONS) return

        val galleryGranted = hasGalleryPermission()
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        val includeCamera = acceptsImages(pendingFileChooserParams) && cameraGranted

        if (!galleryGranted && !includeCamera) {
            Toast.makeText(
                this,
                "Нужен доступ к фото или камере для загрузки файла",
                Toast.LENGTH_SHORT
            ).show()
            cancelFileChooser()
            return
        }

        openFileChooser(
            includeGallery = galleryGranted,
            includeCamera = includeCamera
        )
    }

    private fun openFileChooser(includeGallery: Boolean, includeCamera: Boolean) {
        val params = pendingFileChooserParams
        val cameraIntent = if (includeCamera) createCameraIntent() else null
        val galleryIntent = if (includeGallery) createGalleryIntent(params) else null

        val chooserIntent = when {
            galleryIntent != null && cameraIntent != null -> Intent.createChooser(
                galleryIntent,
                "Выберите фото"
            ).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }

            galleryIntent != null -> Intent.createChooser(galleryIntent, "Выберите фото")
            cameraIntent != null -> cameraIntent
            else -> null
        }

        if (chooserIntent == null) {
            cancelFileChooser()
            return
        }

        try {
            startActivityForResult(chooserIntent, REQUEST_FILE_CHOOSER)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Не найдено приложение для выбора файла", Toast.LENGTH_SHORT).show()
            cancelFileChooser()
        }
    }

    private fun createGalleryIntent(params: WebChromeClient.FileChooserParams?): Intent {
        val acceptTypes = params?.acceptTypes
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()

        val allowMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when (acceptTypes.size) {
                0 -> "image/*"
                1 -> acceptTypes.first()
                else -> "*/*"
            }
            if (acceptTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        }
    }

    private fun createCameraIntent(): Intent? {
        val imageFile = runCatching { createImageFile() }.getOrNull() ?: return null
        val uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            imageFile
        )
        cameraImageUri = uri

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(flags)
        }

        if (intent.resolveActivity(packageManager) == null) return null

        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { resolveInfo ->
            grantUriPermission(resolveInfo.activityInfo.packageName, uri, flags)
        }
        return intent
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.apply { mkdirs() }
            ?: filesDir
        return File.createTempFile("webview_capture_${timestamp}_", ".jpg", storageDir)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILE_CHOOSER) return

        val result = if (resultCode == RESULT_OK) {
            parseFileChooserResult(data)
        } else {
            null
        }

        filePathCallback?.onReceiveValue(result)
        filePathCallback = null
        pendingFileChooserParams = null
        cameraImageUri = null
    }

    private fun parseFileChooserResult(data: Intent?): Array<Uri>? {
        val selectedUris = mutableListOf<Uri>()

        data?.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index)?.uri?.let(selectedUris::add)
            }
        }

        data?.data?.let(selectedUris::add)

        if (selectedUris.isNotEmpty()) {
            return selectedUris.distinct().toTypedArray()
        }

        return cameraImageUri?.let { arrayOf(it) }
    }

    private fun cancelFileChooser() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        pendingFileChooserParams = null
        cameraImageUri = null
    }

    private fun galleryReadPermissionsToRequest(): List<String> = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> emptyList()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasGalleryPermission(): Boolean = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        else -> hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun acceptsImages(params: WebChromeClient.FileChooserParams?): Boolean {
        val acceptTypes = params?.acceptTypes
            ?.map { it.trim().lowercase(Locale.US) }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        return acceptTypes.isEmpty() || acceptTypes.any { type ->
            type == "*/*" || type == "image/*" || type.startsWith("image/") || type.endsWith(".jpg") ||
                type.endsWith(".jpeg") || type.endsWith(".png") || type.endsWith(".webp")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    override fun onDestroy() {
        cancelFileChooser()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val LOCAL_PAGE_URL = "file:///android_asset/Instagram.html"
        private const val REQUEST_FILE_CHOOSER = 10_001
        private const val REQUEST_FILE_CHOOSER_PERMISSIONS = 10_002
    }
}
