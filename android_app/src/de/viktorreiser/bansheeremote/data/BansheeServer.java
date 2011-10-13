package de.viktorreiser.bansheeremote.data;

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
 * all available server and add and remove saved servers.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeServer {
	
	// PRIVATE ====================================================================================
	
	private static SQLiteDatabase mDbinstance = null;
	
	private long mId;
	private long mSameHostId = -1;
	private String mHost;
	private int mPort;
	private long mDbSize = -1;
	
	// PUBLIC =====================================================================================
	
	/**
	 * Used to create a nice output for {@link #toString()}.
	 * 
	 * @param host
	 *            text to print with {@link #toString()}
	 */
	public BansheeServer(String host) {
		mHost = host;
	}
	
	/**
	 * Create banshee server with host and port.
	 * 
	 * @param host
	 *            host of banshee server
	 * @param port
	 *            port of banshee server
	 */
	public BansheeServer(String host, int port) {
		mHost = host;
		mPort = port;
	}
	
	/**
	 * Create banshee server with host, port and same banshee server ID.
	 * 
	 * @param sameHostId
	 *            ID of same banshee server (see {@link #getSameHostId()})
	 * @param host
	 *            host of banshee server
	 * @param port
	 *            port of banshee server
	 */
	public BansheeServer(long sameHostId, String host, int port) {
		mSameHostId = sameHostId;
		mHost = host;
		mPort = port;
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
	 * Get ID of banshee server which represents the same server.<br>
	 * <br>
	 * For example you could define several different addresses (maybe LAN and Internet) for a
	 * single banshee server but you won't that they share the same persisted data. So you define
	 * the ID of the previous defined banshee server and they will share the same persisted database
	 * and don not need to create it twice.
	 * 
	 * @return ID of same banshee server
	 */
	public long getSameHostId() {
		return mSameHostId;
	}
	
	/**
	 * Get locally stored size of database in bytes.<br>
	 * <br>
	 * This will be {@code -1} until you refresh this information with
	 * {@link #updateServer(BansheeServer, int)}.
	 * 
	 * @return database size in bytes
	 */
	public long getDbSize() {
		return mDbSize;
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
		
		Cursor cursor = getDb().query(DB.TABLE_NAME,
				new String [] {DB.ID, DB.HOST, DB.PORT, DB.SAME_ID, DB.DB_SIZE},
				null, null, null, null, null);
		
		while (cursor.moveToNext()) {
			BansheeServer s = new BansheeServer(cursor.getString(1), cursor.getInt(2));
			s.mId = cursor.getLong(0);
			s.mSameHostId = cursor.getLong(3);
			s.mDbSize = cursor.getLong(4);
			server.add(s);
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
		Cursor cursor = getDb().query(DB.TABLE_NAME,
				new String[] {DB.ID, DB.HOST, DB.PORT, DB.SAME_ID, DB.DB_SIZE},
				DB.ID + "=" + id, null, null, null, null);
		
		try {
			if (cursor.moveToFirst()) {
				BansheeServer s = new BansheeServer(cursor.getString(1), cursor.getInt(2));
				s.mId = cursor.getLong(0);
				s.mSameHostId = cursor.getLong(3);
				s.mDbSize = cursor.getLong(4);
				return s;
			} else {
				return null;
			}
		} finally {
			cursor.close();
		}
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
		
		ContentValues v = new ContentValues();
		v.put(DB.HOST, server.mHost);
		v.put(DB.PORT, server.mPort);
		server.mId = getDb().insert(DB.TABLE_NAME, null, v);
	}
	
	/**
	 * Update added server with and local database size.
	 * 
	 * @param server
	 *            server to update (server should be added with {@link #addServer(BansheeServer)}
	 *            before)
	 * @param dbSize
	 *            database size in bytes
	 * 
	 * @see #getDbSize()
	 */
	public static void updateServer(BansheeServer server, long dbSize) {
		if (server.mId < 0) {
			throw new IllegalArgumentException("given server has no valid ID");
		}
		
		server.mDbSize = dbSize;
		ContentValues values = new ContentValues();
		values.put(DB.DB_SIZE, dbSize);
		getDb().update(DB.TABLE_NAME, values, DB.ID + "=" + server.mId, null);
	}
	
	/**
	 * Remove banshee server from list.
	 * 
	 * @param id
	 *            ID of server to remove
	 * 
	 * @see #getId()
	 */
	public static void removeServer(long id) {
		getDb().delete(DB.TABLE_NAME, DB.ID + "=" + id, null);
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
		
		Cursor cursor = getDb().query(
				DB.TABLE_NAME,
				new String [] {DB.ID, DB.HOST, DB.PORT, DB.SAME_ID, DB.DB_SIZE},
				DB.DEFAULT + "!=0", null, null, null, null, "1");
		
		if (cursor.moveToNext()) {
			s = new BansheeServer(cursor.getString(1), cursor.getInt(2));
			s.mId = cursor.getLong(0);
			s.mSameHostId = cursor.getLong(3);
			s.mDbSize = cursor.getLong(4);
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
	
	private static SQLiteDatabase getDb() {
		if (mDbinstance == null) {
			return mDbinstance = new BasheeDbHelper(App.getContext()).getWritableDatabase();
		} else {
			return mDbinstance;
		}
	}
	
	/**
	 * Helper which creates and setups an empty banshee server database.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private static class BasheeDbHelper extends SQLiteOpenHelper {
		
		public BasheeDbHelper(Context context) {
			super(context, "bansheeserver.db", null, 1);
		}
		
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + DB.TABLE_NAME + " (\n"
					+ DB.ID + " INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE,\n"
					+ DB.HOST + " TEXT NOT NULL,\n"
					+ DB.PORT + " INTEGER NOT NULL,\n"
					+ DB.SAME_ID + " INTEGER NOT NULL,\n"
					+ DB.DB_SIZE + " INTEGER NOT NULL DEFAULT 0,\n"
					+ DB.DEFAULT + " INTEGER NOT NULL DEFAULT 0\n"
					+ ");");
		}
		
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			
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
		public static final String SAME_ID = "smaeid";
		public static final String DB_SIZE = "dbsize";
		public static final String DEFAULT = "isDefault";
	}
}
