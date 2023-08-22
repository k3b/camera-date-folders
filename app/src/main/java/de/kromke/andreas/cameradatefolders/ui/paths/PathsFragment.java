package de.kromke.andreas.cameradatefolders.ui.paths;

import android.content.Context;
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

        final TextView textView = binding.textPaths;
        viewModel.getText(getActivity()).observe(getViewLifecycleOwner(), new Observer<String>()
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

    public void onPathChanged(Context context)
    {
        viewModel.setText(context);
    }
}