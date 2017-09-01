package kg.delletenebre.callsassistant;

import android.Manifest;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatDelegate;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Base64;

import com.android.internal.telephony.ITelephony;
import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App extends Application {
    private static App sSelf;
    public static App getInstance() {
        return sSelf;
    }

    protected static final String ACTION_CALL_ANSWER = "kg.calls.assistant.call.answer";
    protected static final String ACTION_CALL_DISMISS = "kg.calls.assistant.call.dismiss";
    protected static final String ACTION_EVENT = "kg.calls.assistant.event";
    protected static final String ACTION_SMS = "kg.calls.assistant.sms";
    protected static final String ACTION_GPS = "kg.calls.assistant.gps";

    private SharedPreferences mPrefs;
    private BluetoothService mBluetoothService;
    private LocationManager mLocationManager;


    @Override
    public void onCreate() {
        super.onCreate();
        sSelf = this;

        new Instabug.Builder(this, "23b86d379605928939e2f5f87947cc5d")
                .setInvocationEvent(InstabugInvocationEvent.SHAKE)
                .build();

        setTheme(R.style.AppTheme);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mLocationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CALL_ANSWER);
        filter.addAction(ACTION_CALL_DISMISS);
        filter.addAction(ACTION_SMS);
        filter.addAction(ACTION_GPS);
        filter.addAction(ACTION_EVENT);
        filter.setPriority(999);
        registerReceiver(new EventsReceiver(), filter);

        mBluetoothService = new BluetoothService();
        mBluetoothService.setOnDataReceivedListener(new BluetoothService.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String btMessage, String deviceAddress) {
                Debug.log("receive: " + btMessage);
                try {
                    JSONObject data = new JSONObject(btMessage);
                    String event = data.getString("event");
                    String number = data.getString("number");

                    if (event.equals("response")) {
                        String action = data.getString("action");
                        switch (action) {
                            case "cd": // Call Dismiss
                                sendBroadcast(new Intent(ACTION_CALL_DISMISS));
                                break;
                            case "ca": // Call Answer
                                sendBroadcast(new Intent(ACTION_CALL_ANSWER));
                                break;
                            case "s1":
                            case "s2":
                            case "s3":
                                String smsButtonNumber = action.substring(1);
                                Intent smsIntent = new Intent(ACTION_SMS);
                                smsIntent.putExtra("phoneNumber", number);
                                smsIntent.putExtra("buttonNumber", smsButtonNumber);
                                sendBroadcast(smsIntent);
                                break;
                            case "gps":
                                Intent gpsIntent = new Intent(ACTION_GPS);
                                gpsIntent.putExtra("phoneNumber", number);
                                gpsIntent.putExtra("coordinates", data.getString("extra"));
                                sendBroadcast(gpsIntent);
                                break;
                        }
                    } else {
                        String contactName = new String(data.getString("name").getBytes("ISO-8859-1"), "UTF-8");
                        String message = new String(data.getString("message").getBytes("ISO-8859-1"), "UTF-8");
                        String type = data.getString("type");
                        String contactPhoto = data.getString("photo");
                        String state = data.getString("state");
                        String buttons = data.getString("buttons");

                        Debug.log("======== Received data ========");
                        Debug.log("RECEIVED event: " + event);
                        Debug.log("RECEIVED type: " + type);
                        Debug.log("RECEIVED number: " + number);
                        Debug.log("RECEIVED state: " + state);
                        Debug.log("RECEIVED name: " + contactName);
                        Debug.log("RECEIVED photo: " + contactPhoto);
                        Debug.log("RECEIVED message: " + message);
                        Debug.log("RECEIVED deviceAddress: " + deviceAddress);
                        Debug.log("======== ======== ==== ========");

                        Intent intent = new Intent(ACTION_EVENT);
                        intent.putExtra("event", event);
                        intent.putExtra("type", type);
                        intent.putExtra("number", number);
                        intent.putExtra("state", state);
                        intent.putExtra("name", contactName);
                        intent.putExtra("photo", contactPhoto);
                        intent.putExtra("message", message);
                        intent.putExtra("buttons", buttons);
                        intent.putExtra("deviceAddress", deviceAddress);
                        sendBroadcast(intent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }




//                try {
//                    final JSONObject data = new JSONObject(btMessage);
//                    final String event = data.getString("event");
//
//                    if (event.equals("response")) {
//                        String number = data.getString("number");
//                        String action = data.getString("action");
//                        String extra = data.getString("extra");
//                        String message;
//
//                        switch (action) {
//                            case "cd": // Call Dismiss
//                                sendBroadcast(new Intent(ACTION_CALL_DISMISS));
//                                endCall();
//                                break;
//
//                            case "ca": // Call Answer
//                                // no "legal" way to answer programmatically
//                                sendBroadcast(new Intent(ACTION_CALL_ANSWER));
//                                break;
//
//                            case "s1":
//                            case "s2":
//                            case "s3":
//                                String smsButtonNumber = action.substring(1);
//
//                                Intent smsIntent = new Intent(ACTION_SMS);
//                                smsIntent.putExtra("buttonNumber", smsButtonNumber);
//                                sendBroadcast(smsIntent);
//
//                                endCall();
//                                message = getPrefs().getString("message_sms_" + smsButtonNumber,
//                                        getString(R.string.pref_default_message));
//                                sendSMS(number, message);
//                                break;
//
//                            case "gps":
//                                sendBroadcast(new Intent(ACTION_GPS));
//                                endCall();
//
//                                message = getLocationSMS(getPrefs().getString("message_gps",
//                                        getString(R.string.pref_default_message_gps)), extra);
//                                sendSMS(number, message);
//                                break;
//                        }
//
//                    } else {
//                        Debug.log("Command received");
//
//                        final String contactName = new String(data.getString("name").getBytes("ISO-8859-1"), "UTF-8");
//                        final String message = new String(data.getString("message").getBytes("ISO-8859-1"), "UTF-8");
//                        final String type = data.getString("type");
//                        final String number = data.getString("number");
//                        final String contactPhoto = data.getString("photo");
//                        final String state = data.getString("state");
//                        final String buttons = data.getString("buttons");
//
//                        Debug.log("======== Received data ========");
//                        Debug.log("RECEIVED event: " + event);
//                        Debug.log("RECEIVED type: " + type);
//                        Debug.log("RECEIVED number: " + number);
//                        Debug.log("RECEIVED state: " + state);
//                        Debug.log("RECEIVED name: " + contactName);
//                        Debug.log("RECEIVED photo: " + contactPhoto);
//                        Debug.log("RECEIVED message: " + message);
//                        Debug.log("======== ======== ==== ========");
//
//                        if ((event.equals("sms") && mPrefs.getBoolean("noty_show_sms", true))
//                                || (event.equals("call") && mPrefs.getBoolean("noty_show_calls", true))) {
//                            NotyOverlay noty = NotyOverlay.create(deviceAddress, number, event);
//                            if (state.equals("idle") || state.equals("missed")) {
//                                noty.close();
//                            } else {
//                                noty.show(type, state, contactName, contactPhoto, message, buttons);
//                            }
//                        }
//                    }
//                } catch (Exception e) {
//                    Debug.error(e.getLocalizedMessage());
//                }

                restartBluetoothCommunication();
            }
        });

        startBluetoothCommunication();
    }

    public SharedPreferences getPrefs()
    {
        return mPrefs;
    }
    public void startBluetoothCommunication() {
        mBluetoothService.startWaitingConnections();
    }
    public void stopBluetoothCommunication() {
        mBluetoothService.stop();
    }
    public void restartBluetoothCommunication() {
        stopBluetoothCommunication();
        startBluetoothCommunication();
    }

    public void connectAndSend(String address, String data) {
        mBluetoothService.connectAndSend(address, data);
    }
    public void connectAndSend(String data) {
        connectAndSend(mPrefs.getString("bluetooth_device", ""), data);
    }

    public JSONObject createJsonData(String event, String type, String state,
                                     String phoneNumber, String message) {
        Map<String,String> contact = getContactInfo(phoneNumber);

        JSONObject info = new JSONObject();
        try {
            info.put("event", event);
            info.put("type", type);
            info.put("number", phoneNumber);
            info.put("buttons", getEnabledButtons());

            info.putOpt("state", state);
            info.putOpt("name", contact.get("name"));
            info.putOpt("photo", contact.get("photo"));
            info.putOpt("message", message);
        } catch (JSONException e) {
            Debug.error("JSON object error");
        }

        Debug.log("createJsonData: " + info.toString());

        return info;
    }

    private String getEnabledButtons() {
        StringBuilder resultBuilder = new StringBuilder();
        String[] buttons = {"s1", "s2", "s3", "gps"};
        char divider = ','; // TODO CHECK TRANSLATION

        if (mPrefs != null) {
            for (int i = 0; i < buttons.length; i++) {
                if (mPrefs.getBoolean(buttons[i], false)) {
                    resultBuilder.append(buttons[i]);
                    resultBuilder.append(divider);
                } else if (i == 0 && !mPrefs.contains(buttons[i])) {
                    resultBuilder.append(buttons[i]);
                    resultBuilder.append(divider);
                }
            }

            int resultBuilderLength = resultBuilder.length();
            if (resultBuilderLength > 0) {
                resultBuilder.deleteCharAt(resultBuilderLength - 1);
            }
        }

        return resultBuilder.toString();
    }

    public String createResponseData(String action, String phoneNumber, String deviceAddress, String extra) {
        JSONObject response = new JSONObject();
        try {
            response.put("event", "response");
            response.put("action", action);
            response.put("number", phoneNumber);
            response.put("address", deviceAddress);
            response.put("extra", extra);
        } catch (JSONException e) {
            Debug.error("JSON object error");
        }

        Debug.log("createResponseData: " + response.toString());

        return response.toString();
    }

    public String createResponseData(String action, String phoneNumber, String deviceAddress) {
        return createResponseData(action, phoneNumber, deviceAddress, "");
    }

    public String createResponseData(String action, String deviceAddress) {
        return createResponseData(action, "", deviceAddress, "");
    }

    public Map<String,String> getContactInfo(String phoneNumber) {
        Map<String,String> contact = new HashMap<>();
        contact.put("name", phoneNumber);
        contact.put("photo", "");

        if (!PermissionsActivity.testPermission(Manifest.permission.READ_CONTACTS)) {
            return contact;
        }

        if (!phoneNumber.isEmpty()) {
            try {
                ContentResolver contentResolver = getContentResolver();

                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(phoneNumber));
                Cursor cursor = contentResolver.query(uri,
                        new String[]{
                                ContactsContract.Contacts._ID,
                                ContactsContract.PhoneLookup.DISPLAY_NAME},
                        null, null, null);

                if (cursor == null) {
                    return contact;
                }

                if (cursor.moveToFirst()) {
                    String name = cursor.getString(
                            cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                    contact.put("name", name);

                    long contactId = cursor.getLong(
                            cursor.getColumnIndex(ContactsContract.Contacts.Photo._ID));

                    Bitmap contactBitmap = retrieveContactPhoto(contentResolver, contactId);
                    if (contactBitmap != null) {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        contactBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                        byte[] byteArray = byteArrayOutputStream.toByteArray();

                        contact.put("photo", Base64.encodeToString(byteArray, Base64.DEFAULT));
                    }
                }

                if (!cursor.isClosed()) {
                    cursor.close();
                }

            } catch (Exception e) {
                Debug.error(e.getLocalizedMessage());
            }
        } else {
            contact.put("name", getString(R.string.private_number));
        }

        return contact;
    }

    public Map<String,String> getContactInfo(Cursor cursor) {
        Map<String,String> contact = new HashMap<>();
        contact.put("name", getString(R.string.private_number));
        contact.put("photo", "");

        if (!PermissionsActivity.testPermission(Manifest.permission.READ_CONTACTS)) {
            return contact;
        }

        try {
            ContentResolver contentResolver = getContentResolver();
            if (cursor == null) {
                return contact;
            }

            if (cursor.moveToFirst()) {
                String name = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                contact.put("name", name);

                long contactId = cursor.getLong(
                        cursor.getColumnIndex(ContactsContract.Contacts.Photo._ID));

                Bitmap contactBitmap = retrieveContactPhoto(contentResolver, contactId);
                if (contactBitmap != null) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    contactBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();

                    contact.put("photo", Base64.encodeToString(byteArray, Base64.DEFAULT));
                }

                String hasPhone = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER));

                // если есть телефоны, получаем и выводим их
                if (hasPhone.equalsIgnoreCase("1")) {
                    Cursor phones = getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId,
                            null,
                            null);

                    if (phones != null) {
                        phones.moveToNext();
                        contact.put("number", phones.getString(phones.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER)));
                        phones.close();
                    }
                }
            }

            if (!cursor.isClosed()) {
                cursor.close();
            }

        } catch (Exception e) {
            Debug.error(e.getLocalizedMessage());
        }

        return contact;
    }


    private Bitmap retrieveContactPhoto(ContentResolver contentResolver, long contactID) {
        Bitmap photo = null;

        try {
            InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(
                    contentResolver,
                    ContentUris.withAppendedId(
                            ContactsContract.Contacts.CONTENT_URI, contactID));

            if (inputStream != null) {
                photo = BitmapFactory.decodeStream(inputStream);

                inputStream.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return photo;
    }

    public boolean hasGpsPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    public Location getLastKnownLocation() {
        Location bestLocation = null;
        if (hasGpsPermission()) {
            List<String> providers = mLocationManager.getProviders(true);
            for (String provider : providers) {
                //noinspection MissingPermission
                Location l = mLocationManager.getLastKnownLocation(provider);
                if (l == null) {
                    continue;
                }
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    // Found best last known location: %s", l);
                    bestLocation = l;
                }
            }
        }
        return bestLocation;
    }
    public String getLocationString() {
        StringBuilder stringBuilder = new StringBuilder();
        Location location = getLastKnownLocation();
        if (location != null) {
            stringBuilder.append(location.getLatitude());
            stringBuilder.append(",");
            stringBuilder.append(location.getLongitude());
        }
        return stringBuilder.toString();
    }
    public String getLocationSMS(String prefixText, String coordinates) {
        //http://maps.google.com?q=25,25

        return prefixText +
                "\r\n" +
                "https://maps.google.com/?q=" +
                coordinates;
    }



    @SuppressWarnings({"rawtypes", "unchecked"})
    private void endCallAidl(Context context) throws Exception {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        Class c = Class.forName(tm.getClass().getName());
        Method m = c.getDeclaredMethod("getITelephony");
        m.setAccessible(true);
        ITelephony telephonyService = (ITelephony) m.invoke(tm);
        telephonyService.endCall();
    }
    public void endCall() {
        if (PermissionsActivity.testPermission(Manifest.permission.CALL_PHONE)) {
            try {
                endCallAidl(getApplicationContext());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public void sendSMS(String phoneNumber, String message) {
        if (PermissionsActivity.testPermission(Manifest.permission.SEND_SMS)) {
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null);
        }
    }
}