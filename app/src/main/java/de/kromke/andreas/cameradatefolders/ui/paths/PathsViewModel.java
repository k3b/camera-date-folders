package de.kromke.andreas.cameradatefolders.ui.paths;

import android.net.Uri;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.kromke.andreas.cameradatefolders.StatusAndPrefs;

public class PathsViewModel extends ViewModel
{
    private final MutableLiveData<String> mText;

    public PathsViewModel()
    {
        mText = new MutableLiveData<>();
        mText.setValue("");
    }

    private String getRawText()
    {
        String text;
        if (StatusAndPrefs.mCamFolder == null)
        {
            text = "Press green button to select camera folder (usually \"/DCIM/Camera\" in internal memory)!";
        }
        else
        {
            Uri camUri = Uri.parse(StatusAndPrefs.mCamFolder);

            boolean bFileMode = StatusAndPrefs.mbForceFileMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N);
            text = (bFileMode) ? camUri.getPath() : camUri.toString();
            if (StatusAndPrefs.mDestFolder != null)
            {
                Uri destUri = Uri.parse(StatusAndPrefs.mDestFolder);
                text +=  "\n\n==>\n\n" + ((bFileMode) ? destUri.getPath() : destUri.toString());
            }

            text = text.replace("%3A", ":").replace("%2F", "/");
        }

        return text;
    }

    public LiveData<String> getText()
    {

        mText.setValue(getRawText());
        return mText;
    }

    public void setText()
    {
        mText.setValue(getRawText());
    }
}
