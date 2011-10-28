package de.viktorreiser.bansheeremote.activity;

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
		
		if (data == null) {
			mArtistCount = (Integer) data[0];
		} else {
			mArtistCount = BansheeDatabase.getArtistCount();
			
		}
		
		setContentView(R.layout.artist);
		((ListView) findViewById(R.id.list)).setAdapter(new ArtistAdapter());
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mArtistCount};
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		return CurrentSongActivity.handleKeyEvent(e) ? true : super.dispatchKeyEvent(e);
	}
	
	// PRIVATE ====================================================================================
	
	private class ArtistEntry {
		
	}
	
	private static class ViewHolder {
		public TextView title;
		public TextView count;
	}
	
	private class ArtistAdapter extends BaseAdapter {

		@Override
		public int getItemViewType(int position) {
			return 0;//mArtists.get(position) instanceof ExtendedArtistInfo ? 1 : 0;
		}
		
		@Override
		public int getViewTypeCount() {
			return 2;
		}
		
		@Override
		public int getCount() {
			return 0;//mArtists.size();
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
//			ArtistInfo info = mArtists.get(position);
//			
//			if (info != null) {
//				holder.title.setText(info.artistName);
//				holder.count.setText("(" + info.trackCount + ")");
//			} else {
//				holder.title.setText("bla");
//				holder.count.setText("(F)");
//			}
			
			return convertView;
		}
		
	}
}
