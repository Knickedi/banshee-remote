package de.viktorreiser.bansheeremote.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.AdapterView;
import de.viktorreiser.bansheeremote.R;

/**
 * Just a couple of global settings.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class SettingsActivity extends PreferenceActivity {
	
	// PRIVATE ====================================================================================
	
	private Preference mWifiPoll;
	private Preference mMobilePoll;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		addPreferencesFromResource(R.xml.settings);
		registerForContextMenu(getListView());
		
		mWifiPoll = findPreference("wifipollinterval");
		mMobilePoll = findPreference("mobilepollinterval");
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		Object listItem = getListView().getAdapter().getItem(
				((AdapterView.AdapterContextMenuInfo) menuInfo).position);
		
		if (listItem == mWifiPoll || listItem == mMobilePoll) {
			new AlertDialog.Builder(this)
					.setTitle(R.string.poll_info)
					.setMessage(R.string.poll_info_message)
					.setPositiveButton(android.R.string.ok, null)
					.show();
		}
	}
}
