package de.kromke.andreas.cameradatefolders.ui.preferences;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.kromke.andreas.cameradatefolders.databinding.FragmentPreferencesBinding;

public class PreferencesFragment extends Fragment
{

    private PreferencesViewModel preferencesViewModel;
    private FragmentPreferencesBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        preferencesViewModel =
                new ViewModelProvider(this).get(PreferencesViewModel.class);

        binding = FragmentPreferencesBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        /*
        final TextView textView = binding.textNotifications;
        notificationsViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>()
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