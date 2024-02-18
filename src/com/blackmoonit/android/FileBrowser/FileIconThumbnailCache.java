package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.util.HashMap;

import android.graphics.drawable.Drawable;


/**
 * Thumbnail cache holding image file's thumbnail. Hashmap wrapper to help facilitate using file cache.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2012
 */
public class FileIconThumbnailCache extends HashMap<String, Drawable> {
	static private final long serialVersionUID = -4426767318521949295L;

	public String getCacheKey(File aFile, int aScaleFactor) {
		return Integer.toString(aScaleFactor)+aFile.getPath();
	}
	
	public boolean containsFile(File aFile, int aScaleFactor) {
		if (aFile!=null) {
			if (containsKey(getCacheKey(aFile,aScaleFactor))) {
				return true;
			} else
				return false;
		}
		return false;
	}

	public Drawable getFile(File aFile, int aScaleFactor) {
		Drawable theResult = null;
		if (aFile!=null) {
			theResult = get(getCacheKey(aFile,aScaleFactor));
		}
		return theResult;
	}

	public void putFile(File aFile, int aScaleFactor, Drawable aThumbnail) {
		if (aFile!=null && aThumbnail!=null) {
			put(getCacheKey(aFile,aScaleFactor),aThumbnail);
		}
	}

	public void removeFile(File aFile, int aScaleFactor) {
		if (aFile!=null) {
			remove(getCacheKey(aFile,aScaleFactor));
		}
	}
	
}
