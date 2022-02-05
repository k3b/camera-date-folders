package de.kromke.andreas.cameradatefolders.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PreferencesViewModel extends ViewModel
{

    private MutableLiveData<String> mText;

    public PreferencesViewModel()
    {
        mText = new MutableLiveData<>();
        mText.setValue("This is dashboard fragment");
    }

    public LiveData<String> getText()
    {
        return mText;
    }
}