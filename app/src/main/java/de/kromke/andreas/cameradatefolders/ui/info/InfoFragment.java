package de.kromke.andreas.cameradatefolders.ui.info;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import de.kromke.andreas.cameradatefolders.Utils;
import de.kromke.andreas.cameradatefolders.databinding.FragmentInfoBinding;

public class InfoFragment extends Fragment
{
    private FragmentInfoBinding binding;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentInfoBinding.inflate(inflater, container, false);

        Context context = getContext();
        if (context != null)
        {
            Utils.AppVersionInfo info = Utils.getVersionInfo(context);
            final String strVersion = "Version " + info.versionName + ((info.isDebug) ? " DEBUG" : "");
            final TextView versionInfo = binding.versionInfo;
            versionInfo.setText(strVersion);
        }

        return binding.getRoot();
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        binding = null;
    }
}
