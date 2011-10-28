package de.viktorreiser.bansheeremote.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.ArtistInfo;

/**
 * Browse artists from synchronized database.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class ArtistActivity extends Activity {
	
	// PRIVATE ====================================================================================
	
	private int mArtistCount;
	private List<ArtistEntry> mArtistEntries;
	
	// OVERRIDDEN =================================================================================
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.getConnection() == null) {
			finish();
			return;
		}
		
		Object [] data = (Object []) getLastNonConfigurationInstance();
		
		if (data != null) {
			mArtistCount = (Integer) data[0];
			mArtistEntries = (List<ArtistEntry>) data[1];
		} else {
			List<ArtistInfo> artistInfo = BansheeDatabase.getArtistInfo();
			mArtistEntries = new ArrayList<ArtistEntry>();
			mArtistCount = artistInfo.size();
			
			for (int i = 0; i < mArtistCount; i++) {
				ArtistInfo info = artistInfo.get(i);
				
				ArtistEntry artist = new ArtistEntry();
				artist.info = info;
				artist.isAlbum = false;
				mArtistEntries.add(artist);
				
				for (int j = 0; j < info.albumCount; j++) {
					ArtistEntry album = new ArtistEntry();
					album.info = info;
					album.isAlbum = true;
					mArtistEntries.add(album);
				}
			}
		}
		
		setContentView(R.layout.artist);
		
		((TextView) findViewById(R.id.artist_title)).setText(
				getString(R.string.all_artists) + " (" + mArtistCount + ")");
		((ListView) findViewById(R.id.list)).setAdapter(new ArtistAdapter());
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mArtistCount, mArtistEntries};
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		return CurrentSongActivity.handleKeyEvent(e) ? true : super.dispatchKeyEvent(e);
	}
	
	// PRIVATE ====================================================================================
	
	private class ArtistEntry {
		public ArtistInfo info;
		public boolean isAlbum = false;
	}
	
	private static class ViewHolder {
		public TextView title;
		public TextView count;
	}
	
	private class ArtistAdapter extends BaseAdapter {

		@Override
		public int getItemViewType(int position) {
			return mArtistEntries.get(position).isAlbum ? 1 : 0;
		}
		
		@Override
		public int getViewTypeCount() {
			return 2;
		}
		
		@Override
		public int getCount() {
			return mArtistEntries.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				ViewHolder holder = new ViewHolder();
				
				convertView = getLayoutInflater().inflate(R.layout.artist_item, null);
				holder.title = (TextView) convertView.findViewById(R.id.title);
				holder.count = (TextView) convertView.findViewById(R.id.count);
				
				convertView.setTag(holder);
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			
			
			ArtistEntry entry = mArtistEntries.get(position);
			
			if (entry.isAlbum) {
				holder.title.setText("Album name");
				holder.count.setText("(-)");
			} else {
				holder.title.setText(entry.info.name);
				holder.count.setText("(" + entry.info.trackCount + ")");
			}
			
			return convertView;
		}
		
	}
}
