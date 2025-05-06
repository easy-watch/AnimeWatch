package com.animewatch.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class ChatActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        TextView chatText = findViewById(R.id.chat_text);
        chatText.setText("Welcome to Chat!");

        Toast.makeText(this, "Chat feature is under development", Toast.LENGTH_SHORT).show();
    }
}
