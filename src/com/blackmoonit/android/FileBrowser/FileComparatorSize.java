package com.blackmoonit.android.FileBrowser;


/**
 * Size, followed by Alphabetical sort.
 * @see AlphaFileComparator
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileComparatorSize extends FileComparator {

	public FileComparatorSize(boolean bReverseSort, boolean bFoldersFirst) {
		super(bReverseSort,bFoldersFirst);
	}
	
	@Override
	protected int compareFiles(FileListDataElement f1, FileListDataElement f2) {
		long l1 = f1.mSize;
		long l2 = f2.mSize;
		boolean bIsFile = f1.isFile();
		if (bIsFile && l1<l2) {
			return -mSorterReverseFactor;
		} else if (bIsFile && l1>l2) {
			return mSorterReverseFactor;
		} else {
			return sortFileName(f1, f2);
		}
	}
	
	
}
