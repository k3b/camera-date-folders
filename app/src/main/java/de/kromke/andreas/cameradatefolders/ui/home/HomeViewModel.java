package de.kromke.andreas.cameradatefolders.ui.home;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.kromke.andreas.cameradatefolders.R;
import de.kromke.andreas.cameradatefolders.StatusAndPrefs;


public class HomeViewModel extends ViewModel
{
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
        mRevertButtonText.setValue("REVERT");
    }

    public LiveData<String> getText(Context context)
    {
        String text;
        if (StatusAndPrefs.mCamFolder == null)
        {
            text = context.getString(R.string.str_no_cam_path);
        }
        else
        {
            if (StatusAndPrefs.mbBackupCopy && (StatusAndPrefs.mDestFolder == null))
            {
                text = context.getString(R.string.str_no_dest_path);
            }
            else
            {
                text = context.getString(StatusAndPrefs.mbBackupCopy ? R.string.str_press_backup : R.string.str_press_start);
                if (StatusAndPrefs.mbDryRun)
                {
                    text += "\n\nAttention: dry run mode!";
                }
            }
        }

        mText.setValue(text);
        return mText;
    }

    private String getStartButtonRawText()
    {
        if (StatusAndPrefs.bSortRunning)
        {
            return "STOP";
        }
        else
        {
            return (StatusAndPrefs.mbBackupCopy) ? "BACKUP" : "SORT";
        }
    }

    private String getRevertButtonRawText()
    {
        if (StatusAndPrefs.bRevertRunning)
        {
            return "STOP";
        }
        else
        {
            return (StatusAndPrefs.mbBackupCopy) ? "FLATTEN" : "REVERT";
        }
    }

    public LiveData<String> getStartButtonText()
    {
        mStartButtonText.setValue(getStartButtonRawText());
        return mStartButtonText;
    }

    public LiveData<String> getRevertButtonText()
    {
        mRevertButtonText.setValue(getRevertButtonRawText());
        return mRevertButtonText;
    }

    public void setText(final String text)
    {
        mText.setValue(text);
    }

    public void setButtonText()
    {
        mStartButtonText.setValue(getStartButtonRawText());
        mRevertButtonText.setValue(getRevertButtonRawText());
    }
}
