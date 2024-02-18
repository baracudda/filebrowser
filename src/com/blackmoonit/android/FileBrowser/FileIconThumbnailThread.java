package com.blackmoonit.android.FileBrowser;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.blackmoonit.concurrent.BitsThreadDaemon;
import com.blackmoonit.graphics.BitsGraphicUtils;
import com.blackmoonit.media.BitsThumbnailUtils;

/**
 * Thumbnail thread used to generate live thumbnails and place them in their appropriate views.
 * 
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2010
 */
public class FileIconThumbnailThread extends BitsThreadDaemon {
	private final WeakReference<Activity> mApp;
	private final FileIcons mFileIcons;

	public FileIconThumbnailThread(Activity aApp, FileIcons aFileIcons) {
		super(0L);
		//setProcessPriority(MIN_PRIORITY+1);
		setProcessName("FileIconThumbnails");
		mTask = new Runnable() {
			@Override
			public void run() {
				try {
					if (mApp!=null && mApp.get()!=null && mFileIcons!=null) {
						consume(mFileIcons.take());
					}
				} catch (InterruptedException e) {
					//watches the queue indefinately, stop watching if interrupted
				}
			}
		};
		mApp = new WeakReference<Activity>(aApp);
		mFileIcons = aFileIcons;
	}
	
    /**
     * Gets a video frame to represent it as a thumbnail.
     * 
     * @param aItem - queue item representing the video file
     * @return The bitmap to be used or null if none was found.
     */
	private Bitmap getVideoFrame(FileIconThumbnailQueueItem aItem) {
		if (aItem==null)
			return null;
		/* cannot use this code for anything less than Android 2.0
		int thumbKind = (mFileIcons.scaleFactor<3)?BitsLegacy.MICRO_THUMBNAIL_KIND:BitsLegacy.MINI_THUMBNAIL_KIND;
		Long theId = BitsFileUtils.getVisualMediaFileContentId(mApp,aItem.imageFile,
				Video.Media.EXTERNAL_CONTENT_URI,Video.Media._ID,Video.Media.DATA);
		if (theId!=null) {
			options=new BitmapFactory.Options();
			options.inSampleSize = 1;
			return MediaStore.Video.Thumbnails.getThumbnail(mApp.getContentResolver(),theId,thumbKind,options);
		} else
			return null;
		*/
		return BitsThumbnailUtils.getVideoThumbnail(aItem.imageFile);
	}	

	protected Drawable getThumbnail(FileIconThumbnailQueueItem aItem) {
		//using some trickery to ensure Grid icons are always small, but GridDetail and others aren't
		//int theEffectiveScaleFactor = Math.max(1,(mFileIcons.scaleFactor*aItem.mThumbnailScaleFactorOverride));
		int theEffectiveScaleFactor = aItem.mThumbnailScaleFactor;
		Drawable theResult = BitsThumbnailUtils.loadThumbnailFromCache(aItem.imageFile,theEffectiveScaleFactor);
		if (theResult!=null)
			return theResult;
		try {
			Bitmap bmOrig = null;
			if (aItem.mimeCategory.equals("image/*")) {
				int theThumbSize = aItem.imageSize * theEffectiveScaleFactor;
				bmOrig = BitsGraphicUtils.getImage(mApp.get(),aItem.imageFile,-1,theThumbSize,theThumbSize,true);
			} else if (aItem.mimeCategory.equals("video/*")) {
				bmOrig = getVideoFrame(aItem);
				if (bmOrig!=null) {
					boolean bFilter = false; //icon sized, not much to filter anyway, so go for speed
					//modify the size of the thumbnail according to settings
					int newW = aItem.imageSize*theEffectiveScaleFactor;
					int newH = newW;
					if (theEffectiveScaleFactor>1) {
						int origW = Math.max(bmOrig.getWidth(),1);	//prevent /0 on next line
						int origH = Math.max(bmOrig.getHeight(),1); //prevent /0 on next line
						float p = Math.min((float)newW/origW,(float)newH/origH);
						newW = Math.min(Math.round(origW*p),origW);
						newH = Math.min(Math.round(origH*p),origH);
						if (theEffectiveScaleFactor>=4)
							bFilter = true; //larger thumbnails should look better
					}
					Thread.yield();
					bmOrig = Bitmap.createScaledBitmap(bmOrig,newW,newH,bFilter);
				}
			}
			Thread.yield();
			if (bmOrig!=null) {
				theResult = new BitmapDrawable(bmOrig);
				BitsThumbnailUtils.saveThumbnailToCache(aItem.imageFile,(BitmapDrawable)theResult,theEffectiveScaleFactor);
				return theResult;
			} else
				return null;
		} catch (Exception e) {
			return null;
		}
	}

	void consume(final FileIconThumbnailQueueItem aItem) {
		final Activity theAct = mApp.get();
		try {
			if (mFileIcons.isEnabled()) {
				if (aItem.viewHasNotBeenRecycled()) {
					if (theAct!=null) { 
						theAct.runOnUiThread(new Runnable() { 
							@Override
							public void run() {
								theAct.setProgressBarIndeterminateVisibility(true);
							} 
						});
					}
					final Drawable theThumbnail = getThumbnail(aItem);
					if (theThumbnail!=null) {
						mFileIcons.checkRecycle();
						mFileIcons.addThumbnail(aItem.imageFile,aItem.mThumbnailScaleFactor,theThumbnail);
						Thread.yield();
						//Activity theAct = mApp.get();
						if (theAct!=null) { 
							theAct.runOnUiThread(new Runnable() { 
								@Override
								public void run() {
									mFileIcons.setThumbnail(aItem);
								}
							});
						}
					}
					if (theAct!=null) { 
						theAct.runOnUiThread(new Runnable() { 
							@Override
							public void run() {
								theAct.setProgressBarIndeterminateVisibility(false);
							} 
						});
					}
				}
			} else {
				mFileIcons.checkRecycle();
			}
		} catch (Exception e) {
			//do not care if there is an exception
		} catch (OutOfMemoryError oomErr) {
			if (mFileIcons.isRecycleBinEmpty()) {//if nothing to recycle, scale down the thumbnail size
				mFileIcons.scaleFactor = Math.max(mFileIcons.scaleFactor/2,1);
				if (aItem.mThumbnailScaleFactor>1) {
					aItem.mThumbnailScaleFactor = mFileIcons.scaleFactor;
				}
			} else
				mFileIcons.emptyRecycleBin();
			if (aItem.viewHasNotBeenRecycled()) {
				mFileIcons.requestThumbnail(aItem.iconView,aItem.imageFile,aItem.mThumbnailScaleFactor);
			}
		} catch (Error e) {
			//also do not care, just do not create one
		}
	}

}
