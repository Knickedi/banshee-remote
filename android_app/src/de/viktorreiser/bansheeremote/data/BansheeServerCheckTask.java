package de.viktorreiser.bansheeremote.data;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import de.viktorreiser.bansheeremote.R;

/**
 * General asynchronous banshee server availability checker.<br>
 * <br>
 * An activity should implement {@link OnBansheeServerCheck} and start the check with
 * {@link #BansheeServerCheckTask(BansheeServer, Activity)}. A dialog will be shown and dismissed on
 * finish. The task will call the callback on the given activity.<br>
 * <br>
 * See {@link #showDialog(Activity)} for some more information.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeServerCheckTask extends AsyncTask<Void, Void, Integer> {
	
	// PRIVATE ====================================================================================
	
	private WeakReference<Activity> mCallback;
	private ProgressDialog mDialog;
	private BansheeServer mServer;
	
	// PUBLIC =====================================================================================
	
	public interface OnBansheeServerCheck {
		
		/**
		 * Server test request response.
		 * 
		 * @param success
		 *            {@code -1} not reachable, {@code 0} reachable but wrong password, {@code 1}
		 *            everything okay
		 */
		public void onBansheeServerCheck(Integer success);
	}
	
	
	/**
	 * Create a banshee server check task.
	 * 
	 * @param server
	 *            banshee server to check
	 * @param callback
	 *            the callback which will be informed when check task finishes - this object is a
	 *            activity also so it can be used to construct the progress dialog
	 */
	public <T extends Activity & OnBansheeServerCheck> BansheeServerCheckTask(BansheeServer server,
			T callback) {
		mServer = server;
		showDialog(callback);
		execute();
	}
	
	/**
	 * Update the callback and show dialog again.<br>
	 * <br>
	 * This is a helper to manage orientation changes as long the check task is still running. You
	 * call {@link #dismissDialog()} when the activity is destroy, retain this task, load it again
	 * in when the new activity is created and call then this method to indicate that the task is
	 * still running.
	 * 
	 * @param callback
	 *            the callback which will be informed when check task finishes - this object is a
	 *            activity also so it can be used to construct the progress dialog
	 */
	public <T extends Activity & OnBansheeServerCheck> void showDialog(T callback) {
		mCallback = new WeakReference<Activity>(callback);
		
		mDialog = new ProgressDialog(callback);
		mDialog.setMessage(callback.getResources().getString(R.string.checking_server));
		mDialog.setIndeterminate(true);
		mDialog.setCancelable(false);
		mDialog.show();
	}
	
	/**
	 * Dismiss the displayed dialog.
	 * 
	 * @see #showDialog(Activity)
	 */
	public void dismissDialog() {
		mDialog.dismiss();
	}
	
	/**
	 * Get the checked banshee server.
	 * 
	 * @return checked banshee server
	 */
	public BansheeServer getServer() {
		return mServer;
	}
	
	// OVERRIDDEN =================================================================================
	
	@Override
	protected Integer doInBackground(Void... params) {
		return BansheeConnection.checkConnection(mServer);
	}
	
	@Override
	protected void onPostExecute(Integer result) {
		try {
			mDialog.dismiss();
		} catch (Exception e) {
			// this is causing a IllegalArgumentException (view not attached to window manager)
			// I can't tell why, always when returning to application
			e.printStackTrace();
		}
		
		Activity a = mCallback.get();
		
		if (a == null) {
			return;
		}
		
		((OnBansheeServerCheck) a).onBansheeServerCheck(result);
	}
}
