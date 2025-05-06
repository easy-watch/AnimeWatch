package com.animewatch.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;
import android.view.View;

public class LoginActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        final EditText emailField = (EditText) findViewById(R.id.email_field);
        final EditText passwordField = (EditText) findViewById(R.id.password_field);
        Button loginButton = (Button) findViewById(R.id.login_button);

        // Replace lambda with anonymous inner class
        loginButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					String email = emailField.getText().toString();
					String password = passwordField.getText().toString();

					if (email.isEmpty() || password.isEmpty()) {
						Toast.makeText(LoginActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
					} else {
						Toast.makeText(LoginActivity.this, "Logged in as " + email, Toast.LENGTH_SHORT).show();
						Intent intent = new Intent(LoginActivity.this, MainActivity.class);
						startActivity(intent);
						finish();
					}
				}
			});
    }
}
