package de.viktorreiser.bansheeremote.activity;

import android.app.Activity;
import android.os.Bundle;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;

/**
 * Here we will load the current player play list, show it and interacti with it.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class PlaylistActivity extends Activity implements OnBansheeCommandHandle {
	
	// PRIVATE ====================================================================================
	
	private OnBansheeCommandHandle mOldCommandHandler;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.mConnection == null) {
			finish();
			return;
		}
		
		mOldCommandHandler = CurrentSongActivity.mConnection.getHandleCallback();
		CurrentSongActivity.mConnection.updateHandleCallback(new OnBansheeCommandHandle() {
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				PlaylistActivity.this.onBansheeCommandHandled(command, params, result);
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
			}
		});
		
		setContentView(R.layout.playlist);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mConnection.updateHandleCallback(mOldCommandHandler);
		}
	}
	
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		if (command == null) {
			return;
		}
		
		if (command == Command.COVER) {
			
		}
	}
}
