package de.viktorreiser.bansheeremote.activity;

import de.viktorreiser.bansheeremote.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Just a couple of global settings.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class SettingsActivity extends PreferenceActivity {
	
	// OVERRIDDEN =================================================================================
	
	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		addPreferencesFromResource(R.xml.settings);
	}
}
