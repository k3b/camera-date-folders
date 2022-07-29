/*
 * Copyright (C) 2022 Andreas Kromke, andreas.kromke@gmail.com
 *
 * This program is free software; you can redistribute it or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package de.kromke.andreas.cameradatefolders;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import de.kromke.andreas.cameradatefolders.databinding.ActivityMainBinding;
import de.kromke.andreas.cameradatefolders.ui.paths.PathsFragment;
import de.kromke.andreas.cameradatefolders.ui.home.HomeFragment;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
import static de.kromke.andreas.cameradatefolders.ui.paths.PathsFragment.PREF_CAM_FOLDER_URI;
import static de.kromke.andreas.cameradatefolders.ui.paths.PathsFragment.PREF_DEST_FOLDER_URI;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_DRY_RUN;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_FOLDER_SCHEME;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_FORCE_FILE_MODE;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_BACKUP_COPY;

// https://stackoverflow.com/questions/63548323/how-to-use-viewmodel-in-a-fragment
// https://stackoverflow.com/questions/6091194/how-to-handle-button-clicks-using-the-xml-onclick-within-fragments
// activityViewModel ???

@SuppressWarnings("Convert2Lambda")
public class MainActivity extends AppCompatActivity
{
    private static final String LOG_TAG = "CDF : MainActivity";
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 11;
    @SuppressWarnings("FieldCanBeLocal")
    private ActivityMainBinding binding;
    ActivityResultLauncher<Intent> mStorageAccessPermissionActivityLauncher;
    ActivityResultLauncher<Intent> mRequestDirectorySelectActivityLauncher;
    Uri mDcimTreeUri = null;
    Uri mDestTreeUri = null;
    Timer mTimer;
    MyTimerTask mTimerTask;
    private static final int timerFrequency = 500;         // milliseconds
    String mCurrHomeText = "";
    String mNewHomeText = "";     // set from worker thread, but in UI thread context
    private Button mStartButton = null;
    private CharSequence mStartButtonText;
    private Button mRevertButton = null;
    private CharSequence mRevertButtonText;
    private boolean mbThreadRunning = false;
    private boolean mbThreadRunningRevert = false;
    private final static int sMaxLogLen = 10000;
    private boolean mbPermissionGranted = false;
    private boolean mbSafModeIsDestFolder = false;  // hack, because cannot pass parameters



    /**************************************************************************
     *
     * called in worker thread context
     *
     *************************************************************************/
    public void messageFromThread(int result, int result2, int result3, final String text, boolean threadEnded)
    {
        runOnUiThread(new Thread(new Runnable()
        {
            public void run()
            {
                //Log.d(LOG_TAG, "called from thread");
                if (threadEnded)
                {
                    if (result >= 0)
                    {
                        mNewHomeText += "success:" + result + ", failure:" + result2 + ", unchanged:" + result3 +"\n";
                    }
                    else
                    {
                        mNewHomeText += "ERROR" + "\n";
                    }

                    // restore button text, currently it is "STOP"
                    if (mStartButton != null)
                    {
                        mStartButton.setText(mStartButtonText);
                        mStartButton = null;
                    }

                    if (mRevertButton != null)
                    {
                        mRevertButton.setText(mRevertButtonText);
                        mRevertButton = null;
                    }

                    mbThreadRunning = false;
                }
                else
                if ((text != null) && !text.isEmpty())
                {
                    mNewHomeText += text + "\n";
                }
            }
        }));
    }


    /**************************************************************************
     *
     * GUI update timer callback, shows deferred messages from worker thread
     *
     *************************************************************************/
    public void TimerCallback()
    {
        //Log.d(LOG_TAG, "TimerCallback()");

        Fragment f = getCurrFragment();
        if (f instanceof HomeFragment)
        {
            boolean bUpdate = false;

            final String strNoCamPath = getString(R.string.str_no_cam_path);
            if (!mNewHomeText.isEmpty())
            {
                if (mCurrHomeText.length() > sMaxLogLen)
                {
                    // text too long, remove ten percent
                    mCurrHomeText = mCurrHomeText.substring(9 * (sMaxLogLen/10));
                }
                mCurrHomeText += mNewHomeText;
                mNewHomeText = "";
                bUpdate = true;
            }
            else
            if (mDcimTreeUri == null)
            {
                if (!mCurrHomeText.equals(strNoCamPath))
                {
                    mCurrHomeText = strNoCamPath;
                    bUpdate = true;
                }
            }
            else
            {
                if (mCurrHomeText.isEmpty() || mCurrHomeText.equals(strNoCamPath))
                {
                    mCurrHomeText = getString(R.string.str_press_start);
                    bUpdate = true;
                }
            }

            if (bUpdate)
            {
                HomeFragment fd = (HomeFragment) f;
                fd.onTextChanged(mCurrHomeText);
            }
        }

    }


    /**************************************************************************
     *
     * GUI update timer, shows deferred messages from worker thread
     *
     *************************************************************************/
    class MyTimerTask extends TimerTask
    {
        @Override
        public void run()
        {
            runOnUiThread(MainActivity.this::TimerCallback);
        }
    }


    /**************************************************************************
     *
     * Activity method
     *
     *************************************************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        registerStorageAccessPermissionCallback();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String val = prefs.getString(PREF_CAM_FOLDER_URI, null);
        if (val != null)
        {
            mDcimTreeUri = Uri.parse(val);
        }
        val = prefs.getString(PREF_DEST_FOLDER_URI, null);
        if (val != null)
        {
            mDestTreeUri = Uri.parse(val);
        }

        registerDirectorySelectCallback();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings, R.id.navigation_info)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
        {
            // We need the old (pre-Android-11) file read/write permissions at once, otherwise
            // we cannot run on older Android versions. Later we may ask for full
            // file access on Android 11 and above.
            requestForPermissionOld();
        }
    }


    /************************************************************************************
     *
     * Activity method
     *
     ***********************************************************************************/
    @Override
    protected void onStart()
    {
        mTimerTask = new MyTimerTask();
        //delay 1000ms, repeat in <timerFrequency>ms
        mTimer = new Timer();
        mTimer.schedule(mTimerTask, 1000, timerFrequency);
        super.onStart();
    }


    /************************************************************************************
     *
     * Activity method
     *
     ***********************************************************************************/
    @Override
    protected void onStop()
    {
        // stop timer
        mTimer.cancel();    // note that a cancelled timer cannot be re-scheduled. Why not?
        mTimer = null;
        mTimerTask = null;

        super.onStop();
    }


    /**************************************************************************
     *
     * permission was granted, immediately or later
     *
     *************************************************************************/
    private void onPermissionGranted()
    {
        mbPermissionGranted = true;
    }


    /**************************************************************************
     *
     * Activity method
     *
     * File read request granted or denied, only used for Android 10 or older
     *
     *************************************************************************/
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode)
        {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
            {
                // If request is cancelled, the result arrays are empty.
                //noinspection StatementWithEmptyBody
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                {
                    onPermissionGranted();
                } else
                {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
            }
        }
    }


    /**************************************************************************
     *
     * variant for Android 4.4 .. 10
     *
     *************************************************************************/
    private void requestForPermissionOld()
    {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED)
        {
            Log.d(LOG_TAG, "permission immediately granted");
            onPermissionGranted();
        } else
        {
            mbPermissionGranted = false;
            Log.d(LOG_TAG, "request permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }


    /**************************************************************************
     *
     * variant for Android 11 (API 30)
     *
     *************************************************************************/
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void requestForPermission30()
    {
        if (Environment.isExternalStorageManager())
        {
            Log.d(LOG_TAG, "permission immediately granted");
            onPermissionGranted();
        }
        else
        {
            Intent intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", this.getPackageName(), null);
            intent.setData(uri);
            mStorageAccessPermissionActivityLauncher.launch(intent);
        }
    }


    /**************************************************************************
     *
     * helper for deprecated startActivityForResult()
     *
     *************************************************************************/
    private void registerStorageAccessPermissionCallback()
    {
        mStorageAccessPermissionActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>()
                {
                    @Override
                    @RequiresApi(api = Build.VERSION_CODES.R)
                    public void onActivityResult(ActivityResult result)
                    {
                        // Note that the resultCode is not helpful here, fwr
                        if (Environment.isExternalStorageManager())
                        {
                            Log.d(LOG_TAG, "registerStorageAccessPermissionCallback(): permission granted");
                            onPermissionGranted();
                        }
                        else
                        {
                            Log.d(LOG_TAG, "registerStorageAccessPermissionCallback(): permission denied");
                            Toast.makeText(getApplicationContext(), R.string.str_permission_denied, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }


    /**************************************************************************
     *
     * helper to start SAF file selector
     *
     *************************************************************************/
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected Intent createSafPickerIntent()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // Ask for read and write access to files and sub-directories in the user-selected directory.
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION +
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION +
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION +
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        return intent;
    }


    /**************************************************************************
     *
     * helper to run thread
     *
     *************************************************************************/
    private void runThread(boolean bFlatten)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        boolean forceFileMode = prefs.getBoolean(PREF_FORCE_FILE_MODE, false);
        boolean fileMode = forceFileMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N);

        if (fileMode)
        {
            requestForPermissionOld();
            if (!mbPermissionGranted)
            {
                Toast.makeText(this, "No permission, yet. Grant and retry!", Toast.LENGTH_LONG).show();
                return;
            }
        }

        boolean dryRun = prefs.getBoolean(PREF_DRY_RUN, false);
        if (dryRun)
        {
            Toast.makeText(this, "Dry Run!", Toast.LENGTH_LONG).show();
        }

        boolean backupCopy = prefs.getBoolean(PREF_BACKUP_COPY, false);

        MyApplication app = (MyApplication) getApplication();
        String scheme = (bFlatten) ? "flat" :  prefs.getString(PREF_FOLDER_SCHEME, "ymd");
        int result = app.runWorkerThread(this, mDcimTreeUri, mDestTreeUri, scheme, backupCopy, dryRun, fileMode);
        if (result == 0)
        {
            mCurrHomeText = "";
            mNewHomeText = fileMode ? "in progress (File mode)...\n\n" : "in progress...\n\n";
            mbThreadRunningRevert = bFlatten;
            mbThreadRunning = true;
        }
        else
        {
            Log.e(LOG_TAG, "cannot run worker thread");
        }
    }


    /**************************************************************************
     *
     * helper to stop thread
     *
     *************************************************************************/
    private void stopThread()
    {
        MyApplication app = (MyApplication) getApplication();
        app.stopWorkerThread();
    }


    /**************************************************************************
     *
     * onClick callback
     *
     *************************************************************************/
    public void onClickButtonStart(View view)
    {
        if (mbThreadRunning)
        {
            if (!mbThreadRunningRevert)
            {
                mNewHomeText = "stopping...\n\n";
                stopThread();
            }
        }
        else
        {
            runThread(false);
            if (mbThreadRunning)
            {
                mStartButton = (Button) view;
                mStartButtonText = mStartButton.getText();
                mStartButton.setText(R.string.str_stop);
            }
        }
    }


    /**************************************************************************
     *
     * onClick callback
     *
     *************************************************************************/
    public void onClickButtonRevert(View view)
    {
        if (mbThreadRunning)
        {
            if (mbThreadRunningRevert)
            {
                mNewHomeText = "stopping...\n\n";
                stopThread();
            }
        }
        else
        {
            runThread(true);
            if (mbThreadRunning)
            {
                mRevertButton = (Button) view;
                mRevertButtonText = mRevertButton.getText();
                mRevertButton.setText(R.string.str_stop);
            }
        }
    }


    /**************************************************************************
     *
     * onClick callback
     *
     *************************************************************************/
    public void onClickSelectCameraFolder(View view)
    {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
        {
            mbSafModeIsDestFolder = false;
            Intent intent = null;
            intent = createSafPickerIntent();
            mRequestDirectorySelectActivityLauncher.launch(intent);
        }
        else
        {
            File dcimPath = new File(Environment.getExternalStorageDirectory(), "DCIM");
            File camPath = new File(dcimPath, "Camera");
            if (!camPath.isDirectory())
            {
                camPath = dcimPath;
            }
            mDcimTreeUri = Uri.fromFile(camPath);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor prefEditor = prefs.edit();
            prefEditor.putString(PREF_CAM_FOLDER_URI, mDcimTreeUri.toString());
            prefEditor.apply();

            onPathWasChanged();
        }
    }


    /**************************************************************************
     *
     * onClick callback
     *
     *************************************************************************/
    public void onClickSelectDestinationFolder(View view)
    {
        if (mDcimTreeUri == null)
        {
            Toast.makeText(getApplicationContext(), R.string.str_must_select_dcim_path, Toast.LENGTH_LONG).show();
        }
        else
        {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
            {
                mbSafModeIsDestFolder = true;
                Intent intent = createSafPickerIntent();
                mRequestDirectorySelectActivityLauncher.launch(intent);
            }
            else
            {
                mDestTreeUri = null;
                onPathWasChanged();
            }
        }
    }


    /**************************************************************************
     *
     * call PathsFragment() due to configuration change
     *
     * https://stackoverflow.com/questions/51385067/android-navigation-architecture-component-get-current-visible-fragment
     *
     *************************************************************************/
    private void onPathWasChanged()
    {
        FragmentManager fm = getSupportFragmentManager();
        // Fragment f = fm.findFragmentById(R.id.navigation_dashboard);     DOES not work, because of navigation bar
        Fragment f = fm.findFragmentById(R.id.nav_host_fragment_activity_main);
        if (f != null)
        {
            f = f.getChildFragmentManager().getFragments().get(0);
            if (f instanceof PathsFragment)
            {
                PathsFragment fd = (PathsFragment) f;
                String val1 = (mDcimTreeUri == null) ? null : mDcimTreeUri.getPath();
                String val2 = (mDestTreeUri == null) ? null : mDestTreeUri.getPath();
                fd.onPathChanged(val1, val2);
            }
        }
    }


    /**************************************************************************
     *
     * https://stackoverflow.com/questions/51385067/android-navigation-architecture-component-get-current-visible-fragment
     *
     *************************************************************************/
    private Fragment getCurrFragment()
    {
        FragmentManager fm = getSupportFragmentManager();
        // Fragment f = fm.findFragmentById(R.id.navigation_dashboard);     DOES not work, because of navigation bar
        Fragment f = fm.findFragmentById(R.id.nav_host_fragment_activity_main);
        if (f != null)
        {
            //List<Fragment> fragmentList = f.getChildFragmentManager().getFragments();
            f = f.getChildFragmentManager().getFragments().get(0);
        }

        return f;
    }


    /**************************************************************************
     *
     * helper for deprecated startActivityForResult()
     *
     *************************************************************************/
    private void registerDirectorySelectCallback()
    {
        mRequestDirectorySelectActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>()
                {
                    @Override
                    public void onActivityResult(ActivityResult result)
                    {
                        int resultCode = result.getResultCode();
                        Intent data = result.getData();
                        boolean bUpdatePrefs = false;
                        Uri treeUri = null;

                        if ((resultCode == RESULT_OK) && (data != null))
                        {
                            treeUri = data.getData();
                            if (treeUri != null)
                            {
                                Log.d(LOG_TAG, " URI = " + treeUri.getPath());
                                if (mbSafModeIsDestFolder)
                                {
                                    if (Utils.pathsOverlap(mDcimTreeUri, treeUri))
                                    {
                                        treeUri = null;     // remove path
                                        Toast.makeText(getApplicationContext(), R.string.str_paths_overlap, Toast.LENGTH_LONG).show();
                                    }
                                    else
                                    {
                                        // in case we are going to use file mode, check if destination is write permitted
                                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                                        boolean forceFileMode = prefs.getBoolean(PREF_FORCE_FILE_MODE, false);
                                        boolean bFileMode = forceFileMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N);
                                        if (bFileMode)
                                        {
                                            String p = UriToPath.getPathFromUri(getApplicationContext(), treeUri);
                                            File f = (p == null) ? null : new File(p);
                                            if ((f == null) || !f.canWrite())
                                            {
                                                Toast.makeText(getApplicationContext(), R.string.str_permission_denied, Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    }
                                }
                                bUpdatePrefs = true;
                            }
                        }
                        else
                        if (mbSafModeIsDestFolder)
                        {
                            // remove dest folder
                            bUpdatePrefs = true;
                        }

                        if (bUpdatePrefs)
                        {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            SharedPreferences.Editor prefEditor = prefs.edit();
                            final String val = (treeUri != null) ? treeUri.toString() : null;
                            if (mbSafModeIsDestFolder)
                            {
                                prefEditor.putString(PREF_DEST_FOLDER_URI, val);
                                mDestTreeUri = treeUri;
                            }
                            else
                            {
                                prefEditor.putString(PREF_CAM_FOLDER_URI, val);
                                mDcimTreeUri = treeUri;
                            }
                            prefEditor.apply();
                            onPathWasChanged();
                        }
                    }
                });
    }
}