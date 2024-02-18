package com.blackmoonit.android.FileBrowser;


/**
 * Alphabetical sort that first removes the extension before comparing.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileComparatorAlpha extends FileComparator {

	public FileComparatorAlpha(boolean bReverseSort, boolean bFoldersFirst) {
		super(bReverseSort,bFoldersFirst);
	}
	
	@Override
	protected int compareFiles(FileListDataElement f1, FileListDataElement f2) {
		return sortFileName(f1, f2)*mSorterReverseFactor;
	}

}
