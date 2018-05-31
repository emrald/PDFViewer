/*
 * PSPDFCatalog.java
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

import android.app.Application;

public class PSPDFCatalog extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PSPDFKitReporting.initializeBugReporting(this);
    }

}
