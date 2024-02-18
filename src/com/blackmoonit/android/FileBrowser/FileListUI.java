package com.blackmoonit.android.FileBrowser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.AbsListView;

/**
 * Any UI for file browser needs to implement these events/methods
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public interface FileListUI {
	
	/**
	 * @return Returns the first visible item position or AdapterView.INVALID_POSITION.
	 */
	public int getFirstVisibleItemPos();
	
	/**
	 * @return Returns the last visible item position or AdapterView.INVALID_POSITION.
	 */
	public int getLastVisibleItemPos();
	
	/**
	 * @return Returns the most important visible item position or AdapterView.INVALID_POSITION.
	 */
	public int getChiefVisibleItemPos();
    
	public void setSelection(int anItemPos);
	public void setSelectionFromTop(int anItemPos);
	public void scrollToPosition(int anItemPos);
    
	public void setFontSize();
	public void invalidateViews();

	/**
	 * Called during {@link onCreate} to handle the startup Intent, if any.
	 * @param aIntent - is the Intent used to start the Activity.  May be null.
	 */
	
	public void processExternalRequest(Intent aIntent);
	
	/**
	 * Refreshes the list view to reflect changes in it's internal list.
	 * Any file system changes are ignored.
	 */
	public void refreshListView();
	
	/**
	 * @return Return TRUE if the back key should exit the app.
	 */
	public boolean doesBackKeyExit();
	
	/**
	 * Event occurs after the file list is filled and the adapters are refreshed
	 * @param aItemPos - scroll list to this item position if > AbsListView.INVALID_POSITION
	 */
	public void onAfterFillList(int aItemPos);
	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);
	
	/**
	 * Return the currently chosen view type (list/grid).
	 * @return Returns the list/grid common ancestor class.
	 */
	public AbsListView getFileListingView();
	
}
