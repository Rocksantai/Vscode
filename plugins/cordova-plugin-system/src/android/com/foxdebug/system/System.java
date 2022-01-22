package com.foxdebug.system;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.foxdebug.system.BrowserDialog;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class System extends CordovaPlugin {

  // private WebviewActivity webviewActivity;
  private Activity activity;
  private Context context;
  private CallbackContext callback;
  private int REQ_PERMISSIONS = 1;
  private int REQ_PERMISSION = 2;
  private int themeColor = 0xFF000000;
  private String themeType = "dark";
  private CallbackContext intentHandler;

  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    this.context = cordova.getContext();
    this.activity = cordova.getActivity();
  }

  public boolean execute(
    String action,
    final JSONArray args,
    final CallbackContext callbackContext
  )
    throws JSONException {
    this.callback = callbackContext;

    final String arg1 = getString(args, 0);
    final String arg2 = getString(args, 1);
    final String arg3 = getString(args, 2);
    final String arg4 = getString(args, 3);
    final String arg5 = getString(args, 4);
    final String arg6 = getString(args, 5);

    switch (action) {
      case "get-webkit-info":
      case "share-file":
      case "is-powersave-mode":
      case "get-app-info":
      case "add-shortcut":
      case "remove-shortcut":
      case "pin-shortcut":
      case "manage-all-files":
      case "get-android-version":
      case "is-external-storage-manager":
      case "request-permissions":
      case "request-permission":
      case "has-permission":
      case "open-in-browser":
      case "launch-app":
        break;
      case "set-intent-handler":
        setIntentHandler(callbackContext);
        return true;
      case "set-ui-theme":
        this.cordova.getActivity()
          .runOnUiThread(
            new Runnable() {
              public void run() {
                setUiTheme(arg1, arg2);
              }
            }
          );
        return true;
      case "in-app-browser":
        this.cordova.getActivity()
          .runOnUiThread(
            new Runnable() {
              public void run() {
                inAppBrowser(arg1, arg2, getBoolean(args, 2));
              }
            }
          );
        return true;
      default:
        return false;
    }

    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            switch (action) {
              case "get-webkit-info":
                getWebkitInfo();
                break;
              case "share-file":
                shareFile(arg1, arg2);
                break;
              case "is-powersave-mode":
                isPowerSaveMode();
                break;
              case "get-app-info":
                getAppInfo();
                break;
              case "add-shortcut":
                addShortcut(arg1, arg2, arg3, arg4, arg5, arg6);
                break;
              case "remove-shortcut":
                removeShortcut(arg1);
                break;
              case "pin-shortcut":
                pinShortcut(arg1);
                break;
              case "manage-all-files":
                askToManageAllFiles();
                break;
              case "get-android-version":
                getAndroidVersion();
                break;
              case "is-external-storage-manager":
                isExternalStorageManager();
                break;
              case "request-permissions":
                requestPermissions(getJSONArray(args, 0));
              case "request-permission":
                requestPermission(arg1);
                break;
              case "has-permission":
                hasPermission(arg1);
                break;
              case "open-in-browser":
                openInBrowser(arg1);
                break;
              case "launch-app":
                launchApp(arg1, arg2, arg3);
                break;
              default:
                break;
            }
          }
        }
      );

    return true;
  }

  private void requestPermissions(JSONArray arr) {
    try {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        int[] res = new int[arr.length()];
        for (int i = 0; i < res.length; ++i) {
          res[i] = 1;
        }
        callback.success(1);
        return;
      }

      String[] permissions = checkPermissions(arr);

      if (permissions.length > 0) {
        cordova.requestPermissions(this, REQ_PERMISSIONS, permissions);
        return;
      }
      callback.success(new JSONArray());
    } catch (Exception e) {
      callback.error(e.toString());
    }
  }

  private void requestPermission(String permission) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      callback.success(1);
      return;
    }

    if (permission != null || !permission.equals("")) {
      if (!cordova.hasPermission(permission)) {
        cordova.requestPermission(this, REQ_PERMISSION, permission);
        return;
      }

      callback.success(1);
    }

    callback.error("No permission passed to request.");
  }

  private void hasPermission(String permission) {
    if (permission != null || !permission.equals("")) {
      int res = 0;
      if (cordova.hasPermission(permission)) {
        res = 1;
      }

      callback.success(res);
      return;
    }
    callback.error("No permission passed to check.");
  }

  public void onRequestPermissionResult(
    int code,
    String[] permissions,
    int[] resCodes
  ) {
    if (code == REQ_PERMISSIONS) {
      JSONArray resAr = new JSONArray();
      for (int res : resCodes) {
        if (res == PackageManager.PERMISSION_DENIED) {
          resAr.put(0);
        }
        resAr.put(1);
      }

      callback.success(resAr);
      return;
    }

    if (
      resCodes.length >= 1 && resCodes[0] == PackageManager.PERMISSION_DENIED
    ) {
      callback.success(0);
      return;
    }
    callback.success(1);
    return;
  }

  private String[] checkPermissions(JSONArray arr) throws Exception {
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < arr.length(); i++) {
      try {
        String permission = arr.getString(i);
        if (permission != null || !permission.equals("")) {
          throw new Exception("Permission cannot be null or empty");
        }
        if (!cordova.hasPermission(permission)) {
          list.add(permission);
        }
      } catch (JSONException e) {}
    }

    String[] res = new String[list.size()];
    return list.toArray(res);
  }

  private void askToManageAllFiles() {
    if (Build.VERSION.SDK_INT >= 30) {
      try {
        Intent intent = new Intent();
        intent.setAction("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
        activity.startActivity(intent);
        callback.success();
      } catch (Exception e) {
        callback.error(e.toString());
      }
      return;
    }

    callback.error("Not supported");
  }

  private void isExternalStorageManager() {
    boolean res = false;
    if (Build.VERSION.SDK_INT >= 30) {
      res = Environment.isExternalStorageManager();
    }

    callback.success(res ? 1 : 0);
  }

  private void getAndroidVersion() {
    callback.success(Build.VERSION.SDK_INT);
  }

  private void getWebkitInfo() {
    PackageInfo info = null;
    JSONObject res = new JSONObject();

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        info = WebView.getCurrentWebViewPackage();
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Class webViewFactory = Class.forName("android.webkit.WebViewFactory");
        Method method = webViewFactory.getMethod("getLoadedPackageInfo");
        info = (PackageInfo) method.invoke(null);
      } else {
        PackageManager packageManager = activity.getPackageManager();

        try {
          info = packageManager.getPackageInfo("com.google.android.webview", 0);
        } catch (PackageManager.NameNotFoundException e) {
          callback.error("Package not found");
        }

        return;
      }

      res.put("packageName", info.packageName);
      res.put("versionName", info.versionName);
      res.put("versionCode", info.versionCode);

      callback.success(res);
    } catch (
      JSONException
      | InvocationTargetException
      | ClassNotFoundException
      | NoSuchMethodException
      | IllegalAccessException e
    ) {
      callback.error(
        "Cannot determine current WebView engine. (" + e.getMessage() + ")"
      );

      return;
    }
  }

  private void isPowerSaveMode() {
    PowerManager powerManager = (PowerManager) context.getSystemService(
      Context.POWER_SERVICE
    );
    boolean powerSaveMode = powerManager.isPowerSaveMode();

    callback.success(powerSaveMode ? 1 : 0);
  }

  private void shareFile(String fileURI, String filename) {
    Activity activity = this.activity;
    Context context = this.context;
    Uri uri = this.getContentProviderUri(fileURI);
    try {
      Intent intent = new Intent(Intent.ACTION_SEND);
      intent.putExtra(Intent.EXTRA_STREAM, uri);
      if (!filename.equals("")) intent.putExtra(Intent.EXTRA_TEXT, filename);
      intent.setType("application/octet-stream");
      activity.startActivity(intent);
      callback.success(uri.toString());
    } catch (Exception e) {
      callback.error(e.getMessage());
    }
  }

  private void getAppInfo() {
    JSONObject res = new JSONObject();
    try {
      PackageManager pm = activity.getPackageManager();
      PackageInfo pInfo = pm.getPackageInfo(context.getPackageName(), 0);
      ApplicationInfo appInfo = context.getApplicationInfo();

      res.put("firstInstallTime", pInfo.firstInstallTime);
      res.put("lastUpdateTime", pInfo.lastUpdateTime);
      res.put("label", appInfo.loadLabel(pm).toString());
      res.put("packageName", pInfo.packageName);
      res.put("versionName", pInfo.versionName);
      res.put("versionCode", pInfo.getLongVersionCode());

      callback.success(res);
    } catch (JSONException e) {
      callback.error(e.getMessage());
    } catch (Exception e) {
      callback.error(e.getMessage());
    }
  }

  private void openInBrowser(String src) {
    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(src));
    activity.startActivity(browserIntent);
  }

  private void launchApp(String appId, String action, String value) {
    Intent intent = context
      .getPackageManager()
      .getLaunchIntentForPackage(appId);
    if (intent == null) {
      callback.error("App not found");
      return;
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    callback.success("Launched " + appId);
  }

  private void inAppBrowser(String url, String title, boolean showButtons) {
    BrowserDialog browserDialog = new BrowserDialog(
      context,
      android.R.style.Theme_NoTitleBar
    );
    browserDialog.setShowButtons(showButtons);
    browserDialog.setTheme(themeColor, themeType);
    browserDialog.show(url, title);
  }

  private void addShortcut(
    String id,
    String label,
    String description,
    String iconSrc,
    String action,
    String data
  ) {
    try {
      Intent intent;
      ImageDecoder.Source imgSrc;
      Bitmap bitmap;
      IconCompat icon;

      imgSrc =
        ImageDecoder.createSource(
          context.getContentResolver(),
          Uri.parse(iconSrc)
        );
      bitmap = ImageDecoder.decodeBitmap(imgSrc);
      icon = IconCompat.createWithBitmap(bitmap);
      intent =
        activity
          .getPackageManager()
          .getLaunchIntentForPackage(activity.getPackageName());
      intent.putExtra("action", action);
      intent.putExtra("data", data);

      ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(label)
        .setLongLabel(description)
        .setIcon(icon)
        .setIntent(intent)
        .build();

      ShortcutManagerCompat.pushDynamicShortcut(context, shortcut);
      callback.success();
    } catch (Exception e) {
      callback.error(e.toString());
    }
  }

  private void pinShortcut(String id) {
    ShortcutManager shortcutManager = context.getSystemService(
      ShortcutManager.class
    );

    if (shortcutManager.isRequestPinShortcutSupported()) {
      ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(context, id)
        .build();

      Intent pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(
        pinShortcutInfo
      );

      PendingIntent successCallback = PendingIntent.getBroadcast(
        context,
        0,
        pinnedShortcutCallbackIntent,
        0
      );

      shortcutManager.requestPinShortcut(
        pinShortcutInfo,
        successCallback.getIntentSender()
      );

      callback.success();
    }

    callback.error("Not suppported");
  }

  private void removeShortcut(String id) {
    try {
      List<String> list = new ArrayList<String>();
      list.add(id);
      ShortcutManagerCompat.removeDynamicShortcuts(context, list);
      callback.success();
    } catch (Exception e) {
      callback.error(e.toString());
    }
  }

  private void setUiTheme(final String color, final String type) {
    if (Build.VERSION.SDK_INT >= 21) {
      final int bgColor = Color.parseColor(color);
      final Window window = activity.getWindow();
      themeColor = bgColor;
      themeType = type.toLowerCase();
      // Method and constants not available on all SDKs but we want to be able to compile this code with any SDK
      window.clearFlags(0x04000000); // SDK 19: WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
      window.addFlags(0x80000000); // SDK 21: WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
      try {
        // Using reflection makes sure any 5.0+ device will work without having to compile with SDK level 21

        window
          .getClass()
          .getMethod("setNavigationBarColor", int.class)
          .invoke(window, themeColor);

        window
          .getClass()
          .getMethod("setStatusBarColor", int.class)
          .invoke(window, themeColor);

        setStatusBarStyle(window);
        setNavigationBarStyle(window);
      } catch (IllegalArgumentException ignore) {} catch (Exception ignore) {}
    }
  }

  private void setStatusBarStyle(final Window window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      View decorView = window.getDecorView();
      int uiOptions = decorView.getSystemUiVisibility();

      if (themeType.equals("light")) {
        decorView.setSystemUiVisibility(
          uiOptions | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        return;
      }
      decorView.setSystemUiVisibility(
        uiOptions & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
      );
    }
  }

  private void setNavigationBarStyle(final Window window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      View decorView = window.getDecorView();
      int uiOptions = decorView.getSystemUiVisibility();

      // 0x80000000 FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
      // 0x00000010 SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

      if (themeType.equals("light")) {
        decorView.setSystemUiVisibility(uiOptions | 0x80000000 | 0x00000010);
        return;
      }
      decorView.setSystemUiVisibility(uiOptions | 0x80000000 & ~0x00000010);
    }
  }

  private void setIntentHandler(CallbackContext callbackContext) {
    intentHandler = callbackContext;
  }

  @Override
  public void onNewIntent(Intent intent) {
    if (intentHandler != null) {
      PluginResult result = new PluginResult(
        PluginResult.Status.OK,
        getIntentJson(intent)
      );
      result.setKeepCallback(true);
      intentHandler.sendPluginResult(result);
    }
  }

  private JSONObject getIntentJson(Intent intent) {
    JSONObject json = new JSONObject();
    try {
      json.put("action", intent.getAction());
      json.put("data", intent.getDataString());
      json.put("type", intent.getType());
      json.put("package", intent.getPackage());
      json.put("extras", getExtrasJson(intent.getExtras()));
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return json;
  }

  private JSONObject getExtrasJson(Bundle extras) {
    JSONObject json = new JSONObject();
    if (extras != null) {
      for (String key : extras.keySet()) {
        try {
          Object value = extras.get(key);
          if (value instanceof String) {
            json.put(key, (String) value);
          } else if (value instanceof Integer) {
            json.put(key, (Integer) value);
          } else if (value instanceof Long) {
            json.put(key, (Long) value);
          } else if (value instanceof Double) {
            json.put(key, (Double) value);
          } else if (value instanceof Float) {
            json.put(key, (Float) value);
          } else if (value instanceof Boolean) {
            json.put(key, (Boolean) value);
          } else if (value instanceof Bundle) {
            json.put(key, getExtrasJson((Bundle) value));
          } else {
            json.put(key, value.toString());
          }
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    }
    return json;
  }

  private Uri getContentProviderUri(String fileUri) {
    Uri uri = Uri.parse(fileUri);
    String Id = context.getPackageName();
    if (fileUri.matches("file:///(.*)")) {
      File file = new File(uri.getPath());
      uri = FileProvider.getUriForFile(context, Id + ".provider", file);
    }
    return uri;
  }

  private boolean isPackageInstalled(
    String packageName,
    PackageManager packageManager
  ) {
    try {
      packageManager.getPackageInfo(packageName, 0);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  private String getString(JSONArray ar, int index) {
    try {
      return ar.getString(index);
    } catch (JSONException e) {
      return null;
    }
  }

  private boolean getBoolean(JSONArray ar, int index) {
    try {
      return ar.getBoolean(index);
    } catch (JSONException e) {
      return false;
    }
  }

  private int getInt(JSONArray ar, int index) {
    try {
      return ar.getInt(index);
    } catch (JSONException e) {
      return 0;
    }
  }

  private JSONArray getJSONArray(JSONArray ar, int index) {
    try {
      return ar.getJSONArray(index);
    } catch (JSONException e) {
      return null;
    }
  }
}
