package com.github.axet.bookreader.services;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.axet.androidlibrary.app.AssetsDexLoader;
import com.github.axet.androidlibrary.services.FileProvider;
import com.github.axet.androidlibrary.services.StorageProvider;

import org.geometerplus.zlibrary.core.image.ZLFileImage;
import org.geometerplus.zlibrary.core.image.ZLImageData;
import org.geometerplus.zlibrary.core.image.ZLImageManager;
import org.geometerplus.zlibrary.ui.android.image.ZLAndroidImageData;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

public class ImagesProvider extends StorageProvider {
    public static String TAG = ImagesProvider.class.getSimpleName();

    public static final String EXT = "png";

    public static ImagesProvider getProvider() {
        return (ImagesProvider) infos.get(ImagesProvider.class);
    }

    public static long getImageSize(ZLFileImage image) {
        try {
            int[] aa = (int[]) AssetsDexLoader.getPrivateField(image.getClass(), "myLengths").get(image);
            int c = 0;
            for (int a : aa)
                c += a;
            return c;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Uri f = find(uri);
        if (f == null)
            return null;
        String s = f.getScheme();
        if (s.equals(ZLFileImage.SCHEME)) {
            final ZLFileImage image = ZLFileImage.byUrlPath(f.getPath());

            if (projection == null)
                projection = FileProvider.COLUMNS;

            final MatrixCursor cursor = new MatrixCursor(projection, 1);

            String[] cols = new String[projection.length];
            Object[] values = new Object[projection.length];

            int i = 0;
            for (String col : projection) {
                if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                    cols[i] = OpenableColumns.DISPLAY_NAME;
                    values[i++] = uri.getLastPathSegment(); // contains original name
                } else if (OpenableColumns.SIZE.equals(col)) {
                    cols[i] = OpenableColumns.SIZE;
                    values[i++] = getImageSize(image);
                }
            }

            values = FileProvider.copyOf(values, i);

            cursor.addRow(values);
            return cursor;
        } else {
            return super.query(uri, projection, selection, selectionArgs, sortOrder);
        }
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Uri f = find(uri);
        if (f == null)
            return null;

        freeUris();

        final ZLFileImage image = ZLFileImage.byUrlPath(f.getPath());
        if (image == null)
            throw new FileNotFoundException();

        try {
            final ZLImageData imageData = ZLImageManager.Instance().getImageData(image);
            final Bitmap bm = ((ZLAndroidImageData) imageData).getFullSizeBitmap();
            return openInputStream(new InputStreamWriter() {
                @Override
                public void copy(OutputStream os) throws IOException {
                    try {
                        bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                        bm.recycle();
                    } catch (Throwable e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public void close() throws IOException {
                }
            }, mode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        return new AssetFileDescriptor(openFile(uri, mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }
}
