package com.homedelivery.pdfviewer;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity {

    ListView listView;
    String[] array_pdf = {"http://www.tutorialspoint.com/android/android_tutorial.pdf",
    "http://maven.apache.org/maven-1.x/maven.pdf","https://www.tutorialspoint.com/ios/ios_tutorial.pdf"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView)findViewById(R.id.listView);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,android.R.id.text1,array_pdf);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Intent intent = new Intent(MainActivity.this,NextActivity.class);
                intent.putExtra("pdf",parent.getItemAtPosition(position)+"");
                startActivity(intent);
            }
        });
    }

}
