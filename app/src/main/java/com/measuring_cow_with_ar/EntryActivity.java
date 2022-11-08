package com.measuring_cow_with_ar;


import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arcore.CameraPermissionHelper;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

public class EntryActivity extends AppCompatActivity {
    private Boolean installRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);
    }

    @Override
    protected void onResume() {
        super.onResume();
//        Logger.logStatus("onResume()");
        checkCanGo();
    }

    private void checkCanGo(){
        String message = null;
        Exception exception = null;

        try {
            switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                case INSTALL_REQUESTED:
                    installRequested = true;
                    return;
                case INSTALLED:
                    break;
            }

            // ARCore requires camera permissions to operate. If we did not yet obtain runtime
            // permission on Android M and above, now is a good time to ask the user for it.
            if (!CameraPermissionHelper.hasCameraPermission(this)) {
                CameraPermissionHelper.requestCameraPermission(this);
                return;
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            message = "Please install ARCore";
            exception = e;
        } catch (UnavailableDeviceNotCompatibleException e) {
            message = "This device does not support AR";
            exception = e;
        } catch (Exception e) {
            message = "Failed to create AR session";
            exception = e;
        }
        if (message != null) {
            showMessage(message);
            return;
        }else {
            // TODO go
            startActivity(new Intent(this, MeasuringCowWithArActivity.class));
        }
    }

    private void showMessage(String message){
        TextView tvResult = this.findViewById(R.id.tvResult);
        tvResult.setText(message);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
//        Logger.logStatus("onRequestPermissionsResult()")
        if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
            // Permission denied with checking "Do not ask again".
            CameraPermissionHelper.launchPermissionSettings(this);
        } else {

        }
    }
}
