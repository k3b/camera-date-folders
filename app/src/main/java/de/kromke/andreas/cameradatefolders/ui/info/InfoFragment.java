package de.kromke.andreas.cameradatefolders.ui.info;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import de.kromke.andreas.cameradatefolders.databinding.FragmentInfoBinding;

public class InfoFragment extends Fragment
{
    private FragmentInfoBinding binding;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentInfoBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    /*
    private void DialogAbout()
    {
        UserSettings.AppVersionInfo info = UserSettings.getVersionInfo(this);

        final String strTitle = getString(R.string.app_name);
        final String strDescription = getString(R.string.str_app_description);
        final String strAuthor = getString(R.string.str_author);
        final String strVersion = "Version " + info.versionName + ((info.isDebug) ? " DEBUG" : "");

        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(strTitle);
        alertDialog.setIcon(R.drawable.app_icon_noborder);
        alertDialog.setMessage(
                strDescription + "\n\n" +
                        strAuthor + "Andreas Kromke" + "\n\n" +
                        strVersion + "\n" +
                        "(" + info.strCreationTime + ")");
        alertDialog.setCancelable(true);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }
    */

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        binding = null;
    }
}
