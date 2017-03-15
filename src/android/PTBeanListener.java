package com.ekgee.ngbean;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.punchthrough.bean.sdk.Bean;
import com.punchthrough.bean.sdk.BeanManager;
import com.punchthrough.bean.sdk.BeanListener;
import com.punchthrough.bean.sdk.BeanDiscoveryListener;
import com.punchthrough.bean.sdk.message.ScratchBank;
import com.punchthrough.bean.sdk.message.DeviceInfo;
import com.punchthrough.bean.sdk.message.BeanError;
import com.punchthrough.bean.sdk.message.Callback;
import com.punchthrough.bean.sdk.message.LedColor;

import android.content.Context;

public class PTBeanListener {
	private static final String TAG = "PTBeanListener";

  private Context context;
  public Bean bean = null;
  public BeanListener listener = null;
  public CallbackContext stickyCallbackContext = null;

  public void success(JSONObject jinfo) {
    PluginResult result = new PluginResult(PluginResult.Status.OK, jinfo);
    result.setKeepCallback(true);
    stickyCallbackContext.sendPluginResult(result);
  }

  public void error(String message) {
    PluginResult result = new PluginResult(PluginResult.Status.ERROR, message);
    result.setKeepCallback(true);
    stickyCallbackContext.sendPluginResult(result);
  }

  public PTBeanListener(Context context, final Bean bean, final CallbackContext callbackContext) {
    this.stickyCallbackContext = callbackContext;
    this.context = context;
    this.bean = bean;

    this.listener = new BeanListener() {

      /**
       * Called when the Bean is connected. Connected means that a Bluetooth GATT connection is made
       * and the setup for the Bean serial protocol is complete.
       */
      @Override
      public void onConnected() {
        bean.readDeviceInfo(new Callback<DeviceInfo>() {
          @Override
          public void onResult(DeviceInfo deviceInfo) {
            if (deviceInfo == null) {
  	          Log.i(TAG, "Bean connection returned null deviceInfo");
              error("Received unexpected results on bean.readDeviceInfo");
              return;
            }

            bean.setLed(LedColor.create(0, 255, 0));
            bean.setLed(LedColor.create(0, 0, 0));

            JSONObject jInfo = new JSONObject();
            try {
              jInfo.put("type", "connected");
              jInfo.put("hardwareVersion", deviceInfo.hardwareVersion());
              jInfo.put("firmwareVersion", deviceInfo.firmwareVersion());
              jInfo.put("sofwareVersion", deviceInfo.softwareVersion());
            } catch (JSONException ex) {
  	          Log.i(TAG, "Exception converting to JSON: " + ex.toString());
              error(ex.toString());
              return;
            }

	          Log.i(TAG, "Bean connection successful");
            success(jInfo);
          }
        });
      }

      /**
       * Called when the connection could not be established. This could either be because the Bean
       * could not be connected, or the serial connection could not be established.
       */
      @Override
      public void onConnectionFailed() {
        Log.i(TAG, "Bean connection failed");

        JSONObject jInfo = new JSONObject();
        try {
          jInfo.put("type", "connectionFailed");
        } catch (JSONException ex) {
          Log.i(TAG, "Exception converting to JSON: " + ex.toString());
          error(ex.toString());
          return;
        }

        success(jInfo);
      }

      /**
       * Called when the Bean has been disconnected.
       */
      @Override
      public void onDisconnected() {
        Log.i(TAG, "Bean disconnected");

        JSONObject jInfo = new JSONObject();
        try {
          jInfo.put("type", "disconnected");
        } catch (JSONException ex) {
          Log.i(TAG, "Exception converting to JSON: " + ex.toString());
          error(ex.toString());
          return;
        }

        success(jInfo);
      }

      /**
       * Called when a serial message is received from the Bean, e.g. a <code>Serial.write()</code>
       * from Arduino code.
       *
       * @param data the data that was sent from th bean
       */
      @Override
      public void onSerialMessageReceived(byte[] data) {
        Log.i(TAG, "Bean sent serial message");

        JSONObject jInfo = new JSONObject();
        try {
          jInfo.put("type", "serialMessageReceived");
          jInfo.put("data", data.toString());
        } catch (JSONException ex) {
          Log.i(TAG, "Exception converting to JSON: " + ex.toString());
          error(ex.toString());
          return;
        }

        success(jInfo);
      }

      /**
       * Called when one of the scratch characteristics of the Bean has updated its value.
       *
       * @param bank  the {@link com.punchthrough.bean.sdk.message.ScratchBank} that was updated
       * @param value the bank's new value
       */
      @Override
      public void onScratchValueChanged(ScratchBank bank, byte[] value) {
        Log.i(TAG, "Bean received scratch value in bank: " + bank.getRawValue());

        JSONObject jInfo = new JSONObject();
        try {
          jInfo.put("type", "scratchValueChanged");
          jInfo.put("bank", bank.getRawValue());      // gets enum value (bank name), rather than bank value
          jInfo.put("value", value.toString());
        } catch (JSONException ex) {
          Log.i(TAG, "Exception converting to JSON: " + ex.toString());
          error(ex.toString());
          return;
        }

        success(jInfo);
      }

      /**
       * Called when an error occurs during sketch or firmware upload.
       *
       * @param error The {@link com.punchthrough.bean.sdk.message.BeanError} that occurred
       */
      @Override
      public void onError(BeanError error) {
        JSONObject jInfo = new JSONObject();
        try {
          jInfo.put("type", "error");
          jInfo.put("code", error);
        } catch (JSONException ex) {
          Log.i(TAG, "Exception converting to JSON: " + ex.toString());
          error(ex.toString());
          return;
        }

        success(jInfo);
      }

      /**
       * Called when a new RSSI value is received, in response to a previous call to
       * {@link Bean#readRemoteRssi()}.
       *
       * @param rssi The RSSI for a connected remote device.
       */
      @Override
      public void onReadRemoteRssi(int rssi) {
        JSONObject jInfo = new JSONObject();
        try {
          jInfo.put("type", "rssiReadRemote");
          jInfo.put("rssi", rssi);
        } catch (JSONException ex) {
          Log.i(TAG, "Exception converting to JSON: " + ex.toString());
          error(ex.toString());
          return;
        }

        success(jInfo);
      }
    };
  }
}
