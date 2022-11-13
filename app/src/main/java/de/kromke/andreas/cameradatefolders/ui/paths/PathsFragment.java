package de.kromke.andreas.cameradatefolders.ui.paths;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import de.kromke.andreas.cameradatefolders.StatusAndPrefs;
import de.kromke.andreas.cameradatefolders.databinding.FragmentPathsBinding;


@SuppressWarnings("Convert2Lambda")
public class PathsFragment extends Fragment
{
    private PathsViewModel viewModel;
    private FragmentPathsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        viewModel = new ViewModelProvider(this).get(PathsViewModel.class);

        binding = FragmentPathsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        boolean fileMode = (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) || StatusAndPrefs.mbForceFileMode;

        final TextView textView = binding.textPaths;
        String val = StatusAndPrefs.mCamFolder;
        String val2 = StatusAndPrefs.mDestFolder;
        if (fileMode)
        {
            if (val != null)
            {
                val = Uri.parse(val).getPath();
            }
            if (val2 != null)
            {
                val2 = Uri.parse(val2).getPath();
            }
        }
        if (val == null)
        {
            val = "(unset)";
        }

        viewModel.setText(val, val2);
        viewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(@Nullable String s)
            {
                textView.setText(s);
            }
        });
        return root;
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        binding = null;
    }

    public void onPathChanged(String uriDcim, String uriDest)
    {
        viewModel.setText(uriDcim, uriDest);
    }

    public void cbSelectCameraFolder(View v)
    {
    }

    public void cbDestFolder(View view)
    {
    }
}