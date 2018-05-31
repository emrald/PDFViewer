/*
 * FileUtils.java
 *   PSPDFKit
 *
 *   Copyright © 2014-2017 PSPDFKit GmbH. All rights reserved.
 *
 *   THIS SOURCE CODE AND ANY ACCOMPANYING DOCUMENTATION ARE PROTECTED BY INTERNATIONAL COPYRIGHT LAW
 *   AND MAY NOT BE RESOLD OR REDISTRIBUTED. USAGE IS BOUND TO THE PSPDFKIT LICENSE AGREEMENT.
 *   UNAUTHORIZED REPRODUCTION OR DISTRIBUTION IS SUBJECT TO CIVIL AND CRIMINAL PENALTIES.
 *   This notice may not be removed from this file.
 */

package com.trivedi.pdfviewer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewTreeObserver;

public class Utils {
    /**
     * Requests read and write permissions to external storage.
     *
     * @param activity    The activity used as context for permission request.
     * @param requestCode Application specific request code to match with a result reported to
     *                    {@link ActivityCompat.OnRequestPermissionsResultCallback#onRequestPermissionsResult(int, String[], int[])}.
     * @return True when permission has been already granted.
     */
    public static boolean requestExternalStorageRwPermission(@NonNull Activity activity, int requestCode) {
        // On Android 6.0+ we ask for SD card access permission.
        // Since documents can be annotated we ask for write permission as well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE },
                        requestCode);
                return false;
            }
        }
        return true;
    }

    /**
     * Licensed under the Apache License, Version 2.0, from
     * <a href="https://github.com/consp1racy/material-navigation-drawer/blob/master/navigation-drawer/src/main/java/net/xpece/material/navigationdrawer/NavigationDrawerUtils.java">source</a>
     */
    public static void setProperNavigationDrawerWidth(final View view) {
        final Context context = view.getContext();
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    //noinspection deprecation
                    view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }

                int smallestWidthPx = context.getResources().getDisplayMetrics().widthPixels
                    < context.getResources().getDisplayMetrics().heightPixels
                    ? context.getResources().getDisplayMetrics().widthPixels
                    : context.getResources().getDisplayMetrics().heightPixels;
                int drawerMargin = context.getResources().getDimensionPixelOffset(R.dimen.drawer_margin);

                view.getLayoutParams().width = Math.min(
                    context.getResources().getDimensionPixelSize(R.dimen.drawer_max_width),
                    smallestWidthPx - drawerMargin
                );
                view.requestLayout();
            }
        });
    }
}
