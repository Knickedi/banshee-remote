package de.viktorreiser.bansheeremote.data;

import android.app.Application;
import android.content.Context;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.animation.Interpolator;
import android.widget.Toast;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.toolbox.content.NetworkStateBroadcast;
import de.viktorreiser.toolbox.util.AndroidUtils;
import de.viktorreiser.toolbox.util.L;
import de.viktorreiser.toolbox.widget.HiddenQuickActionSetup;

/**
 * Static application data.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class App extends Application {
	
	// PRIVATE ====================================================================================
	
	private static final int CACHE_DIP_SIZE = 40;
	
	private static Context mContext;
	private static int mCacheSize;
	private static Toast mGlobalToast;
	
	// PUBLIC =====================================================================================
	
	// IDs of list quick actions
	public static final int QUICK_ACTION_ADD = 1;
	public static final int QUICK_ACTION_REMOVE = 2;
	public static final int QUICK_ACTION_ENQUEUE = 3;
	public static final int QUICK_ACTION_ARTIST = 4;
	public static final int QUICK_ACTION_REMOVE_QUEUE = 5;
	
	public static final int PLAYLIST_REMOTE = 1;
	public static final int PLAYLIST_QUEUE = 2;
	
	/** Path to banshee cache folder on SD card. */
	public static final String CACHE_PATH = Environment.getExternalStorageDirectory()
			.getAbsolutePath() + "/BansheeRemote/";
	
	/** Extension of synchronized database file. */
	public static final String DB_EXT = ".sqlite";
	
	
	/**
	 * Get application context
	 * 
	 * @return application context
	 */
	public static Context getContext() {
		return mContext;
	}
	
	/**
	 * Should application remember last used banshee server on next startup?
	 * 
	 * @return {@code true} if last banshee server should be contacted automatically
	 */
	public static boolean isRememberDefaultServer() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("rememberdefault", true);
	}
	
	/**
	 * Should a pause command be sent if phone is receiving a call?
	 * 
	 * @return {@code true} when pause command should be sent
	 */
	public static boolean isStopOnCall() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("stoponcall", false);
	}
	
	/**
	 * Should phone volume keys be used to control banshee volume?
	 * 
	 * @return {@code false} will preserve the default phone volume key function
	 */
	public static boolean isVolumeKeyControl() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("volumekeycontrol", true);
	}
	
	/**
	 * Get banshee server status poll interval?
	 * 
	 * @param forWifi
	 *            {@code true} will return the corresponding value which is used when WiFi is
	 *            enabled, otherwise the value for mobile network will be returned
	 * 
	 * @return poll interval in milliseconds
	 */
	public static long getPollInterval(boolean forWifi) {
		String key = forWifi ? "wifipollinterval" : "mobilepollinterval";
		int defValue = forWifi ? 1 : 5;
		
		return PreferenceManager.getDefaultSharedPreferences(mContext).getInt(key, defValue) * 1000;
	}
	
	/**
	 * Should the song genre be displayed besides the song title?
	 * 
	 * @return {@code true} if genre should be displayed
	 */
	public static boolean isDisplaySongGenre() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("displaysonggenre", true);
	}
	
	/**
	 * Should year be displayed besides the album title?
	 * 
	 * @return {@code true} if year should be displayed
	 */
	public static boolean isDisplayAlbumYear() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("displayalbumyear", true);
	}
	
	/**
	 * Should track rating be displayed as cover overlay?
	 * 
	 * @return {@code true} if track rating should be displayed
	 */
	public static boolean isDisplayRating() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("displayrating", true);
	}
	
	/**
	 * Should cover be fetched from server when using mobile network.
	 * 
	 * @return {@code true} when cover should be fetched
	 */
	public static boolean isMobileNetworkCoverFetch() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("fetchcovermobile", false);
	}
	
	/**
	 * Get the amount of preloaded playlist tracks from server in on cycle.
	 * 
	 * @return count of tracks to preload
	 */
	public static int getPlaylistPreloadCount() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getInt("playlistfetchcount", 50);
	}
	
	/**
	 * Should a compact layout be used for the playlist.
	 * 
	 * @return {@code true} for a compact layout
	 */
	public static boolean isPlaylistCompact() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("compactplaylist", true);
	}
	
	/**
	 * Should possible database out of date hint be shown?
	 * 
	 * @return {@code false} will suppress the hint
	 */
	public static boolean isShowDbOutOfDateHint() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("dboutofdatehint", true);
	}
	
	/**
	 * Is adding same track twice to playlist allowed?
	 * 
	 * @return {@code true} if allowed
	 */
	public static boolean isPlaylistAddTwice() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("playlisttwice", false);
	}
	
	/**
	 * Is adding same track twice to play queue allowed?
	 * 
	 * @return {@code true} if allowed
	 */
	public static boolean isQueueAddTwice() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("queuetwice", true);
	}
	
	/**
	 * Should app reset to main activity when song was clicked to play.
	 * 
	 * @return {@code true} if it should be reset
	 */
	public static boolean isResetOnPlay() {
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("resetonplay", true);
	}
	
	/**
	 * Default and global setup for a quick action (to keep it similar everywhere).
	 * 
	 * @param context
	 * @param add
	 *            should add to playlist be included?
	 * 
	 * @return quick action setup
	 */
	public static HiddenQuickActionSetup getDefaultHiddenViewSetup(Context context) {
		HiddenQuickActionSetup setup = new HiddenQuickActionSetup(context);
		
		setup.setOpenAnimation(new Interpolator() {
			@Override
			public float getInterpolation(float v) {
				v -= 1;
				return v * v * v + 1;
			}
		});
		setup.setCloseAnimation(new Interpolator() {
			@Override
			public float getInterpolation(float v) {
				return v * v * v;
			}
		});
		
		int imageSize = AndroidUtils.dipToPixel(context, 25);
		
		// fix nasty android tiled background bug by manually setting tile option
		Drawable background = App.getContext().getResources().getDrawable(
				R.drawable.quickaction_background);
		((BitmapDrawable) ((LayerDrawable) background).getDrawable(0)).setTileModeXY(
				TileMode.REPEAT, TileMode.REPEAT);
		
		setup.setBackgroundDrawable(background);
		setup.setImageSize(imageSize, imageSize);
		setup.setAnimationSpeed(700);
		// the ease animation is nice but we don't want it to intercept quick action clicks
		setup.setAnimationInteruptionOffset(0.7f);
		setup.setStartOffset(AndroidUtils.dipToPixel(context, 30));
		setup.setStopOffset(AndroidUtils.dipToPixel(context, 50));
		setup.setStickyStart(true);
		setup.setSwipeOnLongClick(true);
		
		return setup;
	}
	
	/**
	 * Get size of cover thumbnail which should be cached in pixel.
	 * 
	 * @return size in pixel
	 */
	public static int getCacheSize() {
		return mCacheSize;
	}
	
	/**
	 * Show global short toast.
	 * 
	 * @param resId
	 *            text of toast message
	 */
	public static void shortToast(int resId) {
		mGlobalToast.cancel();
		mGlobalToast.setDuration(Toast.LENGTH_SHORT);
		mGlobalToast.setText(resId);
		mGlobalToast.show();
	}
	
	/**
	 * Show global long toast.
	 * 
	 * @param resId
	 *            text of toast message
	 */
	public static void longToast(int resId) {
		mGlobalToast.cancel();
		mGlobalToast.setDuration(Toast.LENGTH_LONG);
		mGlobalToast.setText(resId);
		mGlobalToast.show();
	}
	
	/**
	 * Show global short toast.
	 * 
	 * @param text
	 *            text of toast message
	 */
	public static void shortToast(CharSequence text) {
		mGlobalToast.cancel();
		mGlobalToast.setDuration(Toast.LENGTH_SHORT);
		mGlobalToast.setText(text);
		mGlobalToast.show();
	}
	
	/**
	 * Show global long toast.
	 * 
	 * @param text
	 *            text of toast message
	 */
	public static void longToast(CharSequence text) {
		mGlobalToast.cancel();
		mGlobalToast.setDuration(Toast.LENGTH_LONG);
		mGlobalToast.setText(text);
		mGlobalToast.show();
	}
	
	// OVERRIDDEN =================================================================================
	
	/**
	 * Setup static data.
	 */
	@Override
	public void onCreate() {
		mContext = getApplicationContext();
		mGlobalToast = Toast.makeText(mContext, "", 0);
		NetworkStateBroadcast.initialCheck(mContext);
		
		L.setGlobalTag("Banshee Remote");
		L.setLogLevel(L.LVL_VERBOSE);
		L.setLogTraceLevel(L.LVL_VERBOSE);
		L.setLogTraceFormat(true, false, true);
		
		mCacheSize = AndroidUtils.dipToPixel(mContext, CACHE_DIP_SIZE);
	}
}
