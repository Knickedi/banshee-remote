package de.viktorreiser.bansheeremote.activity;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.toolbox.util.L;

public class PlaylistOverviewActivity extends Activity implements OnBansheeCommandHandle {

	// PRIVATE ====================================================================================
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private ListView mList;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.mConnection == null) {
			finish();
			return;
		}
		
		Object [] dataBefore = (Object []) getLastNonConfigurationInstance();
		
		if (dataBefore == null) {
			CurrentSongActivity.mConnection.sendCommand(Command.PLAYLIST,
					Command.Playlist.encodePlaylistNames());
		}
		
		mOldCommandHandler = CurrentSongActivity.mConnection.getHandleCallback();
		CurrentSongActivity.mConnection.updateHandleCallback(new OnBansheeCommandHandle() {
			@Override
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				PlaylistOverviewActivity.this.onBansheeCommandHandled(command, params, result);
			}
		});
		
		setContentView(R.layout.playlist_overview);
		
		mList = (ListView) findViewById(R.id.list);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mConnection.updateHandleCallback(mOldCommandHandler);
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {};
	}

	@Override
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		switch (command) {
		case PLAYLIST:
			if (Command.Playlist.isPlaylistNames(params)) {
				Object [][] playlists = Command.Playlist.decodePlaylistNames(result);
				L.d("Active: " + Command.Playlist.decodeActivePlaylist(result));
				
				for (int i = 0; i < playlists.length; i++) {
					L.d(playlists[i][0] + " " + playlists[i][1]);
				}
			}
			break;
		
		default:
			break;
		}
	}
}
