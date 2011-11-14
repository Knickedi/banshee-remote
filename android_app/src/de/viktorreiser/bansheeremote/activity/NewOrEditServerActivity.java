package de.viktorreiser.bansheeremote.activity;

import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.App;
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
public class NewOrEditServerActivity extends Activity implements OnBansheeServerCheck {
	
	// PRIVATE ====================================================================================
	
	private EditText mHost;
	private EditText mPort;
	private Spinner mSameHost;
	private EditText mPassword;
	private List<BansheeServer> mServer;
	private BansheeServerCheckTask mCheckTask;
	private long mEditId = -1;
	
	// OVERRIDDEN =================================================================================
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.new_or_edit_server);
		
		mServer = BansheeServer.getServers();
		mEditId = getIntent().getLongExtra("id", -1);
		
		mHost = (EditText) findViewById(R.id.host);
		mPort = (EditText) findViewById(R.id.port);
		mSameHost = (Spinner) findViewById(R.id.same_host);
		mPassword = (EditText) findViewById(R.id.password_id);
		
		setupSameHostChangeListener();
		setupFinishButtonListener();
		
		setResult(RESULT_CANCELED);
		
		mCheckTask = (BansheeServerCheckTask) getLastNonConfigurationInstance();
		
		if (mCheckTask != null) {
			mCheckTask.showDialog(this);
		}
		
		BansheeServer none = new BansheeServer(getString(R.string.own_db));
		mServer.add(0, none);
		ArrayAdapter<BansheeServer> a = new ArrayAdapter<BansheeServer>(
				this, android.R.layout.simple_spinner_item, mServer);
		a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSameHost.setAdapter(a);
		
		setupEditIfPossible();
		
		if (mServer.size() == 1) {
			mSameHost.setVisibility(View.GONE);
			findViewById(R.id.same_host_text).setVisibility(View.GONE);
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
	public void onBansheeServerCheck(Integer success) {
		BansheeServer server = mCheckTask.getServer();
		mCheckTask = null;
		
		if (success == 1) {
			if (mEditId > 0) {
				BansheeServer.updateServer(mEditId, server);
			} else {
				BansheeServer.addServer(server);
			}
			setResult(RESULT_OK);
			finish();
		} else if (success == 0) {
			App.longToast(R.string.host_denied_password);
		} else {
			App.longToast(R.string.host_not_reachable);
		}
	}
	
	// PRIVATE ====================================================================================
	
	private void setupSameHostChangeListener() {
		mSameHost.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> a, View v, int p, long id) {
				mPassword.setEnabled(p == 0);
				mPassword.setFocusable(p == 0);
				mPassword.setFocusableInTouchMode(p == 0);
				mPort.setEnabled(p == 0);
				mPort.setFocusable(p == 0);
				mPort.setFocusableInTouchMode(p == 0);
				
				if (p != 0) {
					mPassword.setText(mServer.get(p).getPasswordId() + "");
					mPort.setText(mServer.get(p).getPort() + "");
				}
			}
			
			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				
			}
		});
	}
	
	private void setupFinishButtonListener() {
		findViewById(R.id.create).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				int port = 8484;
				int passwordId = 0;
				String host = mHost.getText().toString();
				long sameId = ((BansheeServer) mSameHost.getSelectedItem()).getId();
				
				try {
					port = Integer.parseInt(mPort.getText().toString());
				} catch (NumberFormatException e) {
				}
				
				try {
					passwordId = Integer.parseInt(mPassword.getText().toString());
				} catch (NumberFormatException e) {
				}
				
				BansheeServer server;
				
				if (sameId > 0) {
					server = new BansheeServer(host, sameId);
				} else {
					server = new BansheeServer(host, port, passwordId);
				}
				
				mCheckTask = new BansheeServerCheckTask(server, NewOrEditServerActivity.this);
			}
		});
	}
	
	private void setupEditIfPossible() {
		BansheeServer server = BansheeServer.getServer(mEditId);
		
		if (server == null) {
			mEditId = -1;
		} else {
			((Button) findViewById(R.id.create)).setText(R.string.save_changes);
			((TextView) findViewById(R.id.title)).setText(R.string.edit_server);
			mHost.setText(server.getHost());
			mPort.setText(server.getPort() + "");
			mPassword.setText(server.getPasswordId() + "");
			
			for (int i = 0; i < mServer.size(); i++) {
				long id = mServer.get(i).getId();
				
				if (server.getId() == id || mServer.get(i).getSameHostId() > 0) {
					mServer.remove(i);
					i--;
				} else if (server.getSameHostId() == id) {
					mSameHost.setSelection(i);
					mHost.setImeOptions(EditorInfo.IME_ACTION_DONE);
					mPassword.setEnabled(i == 0);
					mPassword.setFocusable(i == 0);
					mPassword.setFocusableInTouchMode(i == 0);
					mPort.setEnabled(i == 0);
					mPort.setFocusable(i == 0);
					mPort.setFocusableInTouchMode(i == 0);
				}
			}
		}
	}
}
