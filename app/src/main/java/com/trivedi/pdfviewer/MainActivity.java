package com.trivedi.pdfviewer;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.util.LruCache;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pspdfkit.PSPDFKit;
import com.pspdfkit.configuration.activity.PdfActivityConfiguration;
import com.pspdfkit.configuration.policy.DefaultApplicationPolicy;
import com.pspdfkit.document.DocumentSource;
import com.pspdfkit.document.PdfDocument;
import com.pspdfkit.document.download.DownloadJob;
import com.pspdfkit.document.download.DownloadProgressFragment;
import com.pspdfkit.document.download.DownloadRequest;
import com.pspdfkit.document.providers.AssetDataProvider;
import com.pspdfkit.document.providers.DataProvider;
import com.pspdfkit.ui.PdfActivityIntentBuilder;
import com.pspdfkit.utils.Size;
import com.readystatesoftware.systembartint.SystemBarTintManager;

import org.reactivestreams.Publisher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity{
    private static final int REQUEST_ASK_FOR_PERMISSION = 1;

    private static final String URI_SCHEME_FILE = "file";

    private static final String IS_WAITING_FOR_PERMISSION_RESULT = "PSPDFKit.MainActivity.waitingForResult";

    private static final String DOWNLOAD_PROGRESS_FRAGMENT = "DownloadProgressFragment";

    private FixedDrawerLayout drawerLayout;
    private View settingsDrawer;
    private ListView examplesListView;

    private MenuItem searchAction;

    private boolean waitingForPermission;
    private ExampleListAdapter exampleListAdapter;

    @Nullable private SearchView searchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ActionBar ab = getSupportActionBar();

        if (ab == null) {
            throw new ExceptionInInitializerError(MainActivity.class.getSimpleName() + " is missing the ActionBar. Probably the wrong theme has been supplied.");
        }

        ab.setDisplayShowHomeEnabled(true);
        ab.setDisplayUseLogoEnabled(true);
        ab.setLogo(R.drawable.ic_logo_padded);

        SpannableString abTitle;
        if (PSPDFKit.VERSION.startsWith("android-") && PSPDFKit.VERSION.length() > 8) {
            // Tagged development builds have a slightly different format.
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION.substring(8));
        } else if (PSPDFKit.VERSION.contains("-")) {
            // Nightly versions have longer version so shorten it.
            String[] split = PSPDFKit.VERSION.split("-");
            abTitle = new SpannableString("PSPDFKit v" + split[0] + "-" + split[split.length - 1]);
        } else {
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION);
        }

        abTitle.setSpan(new RelativeSizeSpan(0.75f), 9, abTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ab.setTitle(abTitle);

        ViewServer.get(this).addWindow(this);

        // Provide the styling for Lollipop's Recents app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Bitmap logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo);
            setTaskDescription(new ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    logoBitmap,
                    ContextCompat.getColor(this, R.color.pspdf__color))
            );
        }

        // Prepare KitKat tint
        SystemBarTintManager stm = new SystemBarTintManager(this);
        stm.setStatusBarTintEnabled(true);
        stm.setStatusBarTintResource(R.color.pspdf_color_dark);

        // Prepare side drawer
        drawerLayout = (FixedDrawerLayout) findViewById(R.id.main_drawer);
        settingsDrawer = findViewById(R.id.settings_drawer);
        Utils.setProperNavigationDrawerWidth(settingsDrawer);

        // Add the preferences to the drawer
        if (getFragmentManager().findFragmentById(R.id.settings_drawer) == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.settings_drawer, new CatalogPreferencesFragment())
                    .commit();
        }

        // Check if the activity was recreated, and see if the user already requested external storage permissions.
        if (savedInstanceState != null) {
            waitingForPermission = savedInstanceState.getBoolean(IS_WAITING_FOR_PERMISSION_RESULT, false);
        }

        ExampleListAdapter.OnExampleClickListener clickListener = new ExampleListAdapter.OnExampleClickListener() {
            @Override
            public void onExampleClicked(View view, PSPDFExample example) {
                example.launchExample(MainActivity.this, CatalogPreferencesFragment.getConfiguration(MainActivity.this));
            }
        };
        // Adapter for showing all available examples in a list. The callback method then launches
        // the clicked example.
        exampleListAdapter = new ExampleListAdapter(clickListener);

        List<PSPDFExample.Section> sections = new ArrayList<>();
        sections.add(new PSPDFExample.Section("Opening Documents",
             /*   new BasicExample(this),
                new DocumentDownloadExample(this),
                new ExternalDocumentExample(this),
                new MemoryDataProviderExample(this),
                new CustomDataProviderExample(this),*/
                new KioskExample(this)/*,
                new PasswordExample(this),
                new InlineMediaExample(this),
                new ScientificPaperExample(this)*/
        ));

        /*sections.add(new PSPDFExample.Section("Behaviour Customization",
                new ZoomExample(this),
                new DynamicConfigurationExample(this),
                new CustomFragmentDynamicConfigurationExample(this),
                new HudViewModesExample(this),
                new DarkThemeExample(this),
                new DocumentSharingExample(this),
                new CustomSharingMenuExample(this),
                new CustomApplicationPolicyExample(this),
                new CustomShareDialogExample(this)
        ));

        sections.add(new PSPDFExample.Section("Layout Customization",
                new ToolbarsInFragmentExample(this),
                new CustomInkSignatureExample(this),
                new CustomLayoutExample(this),
                new FragmentExample(this),
                new DocumentSwitcherExample(this),
                new VerticalScrollbarExample(this),
                new SplitDocumentExample(this)
        ));

        sections.add(new PSPDFExample.Section("Toolbar Customization",
                new CustomActionsExample(this),
                new CustomToolbarIconGroupingExample(this),
                new CustomInlineSearchExample(this),
                new CustomSearchUiExample(this)
        ));

        sections.add(new PSPDFExample.Section("Annotations",
                new AnnotationCreationExample(this),
                new AnnotationRenderingExample(this),
                new AnnotationFlagsExample(this),
                new AnnotationDefaultsExample(this),
                new AnnotationSelectionCustomizationExample(this),
                new CustomAnnotationInspectorExample(this),
                new CustomStampAnnotationsExample(this)
        ));

        sections.add(new PSPDFExample.Section("Forms",
            new FormFillingExample(this),
            new CustomFormHighlightColorExample(this)
        ));

        sections.add(new PSPDFExample.Section("Misc. examples",
                new RandomDocumentReplacementExample(this),
                new DocumentProcessingExample(this),
                new IndexedFullTextSearchExample(this),
                new ScreenReaderExample(this)
        ));*/

        exampleListAdapter.setSections(sections);

        // Setup the list view.
        examplesListView = (ListView) findViewById(R.id.examples_list_view);
        examplesListView.setAdapter(exampleListAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        PSPDFKitReporting.startBugReporting(this);

        final Intent intent = getIntent();
        if (waitingForPermission) return;
        if (intent != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_EDIT.equals(intent.getAction()))) {
            // When opening local files outside of android's Storage Access Framework ask for permissions to external storage.
            if (intent.getData() != null &&
                    URI_SCHEME_FILE.equals(intent.getData().getScheme()) &&
                    !Utils.requestExternalStorageRwPermission(this, REQUEST_ASK_FOR_PERMISSION)) {
                waitingForPermission = true;
                return;
            }
            // We already have read/write permissions to external storage or don't need them.
            showDocument(intent);
        }

        //Reset any custom policy that might exist
        if (PSPDFKit.isInitialized() && !(PSPDFKit.getApplicationPolicy() instanceof DefaultApplicationPolicy)) {
            PSPDFKit.setApplicationPolicy(new DefaultApplicationPolicy());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (waitingForPermission && requestCode == REQUEST_ASK_FOR_PERMISSION) {
            waitingForPermission = false;
            if (getIntent() != null) {
                // We attempt to open document after permissions have been requested.
                showDocument(getIntent());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        searchAction = menu.findItem(R.id.search);
        final MenuItem settingsAction = menu.findItem(R.id.action_settings);

        final Drawable searchIcon = DrawableCompat.wrap(searchAction.getIcon());
        DrawableCompat.setTint(searchIcon, Color.WHITE);
        searchAction.setIcon(searchIcon);

        MenuItemCompat.setOnActionExpandListener(searchAction, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                // On tablets the settings pane is always visible, so the settings action is null.
                if (settingsAction != null) {
                    settingsAction.setVisible(false);
                    settingsAction.setEnabled(false);
                }
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (settingsAction != null) {
                    settingsAction.setVisible(true);
                    settingsAction.setEnabled(true);
                }
                return true;
            }
        });

        searchView = (SearchView) MenuItemCompat.getActionView(searchAction);
        searchView.setIconifiedByDefault(false);
        searchView.requestFocus();
        searchView.setQueryHint("Search Examples...");

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                exampleListAdapter.setQuery(newText);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int menuItemId = item.getItemId();

        switch (menuItemId) {
            case R.id.action_settings:
                if (drawerLayout.isDrawerOpen(settingsDrawer)) {
                    drawerLayout.closeDrawer(settingsDrawer);
                } else {
                    drawerLayout.openDrawer(settingsDrawer);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override public void onBackPressed() {
        // drawerLayout is only used on phones.
        if (drawerLayout != null && drawerLayout.isDrawerOpen(settingsDrawer)) {
            drawerLayout.closeDrawer(settingsDrawer);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ViewServer.get(this).removeWindow(this);
        if (searchView != null) {
            EditText editText = (EditText) searchView.findViewById(com.pspdfkit.R.id.search_src_text);
            // This will prevent the activity from being leaked.
            editText.setCursorVisible(false);

            searchView.setOnQueryTextListener(null);
            searchView = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Retain if we are currently waiting for a result of permission request so we don't set it off twice by accident.
        outState.putBoolean(IS_WAITING_FOR_PERMISSION_RESULT, waitingForPermission);
    }

    private void showDocument(@NonNull Intent intent) {
        final Uri uri = intent.getData();
        if (uri != null) {
            // If the URI can be resolved to a local filesystem path, we can directly access it for best performance.
            if (PSPDFKit.isLocalFileUri(this, uri)) {
                openDocumentAndFinishActivity(uri);
            }
            // All other URIs will be downloaded to the filesystem before opening them. While this is not necessary for all URI types
            // (e.g. content:// URIs could be opened directly as well) it ensures maximum compatibility with arbitrary sources as well as better performance.
            else {
                // Find the DownloadProgressFragment for showing download progress, or create a new one.
                DownloadProgressFragment downloadFragment = (DownloadProgressFragment) getSupportFragmentManager().findFragmentByTag(DOWNLOAD_PROGRESS_FRAGMENT);
                if (downloadFragment == null) {
                    final DownloadRequest request;
                    try {
                        request = new DownloadRequest.Builder(this).uri(uri).build();
                    } catch (Exception ex) {
                        showErrorAndFinishActivity("Download error", "PSPDFKit could not download the PDF file from the given URL.");
                        return;
                    }

                    final DownloadJob job = DownloadJob.startDownload(request);
                    downloadFragment = new DownloadProgressFragment();
                    downloadFragment.show(getSupportFragmentManager(), DOWNLOAD_PROGRESS_FRAGMENT);
                    downloadFragment.setJob(job);
                }

                // Once the download is complete we launch the PdfActivity from the downloaded file.
                downloadFragment.getJob().setProgressListener(new DownloadJob.ProgressListenerAdapter() {
                    @Override public void onComplete(@NonNull File output) {
                        openDocumentAndFinishActivity(Uri.fromFile(output));
                    }
                });
            }
        }
    }

    private void openDocumentAndFinishActivity(@NonNull final Uri uri) {
        final PdfActivityConfiguration configuration = CatalogPreferencesFragment.getConfiguration(MainActivity.this).build();
        final Intent intent = PdfActivityIntentBuilder.fromUri(MainActivity.this, uri)
                .configuration(configuration)
                .build();
        startActivity(intent);
        finish();
    }

    private void showErrorAndFinishActivity(@NonNull final String title, @NonNull final String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(errorMessage)
                .setNeutralButton("Exit catalog app", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private static class ExampleListAdapter extends BaseAdapter {

        public interface OnExampleClickListener {
            void onExampleClicked(View view, PSPDFExample example);
        }

        private static final int VIEW_TYPE_SECTION = 0;
        private static final int VIEW_TYPE_EXAMPLE = 1;

        @NonNull
        protected final List<PSPDFExample.Section> sections = new ArrayList<>();
        protected List<Object> flattenedItems;
        @NonNull private final OnExampleClickListener listener;

        /* The current search query or null */
        private String query;

        public ExampleListAdapter(@NonNull OnExampleClickListener listener) {
            this.listener = listener;
        }

        public void setSections(List<PSPDFExample.Section> sections) {
            this.sections.clear();
            this.sections.addAll(sections);
            flattenedItems = null;
            notifyDataSetChanged();
        }

        @Override public int getCount() {
            ensurePrepared();
            return flattenedItems.size();
        }

        @Override public Object getItem(int position) {
            ensurePrepared();
            return flattenedItems.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public int getViewTypeCount() {
            return 2;
        }

        @Override public int getItemViewType(int position) {
            return getItem(position) instanceof PSPDFExample.Section ? VIEW_TYPE_SECTION : VIEW_TYPE_EXAMPLE;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (getItemViewType(position) == VIEW_TYPE_SECTION) {
                return getSectionView(position, convertView, parent);
            } else {
                return getExampleView(position, convertView, parent);
            }
        }

        private View getExampleView(int position, View convertView, ViewGroup parent) {
            final PSPDFExample example = (PSPDFExample) getItem(position);

            View view = convertView;
            ExampleViewHolder holder;

            // Extract or create the view holder
            if (view != null) {
                holder = (ExampleViewHolder) convertView.getTag();
            } else {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_example, parent, false);
                holder = new ExampleViewHolder(view);
            }

            // Populate the list item
            holder.exampleTitleTextView.setText(example.title);
            holder.exampleDescriptionTextView.setText(example.description);

            // Pass back click events on examples
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.onExampleClicked(v, example);
                }
            });

            return view;
        }

        private View getSectionView(int position, View convertView, ViewGroup parent) {
            final PSPDFExample.Section section = (PSPDFExample.Section) getItem(position);

            View view = convertView;
            SectionViewHolder holder;

            // Extract or create the view holder
            if (view != null) {
                holder = (SectionViewHolder) convertView.getTag();
            } else {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_section, parent, false);
                holder = new SectionViewHolder(view);
            }

            holder.sectionNameTextView.setText(section.getName());

            return view;
        }

        public void setQuery(String query) {
            this.query = query.toLowerCase(Locale.getDefault());
            //We perform the filtering while building the list of items to be displayed
            flattenedItems = null;
            notifyDataSetChanged();
        }

        protected void ensurePrepared() {
            if (flattenedItems == null) {
                flattenedItems = new ArrayList<>();

                for (PSPDFExample.Section section : sections) {
                    int startIndex = flattenedItems.size();

                    int itemsAdded = 0;
                    for (PSPDFExample example : section) {
                        if (TextUtils.isEmpty(query) ||
                                doesExampleMatchQuery(example)) {
                            flattenedItems.add(example);
                            itemsAdded++;
                        }
                    }

                    //We only want to display the section if at least one item in it is displayed
                    if (itemsAdded > 0) {
                        flattenedItems.add(startIndex, section);
                    }
                }
            }
        }

        private boolean doesExampleMatchQuery(PSPDFExample example) {
            return example.title.toLowerCase(Locale.getDefault()).contains(query) ||
                    example.description.toLowerCase(Locale.getDefault()).contains(query);
        }

        private class SectionViewHolder {
            public TextView sectionNameTextView;

            public SectionViewHolder(View view) {
                sectionNameTextView = (TextView) view.findViewById(R.id.sectionNameTextView);
                view.setTag(this);
            }
        }

        private class ExampleViewHolder {
            public TextView exampleTitleTextView;
            public TextView exampleDescriptionTextView;

            public ExampleViewHolder(View view) {
                exampleTitleTextView = (TextView) view.findViewById(R.id.exampleTitleTextView);
                exampleDescriptionTextView = (TextView) view.findViewById(R.id.exampleDescriptionTextView);
                view.setTag(this);
            }
        }
    }
}

    /*private static final int REQUEST_ASK_FOR_PERMISSION = 1;

    private static final String URI_SCHEME_FILE = "file";

    private static final String IS_WAITING_FOR_PERMISSION_RESULT = "PSPDFKit.MainActivity.waitingForResult";

    private static final String DOWNLOAD_PROGRESS_FRAGMENT = "DownloadProgressFragment";

    private FixedDrawerLayout drawerLayout;
    private View settingsDrawer;
    private ListView examplesListView;

    private MenuItem searchAction;

    private boolean waitingForPermission;
    private ExampleListAdapter exampleListAdapter;

    @Nullable private SearchView searchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ActionBar ab = getSupportActionBar();

        if (ab == null) {
            throw new ExceptionInInitializerError(MainActivity.class.getSimpleName() + " is missing the ActionBar. Probably the wrong theme has been supplied.");
        }

        ab.setDisplayShowHomeEnabled(true);
        ab.setDisplayUseLogoEnabled(true);
        ab.setLogo(R.drawable.ic_logo_padded);

        SpannableString abTitle;
        if (PSPDFKit.VERSION.startsWith("android-") && PSPDFKit.VERSION.length() > 8) {
            // Tagged development builds have a slightly different format.
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION.substring(8));
        } else if (PSPDFKit.VERSION.contains("-")) {
            // Nightly versions have longer version so shorten it.
            String[] split = PSPDFKit.VERSION.split("-");
            abTitle = new SpannableString("PSPDFKit v" + split[0] + "-" + split[split.length - 1]);
        } else {
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION);
        }

        abTitle.setSpan(new RelativeSizeSpan(0.75f), 9, abTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ab.setTitle(abTitle);

        ViewServer.get(this).addWindow(this);

        // Provide the styling for Lollipop's Recents app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Bitmap logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo);
            setTaskDescription(new ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    logoBitmap,
                    ContextCompat.getColor(this, R.color.pspdf__color))
            );
        }

        // Prepare KitKat tint
        SystemBarTintManager stm = new SystemBarTintManager(this);
        stm.setStatusBarTintEnabled(true);
        stm.setStatusBarTintResource(R.color.pspdf_color_dark);

        // Prepare side drawer
        drawerLayout = (FixedDrawerLayout) findViewById(R.id.main_drawer);
        settingsDrawer = findViewById(R.id.settings_drawer);
        Utils.setProperNavigationDrawerWidth(settingsDrawer);

        // Add the preferences to the drawer
        if (getFragmentManager().findFragmentById(R.id.settings_drawer) == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.settings_drawer, new CatalogPreferencesFragment())
                    .commit();
        }

        // Check if the activity was recreated, and see if the user already requested external storage permissions.
        if (savedInstanceState != null) {
            waitingForPermission = savedInstanceState.getBoolean(IS_WAITING_FOR_PERMISSION_RESULT, false);
        }

        ExampleListAdapter.OnExampleClickListener clickListener = new ExampleListAdapter.OnExampleClickListener() {
            @Override
            public void onExampleClicked(View view, PSPDFExample example) {
                example.launchExample(MainActivity.this, CatalogPreferencesFragment.getConfiguration(MainActivity.this));
            }
        };
        // Adapter for showing all available examples in a list. The callback method then launches
        // the clicked example.
        exampleListAdapter = new ExampleListAdapter(clickListener);

        List<PSPDFExample.Section> sections = new ArrayList<>();
        sections.add(new PSPDFExample.Section("Opening Documents",
                new KioskExample(this)
        ));

        exampleListAdapter.setSections(sections);

        // Setup the list view.
        examplesListView = (ListView) findViewById(R.id.examples_list_view);
        examplesListView.setAdapter(exampleListAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        PSPDFKitReporting.startBugReporting(this);

        final Intent intent = getIntent();
        if (waitingForPermission) return;
        if (intent != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_EDIT.equals(intent.getAction()))) {
            // When opening local files outside of android's Storage Access Framework ask for permissions to external storage.
            if (intent.getData() != null &&
                    URI_SCHEME_FILE.equals(intent.getData().getScheme()) &&
                    !Utils.requestExternalStorageRwPermission(this, REQUEST_ASK_FOR_PERMISSION)) {
                waitingForPermission = true;
                return;
            }
            // We already have read/write permissions to external storage or don't need them.
            showDocument(intent);
        }

        //Reset any custom policy that might exist
        if (PSPDFKit.isInitialized() && !(PSPDFKit.getApplicationPolicy() instanceof DefaultApplicationPolicy)) {
            PSPDFKit.setApplicationPolicy(new DefaultApplicationPolicy());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (waitingForPermission && requestCode == REQUEST_ASK_FOR_PERMISSION) {
            waitingForPermission = false;
            if (getIntent() != null) {
                // We attempt to open document after permissions have been requested.
                showDocument(getIntent());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        searchAction = menu.findItem(R.id.search);
        final MenuItem settingsAction = menu.findItem(R.id.action_settings);

        final Drawable searchIcon = DrawableCompat.wrap(searchAction.getIcon());
        DrawableCompat.setTint(searchIcon, Color.WHITE);
        searchAction.setIcon(searchIcon);

        MenuItemCompat.setOnActionExpandListener(searchAction, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                // On tablets the settings pane is always visible, so the settings action is null.
                if (settingsAction != null) {
                    settingsAction.setVisible(false);
                    settingsAction.setEnabled(false);
                }
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (settingsAction != null) {
                    settingsAction.setVisible(true);
                    settingsAction.setEnabled(true);
                }
                return true;
            }
        });

        searchView = (SearchView) MenuItemCompat.getActionView(searchAction);
        searchView.setIconifiedByDefault(false);
        searchView.requestFocus();
        searchView.setQueryHint("Search Examples...");

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                exampleListAdapter.setQuery(newText);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int menuItemId = item.getItemId();

        switch (menuItemId) {
            case R.id.action_settings:
                if (drawerLayout.isDrawerOpen(settingsDrawer)) {
                    drawerLayout.closeDrawer(settingsDrawer);
                } else {
                    drawerLayout.openDrawer(settingsDrawer);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override public void onBackPressed() {
        // drawerLayout is only used on phones.
        if (drawerLayout != null && drawerLayout.isDrawerOpen(settingsDrawer)) {
            drawerLayout.closeDrawer(settingsDrawer);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ViewServer.get(this).removeWindow(this);
        if (searchView != null) {
            EditText editText = (EditText) searchView.findViewById(com.pspdfkit.R.id.search_src_text);
            // This will prevent the activity from being leaked.
            editText.setCursorVisible(false);

            searchView.setOnQueryTextListener(null);
            searchView = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Retain if we are currently waiting for a result of permission request so we don't set it off twice by accident.
        outState.putBoolean(IS_WAITING_FOR_PERMISSION_RESULT, waitingForPermission);
    }

    private void showDocument(@NonNull Intent intent) {
        final Uri uri = intent.getData();
        if (uri != null) {
            // If the URI can be resolved to a local filesystem path, we can directly access it for best performance.
            if (PSPDFKit.isLocalFileUri(this, uri)) {
                openDocumentAndFinishActivity(uri);
            }
            // All other URIs will be downloaded to the filesystem before opening them. While this is not necessary for all URI types
            // (e.g. content:// URIs could be opened directly as well) it ensures maximum compatibility with arbitrary sources as well as better performance.
            else {
                // Find the DownloadProgressFragment for showing download progress, or create a new one.
                DownloadProgressFragment downloadFragment = (DownloadProgressFragment) getSupportFragmentManager().findFragmentByTag(DOWNLOAD_PROGRESS_FRAGMENT);
                if (downloadFragment == null) {
                    final DownloadRequest request;
                    try {
                        request = new DownloadRequest.Builder(this).uri(uri).build();
                    } catch (Exception ex) {
                        showErrorAndFinishActivity("Download error", "PSPDFKit could not download the PDF file from the given URL.");
                        return;
                    }

                    final DownloadJob job = DownloadJob.startDownload(request);
                    downloadFragment = new DownloadProgressFragment();
                    downloadFragment.show(getSupportFragmentManager(), DOWNLOAD_PROGRESS_FRAGMENT);
                    downloadFragment.setJob(job);
                }

                // Once the download is complete we launch the PdfActivity from the downloaded file.
                downloadFragment.getJob().setProgressListener(new DownloadJob.ProgressListenerAdapter() {
                    @Override public void onComplete(@NonNull File output) {
                        openDocumentAndFinishActivity(Uri.fromFile(output));
                    }
                });
            }
        }
    }

    private void openDocumentAndFinishActivity(@NonNull final Uri uri) {
        final PdfActivityConfiguration configuration = CatalogPreferencesFragment.getConfiguration(MainActivity.this).build();
        final Intent intent = PdfActivityIntentBuilder.fromUri(MainActivity.this, uri)
                .configuration(configuration)
                .build();
        startActivity(intent);
        finish();
    }

    private void showErrorAndFinishActivity(@NonNull final String title, @NonNull final String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(errorMessage)
                .setNeutralButton("Exit catalog app", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private static class ExampleListAdapter extends BaseAdapter {

        public interface OnExampleClickListener {
            void onExampleClicked(View view, PSPDFExample example);
        }

        private static final int VIEW_TYPE_SECTION = 0;
        private static final int VIEW_TYPE_EXAMPLE = 1;

        @NonNull
        protected final List<PSPDFExample.Section> sections = new ArrayList<>();
        protected List<Object> flattenedItems;
        @NonNull private final OnExampleClickListener listener;

        *//* The current search query or null *//*
        private String query;

        public ExampleListAdapter(@NonNull OnExampleClickListener listener) {
            this.listener = listener;
        }

        public void setSections(List<PSPDFExample.Section> sections) {
            this.sections.clear();
            this.sections.addAll(sections);
            flattenedItems = null;
            notifyDataSetChanged();
        }

        @Override public int getCount() {
            ensurePrepared();
            return flattenedItems.size();
        }

        @Override public Object getItem(int position) {
            ensurePrepared();
            return flattenedItems.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public int getViewTypeCount() {
            return 2;
        }

        @Override public int getItemViewType(int position) {
            return getItem(position) instanceof PSPDFExample.Section ? VIEW_TYPE_SECTION : VIEW_TYPE_EXAMPLE;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (getItemViewType(position) == VIEW_TYPE_SECTION) {
                return getSectionView(position, convertView, parent);
            } else {
                return getExampleView(position, convertView, parent);
            }
        }

        private View getExampleView(int position, View convertView, ViewGroup parent) {
            final PSPDFExample example = (PSPDFExample) getItem(position);

            View view = convertView;
            ExampleViewHolder holder;

            // Extract or create the view holder
            if (view != null) {
                holder = (ExampleViewHolder) convertView.getTag();
            } else {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_example, parent, false);
                holder = new ExampleViewHolder(view);
            }

            // Populate the list item
            holder.exampleTitleTextView.setText(example.title);
            holder.exampleDescriptionTextView.setText(example.description);

            // Pass back click events on examples
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.onExampleClicked(v, example);
                }
            });

            return view;
        }

        private View getSectionView(int position, View convertView, ViewGroup parent) {
            final PSPDFExample.Section section = (PSPDFExample.Section) getItem(position);

            View view = convertView;
            SectionViewHolder holder;

            // Extract or create the view holder
            if (view != null) {
                holder = (SectionViewHolder) convertView.getTag();
            } else {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_section, parent, false);
                holder = new SectionViewHolder(view);
            }

            holder.sectionNameTextView.setText(section.getName());

            return view;
        }

        public void setQuery(String query) {
            this.query = query.toLowerCase(Locale.getDefault());
            //We perform the filtering while building the list of items to be displayed
            flattenedItems = null;
            notifyDataSetChanged();
        }

        protected void ensurePrepared() {
            if (flattenedItems == null) {
                flattenedItems = new ArrayList<>();

                for (PSPDFExample.Section section : sections) {
                    int startIndex = flattenedItems.size();

                    int itemsAdded = 0;
                    for (PSPDFExample example : section) {
                        if (TextUtils.isEmpty(query) ||
                                doesExampleMatchQuery(example)) {
                            flattenedItems.add(example);
                            itemsAdded++;
                        }
                    }

                    //We only want to display the section if at least one item in it is displayed
                    if (itemsAdded > 0) {
                        flattenedItems.add(startIndex, section);
                    }
                }
            }
        }

        private boolean doesExampleMatchQuery(PSPDFExample example) {
            return example.title.toLowerCase(Locale.getDefault()).contains(query) ||
                    example.description.toLowerCase(Locale.getDefault()).contains(query);
        }

        private class SectionViewHolder {
            public TextView sectionNameTextView;

            public SectionViewHolder(View view) {
                sectionNameTextView = (TextView) view.findViewById(R.id.sectionNameTextView);
                view.setTag(this);
            }
        }

        private class ExampleViewHolder {
            public TextView exampleTitleTextView;
            public TextView exampleDescriptionTextView;

            public ExampleViewHolder(View view) {
                exampleTitleTextView = (TextView) view.findViewById(R.id.exampleTitleTextView);
                exampleDescriptionTextView = (TextView) view.findViewById(R.id.exampleDescriptionTextView);
                view.setTag(this);
            }
        }
    }

}*/
       /* implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "Kiosk";

    public static final String CONFIGURATION_ARG = "configuration";

    private PdfActivityConfiguration configuration;
    private ExampleListAdapter exampleListAdapter;
    private ListView examplesListView;
    private boolean waitingForPermission;
    private static final String URI_SCHEME_FILE = "file";
    private static final int REQUEST_ASK_FOR_PERMISSION = 1;
    private static final String DOWNLOAD_PROGRESS_FRAGMENT = "DownloadProgressFragment";

    @Nullable
    //private Disposable listAssetsDisposable;

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        configuration = getIntent().getParcelableExtra(CONFIGURATION_ARG);

        final GridView documentGrid = (GridView) findViewById(android.R.id.list);
        final DocumentAdapter documentAdapter = new DocumentAdapter(this);
        documentGrid.setAdapter(documentAdapter);
        documentGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final DataProvider dataProvider = documentAdapter.getItem(position).getDocumentSource().getDataProvider();

                // Open the touched document.
                final Intent intent = PdfActivityIntentBuilder.fromDataProvider(MainActivity.this, dataProvider)
                        .configuration(configuration)
                        .activityClass(ZoomExampleActivity.class)
                        .build();

                startActivity(intent);
            }
        });
        final ProgressBar progressBar = (ProgressBar) findViewById(android.R.id.progress);
 //       PSPDFKit.initialize(MainActivity.this, "tHCmVLjjZm724h1X7Qr1EnjVH9bAkl75zPeLUC9FiYimERy6_wnwclH6U_39l8j0q9nQF5tdoMXuy0Zw2_DPCm9fSwUbOhoycXO9LxpeLiqRJl_wDpH3H0vky_mSS2UtfbEZPx3rH4T2XkfEhAjRtBLKkmbsw3mefWoqayJE80l8ic9kEkVayuTPY2ZTpnQsrEo_VSLqhvzJOYbJQnglpS4fGS-0EvlSLGmkjQMSq16ixuzp9Sbp1f6PRrVgmJUjrV0vPat2hqBSwiALuR8UPvtftbkuxtGrCErhsH9Wd_my0YdaS9y2k7TZUUCkr3fmp702yWEveRhv0PmXBmpcQ7vGDSvYq8ZGMZLIgfnTxXctiyhzsgFzE5PbfmCW0lb0W_WTKb6txt5riQixgs9F-C_GGy7xF8f2SvmMXI0FlOl27KEwFe45S049gg2mAhC");

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ActionBar ab = getSupportActionBar();

        if (ab == null) {
            throw new ExceptionInInitializerError(MainActivity.class.getSimpleName() + " is missing the ActionBar. Probably the wrong theme has been supplied.");
        }

        ab.setDisplayShowHomeEnabled(true);
        ab.setDisplayUseLogoEnabled(true);
        ab.setLogo(R.drawable.ic_logo_padded);

        SpannableString abTitle;
        if (PSPDFKit.VERSION.startsWith("android-") && PSPDFKit.VERSION.length() > 8) {
            // Tagged development builds have a slightly different format.
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION.substring(8));
        } else if (PSPDFKit.VERSION.contains("-")) {
            // Nightly versions have longer version so shorten it.
            String[] split = PSPDFKit.VERSION.split("-");
            abTitle = new SpannableString("PSPDFKit v" + split[0] + "-" + split[split.length - 1]);
        } else {
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION);
        }

        abTitle.setSpan(new RelativeSizeSpan(0.75f), 9, abTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ab.setTitle(abTitle);

        ViewServer.get(this).addWindow(this);

        // Provide the styling for Lollipop's Recents app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Bitmap logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo);
            setTaskDescription(new ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    logoBitmap,
                    ContextCompat.getColor(this, R.color.pspdf__color))
            );
        }

        // Prepare KitKat tint
        SystemBarTintManager stm = new SystemBarTintManager(this);
        stm.setStatusBarTintEnabled(true);
        stm.setStatusBarTintResource(R.color.pspdf_color_dark);

        // Prepare side drawer
       *//* drawerLayout = (FixedDrawerLayout) findViewById(R.id.main_drawer);
        settingsDrawer = findViewById(R.id.settings_drawer);
        Utils.setProperNavigationDrawerWidth(settingsDrawer);*//*

        // Add the preferences to the drawer
       *//* if (getFragmentManager().findFragmentById(R.id.settings_drawer) == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.settings_drawer, new CatalogPreferencesFragment())
                    .commit();
        }

        // Check if the activity was recreated, and see if the user already requested external storage permissions.
        if (savedInstanceState != null) {
            waitingForPermission = savedInstanceState.getBoolean(IS_WAITING_FOR_PERMISSION_RESULT, false);
        }*//*

        // Load the documents on a background thread.
      *//*  listAssetsDisposable = listAllAssets()
                .subscribeOn(Schedulers.io())
                .filter(new Predicate<String>() {
                    @Override
                    public boolean test(@io.reactivex.annotations.NonNull String s) throws Exception {
                        //Filter so we only get pdf files
                        return s.toLowerCase(Locale.getDefault()).endsWith(".pdf");
                    }
                })
                // The second observe on is necessary so opening the documents runs on a different thread as listing the assets.
                .observeOn(Schedulers.io())
                .flatMap(new Function<String, Publisher<? extends PdfDocument>>() {
                    @Override
                    public Publisher<? extends PdfDocument> apply(final String asset) {
                        return PdfDocument.openDocumentAsync(MainActivity.this, new DocumentSource(new AssetDataProvider(asset)))
                                .toFlowable()
                                .doOnError(new Consumer<Throwable>() {
                                    @Override
                                    public void accept(Throwable throwable) {
                                        // This example catches any error that happens while opening the document (e.g. if a password would be needed).
                                        // If an exception is thrown, the document will not be shown.
                                        Log.w(TAG, String.format("Could not open document '%s' from assets. See exception for reason.", asset), throwable);
                                    }
                                })
                                .onErrorResumeNext(Flowable.<PdfDocument>empty());
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(new Action() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                    }
                })
                .toSortedList(new Comparator<PdfDocument>() {
                    @Override
                    public int compare(PdfDocument document, PdfDocument document2) {
                        if (document == document2) {
                            return 0;
                        } else if (document.getTitle() == null) {
                            return -1;
                        } else if (document2.getTitle() == null) {
                            return 1;
                        } else {
                            return document.getTitle().compareToIgnoreCase(document2.getTitle());
                        }
                    }
                })
                .subscribe(new Consumer<List<PdfDocument>>() {
                    @Override
                    public void accept(List<PdfDocument> documents) {
                        documentAdapter.addAll(documents);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        progressBar.setVisibility(View.GONE);
                        Log.e(TAG, "Error while trying to list all catalog app assets.", throwable);
                        Toast.makeText(MainActivity.this, "Error listing asset files - see logcat for detailed error message.", Toast.LENGTH_LONG).show();
                    }
                });*//*

     //   SpannableString abTitle;
        if (PSPDFKit.VERSION.startsWith("android-") && PSPDFKit.VERSION.length() > 8) {
            // Tagged development builds have a slightly different format.
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION.substring(8));
        } else if (PSPDFKit.VERSION.contains("-")) {
            // Nightly versions have longer version so shorten it.
            String[] split = PSPDFKit.VERSION.split("-");
            abTitle = new SpannableString("PSPDFKit v" + split[0] + "-" + split[split.length - 1]);
        } else {
            abTitle = new SpannableString("PSPDFKit v" + PSPDFKit.VERSION);
        }

        abTitle.setSpan(new RelativeSizeSpan(0.75f), 9, abTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
     //   ab.setTitle(abTitle);

        ExampleListAdapter.OnExampleClickListener clickListener = new ExampleListAdapter.OnExampleClickListener() {
            @Override
            public void onExampleClicked(View view, PSPDFExample example) {
   //     PSPDFExample example=null;
                example.launchExample(MainActivity.this, CatalogPreferencesFragment.getConfiguration(MainActivity.this));
            }
        };

        exampleListAdapter = new ExampleListAdapter(clickListener);
        List<PSPDFExample.Section> sections = new ArrayList<>();
        sections.add(new PSPDFExample.Section("Opening Documents",
                new KioskExample(this)
        ));
//       / new KioskExample(this);
        exampleListAdapter.setSections(sections);

        // Setup the list view.
        examplesListView = (ListView) findViewById(R.id.examples_list_view);
        examplesListView.setAdapter(exampleListAdapter);

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private Flowable<String> listAllAssets() {
        return Flowable.create(new FlowableOnSubscribe<String>() {
            @Override
            public void subscribe(FlowableEmitter<String> emitter) throws Exception {
                try {
                    Queue<String> pathsToCheck = new ArrayDeque<>();
                    Collections.addAll(pathsToCheck, getAssets().list(""));
                    while (!pathsToCheck.isEmpty()) {
                        final String currentPath = pathsToCheck.poll();
                        String[] children = getChildren(currentPath);
                        if (children.length == 0) {
                            // This is just a file, tell our subscriber about it.
                            emitter.onNext(currentPath);
                        } else {
                            // Check all other sub paths.
                            for (String child : children) {
                                pathsToCheck.add(currentPath + File.separator + child);
                            }
                        }
                    }
                    emitter.onComplete();
                } catch (IOException e) {
                    emitter.onError(e);
                }
            }

            private String[] getChildren(String path) throws IOException {
                // Since listing assets is really really slow we assume everything with a '.' in it is a file.
                if (path.contains(".")) {
                    return new String[0];
                } else {
                    return getAssets().list(path);
                }
            }
        }, BackpressureStrategy.BUFFER);
    }
    public class KioskExample extends PSPDFExample {

        public KioskExample(@NonNull final Context context) {
            super("Kiosk Grid", "Displays all documents in the assets folder.");
        }

        @Override public void launchExample(Context context, PdfActivityConfiguration.Builder configuration) {
            final Intent intent = new Intent(context, KioskActivity.class);
            // Pass the configuration to our activity.
            intent.putExtra(KioskActivity.CONFIGURATION_ARG, configuration.build());
            context.startActivity(intent);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
       *//* if (listAssetsDisposable != null) {
            listAssetsDisposable.dispose();
        }*//*
    }

    private class DocumentAdapter extends ArrayAdapter<PdfDocument> {

        private LruCache<String, Bitmap> previewImageCache;

        private Size previewImageSize;

        public DocumentAdapter(Context context) {
            super(context, -1);
            previewImageCache = new LruCache<String, Bitmap>((int) ((Runtime.getRuntime().maxMemory() / 1024) / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    // The cache size will be measured in kilobytes rather than
                    // number of items.
                    return value.getByteCount() / 1024;
                }
            };

            previewImageSize = new Size(context.getResources().getDimensionPixelSize(R.dimen.kiosk_previewimage_width),
                    context.getResources().getDimensionPixelSize(R.dimen.kiosk_previewimage_height));
        }

        @TargetApi(Build.VERSION_CODES.N)
        @NonNull
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder = ViewHolder.get(convertView, parent);
            final PdfDocument document = getItem(position);

            if (holder.previewRenderDisposable != null) {
                holder.previewRenderDisposable.dispose();
            }

            // We only want to render a new preview image if we don't already have one in the cache.
            Bitmap cachedPreview = previewImageCache.get(document.getUid());
            holder.itemPreviewImageView.setImageBitmap(cachedPreview);
            if (cachedPreview == null) {
                // Calculate the size of the rendered preview image.
                Size size = calculateBitmapSize(document, previewImageSize);
                holder.previewRenderDisposable = document.renderPageToBitmapAsync(
                        parent.getContext(),
                        0,
                        (int) size.width,
                        (int) size.height
                ).observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Consumer<Bitmap>() {
                            @Override
                            public void accept(Bitmap bitmap) {
                                holder.itemPreviewImageView.setImageBitmap(bitmap);
                                previewImageCache.put(document.getUid(), bitmap);
                            }
                        });
            }

            if (!TextUtils.isEmpty(document.getTitle())) {
                holder.itemTitleView.setText(document.getTitle());
            } else {
                holder.itemTitleView.setText(getResources().getText(R.string.pspdf__activity_title_unnamed_document));
            }

            return holder.view;
        }

        private Size calculateBitmapSize(PdfDocument document, Size availableSpace) {
            Size pageSize = document.getPageSize(0);
            float ratio;
            if (pageSize.width > pageSize.height) {
                ratio = availableSpace.width / pageSize.width;
            } else {
                ratio = availableSpace.height / pageSize.height;
            }
            return new Size(pageSize.width * ratio, pageSize.height * ratio);
        }
    }

    private static class ViewHolder {

        public final View view;
        public final ImageView itemPreviewImageView;
        public final TextView itemTitleView;
        public Disposable previewRenderDisposable;

        public ViewHolder(View view) {
            this.view = view;
            this.itemPreviewImageView = (ImageView) view.findViewById(R.id.itemPreviewImageView);
            this.itemTitleView = (TextView) view.findViewById(R.id.itemTileView);
        }

        @NonNull
        public static ViewHolder get(View view, ViewGroup parent) {
            ViewHolder holder;

            if (view != null) {
                holder = (ViewHolder) view.getTag();
            } else {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_kiosk_item, parent, false);
                holder = new ViewHolder(view);
                view.setTag(holder);
            }

            return holder;
        }
    }
    private static class ExampleListAdapter extends BaseAdapter {

        public interface OnExampleClickListener {
            void onExampleClicked(View view, PSPDFExample example);
        }

        private static final int VIEW_TYPE_SECTION = 0;
        private static final int VIEW_TYPE_EXAMPLE = 1;

        @NonNull
        protected final List<PSPDFExample.Section> sections = new ArrayList<>();
        protected List<Object> flattenedItems;
        @NonNull private final OnExampleClickListener listener;

        *//* The current search query or null *//*
        private String query;

        public ExampleListAdapter(@NonNull OnExampleClickListener listener) {
            this.listener = listener;
        }

        public void setSections(List<PSPDFExample.Section> sections) {
            this.sections.clear();
            this.sections.addAll(sections);
            flattenedItems = null;
            notifyDataSetChanged();
        }

        @Override public int getCount() {
            ensurePrepared();
            return flattenedItems.size();
        }

        @Override public Object getItem(int position) {
            ensurePrepared();
            return flattenedItems.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public int getViewTypeCount() {
            return 2;
        }

        @Override public int getItemViewType(int position) {
            return getItem(position) instanceof PSPDFExample.Section ? VIEW_TYPE_SECTION : VIEW_TYPE_EXAMPLE;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (getItemViewType(position) == VIEW_TYPE_SECTION) {
                return getSectionView(position, convertView, parent);
            } else {
                return getExampleView(position, convertView, parent);
            }
        }

        private View getExampleView(int position, View convertView, ViewGroup parent) {
            final PSPDFExample example = (PSPDFExample) getItem(position);

            View view = convertView;
            ExampleViewHolder holder;

            // Extract or create the view holder
            if (view != null) {
                holder = (ExampleViewHolder) convertView.getTag();
            } else {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_example, parent, false);
                holder = new ExampleViewHolder(view);
            }

            // Populate the list item
            holder.exampleTitleTextView.setText(example.title);
            holder.exampleDescriptionTextView.setText(example.description);

            // Pass back click events on examples
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.onExampleClicked(v, example);
                }
            });

            return view;
        }

        private View getSectionView(int position, View convertView, ViewGroup parent) {
            final PSPDFExample.Section section = (PSPDFExample.Section) getItem(position);

            View view = convertView;
            SectionViewHolder holder;

            // Extract or create the view holder
            if (view != null) {
                holder = (SectionViewHolder) convertView.getTag();
            } else {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_section, parent, false);
                holder = new SectionViewHolder(view);
            }

            holder.sectionNameTextView.setText(section.getName());

            return view;
        }

        public void setQuery(String query) {
            this.query = query.toLowerCase(Locale.getDefault());
            //We perform the filtering while building the list of items to be displayed
            flattenedItems = null;
            notifyDataSetChanged();
        }

        protected void ensurePrepared() {
            if (flattenedItems == null) {
                flattenedItems = new ArrayList<>();

                for (PSPDFExample.Section section : sections) {
                    int startIndex = flattenedItems.size();

                    int itemsAdded = 0;
                    for (PSPDFExample example : section) {
                        if (TextUtils.isEmpty(query) ||
                                doesExampleMatchQuery(example)) {
                            flattenedItems.add(example);
                            itemsAdded++;
                        }
                    }

                    //We only want to display the section if at least one item in it is displayed
                    if (itemsAdded > 0) {
                        flattenedItems.add(startIndex, section);
                    }
                }
            }
        }

        private boolean doesExampleMatchQuery(PSPDFExample example) {
            return example.title.toLowerCase(Locale.getDefault()).contains(query) ||
                    example.description.toLowerCase(Locale.getDefault()).contains(query);
        }

        private class SectionViewHolder {
            public TextView sectionNameTextView;

            public SectionViewHolder(View view) {
                sectionNameTextView = (TextView) view.findViewById(R.id.sectionNameTextView);
                view.setTag(this);
            }
        }

        private class ExampleViewHolder {
            public TextView exampleTitleTextView;
            public TextView exampleDescriptionTextView;

            public ExampleViewHolder(View view) {
                exampleTitleTextView = (TextView) view.findViewById(R.id.exampleTitleTextView);
                exampleDescriptionTextView = (TextView) view.findViewById(R.id.exampleDescriptionTextView);
                view.setTag(this);
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();

        PSPDFKitReporting.startBugReporting(this);

        final Intent intent = getIntent();
        if (waitingForPermission) return;
        if (intent != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_EDIT.equals(intent.getAction()))) {
            // When opening local files outside of android's Storage Access Framework ask for permissions to external storage.
            if (intent.getData() != null &&
                    URI_SCHEME_FILE.equals(intent.getData().getScheme()) &&
                    !Utils.requestExternalStorageRwPermission(this, REQUEST_ASK_FOR_PERMISSION)) {
                waitingForPermission = true;
                return;
            }
            // We already have read/write permissions to external storage or don't need them.
            showDocument(intent);
        }

        //Reset any custom policy that might exist
        if (PSPDFKit.isInitialized() && !(PSPDFKit.getApplicationPolicy() instanceof DefaultApplicationPolicy)) {
            PSPDFKit.setApplicationPolicy(new DefaultApplicationPolicy());
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (waitingForPermission && requestCode == REQUEST_ASK_FOR_PERMISSION) {
            waitingForPermission = false;
            if (getIntent() != null) {
                // We attempt to open document after permissions have been requested.
                showDocument(getIntent());
            }
        }
    }
    private void showDocument(@NonNull Intent intent) {
        final Uri uri = intent.getData();
        if (uri != null) {
            // If the URI can be resolved to a local filesystem path, we can directly access it for best performance.
            if (PSPDFKit.isLocalFileUri(this, uri)) {
                openDocumentAndFinishActivity(uri);
            }
            // All other URIs will be downloaded to the filesystem before opening them. While this is not necessary for all URI types
            // (e.g. content:// URIs could be opened directly as well) it ensures maximum compatibility with arbitrary sources as well as better performance.
            else {
                // Find the DownloadProgressFragment for showing download progress, or create a new one.
                DownloadProgressFragment downloadFragment = (DownloadProgressFragment) getSupportFragmentManager().findFragmentByTag(DOWNLOAD_PROGRESS_FRAGMENT);
                if (downloadFragment == null) {
                    final DownloadRequest request;
                    try {
                        request = new DownloadRequest.Builder(this).uri(uri).build();
                    } catch (Exception ex) {
                        showErrorAndFinishActivity("Download error", "PSPDFKit could not download the PDF file from the given URL.");
                        return;
                    }

                    final DownloadJob job = DownloadJob.startDownload(request);
                    downloadFragment = new DownloadProgressFragment();
                    downloadFragment.show(getSupportFragmentManager(), DOWNLOAD_PROGRESS_FRAGMENT);
                    downloadFragment.setJob(job);
                }

                // Once the download is complete we launch the PdfActivity from the downloaded file.
                downloadFragment.getJob().setProgressListener(new DownloadJob.ProgressListenerAdapter() {
                    @Override public void onComplete(@NonNull File output) {
                        openDocumentAndFinishActivity(Uri.fromFile(output));
                    }
                });
            }
        }
    }
    private void openDocumentAndFinishActivity(@NonNull final Uri uri) {
        final PdfActivityConfiguration configuration = CatalogPreferencesFragment.getConfiguration(MainActivity.this).build();
        final Intent intent = PdfActivityIntentBuilder.fromUri(MainActivity.this, uri)
                .configuration(configuration)
                .build();
        startActivity(intent);
        finish();
    }

    private void showErrorAndFinishActivity(@NonNull final String title, @NonNull final String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(errorMessage)
                .setNeutralButton("Exit catalog app", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }
}
*/