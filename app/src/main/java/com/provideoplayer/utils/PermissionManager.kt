package com.provideoplayer.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Manages storage permission requests for different Android versions
 */
object PermissionManager {
    
    const val PERMISSION_REQUEST_CODE = 1001
    
    /**
     * Get the required permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            // Android 13+ (API 33+) - Use READ_MEDIA_VIDEO
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            }
            // Android 10-12 (API 29-32) - Use READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            // Below Android 10 - Use both READ and WRITE
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasStoragePermission(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Request storage permissions
     */
    fun requestStoragePermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            getRequiredPermissions(),
            PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * Request storage permission using ActivityResultLauncher
     */
    fun requestStoragePermission(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(getRequiredPermissions())
    }
    
    /**
     * Check if permission was permanently denied (Don't ask again)
     */
    fun isPermissionPermanentlyDenied(activity: Activity): Boolean {
        return getRequiredPermissions().any { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Open app settings to manually grant permissions
     */
    fun openAppSettings(context: Context) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(this)
        }
    }
    
    /**
     * Handle permission result
     */
    fun handlePermissionResult(
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onGranted()
        } else {
            onDenied()
        }
    }
}
