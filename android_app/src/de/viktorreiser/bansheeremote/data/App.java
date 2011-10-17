package de.viktorreiser.bansheeremote.data;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.preference.PreferenceManager;

/**
 * Static application data.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class App extends Application {
	
	// PRIVATE ====================================================================================
	
	private static Context mContext;
	
	// PUBLIC =====================================================================================
	
	public static final String BANSHEE_PATH = Environment.getExternalStorageDirectory()
			.getAbsolutePath() + "/BansheeRemote/";
	
	
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
	public static boolean isMobileNetworkCoverFetch(){
		return PreferenceManager.getDefaultSharedPreferences(mContext)
				.getBoolean("fetchcovermobile", false);
	}
	
	// OVERRIDDEN =================================================================================
	
	/**
	 * Setup static data.
	 */
	@Override
	public void onCreate() {
		mContext = getApplicationContext();
	}
}
