package com.blackmoonit.android.FileBrowser;

import java.io.File;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.blackmoonit.filesystem.MIMEtypeMap;

/**
 * Enable other apps to query Blackmoon for MIME types we've defined.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class MIMETypeProvider extends ContentProvider {
	/**
	 * The authority this provider runs under.
	 */
	public static final String AUTHORITY = "com.blackmoonit.FileBrowser.mimeType";	
    /**
     * The content:// scheme Uri for this provider
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    /**
     * The MIME type of a {@link #CONTENT_URI} result.
     */
    public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE+"/text";
	
    private MIMEtypeMap mMimeMap = null;
    
	@Override
	public boolean onCreate() {
		mMimeMap = new MIMEtypeMap().createMaps();
		return true;
	}
	
	@Override
	public String getType(Uri uri) {
		return CONTENT_ITEM_TYPE;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		MatrixCursor theResult = null;
		String thePath = (uri!=null)?uri.getPath():null;
		if (thePath!=null) {
			File theFile = new File(thePath);
			String theGuess = mMimeMap.guessMIMEtype(theFile);
			theResult = new MatrixCursor(new String[] {"MIMEtype"}, 1);
			theResult.newRow().add(theGuess);
		}
		return theResult;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

}
