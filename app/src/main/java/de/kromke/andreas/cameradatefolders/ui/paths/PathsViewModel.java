package de.kromke.andreas.cameradatefolders.ui.paths;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PathsViewModel extends ViewModel
{
    private final MutableLiveData<String> mText;

    public PathsViewModel()
    {
        mText = new MutableLiveData<>();
        mText.setValue("Press green button to select folder.");
    }

    public LiveData<String> getText()
    {
        return mText;
    }

    public void setText(final String text)
    {
        String readable_text = text.replace("%3A", ":");
        mText.setValue(readable_text);
    }
}
