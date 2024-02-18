package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.util.Locale;

import com.blackmoonit.filesystem.BitsFileUtils;
import com.blackmoonit.filesystem.FileOrchard;

/**
 * ListItem used for the FileListActivity.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileListDataElement extends File {
	/**
	 * All serializable classes need a new UUID
	 */
	private static final long serialVersionUID = 9188560699523093011L;
	/**
	 * Is the file marked?
	 */
	protected boolean bMarked = false;
	/**
	 * Does the folder containing this file contain marked files?
	 */
	protected boolean bFolderContainsMarked = false;
	/**
	 * Is the file represented here just a link to another, real, path?
	 */
	protected boolean bFileIsJumpPoint = false;
	
	//comparitor speed optimizations, pre-calc certain frequently accessed items
	public final String mCompName;
	public final String mParentPath;
	public final String mNameOnly;
	public final String mExtPart;
	//public final String mViewDesc; //content description for Accessibility services (not read in TextViews!)
	public final Long mLastModified;
	public final Long mSize;
	public final boolean bIsFile;
	
	//display adapter pre-calc optimizations
	public final String mExternalStorageRelativePath;

	/**
	 * Constructor taking a URI usually someFile.toURI();
	 * @param uri
	 */
	public FileListDataElement(String aPath) {
		super(aPath);
		mCompName = getName().toLowerCase(Locale.getDefault());
		mParentPath = getParent();
		mNameOnly = BitsFileUtils.replaceExtension(mCompName,"");
		mExtPart = BitsFileUtils.getExtension(this);
		mLastModified = this.lastModified();
		mSize = this.length();
		bIsFile = isFile();
		bFileIsJumpPoint = BitsFileUtils.isFileJumpPoint(this);
		mExternalStorageRelativePath = BitsFileUtils.getParentPathRelativeToExternalStorage(this);
		//String thePronouncableExt = (mExtPart!=null) ? mExtPart.toUpperCase().replace("", " ").trim() : "";
		//mViewDesc = BitsFileUtils.replaceExtension(getName(),thePronouncableExt);
	}

	/**
	 * May wish to tweak the filename for display purposes.
	 * @return Returns this.getName() except for special cases.
	 */
	public String getDisplayName() {
		//return getDisplayName(this);
		return getName();
	}
	
	/**
	 * Gets the string displayed on the screen for a given File object.
	 * 
	 * @param aFile - file object
	 * @return Return the string used for display purposes.
	 */
	public static String getDisplayName(File aFile) {
		if (aFile!=null) {
			//if (aFile.isDirectory())
			//	return "["+aFile.getName()+"]";
			//else
				return aFile.getName();
		} else {
			return null;
		}
	}
	
	public boolean isFileJumpPoint() {
		return bFileIsJumpPoint;
	}
	
	public boolean isMarked() {
		return bMarked;
	}
	
	public void setMark(boolean aValue) {
		bMarked = aValue;
	}
	
	public boolean containsMarked() {
		return (bFolderContainsMarked);
	}
	
	public void setMarkings(FileOrchard aMarkedFiles, File aFile) {
		bMarked = aMarkedFiles.contains(aFile);
		if (!bMarked)
			bFolderContainsMarked = aMarkedFiles.containsTreeOrBranch(aFile); 
	}

	public void resetMarks() {
		bMarked = false;
		bFolderContainsMarked = false;
	}
}
