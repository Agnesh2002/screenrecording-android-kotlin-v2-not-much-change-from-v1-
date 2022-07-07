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
import android.util.SparseIntArray
import android.view.Surface
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


class MainActivity :  AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mScreenDensity:Int? = null
    private lateinit var mProjectionManager: MediaProjectionManager
    private var mMediaProjection: MediaProjection? = null
    private lateinit var mVirtualDisplay: VirtualDisplay
    private lateinit var mMediaProjectionCallback: MediaProjection.Callback
    private lateinit var mToggleButton: ToggleButton
    private lateinit var mMediaRecorder: MediaRecorder
    private lateinit var result: ActivityResultLauncher<Intent>

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

//        FLOW
//        button clicked
//        Checks for permission
//        inside toggleScreenShare
//        inside initRecorder
//        inside shareScreen
//        inside registerActivity (result)
//        inside createVirtualDisplay
//        completed registerActivity
//        button clicked
//        permission OK
//        inside toggleScreenShare
//        inside stopScreenSharing
//        inside destroyMediaProjection


        mToggleButton = binding.toggle

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mMediaRecorder = MediaRecorder()
        mProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mToggleButton.setOnClickListener{

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO))
                {
                    mToggleButton.isChecked = false
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

        }

        result = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ActivityResultCallback {

            if(it.resultCode!=Activity.RESULT_OK)
            {
                Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show()
                mToggleButton.isChecked = false
                return@ActivityResultCallback
            }
            mMediaProjectionCallback = MediaProjectionCallback()
            mMediaProjection = mProjectionManager.getMediaProjection(it.resultCode, it.data!!)
            mMediaProjection!!.registerCallback(mMediaProjectionCallback, null)
            mVirtualDisplay = createVirtualDisplay()
            mMediaRecorder.start()

        })

    }

    private fun onToggleScreenShare() {

        if (mToggleButton.isChecked) {
            initRecorder()
            shareScreen()
        }
        else {
            mMediaRecorder.stop()
            mMediaRecorder.reset()
            stopScreenSharing()
        }
    }

    private fun initRecorder() {

        try
        {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mMediaRecorder.setOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/video.mp4")
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mMediaRecorder.setVideoEncodingBitRate(512 * 1000)
            mMediaRecorder.setVideoFrameRate(30)
            val rotation = windowManager.defaultDisplay.rotation
            val orientation: Int = ORIENTATIONS.get(rotation + 90)
            mMediaRecorder.setOrientationHint(orientation)
            mMediaRecorder.prepare()
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
            result.launch(mProjectionManager.createScreenCaptureIntent())
            return
        }
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder.start()
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        return mMediaProjection!!.createVirtualDisplay("MainActivity", DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity!!, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder.surface, null /*Callbacks*/, null /*Handler*/)
    }

    private fun stopScreenSharing() {

        mVirtualDisplay.release()
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

    companion object{
        const val DISPLAY_WIDTH = 720
        const val DISPLAY_HEIGHT = 1280
        const val REQUEST_PERMISSIONS = 10
        val ORIENTATIONS = SparseIntArray()
    }

}
