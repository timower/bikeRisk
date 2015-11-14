package org.peno.b4.bikerisk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity implements ConnectionManager.ResponseListener {
    public static final int REQUEST_LOGIN = 5;
    private static final String TAG = "LoginActivity";
    private ConnectionManager connectionManager;

    private String username;

    private TextView connectionLostBanner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        connectionLostBanner = (TextView)findViewById(R.id.connectionLost);
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectionManager = ConnectionManager.getInstance(this, this);
    }

    //register button
    public void regClick(View view) {
        Intent registerIntent = new Intent(getApplicationContext(), RegisterActivity.class);
        startActivity(registerIntent);
    }

    //login button
    public void loginClick(View view) {
        EditText result1 = (EditText) findViewById(R.id.editText);
        EditText result2 = (EditText) findViewById(R.id.editText2);
        username = result1.getText().toString();
        String password = result2.getText().toString();
        connectionManager.login(username, password);
    }

    @Override
    public void onResponse(String req, Boolean result, JSONObject response) {
        connectionLostBanner.setVisibility(View.GONE);
        if (req.equals("login")) {
            if (result) {
                Toast.makeText(this, "Welcome "+ username+ "!",Toast.LENGTH_LONG).show();
                setResult(Activity.RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Failed to login.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onConnectionLost(String reason) {
        connectionLostBanner.setVisibility(View.VISIBLE);
        Log.d(TAG, "connection lost: " + reason);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
