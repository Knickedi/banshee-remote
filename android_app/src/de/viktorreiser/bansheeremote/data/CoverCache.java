package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import de.viktorreiser.toolbox.os.SoftPool;

/**
 * Global cover cache pool.<br>
 * <br>
 * You can persist and load covers here. Every loaded cover is cached in memory for quick access.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class CoverCache {
	
	// PRIVATE ====================================================================================
	
	private static SoftPool<Bitmap> mCoverCache = new SoftPool<Bitmap>();
	
	// PUBLIC =====================================================================================
	
	/**
	 * Does cover already exist?
	 * 
	 * @param id
	 *            cover ID returned by server
	 * 
	 * @return {@code true} if cover is available locally
	 */
	public static boolean coverExists(String id) {
		return mCoverCache.get(id.hashCode()) != null
				|| new File(App.BANSHEE_PATH + id + ".jpg").exists();
	}
	
	/**
	 * Get cover.
	 * 
	 * @param id
	 *            cover ID returned by server
	 * 
	 * @return cover as bitmap or {@code null} when there is no cover for given ID
	 */
	public static Bitmap getUnscaledCover(String id) {
		File file = new File(App.BANSHEE_PATH + id + ".jpg");
		
		if (file.exists()) {
			return BitmapFactory.decodeFile(file.getAbsolutePath());
		} else {
			return null;
		}
	}
	
	public static Bitmap getThumbnailedCover(String id) {
		int hash = id.hashCode();
		Bitmap cover = mCoverCache.get(hash);
		
		if (cover != null) {
			return cover;
		}
		
		File file = new File(App.BANSHEE_PATH + id + ".jpg");
		
		if (file.exists()) {
			BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), o);
            
            int width = o.outWidth;
            int height = o.outHeight;
            int required = App.getCacheSize();
            int scale = 1;
            
            while(width / 2 > required || height / 2 > required){
                width /= 2;
                height /= 2;
                scale *= 2;
            }
            
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
			cover = BitmapFactory.decodeFile(file.getAbsolutePath(), o2);
			
			if (cover != null) {
				mCoverCache.put(hash, cover);
			}
		}
		
		return cover;
	}
	
	/**
	 * Put cover into cache.
	 * 
	 * @param id
	 *            cover ID for which the cover should be persisted
	 * @param bitmapData
	 *            image data as raw byte array
	 * 
	 * @return cover as bitmap or {@code null} if failed to persist or the given data was invalid
	 */
	public static boolean addCover(String id, byte [] bitmapData) {
		Bitmap cover = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length);
		
		if (cover != null) {
			mCoverCache.put(id.hashCode(), cover);
			
			try {
				File file = new File(App.BANSHEE_PATH + id + ".jpg");
				file.getParentFile().mkdir();
				file.delete();
				cover.compress(CompressFormat.JPEG, 80, new FileOutputStream(file));
				
				return true;
			} catch (FileNotFoundException e) {
				// too bad, we have a valid cover but failed to persist
			}
		}
		
		return false;
	}
}
