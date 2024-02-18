package com.blackmoonit.android.FileBrowser;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blackmoonit.filesystem.BitsFileUtils;

/**
 * Abstract class implementing the various ways to sort a list of files.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public abstract class FileComparator implements Comparator<FileListDataElement> {
	public int mSorterReverseFactor = 1;
	public boolean mFoldersFirst = true;
	
	private final Pattern DUPLICATE_FILENAME_PATTERN = Pattern.compile(
			BitsFileUtils.DUPLICATE_FILENAME_REGEX,Pattern.CASE_INSENSITIVE);
	private final Matcher mMatcher;
	private String s1;
	private String s2;
	private String n1;
	private String n2;
	private String p1;
	private String p2;
	private String e1;
	private String e2;
	private boolean isPathEqual;
	private int theResult;
	
	public FileComparator() {
		super();
		mMatcher = DUPLICATE_FILENAME_PATTERN.matcher("");
	}
	
	public FileComparator(boolean bReverseSort, boolean bFoldersFirst) {
		this();
		setReverseSort(bReverseSort);
		mFoldersFirst = bFoldersFirst;
	}
	
	@Override
	public int compare(FileListDataElement f1, FileListDataElement f2) {
		if (f1.bIsFile==f2.bIsFile || !mFoldersFirst) {
			return compareFiles(f1,f2);
		} else {
			return (f1.bIsFile)?1:-1;
		}
	}
	
	/**
	 * Compare two folders or two files (mixed is already handled).
	 * @param f1 - file1
	 * @param f2 - file2
	 * @return - 0 means f1=f2, -1 means f1<f2, 1 means f1>f2.
	 */
	protected abstract int compareFiles(FileListDataElement f1, FileListDataElement f2);

	public boolean getReverseSort() {
		return (mSorterReverseFactor==-1)?true:false;
	}
	
	public void setReverseSort(boolean bReverseSort) {
		mSorterReverseFactor = (bReverseSort)?-1:1;
	}
	
	protected int sortFileName(FileListDataElement f1, FileListDataElement f2) {
		s1 = f1.mCompName;
		s2 = f2.mCompName;
		n1 = null;
		n2 = null;
		p1 = f1.mParentPath;
		p2 = f2.mParentPath;
		isPathEqual = (p1==p2) || (p1!=null && p1.equals(p2));

		//we want search results to group duplicate filenames together that are in same folder
		if (!isPathEqual) {
			mMatcher.reset(s1);
			if (mMatcher.matches()) {
				s1 = mMatcher.group(1).trim()+mMatcher.group(3);
				n1 = mMatcher.group(2);
			}
			mMatcher.reset(s2);
			if (mMatcher.matches()) {
				s2 = mMatcher.group(1).trim()+mMatcher.group(3);
				n2 = mMatcher.group(2);
			}
		}
		s1 = f1.mNameOnly;
		s2 = f2.mNameOnly;
		theResult = s1.compareTo(s2);
		if (theResult==0) {
			e1 = f1.mExtPart;
			e2 = f2.mExtPart;
			if (e1!=null && e2!=null) {
				theResult = e1.compareTo(e2);				
			} else if (e1==e2) {  //both are null
				theResult = 0;
			} else
				theResult = (e2!=null)?-1:1;
		}
		//we want search results to group duplicate filenames together that are in same folder
		if (theResult==0 && !isPathEqual) {
			if (p1!=null && p2!=null) {
				theResult = p1.compareTo(p2);
				if (theResult==0 && n1!=n2)
					theResult = (n1!=null && n2!=null)?n1.compareTo(n2):((n1==null)?-1:1);
			} else
				theResult = (p2!=null)?-1:1;
		}
		return theResult;
	}
	
}
