package dev.scriptable.android;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, single-file install grant; never exposes script storage. MIT. */
public final class UpdateFileProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private void validate(Uri uri)throws FileNotFoundException{if(!AppUpdater.installUri(getContext()).equals(uri))throw new FileNotFoundException("Unknown update path");}
    @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{validate(uri);if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(AppUpdater.readyFile(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{validate(uri);}catch(FileNotFoundException e){throw new IllegalArgumentException(e);}
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor result=new MatrixCursor(columns);Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++)if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=UpdateRelease.ASSET;else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=AppUpdater.readyFile(getContext()).length();
        result.addRow(row);return result;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}
