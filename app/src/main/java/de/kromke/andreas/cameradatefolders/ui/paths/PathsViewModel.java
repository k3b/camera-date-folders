package de.kromke.andreas.cameradatefolders.ui.paths;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.io.File;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.kromke.andreas.cameradatefolders.StatusAndPrefs;
import de.kromke.andreas.cameradatefolders.UriToPath;

public class PathsViewModel extends ViewModel
{
    private final MutableLiveData<String> mText;

    public PathsViewModel()
    {
        mText = new MutableLiveData<>();
        mText.setValue("");
    }

    private String getPathWithExplanationFromUriString(Context context, String uriString, boolean bWrite)
    {
        Uri uri = Uri.parse(uriString);
        String text = UriToPath.getPathFromUri(context, uri);
        if (text != null)
        {
            File f = new File(text);
            if (f.isDirectory())
            {
                if (f.canRead())
                {
                    if (bWrite && !f.canWrite())
                    {
                        text += "\n(NOT WRITEABLE!)";
                    }
                }
                else
                {
                    text += "\n(NOT READABLE!)";
                }
            }
            else
            {
                text += "\n(NOT A DIRECTORY!)";
            }
        }
        else
        {
            text = uri.getPath() + "\n(INVALID FOR FILE MODE!)";
        }
        return text;
    }

    private String getRawText(Context context)
    {
        String text;
        if (StatusAndPrefs.mCamFolder == null)
        {
            text = "Press green button to select camera folder (usually \"/DCIM/Camera\" in internal memory)!";
        }
        else
        {
            String text2 = null;
            boolean bFileMode = StatusAndPrefs.mbForceFileMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N);
            if (!bFileMode)
            {
                text = StatusAndPrefs.mCamFolder;
                text2 = StatusAndPrefs.mDestFolder;
            }
            else
            {
                if (StatusAndPrefs.mDestFolder != null)
                {
                    text = getPathWithExplanationFromUriString(context, StatusAndPrefs.mCamFolder, false);
                    text2 = getPathWithExplanationFromUriString(context, StatusAndPrefs.mDestFolder, true);
                }
                else
                {
                    text = getPathWithExplanationFromUriString(context, StatusAndPrefs.mCamFolder, true);
                }
            }

            if (text2 != null)
            {
                text +=  "\n\n==>\n\n" + text2;
            }

            if (text != null)
            {
                text = text.replace("%3A", ":").replace("%2F", "/");
            }
        }

        return text;
    }

    public LiveData<String> getText(Context context)
    {
        mText.setValue(getRawText(context));
        return mText;
    }

    public void setText(Context context)
    {
        mText.setValue(getRawText(context));
    }
}
