package de.kromke.andreas.cameradatefolders.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.kromke.andreas.cameradatefolders.R;
import de.kromke.andreas.cameradatefolders.ui.paths.PathsFragment;

import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_BACKUP_COPY;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_DRY_RUN;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_FOLDER_SCHEME;
import static de.kromke.andreas.cameradatefolders.ui.preferences.PreferencesFragment.PREF_FORCE_FILE_MODE;

public class HomeViewModel extends ViewModel
{
    public static boolean bSortRunning = false;
    public static boolean bRevertRunning = false;
    private MutableLiveData<String> mText;
    private final MutableLiveData<String> mStartButtonText;
    private final MutableLiveData<String> mRevertButtonText;

    public HomeViewModel()
    {
        mText = new MutableLiveData<>();
        mText.setValue("---- ---- ----");

        mStartButtonText = new MutableLiveData<>();
        mStartButtonText.setValue("SORT");

        mRevertButtonText = new MutableLiveData<>();
        mRevertButtonText.setValue("SORT");
    }

    public LiveData<String> getText(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String camFolder = prefs.getString(PathsFragment.PREF_CAM_FOLDER_URI, null);
        if (camFolder == null)
        {
            mText.setValue(context.getString(R.string.str_no_cam_path));
        }
        else
        {
            String destFolder = prefs.getString(PathsFragment.PREF_DEST_FOLDER_URI, null);
            boolean bBackupCopy = prefs.getBoolean(PREF_BACKUP_COPY, false);
            if (bBackupCopy && (destFolder == null))
            {
                mText.setValue(context.getString(R.string.str_no_dest_path));
            }
            else
            {
                mText.setValue(context.getString(bBackupCopy ? R.string.str_press_backup : R.string.str_press_start));
            }
        }
        return mText;
    }

    private String getStartButtonRawText(Context context)
    {
        if (bSortRunning)
        {
            return "STOP";
        }
        else
        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            //boolean forceFileMode = prefs.getBoolean(PREF_FORCE_FILE_MODE, false);
            //mbFileMode = forceFileMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N);
            //mbDryRun = prefs.getBoolean(PREF_DRY_RUN, false);
            boolean bBackupCopy = prefs.getBoolean(PREF_BACKUP_COPY, false);
            //mFolderScheme = prefs.getString(PREF_FOLDER_SCHEME, "ymd");

            return (bBackupCopy) ? "BACKUP" : "SORT";
        }
    }

    private String getRevertButtonRawText(Context context)
    {
        if (bRevertRunning)
        {
            return "STOP";
        }
        else
        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            //boolean forceFileMode = prefs.getBoolean(PREF_FORCE_FILE_MODE, false);
            //mbFileMode = forceFileMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N);
            //mbDryRun = prefs.getBoolean(PREF_DRY_RUN, false);
            boolean bBackupCopy = prefs.getBoolean(PREF_BACKUP_COPY, false);
            //mFolderScheme = prefs.getString(PREF_FOLDER_SCHEME, "ymd");

            return (bBackupCopy) ? "FLATTEN" : "REVERT";
        }
    }

    public LiveData<String> getStartButtonText(Context context)
    {
        mStartButtonText.setValue(getStartButtonRawText(context));
        return mStartButtonText;
    }

    public LiveData<String> getRevertButtonText(Context context)
    {
        mRevertButtonText.setValue(getRevertButtonRawText(context));
        return mRevertButtonText;
    }

    public void setText(final String text)
    {
        mText.setValue(text);
    }

    public void setButtonText(Context context)
    {
        mStartButtonText.setValue(getStartButtonRawText(context));
        mRevertButtonText.setValue(getRevertButtonRawText(context));
    }
}
