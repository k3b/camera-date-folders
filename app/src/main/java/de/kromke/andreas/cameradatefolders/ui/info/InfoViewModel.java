package de.kromke.andreas.cameradatefolders.ui.info;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class InfoViewModel extends ViewModel
{

    private MutableLiveData<String> mText;

    public InfoViewModel()
    {
        mText = new MutableLiveData<>();
        mText.setValue("This is notifications fragment");
    }

    public LiveData<String> getText()
    {
        return mText;
    }
}