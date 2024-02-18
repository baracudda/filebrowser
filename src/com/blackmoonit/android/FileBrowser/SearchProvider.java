package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.BaseColumns;

import com.blackmoonit.content.BitsIntent;
import com.blackmoonit.filesystem.BitsFileUtils;
import com.blackmoonit.filesystem.MIMEtypeMap;
import com.blackmoonit.media.BitsThumbnailUtils;

/**
 * Enable searching the file system.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class SearchProvider extends ContentProvider {
	/**
	 * The authority this provider runs under.
	 */
	public static final String AUTHORITY = "com.blackmoonit.FileBrowser.SearchProvider";	
    /**
     * The content:// scheme Uri for this provider.
     */
    public static final Uri CONTENT_URI = Uri.parse("content://"+AUTHORITY+"/search");

    // MIME types used for searching files or folders
    public static final String MIME_TYPE_DIR =	ContentResolver.CURSOR_DIR_BASE_TYPE +
    											"/vnd.blackmoonit.android.searchabledir";
    public static final String MIME_TYPE_FILE =	ContentResolver.CURSOR_ITEM_BASE_TYPE +
												"/vnd.blackmoonit.android.searchablefile";
    
	// UriMatcher stuff
    private static final int SEARCH_FILES = 0;
    private static final int GET_FILE = 1;
    private static final int SEARCH_SUGGEST = 2;
    private static final int REFRESH_SHORTCUT = 3;
    private static final UriMatcher sURIMatcher = buildUriMatcher();

	/**
	 * Builds up a UriMatcher for search suggestion and shortcut refresh queries.
	 */
	private static UriMatcher buildUriMatcher() {
		UriMatcher matcher =  new UriMatcher(UriMatcher.NO_MATCH);
		matcher.addURI(AUTHORITY,"search",SEARCH_FILES);
		matcher.addURI(AUTHORITY,"file://*",GET_FILE);
		// to get suggestions…
		matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
		matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);

		/* The following are unused in this implementation, but if we include
		 * {@link SearchManager#SUGGEST_COLUMN_SHORTCUT_ID} as a column in our suggestions table, we
		 * could expect to receive refresh queries when a shortcutted suggestion is displayed in
		 * Quick Search Box, in which case, the following Uris would be provided and we
		 * would return a cursor with a single item representing the refreshed suggestion data.
		 * "search_suggest_shortcut" = SearchManager.SUGGEST_URI_PATH_SHORTCUT
		 */
		matcher.addURI(AUTHORITY, "search_suggest_shortcut", REFRESH_SHORTCUT);
		matcher.addURI(AUTHORITY, "search_suggest_shortcut" + "/*", REFRESH_SHORTCUT);

		return matcher;
	}
	
	private static final String[] SUGGESTION_COLUMNS = new String[] {
			BaseColumns._ID,
			SearchManager.SUGGEST_COLUMN_TEXT_1,
			SearchManager.SUGGEST_COLUMN_TEXT_2,
			SearchManager.SUGGEST_COLUMN_ICON_1,
			"suggest_spinner_while_refreshing", //SearchManager.SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING,
			//uncomment the next line only if you want to refresh shortcuts
			"suggest_shortcut_id", //SearchManager.SUGGEST_COLUMN_SHORTCUT_ID,
			SearchManager.SUGGEST_COLUMN_INTENT_DATA
	};
	
	protected static final String[] JUMP_POINT_PROJECTION = new String[] {
		JumpPointProvider.JUMP_ID,
		JumpPointProvider.JUMP_NAME,
		JumpPointProvider.JUMP_DATA,
	};
	protected static final int JUMP_ID_IDX = 0;
	protected static final int JUMP_NAME_IDX = 1;
	protected static final int JUMP_DATA_IDX = 2;

	private boolean mShowThumbnails = true;
	private MIMEtypeMap mMimeMap = null;
	private File mCacheFolder = null;
	private BroadcastReceiver mDeviceStateListener = 
		new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				clearCache();
			}
		};

	@Override
	public boolean onCreate() {
    	mMimeMap = new MIMEtypeMap().createMaps();
		getContext().registerReceiver(mDeviceStateListener,new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
    	return true;
	}

	@Override
	protected void finalize() throws Throwable {
		getContext().unregisterReceiver(mDeviceStateListener);
		clearCache();
		super.finalize();
	}
	
	/**
	 * Clear the cache being used.
	 */
	protected void clearCache() {
		if (mCacheFolder!=null)
			BitsFileUtils.deleteFolderContents(mCacheFolder,null,null,null);
	}
	
	/**
	 * Handles all the searches and suggestion queries from the Search Manager.
	 * When requesting a specific file, the uri alone is required.
	 * When searching all of the filesystem for matches, the selectionArgs argument must carry
	 * the search query as the first element.
	 * All other arguments are ignored.
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
			String sortOrder) {

		mShowThumbnails = AppPreferences.getPrefs(getContext()).getBoolean(
				getContext().getString(R.string.pref_key_show_thumbnails),true);
		if (mCacheFolder==null)
			mCacheFolder = new File(BitsFileUtils.getCacheFolder(getContext()),"searchicons");
		if (mCacheFolder.exists()) {
			clearCache();
		} else {
    		mCacheFolder.mkdirs();
		}
		// Use the UriMatcher to see what kind of query we have and format the db query accordingly
		switch (sURIMatcher.match(uri)) {
			case SEARCH_SUGGEST:
				if (selectionArgs == null) {
					throw new IllegalArgumentException(
							"selectionArgs must be provided for the Uri: " + uri);
				}
				return getSuggestions(selectionArgs[0]);
			case SEARCH_FILES:
				if (selectionArgs == null) {
					throw new IllegalArgumentException(
							"selectionArgs must be provided for the Uri: " + uri);
				}
				return search(selectionArgs[0]);
			case GET_FILE:
				return getFile(uri);
			case REFRESH_SHORTCUT:
				return refreshShortcut(uri);
			default:
				throw new IllegalArgumentException("Unknown Uri: " + uri);
		}
	}

	private Cursor getSuggestions(String query) {
		query = query.toLowerCase(Locale.getDefault());
		return getFileMatches(query,SUGGESTION_COLUMNS,20);
	}

	private Cursor search(String query) {
		query = query.toLowerCase(Locale.getDefault());
		String[] columns = new String[] {
				BaseColumns._ID,
				SearchManager.SUGGEST_COLUMN_TEXT_1,
				SearchManager.SUGGEST_COLUMN_TEXT_2,
				SearchManager.SUGGEST_COLUMN_ICON_1,
				SearchManager.SUGGEST_COLUMN_INTENT_DATA};

		return getFileMatches(query,columns,0);
	}

	private Cursor getFile(Uri uri) {
		String rowId = uri.getPath();
		String[] columns = new String[] {
				SearchManager.SUGGEST_COLUMN_TEXT_1,
				SearchManager.SUGGEST_COLUMN_TEXT_2,
				SearchManager.SUGGEST_COLUMN_INTENT_DATA};

		return getFile(rowId,columns);
	}

	private Cursor refreshShortcut(Uri uri) {
		String rowId = uri.getLastPathSegment();
		return getFile(rowId,SUGGESTION_COLUMNS);
	}

	/**
	 * This method is required in order to query the supported types.
	 * It's also useful in our own query() method to determine the type of Uri received.
	 */
	@Override
	public String getType(Uri uri) {
		switch (sURIMatcher.match(uri)) {
			case SEARCH_FILES:
				return MIME_TYPE_DIR;
			case GET_FILE:
				return MIME_TYPE_FILE;
			case SEARCH_SUGGEST:
				return SearchManager.SUGGEST_MIME_TYPE;
			case REFRESH_SHORTCUT:
				return "vnd.android.cursor.item/vnd.android.search.suggest"; //SearchManager.SHORTCUT_MIME_TYPE;
			default:
				throw new IllegalArgumentException("Unknown URL " + uri);
		}
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
	
	/**
	 * Get the URI to use for the suggestion icon. Create's icons in the app's cache folder
	 * if needed.
	 * 
	 * @param aFile - file used to determine the icon to show
	 * @return Returns the URI Android's search box requires to show an icon
	 */
	private Uri getFileIconUri(File aFile) {
        Uri theResult = null;
		if (mMimeMap.isCategory("image", aFile) && mShowThumbnails) {
        	theResult = BitsFileUtils.getThumbnailUriFromImageFile(getContext(),aFile);
        } else if (mMimeMap.isType("application/zip",aFile)) {
        	theResult = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE+"://"+
    				"com.blackmoonit.android.FileBrowser/"+R.drawable.item_zip);
        } else if (aFile.isDirectory()) {
        	theResult = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE+"://"+
    				"com.blackmoonit.android.FileBrowser/"+R.drawable.item_folder);
        }
        if (theResult==null) {
        	Drawable theFileIcon = null;
        	if (mShowThumbnails) {
        		theFileIcon = BitsThumbnailUtils.getFileIcon(getContext(),mMimeMap,aFile,0,0,0,false);
        	}
        	if (theFileIcon!=null && (theFileIcon instanceof BitmapDrawable) && mCacheFolder!=null) {
            	File tempFile = new File(mCacheFolder,"icon"+Long.toHexString(aFile.hashCode())+".png");
            	if (!tempFile.exists() && mCacheFolder.exists()) {
                	FileOutputStream out = null;
                	try {
                		//File tempFile = File.createTempFile(aFile.getName()+"_","_"+String.valueOf(aFile.hashCode())+".png",mCacheFolder);
                		tempFile.deleteOnExit();
            			out = new FileOutputStream(tempFile);
            	        ((BitmapDrawable)theFileIcon).getBitmap().compress(CompressFormat.PNG,100,out);
            	        out.flush();
            			out.close();
                	} catch (Exception e) {
                		e.printStackTrace();
                    	return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE+"://"+
            					"com.blackmoonit.android.FileBrowser/"+R.drawable.item_file);
                	}
            	}
            	theFileIcon = null;
    	        theResult = Uri.parse("file://"+tempFile.getPath());
            } else {
            	int theRes;
            	if (aFile.isDirectory())
            		theRes = R.drawable.item_folder;
            	else if (mMimeMap.isType("application/zip",aFile))
            		theRes = R.drawable.item_zip;
            	else
            		theRes = R.drawable.item_file;
            	theResult = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE+"://"+
    					"com.blackmoonit.android.FileBrowser/"+theRes);
            }
       	}
        return theResult;
	}

	/**
	 * Adds a found match to the cursor.
	 * 
	 * @param aCursor - cursor used to return matches to the caller
	 * @param aFile - the file found by the search algorithm
	 */
	private void addMatchToCursor(final MatrixCursor aCursor, final File aFile, final String aText1) {
		if (aCursor!=null && aFile!=null) {
			String colName;
			MatrixCursor.RowBuilder theRow = aCursor.newRow();
			for (int i=0; i<aCursor.getColumnCount();i++) {
				colName = aCursor.getColumnName(i);
				if (colName.equals(BaseColumns._ID)) {
					theRow.add(aFile.hashCode());
				} else if (colName.equals("suggest_shortcut_id" )) {
					theRow.add(aFile.getPath());
				} else if (colName.equals(SearchManager.SUGGEST_COLUMN_TEXT_1)) {
					if (aText1==null)
						theRow.add(aFile.getName());
					else
						theRow.add(aText1);
				} else if (colName.equals(SearchManager.SUGGEST_COLUMN_TEXT_2)) {
					theRow.add(BitsFileUtils.getParentPathRelativeToExternalStorage(aFile));
				} else if (colName.equals(SearchManager.SUGGEST_COLUMN_ICON_1)) {
					theRow.add(getFileIconUri(aFile));
				} else if (colName.equals(SearchManager.SUGGEST_COLUMN_INTENT_DATA)) {
					theRow.add(BitsIntent.getViewFileUri(aFile));
				} else if (colName.equals("suggest_spinner_while_refreshing")) {
					theRow.add(1);
				}
			}
		}
	}

	/**
	 * Get the files that match the query.
	 * 
	 * @param aQuery - user defined search string
	 * @param columns - column data desired in the result set
	 * @return Returns a MatrixCursor with all of the result data found along with the columns
	 * requested.
	 */
	private Cursor getFileMatches(String aQuery, String[] columns, int aMaxResults) {
		final MatrixCursor theResult = new MatrixCursor(columns,Math.max(20,aMaxResults));
		//final ModalVar<Boolean> bWaitForResults = new ModalVar<Boolean>();
		if (aQuery==null || aQuery.equals("")) {
			Cursor theJumpPoints = getContext().getContentResolver().query(JumpPointProvider.CONTENT_URI,
					JUMP_POINT_PROJECTION, null, null, null);
			if (theJumpPoints!=null) {
				int i = 0;
				theJumpPoints.moveToFirst();
				while (i++<aMaxResults && !theJumpPoints.isAfterLast()) {
					addMatchToCursor(theResult,new File(theJumpPoints.getString(JUMP_DATA_IDX)),
							theJumpPoints.getString(JUMP_NAME_IDX));
					theJumpPoints.moveToNext();
				}
				theJumpPoints.close();
			}
		} else {
			final FileMatcher fm = new FileMatcher(aQuery,aMaxResults);
			fm.mMimeMap = mMimeMap;
			fm.setMaxResults(aMaxResults);
			File[] searchTheseFolders = FileMatcher.getStandardSearchFolders(null);
			//now perform the actual search
			fm.searchFolders(searchTheseFolders);
			//once searching is done, move the results into a cursor
			while (!fm.mSearchResults.isEmpty()) {
				addMatchToCursor(theResult,fm.mSearchResults.remove(),null);
				Thread.yield();
			}
			
		}
		return theResult;
	}

	/**
	 * Returns a single search result.
	 * 
	 * @param rowId - ID of the search result
	 * @param columns - requested column data
	 * @return Returns the single search result along with the column data desired.
	 */
	private Cursor getFile(String rowId, String[] columns) {
		MatrixCursor theResult = null;
		if (rowId!=null) {
			File theFile = new File(Uri.parse(rowId).getPath());
			if (theFile.exists()) {
				theResult = new MatrixCursor(columns,1);
				addMatchToCursor(theResult,theFile,null);
			}
		}
		return theResult;
	}
	
}
