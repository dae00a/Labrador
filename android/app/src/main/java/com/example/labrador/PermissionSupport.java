package com.example.labrador;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionSupport {
    private Context context;
    private Activity activity;

    private String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CALL_PHONE
    };

    private List<Object> permissionList;
    private final int MULTIPLE_PERMISSIONS = 101 ;

    public PermissionSupport(Activity _activity, Context _context) {
        this.activity = _activity;
        this.context = _context;
    }

    public boolean checkPermission() {
        int result;
        permissionList = new ArrayList<>();

        for (String pm : permissions) {
            result = ContextCompat.checkSelfPermission(context, pm);
            if(result != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(pm);
            }
        }

        return permissionList.isEmpty();
    }

    public  void requestPermission() {
        ActivityCompat.requestPermissions(activity, permissionList.toArray(new String[permissionList.size()]),
                MULTIPLE_PERMISSIONS);
    }

    public boolean permissionResult(int requestCode , @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MULTIPLE_PERMISSIONS && (grantResults.length > 0)) {
            for (int grantResult : grantResults) {
                if (grantResult == -1) {
                    return false;
                }
            }
        }
        return true;
    }
}
