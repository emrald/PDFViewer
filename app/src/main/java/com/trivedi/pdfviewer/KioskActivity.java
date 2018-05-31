/*
 * KioskActivity.java
 *
 *   PSPDFKit
 *
 *   Copyright © 2017 PSPDFKit GmbH. All rights reserved.
 *
 *   THIS SOURCE CODE AND ANY ACCOMPANYING DOCUMENTATION ARE PROTECTED BY INTERNATIONAL COPYRIGHT LAW
 *   AND MAY NOT BE RESOLD OR REDISTRIBUTED. USAGE IS BOUND TO THE PSPDFKIT LICENSE AGREEMENT.
 *   UNAUTHORIZED REPRODUCTION OR DISTRIBUTION IS SUBJECT TO CIVIL AND CRIMINAL PENALTIES.
 *   This notice may not be removed from this file.
 */

package com.trivedi.pdfviewer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pspdfkit.configuration.activity.PdfActivityConfiguration;
import com.pspdfkit.document.DocumentSource;
import com.pspdfkit.document.PdfDocument;
import com.pspdfkit.document.providers.AssetDataProvider;
import com.pspdfkit.document.providers.DataProvider;
import com.pspdfkit.ui.PdfActivityIntentBuilder;
import com.pspdfkit.utils.Size;

import org.reactivestreams.Publisher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
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

/**
 * This activity displays all documents found in the assets folder of the app.
 */

public class KioskActivity extends AppCompatActivity {

    private static final String TAG = "Kiosk";

    public static final String CONFIGURATION_ARG = "configuration";

    private PdfActivityConfiguration configuration;

    @Nullable private Disposable listAssetsDisposable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);

        configuration = getIntent().getParcelableExtra(CONFIGURATION_ARG);

        final GridView documentGrid = (GridView) findViewById(android.R.id.list);
        final DocumentAdapter documentAdapter = new DocumentAdapter(this);
        documentGrid.setAdapter(documentAdapter);
        documentGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final DataProvider dataProvider = documentAdapter.getItem(position).getDocumentSource().getDataProvider();

                // Open the touched document.
                final Intent intent = PdfActivityIntentBuilder.fromDataProvider(KioskActivity.this, dataProvider)
                        .configuration(configuration)
                        .activityClass(ZoomExampleActivity.class)
                        .build();

                startActivity(intent);
            }
        });

        final ProgressBar progressBar = (ProgressBar) findViewById(android.R.id.progress);

        // Load the documents on a background thread.
        listAssetsDisposable = listAllAssets()
            .subscribeOn(Schedulers.io())
            .filter(new Predicate<String>() {
                @Override public boolean test(@io.reactivex.annotations.NonNull String s) throws Exception {
                    //Filter so we only get pdf files
                    return s.toLowerCase(Locale.getDefault()).endsWith(".pdf");
                }
            })
            // The second observe on is necessary so opening the documents runs on a different thread as listing the assets.
            .observeOn(Schedulers.io())
            .flatMap(new Function<String, Publisher<? extends PdfDocument>>() {
                @Override
                public Publisher<? extends PdfDocument> apply(final String asset) {
                    return PdfDocument.openDocumentAsync(KioskActivity.this, new DocumentSource(new AssetDataProvider(asset)))
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
                @Override public int compare(PdfDocument document, PdfDocument document2) {
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
                    documentAdapter.addAll(documents.get(0));
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error while trying to list all catalog app assets.", throwable);
                    Toast.makeText(KioskActivity.this, "Error listing asset files - see logcat for detailed error message.", Toast.LENGTH_LONG).show();
                }
            });
    }

    /**
     * Lists all assets in the assets directory.
     *
     * @return A observable sending all file paths in the assets folder.
     */
    private Flowable<String> listAllAssets() {
        return Flowable.create(new FlowableOnSubscribe<String>() {
            @Override public void subscribe(FlowableEmitter<String> emitter) throws Exception {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listAssetsDisposable != null) {
            listAssetsDisposable.dispose();
        }
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
}
