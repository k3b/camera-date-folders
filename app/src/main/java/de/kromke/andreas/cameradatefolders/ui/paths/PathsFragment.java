package de.kromke.andreas.cameradatefolders.ui.paths;

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
import de.kromke.andreas.cameradatefolders.databinding.FragmentPathsBinding;

public class PathsFragment extends Fragment
{
    private PathsViewModel pathsViewModel;
    private FragmentPathsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        pathsViewModel =
                new ViewModelProvider(this).get(PathsViewModel.class);

        binding = FragmentPathsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textPaths;
        pathsViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>()
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

    public void onPathChanged(String uri)
    {
        pathsViewModel.setText(uri);
    }

    public void cbSelectCameraFolder(View v)
    {

    }
}