import com.acs.smartcard.Reader;
import com.acs.smartcard.ReaderException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

/**** DEMO APP ****/

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback; // scanner
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;  // scanner
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.acs.bluetooth.Acr1255uj1Reader;
import com.acs.bluetooth.Acr1255uj1Reader.OnBatteryLevelAvailableListener;
import com.acs.bluetooth.Acr1255uj1Reader.OnBatteryLevelChangeListener;
import com.acs.bluetooth.Acr3901us1Reader;
import com.acs.bluetooth.Acr3901us1Reader.OnBatteryStatusAvailableListener;
import com.acs.bluetooth.Acr3901us1Reader.OnBatteryStatusChangeListener;
import com.acs.bluetooth.BluetoothReader;
import com.acs.bluetooth.BluetoothReader.OnAtrAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnAuthenticationCompleteListener;
import com.acs.bluetooth.BluetoothReader.OnCardPowerOffCompleteListener;
import com.acs.bluetooth.BluetoothReader.OnCardStatusAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnCardStatusChangeListener;
import com.acs.bluetooth.BluetoothReader.OnDeviceInfoAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnEnableNotificationCompleteListener;
import com.acs.bluetooth.BluetoothReader.OnEscapeResponseAvailableListener;
import com.acs.bluetooth.BluetoothReader.OnResponseApduAvailableListener;
import com.acs.bluetooth.BluetoothReaderGattCallback;
import com.acs.bluetooth.BluetoothReaderGattCallback.OnConnectionStateChangeListener;
import com.acs.bluetooth.BluetoothReaderManager;
import com.acs.bluetooth.BluetoothReaderManager.OnReaderDetectionListener;

public class BleBizPlugin extends CordovaPlugin {

    // adb logcat bizcode:D SystemWebViewClient:D *:S --- show plugin logs
    // adb logcat Bth:D SystemWebViewClient:D *:S

    public static final String TAG = "bizcode"; 
    /* Detected reader. */
    //private BluetoothReader mBluetoothReader;
    /* ACS Bluetooth reader library. */
    private BluetoothReaderManager mBluetoothReaderManager;
    private BluetoothReaderGattCallback mGattCallback;  // Ble
    /* Bluetooth GATT client. */
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice connectedDevice;

    private static final String LISTEN = "listen";
    private static final String PING = "ping";
    /* Default master key. */
    private static final String DEFAULT_1255_MASTER_KEY = "ACR1255U-J1 Auth";
    /* BIZCODE LEGGERE UID */
    private static final String UID_1255_APDU_COMMAND = "FF CA 00 00 00";

    public static boolean isConnected = false;
    /* Read 16 bytes from the binary block 0x04 (MIFARE 1K or 4K). */
    //private static final String DEFAULT_1255_APDU_COMMAND = "FF B0 00 04 01";

    /* Reader to be connected. */
    private String mDeviceAddress;

    private static final byte[] AUTO_POLLING_START = { (byte) 0xE0, 0x00, 0x00, 0x40, 0x01 };
    private static final byte[] AUTO_POLLING_STOP = { (byte) 0xE0, 0x00, 0x00, 0x40, 0x00 };

    private CallbackContext callback;  // al primo passaggio in listen la setto e la invoco ad ogni plugin result

    private Context context; // = this.cordova.getActivity().getApplicationContext();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        /**
         * INIZIALIZZO
         * inizialize diverso da onreceive
         */

        //https://ourcodeworld.com/articles/read/320/how-to-get-the-the-context-within-a-cordova-plugin-in-android
        context = this.cordova.getActivity().getApplicationContext();
        
        Log.i(TAG, "CALLED FUNCT initialize ");

        // inizializzazione manager
        BluetoothManager bluetoothManager = null;
        // personalizzato getSystemService per cordova
        bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.i(TAG, "Unable to initialize BluetoothManager.");
            return;
        }
        else{
            Log.i(TAG, "I was able to initialize BluetoothManager.");
        }

        /** l'adapter mi serve inizializzato per fare scansione bluetooth */
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.i(TAG, "Unable to obtain a BluetoothAdapter.");
            return;
        }

        /* Initialize mBluetoothReaderManager. */
        mBluetoothReaderManager = new BluetoothReaderManager();

        /* Register BluetoothReaderManager's listeners */
        mBluetoothReaderManager.setOnReaderDetectionListener(new OnReaderDetectionListener() {
            @Override
            public void onReaderDetection(BluetoothReader reader) {
                Log.i(TAG, "CALLED FUNCT onReaderDetection");
                try{ 
                    if (reader instanceof Acr3901us1Reader) {
                        /* The connected reader is ACR3901U-S1 reader. */
                        Log.i(TAG, "On Acr3901us1Reader Detected.");
                    } else if (reader instanceof Acr1255uj1Reader) {
                        /* The connected reader is ACR1255U-J1 reader. */
                        Log.i(TAG, "On Acr1255uj1Reader Detected.");

                        setListener(reader);
                        activateReader(reader);

                        // prima qui cercavo di autenticare il device - spostato in notification success
                    }
                } catch(RuntimeException e) {
                    Log.e(TAG, e.getMessage(), e);
                    Log.i(TAG, "Eruras 1 " + e.getMessage());
                }
            }
        });

        /* Register BluetoothReaderGattCallback's listeners */
        mGattCallback = new BluetoothReaderGattCallback();
        // CHIAMATA QUANDO LA CONNESSIONE CON IL DEVICE HSA SUCCESSO  file:///D:/htdocs/ble_docs/ACS_BT_EVK_Android-1.01/Documents/Application%20Programming%20Interface/index.html
        mGattCallback.setOnConnectionStateChangeListener(new OnConnectionStateChangeListener() {
            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int state, final int newState) {
                Log.i(TAG, "CALLED FUNCT onConnectionStateChange");
                try {
                    Log.i(TAG, "connection state changed - gatt string: " + gatt.toString());
                    // connection state changed android.bluetooth.BluetoothGatt@920dd88
                    // connection state changed android.bluetooth.BluetoothGatt@7aac346
                    // if (state != BluetoothGatt.GATT_SUCCESS) {
                    //     /*0
                    //      * Show the message on fail to
                    //      * connect/disconnect.
                    //      */
                    //     mConnectState = BluetoothReader.STATE_DISCONNECTED;

                    //     if (newState == BluetoothReader.STATE_CONNaaECTED) {
                    //         mTxtConnectionState
                    //                 .setText(R.string.connect_fail);
                    //     } else if (newState == BluetoothReader.STATE_DISCONNECTED) {
                    //         mTxtConnectionState
                    //                 .setText(R.string.disconnect_fail);
                    //     }
                    //     clearAllUi(); 
                    //     updateUi(null);
                    //     invalidateOptionsMenu();
                    //     return;
                    // }

                    // updateConnectionState(newState);

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "setto isconnected a true in quanto penso di essere connesso");
                        isConnected = true;
                        /* Detect the connected reader. */
                        //Log.i(TAG, "mBluetoothReaderManager " + mBluetoothReaderManager.toString());
                        if (mBluetoothReaderManager != null) {
                            mBluetoothReaderManager.detectReader(gatt, mGattCallback);
                            Log.i(TAG, "mBluetoothReaderManager successfully detected reader" + mBluetoothReaderManager.toString());
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "mBluetoothReaderManager reader was disconnected " + mBluetoothReaderManager.toString() + " we try onDisconnectReconnect");
                        onDisconnectedTryReconnect();
                    }
                } catch(RuntimeException e) {
                    Log.e(TAG, e.getMessage(), e);
                    Log.i(TAG, "Eruras 2 " + e.getMessage());
                }
            }
        });
    }


    private void onDisconnectedTryReconnect(){  // final ?????????
        Log.i(TAG, "start onDisconnectedTryReconnect " + mBluetoothGatt.toString());
        Log.i(TAG, "setto isconnected a false in quanto sono entrato in ondisconnectedtryreconnect");
        isConnected = false;
        /*
         * Release resources occupied by Bluetooth
         * GATT client.
         */
        if (mBluetoothGatt != null) {
            // IMPORTANT closing gatt bluetooth android connection - if you reach the limit the read won' t connect anymore until you reboot bluetooth on your android device 
            Log.i(TAG, "onDisconnectedTryReconnect - closing connection mBluetoothGatt");
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            //Log.i(TAG, "This is the bluetooth manager " + mBluetoothGatt.toString());
        }
        // after reader disconnection restart scan
        //mScanning = true;
        bluetoothAdapter.startLeScan(mLeScanCallback); // callback at the end of file
    }
    

    /*
     * Update listener
     */
    private void setListener(BluetoothReader reader) {
        Log.i(TAG, "START set listener");

        if (reader instanceof Acr1255uj1Reader) {
            ((Acr1255uj1Reader) reader).setOnBatteryLevelChangeListener(new OnBatteryLevelChangeListener() {
                @Override
                public void onBatteryLevelChange(BluetoothReader bluetoothReader, final int batteryLevel) {
                    Log.i(TAG, "mBatteryLevelListener data: "+ batteryLevel);
                }
            });
        }

        reader.setOnResponseApduAvailableListener(new OnResponseApduAvailableListener() {
            @Override
            public void onResponseApduAvailable(BluetoothReader bluetoothReader, final byte[] apdu, final int errorCode) {
                String apdu_response = getResponseString(apdu, errorCode);
                Log.i(TAG, "setOnResponseApduAvailableListener apdu_response : "+ apdu_response);

                String card_uid = getResponseString(apdu, errorCode);
                String codice_verifica = card_uid.substring(card_uid.length() - 4);
                if (card_uid.length() > 4) {
                    card_uid = card_uid.substring(0, card_uid.length() - 4);  // last 4 chars call status - 9000 success
                    card_uid = card_uid.replaceAll("..(?!$)", "$0:");
                }

                Log.i(TAG, "card_uid: "+ card_uid);

                //
                PluginResult result = new PluginResult(PluginResult.Status.OK, card_uid);
                result.setKeepCallback(true);
                callback.sendPluginResult(result);

            }
        });

        reader.setOnCardStatusChangeListener(new OnCardStatusChangeListener() {
            @Override
            public void onCardStatusChange(BluetoothReader bluetoothReader, final int sta) {
                Log.i(TAG, "mCardStatusListener sta: " + sta);
                Log.i(TAG, "mCardStatusListener staTUS: " + getCardStatusString(sta));
                if(sta == 2){  // 2 È CODICE CARTA PRESENT.  // 255 power saving mode, keep alive
                    //byte[] apduCommand = UID_1255_APDU_COMMAND.getBytes(StandardCharsets.UTF_8);
                    byte[] apduCommand = {(byte)0xFF, (byte)0xCA, (byte)0x00, (byte)0x00, (byte)0x00};

                    if (apduCommand != null && apduCommand.length > 0) {
                        reader.transmitApdu(apduCommand);
                    } else {
                        Log.i(TAG, "apdu not valid");
                    }
                }
                // sleep mode
                /*if(sta == 255){
                    reader.powerOffCard();
                }*/
            }
        });

        /* Handle on slot status available. */
        reader.setOnCardStatusAvailableListener(new OnCardStatusAvailableListener() {
            @Override
            public void onCardStatusAvailable(BluetoothReader bluetoothReader, final int cardStatus, final int errorCode) {
                Log.i(TAG, "CALLED FUNCT onCardStatusAvailable");
                if (errorCode != BluetoothReader.ERROR_SUCCESS) {
                    Log.i(TAG, "onCardStatusAvailable FAIL: "+ getErrorString(errorCode));
                } else {
                    Log.i(TAG, "onCardStatusAvailable SUCCESS: "+ getCardStatusString(cardStatus));
                }
            }
        });

        reader.setOnAuthenticationCompleteListener(new OnAuthenticationCompleteListener() {
            @Override
            public void onAuthenticationComplete(BluetoothReader bluetoothReader, final int errorCode) {
                Log.i(TAG, "CALLED FUNCT onAuthenticationComplete ");
                try {
                    if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                        Log.i(TAG, "Authentication success");
                        bluetoothReader.transmitEscapeCommand(AUTO_POLLING_START);
                    } else {
                        Log.i(TAG, "Authentication failed");
                    }
                } 
                catch(RuntimeException e) {
                    Log.e(TAG, e.getMessage(), e);
                    Log.i(TAG, "Eruras 3 " + e.getMessage());
                }
            }
        });

        reader.setOnEnableNotificationCompleteListener(new OnEnableNotificationCompleteListener() {
            @Override
            public void onEnableNotificationComplete(BluetoothReader bluetoothReader, final int result) {
                try {
                    if (result != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "The device is unable to set notification!");
                    } else {
                        Log.i(TAG, "The device is ready to use!");

                        // incollo qui il blocco che era in setOnReaderDetectionListener
                        byte[] masterKey = DEFAULT_1255_MASTER_KEY.getBytes(StandardCharsets.UTF_8);
                        String mk_str = new String(masterKey, StandardCharsets.UTF_8);
                        Log.i(TAG, "master key " + mk_str);
                        // byte[] masterKey3 = DEFAULT_1255_MASTER_KEY.getBytes(StandardCharsets.US_ASCII);
                        // String mk3_str = new String(masterKey3, StandardCharsets.US_ASCII);
                        // Log.i(TAG, "master key 3 " + mk3_str);


                        String mk4_str = toHexString(masterKey);
                        Log.i(TAG, "string master key 4 " + mk4_str); // master key 4master key 4master key 4
                        byte[] masterKey4 = hexString2Bytes(mk4_str);


                        /* Retrieve master key from edit box. */
                        // byte masterKey[] = Utils.getEditTextinHexBytes("aaaaaa");

                        if (masterKey4 != null && masterKey4.length > 0) {
                            /* Clear response field for the result of authentication. */
                            Log.i(TAG, "Start Authentication");

                            /* Start authentication. */
                            if (!reader.authenticate(masterKey4)) {
                                // mTxtAuthentication.setText(R.string.card_reader_not_ready);
                                Log.i(TAG, "Authentication masterkey check failed");
                            } else {
                                Log.i(TAG, "Authentication masterkey check success");
                            }
                        } else {
                            Log.i(TAG, "Character format error!");
                        }
                    }
                } catch(RuntimeException e) {
                    Log.e(TAG, "onEnableNotificationComplete", e);
                }                
            }
        });
    }

    /* Start the process to enable the reader's notifications. */
    private void activateReader(BluetoothReader reader) {
        Log.i(TAG, "CALLED FUNCT activateReader ");
        try {
            if (reader == null) {
                return;
            }

            if (reader instanceof Acr3901us1Reader) {
                /* Start pairing to the reader. */
                 Log.i(TAG, "activateReader acr39 non supportato");
            } else if (reader instanceof Acr1255uj1Reader) {
                /* Enable notification. */
                Log.i(TAG, "activateReader ci siamo");
                reader.enableNotification(true);
            }
        }
        catch(RuntimeException e) {
            Log.e(TAG, e.getMessage(), e);
            Log.i(TAG, "Eruras 4 " + e.getMessage());
        }
    }


    /*
     * Listen to Bluetooth bond status change event. And turns on reader's
     * notifications once the card reader is bonded.
     */
    // private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

    //     public void onReceive(Context context, Intent intent) {
    //         Log.i(TAG, "START onreceive");
    //         BluetoothAdapter bluetoothAdapter = null;
    //         BluetoothManager bluetoothManager = null;
    //         final String action = intent.getAction();

    //         if (!(mBluetoothReader instanceof Acr3901us1Reader)) {
    //             /* Only ACR3901U-S1 require bonding. */
    //             return;
    //         }

    //         if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
    //             Log.i(TAG, "ACTION_BOND_STATE_CHANGED");

    //             /* Get bond (pairing) state */
    //             if (mBluetoothReaderManager == null) {
    //                 Log.i(TAG, "Unable to initialize BluetoothReaderManager.");
    //                 return;
    //             }

    //             // personalizzato getSystemService per cordova
    //             // bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
    //             // if (bluetoothManager == null) {
    //             //     Log.i(TAG, "Unable to initialize BluetoothManager.");
    //             //     return;
    //             // }

    //             bluetoothAdapter = bluetoothManager.getAdapter();
    //             if (bluetoothAdapter == null) {
    //                 Log.i(TAG, "Unable to initialize BluetoothAdapter.");
    //                 return;
    //             }

    //             final BluetoothDevice device = bluetoothAdapter
    //                     .getRemoteDevice(mDeviceAddress);
                        

    //             if (device == null) {
    //                 return;
    //             }

    //             final int bondState = device.getBondState();

    //             // TODO: remove log message
    //             /*Log.i(TAG, "BroadcastReceiver - getBondState. state = "
    //                     + getBondingStatusString(bondState));*/

    //             /* Enable notification */
    //             if (bondState == BluetoothDevice.BOND_BONDED) {
    //                 if (mBluetoothReader != null) {
    //                     mBluetoothReader.enableNotification(true);
    //                 }
    //             }
    //         }
    //         Log.i(TAG, "END onreceive");
    //     }

    // };


    private void listen(CallbackContext callbackContext) {
        try {
            Log.i(TAG, "START function listen");


            //START SCAN
            //mScanning = true;
            bluetoothAdapter.startLeScan(mLeScanCallback); // callback alla fine del file
            //invalidateOptionsMenu();

            // /*
            //  * Connect Device.
            //  */
            // /* Clear old GATT connection. */
            // if (mBluetoothGatt != null) {
            //     Log.i(TAG, "Clear old GATT connection");
            //     mBluetoothGatt.disconnect();
            //     mBluetoothGatt.close();
            //     mBluetoothGatt = null;
            // }

            // // /* Create a new connection. */
            // final BluetoothDevice device = bluetoothAdapter
            //         .getRemoteDevice("94:E3:6D:A6:6A:2B"); // mDeviceAddress

            // if (device == null) {
            //     Log.i(TAG, "Device not found. Unable to connect.");
            //     return;
            // }
            // else{
            //     Log.i(TAG, "Device connected.");
            // }

            // //* Connect to GATT server. */ lui come primo parametro usava this, siccome la mia classe, 
            // //non poteva essere convertita in context ho prelevato quest ultimo con il metodo descritto nel link sopra
            // mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
            // // return;

            // getBatteryLevelString(69);
            
            // PluginResult result = new PluginResult(PluginResult.Status.OK, "Hel5643dftg654lo world.");
            // result.setKeepCallback(true);
            callback = callbackContext;  // FONDAMENTALE
            // callbackContext.sendPluginResult(result); /**/
            // Log.i(TAG, "END function  lis04ten");
        }
        catch(RuntimeException e) {
            Log.e(TAG, e.getMessage(), e);
            Log.i(TAG, "Eruras 5 " + e.getMessage());
        }
    }

    private void ping(CallbackContext callbackContext) {
        try {
            Log.i(TAG, "START function ping reader");

            PluginResult.Status status = PluginResult.Status.OK;

            BluetoothManager bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);

            if(connectedDevice != null){
                int mConnectionState = bluetoothManager.getConnectionState(connectedDevice, BluetoothProfile.GATT);

                Log.i(TAG, "mConnectionState " + mConnectionState);

                if(isConnected){
                    Log.i(TAG, "funzione di ping e isconnected = true");
                    if (mConnectionState == BluetoothReader.STATE_CONNECTING) {
                        Log.i(TAG, "mConnectionState mi risulta che sia STATE_CONNECTING");
                    } else if (mConnectionState == BluetoothReader.STATE_CONNECTED) {
                        Log.i(TAG, "mConnectionState mi risulta che sia STATE_CONNECTED");
                    } else if (mConnectionState == BluetoothReader.STATE_DISCONNECTING) {
                        status = PluginResult.Status.ERROR;
                        Log.i(TAG, "mConnectionState mi risulta che sia STATE_DISCONNECTING, TENTO RICONNESSIONE");
                        onDisconnectedTryReconnect();
                    } else {
                        status = PluginResult.Status.ERROR;
                        Log.i(TAG, "mConnectionState mi risulta che sia STATE_DISCONNECTED, TENTO RICONNESSIONE");
                        onDisconnectedTryReconnect();
                    }
                } else {
                    Log.i(TAG, "funzione di ping e isconnected = false -> proviamo a riavviare la scansione");
                    bluetoothAdapter.stopLeScan(mLeScanCallback); // callback at the end of file
                    bluetoothAdapter.startLeScan(mLeScanCallback); // callback at the end of file
                }
            } else{
                Log.i(TAG, "connectedDevice e uguale a null -> proviamo a riavviare la scansione");
                bluetoothAdapter.stopLeScan(mLeScanCallback); // callback at the end of file
                bluetoothAdapter.startLeScan(mLeScanCallback); // callback at the end of file
            }
            

            PluginResult result = new PluginResult(status, "PING - Termino funzione di ping");
            result.setKeepCallback(true);
            // callback = callbackContext;  // FONDAMENTALE
            callbackContext.sendPluginResult(result); /**/
            // Log.i(TAG, "END function  lis04ten");
        }
        catch(RuntimeException e) {
            Log.e(TAG, e.getMessage(), e);
            Log.i(TAG, "Eruras 7 " + e.getMessage());
        }
    }


    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equalsIgnoreCase(LISTEN)) {  // ho scritto io nel js  che gli devo passare listen, fquesta e la prima funzione eseguita
            Log.i(TAG, "Execute lanciata, azione = listen");
            /*PluginResult result = new PluginResult(PluginResult.Status.OK, "Hello world. BRAO");
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);*/
            listen(callbackContext);
        } else if (action.equalsIgnoreCase(PING)) {  // ho scritto io nel js  che gli devo passare listen, fquesta e la prima funzione eseguita
            Log.i(TAG, "Execute lanciata, azione = ping");
            /*PluginResult result = new PluginResult(PluginResult.Status.OK, "Hello world. BRAO");
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);*/
            ping(callbackContext);
        } else {
            Log.i(TAG, "Execute lanciata, azione != listen");
            PluginResult result = new PluginResult(PluginResult.Status.OK, "Hello world. INVALIDO");
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            // invalid action
            return false;
        }

        return true;
    }


    /* Get the Battery level string. */
    private String getBatteryLevelString(int batteryLevel) {
        if (batteryLevel < 0 || batteryLevel > 100) {
            Log.i(TAG, "BATTERY LEVEL Unknown"); 
            return "Unknown."; 
        }
        Log.i(TAG, "BATTERY LEVEL" + String.valueOf(batteryLevel));
        return String.valueOf(batteryLevel) + "%";
    }

    /* Device scan callback. 
    boolean 	startLeScan(BluetoothAdapter.LeScanCallback callback)
    This method was deprecated in API level 21. use BluetoothLeScanner#startScan(List, ScanSettings, ScanCallback) instead. 
    https://developer.android.com/reference/android/bluetooth/BluetoothAdapter
    */
    private LeScanCallback mLeScanCallback = new LeScanCallback() {

        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            try {
                if((device.getName() == null ? "" : device.getName()).startsWith("ACR1255U-J1")) { // ACR1255U-J1-006047 p/n-seriale 
                    // IMPORTANT Clear OLD gatt bth android connection IF EXISTS - if you reach the limit the read won' t connect anymore until you reboot bluetooth on your android
                    if (mBluetoothGatt != null) {
                        try {
                            Log.i(TAG, "Clear old GATT connection");
                            connectedDevice = null;
                            mBluetoothGatt.disconnect();
                            mBluetoothGatt.close();
                            mBluetoothGatt = null;
                        } catch(RuntimeException e) {
                            Log.e(TAG, e.getMessage(), e);
                            Log.i(TAG, "Eruras 8 " + e.getMessage());
                        }
                    }
                    connectedDevice = device;
                    mDeviceAddress = device.getAddress();
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                    mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
                    Log.i(TAG, "device found - model_name: " + device.getName());
                    //Log.i(TAG, "device found: " + device.toString() + " " + device.getName() + " " + device.getAddress());        12-05 15:16:18.380 17505 17505 I bizcode : device found: 94:E3:6D:A6:6A:2B ACR1255U-J1-006047 94:E3:6D:A6:6A:2B
                }
            } catch(RuntimeException e) {
                Log.e(TAG, e.getMessage(), e);
                Log.i(TAG, "Eruras 6 " + e.getMessage());
            }
        }
    };


   /**
     * Creates a hexadecimal <code>String</code> representation of the
     * <code>byte[]</code> passed. Each element is converted to a
     * <code>String</code> via the {@link Integer#toHexString(int)} and
     * separated by <code>" "</code>. If the array is <code>null</code>, then
     * <code>""<code> is returned.
     * 
     * @param array
     *            the <code>byte</code> array to convert.
     * @return the <code>String</code> representation of <code>array</code> in
     *         hexadecimal.
     */
    public String toHexString(byte[] array) {

        String bufferString = "";

        if (array != null) {
            for (int i = 0; i < array.length; i++) {
                String hexChar = Integer.toHexString(array[i] & 0xFF);
                if (hexChar.length() == 1) {
                    hexChar = "0" + hexChar;
                }
                bufferString += hexChar.toUpperCase(Locale.US);// + " ";
            }
        }

        return bufferString;
    }

    /**
     * Creates a <code>byte[]</code> representation of the hexadecimal
     * <code>String</code> passed.
     * 
     * @param string
     *            the hexadecimal string to be converted.
     * @return the <code>array</code> representation of <code>String</code>.
     * @throws IllegalArgumentException
     *             if <code>string</code> length is not in even number.
     * @throws NullPointerException
     *             if <code>string == null</code>.
     * @throws NumberFormatException
     *             if <code>string</code> cannot be parsed as a byte value.
     */
    public byte[] hexString2Bytes(String string) {
        if (string == null)
            throw new NullPointerException("string was null");

        int len = string.length();

        if (len == 0)
            return new byte[0];
        if (len % 2 == 1)
            throw new IllegalArgumentException(
                    "string length should be an even number");

        byte[] ret = new byte[len / 2];
        byte[] tmp = string.getBytes();

        for (int i = 0; i < len; i += 2) {
            if (!isHexNumber(tmp[i]) || !isHexNumber(tmp[i + 1])) {
                throw new NumberFormatException(
                        "string contained invalid value");
            }
            ret[i / 2] = uniteBytes(tmp[i], tmp[i + 1]);
        }
        return ret;
    }

    private boolean isHexNumber(byte value) {
        if (!(value >= '0' && value <= '9') && !(value >= 'A' && value <= 'F')
                && !(value >= 'a' && value <= 'f')) {
            return false;
        }
        return true;
    }

    private byte uniteBytes(byte src0, byte src1) {
        byte _b0 = Byte.decode("0x" + new String(new byte[] { src0 }))
                .byteValue();
        _b0 = (byte) (_b0 << 4);
        byte _b1 = Byte.decode("0x" + new String(new byte[] { src1 }))
                .byteValue();
        byte ret = (byte) (_b0 ^ _b1);
        return ret;
    }

    /* Get the Card status string. */
    private String getCardStatusString(int cardStatus) {
        if (cardStatus == BluetoothReader.CARD_STATUS_ABSENT) {
            return "Absent.";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_PRESENT) {
            return "Present.";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_POWERED) {
            return "Powered.";
        } else if (cardStatus == BluetoothReader.CARD_STATUS_POWER_SAVING_MODE) {
            return "Power saving mode.";
        }
        return "The card status is unknown.";
    }

    /* Get the Error string. */
    private String getErrorString(int errorCode) {
        if (errorCode == BluetoothReader.ERROR_SUCCESS) {
            return "";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_CHECKSUM) {
            return "The checksum is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_DATA_LENGTH) {
            return "The data length is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_COMMAND) {
            return "The command is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_UNKNOWN_COMMAND_ID) {
            return "The command ID is unknown.";
        } else if (errorCode == BluetoothReader.ERROR_CARD_OPERATION) {
            return "The card operation failed.";
        } else if (errorCode == BluetoothReader.ERROR_AUTHENTICATION_REQUIRED) {
            return "Authentication is required.";
        } else if (errorCode == BluetoothReader.ERROR_LOW_BATTERY) {
            return "The battery is low.";
        } else if (errorCode == BluetoothReader.ERROR_CHARACTERISTIC_NOT_FOUND) {
            return "Error characteristic is not found.";
        } else if (errorCode == BluetoothReader.ERROR_WRITE_DATA) {
            return "Write command to reader is failed.";
        } else if (errorCode == BluetoothReader.ERROR_TIMEOUT) {
            return "Timeout.";
        } else if (errorCode == BluetoothReader.ERROR_AUTHENTICATION_FAILED) {
            return "Authentication is failed.";
        } else if (errorCode == BluetoothReader.ERROR_UNDEFINED) {
            return "Undefined error.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_DATA) {
            return "Received data error.";
        } else if (errorCode == BluetoothReader.ERROR_COMMAND_FAILED) {
            return "The command failed.";
        }
        return "Unknown error.";
    }

    /* Get the Response string. */
    private String getResponseString(byte[] response, int errorCode) {
        if (errorCode == BluetoothReader.ERROR_SUCCESS) {
            if (response != null && response.length > 0) {
                return toHexString(response);
            }
            return "";
        }
        return getErrorString(errorCode);
    }

}
