package com.blackmoonit.android.FileBrowser;

import java.io.File;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;

import com.blackmoonit.filesystem.MIMEtypeMap;
import com.blackmoonit.graphics.BitsGraphicUtils;
import com.blackmoonit.lib.FifoQueue;
import com.blackmoonit.media.BitsThumbnailUtils;

/**
 * Manage icon retrieval for various file types. Folders are not handled.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileIcons {
	private static final long LOW_MEMORY_WARNING = 1024*64;
	private final Activity mAct;
	private final MIMEtypeMap mMimeMap;
	private final PriorityBlockingQueue<FileIconThumbnailQueueItem> mThumbnailQueue;
	private final FileIconThumbnailCache mThumbnailCache;
	private final FifoQueue<String> mThumbnailRecycleBin;
	private FileIconThumbnailThread mThumbnailThread;
	private final Intent mActivityIconIntent;
	private final PackageManager mPkgMgr;
	private String mLastMIMEtype = "";
	private Drawable mLastMIMEicon = null;
	private boolean mEnableThumbnails = false;
	public int origIconSize = 48;
	public int scaleFactor = 1;
	
	public interface OnSetThumbnail {
		public void onSetThumbnail(ImageView v, Drawable d);
	}
	public OnSetThumbnail mOnSetThumbnail = null;
	
	public FileIcons(Activity aAct,	MIMEtypeMap aMimeMap) {
		mAct = aAct;
		mPkgMgr = mAct.getPackageManager();
		mMimeMap = aMimeMap;
		mThumbnailQueue = new PriorityBlockingQueue<FileIconThumbnailQueueItem>(); 
		mThumbnailCache = new FileIconThumbnailCache();
		mThumbnailRecycleBin = new FifoQueue<String>();
		mActivityIconIntent = new Intent(Intent.ACTION_VIEW);
		origIconSize = BitsGraphicUtils.dipsToPixels(mAct,origIconSize);
	}
	
	/**
	 * 
	 * @param aImagePath
	 * @param aScaling - -1 for smaller only, 1 for up only, 0 for any scaling.
	 * @param aOutputX
	 * @param aOutputY
	 * @return
	 */
	public Bitmap getImage(String aImagePath, int aScaling, int aOutputX, int aOutputY) {
		File theFile = null;
		if (aImagePath!=null)
			theFile = new File(aImagePath);
		if (theFile!=null && theFile.exists() && theFile.isFile()) {
			try {
				/* orig code
				Bitmap bmOrig = MediaStore.Images.Media.getBitmap(mAct.getContentResolver(),
						BitsFileUtils.getContentUriFromImageFile(mAct,theFile));
				if (aOutputX!=0 && aOutputY!=0)
					return Bitmap.createScaledBitmap(bmOrig,aOutputX,aOutputY,true);
				else
					return bmOrig;
				*/				
				Bitmap bmOrig;
				if (aOutputX!=0 && aOutputY!=0) {
					bmOrig = BitsGraphicUtils.getImage(mAct,theFile,aScaling,aOutputX,aOutputY,true);
				} else {
					bmOrig = new BitmapDrawable(theFile.getPath()).getBitmap();
				}
				return bmOrig;
			} catch (Exception e) {
				return null;
			}
		} else
			return null;
	}

	protected boolean isJumpActivity(ResolveInfo aResolveInfo) {
		return aResolveInfo.activityInfo.name.equals("com.blackmoonit.android.FileBrowser.ViewFileAsJumpPoint");
	}

	public void setFileIcon(final ImageView aFileIcon, final File aFile, int aScaleFactor) {		
		int theScaleFactor = (aScaleFactor<1) ? scaleFactor : aScaleFactor;
		try {
			String theMIMEtype = mMimeMap.guessMIMEtype(aFile.getName());
			String theMIMEcategory = mMimeMap.getMIMEcategory(theMIMEtype);
			boolean isVisualMedia = (theMIMEcategory!=null) &&
				(theMIMEcategory.equals("image/*") || theMIMEcategory.equals("video/*"));
			
			if (theMIMEtype!=null) {
				boolean bMIMEtypeChanged = !theMIMEtype.equals(mLastMIMEtype);
				if (theMIMEtype.equals("application/vnd.android.package-archive")) {
					mLastMIMEicon = null;
					try {
						PackageInfo pi = mPkgMgr.getPackageArchiveInfo(aFile.getPath(),PackageManager.GET_ACTIVITIES);
						pi.applicationInfo.sourceDir = aFile.getPath();
						pi.applicationInfo.publicSourceDir = aFile.getPath();
						mLastMIMEicon = mPkgMgr.getApplicationIcon(pi.applicationInfo);
						if (mLastMIMEicon!=null && 
								(mLastMIMEicon.getIntrinsicHeight()>origIconSize) && 
								(mLastMIMEicon instanceof BitmapDrawable)) {
							mLastMIMEicon = new BitmapDrawable(Bitmap.createScaledBitmap(
									((BitmapDrawable)mLastMIMEicon).getBitmap(),
									origIconSize,origIconSize,true));
						}
					} catch (Exception e) {
						//do not modify the icon if we cannot find any package about it
						mLastMIMEicon = null;					
					}
				} else if (bMIMEtypeChanged && theMIMEtype.equals("application/zip")) {
					mLastMIMEicon = mAct.getResources().getDrawable(R.drawable.item_zip);
				} else if (theMIMEcategory!=null) {
					//use cached icon if same MIMEtype else get icon
					if (bMIMEtypeChanged || mLastMIMEicon==null || 
							(mEnableThumbnails && isVisualMedia) ) {
						mActivityIconIntent.setDataAndType(Uri.fromFile(aFile), theMIMEtype);
						try {
							//mLastMIMEicon = mPkgMgr.getActivityIcon(mActivityIconIntent);
							List<ResolveInfo> appList = mPkgMgr.queryIntentActivities(mActivityIconIntent,PackageManager.MATCH_DEFAULT_ONLY);
							if (appList.size()==0)
								appList = mPkgMgr.queryIntentActivities(mActivityIconIntent,PackageManager.GET_ACTIVITIES);
							if (appList.size()>0) {
								if (!isJumpActivity(appList.get(0)))
									mLastMIMEicon = appList.get(0).loadIcon(mPkgMgr);
								else {
									if (appList.size()>1)
										mLastMIMEicon = appList.get(1).loadIcon(mPkgMgr);
									else
										mLastMIMEicon = null;
								}
							} else 
								mLastMIMEicon = null;
						} catch (Exception e) {
							mLastMIMEicon = null;					
						}
					}
				}
			} else {
				mLastMIMEicon = null;
			}

			boolean bUsedCache = false;
			if (isVisualMedia) {
				if (mEnableThumbnails && mThumbnailCache.containsFile(aFile,theScaleFactor)) {
					mLastMIMEicon = mThumbnailCache.getFile(aFile,theScaleFactor);
					bUsedCache = true;
				}
			}
			
			//icon is now set, if possible, put it in the ImageView
			mLastMIMEtype = theMIMEtype;
			if (mLastMIMEicon!=null) {
				if (mOnSetThumbnail!=null)
					mOnSetThumbnail.onSetThumbnail(aFileIcon,mLastMIMEicon);
				else
					aFileIcon.setImageDrawable(mLastMIMEicon);
			} else {
				if (mOnSetThumbnail!=null)
					mOnSetThumbnail.onSetThumbnail(aFileIcon,mAct.getResources().getDrawable(R.drawable.item_file));
				else
					aFileIcon.setImageResource(R.drawable.item_file);
			}

			if (mEnableThumbnails && !bUsedCache && isVisualMedia) {
				requestThumbnail(aFileIcon,aFile,theScaleFactor);
			}
			
		} catch (Exception e) {
			aFileIcon.setImageResource(R.drawable.item_file);					
		} catch (OutOfMemoryError oomErr) {
			//clear out some cached images
			emptyRecycleBin();
			aFileIcon.setImageResource(R.drawable.item_file);
		}
	}

	/**
	 * Enable or disable the generation of thumbnails.
	 * 
	 * @param b - true to enable, false to disable
	 */
	public void setEnabled(boolean b) {
		if (mEnableThumbnails!=b) {
			mEnableThumbnails = false;
			emptyRecycleBin();
			mEnableThumbnails = b;
			setSuspend(!b);
			if (!b) {
				mLastMIMEicon = null;
				mLastMIMEtype = "";
			}
		}
	}
	
	/**
	 * Start/stop the background thread without affecting anything else.
	 * 
	 * @param b - true will stop the thread, false will start it if enabled.
	 */
	public void setSuspend(boolean b) {
		if (!b && mEnableThumbnails) {
			if (mThumbnailThread==null) {
				mThumbnailThread = new FileIconThumbnailThread(mAct,this);
				mThumbnailThread.start();
			}
		} else if (mThumbnailThread!=null) {
			mThumbnailThread.halt();
			mThumbnailThread = null;
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		setSuspend(true);
		super.finalize();
	}

	public boolean isEnabled() {
		return mEnableThumbnails;
	}
	
	public void requestThumbnail(final ImageView aFileIcon, final File aFile, final int aScaleFactor) {
		if (mThumbnailQueue!=null && aFileIcon!=null && aFile!=null && aFile.canRead()) {
			mThumbnailQueue.offer(new FileIconThumbnailQueueItem(aFileIcon,aFile,
					mMimeMap.guessMIMEtype(aFile),origIconSize,aScaleFactor));
		}
	}
	
	public void addThumbnail(final File aFile, int aScaleFactor, final Drawable aThumbnail) {
		if (mThumbnailCache!=null) {
			mThumbnailCache.putFile(aFile,aScaleFactor,aThumbnail);
		}
	}
	
	public Drawable getThumbnail(final File aFile, int aScaleFactor) {
		if (mThumbnailCache!=null) {
			return mThumbnailCache.getFile(aFile,aScaleFactor);
		} else {
			return null;
		}
	}
	
	public void removeThumbnail(final File aFile) {
		if (aFile!=null) {
			if (mThumbnailRecycleBin!=null) {
				int idx = mThumbnailRecycleBin.indexOf(aFile.getPath());
				if (idx>-1) {
					mThumbnailRecycleBin.remove(idx);
				}
			}
			if (mThumbnailCache!=null) {
				mThumbnailCache.removeFile(aFile,scaleFactor);
			}
			BitsThumbnailUtils.removeThumbnailCacheFile(aFile,scaleFactor);
		}
	}
	
	public boolean isRecycleBinEmpty() {
		if (mThumbnailRecycleBin!=null)
			return (mThumbnailRecycleBin.size()==0);
		else
			return true;
	}

	public void emptyRecycleBin() {
		if (mThumbnailRecycleBin!=null && mThumbnailCache!=null) {
			if (mThumbnailCache.size()>0 && mEnableThumbnails) {
				while (mThumbnailRecycleBin.size()>0) {
					mThumbnailCache.remove(mThumbnailRecycleBin.remove());
				}
			}
			mThumbnailCache.clear();
			mThumbnailRecycleBin.clear();
			System.gc();
		}
	}

	public FileIconThumbnailQueueItem take() throws InterruptedException {
		if (mThumbnailQueue!=null) {
			try {
				return mThumbnailQueue.take();
			} catch (Exception e) {
				/* catch added due to this bug report:
				 * 
				 Blackmoon File Browser generated the following exception:
				 java.lang.ClassCastException:

				 --------- Instruction Stack Trace ---------
				 1. com.blackmoonit.android.FileBrowser.FileIcons.take(FileIcons.java:252)
				 2. com.blackmoonit.android.FileBrowser.FileIconThumbnailThread$1.run(FileIconThumbnailThread.java:33)
				 3. com.blackmoonit.concurrent.BitsThreadDaemon.runTask(BitsThreadDaemon.java:76)
				 4. com.blackmoonit.concurrent.BitsThread.run(BitsThread.java:26)
				 -------------------------------------------

				 -------- Environment --------
				 Time    = 2011.01.02_01.32.12_EST
				 Device  = pt701/pt701/pt701/:Eclair/ECLAIR/eng.root.20101109.085705:eng/test-keys
				 Make    = HTC
				 Model   = pt701
				 Product = pt701
				 App     = com.blackmoonit.android.FileBrowser, version 6.5 (build 85)
				 Locale  = English (United States)
				 -----------------------------
				 * 
				 * No idea why this exception occured and therefore this is just a last ditch effort to
				 * ignore the entry that caused the problem. I cannot find anywhere in the code where
				 * a class other than FileIconThumbnailQueueItem is ever created and placed in this queue.
				 * My only guess is that sometimes null is returned from .take() and it is a bug in the
				 * firmware of the specific phone shown.
				 */
				return null;
			}
		} else {
			return null;
		}
	}
	
	public boolean isQueueEmpty() {
		if (mThumbnailQueue!=null)
			return mThumbnailQueue.isEmpty();
		else
			return true;
	}
	
	public void clear() {
		if (mThumbnailQueue!=null) {
			mThumbnailQueue.clear();
		}
	}

	public void checkRecycle() {
		if (Runtime.getRuntime().freeMemory()<LOW_MEMORY_WARNING) {
			emptyRecycleBin();
		}
	}
	
	public void checkRecycleView(ImageView aView, File aFile) {
		if (mThumbnailRecycleBin!=null && aView!=null && aFile!=null) {
			String oldTag = (String)(aView.getTag());
			String newTag = aFile.getPath();
			aView.setTag(newTag);
			if ((oldTag!=null) && !oldTag.equals(newTag)) {
				mThumbnailRecycleBin.offer(oldTag);
			}
		}
	}
	
	/**
	 * Used by the list/grid view to recycle the IconView during it's onRecycleView callback event.
	 * @param aView - the ImageView containing the icon/thumbnail managed by FileIcons.
	 */
	public void recycleView(ImageView aView) {
		if (mThumbnailRecycleBin!=null && aView!=null) {
			String theTag = (String)(aView.getTag());
			if (theTag!=null) 
				mThumbnailRecycleBin.offer(theTag);
		}
	}

	public void setThumbnail(FileIconThumbnailQueueItem aItem) {
		if (aItem!=null) {
			if (aItem.viewHasNotBeenRecycled()) {
				if (mOnSetThumbnail!=null) {
					mOnSetThumbnail.onSetThumbnail(aItem.iconView,getThumbnail(
							aItem.imageFile,aItem.mThumbnailScaleFactor));
				} else {
					aItem.iconView.setImageDrawable(getThumbnail(
							aItem.imageFile,aItem.mThumbnailScaleFactor));
				}
			} else if (mThumbnailRecycleBin!=null) {
				mThumbnailRecycleBin.offer(aItem.imageFile.getPath());
			}
		}
	}

	public String getScaleFactor() {
		return Integer.toString(scaleFactor);
	}

	public void setScaleFactor(String aNewStringVal) {
		BitsThumbnailUtils.mThumbnailFilePrefix = BitsThumbnailUtils.DEFAULT_THUMBNAIL_FILE_PREFIX+aNewStringVal;
		int theNewValue = (aNewStringVal!=null)?Integer.parseInt(aNewStringVal):scaleFactor;
		if (scaleFactor!=theNewValue) {
			boolean saveEnabled = isEnabled();
			setEnabled(false);
			emptyRecycleBin();
			setEnabled(saveEnabled);
			scaleFactor = theNewValue;
			if (mAct instanceof ListActivity)
				((ListActivity)mAct).getListView().invalidateViews();
		}
		
	}
	
}
