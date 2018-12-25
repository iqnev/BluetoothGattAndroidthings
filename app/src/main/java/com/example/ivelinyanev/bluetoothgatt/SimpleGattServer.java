package com.example.ivelinyanev.bluetoothgatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import profile.ClickProfile;

/**
 * @author Ivelin Yanev <bgfortran@gmail.com>
 * @since 2018
 *
 */
public class SimpleGattServer {

    private static final String TAG = SimpleGattServer.class.getSimpleName();

    private BluetoothManager bluetoothManager;
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private Context context;
    private GattServerListener gattServerListener;

    private Set<BluetoothDevice> bluetoothDevices = new HashSet<>();

    public interface GattServerListener {
        void onServerrWritten();
        byte[] onCounterRead();
    }


    private BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
           if(newState == BluetoothProfile.STATE_CONNECTED) {
               Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
           } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
               Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
               bluetoothDevices.remove(device);
           }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if(ClickProfile.CHARACTERISTIC_COUNTER_UUID.equals(characteristic.getUuid())) {
                byte[] value = gattServerListener.onCounterRead();

                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0 , value);
            } else {
                bluetoothGattServer .sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (ClickProfile.CHARACTERISTIC_INTERACTOR_UUID.equals(characteristic.getUuid())) {
                if (gattServerListener != null) {
                    gattServerListener.onServerrWritten();
                }
                notifyRegisteredDevices();
            } else {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            if (ClickProfile.DESCRIPTOR_CONFIG.equals(descriptor.getUuid())) {
                byte[] returnValue;
                if (bluetoothDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue);
            } else if (ClickProfile.DESCRIPTOR_USER_DESC.equals(descriptor.getUuid())) {
                byte[] returnValue = ClickProfile.getUserDescription(descriptor.getCharacteristic().getUuid());
                returnValue = Arrays.copyOfRange(returnValue, offset, returnValue.length);
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue);
            } else {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (ClickProfile.DESCRIPTOR_CONFIG.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    bluetoothDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    bluetoothDevices.remove(device);
                }

                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                }
            } else {
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                }
            }
        }
    };

    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {

        }

        @Override
        public void onStartFailure(int errorCode) {
            ;
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    beginAdvertising();
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    stopAdvertising();
                    break;
                default:
                    break;
            }
        }
    };

    public void onCreate(Context context, GattServerListener listener) throws RuntimeException {
        this.context = context;
        this.gattServerListener = listener;

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            throw new RuntimeException("GATT server requires Bluetooth support");
        }

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(broadcastReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled... enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled... starting services");
            beginAdvertising();
            startServer();
        }

    }

    public void onDestroy() {
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            stopServer();
            stopAdvertising();
        }

        context.unregisterReceiver(broadcastReceiver);
        gattServerListener = null;
    }

    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter == null) {
            return false;
        }

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }

        return true;
    }

    private void  beginAdvertising() {
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (bluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(ClickProfile.SERVICE_UUID))
                .build();

        bluetoothLeAdvertiser
                .startAdvertising(settings, data, advertiseCallback);
    }

    private void stopAdvertising() {
        if (bluetoothLeAdvertiser == null) {
            return;
        }
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
    }

    private void startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(context, bluetoothGattServerCallback);
        if (bluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        bluetoothGattServer.addService(createClickService());
    }

    private BluetoothGattService createClickService() {
        BluetoothGattService service = new BluetoothGattService(ClickProfile.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic counter = new BluetoothGattCharacteristic(ClickProfile.CHARACTERISTIC_COUNTER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor counterConfig = new BluetoothGattDescriptor(ClickProfile.DESCRIPTOR_CONFIG, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        counter.addDescriptor(counterConfig);
        BluetoothGattDescriptor counterDescription = new BluetoothGattDescriptor(ClickProfile.DESCRIPTOR_USER_DESC, BluetoothGattDescriptor.PERMISSION_READ);
        counter.addDescriptor(counterDescription);

        BluetoothGattCharacteristic interactor = new BluetoothGattCharacteristic(ClickProfile.CHARACTERISTIC_INTERACTOR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor interactorDescription = new BluetoothGattDescriptor(ClickProfile.DESCRIPTOR_USER_DESC, BluetoothGattDescriptor.PERMISSION_READ);
        interactor.addDescriptor(interactorDescription);

        service.addCharacteristic(counter);
        service.addCharacteristic(interactor);

        return service;
    }

    private void notifyRegisteredDevices() {
        if (bluetoothDevices.isEmpty()) {
            return;
        }

        Log.i(TAG, "Sending update to " + bluetoothDevices.size() + " subscribers");
        for (BluetoothDevice device : bluetoothDevices) {
            BluetoothGattCharacteristic counterCharacteristic = bluetoothGattServer
                    .getService(ClickProfile.SERVICE_UUID)
                    .getCharacteristic(ClickProfile.CHARACTERISTIC_COUNTER_UUID);
            byte[] value = gattServerListener.onCounterRead();
            counterCharacteristic.setValue(value);
            bluetoothGattServer.notifyCharacteristicChanged(device, counterCharacteristic, false);
        }
    }

    private void stopServer() {
        if (bluetoothGattServer == null) {
            return;
        }
        bluetoothGattServer.close();
    }

}
