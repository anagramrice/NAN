package net.mobilewebprint.nan;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.Uri;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceManager;

import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.lang.Thread;
import java.lang.Runnable;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;


/*
 * Note: as it stands, to run, do the following:
 *
 * 1. On Pixel #1, run the NAN app, and click the PUBLISH button.
 * 2. On Pixel #2, run the NAN app, and click the SUBSCRIBE button.
 *   -- There is a slight delay. Wait until both have 2 MAC addresses.
 *
 * Unfortunately, requestNetwork does not get any callbacks.
 *
 */

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final int                 MAC_ADDRESS_MESSAGE             = 55;
  private static final int          MY_PERMISSION_FINE_LOCATION_REQUEST_CODE = 88;
  private static final int          MY_PERMISSION_BACKGROUND_LOCATION_REQUEST_CODE = 66;
//  private final String              SERVICE_NAME                         = "org.wifi.nan.test";

  private BroadcastReceiver         broadcastReceiver;
  private WifiAwareManager          wifiAwareManager;
  private ConnectivityManager       connectivityManager;
  private WifiAwareSession          wifiAwareSession;
  private NetworkSpecifier          networkSpecifier;
  private PublishDiscoverySession   publishDiscoverySession;
  private SubscribeDiscoverySession subscribeDiscoverySession;
  private PeerHandle                peerHandle;
  private byte[]                    myMac;
  private byte[]                    otherMac;

  private int                       pubType;
  private int                       subType;
  private String                    EncryptType;
  private String                    SERVICE_NAME;
  private byte[]                    serviceInfo;
  private byte[]                    pmk;
  private String                    psk;

  private final int                 IP_ADDRESS_MESSAGE             = 33;
  private final int                 MESSAGE                        = 7;
  private static final int          MY_PERMISSION_EXTERNAL_REQUEST_CODE = 99;
  private static final int          MY_PERMISSION_EXTERNAL_READ_REQUEST_CODE = 98;
  private Inet6Address              ipv6;
  private ServerSocket              serverSocket;
  private Inet6Address              peerIpv6;
  private int                       peerPort;
//  private final byte[]              serviceInfo            = "android".getBytes();
//  private final byte[]              pmk            = "123456789abcdef0123456789abcdef0".getBytes();
  private byte[]                    portOnSystem;
  private int                       portToUse;
  private byte[]                    myIP;
  private byte[]                    otherIP;
  private byte[]                    msgtosend;


  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
      if (key.equals(getString(R.string.service_name))) {
        SERVICE_NAME = sharedPreferences.getString(getResources().getString(R.string.service_name),"org.wifi.nan.test");
        Log.d("prefs","service Name updated to: " + SERVICE_NAME);
      } else if (key.equals(getString(R.string.service_specific_info))) {
        serviceInfo = sharedPreferences.getString(getResources().getString(R.string.service_specific_info),"android").getBytes();
        Log.d("prefs","service info updated to: " + new String(serviceInfo));
      } else if (key.equals(getString(R.string.encryptType))) {
        EncryptType = sharedPreferences.getString(getResources().getString(R.string.encryptType),"open");
      } else if (key.equals(getString(R.string.pubType))) {
          String type = sharedPreferences.getString(getResources().getString(R.string.pubType),"unsolicited");
          if (type.equals("unsolicited")){
            pubType = PublishConfig.PUBLISH_TYPE_UNSOLICITED;
            Log.d("prefs","updated pubtype : " + type );
          } else{
            pubType = PublishConfig.PUBLISH_TYPE_SOLICITED;
            Log.d("prefs","updated pubtype : " + type );
          }

      } else if (key.equals(getString(R.string.subType))) {
          String type = sharedPreferences.getString(getResources().getString(R.string.subType),"passive");
          if (type.equals("passive")){
            subType = SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE;
            Log.d("prefs","updated subtype: " + type);
          } else{
            subType = SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE;
            Log.d("prefs","updated subtype: " + type);
          }
      }
      try {
        if (EncryptType.equals("pmk")) {
          pmk = sharedPreferences.getString(getResources().getString(R.string.security_pass), "123456789abcdef0123456789abcdef0").getBytes();
          Log.d("prefs", "pmk " + new String(pmk));
        } else if (EncryptType.equals("psk")) {
          psk = sharedPreferences.getString(getResources().getString(R.string.security_pass), "12345678");
          Log.d("prefs", "psk " + psk);
        }
      } catch (java.lang.NullPointerException e){
        Log.e("prefs", e.toString());
      }

  }

  private void setupSharedPreferences() {
    // Get all of the values from shared preferences to set it up
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    Map<String,?> keys = sharedPreferences.getAll();

    for(Map.Entry<String,?> entry : keys.entrySet()){
      Log.d("map values",entry.getKey() + ": " +
              entry.getValue().toString());
      if (entry.getKey().equals(getResources().getString(R.string.service_name)) ){
        SERVICE_NAME=entry.getValue().toString();
        Log.d("prefs","service Name set " + entry.getValue().toString());
      }
      if (entry.getKey().equals(getResources().getString(R.string.service_specific_info))){
        serviceInfo=entry.getValue().toString().getBytes();
        Log.d("prefs", "service info set " + entry.getValue().toString());
      }
      if (entry.getKey().equals(getResources().getString(R.string.encryptType))) {
        EncryptType = entry.getValue().toString();
      }
      if (entry.getKey().equals(getResources().getString(R.string.pubType))) {
        if (entry.getValue().toString().equals("unsolicited")){
          pubType = PublishConfig.PUBLISH_TYPE_UNSOLICITED;
          Log.d("prefs","pubtype unsolict: " + pubType);
        } else{
          pubType = PublishConfig.PUBLISH_TYPE_SOLICITED;
          Log.d("prefs","pubtype solicit: " + pubType);
        }
      }
      if (entry.getKey().equals(getResources().getString(R.string.subType))) {
        if (entry.getValue().toString().equals("passive")){
          subType = SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE;
          Log.d("prefs","updated subtype: " + subType);
        } else{
          subType = SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE;
          Log.d("prefs","updated subtype: " + subType);
        }
      }
    }
    try {
      if (EncryptType.equals("pmk")){
        pmk = sharedPreferences.getString(getResources().getString(R.string.security_pass),"123456789abcdef0123456789abcdef0").getBytes();
        Log.d("prefs", "pmk " + new String(pmk));
      } else if (EncryptType.equals("psk")) {
        psk = sharedPreferences.getString(getResources().getString(R.string.security_pass),"12345678");
        Log.d("prefs", "psk " + psk);
      }
    } catch (java.lang.NullPointerException e){
      Log.e("prefs", e.toString());
    }


    // Register the listener
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
  }

  /**
   * Handles initialization (creation) of the activity.
   *
   * @param savedInstanceState
   */
  @Override
  @TargetApi(26)
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    wifiAwareManager = null;
    wifiAwareSession = null;
    connectivityManager = null;
    networkSpecifier = null;
    publishDiscoverySession = null;
    subscribeDiscoverySession = null;
    peerHandle = null;

    //Log.d("myTag","Supported Aware: " + getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE));
    setupSharedPreferences();
    setupPermissions();
    setContentView(R.layout.activity_main);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    //------------------------------------------------------------------------------------------------------
    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.sendmsgfab);        /* +++++ */
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        String msg= "messageToBeSent: ";
        EditText editText = (EditText)findViewById(R.id.msgtext);
        msg += editText.getText().toString();
        msgtosend = msg.getBytes();
        if (publishDiscoverySession != null && peerHandle != null) {
          publishDiscoverySession.sendMessage(peerHandle, MESSAGE, msgtosend);
        } else if(subscribeDiscoverySession != null && peerHandle != null) {
          subscribeDiscoverySession.sendMessage(peerHandle, MESSAGE, msgtosend);
        }
      }
    });                                                                                   /* ----- */
    //------------------------------------------------------------------------------------------------------

    //------------------------------------------------------------------------------------------------------
    Button statusButton = (Button)findViewById(R.id.statusbtn);                             /* +++++ */
    statusButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (publishDiscoverySession != null && peerHandle != null) {
          //publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          Snackbar.make(view, "publisher req met", Snackbar.LENGTH_LONG)
                  .setAction("Action", null).show();
          Button responderButton = (Button)findViewById(R.id.responderButton);
          responderButton.setEnabled(true);

        } else if(subscribeDiscoverySession != null && peerHandle != null) {
          Snackbar.make(view, "subscriber req met", Snackbar.LENGTH_LONG)
                  .setAction("Action", null).show();
          Button initiatorButton = (Button)findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(true);
        } else if(peerHandle == null) {
          Snackbar.make(view, "no peerHandle", Snackbar.LENGTH_LONG)
                  .setAction("Action", null).show();
        } else{
          Snackbar.make(view, "no DiscoverySession", Snackbar.LENGTH_LONG)
                  .setAction("Action", null).show();
        }
      }
    });                                                                                   /* ----- */
    //------------------------------------------------------------------------------------------------------

    Button publishButton = (Button)findViewById(R.id.publishButton);
    publishButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        publishService();
        setStatus("NAN available: Device is Publisher \n--> click responder for connection");
      }
    });

    Button subscribeButton = (Button)findViewById(R.id.subscribeButton);
    subscribeButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        subscribeToService();
        setStatus("NAN available: Device is Subscriber \n--> click initiator for connection");
      }
    });

    Button initiatorButton = (Button)findViewById(R.id.initiatorButton);
    initiatorButton.setOnClickListener(new View.OnClickListener() {
      @RequiresApi(api = Build.VERSION_CODES.Q)
      @Override
      public void onClick(View v) {
        Log.d("myTag", "initiating subscribeSession "+EncryptType);
        //networkSpecifier = wifiAwareSession.createNetworkSpecifierOpen(WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, otherMac);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          if (EncryptType.equals("open")) {
            networkSpecifier = new WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession, peerHandle)
                    .build();
          } else if (EncryptType.equals("pmk")){
            networkSpecifier = new WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession, peerHandle)
                    .setPmk(pmk)
                    .build();
          } else if (EncryptType.equals("psk")){
            networkSpecifier = new WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession, peerHandle)
                    .setPskPassphrase(psk)
                    .build();
          }
        }

        Log.d("myTag", "Initiator button clicked <subscriber is an initiator>");
        setStatus("NAN initiator: subscriber networkSpecifier created");
        requestNetwork();
      }
    });

    //-------------------------------------------------------------------------------------------- +++++
    Button responderButton = (Button)findViewById(R.id.responderButton);
    responderButton.setOnClickListener(new View.OnClickListener() {
      @RequiresApi(api = Build.VERSION_CODES.Q)
      @Override
      public void onClick(View v) {
        Log.d("step3 Responder", "starting dataSession "+EncryptType);
        //networkSpecifier = wifiAwareSession.createNetworkSpecifierOpen(WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER, otherMac);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          if (EncryptType.equals("open")) {
            networkSpecifier = new WifiAwareNetworkSpecifier.Builder(publishDiscoverySession, peerHandle)
                    .build();
            portOnSystem = portToBytes(serverSocket.getLocalPort());
            Log.d("step3 Responder", "server port sending OTA");
            if (publishDiscoverySession != null && peerHandle != null) {
              publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, portOnSystem);
              Log.d("step3 Responder", "pub_sess");
            } else if (subscribeDiscoverySession != null && peerHandle != null)  {
              subscribeDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, portOnSystem);
              Log.d("step3 Responder", "dis_sess");
            }
          } else if (EncryptType.equals("pmk")){
            try{
              networkSpecifier = new WifiAwareNetworkSpecifier.Builder(publishDiscoverySession, peerHandle)
                      .setPmk(pmk)
                      .setPort(portToUse)
                      .build();
            }catch (Exception e) {
              Log.e("step3 Responder",e.toString());
              Log.d("step3 Responder", publishDiscoverySession.toString() + peerHandle.toString());
            }

          } else if (EncryptType.equals("psk")){
            try{
              networkSpecifier = new WifiAwareNetworkSpecifier.Builder(publishDiscoverySession, peerHandle)
                      .setPskPassphrase(psk)
                      .setPort(portToUse)
                      .build();
            } catch (Exception e) {
              Log.e("step3 Responder",e.toString());
              Log.d("step3 Responder", publishDiscoverySession.toString() + peerHandle.toString());
            }

          }
        }
        ;
        Log.d("myTag", "Responder button clicked <publisher is an responder>\"");
        setStatus("NAN publisher: Responder networkSpecifier created");
        requestNetwork(); /* */
      }
    });
    //-------------------------------------------------------------------------------------------- -----

    //-------------------------------------------------------------------------------------------- +++++
    Button sendFileButton = (Button)findViewById(R.id.sendbtn);
    sendFileButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          if (EncryptType.equals("open")) {
            Toast.makeText(MainActivity.this, "Sending to port: " + portToUse, Toast.LENGTH_SHORT).show();
            Log.d("myTag", "sending to " + portToUse + "\t" +peerIpv6.getScopedInterface().getDisplayName());
            clientSendFile(Inet6Address.getByAddress("WifiAwareHost",otherIP, peerIpv6.getScopedInterface()), portToUse);
          }
          else{
            Toast.makeText(MainActivity.this, "Sending to port: " + peerPort, Toast.LENGTH_SHORT).show();
            Log.d("myTag", "sending to " + peerPort + "\t" +peerIpv6.getScopedInterface().getDisplayName());
            clientSendFile(Inet6Address.getByAddress("WifiAwareHost",otherIP, peerIpv6.getScopedInterface()), peerPort);
          }

        } catch (UnknownHostException e) {
          Log.d("sendFileError", "exception " + e.toString());
        }

        //TODO: spin up client and send to server
      }
    });
    //-------------------------------------------------------------------------------------------- -----

    connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

    setHaveSession(false);

    setStatus("starting...");

    String  status              = null;
    boolean hasNan              = false;

    PackageManager packageManager = getPackageManager();
    if (packageManager == null) {
      status = "Cannot get PackageManager";
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        hasNan = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
      }
    }

    if (!hasNan) {
      status = "Device does not have NAN";
    } else {

      wifiAwareManager = (WifiAwareManager)getSystemService(Context.WIFI_AWARE_SERVICE);

      if (wifiAwareManager == null) {
        status = "Cannot get WifiAwareManager";
      }
    }

    setStatus(status);
  }
  /**
   * App Permissions for Fine Location
   **/
  private void setupPermissions() {
      // If we don't have the record network permission...
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          // And if we're on SDK M or later...
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              // Ask again, nicely, for the permissions.
              String[] permissionsWeNeed = new String[]{ Manifest.permission.ACCESS_FINE_LOCATION };
              requestPermissions(permissionsWeNeed, MY_PERMISSION_FINE_LOCATION_REQUEST_CODE);
          }
      }

/*      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        // And if we're on SDK M or later...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          // Ask again, nicely, for the permissions.
          String[] permissionsWeNeed = new String[]{ Manifest.permission.ACCESS_BACKGROUND_LOCATION };
          requestPermissions(permissionsWeNeed, MY_PERMISSION_BACKGROUND_LOCATION_REQUEST_CODE);
        }
      }*/

      //-------------------------------------------------------------------------------------------- +++++
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        // And if we're on SDK M or later...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          // Ask again, nicely, for the permissions.
          String[] permissionsWeNeed = new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE };
          requestPermissions(permissionsWeNeed, MY_PERMISSION_EXTERNAL_REQUEST_CODE);
        }
      }
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      // And if we're on SDK M or later...
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // Ask again, nicely, for the permissions.
        String[] permissionsWeNeed = new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE };
        requestPermissions(permissionsWeNeed, MY_PERMISSION_EXTERNAL_READ_REQUEST_CODE);
      }
    }
      //-------------------------------------------------------------------------------------------- -----
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String permissions[], @NonNull int[] grantResults) {
      switch (requestCode) {
          case MY_PERMISSION_FINE_LOCATION_REQUEST_CODE: {
              // If request is cancelled, the result arrays are empty.
              if (grantResults.length > 0
                      && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 return;

              } else {
                  Toast.makeText(this, "Permission for location not granted. NAN can't run.", Toast.LENGTH_LONG).show();
                  finish();
                  // The permission was denied, so we can show a message why we can't run the app
                  // and then close the app.
              }
          }
/*          case MY_PERMISSION_BACKGROUND_LOCATION_REQUEST_CODE: {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              return;

            } else {
              Toast.makeText(this, "Permission for background location not granted.", Toast.LENGTH_LONG).show();
              // and then close the app.
            }
          }*/
          //-------------------------------------------------------------------------------------------- +++++
          case MY_PERMISSION_EXTERNAL_REQUEST_CODE: {
          // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              return;

            } else {
              Toast.makeText(this, "no sd card access", Toast.LENGTH_LONG).show();
            }
          }
          case MY_PERMISSION_EXTERNAL_READ_REQUEST_CODE: {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              return;

            } else {
              Toast.makeText(this, "no sd card access", Toast.LENGTH_LONG).show();
            }
          }
          //-------------------------------------------------------------------------------------------- -----
          // Other permissions could go down here

      }
  }

  @TargetApi(26)
  private void requestNetwork() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }

    if (networkSpecifier == null) {
      Log.d("myTag", "No NetworkSpecifier Created ");
      return;
    }
    Log.d("myTag", "building network interface");
    Log.d("myTag", "using networkspecifier: " + networkSpecifier.toString());
    NetworkRequest networkRequest = new NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
        .setNetworkSpecifier(networkSpecifier)
        .build();

    Log.d("myTag", "finish building network interface");
    connectivityManager.requestNetwork(networkRequest, new NetworkCallback(){
      @Override
      public void onAvailable(Network network) {
        super.onAvailable(network);
        Log.d("myTag", "Network Available: " + network.toString());
      }

      @Override
      public void onLosing(Network network, int maxMsToLive) {
        super.onLosing(network, maxMsToLive);
        Log.d("myTag", "losing Network");
      }

      @Override
      public void onLost(Network network) {
        super.onLost(network);
        Toast.makeText(MainActivity.this, "lost network", Toast.LENGTH_LONG).show();
        Log.d("myTag", "Lost Network");
      }

      @Override
      public void onUnavailable() {
        super.onUnavailable();
        Toast.makeText(MainActivity.this, "onUnavailable", Toast.LENGTH_SHORT).show();
        Log.d("myTag", "entering onUnavailable ");
      }

      @RequiresApi(api = Build.VERSION_CODES.Q)
      @Override
      public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        Toast.makeText(MainActivity.this, "onCapabilitiesChanged", Toast.LENGTH_SHORT).show();
        WifiAwareNetworkInfo peerAwareInfo = (WifiAwareNetworkInfo) networkCapabilities.getTransportInfo();
        peerIpv6 = peerAwareInfo.getPeerIpv6Addr();
        peerPort = peerAwareInfo.getPort();
        Log.d("myTag", "entering onCapabilitiesChanged ");
        setStatus("Ready for file transfer\nSend File when both IPv6 shown");
      }

      //-------------------------------------------------------------------------------------------- +++++
      @Override
      public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        super.onLinkPropertiesChanged(network, linkProperties);
        //TODO: create socketServer on different thread to transfer files
        Toast.makeText(MainActivity.this, "onLinkPropertiesChanged", Toast.LENGTH_SHORT).show();
        Log.d("myTag", "entering linkPropertiesChanged ");
        try {
          //Log.d("myTag", "iface name: " + linkProperties.getInterfaceName());
          //Log.d("myTag", "iface link addr: " + linkProperties.getLinkAddresses());

          NetworkInterface awareNi = NetworkInterface.getByName(
                  linkProperties.getInterfaceName());
          /*Inet6Address ipv6 = null;
          Enumeration<NetworkInterface> ifcs = NetworkInterface.getNetworkInterfaces();
          while (ifcs.hasMoreElements()) {
            NetworkInterface iface = ifcs.nextElement();
            Log.d("myTag", "iface: " + iface.toString());
          }*/

          Enumeration<InetAddress> Addresses = awareNi.getInetAddresses();
          while (Addresses.hasMoreElements()) {
            InetAddress addr = Addresses.nextElement();
            if (addr instanceof Inet6Address) {
              Log.d("myTag", "netinterface ipv6 address: " + addr.toString());
              if (((Inet6Address) addr).isLinkLocalAddress()) {
                ipv6 = Inet6Address.getByAddress("WifiAware",addr.getAddress(),awareNi);
                myIP = addr.getAddress();
                if (publishDiscoverySession != null && peerHandle != null) {
                  publishDiscoverySession.sendMessage(peerHandle, IP_ADDRESS_MESSAGE, myIP);
                } else if(subscribeDiscoverySession != null && peerHandle != null){
                  subscribeDiscoverySession.sendMessage(peerHandle,IP_ADDRESS_MESSAGE, myIP);
                }
                break;
              }
            }
          }
        }
        catch (SocketException e) {
          Log.d("myTag", "socket exception " + e.toString());
        }
        catch (Exception e) {
          //EXCEPTION!!! java.lang.NullPointerException: Attempt to invoke virtual method 'java.util.Enumeration java.net.NetworkInterface.getInetAddresses()' on a null object reference
          Log.d("myTag", "EXCEPTION!!! " + e.toString());
        }
        //startServer(0,3,ipv6);
        // should be done in a separate thread
        /*
        startServer
        ServerSocket ss = new ServerSocket(0, 5, ipv6);
        int port = ss.getLocalPort();    */
        //TODO: need to send this port via messages to other device to finish client conn info

        // should be done in a separate thread
        // obtain server IPv6 and port number out-of-band
        //TODO: Retrieve address:port IPv6 before this client thread can be created
        /*
        Socket cs = network.getSocketFactory().createSocket(serverIpv6, serverPort);  */
      }
      //-------------------------------------------------------------------------------------------- -----

    });
  }

  /**
   * Resuming activity
   *
   */
  @Override
  @TargetApi(26)
  protected void onResume() {
    super.onResume();

    String  status = null;
    Log.d("myTag", "Current phone build" + Build.VERSION.SDK_INT +"\tMinimum:"+ Build.VERSION_CODES.O);
    Log.d("myTag","Supported Aware: " + getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Log.d("myTag", "Entering OnResume is executed");
        IntentFilter filter   = new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        broadcastReceiver     = new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String status = "";
            wifiAwareManager.getCharacteristics();
            boolean nanAvailable = wifiAwareManager.isAvailable();
            Log.d("myTag", "NAN is available");
            if (nanAvailable) {
              attachToNanSession();
              status = "NAN has become Available";
              Log.d("myTag", "NAN attached");
            } else {
              status = "NAN has become Unavailable";
              Log.d("myTag", "NAN unavailable");
            }

            setStatus(status);
          }
      };

      getApplicationContext().registerReceiver(broadcastReceiver, filter);

      boolean nanAvailable = wifiAwareManager.isAvailable();
      if (nanAvailable) {
        attachToNanSession();
        status = "NAN is Available";
      } else {
        status = "NAN is Unavailable";
      }
    } else {
      status = "NAN is only supported in O+";
    }

    setStatus(status);
  }

  /**
   * Handles attaching to NAN session.
   *
   */
  @TargetApi(26)
  private void attachToNanSession() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }

    // Only once
    if (wifiAwareSession != null) {
      return;
    }

    if (wifiAwareManager == null || !wifiAwareManager.isAvailable()) {
      setStatus("NAN is Unavailable in attach");
      return;
    }

    wifiAwareManager.attach(new AttachCallback() {
      @Override
      public void onAttached(WifiAwareSession session) {
        super.onAttached(session);

        closeSession();
        wifiAwareSession = session;
        setHaveSession(true);
      }

      @Override
      public void onAttachFailed() {
        super.onAttachFailed();
        setHaveSession(false);
        setStatus("attach() failed.");
      }

    }, new IdentityChangedListener() {
      @Override
      public void onIdentityChanged(byte[] mac) {
        super.onIdentityChanged(mac);
        setMacAddress(mac);
      }
    }, null);
  }

  @TargetApi(26)
  private void publishService() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { return; }
    Log.d("nanPUBLISH", "building publish session "+ SERVICE_NAME);

    if (pubType==PublishConfig.PUBLISH_TYPE_UNSOLICITED)
      Log.d("nanPUBLISH", "publish unsolicited "+pubType);
    else if(pubType==PublishConfig.PUBLISH_TYPE_SOLICITED)
      Log.d("nanPUBLISH", "publish solicited "+pubType);

    PublishConfig config = new PublishConfig.Builder()
        .setServiceName(SERVICE_NAME)
        .setServiceSpecificInfo(serviceInfo)
        .setPublishType(pubType)
        .build();

    //-------------------------------------------------------------------------------------------- +++++
    Log.d("nanPUBLISH", "build finish");
    wifiAwareSession.publish(config, new DiscoverySessionCallback() {
      @Override
      public void onPublishStarted(@NonNull PublishDiscoverySession session) {
        super.onPublishStarted(session);

        publishDiscoverySession = session;
        startServer(0,3);
        Button sendBtn = (Button)findViewById(R.id.sendbtn);
        sendBtn.setEnabled(false);
        Button responderButton = (Button)findViewById(R.id.responderButton);
        responderButton.setEnabled(true);
        if (publishDiscoverySession != null && peerHandle != null) {
          publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          Log.d("nanPUBLISH", "onPublishStarted sending mac");

          Button initiatorButton = (Button)findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(false);

        }
      }
      @Override
      public void onServiceDiscovered(PeerHandle peerHandle_, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
        super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);

        peerHandle = peerHandle_;
        Log.d("nanPUBLISH", "onServiceDiscovered found peerHandle");
      }

      @Override
      public void onMessageReceived(PeerHandle peerHandle_, byte[] message) {
        super.onMessageReceived(peerHandle, message);
        Log.d("nanPUBLISH", "received message");
        if(message.length == 2) {
          portToUse = byteToPortInt(message);
          Log.d("received", "will use port number "+ portToUse);
        } else if (message.length == 6){
          setOtherMacAddress(message);
          //Toast.makeText(MainActivity.this, "mac received", Toast.LENGTH_SHORT).show();
        } else if (message.length == 16) {
          setOtherIPAddress(message);
          //Toast.makeText(MainActivity.this, "ip received", Toast.LENGTH_SHORT).show();
        } else if (message.length > 16) {
          setMessage(message);
          //Toast.makeText(MainActivity.this, "message received", Toast.LENGTH_SHORT).show();
        }

        peerHandle = peerHandle_;

        if (publishDiscoverySession != null && peerHandle != null) {
          publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          Log.d("nanPUBLISH", "onMessageReceived sending mac");
          Button responderButton = (Button)findViewById(R.id.responderButton);
          Button initiatorButton = (Button)findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(false);
          responderButton.setEnabled(true);
        }
      }
    }, null);
    //-------------------------------------------------------------------------------------------- -----
  }

  //-------------------------------------------------------------------------------------------- +++++
  @TargetApi(26)
  private void subscribeToService() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { return; }
    Log.d("nanSUBSCRIBE", "building subscribe session");

    if (subType==SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
      Log.d("nanPUBLISH", "subscribe active ");
    else if(subType==SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
      Log.d("nanPUBLISH", "subscribe passive ");

    SubscribeConfig config = new SubscribeConfig.Builder()
        .setServiceName(SERVICE_NAME)
        .setServiceSpecificInfo(serviceInfo)
        .setSubscribeType(subType)
        .build();
    Log.d("nanSUBSCRIBE", "build finish");
    wifiAwareSession.subscribe(config, new DiscoverySessionCallback() {

      @Override
      public void onServiceDiscovered(PeerHandle peerHandle_, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
        super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);

        peerHandle = peerHandle_;

        if (subscribeDiscoverySession != null && peerHandle != null) {
          subscribeDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          Log.d("nanSUBSCRIBE", "onServiceDiscovered send mac");
          Button responderButton = (Button)findViewById(R.id.responderButton);
          Button initiatorButton = (Button)findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(true);
          responderButton.setEnabled(false);
        }
      }

      @Override
      public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
        super.onSubscribeStarted(session);

        subscribeDiscoverySession = session;

        if (subscribeDiscoverySession != null && peerHandle != null) {
          subscribeDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          Log.d("nanSUBSCRIBE", "onServiceStarted send mac");
          Button responderButton = (Button)findViewById(R.id.responderButton);
          Button initiatorButton = (Button)findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(true);
          responderButton.setEnabled(false);
        }
      }

      @Override
      public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
        super.onMessageReceived(peerHandle, message);
        Log.d("nanSUBSCRIBE", "received message");
        //Toast.makeText(MainActivity.this, "received", Toast.LENGTH_LONG).show();
        if(message.length == 2) {
          portToUse = byteToPortInt(message);
          Log.d("received", "will use port number "+ portToUse);
        } else if (message.length == 6){
          setOtherMacAddress(message);
          //Toast.makeText(MainActivity.this, "mac received", Toast.LENGTH_SHORT).show();
        } else if (message.length == 16) {
          setOtherIPAddress(message);
          //Toast.makeText(MainActivity.this, "ip received", Toast.LENGTH_SHORT).show();
        } else if (message.length > 16) {
          setMessage(message);
          //Toast.makeText(MainActivity.this, "message received", Toast.LENGTH_SHORT).show();
        }
      }
    }, null);
  }
  //-------------------------------------------------------------------------------------------- -----

  /**
   * Handles cleanup of the activity.
   *
   */
  @Override
  protected void onPause() {
    super.onPause();
    getApplicationContext().unregisterReceiver(broadcastReceiver);
    closeSession();
  }

  private void closeSession() {

    if (publishDiscoverySession != null) {
      publishDiscoverySession.close();
      publishDiscoverySession = null;
    }

    if (subscribeDiscoverySession != null) {
      subscribeDiscoverySession.close();
      subscribeDiscoverySession = null;
    }

    if (wifiAwareSession != null) {
      wifiAwareSession.close();
      wifiAwareSession = null;
    }
  }

  /**
   * Handles creating the options menu.
   *
   * @param menu
   * @return
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  /**
   * Handles when an option is selected from the menu.
   *
   * @param item
   * @return
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    //-------------------------------------------------------------------------------------------- +++++

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
      startActivity(startSettingsActivity);
      return true;
    }
    if (id == R.id.close) {
      closeSession();
      finish();
      System.exit(0);
    }

    return super.onOptionsItemSelected(item);
    //-------------------------------------------------------------------------------------------- -----
  }

  /**
   * Helper to set the status field.
   *
   * @param status
   */
  private void setStatus(String status) {
    TextView textView = (TextView)findViewById(R.id.status);
    textView.setText(status);
  }

  private void setHaveSession(boolean haveSession) {
    CheckBox cbHaveSession = (CheckBox)findViewById(R.id.haveSession);
    cbHaveSession.setChecked(haveSession);
  }

  private void setMacAddress(byte[] mac) {
    myMac = mac;
    String macAddress = String.format("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    EditText editText = (EditText)findViewById(R.id.macAddress);
    editText.setText(macAddress);
  }

  private void setOtherMacAddress(byte[] mac) {
    otherMac = mac;
    String macAddress = String.format("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    EditText editText = (EditText)findViewById(R.id.otherMac);
    editText.setText(macAddress);
  }

  //-------------------------------------------------------------------------------------------- +++++
  private void setOtherIPAddress(byte[] ip) {
    otherIP = ip;
    try {
      String ipAddr = Inet6Address.getByAddress(otherIP).toString();
      EditText editText = (EditText) findViewById(R.id.IPv6text);
      editText.setText(ipAddr);
    } catch (UnknownHostException e) {
      Log.d("myTag", "socket exception " + e.toString());
    }
  }
  private void setMessage(byte[] msg) {
    String outmsg = new String(msg).replace("messageToBeSent: ","");
    EditText editText = (EditText) findViewById(R.id.msgtext);
    editText.setText(outmsg);
  }

  public int byteToPortInt(byte[] bytes){
    return ((bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF));
  }

  public byte[] portToBytes(int port){
    byte[] data = new byte [2];
    data[0] = (byte) (port & 0xFF);
    data[1] = (byte) ((port >> 8) & 0xFF);
    return data;
  }

  @TargetApi(26)
  public void startServer(final int port, final int backlog) {
    Runnable serverTask = new Runnable() {
      @Override
      public void run() {
        try{
          Log.d("serverThread", "thread running");
          Thread.sleep(1000);
          serverSocket = new ServerSocket(port, backlog);
          //ServerSocket serverSocket = new ServerSocket();
          while (true) {
            portToUse = serverSocket.getLocalPort();
            if (EncryptType.equals("open")) {
              portOnSystem = portToBytes(serverSocket.getLocalPort());
            }

            Log.d("serverThread", "server waiting to accept on " + serverSocket.toString());
            Socket clientSocket = serverSocket.accept();
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
            byte[] buffer = new byte[4096];
            int read;
            int totalRead = 0;
            ContentValues values = new ContentValues();

            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "nanFile");       //file name
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");        //file extension, will automatically add to file
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS );     //end "/" is not mandatory

            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);      //important!

            OutputStream fos = getContentResolver().openOutputStream(uri);
            //FileOutputStream fos = new FileOutputStream("/sdcard/Download/newfile");
            Log.d("serverThread", "Socket being written to begin... ");
            while ((read = in.read(buffer)) > 0) {
              fos.write(buffer,0,read);
              totalRead += read;
              if (totalRead%(4096*2500)==0) {//every 10MB update status
                  Log.d("clientThread", "total bytes retrieved:" + totalRead);
              }
            }
            Log.d("serverThread", "finished file transfer: " + totalRead);

          }
        } catch (IOException e) {
          Log.d("serverThread", "socket exception " + e.toString());
          Log.d("serverThread",  e.getStackTrace().toString());
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    };
    Thread serverThread = new Thread(serverTask);
    serverThread.start();

  }
  public void clientSendFile(final Inet6Address serverIP,final int serverPort) {
    Runnable clientTask = new Runnable() {
      @Override
      public void run() {
        byte[] buffer = new byte[4096];
        int bytesRead;
        Socket clientSocket = null;
        int fsize = 1;
        InputStream is = null;
        OutputStream outs = null;
        Log.d("clientThread", "thread running socket info "+ serverIP.getHostAddress() + "\t" + serverPort);
        try {
          clientSocket = new Socket( serverIP , serverPort );
          is = clientSocket.getInputStream();
          outs = clientSocket.getOutputStream();
          Log.d("clientThread", "socket created ");
        } catch (IOException ex) {
          Log.d("clientThread", "socket could not be created " + ex.toString());
        }
        try {
          Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
          Cursor cursor = getContentResolver().query(contentUri, null, null,
                  null,  "date_modified DESC");
          //Log.d("clientThread", DatabaseUtils.dumpCursorToString(cursor));
          Uri uri = null;
          if (cursor.getCount() == 0) {
            Log.d( "clientThread","No Video files");;
          } else {
            while (cursor.moveToNext()) {
              String fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME));
              Log.d("clientThread", fileName);
              long id = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media._ID));
              uri = ContentUris.withAppendedId(contentUri, id);
              fsize = cursor.getColumnIndex(OpenableColumns.SIZE);
              File mediaFile = new File(uri.getPath());
              long fileSizeInBytes = mediaFile.length();
              Log.d("clientThread", String.valueOf(fileSizeInBytes));
              break;
            }
          }
            if (uri == null) {
              Log.d( "clientThread","\"IEEEspecJun2018.pdf\" not found");
            }

          InputStream in = getContentResolver().openInputStream(uri);
          //InputStream in = new FileInputStream("/sdcard/Download/IEEEspecJun2018.pdf");
          int count;
          int totalSent = 0;
          DataOutputStream dos = new DataOutputStream(outs);
          Log.d("clientThread", "beginning to send file (log updates every 2MB)");
          while ((count = in.read(buffer))>0){
            totalSent += count;
            dos.write(buffer, 0, count);
            if (totalSent%(10240*200)==0) {//every 2MB update status
                Log.d("clientThread", "total bytes sent:" + totalSent  +"\t"+ fsize);
                try{
                  float percent = (float) totalSent/fsize;
                  setStatus("percent sent: "+ String.format("%.2f",percent));
                } catch (Exception e) {
                  Log.e("clientThread",e.toString());
              }
            }
          }
          in.close();
          dos.close();
          Log.d("clientThread", "finished sending file!!! "+totalSent);
          setStatus("Finished sending file");
        } catch(FileNotFoundException e){
          Log.d("clientThread", "file not found exception " + e.toString());
        } catch(IOException e){
          Log.d("clientThread", e.toString());
        }

      }
    };
    Thread clientThread = new Thread(clientTask);
    clientThread.start();

  }

  //-------------------------------------------------------------------------------------------- -----

}
