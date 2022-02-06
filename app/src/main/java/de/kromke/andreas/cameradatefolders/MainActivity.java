package de.kromke.andreas.cameradatefolders;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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
import de.kromke.andreas.cameradatefolders.ui.dashboard.DashboardFragment;

// https://stackoverflow.com/questions/63548323/how-to-use-viewmodel-in-a-fragment
// https://stackoverflow.com/questions/6091194/how-to-handle-button-clicks-using-the-xml-onclick-within-fragments
// activityViewModel ???

@SuppressWarnings("Convert2Lambda")
public class MainActivity extends AppCompatActivity
{
    private static final String LOG_TAG = "CDF : MainActivity";
    private ActivityMainBinding binding;
    ActivityResultLauncher<Intent> mRequestDirectorySelectActivityLauncher;
    Uri mDcimTreeUri = null;


    /**************************************************************************
     *
     * Activity method
     *
     *************************************************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        registerDirectorySelectCallback();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
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
     * onClick callback
     *
     *************************************************************************/
    public void onClickButtonStart(View view)
    {
        if (mDcimTreeUri != null)
        {
            Utils utils = new Utils(this);
            utils.gatherFiles(mDcimTreeUri);
        }
    }


    /**************************************************************************
     *
     * onClick callback
     *
     *************************************************************************/
    public void onClickButtonRevert(View view)
    {
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
     * call DashboardFragment() due to configuration change
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
            if (f instanceof DashboardFragment)
            {
                DashboardFragment fd = (DashboardFragment) f;
                fd.onPathChanged(mDcimTreeUri.toString());
            }
        }
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
                                onDcimPathChanged();
                            }
                        }
                    }
                });
    }
}