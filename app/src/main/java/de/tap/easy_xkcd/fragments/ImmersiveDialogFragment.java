package de.tap.easy_xkcd.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import timber.log.Timber;

// Adapted from https://stackoverflow.com/a/28016335/5136129
public class ImmersiveDialogFragment extends DialogFragment {
    public static ImmersiveDialogFragment getInstance(String text) {
        Bundle args = new Bundle();
        args.putString("text", text);
        ImmersiveDialogFragment immersiveDialogFragment = new ImmersiveDialogFragment();
        immersiveDialogFragment.setArguments(args);
        return immersiveDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setMessage(getArguments().getString("text"))
                .create();

        // Temporarily set the dialogs window to not focusable to prevent the short
        // popup of the navigation bar.
        if (savedInstanceState == null) {
            alertDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }

        return alertDialog;

    }



    public void showImmersive(AppCompatActivity activity) {

        // Show the dialog.
        show(activity.getSupportFragmentManager(), "");

        // It is necessary to call executePendingTransactions() on the FragmentManager
        // before hiding the navigation bar, because otherwise getWindow() would raise a
        // NullPointerException since the window was not yet created.
        getFragmentManager().executePendingTransactions();

        // Hide the navigation bar. It is important to do this after show() was called.
        // If we would do this in onCreateDialog(), we would get a requestFeature()
        // error.
        getDialog().getWindow().getDecorView().setSystemUiVisibility(
                getActivity().getWindow().getDecorView().getSystemUiVisibility()
        );

        // Make the dialogs window focusable again.
        getDialog().getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        );

    }

}

