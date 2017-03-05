package com.ekgee.ngbean;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import android.app.Activity;
import android.provider.Settings;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothManager;
import android.os.AsyncTask;
import android.widget.Toast;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.Context;
import android.os.Build;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.punchthrough.bean.sdk.Bean;
import com.punchthrough.bean.sdk.BeanManager;
import com.punchthrough.bean.sdk.BeanListener;
import com.punchthrough.bean.sdk.BeanDiscoveryListener;
import com.punchthrough.bean.sdk.message.Callback;
import com.punchthrough.bean.sdk.message.LedColor;

/*
 * The Plugin.
 */
public class PTBeanPlugin extends CordovaPlugin {
	private static final String TAG = "PTBeanPlugin";

  public static Bean beanSelected = null;
  public static ArrayList<Bean> beanList = new ArrayList<Bean>();
  public static PTBeanListener ptBeanListener = null;

  private BluetoothAdapter bluetoothAdapter;
  private CallbackContext enableBluetoothCallback;

  private static final int REQUEST_ENABLE_BLUETOOTH = 1;

	/*
	* Constructor.
	*/
	public PTBeanPlugin() {
  }

	/*
	* Sets the context of the Command.
	*
	* @param cordova The context of the main Activity.
	* @param webView The CordovaWebView Cordova is running in.
	*/
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		Log.i(TAG, "Initializing Plugin");
	}

  public static JSONArray jsonBeanList(List<Bean> beans) {
    JSONArray jsonArray = new JSONArray();
    for (Bean bean : beans) {
      JSONObject jObj = new JSONObject();
      try {
        jObj.put("name", bean.getDevice().getName());         // "Bean"              (example)
        jObj.put("address", bean.getDevice().getAddress());   // "B4:99:4C:1E:BC:75" (example)
        jsonArray.put(jObj);
      }
      catch(JSONException ex) {
      }
    }
    return jsonArray;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {

    if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

      if (resultCode == Activity.RESULT_OK) {
        Log.i(TAG, "User enabled Bluetooth");
        if (enableBluetoothCallback != null) {
          enableBluetoothCallback.success();
        }
      } else {
        Log.i(TAG, "User didn't enable Bluetooth");
        if (enableBluetoothCallback != null) {
          enableBluetoothCallback.error("User didn't enable Bluetooth");
        }
      }

      enableBluetoothCallback = null;
    }
  }

	public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
		Log.i(TAG, "Received action: "+ action + "; Args:  " + args);

    if (bluetoothAdapter == null) {
      Activity activity = cordova.getActivity();
      boolean hardwareSupportsBLE = activity.getApplicationContext().getPackageManager()
          .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && Build.VERSION.SDK_INT >= 18;

      if (!hardwareSupportsBLE) {
        Log.w(TAG, "BLE communication is not supported");
        callbackContext.error("BLE communication is not supported");
        return false;
      }

      BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
      bluetoothAdapter = bluetoothManager.getAdapter();
    }

    if (action.equals("isenabled")) {

      if ((bluetoothAdapter != null) && bluetoothAdapter.isEnabled()) {
        callbackContext.success();
        return true;
      } else {
        callbackContext.error("Bluetooth is currently disabled");
        return false;
      }

    } else if (action.equals("enable")) {

      enableBluetoothCallback = callbackContext;
      Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);
      return true;

    } else if (action.equals("find")) {

      // Does a BLE plugin need to spawn a thread? Getting weird warning about thread sleep in logs?
      // changing the callback handling to see if it fixes the issue.

      // final ArrayList<Bean> beanList = this.beanList;
      BeanDiscoveryListener discovery = new BeanDiscoveryListener() {
        @Override
        public void onBeanDiscovered(Bean bean, int rssi) {
		      Log.i(TAG, "Bean discovered: " + bean.getDevice().getName() + '@' + bean.getDevice().getAddress());
          PTBeanPlugin.beanList.add(bean);
        }

        @Override
        public void onDiscoveryComplete() {
          // This is called when the scan times out, defined by the BeanManager.setScanTimeout(int seconds) method
		      Log.i(TAG, "Bean discovery complete, found " + PTBeanPlugin.beanList.size());

          PluginResult pluginResultAsync = new PluginResult(PluginResult.Status.OK,
                PTBeanPlugin.jsonBeanList(PTBeanPlugin.beanList));

          pluginResultAsync.setKeepCallback(false);
          callbackContext.sendPluginResult(pluginResultAsync);
        }
      };

      Integer timeout = 10;
      if ((args != null) && (args.length() > 0)) {
        timeout = args.getInt(0);
      }

      Log.i(TAG, "Bean discovery started, timeout seconds setting: " + timeout.toString());

      BeanManager.getInstance().setScanTimeout(timeout);    // optional, default is 30 seconds
      BeanManager.getInstance().startDiscovery(discovery);

      // create an empty result and return, when doing asynchronous activities
      // https://github.com/apache/cordova-android/blob/master/framework/src/org/apache/cordova/CallbackContext.java
      PluginResult pluginResultAsync = new PluginResult(PluginResult.Status.NO_RESULT);
      pluginResultAsync.setKeepCallback(true);
      callbackContext.sendPluginResult(pluginResultAsync);
      return true;

		} else if (action.equals("select")) {

      if ((PTBeanPlugin.beanList == null) || (PTBeanPlugin.beanList.size() == 0)) {
        callbackContext.error("Bean list not populated, please call 'find' first");
        return false;
      }

      // defaults to first bean
      Bean bean = PTBeanPlugin.beanList.get(0);
      if ((args != null) && (args.length() > 0)) {
        String beanAddr = args.getString(0);
        // Predicate<Bean> predicate = b -> b.getDevice().getAddress().equals(beanAddr);
        // bean = list.stream().filter(predicate).findFirst().get();

        // find bean in list using provided address
        for(Bean b : PTBeanPlugin.beanList) {
          if(b.getDevice().getAddress().equals(beanAddr)) {
            bean = b;
          }
        }
      }

      Log.i(TAG, "Selected Bean: " + bean.getDevice().getAddress());
      PTBeanPlugin.beanSelected = bean;

      callbackContext.success();
      return true;

    } else if (action.equals("connect")) {

      if (PTBeanPlugin.beanSelected == null) {
        callbackContext.error("Cannot connect to bean, please call 'select' first");
        return false;
      }

      if (PTBeanPlugin.beanSelected.isConnected()) {
        callbackContext.error("Cannot connect to bean, appears to be already connected");
        return false;
      }

      Log.i(TAG, "Connecting Bean: " + PTBeanPlugin.beanSelected.getDevice().getAddress());

      Context context = this.cordova.getActivity().getApplicationContext();

      // setup a listener, which uses callback to communicate back to caller
      PTBeanPlugin.ptBeanListener = new PTBeanListener(context, PTBeanPlugin.beanSelected, callbackContext);
      PTBeanPlugin.beanSelected.connect(context, PTBeanPlugin.ptBeanListener.listener);

      // the callback is used repeatedly by the lister, to return bean events
      // create an empty result and return, when doing asynchronous activities
      PluginResult pluginResultAsync = new PluginResult(PluginResult.Status.NO_RESULT);
      pluginResultAsync.setKeepCallback(true);
      callbackContext.sendPluginResult(pluginResultAsync);
      return true;

    } else if (action.equals("disconnect")) {

      if (PTBeanPlugin.beanSelected == null) {
        callbackContext.error("Cannot disconnect, please call 'select' and 'connect' first");
        return false;
      }

      if (!PTBeanPlugin.beanSelected.isConnected()) {
        callbackContext.error("Cannot disconnect from bean, does not appear to be connected");
        return false;
      }

      Log.i(TAG, "Disconnecting Bean: " + PTBeanPlugin.beanSelected.getDevice().getAddress());
      PTBeanPlugin.beanSelected.disconnect();

      callbackContext.success();
      return true;

    } else if (action.equals("serial")) {

      if ((PTBeanPlugin.beanSelected == null) || (!PTBeanPlugin.beanSelected.isConnected())) {
        callbackContext.error("Cannot send serial data to bean, not connected");
        return false;
      }

      if ((args != null) && (args.length() > 0)) {
        String serialData = args.getString(0);

        Log.i(TAG, "Sending serial data to Bean: " + serialData);
        PTBeanPlugin.beanSelected.sendSerialMessage(serialData);

        callbackContext.success();
        return true;

      } else {
        callbackContext.error("No data to send to bean, please provide args");
        return false;
      }

    } else if (action.equals("temperature")) {

      if ((PTBeanPlugin.beanSelected == null) || (!PTBeanPlugin.beanSelected.isConnected())) {
        callbackContext.error("Cannot get temerature from bean, not connected");
        return false;
      }

      Log.i(TAG, "Reading bean temperature");
      PTBeanPlugin.beanSelected.readTemperature(new Callback<Integer>() {
        @Override
        public void onResult(Integer data){
          Log.d(TAG, "Received bean temperature: " + data);

          JSONObject jInfo = new JSONObject();
          try {
            jInfo.put("temperature", data);
          } catch (JSONException ex) {
            Log.i(TAG, "Exception converting temperature to JSON: " + ex.toString());
            callbackContext.error(ex.toString());
            return;
          }

          callbackContext.success(jInfo);
        }
      });

      return true;

    } else if (action.equals("led")) {

      if ((PTBeanPlugin.beanSelected == null) || (!PTBeanPlugin.beanSelected.isConnected())) {
        callbackContext.error("Cannot set LED on bean, not connected");
        return false;
      }

      if ((args != null) && (args.length() > 2)) {

        // why so complicated here:
        // https://github.com/PunchThrough/bean-sdk-android/blob/master/sdk/src/main/java/com/punchthrough/bean/sdk/message/LedColor.java
        Integer ledRed = args.getInt(0);
        Integer ledGrn = args.getInt(1);
        Integer ledBlu = args.getInt(2);

        Log.d(TAG, "Setting bean led color: (" + ledRed + "," + ledGrn + "," + ledBlu + ")");

        LedColor ledColor = LedColor.create(ledRed, ledGrn, ledBlu);
        PTBeanPlugin.beanSelected.setLed(ledColor);

      } else {

        Log.d(TAG, "Setting bean default led color: (0,0,0)");

        LedColor ledColor = LedColor.create(0,0,0);
        PTBeanPlugin.beanSelected.setLed(ledColor);
      }

      callbackContext.success();
      return true;

		} else {

      return super.execute(action, args, callbackContext);
		}
	}
}