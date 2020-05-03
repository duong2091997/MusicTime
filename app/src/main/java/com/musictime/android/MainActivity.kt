package com.musictime.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.musictime.android.databinding.ActivityMainBinding
import java.lang.IllegalArgumentException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 1
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)

        checkAndRequestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //TODO: Permission granted
                } else {
                    //TODO: Permission denied
                }
            }
            PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE -> {

            }
        }
    }

    private fun checkAndRequestPermission(permission: String) {
        if (!isPermissionGranted(permission)) {
            requestPermission(permission)
        }
    }

    private fun isPermissionGranted(permission: String) =
        (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED)

    private fun requestPermission(permission: String) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                permission)) {
            //TODO: Show explanation to the user *asynchronously*
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                getRequestCodeOf(permission))
        }
    }

    private fun getRequestCodeOf(permission: String) =
        when(permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE
            else -> throw IllegalArgumentException("Wrong permission")
        }
}
