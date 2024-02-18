package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * Jump Points stored in the settings file.
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public final class JumpPointDatabase {
	private final Context mContext;
	private final SharedPreferences mSettings;
	public static final int DATA_IDX = 0;
	public static final int NAME_IDX = 1;
	
	public JumpPointDatabase(Context aContext, SharedPreferences aSettings) {
		super();
		mContext = aContext;
		mSettings = aSettings;
	}
	
	/**
	 * 
	 * @param aJumpPoint - raw string stored
	 * @return Jump Point array, 0 is file.getPath(), 1 is name.
	 */
	public String[] decodeJumpPoint(String aJumpPoint) {
		if (aJumpPoint!=null)
			return aJumpPoint.split("\\?");
		else
			return null;
	}
	
	public String encodeJumpPoint(String aName, File aFile) {
		if (aName!=null && aFile!=null)
			return aFile.getPath()+"?"+aName;
		else
			return null;
	}
	
	/**
	 * Get all the user created jump points. May return NULL.
	 * 
	 * @return Returns the raw string array containing: "targetFile?name"
	 */
	public String[] getJumpPoints() {
		String[] theResult = null;
		if (mContext!=null && mSettings!=null) {
			String theCountKey = mContext.getString(R.string.pref_key_jump_point_count);
			String thePrefKey = mContext.getString(R.string.pref_key_jump_point_);
			int idxJumpPointEnd = mSettings.getInt(theCountKey,0);
			theResult = new String[idxJumpPointEnd];
			for (int i=0; i<idxJumpPointEnd; i++) {
				theResult[i] = mSettings.getString(thePrefKey+Integer.toString(i),null);
			}
		}
		return theResult;
	}
	
	/**
	 * Save raw jump point strings back into the database.
	 * @param aJumpPoints - output of {@link #getJumpPoints()}.
	 */
	public void addJumpPoints(String[] aJumpEntry) {
		if (mContext!=null && mSettings!=null && aJumpEntry!=null && aJumpEntry.length>0) {
			String theCountKey = mContext.getString(R.string.pref_key_jump_point_count);
			String thePrefKey = mContext.getString(R.string.pref_key_jump_point_);
			int idxJumpPointEnd = mSettings.getInt(theCountKey,0);
			for (int i=0; i<aJumpEntry.length; i++) {
				Editor theEditor = mSettings.edit();
				theEditor.putString(thePrefKey+Integer.toString(idxJumpPointEnd++),aJumpEntry[i]);
				theEditor.putInt(theCountKey,idxJumpPointEnd);
				AppPreferences.applyAndBackup(mContext,theEditor);
			}
		}
	}
	
	/**
	 * Get all the user created jump points.
	 * 
	 * @return Returns the map containing the raw string jump point and their ID.
	 */
	public HashMap<String,Integer> getJumpPointsMap() {
		HashMap<String,Integer> theResult = new HashMap<String,Integer>();
		String[] theJumpPoints = getJumpPoints();
		if (theJumpPoints!=null) {
			for (Integer i=0; i<theJumpPoints.length; i++) {
				if (theJumpPoints[i]!=null) {
					theResult.put(theJumpPoints[i],i);
				}
			}
		}
		return theResult;
	}
	
	/**
	 * Get a user created jump point.
	 * 
	 * @param aRowId - the jump point ID
	 * @return Returns the String[] containing the jump point name at 1 and the target file at 0.
	 */
	public String[] getJumpPoint(int aRowId) {
		if (mContext!=null && mSettings!=null) {
			String thePrefKey = mContext.getString(R.string.pref_key_jump_point_);
			String anEntry = mSettings.getString(thePrefKey+Integer.toString(aRowId),null);
			if (anEntry!=null) {
				return decodeJumpPoint(anEntry);
			}
		}
		return null;
	}
		
	/**
	 * Add a jump point to the database (settings file).
	 * @param aFile - jump point
	 * @param aName - jump point name
	 * @return Returns the new Id for the jump point added.
	 */
	public int addJumpPoint(File aFile, String aName) {
		if (mContext!=null && mSettings!=null && aName!=null && !aName.equals("")) {
			String theCountKey = mContext.getString(R.string.pref_key_jump_point_count);
			String thePrefKey = mContext.getString(R.string.pref_key_jump_point_);
			int idxJumpPointEnd = mSettings.getInt(theCountKey,0);
			String anEntry = encodeJumpPoint(aName,aFile);
			Editor theEditor = mSettings.edit();
			theEditor.putString(thePrefKey+Integer.toString(idxJumpPointEnd),anEntry);
			theEditor.putInt(theCountKey,idxJumpPointEnd+1);
			AppPreferences.applyAndBackup(mContext,theEditor);
			return idxJumpPointEnd;
		} else
			return -1;
	}
	
	/**
	 * Update a particular jump point.
	 * @param aRowId - Id of jump point to update
	 * @param aFile - jump point
	 * @param aName - jump point name
	 * @return Returns the number of jump points affected, 1 on success, 0 on failure.
	 */
	public int updateJumpPoint(int aRowId, File aFile, String aName) {
		if (mContext!=null && mSettings!=null && aName!=null && !aName.equals("")) {
			String thePrefKey = mContext.getString(R.string.pref_key_jump_point_);
			if (mSettings.contains(thePrefKey+Integer.toString(aRowId))) {
				String anEntry = encodeJumpPoint(aName,aFile);
				Editor theEditor = mSettings.edit();
				theEditor.putString(thePrefKey+Integer.toString(aRowId),anEntry);
				AppPreferences.applyAndBackup(mContext,theEditor);
				return 1;
			}
		}
		return 0;
	}
	
	/**
	 * Removes a saved jump point. 
	 * @param aRowId - Id of jump point to delete
	 * @return Returns the number of jump points affected, 1 on success, 0 on failure.
	 */
	public int removeJumpPoint(int aRowId) {
		if (mContext!=null && mSettings!=null) {
			String theCountKey = mContext.getString(R.string.pref_key_jump_point_count);
			String thePrefKey = mContext.getString(R.string.pref_key_jump_point_);
			int idxJumpPointEnd = mSettings.getInt(theCountKey,0);
			if (mSettings.contains(thePrefKey+Integer.toString(aRowId))) {
				Editor theEditor = mSettings.edit();
				for (int i=aRowId; i<idxJumpPointEnd-1; i++) {
					theEditor.putString(thePrefKey+Integer.toString(i),
							mSettings.getString(thePrefKey+Integer.toString(i+1),""));
				}
				//remove last entry
				idxJumpPointEnd -= 1;
				theEditor.remove(thePrefKey+Integer.toString(idxJumpPointEnd));
				theEditor.putInt(theCountKey,idxJumpPointEnd);
				AppPreferences.applyAndBackup(mContext,theEditor);
				return 1;
			}
		}
		return 0;
	}
	
	/**
	 * Sort the Jump Points by their user defined name.
	 * 
	 * @param aJumpPoints - the user defined jump points (keyset of {@link #getJumpPointsMap()})
	 */
	public void sortJumpPointNames(String[] aJumpPoints) {
		if (aJumpPoints!=null && aJumpPoints.length>0) {
			Arrays.sort(aJumpPoints,new Comparator<String>() {
				@Override
				public int compare(String s1, String s2) {
					if (s1!=null && s2!=null) {
						String[] sc1 = decodeJumpPoint(s1);
						String[] sc2 = decodeJumpPoint(s2);
						int rc = sc1[1].compareToIgnoreCase(sc2[1]);
						if (rc!=0)
							return rc;
						else
							return sc1[0].compareToIgnoreCase(sc2[0]);
					} else if (s1!=null)
						return 1;
					else
						return -1;
				}
			});
		}
	}

}
