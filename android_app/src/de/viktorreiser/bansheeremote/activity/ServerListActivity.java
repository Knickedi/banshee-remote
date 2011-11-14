package de.viktorreiser.bansheeremote.activity;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.App;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeServer;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask.OnBansheeServerCheck;

/**
 * Choose server from list or create a new one.<br>
 * <br>
 * If chosen server is reachable then the chosen server will be persisted, {@code RESULT_OK} will be
 * set and the activity will be finished. Otherwise {@code RESULT_CANCELED} will be set.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class ServerListActivity extends Activity implements OnItemClickListener,
		OnBansheeServerCheck {
	
	// PRIVATE ====================================================================================
	
	private static final int REQUEST_NEW_SERVER = 1;
	
	private ListView mList;
	private List<BansheeServer> mServer;
	private BansheeServerCheckTask mCheckTask;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.server_list);
		
		mServer = BansheeServer.getServers();
		
		mList = (ListView) findViewById(R.id.list);
		mList.setAdapter(new ServerAdapter());
		mList.setOnItemClickListener(this);
		registerForContextMenu(mList);
		
		setResult(RESULT_CANCELED);
		
		mCheckTask = (BansheeServerCheckTask) getLastNonConfigurationInstance();
		
		if (mCheckTask != null) {
			mCheckTask.showDialog(this);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_NEW_SERVER:
			if (resultCode == RESULT_OK) {
				resetServerSettings();
				mServer = BansheeServer.getServers();
				((ServerAdapter) mList.getAdapter()).notifyDataSetChanged();
			}
			break;
		
		default:
			super.onActivityResult(requestCode, resultCode, data);
			break;
		}
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mCheckTask != null) {
			mCheckTask.dismissDialog();
		}
		
		return mCheckTask;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
		
		if (position != mServer.size()) {
			menu.add(Menu.NONE, 1, Menu.NONE, R.string.edit_server);
			menu.add(Menu.NONE, 2, Menu.NONE, R.string.remove_server);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getItemId() == 1) {
			Intent intent = new Intent(ServerListActivity.this, NewOrEditServerActivity.class);
			intent.putExtra("id", ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).id);
			startActivityForResult(intent, REQUEST_NEW_SERVER);
			return true;
		} else if (item.getItemId() == 2) {
			resetServerSettings();
			long id = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).id;
			BansheeServer.removeServer(id);
			mServer = BansheeServer.getServers();
			((ServerAdapter) mList.getAdapter()).notifyDataSetChanged();
			return true;
		}
		
		return false;
	}
	
	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		if (position == mServer.size()) {
			startActivityForResult(
					new Intent(ServerListActivity.this, NewOrEditServerActivity.class),
					REQUEST_NEW_SERVER);
		} else {
			// check selected server whether it's reachable
			mCheckTask = new BansheeServerCheckTask(mServer.get(position), this);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		menu.add(Menu.NONE, 1, Menu.NONE, R.string.settings)
				.setIcon(R.drawable.settings);
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 1:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onBansheeServerCheck(Integer success) {
		BansheeServer server = mCheckTask.getServer();
		mCheckTask = null;
		
		if (success == 1) {
			// checked server is okay, so persist as default it and return to main activity
			BansheeServer.setDefaultServer(server.getId());
			setResult(RESULT_OK);
			finish();
		} else if (success == 0) {
			App.longToast(R.string.host_denied_password);
		} else {
			App.longToast(R.string.host_not_reachable);
		}
	}
	
	// PRIVATE ====================================================================================
	
	private void resetServerSettings() {
		BansheeDatabase.close();
		CurrentSongActivity.resetConnection();
	}
	
	private class ServerAdapter extends BaseAdapter {
		
		@Override
		public int getCount() {
			return mServer.size() + 1;
		}
		
		@Override
		public Object getItem(int position) {
			return position == mServer.size() ? null : mServer.get(position);
		}
		
		@Override
		public long getItemId(int position) {
			return position == mServer.size() ? -1 : mServer.get(position).getId();
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(
						android.R.layout.simple_list_item_1, null);
			}
			
			if (position == mServer.size()) {
				((TextView) convertView).setText(R.string.new_server);
			} else {
				((TextView) convertView).setText(mServer.get(position).toString());
			}
			
			return convertView;
		}
	}
}
