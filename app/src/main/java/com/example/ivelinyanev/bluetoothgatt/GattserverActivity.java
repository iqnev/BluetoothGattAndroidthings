package com.example.ivelinyanev.bluetoothgatt;

import android.app.Activity;
import android.os.Bundle;

import java.util.Random;

/**
 *
 */
public class GattserverActivity extends Activity {

    private final SimpleGattServer simpleGattServer = new SimpleGattServer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int min = 20;
        final int max = 80;
        simpleGattServer.onCreate(this, new SimpleGattServer.GattServerListener() {
            @Override
            public void onServerrWritten() {

            }

            @Override
            public byte[] onCounterRead() {
                return toByteArray(new Random().nextInt((max - min) + 1) + min);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        simpleGattServer.onDestroy();
    }

    public static byte[] toByteArray(int value) {
        return new byte[]{
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
        };
    }
}
