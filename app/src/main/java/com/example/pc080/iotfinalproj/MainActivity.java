package com.example.pc080.iotfinalproj;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static java.lang.Integer.parseInt;

public class MainActivity extends AppCompatActivity implements RadioGroup.OnCheckedChangeListener {
    public static final int BASIC_ID = 87;
    public static final String PREFS_NAME = "MyPrefsFile";
    public static final String TAG = MainActivity.class.getSimpleName();

    private Toast mToast;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;
    private Bitmap largeIcon;

    private TextView mTextMessage;
    private TextView txtSocket0;
    private TextView txtSocket1;
    private TextView dashTxtSocket0;
    private TextView dashTxtSocket1;
    private TextView dashTxtSocket0Time;
    private TextView dashTxtSocket1Time;
    private Switch notificationSwitch;
    private SeekBar durationSeekbar;
    private TextView txtDuration;

    private int alarmDuration;
    private boolean isNotification = false;


    private String deviceAddress;

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;

    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private Button btnConnectDisconnect;
    private ImageButton imgBtnSocket0, imgBtnSocket1;
    private Button btnTestNotification;

    private int cmd_type;
    private int socket0_status;
    private int socket1_status;

    private int socket0_DB_status;
    private int socket1_DB_status;
    private String socket0_DB_time;
    private String socket1_DB_time;
    private long socket0_millisecond_time;
    private long socket1_millisecond_time;

    private Notification notification;



    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title_home);
                    mainFunctionController(true);
                    dashboardFunctionController(false);
                    notificationFunctionController(false);
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    mainFunctionController(false);
                    dashboardFunctionController(true);
                    notificationFunctionController(false);
                    readFromDB(0);
                    readFromDB(1);
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    mainFunctionController(false);
                    dashboardFunctionController(false);
                    notificationFunctionController(true);
                    setNotification(alarmDuration);
                    return true;
            }
            return false;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int permission = ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION);

        //check Bluetooth 4.0
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        //Mainpage object
        imgBtnSocket0 = (ImageButton) findViewById(R.id.imgBtn_socket0);
        imgBtnSocket1 = (ImageButton) findViewById(R.id.imgBtn_socket1);
        imgBtnSocket0.setEnabled(false);
        imgBtnSocket1.setEnabled(false);
        btnConnectDisconnect=(Button) findViewById(R.id.btn_connect);
        //Dashboard object
        dashTxtSocket0 = (TextView) findViewById(R.id.dash_txt_socet0);
        dashTxtSocket1 = (TextView) findViewById(R.id.dash_txt_socet1);
        dashTxtSocket0Time = (TextView) findViewById(R.id.dash_txt_socket0_time);
        dashTxtSocket1Time = (TextView) findViewById(R.id.dash_txt_socket1_time);
        //Notification object
        notificationSwitch = (Switch) findViewById(R.id.notification_switch);
        durationSeekbar = (SeekBar) findViewById(R.id.duration_seekbar);
        durationSeekbar.setMax(48);
        txtDuration = (TextView) findViewById(R.id.txt_duration);
        notificationSwitch.setEnabled(false);
        durationSeekbar.setEnabled(false);
        btnTestNotification = (Button) findViewById(R.id.btn_testNotification);

        listenerInit();

        // Set initial UI state
        service_init();

        mTextMessage = (TextView) findViewById(R.id.message);
        txtSocket0 = (TextView) findViewById(R.id.txt_socket0);
        txtSocket1 = (TextView) findViewById(R.id.txt_socket1);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        dashboardFunctionController(false);

        setNotification(alarmDuration);


        // Restore preferences
        restorePreferences();

    }
    private void setNotification(long time)
    {
        time = time * 60 * 60 * 1000;
        largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_socket_on);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat.Builder(this);

        Log.d(TAG, "Current time: " + System.currentTimeMillis());

        long targetTime = System.currentTimeMillis() + time + 3000;
        Log.d(TAG, "Target time: " + targetTime );
        builder.setSmallIcon(R.drawable.ic_socket_on)
                .setLargeIcon(largeIcon)
                .setWhen(targetTime)
                .setContentTitle("Device has been used for a long time!")
                .setContentText("You should turn off your socket");

        notification = builder.build();
    }

    private void restorePreferences()
    {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        isNotification = settings.getBoolean("notification", false);
        notificationSwitch.setChecked(isNotification);
        alarmDuration = settings.getInt("alarmDuration", 0);
        durationSeekbar.setProgress(alarmDuration);
        txtDuration.setText(alarmDuration + " hour");
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            imgBtnSocket0.setEnabled(false);
            imgBtnSocket1.setEnabled(false);
            mService = null;
        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_CONNECT_MSG");
                        btnConnectDisconnect.setText("Disconnect");
                        Log.d(TAG, "Connected to: "+ mDevice.getName());
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
                //Initialize socket status
                imgBtnSocket0.postDelayed(new Runnable(){
                    @Override
                    public void run() {
                        checkSocketStatus();
                    }}, 2000);

                imgBtnSocket0.postDelayed(new Runnable(){
                    @Override
                    public void run() {
                        if(socket0_status==0)
                            imgBtnSocket0.setImageResource(R.drawable.ic_socket_off);
                        else
                            imgBtnSocket0.setImageResource(R.drawable.ic_socket_on);

                        imgBtnSocket0.setEnabled(true);

                        if(socket1_status==0)
                            imgBtnSocket1.setImageResource(R.drawable.ic_socket_off);
                        else
                            imgBtnSocket1.setImageResource(R.drawable.ic_socket_on);

                        imgBtnSocket1.setEnabled(true);
                    }}, 4000);


            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        imgBtnSocket0.setEnabled(false);
                        imgBtnSocket1.setEnabled(false);
                        btnConnectDisconnect.setText("Connect");
                        Log.d(TAG, "Disconnected to: "+ mDevice.getName());
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        deviceAddress = null;
                        //setUiState();
                    }
                });

            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                //Data received
                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String text = new String(txValue, "UTF-8");
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            //listAdapter.add("["+currentDateTimeString+"] RX: "+text);
                            Log.d(TAG, "TextIn = " + text);
                            deviceResponseHelper(text);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });



            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                imgBtnSocket0.setEnabled(false);
                imgBtnSocket1.setEnabled(false);
                mService.disconnect();
            }

        }

    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Service initialied");

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);

                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }


    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("BLE UART still in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    protected void writeToDB(int socket, int status){
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        //Write to database
        DatabaseReference myRef = database.getReference("myDevice/socket" + socket);
        myRef.child("status").setValue(status);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        Date date = calendar.getTime();
        String dateString = sdf.format(date);
        Log.d(TAG, "current time:  "+ dateString);

        myRef.child("time").setValue(dateString);
    }

    protected void readFromDB(int socket){

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            DatabaseReference myRef = database.getReference("myDevice/socket" + socket);
            //Log.d(TAG, "socket" + socket);


            if(socket == 0) {//Read Socket0
                myRef.child("status").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        socket0_DB_status = Integer.parseInt(snapshot.getValue().toString());
                        if(socket0_DB_status==1)
                            dashTxtSocket0.setText("socket 0 已開啟");
                        else
                            dashTxtSocket0.setText("socket 0 已關閉");
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });

                myRef.child("time").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        socket0_DB_time = snapshot.getValue().toString();
                        dashTxtSocket0Time.setText(socket0_DB_time);
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            Date dateParse = sdf.parse(socket0_DB_time);
                            Calendar calendar = Calendar.getInstance();
                            long nowDate = calendar.getTime().getTime();

                            long specialDate = dateParse.getTime();
                            socket0_millisecond_time = nowDate-specialDate;
                            long betweenTimeSecond = (nowDate-specialDate) / (1000);
                            long betweenTimeMinute = (nowDate-specialDate) / (1000 * 60);
                            long betweenTimeHour = (nowDate-specialDate) / (1000 * 60 * 60);
                            long betweenTimeDay = (nowDate-specialDate) / (1000 * 60 * 60 * 24);

                            if (betweenTimeSecond<60)
                                dashTxtSocket0Time.setText(betweenTimeSecond + "秒");
                                //Log.d(TAG, "betweenTimeSecond: "+betweenTimeSecond);
                            else if (betweenTimeMinute<60)
                                dashTxtSocket0Time.setText(betweenTimeMinute + "分鐘");
                                //Log.d(TAG, "betweenTimeMinute: "+betweenTimeMinute);
                            else if (betweenTimeHour<24)
                                dashTxtSocket0Time.setText(betweenTimeHour + "小時");
                                //Log.d(TAG, "betweenTimeHour: "+betweenTimeHour);
                            else
                                dashTxtSocket0Time.setText(betweenTimeDay + "天");
                                //Log.d(TAG, "betweenTimeDay: "+betweenTimeDay);
                        }catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });


            }
            if(socket == 1) {//Read Socket1
                myRef.child("status").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        socket1_DB_status = Integer.parseInt(snapshot.getValue().toString());
                        if(socket1_DB_status==1)
                            dashTxtSocket1.setText("socket 1 已開啟");
                        else
                            dashTxtSocket1.setText("socket 1 已關閉");
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });

                myRef.child("time").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        socket1_DB_time = snapshot.getValue().toString();
                        dashTxtSocket1Time.setText(socket1_DB_time);
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            Date dateParse = sdf.parse(socket1_DB_time);
                            Calendar calendar = Calendar.getInstance();
                            long nowDate = calendar.getTime().getTime();

                            long specialDate = dateParse.getTime();
                            socket1_millisecond_time = nowDate-specialDate;
                            long betweenTimeSecond = (nowDate-specialDate) / (1000);
                            long betweenTimeMinute = (nowDate-specialDate) / (1000 * 60);
                            long betweenTimeHour = (nowDate-specialDate) / (1000 * 60 * 60);
                            long betweenTimeDay = (nowDate-specialDate) / (1000 * 60 * 60 * 24);

                            if (betweenTimeSecond<60)
                                dashTxtSocket1Time.setText(betweenTimeSecond + "秒");
                            //Log.d(TAG, "betweenTimeSecond: "+betweenTimeSecond);
                            else if (betweenTimeMinute<60)
                                dashTxtSocket1Time.setText(betweenTimeMinute + "分鐘");
                            //Log.d(TAG, "betweenTimeMinute: "+betweenTimeMinute);
                            else if (betweenTimeHour<24)
                                dashTxtSocket1Time.setText(betweenTimeHour + "小時");
                                //Log.d(TAG, "betweenTimeHour: "+betweenTimeHour);
                            else
                                dashTxtSocket1Time.setText(betweenTimeDay + "天");
                            //Log.d(TAG, "betweenTimeDay: "+betweenTimeDay);
                        }catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
            }
        } else {
            showSingleToast(this, "Please connect Internet to receive latest data!", 3000);
        }
    }

    protected void mainFunctionController(boolean crtl) {
        if(crtl){
            txtSocket0.setVisibility(View.VISIBLE);
            txtSocket1.setVisibility(View.VISIBLE);
            imgBtnSocket0.setEnabled(true);
            imgBtnSocket0.setVisibility(View.VISIBLE);
            imgBtnSocket1.setEnabled(true);
            imgBtnSocket1.setVisibility(View.VISIBLE);
            btnConnectDisconnect.setEnabled(true);
            btnConnectDisconnect.setVisibility(View.VISIBLE);
        }else{
            txtSocket0.setVisibility(View.INVISIBLE);
            txtSocket1.setVisibility(View.INVISIBLE);
            imgBtnSocket0.setEnabled(false);
            imgBtnSocket0.setVisibility(View.INVISIBLE);
            imgBtnSocket1.setEnabled(false);
            imgBtnSocket1.setVisibility(View.INVISIBLE);
            btnConnectDisconnect.setEnabled(false);
            btnConnectDisconnect.setVisibility(View.INVISIBLE);
        }

    }

    protected void dashboardFunctionController(boolean crtl) {
        if(crtl){
            dashTxtSocket0.setVisibility(View.VISIBLE);
            dashTxtSocket1.setVisibility(View.VISIBLE);
            dashTxtSocket0Time.setVisibility(View.VISIBLE);
            dashTxtSocket1Time.setVisibility(View.VISIBLE);
        }else{
            dashTxtSocket0.setVisibility(View.INVISIBLE);
            dashTxtSocket1.setVisibility(View.INVISIBLE);
            dashTxtSocket0Time.setVisibility(View.INVISIBLE);
            dashTxtSocket1Time.setVisibility(View.INVISIBLE);
        }
    }

    protected void notificationFunctionController(boolean crtl) {
        if(crtl){
            notificationSwitch.setVisibility(View.VISIBLE);
            durationSeekbar.setVisibility(View.VISIBLE);
            txtDuration.setVisibility(View.VISIBLE);
            btnTestNotification.setVisibility(View.VISIBLE);
            notificationSwitch.setEnabled(true);
            durationSeekbar.setEnabled(true);
            btnTestNotification.setEnabled(true);
        }else{
            notificationSwitch.setVisibility(View.INVISIBLE);
            durationSeekbar.setVisibility(View.INVISIBLE);
            txtDuration.setVisibility(View.INVISIBLE);
            btnTestNotification.setVisibility(View.INVISIBLE);
            notificationSwitch.setEnabled(false);
            durationSeekbar.setEnabled(false);
            btnTestNotification.setEnabled(false);
        }
    }

    protected void listenerInit(){

        imgBtnSocket0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imgBtnSocket0.setEnabled(false);
                imgBtnSocket1.setEnabled(false);
                imgBtnSocket0.setImageResource(R.drawable.ic_socket_disable);
                imgBtnSocket1.setImageResource(R.drawable.ic_socket_disable);
                checkSocketStatus();
                imgBtnSocket0.postDelayed(new Runnable(){
                    @Override
                    public void run() {
                        String message = "";
                        if(socket0_status==0) {
                            message = "0,0,on";
                            writeToDB(0, 1);
                        } else{
                            message = "0,0,off";
                            writeToDB(0, 0);
                        }
                        byte[] value;
                        try {
                            //send data to service
                            value = message.getBytes("UTF-8");
                            mService.writeRXCharacteristic(value);
                            //Update the log with time stamp
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            Log.d(TAG, "["+currentDateTimeString+"] TX: "+ message);


                            imgBtnSocket0.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    imgBtnSocket0.setEnabled(true);
                                    if(socket0_status==1)//current status is on then change image to off
                                        imgBtnSocket0.setImageResource(R.drawable.ic_socket_off);
                                    else
                                        imgBtnSocket0.setImageResource(R.drawable.ic_socket_on);
                                    imgBtnSocket1.setEnabled(true);
                                    if(socket1_status==0)
                                        imgBtnSocket1.setImageResource(R.drawable.ic_socket_off);
                                    else
                                        imgBtnSocket1.setImageResource(R.drawable.ic_socket_on);
                                }
                            }, 2000);
                        } catch (UnsupportedEncodingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }}, 2000);
            }
        });


        imgBtnSocket1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imgBtnSocket0.setEnabled(false);
                imgBtnSocket1.setEnabled(false);
                imgBtnSocket0.setImageResource(R.drawable.ic_socket_disable);
                imgBtnSocket1.setImageResource(R.drawable.ic_socket_disable);
                checkSocketStatus();
                imgBtnSocket1.postDelayed(new Runnable(){
                    @Override
                    public void run() {
                        String message = "";
                        if(socket1_status==0) {
                            message = "0,1,on";
                            writeToDB(1, 1);
                        } else{
                            message = "0,1,off";
                            writeToDB(1, 0);
                        }
                        byte[] value;
                        try {
                            //send data to service
                            value = message.getBytes("UTF-8");
                            mService.writeRXCharacteristic(value);
                            //Update the log with time stamp
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            Log.d(TAG, "["+currentDateTimeString+"] TX: "+ message);
                            imgBtnSocket1.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    imgBtnSocket0.setEnabled(true);
                                    if(socket0_status==0)
                                        imgBtnSocket0.setImageResource(R.drawable.ic_socket_off);
                                    else
                                        imgBtnSocket0.setImageResource(R.drawable.ic_socket_on);
                                    imgBtnSocket1.setEnabled(true);
                                    if(socket1_status==1)//current status is on then change image to off
                                        imgBtnSocket1.setImageResource(R.drawable.ic_socket_off);
                                    else
                                        imgBtnSocket1.setImageResource(R.drawable.ic_socket_on);
                                }
                            }, 2000);
                        } catch (UnsupportedEncodingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }}, 2000);
            }
        });


        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                    if (btnConnectDisconnect.getText().equals("Connect")){

                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices

                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice!=null)
                        {
                            imgBtnSocket0.setEnabled(false);
                            imgBtnSocket1.setEnabled(false);
                            imgBtnSocket0.setImageResource(R.drawable.ic_socket_disable);
                            imgBtnSocket1.setImageResource(R.drawable.ic_socket_disable);
                            mService.disconnect();
                        }
                    }
                }
            }
        });

        durationSeekbar.setOnSeekBarChangeListener(new durationSeekbarListener());
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isNotification = true;
                    setNotification(alarmDuration);
                    notificationManager.notify(BASIC_ID, notification);
                } else {
                    isNotification = false;
                    setNotification(alarmDuration);
                    notificationManager.cancel(BASIC_ID);
                }
            }
        });

        btnTestNotification.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                setNotification(alarmDuration);
                notificationManager.notify(BASIC_ID, notification);
            }
        });
    }

    private class durationSeekbarListener implements SeekBar.OnSeekBarChangeListener{
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(fromUser) {
                alarmDuration = progress;
                Log.d(TAG, "progress:" + alarmDuration);
                txtDuration.setText(alarmDuration + " hour");
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "progress:" + alarmDuration);
            txtDuration.setText(alarmDuration + " hour");
        }
    }

    protected void deviceResponseHelper(String text) {
        String[] temp = text.split(",");

        cmd_type = Integer.parseInt(temp[0]);
        Log.d(TAG, "cmd_type: " + cmd_type);
        if(cmd_type == 1)
        {
            socket0_status = Integer.parseInt(temp[1]);
            socket1_status = Integer.parseInt(temp[2]);
            //Log.d(TAG, "socket0_status:" + socket0_status);
            //Log.d(TAG, "socket1_status:" + socket1_status);
        }

    }

    protected void checkSocketStatus() {
        String message= "1,0,0";
        byte[] value;
        try {
            //send data to service
            value = message.getBytes("UTF-8");
            mService.writeRXCharacteristic(value);
            //Update the log with time stamp
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }
    @Override
    public void onStop()
    {
        super.onStop();
        Log.d(TAG, "onStop()");
        // We need an Editor object to make preference changes.
        // All objects are from android.context.Context
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("notification", isNotification);
        editor.putInt("alarmDuration", alarmDuration);

        // Commit the edits!
        editor.commit();
    }

    public void showSingleToast(Context context, String text, int duration) {
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(context, text, duration);
        mToast.show();
    }

}
