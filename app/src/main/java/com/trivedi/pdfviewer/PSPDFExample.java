/*
 * PSPDFKitExample.java
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

import android.content.Context;
import android.support.annotation.NonNull;

import com.pspdfkit.configuration.activity.PdfActivityConfiguration;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Abstract example class which provides {@link #launchExample(Context, PdfActivityConfiguration.Builder)}
 * as a generic way of launching a catalog app example.
 */
public abstract class PSPDFExample {

    /** Quick start guide asset file name. */
    public static final String QUICK_START_GUIDE = "Guide-v3.pdf";

    /**
     * Short title of the example.
     */
    @NonNull public String title;

    /**
     * Full description of the example.
     */
    @NonNull public String description;

    /**
     * Convenience constructor. Examples can pass their <code>title</code> and <code>description</code>
     * here.
     */
    public PSPDFExample(@NonNull String title, @NonNull String description) {
        this.title = title;
        this.description = description;
    }

    public abstract void launchExample(Context context, PdfActivityConfiguration.Builder configuration);

    /**
     * A section is a named list of examples grouped together (e.g. "Multimedia examples").
     */
    public static class Section extends ArrayList<PSPDFExample> {
        private final String name;

        public Section(String name, PSPDFExample... examples) {
            this.name = name;
            Collections.addAll(this, examples);
        }

        public String getName() {
            return name;
        }
    }
}
