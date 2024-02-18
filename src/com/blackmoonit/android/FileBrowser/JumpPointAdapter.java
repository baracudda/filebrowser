package com.blackmoonit.android.FileBrowser;

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.blackmoonit.widget.BitsCursorAdapter;

/**
 * Adapter for the grid/list view of jump points.
 *
 * @author Ryan Fischbach<br>Blackmoon Info Tech Services - &copy;2011
 */
public class JumpPointAdapter extends BitsCursorAdapter {
	protected final FileIcons mFileIcons;
	protected final Drawable mFolderIcon;

	public JumpPointAdapter(Context aContext, int aItemLayoutResId,
			Cursor aCursor, String[] aColumnNames, int[] aColumnViewResIds, FileIcons aFileIcons) {
		super(aContext, aItemLayoutResId, aCursor, aColumnNames, aColumnViewResIds);
		mResizableTextViews = new int[] {
				R.id.fb_cell_filename
		};
		mFileIcons = aFileIcons;
		mFolderIcon = aContext.getResources().getDrawable(R.drawable.item_folder);
	}

	@Override
	public View applyItemView(Cursor aCursor, View anItemView) {
		applyFileIcon(aCursor,(ImageView)getViewHandle(anItemView,R.id.ImageFileIcon));
		return anItemView;
	}

	@Override
	public View newView(Context aContext, Cursor aCursor, ViewGroup aParent) {
		View theResult = super.newView(aContext, aCursor, aParent);
		View v = (ImageView)getViewHandle(theResult,R.id.ImageFileIcon);
		((View)v.getParent()).setTag("JumpPoint");
		return theResult;

	}

	public void applyFileIcon(final Cursor aCursor, ImageView v) {
		File theFile = new File(aCursor.getString(aCursor.getColumnIndex(JumpPointProvider.JUMP_DATA)));
		mFileIcons.checkRecycleView(v,theFile);	
		if (theFile.isDirectory()) {
			if (mFileIcons.mOnSetThumbnail!=null)
				mFileIcons.mOnSetThumbnail.onSetThumbnail(v,mFolderIcon);
			else
				v.setImageResource(R.drawable.item_folder);
		} else {
			mFileIcons.setFileIcon(v,theFile,1);
		}
	}
	
}
