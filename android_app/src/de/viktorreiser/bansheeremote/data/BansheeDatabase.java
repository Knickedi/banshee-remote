package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.database.sqlite.SQLiteDatabase;

/**
 * This class handles the synchronized banshee database(s).
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeDatabase {
	
	/**
	 * Global instance of currently used database.
	 */
	private static SQLiteDatabase mBansheeDatabase;
	
	
	/**
	 * Is database up to date?
	 * 
	 * @param server
	 *            banshee server to check
	 * @param dbSize
	 *            size of new database in bytes
	 * 
	 * @return {@code true} if database exists and has the same byte size
	 */
	public static boolean isDatabaseUpToDate(BansheeServer server, long dbSize) {
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		} else {
			server = same;
		}
		
		if (!new File(App.BANSHEE_PATH + id + ".db").exists()) {
			return false;
		} else {
			return dbSize == server.mDbSize;
		}
	}
	
	/**
	 * Persist a new database for a banshee server.
	 * 
	 * @param server
	 *            banshee server for which the database is used
	 * @param dbData
	 *            database file as byte data
	 * 
	 * @return {@code true} if database was updated successfully ({@link #open(BansheeServer)} is
	 *         called automatically) otherwise {@code false} and no database is bound anymore
	 */
	public static boolean updateDatabase(BansheeServer server, byte [] dbData) {
		if (server.getId() < 1) {
			throw new IllegalArgumentException("server is not a valid added server");
		}
		
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		}
		
		try {
			File file = new File(App.BANSHEE_PATH + id + ".db");
			
			file.getParentFile().mkdir();
			file.delete();
			
			OutputStream os = new FileOutputStream(file);
			os.write(dbData);
			os.close();
			
			if (!open(server)) {
				file.delete();
				return false;
			}
			
			if (same == null) {
				server.mDbSize = dbData.length;
				BansheeServer.updateServer(server);
			} else {
				same.mDbSize = dbData.length;
				BansheeServer.updateServer(same);
			}
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Open database for access.
	 * 
	 * @param server
	 *            banshee server for which a database should be opened
	 * 
	 * @return {@code true} if database was opened and is ready for access, otherwise {@code false}
	 *         (because there is no synchronized database)
	 */
	public static boolean open(BansheeServer server) {
		if (server.getId() < 1) {
			throw new IllegalArgumentException("server is not a valid added server");
		}
		
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		}
		
		if (isOpen()) {
			mBansheeDatabase.close();
		}
		
		File file = new File(App.BANSHEE_PATH + id + ".db");
		
		try {
			mBansheeDatabase = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
					SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
		} catch (Exception e) {
			mBansheeDatabase = null;
			return false;
		}
		
		return true;
	}
	
	/**
	 * Is there a open database?
	 * 
	 * @return {@code true} if a database is open and ready for access
	 */
	public static boolean isOpen() {
		return mBansheeDatabase != null;
	}
}
