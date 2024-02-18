package com.blackmoonit.android.FileBrowser;


/**
 * Date (oldest first), followed by Alphabetical sort.
 * @see AlphaFileComparator
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileComparatorDate extends FileComparator {

	public FileComparatorDate(boolean bReverseSort, boolean bFoldersFirst) {
		super(bReverseSort,bFoldersFirst);
	}
	
	@Override
	protected int compareFiles(FileListDataElement f1, FileListDataElement f2) {
		long lm1 = f1.mLastModified;
		long lm2 = f2.mLastModified;
		if (lm1<lm2) {
			return mSorterReverseFactor;
		} else if (lm1>lm2) {
			return -mSorterReverseFactor;
		} else {
			return sortFileName(f1, f2);
		}
	}
	
}
