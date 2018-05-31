package com.homedelivery.pdfviewer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by TI A1 on 15-02-2017.
 */
public class NextActivity extends Activity {

    Bundle bn;
    String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.next_activity);

        bn = getIntent().getExtras();
        if (bn != null) {
            url = bn.getString("pdf");
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), "application/pdf");
     //  intent.setDataAndType(Uri.parse("http://docs.google.com/viewer?url=" + url), "text/html");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e("Error...",e.getMessage()+"");
        }
    }
}
