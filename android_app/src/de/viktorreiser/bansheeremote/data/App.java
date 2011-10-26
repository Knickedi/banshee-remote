package de.viktorreiser.bansheeremote.data;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.animation.Interpolator;
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
	
	private static Context mContext;
	
	// PUBLIC =====================================================================================
	
	public static final int QUICK_ACTION_ADD = 1;
	public static final int QUICK_ACTION_REMOVE = 2;
	public static final int QUICK_ACTION_ENQUEUE = 3;
	
	
	public static final String BANSHEE_PATH = Environment.getExternalStorageDirectory()
			.getAbsolutePath() + "/BansheeRemote/";
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
	 * Default and global setup for a quick action (to keep it similar everywhere).
	 * 
	 * @param context
	 * @param add
	 *            should add to playlist be included?
	 * 
	 * @return quick action setup
	 */
	public static HiddenQuickActionSetup getDefaultHiddenViewSetup(
			Context context, boolean add, boolean remove) {
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
		
		setup.setBackgroundResource(R.drawable.quickaction_background);
		setup.setImageSize(imageSize, imageSize);
		setup.setAnimationSpeed(700);
		// the ease animation is nice but we don't want it to intercept quick action clicks
		setup.setAnimationInteruptionOffset(0.7f);
		setup.setStartOffset(AndroidUtils.dipToPixel(context, 30));
		setup.setStopOffset(AndroidUtils.dipToPixel(context, 50));
		setup.setStickyStart(true);
		setup.setSwipeOnLongClick(true);
		
		setup.addAction(QUICK_ACTION_ENQUEUE, R.string.quick_enqueue, R.drawable.enqueue);
		
		if (add) {
			setup.addAction(QUICK_ACTION_ADD, R.string.quick_add, R.drawable.add);
		}
		
		if (remove) {
			setup.addAction(QUICK_ACTION_REMOVE, R.string.quick_remove, R.drawable.remove);
		}
		
		return setup;
	}
	
	// OVERRIDDEN =================================================================================
	
	/**
	 * Setup static data.
	 */
	@Override
	public void onCreate() {
		mContext = getApplicationContext();
		NetworkStateBroadcast.initialCheck(mContext);
		
		L.setGlobalTag("Banshee Remote");
		L.setLogLevel(L.LVL_VERBOSE);
		L.setLogTraceLevel(L.LVL_VERBOSE);
		L.setLogTraceFormat(true, false, true);
	}
}
