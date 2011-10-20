package de.viktorreiser.bansheeremote.activity;

import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeServer;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask;
import de.viktorreiser.bansheeremote.data.BansheeServerCheckTask.OnBansheeServerCheck;

/**
 * Create a new server (if it is valid and reachable) and add it to server list.<br>
 * <br>
 * When a server is successfully added then {@code RESULT_OK} is set, otherwise
 * {@code RESULT_CANCELED}.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class NewServerActivity extends Activity implements OnBansheeServerCheck {
	
	// PRIVATE ====================================================================================
	
	private EditText mHost;
	private EditText mPort;
	private Spinner mSameHost;
	private List<BansheeServer> mServer;
	private BansheeServerCheckTask mCheckTask;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.new_server);
		
		mServer = BansheeServer.getServers();
		
		mHost = (EditText) findViewById(R.id.host);
		mPort = (EditText) findViewById(R.id.port);
		mSameHost = (Spinner) findViewById(R.id.same_host);
		
		findViewById(R.id.create).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				int port = 8484;
				
				try {
					port = Integer.parseInt(mPort.getText().toString());
				} catch (NumberFormatException e) {
				}
				
				BansheeServer server = new BansheeServer(
						((BansheeServer) mSameHost.getSelectedItem()).getId(),
						mHost.getText().toString(), port);
				
				mCheckTask = new BansheeServerCheckTask(server, NewServerActivity.this);
			}
		});
		
		setResult(RESULT_CANCELED);
		
		mCheckTask = (BansheeServerCheckTask) getLastNonConfigurationInstance();
		
		if (mCheckTask != null) {
			mCheckTask.showDialog(this);
		}
		
		if (mServer.size() == 0) {
			mSameHost.setVisibility(View.GONE);
			findViewById(R.id.same_host_text).setVisibility(View.GONE);
		}
		
		BansheeServer none = new BansheeServer(getString(R.string.own_db));
		mServer.add(0, none);
		ArrayAdapter<BansheeServer> a = new ArrayAdapter<BansheeServer>(
				this, android.R.layout.simple_spinner_item, mServer);
		a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSameHost.setAdapter(a);
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mCheckTask != null) {
			mCheckTask.dismissDialog();
		}
		
		return mCheckTask;
	}
	
	@Override
	public void onBansheeServerCheck(boolean success) {
		BansheeServer server = mCheckTask.getServer();
		mCheckTask = null;
		
		if (success) {
			// created server is valid, persist it and leave activity
			BansheeServer.addServer(server);
			setResult(RESULT_OK);
			finish();
		} else {
			Toast.makeText(this, R.string.host_not_reachable, Toast.LENGTH_LONG).show();
		}
	}
}
