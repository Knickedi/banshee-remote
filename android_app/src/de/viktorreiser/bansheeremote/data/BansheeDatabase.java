package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * This class handles the synchronized banshee database(s).
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeDatabase {
	
	// PRIVATE ====================================================================================
	
	/**
	 * Global instance of currently used database.
	 */
	private static SQLiteDatabase mBansheeDatabase;
	
	// PUBLIC =====================================================================================
	
	public static class TrackInfo {
		public long id;
		public long aritstId;
		public long albumId;
		public String title;
		public String artist;
		public String album;
		public String genre;
		public int year;
		public long totalTime;
		public int trackNumber;
		public String artId;
	}
	
	/**
	 * Is database up to date?
	 * 
	 * @param server
	 *            banshee server to check
	 * @param timestamp
	 *            timestamp
	 * 
	 * @return {@code true} if database exists and has the same byte size
	 */
	public static boolean isDatabaseUpToDate(BansheeServer server, long timestamp) {
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
			return timestamp == server.mDbTimestamp;
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
				server.mDbTimestamp = dbData.length;
				BansheeServer.updateServer(server);
			} else {
				same.mDbTimestamp = dbData.length;
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
	
	public static TrackInfo getTrackInfo(long id) {
		if (!isOpen()) {
			return null;
		}
		
		Cursor cursor = mBansheeDatabase.rawQuery(""
				+ " SELECT t." + DB.ID + ", t." + DB.TITLE + ", t." + DB.DURATION
				+ ", t." + DB.YEAR + ", t." + DB.GENRE + ", r." + DB.NAME
				+ ", l." + DB.TITLE + ", l." + DB.ART_ID + ", r." + DB.ID + ", l." + DB.ID
				+ ", t." + DB.TRACK_NUMBER
				+ " FROM " + DB.TABLE_TRACKS + " AS t"
				+ " JOIN " + DB.TABLE_ARTISTS + " AS r, " + DB.TABLE_ALBUM + " AS l"
				+ " ON t." + DB.ARTIST_ID + "=r." + DB.ID
				+ " AND t." + DB.ALBUM_ID + "=l." + DB.ID
				+ " WHERE t." + DB.ID + "=" + id, null);
		
		if (cursor.moveToFirst()) {
			TrackInfo info = new TrackInfo();
			
			info.id = cursor.getLong(0);
			info.title = cleanString(cursor, 1);
			info.totalTime = cursor.getLong(2);
			info.year = cleanInt(cursor, 3);
			info.genre = cleanString(cursor, 4);
			info.artist = cleanString(cursor, 5);
			info.album = cleanString(cursor, 6);
			info.artId = cleanString(cursor, 7);
			info.aritstId = cursor.getLong(8);
			info.albumId = cursor.getLong(9);
			info.trackNumber = cleanInt(cursor, 10);
			
			cursor.close();
			return info;
		} else {
			cursor.close();
			return null;
		}
	}
	
	private static String cleanString(Cursor c, int index) {
		return c.isNull(index) ? "" : c.getString(index);
	}
	
	private static int cleanInt(Cursor c, int index) {
		return c.isNull(index) ? -1 : c.getInt(index);
	}
	
	private static class DB {
		public static final String TABLE_TRACKS = "tracks";
		public static final String TABLE_ARTISTS = "artists";
		public static final String TABLE_ALBUM = "albums";

		public static final String ID = "_id";
		public static final String ARTIST_ID = "artistId";
		public static final String ALBUM_ID = "albumId";
		public static final String TITLE = "title";
		public static final String DURATION = "duration";
		public static final String YEAR = "year";
		public static final String GENRE = "genre";
		public static final String NAME = "name";
		public static final String ART_ID = "artId";
		public static final String TRACK_NUMBER = "trackNumber";
	}
}
