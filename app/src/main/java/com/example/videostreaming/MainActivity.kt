package com.example.videostreaming

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.videostreaming.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import kotlin.Exception


class MainActivity :  AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private val TAG = "MainActivity"
    //private val REQUEST_CODE = 1000
    private var mScreenDensity = 0
    private var mProjectionManager: MediaProjectionManager? = null
    private val DISPLAY_WIDTH = 720
    private val DISPLAY_HEIGHT = 1280
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mMediaProjectionCallback: MediaProjection.Callback? = null
    private lateinit var mToggleButton: ToggleButton
    private var mMediaRecorder: MediaRecorder? = null
    private val ORIENTATIONS = SparseIntArray()
    private val REQUEST_PERMISSIONS = 10
    var result: ActivityResultLauncher<Intent>? = null

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mToggleButton = binding.toggle

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mMediaRecorder = MediaRecorder()
        mProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        result = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ActivityResultCallback {

            if(it.resultCode!=Activity.RESULT_OK)
            {
                Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show()
                mToggleButton.setChecked(false)
                return@ActivityResultCallback
            }
            mMediaProjectionCallback = MediaProjectionCallback()
            mMediaProjection = mProjectionManager?.getMediaProjection(it.resultCode, it.data!!)
            mMediaProjection?.registerCallback(mMediaProjectionCallback, null)
            mVirtualDisplay = createVirtualDisplay()
            mMediaRecorder!!.start()
        })

        binding.toggle.setOnClickListener(View.OnClickListener {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO))
                {
                    binding.toggle.isChecked = false
                    Snackbar.make(findViewById(android.R.id.content), "Please enable Microphone and Storage permissions.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("ENABLE") { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSIONS) }.show()
                }
                else
                {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSIONS)
                }
            }
            else
            {
                onToggleScreenShare()
            }

        })

    }


    private fun onToggleScreenShare() {

        if (binding.toggle.isChecked)
        {
            initRecorder()
            shareScreen()
        }
        else
        {
            try {
                mMediaRecorder?.stop()
                mMediaRecorder?.reset()
            } catch (stopException: Exception) {
                Log.d("STOPPED",stopException.printStackTrace().toString())
            }

            Log.v("LOG", "Stopping Recording")
            stopScreenSharing()
        }
    }

    private fun initRecorder() {
        try
        {
            mMediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            mMediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mMediaRecorder?.setOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/video.mp4")
            mMediaRecorder?.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mMediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mMediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mMediaRecorder?.setVideoEncodingBitRate(512 * 1000)
            mMediaRecorder?.setVideoFrameRate(30)
            val rotation = windowManager.defaultDisplay.rotation
            val orientation: Int = ORIENTATIONS.get(rotation + 90)
            mMediaRecorder?.setOrientationHint(orientation)
            mMediaRecorder?.prepare()
        }
        catch (e: IOException)
        {
            e.printStackTrace()
        }
    }

    private inner class MediaProjectionCallback : MediaProjection.Callback()

    private fun shareScreen() {
        if (mMediaProjection == null)
        {
            result!!.launch(mProjectionManager?.createScreenCaptureIntent())
            return
        }
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder?.start()
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        return mMediaProjection!!.createVirtualDisplay("MainActivity", DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder!!.surface, null /*Callbacks*/, null /*Handler*/)
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay == null)
        {
            return
        }
        mVirtualDisplay?.release()
        //mMediaRecorder.release(); //If used: mMediaRecorder object cannot be reused again
        destroyMediaProjection()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyMediaProjection()
    }

    private fun destroyMediaProjection() {
        if (mMediaProjection != null)
        {
            mMediaProjection!!.unregisterCallback(mMediaProjectionCallback)
            mMediaProjection!!.stop()
            mMediaProjection = null
        }
        Log.i(TAG, "MediaProjection Stopped")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED)
                {
                    onToggleScreenShare()
                }
                else
                {
                    mToggleButton.isChecked = false
                    Snackbar.make(findViewById(androidx.appcompat.R.id.content), "Please enable Microphone and Storage permissions.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("ENABLE") {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.addCategory(Intent.CATEGORY_DEFAULT)
                        intent.data = Uri.parse("package:$packageName")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        startActivity(intent)
                    }.show()
                }
                return
            }
        }
    }



}


fun oldcode()
    {
//        binding.webView.settings.setJavaScriptEnabled(true)
//
//        binding.btnLoad.setOnClickListener(View.OnClickListener {
//            binding.webView.loadUrl(binding.etUrl.text.toString())
//        })
//
//        binding.webView.webViewClient = object : WebViewClient() {
//            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
//                view?.loadUrl(url)
//                return true
//            }
//        }
//
//        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
//        "http://192.168.1.12:8080/video"
//
//
//        override fun onCreateOptionsMenu(menu: Menu): Boolean {
//            val inflater: MenuInflater = menuInflater
//            inflater.inflate(R.menu.menu, menu)
//            return true
//        }
//
//        override fun onOptionsItemSelected(item: MenuItem): Boolean {
//            // Handle item selection
//            return when (item.itemId) {
//                R.id.menu_record -> {
//                    Toast.makeText(applicationContext, "clicked "+item.title, Toast.LENGTH_SHORT).show()
//                    true
//                }
//                else -> super.onOptionsItemSelected(item)
//            }
//        }
    }


