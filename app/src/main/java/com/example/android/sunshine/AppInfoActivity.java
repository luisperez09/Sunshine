package com.example.android.sunshine;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class AppInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_info);

        TextView tv = (TextView) findViewById(R.id.app_info_tv);
        Intent intent = getIntent();
        String action = intent.getStringExtra("action");
        if (action.equals("about")) {
            tv.setText(getString(R.string.about_text));
            setTitle(getString(R.string.action_about));
        } else if (action.equals("dev_info")) {
            tv.setText(getString(R.string.dev_info_text));
            setTitle(getString(R.string.dev_info_action));
        }
    }
}
