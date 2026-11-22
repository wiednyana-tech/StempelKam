package com.wiednyana.stempelkam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.location.Geocoder
import android.location.Location
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: WatermarkView
    private lateinit var flashView: android.view.View
    private lateinit var tvGps: TextView
    private lateinit var btnFlash: TextView
    private lateinit var btnStyle: TextView
    private lateinit var btnGallery: ImageView
    private lateinit var btnShutter: ImageButton

    private lateinit var prefs: SharedPreferences
    private lateinit var fused: FusedLocationProviderClient
    private val bg: ExecutorService = Executors.newSingleThreadExecutor()
    private val geoExec: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val sound = MediaActionSound()

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private val data = WatermarkData()
    private var currentLocation: Location? = null
    private var lastGeocoded: Location? = null
    private var lastUri: Uri? = null
    private var locationOn = false
    private var busy = false

    private val ticker = object : Runnable {
        override fun run() {
            data.time = Date()
            overlay.invalidate()
            handler.postDelayed(this, 1000L)
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (granted(Manifest.permission.CAMERA)) startCamera()
            else toast("Izin kamera diperlukan")
            if (hasLocationPerm()) startLocation()
            else toast("Tanpa izin lokasi, alamat & koordinat tidak tampil")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        flashView = findViewById(R.id.flashView)
        tvGps = findViewById(R.id.tvGps)
        btnFlash = findViewById(R.id.btnFlash)
        btnStyle = findViewById(R.id.btnStyle)
        btnGallery = findViewById(R.id.btnGallery)
        btnShutter = findViewById(R.id.btnShutter)
        btnGallery.clipToOutline = true
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        prefs = getSharedPreferences("stempel", MODE_PRIVATE)
        loadPrefs()
        overlay.data = data
        btnStyle.text = WatermarkRenderer.STYLE_NAMES[overlay.style]

        fused = LocationServices.getFusedLocationProviderClient(this)
        sound.load(MediaActionSound.SHUTTER_CLICK)

        btnShutter.setOnClickListener { takePhoto() }
        findViewById<TextView>(R.id.btnFlip).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        btnFlash.setOnClickListener { cycleFlash() }
        btnStyle.setOnClickListener {
            overlay.style = (overlay.style + 1) % WatermarkRenderer.STYLE_NAMES.size
            btnStyle.text = WatermarkRenderer.STYLE_NAMES[overlay.style]
            savePrefs()
            overlay.invalidate()
        }
        findViewById<TextView>(R.id.btnEdit).setOnClickListener { showEditDialog() }
        btnGallery.setOnClickListener { openLastPhoto() }

        if (requiredPerms().all { granted(it) }) startCamera()
        else permLauncher.launch(requiredPerms())
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
        if (hasLocationPerm()) startLocation()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        stopLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        bg.shutdown()
        geoExec.shutdown()
        sound.release()
    }

    // ---------------- Kamera ----------------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
                imageCapture = capture
            } catch (e: Exception) {
                toast("Kamera gagal dibuka: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun cycleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        btnFlash.text = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> "⚡ Nyala"
            ImageCapture.FLASH_MODE_AUTO -> "⚡ Auto"
            else -> "⚡ Mati"
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        if (busy) return
        busy = true

        val snapshot = data.copy(time = Date())
        val loc = currentLocation
        val style = overlay.style
        val front = lensFacing == CameraSelector.LENS_FACING_FRONT

        sound.play(MediaActionSound.SHUTTER_CLICK)
        flashView.alpha = 0.8f
        flashView.animate().alpha(0f).setDuration(250).start()

        capture.takePicture(bg, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    val src = image.toBitmap()
                    image.close()

                    val m = Matrix()
                    m.postRotate(rotation.toFloat())
                    if (front) m.postScale(-1f, 1f)
                    var bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                    if (!bmp.isMutable) bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)

                    WatermarkRenderer.draw(Canvas(bmp), bmp.width, bmp.height, snapshot, style)
                    val uri = saveToGallery(bmp, snapshot.time, loc)
                    val thumb = Bitmap.createScaledBitmap(
                        bmp, 160, (160f * bmp.height / bmp.width).toInt(), true
                    )
                    runOnUiThread {
                        busy = false
                        if (uri != null) {
                            lastUri = uri
                            btnGallery.setImageBitmap(thumb)
                            toast("Foto tersimpan di Galeri › StempelKam")
                        } else {
                            toast("Gagal menyimpan foto")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { busy = false; toast("Error: ${e.message}") }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { busy = false; toast("Gagal mengambil foto: ${exception.message}") }
            }
        })
    }

    private fun saveToGallery(bmp: Bitmap, time: Date, loc: Location?): Uri? {
        val name = "STK_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(time) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, time.time)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StempelKam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }

        // Tulis EXIF (waktu & GPS) supaya data lokasi juga tersimpan di metadata file.
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(time)
                )
                if (loc != null) exif.setGpsInfo(loc)
                exif.saveAttributes()
            }
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun openLastPhoto() {
        val uri = lastUri
        if (uri == null) {
            toast("Belum ada foto")
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: ActivityNotFoundException) {
            toast("Tidak ada aplikasi galeri")
        }
    }

    // ---------------- Lokasi ----------------

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            currentLocation = loc
            data.hasLocation = true
            data.lat = loc.latitude
            data.lon = loc.longitude
            data.altitude = if (loc.hasAltitude()) loc.altitude else null
            data.accuracy = loc.accuracy
            tvGps.text = String.format(Locale.US, "GPS ±%.0f m", loc.accuracy)

            val prev = lastGeocoded
            if (prev == null || prev.distanceTo(loc) > 25f) {
                lastGeocoded = loc
                reverseGeocode(loc)
            }
            overlay.invalidate()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (locationOn || !hasLocationPerm()) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        locationOn = true
    }

    private fun stopLocation() {
        if (!locationOn) return
        fused.removeLocationUpdates(locationCallback)
        locationOn = false
    }

    private fun reverseGeocode(loc: Location) {
        if (!Geocoder.isPresent()) return
        geoExec.execute {
            try {
                @Suppress("DEPRECATION")
                val list = Geocoder(this, Locale("id", "ID")).getFromLocation(loc.latitude, loc.longitude, 1)
                val addr = list?.firstOrNull()?.getAddressLine(0)
                if (!addr.isNullOrBlank()) {
                    runOnUiThread {
                        data.address = addr
                        overlay.invalidate()
                    }
                }
            } catch (_: Exception) {
                // Tanpa internet: alamat tidak tersedia, koordinat tetap tampil.
                runOnUiThread { lastGeocoded = null }
            }
        }
    }

    // ---------------- Pengaturan ----------------

    private fun showEditDialog() {
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val etNote = EditText(this).apply {
            hint = "Catatan / nama proyek / judul"
            setText(data.note)
            isSingleLine = true
        }
        val cbNote = CheckBox(this).apply { text = "Tampilkan catatan"; isChecked = data.showNote }
        val cbAddr = CheckBox(this).apply { text = "Tampilkan alamat"; isChecked = data.showAddress }
        val cbCoord = CheckBox(this).apply { text = "Tampilkan koordinat"; isChecked = data.showCoords }
        val cbAlt = CheckBox(this).apply { text = "Tampilkan ketinggian"; isChecked = data.showAltitude }
        listOf(etNote, cbNote, cbAddr, cbCoord, cbAlt).forEach { box.addView(it) }

        AlertDialog.Builder(this)
            .setTitle("Atur Watermark")
            .setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                data.note = etNote.text.toString().trim()
                data.showNote = cbNote.isChecked
                data.showAddress = cbAddr.isChecked
                data.showCoords = cbCoord.isChecked
                data.showAltitude = cbAlt.isChecked
                savePrefs()
                overlay.invalidate()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun loadPrefs() {
        data.note = prefs.getString("note", "") ?: ""
        data.showNote = prefs.getBoolean("showNote", true)
        data.showAddress = prefs.getBoolean("showAddress", true)
        data.showCoords = prefs.getBoolean("showCoords", true)
        data.showAltitude = prefs.getBoolean("showAltitude", false)
        overlay.style = prefs.getInt("style", 0)
    }

    private fun savePrefs() {
        prefs.edit()
            .putString("note", data.note)
            .putBoolean("showNote", data.showNote)
            .putBoolean("showAddress", data.showAddress)
            .putBoolean("showCoords", data.showCoords)
            .putBoolean("showAltitude", data.showAltitude)
            .putInt("style", overlay.style)
            .apply()
    }

    // ---------------- Izin ----------------

    private fun requiredPerms(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT <= 28) list += Manifest.permission.WRITE_EXTERNAL_STORAGE
        return list.toTypedArray()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPerm() =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
