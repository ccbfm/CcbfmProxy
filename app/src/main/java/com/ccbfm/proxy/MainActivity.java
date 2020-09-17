package com.ccbfm.proxy;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ccbfm.proxy.droid.ProxyManager;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final EditText editText = findViewById(R.id.editText);
        final EditText editText2 = findViewById(R.id.editText2);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                ProxyDroid.serviceStart(MainActivity.this, "113.124.84.65", 57114);
                String host = editText.getText().toString();
                String portStr = editText2.getText().toString();
                int port = 0;
                try {
                    port = Integer.parseInt(portStr);
                } catch (Exception e){
                    return;
                }
                if(TextUtils.isEmpty(host) || port <= 0){
                    return;
                }
                ProxyManager.instance().startProxy(MainActivity.this, host, port);
            }
        });
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                ProxyDroid.serviceStop(MainActivity.this);
                ProxyManager.instance().stopProxy(MainActivity.this);
            }
        });
        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

}
