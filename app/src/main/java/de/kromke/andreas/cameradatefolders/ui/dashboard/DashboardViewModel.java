package de.kromke.andreas.cameradatefolders.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class DashboardViewModel extends ViewModel
{
    private MutableLiveData<String> mText;

    public DashboardViewModel()
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
        mText.setValue(text);
    }
}
