package de.kromke.andreas.cameradatefolders.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import de.kromke.andreas.cameradatefolders.R;
import de.kromke.andreas.cameradatefolders.databinding.FragmentHomeBinding;

@SuppressWarnings("Convert2Lambda")
public class HomeFragment extends Fragment
{
    private HomeViewModel viewModel;
    private FragmentHomeBinding binding;
    private TextView mScanTextView;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        viewModel.getText(getActivity()).observe(getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(@Nullable String s)
            {
                textView.setText(s);
            }
        });

        final Button buttonStartView = binding.buttonStart;
        if (HomeViewModel.bRevertRunning)
        {
            buttonStartView.setEnabled(false);
        }
        viewModel.getStartButtonText(getActivity()).observe(getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(@Nullable String s)
            {
                buttonStartView.setText(s);
            }
        });

        final Button buttonRevertView = binding.buttonRevert;
        if (HomeViewModel.bSortRunning)
        {
            buttonRevertView.setEnabled(false);
        }
        viewModel.getRevertButtonText(getActivity()).observe(getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(@Nullable String s)
            {
                buttonRevertView.setText(s);
            }
        });

        return root;
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        mScanTextView = view.findViewById(R.id.text_home);
        mScanTextView.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        binding = null;
    }

    public void updateButtons(Context context)
    {
        final Button buttonStartView = binding.buttonStart;
        buttonStartView.setEnabled(!HomeViewModel.bRevertRunning);

        final Button buttonRevert = binding.buttonRevert;
        buttonRevert.setEnabled(!HomeViewModel.bSortRunning);

        viewModel.setButtonText(context);
    }

    // change text and afterwards scroll to end position
    public void onTextChanged(String text)
    {
        viewModel.setText(text);
        final int scrollAmount = mScanTextView.getLayout().getLineTop(mScanTextView.getLineCount()) - mScanTextView.getHeight();
        mScanTextView.scrollTo(0, Math.max(scrollAmount, 0));
    }

    /*
    public void onStartButtonTextChanged(String text)
    {
        viewModel.setStartButtonText(text);
    }
    */
}