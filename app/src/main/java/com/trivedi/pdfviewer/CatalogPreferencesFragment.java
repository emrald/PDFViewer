/*
 * CatalogPreferencesFragment.java
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

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.pspdfkit.PSPDFKit;
import com.pspdfkit.configuration.PdfConfiguration;
import com.pspdfkit.configuration.activity.HudViewMode;
import com.pspdfkit.configuration.activity.PdfActivityConfiguration;
import com.pspdfkit.configuration.annotations.AnnotationEditingConfiguration;
import com.pspdfkit.configuration.forms.FormEditingConfiguration;
import com.pspdfkit.configuration.page.PageFitMode;
import com.pspdfkit.configuration.page.PageLayoutMode;
import com.pspdfkit.configuration.page.PageScrollDirection;
import com.pspdfkit.configuration.page.PageScrollMode;
import com.pspdfkit.document.datastore.DocumentDataStore;
import com.pspdfkit.ui.PdfFragment;

/**
 * This settings fragment is used to configure the {@link PdfConfiguration} used by the examples.
 */
public class CatalogPreferencesFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREF_PAGE_SCROLL_DIRECTION = "page_scroll_direction";
    private static final String PREF_PAGE_LAYOUT_MODE = "page_layout_mode";
    private static final String PREF_PAGE_SCROLL_CONTINUOUS = "scroll_continuously";
    private static final String PREF_FIT_PAGE_TO_WIDTH = "fit_page_to_width";
    private static final String PREF_FIRST_PAGE_AS_SINGLE = "first_page_as_single";
    private static final String PREF_SHOW_GAP_BETWEEN_PAGES = "show_gap_between_pages";
    private static final String PREF_IMMERSIVE_MODE = "immersive_mode";
    private static final String PREF_SYSTEM_HUD_MODE = "hud_view_mode";
    private static final String PREF_HIDE_UI_WHEN_CREATING_ANNOTATIONS = "hide_ui_when_creating_annotations";
    private static final String PREF_SHOW_SEARCH_ACTION = "show_search_action";
    private static final String PREF_INLINE_SEARCH = "inline_search";
    private static final String PREF_SHOW_THUMBNAIL_BAR = "show_thumbnail_bar";
    private static final String PREF_SHOW_THUMBNAIL_GRID_ACTION = "show_thumbnail_grid_action";
    private static final String PREF_SHOW_OUTLINE_ACTION = "show_outline_action";
    private static final String PREF_SHOW_ANNOTATION_LIST_ACTION = "show_annotation_list_action";
    private static final String PREF_SHOW_PAGE_NUMBER_OVERLAY = "show_page_number_overlay";
    private static final String PREF_SHOW_PAGE_LABELS = "show_page_labels";
    private static final String PREF_INVERT_COLORS = "invert_colors";
    private static final String PREF_GRAYSCALE = "grayscale";
    private static final String PREF_START_PAGE = "start_page";
    private static final String PREF_RESTORE_LAST_VIEWED_PAGE = "restore_last_viewed_page";
    private static final String PREF_CLEAR_CACHE = "clear_cache";
    private static final String PREF_CLEAR_APP_DATA = "clear_app_data";
    private static final String PREF_ENABLE_ANNOTATION_EDITING = "enable_annotation_editing";
    private static final String PREF_ENABLE_TEXT_SELECTION = "enable_text_selection";
    private static final String PREF_ENABLE_FORM_EDITING = "enable_form_editing";
    private static final String PREF_SHOW_SHARE_ACTION = "show_share_action";
    private static final String PREF_SHOW_PRINT_ACTION = "show_print_action";

    Preference clearCacheBtn;
    Preference clearAppDataBtn;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        clearCacheBtn = findPreference(PREF_CLEAR_CACHE);
        clearCacheBtn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PSPDFKit.clearCaches(getActivity(), true);
                Toast.makeText(getActivity(), "Cache cleared.", Toast.LENGTH_SHORT).show();
                return true;
            }
        });


        clearAppDataBtn = findPreference(PREF_CLEAR_APP_DATA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clearAppDataBtn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @TargetApi(Build.VERSION_CODES.KITKAT) @Override public boolean onPreferenceClick(Preference preference) {
                    ((ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE)).clearApplicationUserData();
                    return true;
                }
            });
        } else {
            clearAppDataBtn.setEnabled(false);
        }

        // Ensure proper defaults are set.
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(@SuppressWarnings("NullableProblems") LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // Keep the same padding on all devices.
        if (view != null) {
            ListView lv = (ListView) view.findViewById(android.R.id.list);
            lv.setPadding(10, 10, 10, 10);
        }
        return view;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case PREF_RESTORE_LAST_VIEWED_PAGE:
                // If the setting for remembering the last viewed page is turned off, reset all viewed pages.
                final boolean enabled = getBooleanValue(sharedPreferences, key);
                if (!enabled) {
                    final DocumentDataStore dataStore = DocumentDataStore.getInstance();
                    for (DocumentDataStore.DocumentUid uid : dataStore.getDocumentUids()) {
                        dataStore.getDataForDocument(uid.documentUid).putInt(PdfFragment.DOCUMENTSTORE_KEY_LAST_VIEWED_PAGE_INDEX, 0);
                    }
                }
                break;
            case PREF_ENABLE_FORM_EDITING:
                PSPDFKit.clearCaches(getActivity(), true);
                break;

            default:
        }
    }

    public static PdfActivityConfiguration.Builder getConfiguration(Context context) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        final String pageScrollDirectionHorizontal = context.getString(R.string.page_scroll_direction_horizontal);
        final String hudViewModeDefault = context.getString(R.string.hud_view_mode_automatic);

        //noinspection ConstantConditions
        final PageScrollDirection pageScrollDirection = isStringValueSet(sharedPref, PREF_PAGE_SCROLL_DIRECTION, pageScrollDirectionHorizontal) ? PageScrollDirection.HORIZONTAL : PageScrollDirection.VERTICAL;
        final PageScrollMode pageScrollMode = getBooleanValue(sharedPref, PREF_PAGE_SCROLL_CONTINUOUS) ? PageScrollMode.CONTINUOUS : PageScrollMode.PER_PAGE;
        final PageFitMode pageFitMode = getBooleanValue(sharedPref, PREF_FIT_PAGE_TO_WIDTH) ? PageFitMode.FIT_TO_WIDTH : PageFitMode.FIT_TO_SCREEN;

        final PageLayoutMode pageLayoutMode;
        if (isStringValueSet(sharedPref, PREF_PAGE_LAYOUT_MODE, context.getString(R.string.page_layout_single))) {
            pageLayoutMode = PageLayoutMode.SINGLE;
        } else if (isStringValueSet(sharedPref, PREF_PAGE_LAYOUT_MODE, context.getString(R.string.page_layout_double))) {
            pageLayoutMode = PageLayoutMode.DOUBLE;
        } else {
            pageLayoutMode = PageLayoutMode.AUTO;
        }

        final boolean restoreLastViewedPage = getBooleanValue(sharedPref, PREF_RESTORE_LAST_VIEWED_PAGE);
        final int searchType = getBooleanValue(sharedPref, PREF_INLINE_SEARCH) ? PdfActivityConfiguration.SEARCH_INLINE : PdfActivityConfiguration.SEARCH_MODULAR;
        final HudViewMode hudViewMode = getHudModeFromPreferenceString(context, getStringValue(sharedPref, PREF_SYSTEM_HUD_MODE, hudViewModeDefault));
        boolean hideUserInterfaceWhenCreatingAnnotations = getBooleanValue(sharedPref, PREF_HIDE_UI_WHEN_CREATING_ANNOTATIONS);

        final boolean firstPageAlwaysSingle = getBooleanValue(sharedPref, PREF_FIRST_PAGE_AS_SINGLE);
        final boolean showGapBetweenPages = getBooleanValue(sharedPref, PREF_SHOW_GAP_BETWEEN_PAGES);

        int startPage;
        try {
            startPage = Integer.parseInt(getStringValue(sharedPref, PREF_START_PAGE, "0"));
        } catch (NumberFormatException ex) {
            startPage = 0;
            sharedPref.edit().putString(PREF_START_PAGE, "0").apply();
        }

        final PdfActivityConfiguration.Builder configuration = new PdfActivityConfiguration.Builder(context)
                .scrollDirection(pageScrollDirection)
                .scrollMode(pageScrollMode)
                .fitMode(pageFitMode)
                .layoutMode(pageLayoutMode)
                .firstPageAlwaysSingle(firstPageAlwaysSingle)
                .showGapBetweenPages(showGapBetweenPages)
                .restoreLastViewedPage(restoreLastViewedPage)
                .setHudViewMode(hudViewMode)
                .hideUserInterfaceWhenCreatingAnnotations(hideUserInterfaceWhenCreatingAnnotations)
                .setSearchType(searchType);

        if (startPage != 0) {
            configuration.page(startPage);
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_SEARCH_ACTION)) {
            configuration.enableSearch();
        } else {
            configuration.disableSearch();
        }

        configuration.useImmersiveMode(getBooleanValue(sharedPref, PREF_IMMERSIVE_MODE));

        if (getBooleanValue(sharedPref, PREF_SHOW_THUMBNAIL_BAR)) {
            configuration.showThumbnailBar();
        } else {
            configuration.hideThumbnailBar();
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_THUMBNAIL_GRID_ACTION)) {
            configuration.showThumbnailGrid();
        } else {
            configuration.hideThumbnailGrid();
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_OUTLINE_ACTION)) {
            configuration.enableOutline();
        } else {
            configuration.disableOutline();
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_ANNOTATION_LIST_ACTION)) {
            configuration.enableAnnotationList();
        } else {
            configuration.disableAnnotationList();
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_PAGE_NUMBER_OVERLAY)) {
            configuration.showPageNumberOverlay();
        } else {
            configuration.hidePageNumberOverlay();
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_PAGE_LABELS)) {
            configuration.showPageLabels();
        } else {
            configuration.hidePageLabels();
        }

        if (getBooleanValue(sharedPref, PREF_GRAYSCALE)) {
            configuration.toGrayscale(true);
        } else {
            configuration.toGrayscale(false);
        }

        if (getBooleanValue(sharedPref, PREF_INVERT_COLORS)) {
            configuration.invertColors(true);
        } else {
            configuration.invertColors(false);
        }

        AnnotationEditingConfiguration.Builder annotationEditingConfiguration = new AnnotationEditingConfiguration.Builder(context);
        if (getBooleanValue(sharedPref, PREF_ENABLE_ANNOTATION_EDITING)) {
            annotationEditingConfiguration.enableAnnotationEditing();
        } else {
            annotationEditingConfiguration.disableAnnotationEditing();
        }

        FormEditingConfiguration.Builder formEditingConfiguration = new FormEditingConfiguration.Builder();
        if (getBooleanValue(sharedPref, PREF_ENABLE_FORM_EDITING)) {
            formEditingConfiguration.enableFormEditing();
        } else {
            formEditingConfiguration.disableFormEditing();
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_SHARE_ACTION)) {
            configuration.enableShare();
        } else {
            configuration.disableShare();
        }

        if (getBooleanValue(sharedPref, PREF_SHOW_PRINT_ACTION)) {
            configuration.enablePrinting();
        } else {
            configuration.disablePrinting();
        }

        if (getBooleanValue(sharedPref, PREF_ENABLE_TEXT_SELECTION)) {
            configuration.textSelectionEnabled(true);
        } else {
            configuration.textSelectionEnabled(false);
        }

        configuration.annotationEditingConfiguration(annotationEditingConfiguration.build());
        configuration.formEditingConfiguration(formEditingConfiguration.build());
        return configuration;
    }

    private static HudViewMode getHudModeFromPreferenceString(@NonNull Context context, @NonNull String hudModePreferenceValue) {
        HudViewMode hudMode = HudViewMode.HUD_VIEW_MODE_AUTOMATIC;
        if (hudModePreferenceValue.equals(context.getString(R.string.hud_view_mode_automatic))) {
            hudMode = HudViewMode.HUD_VIEW_MODE_AUTOMATIC;
        } else if (hudModePreferenceValue.equals(context.getString(R.string.hud_view_mode_automatic_border_pages))) {
            hudMode = HudViewMode.HUD_VIEW_MODE_AUTOMATIC_BORDER_PAGES;
        } else if (hudModePreferenceValue.equals(context.getString(R.string.hud_view_mode_visible))) {
            hudMode = HudViewMode.HUD_VIEW_MODE_VISIBLE;
        } else if (hudModePreferenceValue.equals(context.getString(R.string.hud_view_mode_hidden))) {
            hudMode = HudViewMode.HUD_VIEW_MODE_HIDDEN;
        }
        return hudMode;
    }

    private static boolean isStringValueSet(SharedPreferences sharedPref, String key, String expected) {
        //noinspection ConstantConditions
        return sharedPref.getString(key, "").equals(expected);
    }

    private static boolean getBooleanValue(SharedPreferences sharedPref, String key) {
        return sharedPref.getBoolean(key, false);
    }

    private static String getStringValue(SharedPreferences sharedPref, String key, String defaultValue) {
        return sharedPref.getString(key, defaultValue);
    }
}
