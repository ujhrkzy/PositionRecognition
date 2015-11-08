package com.ujhrkzy.accelerometersender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.util.Log;

public class BluetoothTask {
    private static final String TAG = "BluetoothTask";
    /**
     * UUIDはサーバと一致している必要がある。 - 独自サービスのUUIDはツールで生成する。（ほぼ乱数） - 注：このまま使わないように。
     */
    private static final UUID APP_UUID = UUID
            .fromString("11111111-1111-1111-1111-111111111123");

    private final MainActivity activity;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice = null;
    private BluetoothSocket bluetoothSocket;
    private InputStream btIn;
    private OutputStream btOut;

    public BluetoothTask(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * Bluetoothの初期化。
     */
    public void init() {
        // BTアダプタ取得。取れなければBT未実装デバイス。
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            activity.errorDialog("This device is not implement Bluetooth.");
            return;
        }
        // BTが設定で有効になっているかチェック。
        if (!bluetoothAdapter.isEnabled()) {
            activity.errorDialog("This device is disabled Bluetooth.");
            return;
        }
    }

    /**
     * @return ペアリング済みのデバイス一覧を返す。デバイス選択ダイアログ用。
     */
    public Set<BluetoothDevice> getPairedDevices() {
        return bluetoothAdapter.getBondedDevices();
    }

    /**
     * 非同期で指定されたデバイスの接続を開始する。 - 選択ダイアログから選択されたデバイスを設定される。
     * 
     * @param device
     *            選択デバイス
     */
    public void doConnect(BluetoothDevice device) {
        bluetoothDevice = device;
        try {
            bluetoothSocket = bluetoothDevice
                    .createRfcommSocketToServiceRecord(APP_UUID);
            new ConnectTask().execute();
        } catch (IOException e) {
            Log.e(TAG, e.toString(), e);
            activity.errorDialog(e.toString());
        }
    }

    /**
     * 非同期でBluetoothの接続を閉じる。
     */
    public void doClose() {
        new CloseTask().execute();
    }

    public AccelerometerEventListener createAccelerometerEventListener() {
        return new AccelerometerEventListener() {

            @Override
            public void accept(AccelerometerValue value) {
                String msg = String.format("x:%s,y:%s,z:%s", value.getValueX(),
                        value.getValueY(), value.getValueZ());
                doSend(msg);
            }
        };
    }

    /**
     * 非同期でメッセージの送受信を行う。
     * 
     * @param msg
     *            送信メッセージ.
     */
    public void doSend(String msg) {
        new SendTask().doInBackground(msg);
        // new SendTask().execute(msg);
    }

    /**
     * サーバとメッセージの送受信を行う非同期タスク。
     */
    private class SendTask extends AsyncTask<String, Void, Object> {
        @Override
        protected Object doInBackground(String... params) {
            if (btOut == null) {
                return null;
            }
            try {
                byte[] bytes = params[0].getBytes("UTF-8");
                btOut.write(bytes);
                btOut.flush();
                return null;
            } catch (Throwable t) {
                doClose();
                return t;
            }
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Exception) {
                Log.e(TAG, result.toString(), (Throwable) result);
                activity.errorDialog(result.toString());
            }
        }
    }

    /**
     * Bluetoothと接続を開始する非同期タスク。 - 時間がかかる場合があるのでProcessDialogを表示する。 -
     * 双方向のストリームを開くところまで。
     */
    private class ConnectTask extends AsyncTask<Void, Void, Object> {
        @Override
        protected void onPreExecute() {
            activity.showWaitDialog("Connect Bluetooth Device.");
        }

        @Override
        protected Object doInBackground(Void... params) {
            try {
                bluetoothSocket.connect();
                btIn = bluetoothSocket.getInputStream();
                btOut = bluetoothSocket.getOutputStream();
            } catch (Throwable t) {
                doClose();
                return t;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Throwable) {
                Log.e(TAG, result.toString(), (Throwable) result);
                activity.errorDialog(result.toString());
            } else {
                activity.hideWaitDialog();
            }
        }
    }

    /**
     * Bluetoothと接続を終了する非同期タスク。 - 不要かも知れないが念のため非同期にしている。
     */
    private class CloseTask extends AsyncTask<Void, Void, Object> {
        @Override
        protected Object doInBackground(Void... params) {
            try {
                try {
                    btOut.close();
                } catch (Throwable t) {
                }
                try {
                    btIn.close();
                } catch (Throwable t) {/* ignore */
                }
                bluetoothSocket.close();
            } catch (Throwable t) {
                return t;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Throwable) {
                Log.e(TAG, result.toString(), (Throwable) result);
                activity.errorDialog(result.toString());
            }
        }
    }
}
