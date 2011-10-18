package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.TrackInfo;

/**
 * Here we will load the current player play list, show it and interacti with it.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class PlaylistActivity extends Activity implements OnBansheeCommandHandle {
	
	// PRIVATE ====================================================================================
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private boolean mLoadingDismissed;
	private List<TrackInfo> mPlaylist;
	private PlaylistAdapter mAdapter;
	
	// OVERRIDDEN =================================================================================
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.mConnection == null) {
			finish();
			return;
		}
		
		Object [] dataBefore  = (Object []) getLastNonConfigurationInstance();
		
		if (dataBefore != null) {
			mOldCommandHandler = (OnBansheeCommandHandle) dataBefore[0];
			mPlaylist = (List<TrackInfo>) dataBefore[1];
			mLoadingDismissed = (Boolean) dataBefore[2];
		} else {
			mLoadingDismissed = false;
			mPlaylist = new ArrayList<TrackInfo>();
			
			mOldCommandHandler = CurrentSongActivity.mConnection.getHandleCallback();
			CurrentSongActivity.mConnection.updateHandleCallback(new OnBansheeCommandHandle() {
				public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
					PlaylistActivity.this.onBansheeCommandHandled(command, params, result);
					mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				}
			});
			
			CurrentSongActivity.mConnection.sendCommand(Command.PLAYLIST, null);
		}
		
		setContentView(R.layout.playlist);
		
		mAdapter = new PlaylistAdapter();
		((ListView) findViewById(R.id.list)).setAdapter(mAdapter);
		
		if (mLoadingDismissed) {
			findViewById(R.id.loading_progress).setVisibility(View.GONE);
			
			if (mPlaylist.size() == 0) {
				findViewById(R.id.no_entries).setVisibility(View.VISIBLE);
			}
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (CurrentSongActivity.mConnection != null) {
			CurrentSongActivity.mConnection.updateHandleCallback(mOldCommandHandler);
		}
	}
	
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mOldCommandHandler, mPlaylist, mLoadingDismissed};
	}
	
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		if (command == null) {
			return;
		}
		
		if (command == Command.COVER) {
			
		} else if (command == Command.PLAYLIST) {
			if (result != null) {
				long [] ids = Command.Playlist.decode(result);
				mPlaylist = new ArrayList<TrackInfo>(ids.length);
				
				for (int i = 0; i < ids.length; i++) {
					mPlaylist.add(BansheeDatabase.getTrackInfo(ids[i]));
				}
			}
			
			mLoadingDismissed = true;
			findViewById(R.id.loading_progress).setVisibility(View.GONE);
			
			if (mPlaylist.size() == 0) {
				findViewById(R.id.no_entries).setVisibility(View.VISIBLE);
			} else {
				mAdapter.notifyDataSetChanged();
			}
		}
	}
	
	// PRIVATE ====================================================================================
	
	private class PlaylistAdapter extends BaseAdapter {

		public int getCount() {
			return mPlaylist.size();
		}

		public Object getItem(int position) {
			return mPlaylist.get(position);
		}

		public long getItemId(int position) {
			return mPlaylist.get(position).id;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = new TextView(PlaylistActivity.this);
			}
			
			((TextView) convertView).setText(position + "");
			
			return convertView;
		}
		
	}
}
