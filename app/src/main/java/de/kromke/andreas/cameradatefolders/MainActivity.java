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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import de.kromke.andreas.cameradatefolders.databinding.ActivityMainBinding;
import de.kromke.andreas.cameradatefolders.ui.paths.PathsFragment;
import de.kromke.andreas.cameradatefolders.ui.home.HomeFragment;

import static de.kromke.andreas.cameradatefolders.ui.paths.PathsFragment.PREF_CAM_FOLDER_URI;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_DRY_RUN;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_FOLDER_SCHEME;

// https://stackoverflow.com/questions/63548323/how-to-use-viewmodel-in-a-fragment
// https://stackoverflow.com/questions/6091194/how-to-handle-button-clicks-using-the-xml-onclick-within-fragments
// activityViewModel ???

@SuppressWarnings("Convert2Lambda")
public class MainActivity extends AppCompatActivity
{
    private static final String LOG_TAG = "CDF : MainActivity";
    @SuppressWarnings("FieldCanBeLocal")
    private ActivityMainBinding binding;
    ActivityResultLauncher<Intent> mRequestDirectorySelectActivityLauncher;
    Uri mDcimTreeUri = null;
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
                Log.d(LOG_TAG, "called from thread");
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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String val = prefs.getString(PREF_CAM_FOLDER_URI, null);
        if (val != null)
        {
            mDcimTreeUri = Uri.parse(val);
        }

        registerDirectorySelectCallback();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
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
     * helper to start SAF file selector
     *
     *************************************************************************/
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

        String scheme = (bFlatten) ? "flat" :  prefs.getString(PREF_FOLDER_SCHEME, "ymd");
        boolean dryRun = prefs.getBoolean(PREF_DRY_RUN, false);
        if (dryRun)
        {
            Toast.makeText(this, "Dry Run!", Toast.LENGTH_LONG).show();
        }

        MyApplication app = (MyApplication) getApplication();
        int result = app.runWorkerThread(this, mDcimTreeUri, scheme, dryRun);
        if (result == 0)
        {
            mCurrHomeText = "";
            mNewHomeText = "in progress...\n\n";
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
        Intent intent = createSafPickerIntent();
        mRequestDirectorySelectActivityLauncher.launch(intent);
    }


    /**************************************************************************
     *
     * call PathsFragment() due to configuration change
     *
     * https://stackoverflow.com/questions/51385067/android-navigation-architecture-component-get-current-visible-fragment
     *
     *************************************************************************/
    private void onDcimPathChanged()
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
                fd.onPathChanged(mDcimTreeUri.getPath());
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

                        if ((resultCode == RESULT_OK) && (data != null))
                        {
                            Uri treeUri = data.getData();
                            if (treeUri != null)
                            {
                                Log.d(LOG_TAG, " URI = " + treeUri.getPath());

                                grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                mDcimTreeUri = treeUri;

                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                                SharedPreferences.Editor prefEditor = prefs.edit();
                                prefEditor.putString(PREF_CAM_FOLDER_URI, treeUri.toString());
                                prefEditor.apply();

                                onDcimPathChanged();
                            }
                        }
                    }
                });
    }
}