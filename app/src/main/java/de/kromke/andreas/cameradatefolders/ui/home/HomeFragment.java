package de.kromke.andreas.cameradatefolders.ui.home;

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
import de.kromke.andreas.cameradatefolders.StatusAndPrefs;
import de.kromke.andreas.cameradatefolders.databinding.FragmentHomeBinding;

@SuppressWarnings("Convert2Lambda")
public class HomeFragment extends Fragment
{
    private HomeViewModel viewModel;
    private FragmentHomeBinding binding;
    private TextView mScanTextView;
    private int mButtonStartPaddingLeft;
    private int mButtonStartPaddingTop;
    private int mButtonStartPaddingRight;
    private int mButtonStartPaddingBottom;

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
        // hack to get original padding that we can later change for larger texts
        mButtonStartPaddingLeft = buttonStartView.getPaddingLeft();
        mButtonStartPaddingTop = buttonStartView.getPaddingTop();
        mButtonStartPaddingRight = buttonStartView.getPaddingRight();
        mButtonStartPaddingBottom = buttonStartView.getPaddingBottom();
        if (StatusAndPrefs.bRevertRunning)
        {
            buttonStartView.setEnabled(false);
        }
        viewModel.getStartButtonText().observe(getViewLifecycleOwner(), new Observer<String>()
        {
            @Override
            public void onChanged(@Nullable String s)
            {
                if (s != null)
                {
                    int padLeft = mButtonStartPaddingLeft;
                    int padRight = mButtonStartPaddingRight;
                    if (s.length() > 5)
                    {
                        // smaller padding for larger text
                        padLeft = (padLeft * 2) / 3;
                        padRight = (padRight * 2) / 3;
                    }
                    buttonStartView.setPadding(padLeft, mButtonStartPaddingTop, padRight, mButtonStartPaddingBottom);
                }
                buttonStartView.setText(s);
            }
        });

        final Button buttonRevertView = binding.buttonRevert;
        if (StatusAndPrefs.bSortRunning)
        {
            buttonRevertView.setEnabled(false);
        }
        viewModel.getRevertButtonText().observe(getViewLifecycleOwner(), new Observer<String>()
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

    public void updateButtons()
    {
        final Button buttonStartView = binding.buttonStart;
        buttonStartView.setEnabled(!StatusAndPrefs.bRevertRunning);

        final Button buttonRevert = binding.buttonRevert;
        buttonRevert.setEnabled(!StatusAndPrefs.bSortRunning);

        viewModel.setButtonText();
    }

    // change text and afterwards scroll to end position
    public void onTextChanged(String text)
    {
        viewModel.setText(text);
        final int scrollAmount = mScanTextView.getLayout().getLineTop(mScanTextView.getLineCount()) - mScanTextView.getHeight();
        mScanTextView.scrollTo(0, Math.max(scrollAmount, 0));
    }
}