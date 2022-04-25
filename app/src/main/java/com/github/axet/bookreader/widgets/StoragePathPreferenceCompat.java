package com.github.axet.bookreader.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.OpenStorageChoicer;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.app.Storage;

import java.io.File;

public class StoragePathPreferenceCompat extends com.github.axet.androidlibrary.preferences.StoragePathPreferenceCompat {
    CharSequence defSummary;

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StoragePathPreferenceCompat(Context context) {
        super(context);
    }

    public void create() {
        defSummary = getSummary();
        choicer = new OpenStorageChoicer(storage, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
            Uri reset;

            @Override
            public void onResult(Uri uri) {
                if (uri.equals(reset)) {
                    SharedPreferences.Editor editor = getSharedPreferences().edit();
                    editor.remove(getKey());
                    editor.apply();
                    setSummary(defSummary);
                } else {
                    if (callChangeListener(uri.toString())) {
                        setText(uri.toString());
                    }
                }
            }

            @Override
            public OpenFileDialog fileDialogBuild() {
                final OpenFileDialog d = super.fileDialogBuild();

                d.setNeutralButton(R.string.default_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File path = storage.getLocalStorage();
                        d.setCurrentPath(path);
                        reset = Uri.fromFile(path);
                        Toast.makeText(context, path.toString(), Toast.LENGTH_SHORT).show();
                    }
                });

                return d;
            }
        };
        choicer.setTitle(getTitle().toString());
        choicer.setContext(getContext());
    }

    @Override
    public void onClick() {
        super.onClick();
    }

    @Override
    public void onSetInitialValue(boolean restoreValue, Object defaultValue) { // allow to show null
        String v = restoreValue ? getPersistedString(getText()) : (String) defaultValue;
        Uri u = storage.getStoragePath(v);
        if (u != null) {
            setText(u.toString());
            setSummary(Storage.getDisplayName(getContext(), u));
        }
    }

    @Override
    public Object onGetDefaultValue(TypedArray a, int index) {
        super.onGetDefaultValue(a, index);
        return null; // no default for books reader
    }
}
