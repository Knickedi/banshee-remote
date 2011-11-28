package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Banshee server (manager).<br>
 * <br>
 * An instance provides informations about a single server.<br>
 * <br>
 * This class has static helper methods which helps you to persist the default (chosen) server, get
 * all available servers and add and remove saved servers.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeServer {
	
	// PRIVATE ====================================================================================
	
	private static SQLiteDatabase mDbinstance = null;
	
	private long mId;
	private String mHost;
	private int mPort;
	long mSameHostId = -1;
	long mDbTimestamp = -1;
	int mPasswordId = 0;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Used to create a nice output for {@link #toString()} (not a valid server).
	 * 
	 * @param host
	 *            text to print with {@link #toString()}
	 */
	public BansheeServer(String host) {
		mHost = host;
	}
	
	/**
	 * Create banshee server with host, port and same banshee server ID.
	 * 
	 * @param host
	 *            host of banshee server
	 * @param sameHostId
	 *            ID of same banshee server (see {@link #getSameHostId()})
	 */
	public BansheeServer(String host, long sameHostId) {
		BansheeServer same = getServer(sameHostId);
		
		if (same == null) {
			throw new IllegalArgumentException("same host ID is invalid");
		}
		
		mHost = host;
		mSameHostId = sameHostId;
		mPort = same.mPort;
		mPasswordId = same.mPasswordId;
		mDbTimestamp = same.mDbTimestamp;
	}
	
	/**
	 * Create banshee server with host, port and password ID.
	 * 
	 * @param host
	 *            host of banshee server
	 * @param port
	 *            port of banshee server
	 * @param passwordId
	 *            secret password (number) needed for communication
	 */
	public BansheeServer(String host, int port, int passwordId) {
		mHost = host;
		mPort = port;
		mPasswordId = passwordId;
	}
	
	
	/**
	 * Get internal ID of banshee server.<br>
	 * <br>
	 * Assigned by {@link #addServer(BansheeServer)} and then always returned by
	 * {@link #getServers()} and {@link #getDefaultServer()} used for
	 * {@link #setDefaultServer(long)} and {@link #removeServer(long)}.
	 * 
	 * @return internal ID of banshee server
	 */
	public long getId() {
		return mId;
	}
	
	/**
	 * Get host of banshee server.
	 * 
	 * @return host of banshee server
	 */
	public String getHost() {
		return mHost;
	}
	
	/**
	 * Get port of banshee server.
	 * 
	 * @return port of banshee server
	 */
	public int getPort() {
		return mPort;
	}
	
	/**
	 * Get password ID for banshee server.
	 * 
	 * @return password ID for banshee server
	 */
	public int getPasswordId() {
		return mPasswordId;
	}
	
	/**
	 * Get ID of server which database will be used by this server.
	 * 
	 * @return id of parent server or {@code -1} for none
	 */
	public long getSameHostId() {
		return mSameHostId;
	}
	
	
	/**
	 * Get list of created banshee servers.
	 * 
	 * @return list of created banshee servers
	 * 
	 * @see #getId()
	 */
	public static List<BansheeServer> getServers() {
		List<BansheeServer> server = new ArrayList<BansheeServer>();
		
		Cursor cursor = getDb().query(DB.TABLE_NAME, DB.ALL_COLUMNS,
				null, null, null, null, null);
		
		while (cursor.moveToNext()) {
			server.add(fillFromCursor(cursor));
		}
		
		cursor.close();
		
		return server;
	}
	
	/**
	 * Get server with given ID.
	 * 
	 * @param id
	 *            ID of server
	 * 
	 * @return banshee server with given ID or {@code null} if the ID is invalid
	 */
	public static BansheeServer getServer(long id) {
		BansheeServer s = null;
		
		Cursor cursor = getDb().query(DB.TABLE_NAME, DB.ALL_COLUMNS,
				DB.ID + "=" + id, null, null, null, null);
		
		if (cursor.moveToFirst()) {
			s = fillFromCursor(cursor);
		}
		
		cursor.close();
		return s;
	}
	
	/**
	 * Add a banshee server to list.
	 * 
	 * @param server
	 *            server to add
	 * 
	 * @see #getId()
	 */
	public static void addServer(BansheeServer server) {
		if (server.mId > 0) {
			throw new IllegalArgumentException("given server has already a valid ID");
		}
		
		BansheeServer sameServer = getServer(server.mSameHostId);
		
		if (server.mSameHostId > 0 && sameServer == null) {
			throw new IllegalArgumentException("same host id is invalid");
		}
		
		ContentValues v = new ContentValues();
		v.put(DB.HOST, server.mHost);
		v.put(DB.PORT, sameServer == null ? server.mPort : 0);
		v.put(DB.SAME_ID, server.mSameHostId);
		v.put(DB.PASSWORD_ID, sameServer == null ? server.mPasswordId : 0);
		server.mId = getDb().insert(DB.TABLE_NAME, null, v);
	}
	
	/**
	 * Update server (use that if you really can't use an already existing server only).
	 * 
	 * @param id
	 *            ID of server
	 * @param server
	 *            server to update
	 */
	public static void updateServer(long id, BansheeServer server) {
		if (getServer(id) == null) {
			throw new IllegalArgumentException("given server has no valid ID");
		}
		
		server.mId = id;
		updateServer(server);
	}
	
	/**
	 * Remove banshee server from list (an open databases will be closed after that!)
	 * 
	 * @param id
	 *            ID of server to remove
	 * 
	 * @see #getId()
	 */
	public static void removeServer(long id) {
		BansheeServer dbServer = BansheeDatabase.getServer();
		BansheeDatabase.close();
		BansheeServer server = getServer(id);
		
		if (server == null) {
			throw new IllegalArgumentException("given server has already a valid ID");
		}
		
		List<BansheeServer> childServers = new ArrayList<BansheeServer>();
		
		for (BansheeServer cs : BansheeServer.getServers()) {
			if (cs.mSameHostId == server.mId) {
				childServers.add(cs);
			}
		}
		
		if (!childServers.isEmpty()) {
			if (dbServer != null) {
				BansheeServer firstChild = childServers.get(0);
				
				if (dbServer.mSameHostId == server.mId) {
					new File(App.CACHE_PATH + server.mId + App.DB_EXT).renameTo(
							new File(App.CACHE_PATH + firstChild.mId + App.DB_EXT));
				}
				
				if (dbServer.mId == server.mId) {
					new File(App.CACHE_PATH + server.mId + App.DB_EXT).delete();
				}
				
				for (int i = 0; i < childServers.size(); i++) {
					BansheeServer cs = childServers.get(i);
					cs.mSameHostId = i == 0 ? -1 : firstChild.mId;
					cs.mDbTimestamp = server.mDbTimestamp;
					cs.mPort = server.mPort;
					cs.mPasswordId = server.mPasswordId;
					updateServer(cs);
				}
			}
		} else {
			new File(App.CACHE_PATH + server.mId + App.DB_EXT).delete();
		}
		
		getDb().delete(DB.TABLE_NAME, DB.ID + "=" + server.mId + " OR "
				+ DB.SAME_ID + "=" + DB.ID, null);
	}
	
	/**
	 * Get banshee server which should be used for next connection attempt.
	 * 
	 * @return server which should be used for next connection attempt
	 * 
	 * @see #setDefaultServer(long)
	 */
	public static BansheeServer getDefaultServer() {
		BansheeServer s = null;
		
		Cursor cursor = getDb().query(DB.TABLE_NAME, DB.ALL_COLUMNS,
				DB.DEFAULT + "!=0", null, null, null, null, "1");
		
		if (cursor.moveToNext()) {
			s = fillFromCursor(cursor);
		}
		
		cursor.close();
		return s;
	}
	
	/**
	 * Set banshee server which should be used for next connection attempt.
	 * 
	 * @param id
	 *            ID of server which should be used for next connection attempt
	 * 
	 * @see #getDefaultServer()
	 * @set {@link #getId()}
	 */
	public static void setDefaultServer(long id) {
		getDb().execSQL("UPDATE " + DB.TABLE_NAME + " SET " + DB.DEFAULT
				+ "= (CASE WHEN " + DB.ID + "=" + id + " THEN 1 ELSE 0 END);");
	}
	
	// PACKAGE ====================================================================================
	
	/**
	 * Update server.
	 * 
	 * @param server
	 *            server to update (server should be added with {@link #addServer(BansheeServer)}
	 *            before)
	 */
	static void updateServer(BansheeServer server) {
		BansheeServer sameServer = getServer(server.mSameHostId);
		
		if (server.mSameHostId > 0 && sameServer == null) {
			throw new IllegalArgumentException("same host id is invalid");
		}
		
		ContentValues v = new ContentValues();
		v.put(DB.SAME_ID, sameServer == null ? -1 : server.mSameHostId);
		v.put(DB.DB_TIMESTAMP, sameServer == null ? server.mDbTimestamp : 0);
		v.put(DB.HOST, server.mHost);
		v.put(DB.PORT, sameServer == null ? server.mPort : 0);
		v.put(DB.SAME_ID, server.mSameHostId);
		v.put(DB.PASSWORD_ID, sameServer == null ? server.mPasswordId : 0);
		getDb().update(DB.TABLE_NAME, v, DB.ID + "=" + server.mId, null);
	}
	
	// OVERRIDDEN =================================================================================
	
	/**
	 * Return string representation of banshee server.<br>
	 * <br>
	 * If host is {@code null} then empty.<br>
	 * If port is not set or less than {@code 1024} then only host (text) as string.<br>
	 * When both given then "host : port".
	 */
	@Override
	public String toString() {
		if (mHost == null) {
			return "";
		} else if (mPort <= 1024) {
			return mHost;
		} else {
			return mHost + " : " + mPort;
		}
	}
	
	// PRIVATE ====================================================================================
	
	/**
	 * For internal use.
	 */
	private BansheeServer() {
		
	}
	
	
	/**
	 * Get a banshee server from current cursor position (expects {@link DB#ALL_COLUMNS} as supplied
	 * columns in database query).
	 * 
	 * @param cursor
	 *            cursor pointing at a banshee server
	 * 
	 * @return banshee server
	 */
	private static BansheeServer fillFromCursor(Cursor cursor) {
		BansheeServer s = new BansheeServer();
		s.mId = cursor.getLong(0);
		s.mHost = cursor.getString(1);
		s.mPort = cursor.getInt(2);
		s.mSameHostId = cursor.getLong(3);
		s.mDbTimestamp = cursor.getLong(4);
		s.mPasswordId = cursor.getInt(5);
		
		if (s.mSameHostId > 0) {
			BansheeServer p = getServer(s.mSameHostId);
			
			if (p != null) {
				s.mPasswordId = p.mPasswordId;
				s.mPort = p.mPort;
				s.mDbTimestamp = p.mDbTimestamp;
			} else {
				s.mSameHostId = -1;
			}
		}
		
		return s;
	}
	
	/**
	 * Get database instance for banshee servers.
	 * 
	 * @return database instance
	 */
	private static SQLiteDatabase getDb() {
		if (mDbinstance == null) {
			return mDbinstance = new BansheeDbHelper(App.getContext()).getWritableDatabase();
		} else {
			return mDbinstance;
		}
	}
	
	/**
	 * Helper which creates and setups an empty banshee server database.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private static class BansheeDbHelper extends SQLiteOpenHelper {
		
		public BansheeDbHelper(Context context) {
			super(context, "bansheeserver.db", null, 4);
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + DB.TABLE_NAME + " (\n"
					+ DB.ID + " INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE,\n"
					+ DB.HOST + " TEXT NOT NULL,\n"
					+ DB.PORT + " INTEGER NOT NULL,\n"
					+ DB.SAME_ID + " INTEGER NOT NULL,\n"
					+ DB.DB_TIMESTAMP + " INTEGER NOT NULL DEFAULT 0,\n"
					+ DB.DEFAULT + " INTEGER NOT NULL DEFAULT 0,\n"
					+ DB.PASSWORD_ID + " INTEGER NOT NULL DEFAULT 0"
					+ ");");
		}
		
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			try {
				db.beginTransaction();
				
				switch (oldVersion) {
				case 1:
					// from 1 to 2
					// dbSize changed to dbTimestamp - just discard old value
					// smaeid corrected to sameId
					db.execSQL("ALTER TABLE " + DB.TABLE_NAME
							+ " RENAME TO tmp" + DB.TABLE_NAME + ";");
					onCreate(db);
					db.execSQL("INSERT INTO " + DB.TABLE_NAME + "(" + DB.ID + "," + DB.HOST + ","
							+ DB.PORT + "," + DB.DEFAULT + "," + DB.SAME_ID + ") "
							+ "SELECT " + DB.ID + "," + DB.HOST + "," + DB.PORT + "," + DB.DEFAULT
							+ ",smaeid FROM tmp" + DB.TABLE_NAME + ";");
					db.execSQL("DROP TABLE tmp" + DB.TABLE_NAME + ";");
					
				case 2:
					// from 2 to 3 we have a new column: password id
					db.execSQL("ALTER TABLE " + DB.TABLE_NAME
							+ " ADD COLUMN " + DB.PASSWORD_ID + " INTEGER NOT NULL DEFAULT 0;");
					
					break;
				
				case 3:
					// nothing to do - a fresh creation was just lacking the password ID!
					break;
				}
				
				db.setTransactionSuccessful();
			} catch (Exception e) {
				// failed with upgrade - just create a fresh one and remove everything else
				db.endTransaction();
				
				
				try {
					db.execSQL("DROP TABLE " + DB.TABLE_NAME + ";");
				} catch (Exception e2) {
				}
				
				try {
					db.execSQL("DROP TABLE tmp" + DB.TABLE_NAME + ";");
				} catch (Exception e2) {
				}
				
				onCreate(db);
				
			} finally {
				if (db.inTransaction()) {
					db.endTransaction();
				}
			}
		}
	}
	
	/**
	 * Database (column) constants.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private static class DB {
		public static final String TABLE_NAME = "server";
		public static final String ID = "_id";
		public static final String HOST = "host";
		public static final String PORT = "port";
		public static final String SAME_ID = "sameId";
		public static final String DB_TIMESTAMP = "dbTimestamp";
		public static final String DEFAULT = "isDefault";
		public static final String PASSWORD_ID = "passId";
		
		public static final String [] ALL_COLUMNS = new String [] {
				DB.ID, DB.HOST, DB.PORT, DB.SAME_ID, DB.DB_TIMESTAMP, DB.PASSWORD_ID
		};
	}
}
