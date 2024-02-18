package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.LiveFolders;

import com.blackmoonit.database.TypedMatrixCursor;
import com.blackmoonit.filesystem.BitsFileUtils;
import com.blackmoonit.filesystem.MIMEtypeMap;
import com.blackmoonit.lib.BitsStringUtils;
import com.blackmoonit.media.BitsThumbnailUtils;

/**
 * Live Folders need access to jump points via a ContentProvider
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class JumpPointProvider extends ContentProvider {
	@SuppressWarnings("unused")
	private static final String TAG = "BITS.FileBrowser.JumpPointProvider";
	
	/**
	 * The authority this provider runs under.
	 */
	public static final String AUTHORITY = "com.blackmoonit.FileBrowser.JumpPoint";	
    /**
     * The content:// scheme Uri for this provider.
     */
	public static final Uri CONTENT_URI = Uri.parse("content://"+AUTHORITY+"/jumppoint");
	public static final Uri LIVE_FOLDER_URI = CONTENT_URI;
    /**
     * The type of a {@link #CONTENT_URI} result.
     */
    public static final String CONTENT_TYPE = "vnd.blackmoonit.file.jumppoint";

	// UriMatcher stuff
    private static final int MATCH_CODE_ALL_JUMP_POINTS = 0;
    private static final int MATCH_CODE_JUMP_POINT = 1;
    private static final UriMatcher URI_MATCHER = buildUriMatcher();

	/**
	 * Builds up a UriMatcher.
	 */
	private static UriMatcher buildUriMatcher() {
		UriMatcher matcher =  new UriMatcher(UriMatcher.NO_MATCH);
		matcher.addURI(AUTHORITY,"jumppoint",MATCH_CODE_ALL_JUMP_POINTS);
		matcher.addURI(AUTHORITY,"jumppoint/#",MATCH_CODE_JUMP_POINT);
		return matcher;
	}
	
	public static final String JUMP_ID = BaseColumns._ID;
	public static final String JUMP_NAME = LiveFolders.NAME;
	public static final String JUMP_DATA = "data"; //file.getPath()
	public static final String JUMP_URI = LiveFolders.INTENT;
	public static final String JUMP_ICON = LiveFolders.ICON_BITMAP;

	//Set of columns needed by a LiveFolder
    private static final String[] CURSOR_COLUMNS = new String[] {
    	JUMP_ID, 
    	JUMP_NAME, 
    	JUMP_DATA,
    	JUMP_URI, 
    	LiveFolders.ICON_BITMAP,
    	LiveFolders.ICON_PACKAGE, 
    	LiveFolders.ICON_RESOURCE
    };
    
    private static final int[] CURSOR_COLUMN_TYPES = new int[] {
    	TypedMatrixCursor.COLUMN_TYPE_INTEGER, 
    	TypedMatrixCursor.COLUMN_TYPE_STRING, 
    	TypedMatrixCursor.COLUMN_TYPE_STRING,
    	TypedMatrixCursor.COLUMN_TYPE_STRING,
    	TypedMatrixCursor.COLUMN_TYPE_BLOB,
    	TypedMatrixCursor.COLUMN_TYPE_STRING,
    	TypedMatrixCursor.COLUMN_TYPE_INTEGER, 
    };
    
	private MIMEtypeMap mMimeMap = null;
	private boolean mShowThumbnails = true;
	private SharedPreferences mSettings = null;
	private JumpPointDatabase mJumpPointsDb = null;

	@Override
	public boolean onCreate() {
    	mMimeMap = new MIMEtypeMap().createMaps();
    	mSettings = AppPreferences.getPrefs(getContext());
    	mJumpPointsDb = new JumpPointDatabase(getContext(),mSettings);
		return true;
	}
	
	public int[] getColumnTypes(String[] aProjection) {
		int[] theResult = new int[aProjection.length];
		for (int i=0; i<aProjection.length; i++) {
			for (int j=0; j<CURSOR_COLUMNS.length; j++) {
				if (CURSOR_COLUMNS[j].equals(aProjection[i])) {
					theResult[i] = CURSOR_COLUMN_TYPES[j];
				}
			}
		}
		return theResult;
	}
	
	/**
	 * Convenience function to get the rowId encoded in our Uri.
	 * @param aUri - Uri param that matches {@link #MATCH_CODE_JUMP_POINT}.
	 * @return returns the rowId.
	 */
	private int getRowId(Uri aUri) {
		try {
			return Integer.valueOf(aUri.getLastPathSegment());
		} catch (NumberFormatException e) {
			//should never occur since our Uri matcher guarantees this will be a number.
			throw new IllegalArgumentException("_ID must be an integer: " + aUri);
		}
	}

	@Override
	public Cursor query(Uri aUri, String[] aProjection, String aSelection,
			String[] aSelectionArgs, String aSortOrder) {
		mShowThumbnails = mSettings.getBoolean(
				getContext().getString(R.string.pref_key_show_thumbnails),true);
		int[] theProjectionTypes;
		if (aProjection==null || aProjection.length==0) {
			aProjection = CURSOR_COLUMNS;
			theProjectionTypes = CURSOR_COLUMN_TYPES;
		} else {
			theProjectionTypes = getColumnTypes(aProjection);
		}
		TypedMatrixCursor theResult;
		// Use the UriMatcher to see what kind of query we have and format the db query accordingly
		switch (URI_MATCHER.match(aUri)) {
			case MATCH_CODE_ALL_JUMP_POINTS:
				theResult = new TypedMatrixCursor(aProjection,theProjectionTypes);
				//notification Uri = "address" of our change notice if client registers one
				theResult.setNotificationUri(getContext().getContentResolver(),CONTENT_URI);
				theResult.setOnQueryDataStore(new TypedMatrixCursor.OnQueryDataStore() {
					@Override
					public boolean onQueryDataStore(TypedMatrixCursor aCursor) {
						return getJumpPoints(aCursor);
					}
				});
				theResult.requery();
				return theResult;
			case MATCH_CODE_JUMP_POINT:
				final int rowId = getRowId(aUri);
				theResult = new TypedMatrixCursor(aProjection,theProjectionTypes,1);
				//notification Uri = "address" of our change notice if client registers one
				theResult.setNotificationUri(getContext().getContentResolver(),
						ContentUris.withAppendedId(CONTENT_URI,rowId));
				theResult.setOnQueryDataStore(new TypedMatrixCursor.OnQueryDataStore() {
					@Override
					public boolean onQueryDataStore(TypedMatrixCursor aCursor) {
						return getJumpPoint(aCursor,rowId);
					}
				});
				theResult.requery();
				return theResult;
			case UriMatcher.NO_MATCH:
				return getErrorMsgRow();
			default:
				throw new IllegalArgumentException("Unknown Uri: " + aUri);
		}
	}

	@Override
	public String getType(Uri aUri) {
        switch (URI_MATCHER.match(aUri)) {
            case MATCH_CODE_ALL_JUMP_POINTS:
                return ContentResolver.CURSOR_DIR_BASE_TYPE+"/"+CONTENT_TYPE;
            case MATCH_CODE_JUMP_POINT:
                return ContentResolver.CURSOR_ITEM_BASE_TYPE+"/"+CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + aUri);
        }
	}
	
	@Override
	public Uri insert(Uri aUri, ContentValues aValues) {
		switch (URI_MATCHER.match(aUri)) {
			case MATCH_CODE_ALL_JUMP_POINTS:
				File theFile;
				if (aValues.containsKey(JUMP_DATA))
					theFile = new File(aValues.getAsString(JUMP_DATA));
				else
	                throw new IllegalArgumentException("JUMP_DATA column value required.");
				String theName = (aValues.containsKey(JUMP_NAME))?
						aValues.getAsString(JUMP_NAME):theFile.getName();
				if (BitsStringUtils.isEmpty(theName))
					theName = theFile.getName();
				int theNewId = mJumpPointsDb.addJumpPoint(theFile,theName);
				if (theNewId>=0) {
					Uri theNewUri = ContentUris.withAppendedId(CONTENT_URI,theNewId);
					//let clients with ContentObservers know that our data has changed
					getContext().getContentResolver().notifyChange(theNewUri,null);						
					return theNewUri;
				} else
					throw new SQLException("Failed to insert row: "+aUri);
			case MATCH_CODE_JUMP_POINT:
				throw new UnsupportedOperationException();
			default:
				throw new IllegalArgumentException("Unknown Uri: " + aUri);
		}
	}

	@Override
	public int delete(Uri aUri, String aSelection, String[] aSelectionArgs) {
		int theRowsAffected = 0;
		switch (URI_MATCHER.match(aUri)) {
			case MATCH_CODE_ALL_JUMP_POINTS:
				throw new UnsupportedOperationException();
			case MATCH_CODE_JUMP_POINT:
				int rowId = getRowId(aUri);
				theRowsAffected = mJumpPointsDb.removeJumpPoint(rowId);
				break;
			default:
				throw new IllegalArgumentException("Unknown Uri: " + aUri);
		}
		if (theRowsAffected>0) {
			//let clients with ContentObservers know that our data has changed
			getContext().getContentResolver().notifyChange(aUri,null);
		}
		return theRowsAffected;
	}

	@Override
	public int update(Uri aUri, ContentValues aValues, String aSelection, String[] aSelectionArgs) {
		int theRowsAffected;
		switch (URI_MATCHER.match(aUri)) {
			case MATCH_CODE_ALL_JUMP_POINTS:
				throw new UnsupportedOperationException();
			case MATCH_CODE_JUMP_POINT:
				int rowId = getRowId(aUri);
				String[] theJumpPoint = mJumpPointsDb.getJumpPoint(rowId);
				if (aValues.containsKey(JUMP_NAME))
					theJumpPoint[JumpPointDatabase.NAME_IDX] = aValues.getAsString(JUMP_NAME);
				if (aValues.containsKey(JUMP_DATA))
					theJumpPoint[JumpPointDatabase.DATA_IDX] = aValues.getAsString(JUMP_DATA);
				theRowsAffected = mJumpPointsDb.updateJumpPoint(rowId,
						new File(theJumpPoint[JumpPointDatabase.DATA_IDX]),
						theJumpPoint[JumpPointDatabase.NAME_IDX]);
				break;
			default:
				throw new IllegalArgumentException("Unknown Uri: " + aUri);
		}
		if (theRowsAffected>0) {
			//let clients with ContentObservers know that our data has changed
			getContext().getContentResolver().notifyChange(aUri,null);
		}
		return theRowsAffected;
	}
	
	private Cursor getErrorMsgRow() {
		MatrixCursor theResult = new MatrixCursor(new String[] {
		    	BaseColumns._ID, 
		    	LiveFolders.NAME, 
		});
		theResult.addRow(new Object[] {
		    	-1, //id
		    	getContext().getString(R.string.list_empty), //name 
		});
		return theResult;
	}

	/**
	 * Get the all the jump points.
	 * @param aCursor - result container to be filled.
	 * @return Returns TRUE if there was no data store error.
	 */
	private boolean getJumpPoints(TypedMatrixCursor aCursor) {
		HashMap<String,Integer> theJumpPoints = mJumpPointsDb.getJumpPointsMap();
		if (theJumpPoints!=null && theJumpPoints.size()>0) {
			String[] theKeysArray = new String[theJumpPoints.size()];
			theJumpPoints.keySet().toArray(theKeysArray);
			mJumpPointsDb.sortJumpPointNames(theKeysArray);
			for (String aKey: theKeysArray) {
				Integer theRowId = theJumpPoints.get(aKey);
				String[] theJumpPoint = mJumpPointsDb.decodeJumpPoint(aKey);
				addJumpPointToCursor(aCursor,theRowId,new File(theJumpPoint[0]),theJumpPoint[1]);
				Thread.yield();
			}
		}
		return true;
	}

	/**
	 * Get a single jump point.
	 * @param aCursor - result container to be filled.
	 * @param rowId - ID of the jump point
	 * @return Returns TRUE if there was no data store error.
	 */
	private boolean getJumpPoint(TypedMatrixCursor aCursor, int aRowId) {
		String[] theJumpPoint = mJumpPointsDb.getJumpPoint(aRowId);
		if (theJumpPoint!=null) {
			addJumpPointToCursor(aCursor,aRowId,new File(theJumpPoint[0]),theJumpPoint[1]);
		}
		return true;
	}

	private void addJumpPointToCursor(final TypedMatrixCursor aCursor, int rowId, 
			final File aFile, final String aName) {
		if (aCursor!=null && aFile!=null) {
			String colName;
			Drawable theIcon = null;
			TypedMatrixCursor.RowBuilder theRow = aCursor.newRow();
			for (int i=0; i<aCursor.getColumnCount(); i++) {
				colName = aCursor.getColumnName(i);
				if (colName.equals(JUMP_ID)) {
					theRow.add(rowId);
				} else if (colName.equals(JUMP_NAME)) {
					if (aName!=null)
						theRow.add(aName);
					else
						theRow.add(aFile.getName());
				} else if (colName.equals(JUMP_DATA)) {
					theRow.add(aFile.getPath());
				} else if (colName.equals(JUMP_URI)) {
					theRow.add(Uri.parse("jumppoint://"+aFile.getPath()));
				} else if (colName.equals(LiveFolders.DESCRIPTION)) {
					theRow.add(BitsFileUtils.getParentPathRelativeToExternalStorage(aFile));
				} else if (colName.equals(LiveFolders.ICON_BITMAP)) {
					if (mShowThumbnails) {
		        		theIcon = BitsThumbnailUtils.getFileIcon(getContext(),mMimeMap,aFile,0,0,0,true);
		        	} else {
		        		theIcon = null;
		        	}
					//despite Android docs, ICON_BITMAP expects blob
	        		theRow.addBlob(theIcon);
				} else if (colName.equals(LiveFolders.ICON_PACKAGE)) {
					if (theIcon==null) 
						theRow.add("com.blackmoonit.android.FileBrowser");
					else
						theRow.add(null);
				} else if (colName.equals(LiveFolders.ICON_RESOURCE)) {
					if (theIcon==null) {
						int theResId;
		            	if (aFile.isDirectory())
		            		theResId = R.drawable.item_folder;
		            	else if (mMimeMap.isType("application/zip",aFile))
		            		theResId = R.drawable.item_zip;
		            	else
		            		theResId = R.drawable.item_file;
		            	theRow.add(Intent.ShortcutIconResource.fromContext(getContext(),theResId));
					} else
						theRow.add(null);
				}
			}
		}
	}
	
}
