package net.mobilewebprint.nan;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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
import java.util.regex.*;


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

public class MainActivity extends AppCompatActivity {

  private final int                 MAC_ADDRESS_MESSAGE             = 55;
  private final int                 IP_ADDRESS_MESSAGE             = 33;
  private final int                 MESSAGE                        = 7;
  private static final int          MY_PERMISSION_COARSE_LOCATION_REQUEST_CODE = 88;
  private static final int          MY_PERMISSION_EXTERNAL_REQUEST_CODE = 99;
  private final String              THE_MAC                         = "THEMAC";

  private BroadcastReceiver         broadcastReceiver;
  private WifiAwareManager          wifiAwareManager;
  private ConnectivityManager       connectivityManager;
  private WifiAwareSession          wifiAwareSession;
  private NetworkSpecifier          networkSpecifier;
  private PublishDiscoverySession   publishDiscoverySession;
  private SubscribeDiscoverySession subscribeDiscoverySession;
  private PeerHandle                peerHandle;
  private Inet6Address              ipv6;
  private ServerSocket              serverSocket;
  private byte[]                    portOnSystem;
  private int                       portToUse;
  private byte[]                    myMac;
  private byte[]                    otherMac;
  private byte[]                    myIP;
  private byte[]                    otherIP;
  private byte[]                    msgtosend;

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
    setupPermissions();
    setContentView(R.layout.activity_main);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.sendmsgfab);
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
    });

    Button statusButton = (Button)findViewById(R.id.statusbtn);
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
    });

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
      @Override
      public void onClick(View v) {

        //networkSpecifier = wifiAwareSession.createNetworkSpecifierOpen(WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, otherMac);
        networkSpecifier = subscribeDiscoverySession.createNetworkSpecifierOpen(peerHandle);
        Log.d("myTag", "Initiator button clicked <subscriber is an initiator>");
        setStatus("NAN initiator: subscriber networkSpecifier created");
        requestNetwork();
      }
    });

    Button responderButton = (Button)findViewById(R.id.responderButton);
    responderButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //networkSpecifier = wifiAwareSession.createNetworkSpecifierOpen(WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER, otherMac);
        networkSpecifier = publishDiscoverySession.createNetworkSpecifierOpen(peerHandle);
        Log.d("myTag", "Responder button clicked <publisher is an responder>\"");
        setStatus("NAN publisher: Responder networkSpecifier created");
        requestNetwork();
      }
    });

    Button sendFileButton = (Button)findViewById(R.id.sendbtn);
    sendFileButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          Toast.makeText(MainActivity.this, "Sending to port: " + portToUse, Toast.LENGTH_LONG).show();
          Log.d("myTag", "sending to " + portToUse + "\t" +ipv6.getScopedInterface().getDisplayName());
          clientSendFile(Inet6Address.getByAddress("WifiAwareHost",otherIP, ipv6.getScopedInterface()), portToUse);
        } catch (UnknownHostException e) {
          Log.d("sendFileError", "exception line 215" + e.toString());
        }

        //TODO: spin up client and send to server
      }
    });

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
   * App Permissions for Coarse Location
   **/
  private void setupPermissions() {
      // If we don't have the record network permission...
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          // And if we're on SDK M or later...
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              // Ask again, nicely, for the permissions.
              String[] permissionsWeNeed = new String[]{ Manifest.permission.ACCESS_COARSE_LOCATION };
              requestPermissions(permissionsWeNeed, MY_PERMISSION_COARSE_LOCATION_REQUEST_CODE);
          }
      }
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        // And if we're on SDK M or later...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          // Ask again, nicely, for the permissions.
          String[] permissionsWeNeed = new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE };
          requestPermissions(permissionsWeNeed, MY_PERMISSION_EXTERNAL_REQUEST_CODE);
        }
      }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String permissions[], @NonNull int[] grantResults) {
      switch (requestCode) {
          case MY_PERMISSION_COARSE_LOCATION_REQUEST_CODE: {
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
          case MY_PERMISSION_EXTERNAL_REQUEST_CODE: {
          // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              return;

            } else {
              Toast.makeText(this, "no sd card access", Toast.LENGTH_LONG).show();
            }
          }
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
      }

      @Override
      public void onLost(Network network) {
        super.onLost(network);
      }

      @Override
      public void onUnavailable() {
        super.onUnavailable();
      }

      @Override
      public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
      }

      @Override
      public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        super.onLinkPropertiesChanged(network, linkProperties);
        //TODO: create socketServer on different thread to transfer files
        Toast.makeText(MainActivity.this, "onLinkPropertiesChanged", Toast.LENGTH_LONG).show();
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
        startServer(0,3,ipv6);
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
    Log.d("nanPUBLISH", "building publish session");
    PublishConfig config = new PublishConfig.Builder()
        .setServiceName(THE_MAC)
        .build();
    Log.d("nanPUBLISH", "build finish");
    wifiAwareSession.publish(config, new DiscoverySessionCallback() {
      @Override
      public void onPublishStarted(@NonNull PublishDiscoverySession session) {
        super.onPublishStarted(session);

        publishDiscoverySession = session;
        if (publishDiscoverySession != null && peerHandle != null) {
          publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          Log.d("nanPUBLISH", "onPublishStarted sending mac");
          Button responderButton = (Button)findViewById(R.id.responderButton);
          Button initiatorButton = (Button)findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(false);
          responderButton.setEnabled(true);
        }
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
          Toast.makeText(MainActivity.this, "mac received", Toast.LENGTH_LONG).show();
        } else if (message.length == 16) {
          setOtherIPAddress(message);
          Toast.makeText(MainActivity.this, "ip received", Toast.LENGTH_LONG).show();
        } else if (message.length > 16) {
          setMessage(message);
          Toast.makeText(MainActivity.this, "message received", Toast.LENGTH_LONG).show();
        }

        peerHandle  = peerHandle_;

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
  }

  @TargetApi(26)
  private void subscribeToService() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { return; }
    Log.d("nanSUBSCRIBE", "building subscribe session");
    SubscribeConfig config = new SubscribeConfig.Builder()
        .setServiceName(THE_MAC)
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
        Toast.makeText(MainActivity.this, "received", Toast.LENGTH_LONG).show();
        if(message.length == 2) {
          portToUse = byteToPortInt(message);
          Log.d("received", "will use port number "+ portToUse);
        } else if (message.length == 6){
          setOtherMacAddress(message);
          Toast.makeText(MainActivity.this, "mac received", Toast.LENGTH_LONG).show();
        } else if (message.length == 16) {
          setOtherIPAddress(message);
          Toast.makeText(MainActivity.this, "ip received", Toast.LENGTH_LONG).show();
        } else if (message.length > 16) {
          setMessage(message);
          Toast.makeText(MainActivity.this, "message received", Toast.LENGTH_LONG).show();
        }
      }
    }, null);
  }

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

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }
    if (id == R.id.close) {
      closeSession();
      finish();
      System.exit(0);
    }

    return super.onOptionsItemSelected(item);
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
  public void startServer(final int port, final int backlog, final InetAddress bindAddr) {
    Runnable serverTask = new Runnable() {
      @Override
      public void run() {
        try{
          Log.d("serverThread", "thread running");
          serverSocket = new ServerSocket(port, backlog, bindAddr);
          //ServerSocket serverSocket = new ServerSocket();
          while (true) {
            portOnSystem = portToBytes(serverSocket.getLocalPort());
            if (publishDiscoverySession != null && peerHandle != null) {
              publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, portOnSystem);
            } else if (subscribeDiscoverySession != null && peerHandle != null)  {
              subscribeDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, portOnSystem);
            }
            Log.d("serverThread", "server waiting to accept on " + serverSocket.toString());
            Socket clientSocket = serverSocket.accept();
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
            byte[] bytes = new byte[8192];
            int count;
            FileOutputStream fos = new FileOutputStream("/sdcard/Download/newfile");
            while ((count = in.read(bytes)) > 0) {
              fos.write(bytes,0,count);
            }


          }
        } catch (IOException e) {
          Log.d("serverThread", "socket exception " + e.toString());
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
        byte[] bytes = new byte[1024];
        int bytesRead;
        Socket clientSocket = null;
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
          File filetosend = new File("/sdcard/Download/file");
          //long length = filetosend.length();
          InputStream in = new FileInputStream(filetosend);
          //ByteArrayOutputStream baos = new ByteArrayOutputStream();
          int count;
          while ((count = in.read(bytes))>0){
            outs.write(bytes, 0, count);
          }
        } catch(FileNotFoundException e){
          Log.d("clientThread", "file not found exception " + e.toString());
        } catch(IOException e){
          Log.d("clientThread", e.toString());
        }

/*
        if (is != null) {

          FileOutputStream fos = null;
          BufferedOutputStream bos = null;
          try {
            fos = new FileOutputStream( "/sdcard/Download/file" );
            bos = new BufferedOutputStream(fos);
            bytesRead = is.read(aByte, 0, aByte.length);
            Log.d("clientThread", "reading file");

            do {
              baos.write(aByte);
              bytesRead = is.read(aByte);
            } while (bytesRead != -1);
            Log.d("clientThread", "sending file...");
            bos.write(baos.toByteArray());
            bos.flush();
            bos.close();
            Log.d("clientThread", "finsh sending file");
            clientSocket.close();
          } catch (IOException ex) {
            Log.d("clientThread", "io exception " + ex.toString());
          }
        }*/

      }
    };
    Thread clientThread = new Thread(clientTask);
    clientThread.start();

  }

}
