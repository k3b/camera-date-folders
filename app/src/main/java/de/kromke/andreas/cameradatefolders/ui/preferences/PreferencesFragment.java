package de.kromke.andreas.cameradatefolders.ui.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
//import androidx.lifecycle.ViewModelProvider;
import de.kromke.andreas.cameradatefolders.R;
import de.kromke.andreas.cameradatefolders.databinding.FragmentPreferencesBinding;

@SuppressWarnings("Convert2Lambda")
public class PreferencesFragment extends Fragment
{
    public static final String PREF_FOLDER_SCHEME = "prefFolderScheme";
    public static final String PREF_BACKUP_COPY = "prefBackupCopy";
    public static final String PREF_DRY_RUN = "prefDryRun";
    public static final String PREF_FORCE_FILE_MODE = "prefForceFileMode";

    private static final String LOG_TAG = "CDF : PF";
    //private PreferencesViewModel viewModel;
    private FragmentPreferencesBinding binding;

    // convert radio button id to scheme in text form
    private static String schemeId2Val(int id)
    {
        switch (id)
        {
            case R.id.button_scheme_y_m_d:
                return "ymd";
            case R.id.button_scheme_m_d:
                return "md";
            case R.id.button_scheme_y_d:
                return "yd";
            case R.id.button_scheme_y_m:
                return "ym";
            case R.id.button_scheme_d:
                return "d";
            case R.id.button_scheme_m:
                return "m";
            case R.id.button_scheme_y:
                return "y";
        }
        return "ymd";
    }

    // convert scheme in text form to radio button id
    private static int schemeVal2Id(final String val)
    {
        switch (val)
        {
            case "ymd":
                return R.id.button_scheme_y_m_d;
            case "md":
                return R.id.button_scheme_m_d;
            case "yd":
                return R.id.button_scheme_y_d;
            case "ym":
                return R.id.button_scheme_y_m;
            case "d":
                return R.id.button_scheme_d;
            case "m":
                return R.id.button_scheme_m;
            case "y":
                return R.id.button_scheme_y;
        }
        return R.id.button_scheme_y_m_d;
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        //viewModel = new ViewModelProvider(this).get(PreferencesViewModel.class);

        binding = FragmentPreferencesBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        //
        // folder scheme
        //

        final RadioGroup folderScheme = binding.schemeRadioGroup;
        String val = prefs.getString(PREF_FOLDER_SCHEME, "ymd");
        folderScheme.check(schemeVal2Id(val));
        folderScheme.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(RadioGroup view, int checkedId)
                {
                    int id = view.getCheckedRadioButtonId();
                    Log.d(LOG_TAG, "checked Button id = " + id);
                    final String val = schemeId2Val(id);
                    SharedPreferences.Editor prefEditor = prefs.edit();
                    prefEditor.putString(PREF_FOLDER_SCHEME, val);
                    prefEditor.apply();
                }
            });

        //
        // backup copy switch
        //

        final SwitchCompat swBackupCopy = binding.switchBackupCopy;
        boolean bState = prefs.getBoolean(PREF_BACKUP_COPY, false);
        swBackupCopy.setChecked(bState);
        swBackupCopy.setOnCheckedChangeListener(new SwitchCompat.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton view, boolean b)
            {
                Log.d(LOG_TAG, "Backup Copy switch = " + b);
                SharedPreferences.Editor prefEditor = prefs.edit();
                prefEditor.putBoolean(PREF_BACKUP_COPY, b);
                prefEditor.apply();
            }
        });

        //
        // dry run switch
        //

        final SwitchCompat swDryRun = binding.switchDryRun;
        bState = prefs.getBoolean(PREF_DRY_RUN, false);
        swDryRun.setChecked(bState);
        swDryRun.setOnCheckedChangeListener(new SwitchCompat.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton view, boolean b)
            {
                Log.d(LOG_TAG, "Dry Run switch = " + b);
                SharedPreferences.Editor prefEditor = prefs.edit();
                prefEditor.putBoolean(PREF_DRY_RUN, b);
                prefEditor.apply();
            }
        });

        //
        // force file mode switch
        //

        final SwitchCompat swForceFileMode = binding.switchForceFileMode;
        bState = prefs.getBoolean(PREF_FORCE_FILE_MODE, false);
        swForceFileMode.setChecked(bState);
        swForceFileMode.setOnCheckedChangeListener(new SwitchCompat.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton view, boolean b)
            {
                Log.d(LOG_TAG, "Force File Mode switch = " + b);
                SharedPreferences.Editor prefEditor = prefs.edit();
                prefEditor.putBoolean(PREF_FORCE_FILE_MODE, b);
                prefEditor.apply();
            }
        });
        /*
        preferencesViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(@Nullable String s)
            {
                textView.setText(s);
            }
        });
        */
        return root;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        binding = null;
    }
}