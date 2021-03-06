package com.espressif.iot.ui.settings;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.log4j.Logger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.espressif.iot.R;
import com.espressif.iot.base.api.EspBaseApiUtil;
import com.espressif.iot.base.application.EspApplication;
import com.espressif.iot.base.net.rest2.EspHttpDownloadUtil.ProgressUpdateListener;
import com.espressif.iot.esppush.EspPushUtils;
import com.espressif.iot.log.LogConfigurator;
import com.espressif.iot.log.ReadLogTask;
import com.espressif.iot.type.upgrade.EspUpgradeApkResult;
import com.espressif.iot.type.user.EspLoginResult;
import com.espressif.iot.ui.configure.WifiConfigureActivity;
import com.espressif.iot.ui.login.LoginTask;
import com.espressif.iot.ui.login.LoginThirdPartyDialog;
import com.espressif.iot.ui.login.LoginThirdPartyDialog.OnLoginListener;
import com.espressif.iot.ui.register.RegisterActivity;
import com.espressif.iot.user.IEspUser;
import com.espressif.iot.user.builder.BEspUser;
import com.espressif.iot.util.AccountUtil;
import com.espressif.iot.util.EspStrings;
import com.espressif.iot.util.EspDefaults;

public class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener
{
    private final Logger log = Logger.getLogger(getClass());
    
    /**
     * The default update log URL
     */
    private static final String DEFAULT_VERSION_LOG_URL = "file:///android_asset/html/en_us/update.html";
    /**
     * The update log URL for WebView
     */
    private static final String VERSION_LOG_URL = "file:///android_asset/html/%locale/update.html";
    /**
     * The path for AssetManager
     */
    private static final String VERSION_LOG_PATH = "html/%locale/update.html";
    
    private static final int REQUEST_REGISTER = 1000;
    
    private static final String KEY_ACCOUNT_CATEGORY = "account_category";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_ACCOUNT_REGISTER = "account_register";
    private static final String KEY_ACCOUNT_AUTO_LOGIN = "account_auto_login";
    private static final String KEY_ACCOUNT_LOGOUT = "account_logout";
    
    private static final String KEY_AUTO_REFRESH_DEVICE = "device_auto_refresh";
    private static final String KEY_AUTO_CONFIGURE_DEVICE = "device_auto_configure";
    private static final String KEY_SHOW_MESH_TREE = "device_show_mesh_tree";
    
    private static final String KEY_VERSION_CATEGORY = "version_category";
    private static final String KEY_VERSION_NAME = "version_name";
    private static final String KEY_VERSION_UPGRADE = "version_upgrade";
    private static final String KEY_VERSION_LOG = "version_log";
    
    private static final String KEY_STORE_LOG = "store_log";
    private static final String KEY_READ_LOG = "read_log";
    private static final String KEY_CLEAR_LOG = "clear_log";
    
    private static final String KEY_WIFI_EDIT = "wifi_edit";
    
    private static final String KEY_MESSAGE_ESPPUSH = "message_esppush";
    
    private PreferenceCategory mAccountCategory;
    private Preference mAccountPre;
    private Preference mAccountRegisterPre;
    private CheckBoxPreference mAutoLoginPre;
    private Preference mAccountLogoutPre;
    
    private ListPreference mAutoRefreshDevicePre;
    private ListPreference mAutoConfigureDevicePre;
    private CheckBoxPreference mShowMeshTreePre;
    
    private PreferenceCategory mVersionCategory;
    private Preference mVersionNamePre;
    private Preference mVersionUpgradePre;
    private Preference mVersionLogPre;
    
    private CheckBoxPreference mStoreLogPre;
    private Preference mReadLogPre;
    private Preference mClearLogPre;
    
    private Preference mWifiEditPre;
    
    private CheckBoxPreference mEspPushPre;
    
    private IEspUser mUser;
    
    private UpgradeApkTask mUpgradeApkTask;
    
    private AlertDialog mLogDialog;
    private List<String> mLogList;
    private ArrayAdapter<String> mLogAdapter;
    
    private SharedPreferences mShared;
    
    private LocalBroadcastManager mBroadcastManager;
    
    private LoginThirdPartyDialog mThirdPartyLoginDialog;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.settings);
        
        mUser = BEspUser.getBuilder().getInstance();
        mShared = getActivity().getSharedPreferences(EspStrings.Key.SETTINGS_NAME, Context.MODE_PRIVATE);
        mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
        
        // About Account
        mAccountCategory = (PreferenceCategory)findPreference(KEY_ACCOUNT_CATEGORY);
        mAccountPre = findPreference(KEY_ACCOUNT);
        mAutoLoginPre = (CheckBoxPreference)findPreference(KEY_ACCOUNT_AUTO_LOGIN);
        mAutoLoginPre.setChecked(mShared.getBoolean(EspStrings.Key.KEY_AUTO_LOGIN, EspDefaults.AUTO_LOGIN));
        mAutoLoginPre.setOnPreferenceChangeListener(this);
        onLoginChanged(mUser.isLogin());
        
        mThirdPartyLoginDialog = new LoginThirdPartyDialog(getActivity());
        mThirdPartyLoginDialog.setOnLoginListener(mThirdPartyLoginListener);
        
        // About Device
        mAutoRefreshDevicePre = (ListPreference)findPreference(KEY_AUTO_REFRESH_DEVICE);
        String autoRefreshTime =
            "" + mShared.getLong(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_REFRESH, EspDefaults.AUTO_REFRESH_DEVICE_TIME);
        mAutoRefreshDevicePre.setValue(autoRefreshTime);
        mAutoRefreshDevicePre.setSummary(mAutoRefreshDevicePre.getEntry());
        mAutoRefreshDevicePre.setOnPreferenceChangeListener(this);
        mShowMeshTreePre = (CheckBoxPreference)findPreference(KEY_SHOW_MESH_TREE);
        boolean showMeshTree =
            mShared.getBoolean(EspStrings.Key.SETTINGS_KEY_SHOW_MESH_TREE, EspDefaults.SHOW_MESH_TREE);
        mShowMeshTreePre.setChecked(showMeshTree);
        mShowMeshTreePre.setOnPreferenceChangeListener(this);
        
        mAutoConfigureDevicePre = (ListPreference)findPreference(KEY_AUTO_CONFIGURE_DEVICE);
        String autoConfigureValue =
            "" + mShared.getInt(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_CONFIGURE, EspDefaults.AUTO_CONFIGRUE_RSSI);
        mAutoConfigureDevicePre.setValue(autoConfigureValue);
        mAutoConfigureDevicePre.setSummary(mAutoConfigureDevicePre.getEntry());
        mAutoConfigureDevicePre.setOnPreferenceChangeListener(this);
        
        // About Version
        mVersionCategory = (PreferenceCategory)findPreference(KEY_VERSION_CATEGORY);
        mVersionNamePre = findPreference(KEY_VERSION_NAME);
        String versionName = EspApplication.sharedInstance().getVersionName();
        mVersionNamePre.setSummary(versionName);
        
        mVersionUpgradePre = findPreference(KEY_VERSION_UPGRADE);
        if (mVersionUpgradePre != null && !EspApplication.SUPPORT_APK_UPGRADE)
        {
            mVersionCategory.removePreference(mVersionUpgradePre);
        }
        
        mVersionLogPre = findPreference(KEY_VERSION_LOG);
        
        // About DEBUG
        mStoreLogPre = (CheckBoxPreference)findPreference(KEY_STORE_LOG);
        mStoreLogPre.setOnPreferenceChangeListener(this);
        mReadLogPre = findPreference(KEY_READ_LOG);
        boolean store = mShared.getBoolean(EspStrings.Key.SETTINGS_KEY_STORE_LOG, EspDefaults.STORE_LOG);
        mStoreLogPre.setChecked(store);
        mClearLogPre = findPreference(KEY_CLEAR_LOG);
        
        mLogList = new ArrayList<String>();
        mLogAdapter =
            new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, mLogList);
        mLogDialog = new AlertDialog.Builder(getActivity()).setAdapter(mLogAdapter, null).create();
        
        mWifiEditPre = findPreference(KEY_WIFI_EDIT);
        
        mEspPushPre = (CheckBoxPreference)findPreference(KEY_MESSAGE_ESPPUSH);
        boolean espPushOnOff = mShared.getBoolean(EspStrings.Key.SETTINGS_KEY_ESPPUSH, EspDefaults.ESPPUSH_ON);
        mEspPushPre.setChecked(espPushOnOff);
        mEspPushPre.setOnPreferenceChangeListener(this);
        mEspPushPre.setEnabled(mUser.isLogin());
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        
        if (mUpgradeApkTask != null)
        {
            mUpgradeApkTask.cancel(true);
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_REGISTER)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                // Register completed, direct login
                String email = data.getStringExtra(EspStrings.Key.REGISTER_NAME_EMAIL);
                String password = data.getStringExtra(EspStrings.Key.REGISTER_NAME_PASSWORD);
                
                login(email, password);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
    {
        if (preference == mVersionUpgradePre)
        {
            updateApk();
            return true;
        }
        else if (preference == mVersionLogPre)
        {
            showUpdateLogDialog();
            return true;
        }
        else if (preference == mReadLogPre)
        {
            readDebugLog();
            return true;
        }
        else if (preference == mClearLogPre)
        {
            clearDebugLog();
            return true;
        }
        else if (preference == mAccountPre)
        {
            if (!mUser.isLogin())
            {
                showLoginDialog();
                return true;
            }
        }
        else if (preference == mAccountRegisterPre)
        {
            Intent i = new Intent(getActivity(), RegisterActivity.class);
            startActivityForResult(i, REQUEST_REGISTER);
            return true;
        }
        else if (preference == mAccountLogoutPre)
        {
            logout();
        }
        else if (preference == mWifiEditPre)
        {
            startActivity(new Intent(getActivity(), WifiConfigureActivity.class));
            return true;
        }
        
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference == mAutoLoginPre)
        {
            boolean autoLogin = (Boolean)newValue;
            mShared.edit().putBoolean(EspStrings.Key.KEY_AUTO_LOGIN, autoLogin).apply();
            return true;
        }
        else if (preference == mAutoRefreshDevicePre)
        {
            String time = newValue.toString();
            mAutoRefreshDevicePre.setValue(time);
            mAutoRefreshDevicePre.setSummary(mAutoRefreshDevicePre.getEntry());
            mShared.edit().putLong(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_REFRESH, Long.parseLong(time)).apply();
            return true;
        }
        else if (preference == mAutoConfigureDevicePre)
        {
            String value = newValue.toString();
            mAutoConfigureDevicePre.setValue(value);
            mAutoConfigureDevicePre.setSummary(mAutoConfigureDevicePre.getEntry());
            mShared.edit().putInt(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_CONFIGURE, Integer.parseInt(value)).apply();
            return true;
        }
        else if (preference == mShowMeshTreePre)
        {
            boolean showMeshTree = (Boolean)newValue;
            mShared.edit().putBoolean(EspStrings.Key.SETTINGS_KEY_SHOW_MESH_TREE, showMeshTree).apply();
            return true;
        }
        else if (preference == mStoreLogPre)
        {
            onStoreDebugLogChanged((Boolean) newValue);
            return true;
        }
        else if (preference == mEspPushPre)
        {
            boolean onOff = (Boolean)newValue;
            mShared.edit().putBoolean(EspStrings.Key.SETTINGS_KEY_ESPPUSH, onOff).apply();
            if (onOff)
            {
                EspPushUtils.startPushService(getActivity());
            }
            else
            {
                EspPushUtils.stopPushService(getActivity());
            }
            return true;
        }
        
        return false;
    }
    
    //******** About Account *********//
    private void onLoginChanged(boolean isLogin)
    {
        if (isLogin)
        {
            mAccountPre.setTitle(mUser.getUserName());
            mAccountPre.setSummary(mUser.getUserEmail());
            mAutoLoginPre.setEnabled(true);
            
            mAccountRegisterPre = mAccountCategory.findPreference(KEY_ACCOUNT_REGISTER);
            if (mAccountRegisterPre != null)
            {
                mAccountCategory.removePreference(mAccountRegisterPre);
            }
            
            mAccountLogoutPre = new Preference(getActivity());
            mAccountLogoutPre.setKey(KEY_ACCOUNT_LOGOUT);
            mAccountLogoutPre.setTitle(R.string.esp_settings_account_logout);
            mAccountCategory.addPreference(mAccountLogoutPre);
        }
        else
        {
            mAccountPre.setTitle(R.string.esp_settings_account_not_login);
            mAccountPre.setSummary(R.string.esp_settings_account_not_login_summary);
            mAutoLoginPre.setEnabled(false);
            
            mAccountLogoutPre = mAccountCategory.findPreference(KEY_ACCOUNT_LOGOUT);
            if (mAccountLogoutPre != null)
            {
                mAccountCategory.removePreference(mAccountLogoutPre);
            }
            
            mAccountRegisterPre = new Preference(getActivity());
            mAccountRegisterPre.setKey(KEY_ACCOUNT_REGISTER);
            mAccountRegisterPre.setTitle(R.string.esp_settings_account_register);
            mAccountCategory.addPreference(mAccountRegisterPre);
        }
        
        if (mEspPushPre != null)
        {
            mEspPushPre.setEnabled(isLogin);
            if (isLogin)
            {
                if (mEspPushPre.isChecked())
                {
                    EspPushUtils.startPushService(getActivity());
                }
                else
                {
                    EspPushUtils.stopPushService(getActivity());
                }
            }
        }
    }
    
    private void showLoginDialog()
    {
        View view = View.inflate(getActivity(), R.layout.login_dialog, null);
        final EditText accountEdT = (EditText)view.findViewById(R.id.login_edt_account);
        final EditText pwdEdt = (EditText)view.findViewById(R.id.login_edt_password);
        final TextView thirdPartyLoginTV = (TextView)view.findViewById(R.id.login_text_third_party);
        
        final AlertDialog dialog = new AlertDialog.Builder(getActivity()).setTitle(R.string.esp_login_login)
            .setView(view)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
            {
                
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    String account = accountEdT.getText().toString();
                    String password = pwdEdt.getText().toString();
                    login(account, password);
                }
            })
            .show();
        
        thirdPartyLoginTV.setOnClickListener(new View.OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                dialog.dismiss();
                mThirdPartyLoginDialog.show();
            }
        });
    }
    
    private OnLoginListener mThirdPartyLoginListener = new OnLoginListener()
    {
        
        @Override
        public void onLoginComplete(EspLoginResult result)
        {
            if (result == EspLoginResult.SUC)
            {
                loginSuc();
            }
        }
    };
    
    private void login(final String account, final String password)
    {
        final int accountType = AccountUtil.getAccountType(account);
        if (accountType == AccountUtil.TYPE_NONE)
        {
            Toast.makeText(getActivity(), R.string.esp_login_email_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        
        new LoginTask(getActivity())
        {
            @Override
            public EspLoginResult doLogin()
            {
                if (accountType == AccountUtil.TYPE_EMAIL)
                {
                    return mUser.doActionUserLoginInternet(account, password);
                }
                else if (accountType == AccountUtil.TYPE_PHONE)
                {
                    return mUser.doActionUserLoginPhone(account, password);
                }
                
                return null;
            }
            
            public void loginResult(EspLoginResult result)
            {
                if (result == EspLoginResult.SUC)
                {
                    loginSuc();
                }
            }

        }.execute();
    }
    
    private void loginSuc()
    {
        onLoginChanged(true);
        
        mBroadcastManager.sendBroadcast(new Intent(EspStrings.Action.LOGIN_NEW_ACCOUNT));
    }
    
    private void logout()
    {
        mShared.edit().putBoolean(EspStrings.Key.KEY_AUTO_LOGIN, false).apply();
        getActivity().setResult(SettingsActivity.RESULT_CODE_LOGOUT);
        getActivity().finish();
    }
    
    //******** About Version *********//
    private void updateApk()
    {
        ConnectivityManager cm = (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE)
        {
            // Show dialog to hint using mobile data now
            new AlertDialog.Builder(getActivity()).setTitle(R.string.esp_upgrade_apk_mobile_data_title)
                .setMessage(R.string.esp_upgrade_apk_mobile_data_msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        executeUpgradeApkTask();
                    }
                })
                .show();
        }
        else
        {
            executeUpgradeApkTask();
        }
    }
    
    private void executeUpgradeApkTask()
    {
        mUpgradeApkTask = new UpgradeApkTask();
        mUpgradeApkTask.execute();
    }
    
    private class UpgradeApkTask extends AsyncTask<Void, Integer, EspUpgradeApkResult>
    {
        @Override
        protected void onPreExecute()
        {
            mVersionUpgradePre.setEnabled(false);
        }
        
        @Override
        protected EspUpgradeApkResult doInBackground(Void... arg0)
        {
            return EspBaseApiUtil.upgradeApk(mUpdateListener);
        }
        
        @Override
        protected void onPostExecute(EspUpgradeApkResult result)
        {
            mVersionUpgradePre.setEnabled(true);
            
            switch (result)
            {
                case UPGRADE_COMPLETE:
                    Toast.makeText(getActivity(), R.string.esp_upgrade_apk_status_complete_toast, Toast.LENGTH_LONG)
                        .show();
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_complete);
                    break;
                case DOWNLOAD_FAILED:
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_download_failed);
                    break;
                case LOWER_VERSION:
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_lower_version);
                    break;
                case NOT_FOUND:
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_not_found);
                    break;
            }
        }
        
        @Override
        protected void onProgressUpdate(Integer... values)
        {
            int percent = values[0];
            mVersionUpgradePre.setSummary(getString(R.string.esp_upgrade_apk_downloading, percent));
        }
        
        /**
         * Update download progress
         */
        private ProgressUpdateListener mUpdateListener = new ProgressUpdateListener()
        {
            
            @Override
            public void onProgress(long downloadSize, double percent)
            {
                int per = (int)(percent * 100);
                publishProgress(per);
            }
            
        };
    }
    
    /**
     * Show update log
     */
    private void showUpdateLogDialog()
    {
        /*
         * check for the full language + country resource, if not there, check for the only language resource, if not
         * there again, use default(en_us) language log
         */
        boolean isLogFileExist;
        Locale locale = Locale.getDefault();
        String languageCode = locale.getLanguage().toLowerCase(Locale.US);
        String countryCode = locale.getCountry().toLowerCase(Locale.US);
        
        String folderName = languageCode + "_" + countryCode;
        String path = VERSION_LOG_PATH.replace("%locale", folderName);
        // check full language + country resource
        isLogFileExist = isAssetFileExist(path);
        
        if (!isLogFileExist)
        {
            folderName = languageCode;
            path = VERSION_LOG_PATH.replace("%locale", folderName);
            
            // check the only language resource
            isLogFileExist = isAssetFileExist(path);
        }
        
        String url;
        if (isLogFileExist)
        {
            url = VERSION_LOG_URL.replaceAll("%locale", folderName);
        }
        else
        {
            url = DEFAULT_VERSION_LOG_URL;
        }
        
        WebView webview = new WebView(getActivity());
        webview.loadUrl(url);
        
        new AlertDialog.Builder(getActivity()).setView(webview).show();
    }
    
    /**
     * Check whether the log file exist
     * @param path
     * @return
     */
    private boolean isAssetFileExist(String path)
    {
        boolean result = true;
        
        final AssetManager am = getActivity().getAssets();
        InputStream is = null;
        try
        {
            is = am.open(path);
        }
        catch (Exception ignored)
        {
            result = false;
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (Exception ignored)
                {
                }
            }
        }
        
        return result;
    }
    
    //******** About Debug *********//
    private void readDebugLog()
    {
        final ReadLogTask task = new ReadLogTask()
        {
            @Override
            protected void onPreExecute()
            {
                mLogList.clear();
                mLogAdapter.notifyDataSetChanged();
                mLogDialog.show();
            }
            
            @Override
            protected void onProgressUpdate(String... values)
            {
                mLogList.add("\n" + values[0] + "\n");
                mLogAdapter.notifyDataSetChanged();
                mLogDialog.getListView().setSelection(mLogList.size() - 1);
            }
            
            @Override
            protected void onPostExecute(Boolean result)
            {
                if (mLogList.isEmpty())
                {
                    mLogList.add(getActivity().getString(R.string.esp_settings_debug_read_log_no_log));
                    mLogAdapter.notifyDataSetChanged();
                }
            }
        };
        mLogDialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            
            @Override
            public void onDismiss(DialogInterface dialog)
            {
                task.cancel(true);
            }
        });
        task.execute();
    }
    
    private void onStoreDebugLogChanged(Boolean store)
    {
        mShared.edit().putBoolean(EspStrings.Key.SETTINGS_KEY_STORE_LOG, store).apply();
        Logger root = Logger.getRootLogger();
        if (store)
        {
            root.addAppender(LogConfigurator.createFileAppender());
            log.debug("Open log file store");
        }
        else
        {
            root.removeAppender(LogConfigurator.APPENDER_NAME);
        }
    }
    
    private void clearDebugLog()
    {
        File file = new File(LogConfigurator.DefaultLogFileDirPath);
        if (file.isDirectory())
        {
            File[] logFiles = file.listFiles();
            for (int i = 0; i < logFiles.length; i++)
            {
                logFiles[i].delete();
            }
        }
        else
        {
            log.warn("Delete path is not directry");
        }
        if (mStoreLogPre.isChecked())
        {
            Logger root = Logger.getRootLogger();
            root.removeAppender(LogConfigurator.APPENDER_NAME);
            root.addAppender(LogConfigurator.createFileAppender());
        }
    }
    
}
