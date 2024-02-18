package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.os.Environment;

import com.blackmoonit.filesystem.BitsFileUtils;
import com.blackmoonit.filesystem.MIMEtypeMap;
import com.blackmoonit.lib.BitsLegacy;
import com.blackmoonit.lib.BitsStringUtils;
import com.blackmoonit.lib.FifoQueue;

/**
 * Matcher used to take a user query and match it against file objects.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileMatcher {
	private static final int OP_NAME = 0;
	private static final int OP_MIME = 1;
	private static final int OP_REGEX = 2;
	private static final int OP_DATE_MIN = 3;
	private static final int OP_DATE_MAX = 4;
	private static final int OP_SIZE_MIN = 5;
	private static final int OP_SIZE_MAX = 6;
	private static final int OP_DUPLICATES = 7;
	
	public static final long ONE_DAY_MILLIS = 86400000L; //1000*60*60*24 milliseconds

	private class FileMatcherTerm {
		@SuppressWarnings("unused")
		String mTerm;
		int mOp;
		boolean bNegate = false; //OP_DUP will add orig filename too if true
		Pattern mPattern = null;
		Matcher mMatcher = null;
		Long mValue = null;
		
		private FileMatcherTerm(String aTerm, int aOp) {
			mTerm = aTerm;
			mOp = aOp;
			int theFlags = 0;
			switch (aOp) {
				case OP_NAME:
				case OP_MIME:
					if (aTerm.startsWith("!") && aTerm.length()>1) {
						bNegate = true;
						aTerm = aTerm.substring(1);
					}
					aTerm = ".*"+scrubText(aTerm)+".*";
					theFlags = Pattern.CASE_INSENSITIVE;
					try {
						mPattern = Pattern.compile(aTerm,theFlags);
					} catch (PatternSyntaxException pse) {
						mPattern = null;
					}
					if (mPattern!=null)
						mMatcher = mPattern.matcher("");
					break;
				case OP_REGEX:
					try {
						if (aTerm.endsWith("i"))
							theFlags = Pattern.CASE_INSENSITIVE;
						mPattern = Pattern.compile(aTerm,theFlags);
					} catch (PatternSyntaxException pse) {
						try {
							mPattern = Pattern.compile(aTerm.replace("\\","\\\\"),theFlags);
						} catch (PatternSyntaxException pse2) {
							mOp = OP_NAME;
							mPattern = null;
						}
					}
					if (mPattern!=null)
						mMatcher = mPattern.matcher("");
					break;
				case OP_DUPLICATES:
					bNegate = (aTerm.equals("22"));
					aTerm = BitsFileUtils.DUPLICATE_FILENAME_REGEX;
					theFlags = Pattern.CASE_INSENSITIVE;
					mPattern = Pattern.compile(aTerm,theFlags);
					mMatcher = mPattern.matcher("");
					break;
				case OP_DATE_MAX:
				case OP_DATE_MIN:
					try {
						mValue = Long.parseLong(aTerm)*ONE_DAY_MILLIS;
					} catch (NumberFormatException nfe) {
						mValue = 0L;
					}
					break;
				case OP_SIZE_MAX:
				case OP_SIZE_MIN:
					Matcher m = Pattern.compile("(\\d+)(\\D+)").matcher(
							aTerm.toLowerCase(Locale.getDefault()));
					if (m.find()) {
						aTerm = m.group(1);
						try {
							mValue = Long.parseLong(aTerm);
							if (m.group(2).startsWith("k")) {
								mValue = mValue * 1024;
							} else if (m.group(2).startsWith("m")) {
								mValue = mValue * 1024 * 1024;
							} else if (m.group(2).startsWith("g")) {
								mValue = mValue * 1024 * 1024 * 1024;
							} else if (m.group(2).startsWith("t")) {
								mValue = mValue * 1024 * 1024 * 1024 * 1024;
							}
						} catch (Exception e) {
							mValue = 0L;
						}
					}
					break;
			}
		}
		
		private String scrubText(String aStr) {
			if (aStr==null)
				return null;
			/*
			 *  the backslash needs to be doubled up on the replacement string to tell
			 *  the system that we really want a backslash in it. Since all backslashes
			 *  in string literals need to be doubled in the source, it comes out as 4 slashes
			 *  per 1 literal backslash in the replacement string. 
			 */
			aStr = aStr.replaceAll("\\\\","\\\\\\\\");
			aStr = aStr.replaceAll("\\(","\\\\(");
			aStr = aStr.replaceAll("\\)","\\\\)");
			aStr = aStr.replaceAll("\\.","\\\\.");
			aStr = aStr.replaceAll("\\?",".");
			aStr = aStr.replaceAll("\\*",".*");
			aStr = aStr.replaceAll("\\^","\\\\^");
			aStr = aStr.replaceAll("\\$","\\\\$");
			aStr = aStr.replaceAll("\\[","\\\\[");
			aStr = aStr.replaceAll("\\]","\\\\]");
			return aStr;			
		}
		
		private String scrubGroup(int aGroupNum) {
			return BitsStringUtils.trim(scrubText(mMatcher.group(aGroupNum)));
		}
	}
	
	private List<FileMatcherTerm> mTerms = new ArrayList<FileMatcherTerm>();
	public MIMEtypeMap mMimeMap = null;
	/**
	 * SearchResults queue acts as an intermediary between the search thread and the UI thread 
	 * so that we avoid IllegalStateExceptions with updating the DataSource list without also 
	 * updating the UI. Also, if we updated the UI after every find, it will slow the device 
	 * down intollerably. Basic producer part of a producer/consumer threading model.
	 */
	public ConcurrentLinkedQueue<File> mSearchResults = new ConcurrentLinkedQueue<File>();
	private int mMaxResults = 0;
	private int mSearchResultCounter = 0;
	private boolean bSearchFinished = true;
	private FileComparator mSorterAlpha = null;
	
	/**
	 * 
	 * @param aUserQuery - query string
	 */
	public FileMatcher(String aUserQuery) {
		this(aUserQuery,0);
	}
	
	/**
	 * 
	 * @param aUserQuery - query string
	 * @param aResultLimit - limit results
	 */
	public FileMatcher(String aUserQuery, int aResultLimit) {
		setMaxResults(aResultLimit);
		setUserQuery(aUserQuery);
	}

	public void setMaxResults(int aResultLimit) {
		if (aResultLimit>=0)
			mMaxResults = aResultLimit;
	}
	
	public void setUserQuery(String aQuery) {
		mTerms.clear();
		if (aQuery==null || aQuery.length()==0)
			return;
		String theQuery = aQuery.trim();
		int idx;
		while (theQuery.length()>0) {
			String theTerm = null;
			if (theQuery.startsWith("\"")) {
				idx = theQuery.indexOf("\"",1);
				if (idx<0)
					idx = theQuery.length();
				theTerm = theQuery.substring(1,idx);
				mTerms.add(new FileMatcherTerm(theTerm,OP_NAME));
			} else if (theQuery.startsWith(">")) {
				idx = theQuery.indexOf(" ",1);
				if (idx<0)
					idx = theQuery.length();
				theTerm = theQuery.substring(1,idx);
				try {
					Long.parseLong(theTerm);
					mTerms.add(new FileMatcherTerm(theTerm,OP_DATE_MIN));
				} catch (NumberFormatException nfe) {
					mTerms.add(new FileMatcherTerm(theTerm,OP_SIZE_MIN));
				}
			} else if (theQuery.startsWith("<")) {
				idx = theQuery.indexOf(" ",1);
				if (idx<0)
					idx = theQuery.length();
				theTerm = theQuery.substring(1,idx);
				try {
					Long.parseLong(theTerm);
					mTerms.add(new FileMatcherTerm(theTerm,OP_DATE_MAX));
				} catch (NumberFormatException nfe) {
					mTerms.add(new FileMatcherTerm(theTerm,OP_SIZE_MAX));
				}
			} else if (theQuery.startsWith(".")) {
				idx = theQuery.indexOf(" ",1);
				if (idx<0)
					idx = theQuery.length();
				theTerm = theQuery.substring(1,idx);
				mTerms.add(new FileMatcherTerm(theTerm,OP_MIME));
			} else if (theQuery.startsWith("/")) {
				idx = theQuery.indexOf("/",1);
				if (idx<0)
					idx = theQuery.length();
				theTerm = theQuery.substring(1,idx);
				if (theTerm.equals("2") || theTerm.equals("22")) {
					mSorterAlpha = new FileComparatorAlpha(false,false);
					mTerms.add(new FileMatcherTerm(theTerm,OP_DUPLICATES));
				} else {
					mTerms.add(new FileMatcherTerm(theTerm,OP_REGEX));
				}
			} else {
				idx = theQuery.indexOf(" ",1);
				if (idx<0)
					idx = theQuery.length();
				theTerm = theQuery.substring(0,idx);
				mTerms.add(new FileMatcherTerm(theTerm,OP_NAME));
			}
			try {
				theQuery = theQuery.substring(idx+1).trim();
			} catch (IndexOutOfBoundsException ioobe) {
				theQuery = "";
			} catch (NullPointerException npe) {
				theQuery = "";
			}
		}
	}
	
	//optimizing vars so that we don't create/free a crap-ton of them during a search
	private boolean theSearchResult;
	private File dupToAdd;
	
	public boolean matchFile(final File aFile) {
		if (aFile!=null && mTerms.size()>0) {
			theSearchResult = true;
			dupToAdd = null;
			for (FileMatcherTerm theTerm:mTerms) {
				switch (theTerm.mOp) {
					case OP_NAME:
					case OP_REGEX:
						if (theTerm.mMatcher!=null) {
							theTerm.mMatcher.reset(aFile.getName());
							theSearchResult = theTerm.mMatcher.matches();
							if (theTerm.bNegate)
								theSearchResult = !theSearchResult;
						}
						break;
					case OP_MIME:
						if (theTerm.mMatcher!=null && mMimeMap!=null) {
							theTerm.mMatcher.reset(mMimeMap.getMIMEtype(aFile));
							theSearchResult = theTerm.mMatcher.matches();
							if (theTerm.bNegate)
								theSearchResult = !theSearchResult;
							else if (!theSearchResult) {
								theTerm.mMatcher.reset(aFile.getName());
								theSearchResult = theTerm.mMatcher.matches();
							}
						}
						break;
					case OP_DATE_MIN:
						if (theTerm.mValue!=null) {
							theSearchResult = (aFile.lastModified()<System.currentTimeMillis()-theTerm.mValue);		
						}
						break;
					case OP_DATE_MAX:
						if (theTerm.mValue!=null) {
							theSearchResult = (aFile.lastModified()>=System.currentTimeMillis()-theTerm.mValue);		
						}
						break;
					case OP_SIZE_MIN:
						if (theTerm.mValue!=null) {
							theSearchResult = (aFile.length()>=theTerm.mValue);		
						}
						break;
					case OP_SIZE_MAX:
						if (theTerm.mValue!=null) {
							theSearchResult = (aFile.length()<=theTerm.mValue);		
						}
						break;
					case OP_DUPLICATES:
						theSearchResult = false;
						if (theTerm.mMatcher!=null && aFile.isFile()) {
							theTerm.mMatcher.reset(aFile.getName());
							if (theTerm.mMatcher.matches()) {
								final String origFilenameRegex = "^(?:"+
										theTerm.scrubGroup(1)+").*(?:"	+theTerm.scrubGroup(3)+")$";
								FileListDataElement[] theMatches = FileListDataSource.listFiles(aFile.getParentFile(),
										new FileFilter(){
									@Override
									public boolean accept(File aFile2) {
										return (aFile2.getName().matches(origFilenameRegex) &&
												aFile2.length()==aFile.length());
									}
								});
								Arrays.sort(theMatches,mSorterAlpha);
								theSearchResult = (theMatches.length>1 && !theMatches[0].equals(aFile)); 
								if (theSearchResult && theTerm.bNegate && mSearchResults!=null) {
									dupToAdd = theMatches[0];
								}
							}
						}
						break;
				}
				if (!theSearchResult)
					break;
			}//foreach term
			if (theSearchResult && dupToAdd!=null) {
				mSearchResults.add(dupToAdd);
				dupToAdd = null;
			}
			return theSearchResult;
		} else
			return false;
	}
	
	protected void resetSearch() {
		mSearchResults.clear();
		mSearchResultCounter = 0;
		bSearchFinished = false;
	}
	
	public boolean isSearchFinished() {
		return bSearchFinished;
	}
	
	/**
	 * Search the folder and it's subfolders for files/folders matching the query.
	 * Does not reset the search queue from scratch, nor set the finished searching flag.
	 * 
	 * @param aFolder - root folder to start the search
	 */
	protected void searchSingleFolder(File aFolder) {
		if (aFolder!=null && !mTerms.isEmpty() && aFolder.exists() && aFolder.isDirectory()) {
			String theSysFolder = BitsFileUtils.getExternalSystemFolder().getPath();
			if (aFolder.getPath().equals(theSysFolder)) {
				theSysFolder = "";
			}
			//search file system using a queue instead of resursive function calls
			FifoQueue<File> theFileList = new FifoQueue<File>(aFolder.listFiles());
			File subFile;
			while (!theFileList.isEmpty() && !Thread.interrupted()) {
				if (mMaxResults>0 && mSearchResultCounter>mMaxResults)
					break;
				try {
					subFile = theFileList.remove();
					boolean bFileIsJumpPoint = BitsFileUtils.isFileJumpPoint(subFile);
					if (!bFileIsJumpPoint) {
						if (!subFile.isHidden() && subFile.canRead()) {
							if (matchFile(subFile)) {
								mSearchResults.add(subFile);
								mSearchResultCounter += 1;
							}
							if (subFile.isDirectory() && !subFile.getPath().equals(theSysFolder) &&
									!bFileIsJumpPoint) {
								theFileList.addAll(subFile.listFiles());
							}
						}
					}
					subFile = null;
				} catch (OutOfMemoryError oom) {
					System.gc();
					try {
						Thread.sleep(1000L);
					} catch (InterruptedException e) {
						//exit, nothing to do
					}
				}
				Thread.yield();
			}//while
		}
	}
	
	/**
	 * Search the folder and it's subfolders for files/folders matching the query.
	 * 
	 * @param aFolder - root folder to start the search
	 */
	public void searchFolder(File aFolder) {
		resetSearch();
		searchSingleFolder(aFolder);
		bSearchFinished = true;
	}
	
	/**
	 * Search the array of folders and their subfolders for files/folders matching the query.
	 * 
	 * @param aFolders - array of root folders in which to start the searches
	 */
	public void searchFolders(File[] aFolders) {
		resetSearch();
		if (aFolders!=null)
			for (File theFolder : aFolders) {
				searchSingleFolder(theFolder);
			}
		bSearchFinished = true;
	}
	
	/**
	 * Used by getStandardSearchFolders to get the public folder and if it has a different path 
	 * than {@link Environment#getExternalStorageDirectory() the SD card}, add it to the list.

	 * @param aPublicFolderList - result set
	 * @param aPublicFolder - public folder to check, use the BitsLegacy.FOLDER_X constants.
	 */
	private static void addPublicFolder(ArrayList<File> aPublicFolderList, String aPublicFolder) {
		File thePublicFolder = BitsLegacy.getExternalPublicFolder(aPublicFolder);
		if (aPublicFolderList!=null && thePublicFolder!=null && 
				!thePublicFolder.getPath().startsWith(Environment.getExternalStorageDirectory().getPath()))
			aPublicFolderList.add(thePublicFolder);
	}

	public static File[] getStandardSearchFolders(File aCurrentFolder) {
		ArrayList<File> theResults = new ArrayList<File>();
		if (aCurrentFolder!=null && aCurrentFolder.exists()) {
			theResults.add(aCurrentFolder);
		}
		File theMemDevice = Environment.getExternalStorageDirectory();
		if (BitsLegacy.SDK_Version()>8 && theMemDevice!=null) {  
			//public folders not necessarily on the SD card itself introduced in 2.0
			//the order these are listed here will also become the search order
			addPublicFolder(theResults,BitsLegacy.FOLDER_DOWNLOADS);

			addPublicFolder(theResults,BitsLegacy.FOLDER_DCIM);
			addPublicFolder(theResults,BitsLegacy.FOLDER_PICTURES);
			addPublicFolder(theResults,BitsLegacy.FOLDER_MUSIC);

			addPublicFolder(theResults,BitsLegacy.FOLDER_MOVIES);
			addPublicFolder(theResults,BitsLegacy.FOLDER_PODCASTS);

			addPublicFolder(theResults,BitsLegacy.FOLDER_RINGTONES);			
			addPublicFolder(theResults,BitsLegacy.FOLDER_ALARMS);
			addPublicFolder(theResults,BitsLegacy.FOLDER_NOTIFICATIONS);
		}
		theResults.add(theMemDevice);
		File[] theFunctionResult = new File[theResults.size()];
		theResults.toArray(theFunctionResult);
		return theFunctionResult;
	}

}
