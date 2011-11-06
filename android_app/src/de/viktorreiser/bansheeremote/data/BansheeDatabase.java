package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.viktorreiser.bansheeremote.R;

/**
 * This class handles the synchronized banshee database(s).
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class BansheeDatabase {
	
	// PRIVATE ====================================================================================
	
	private static SQLiteDatabase mBansheeDatabase;
	private static BansheeServer mServer;
	
	private static Set<TrackI> mOrderedTrackInfo = null;
	private static Map<Long, TrackI> mTrackInfo = null;
	private static Set<AlbumI> mOrderedAlbumInfo = null;
	private static Map<Long, AlbumI> mAlbumInfo = null;
	private static Set<ArtistI> mOrderedArtistInfo = null;
	private static Map<Long, ArtistI> mArtistInfo = null;
	
	// PUBLIC =====================================================================================
	
	public static class TrackI {
		
		private static TrackI createUnknown() {
			TrackI i = new TrackI();
			i.title = App.getContext().getString(R.string.unknown_track);
			i.genre = "";
			return i;
		}
		
		private long id;
		private long artistId;
		private long albumId;
		private String title;
		private int trackNumber;
		private int duration;
		private int year;
		private String genre;
		private AlbumI album;
		private ArtistI artist;
		
		public long getId() {
			return id;
		}
		
		public long getArtistId() {
			return artistId;
		}
		
		public long getAlbumId() {
			return albumId;
		}
		
		public String getTitle() {
			return title;
		}
		
		public int getTrackNumber() {
			return trackNumber;
		}
		
		public int getDuration() {
			return duration;
		}
		
		public int getYear() {
			return year;
		}
		
		public String getGenre() {
			return genre;
		}
		
		public AlbumI getAlbum() {
			if (album == null) {
				album = getAlbumI(albumId);
			}
			
			return album;
		}
		
		public ArtistI getArtist() {
			if (artist == null) {
				artist = getArtistI(artistId);
			}
			
			return artist;
		}
	}
	
	public static class AlbumI {
		
		private static AlbumI createUnknown() {
			AlbumI i = new AlbumI();
			i.title = App.getContext().getString(R.string.all_albums);
			i.artId = "";
			return i;
		}
		
		private long id;
		private long artistId;
		private String title;
		private String artId;
		private int trackCount;
		private ArtistI artist;
		
		public long getId() {
			return id;
		}
		
		public long getArtistId() {
			return artistId;
		}
		
		public String getTitle() {
			return title;
		}
		
		public String getArtId() {
			return artId;
		}
		
		public int getTrackCount() {
			return trackCount;
		}
		
		public ArtistI getArtistI() {
			if (artist == null) {
				artist = BansheeDatabase.getArtistI(artistId);
			}
			
			return artist;
		}
	}
	
	public static class ArtistI {
		
		private static ArtistI createUnknown() {
			ArtistI i = new ArtistI();
			i.name = App.getContext().getString(R.string.unknown_artist);
			return i;
		}
		
		private long id;
		private String name;
		private int trackCount;
		private int albumCount;
		
		public long getId() {
			return id;
		}
		
		public String getName() {
			return name;
		}
		
		public int getTrackCount() {
			return trackCount;
		}
		
		public int getAlbumCount() {
			return albumCount;
		}
	}
	
	
	public static TrackI [] getOrderedTrackI() {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		return mOrderedTrackInfo.toArray(new TrackI [0]);
	}
	
	public static TrackI [] getOrderedTrackIForAlbum(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		List<TrackI> info = new LinkedList<TrackI>();
		
		for (TrackI i : mOrderedTrackInfo) {
			if (i.getAlbumId() == id) {
				info.add(i);
			}
		}
		
		Collections.sort(info, new Comparator<TrackI>() {
			@Override
			public int compare(TrackI lhs, TrackI rhs) {
				return lhs.trackNumber - rhs.trackNumber;
			}
		});
		
		return info.toArray(new TrackI [0]);
	}
	
	public static TrackI [] getOrderedTrackIForArtist(long id) {
		if (!isOpen()) {
			return null;
		}
		
		List<TrackI> info = new LinkedList<TrackI>();
		
		for (TrackI i : mOrderedTrackInfo) {
			if (i.getArtistId() == id) {
				info.add(i);
			}
		}
		
		return info.toArray(new TrackI [0]);
	}
	
	public static TrackI getTrackI(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		TrackI i = mTrackInfo.get(id);
		return i == null ? TrackI.createUnknown() : i;
	}
	
	public static TrackI getUncachedTrackI(long id) {
		if (!isOpen()) {
			return null;
		}
		
		if (mOrderedTrackInfo != null) {
			TrackI i = mTrackInfo.get(id);
			return i == null ? TrackI.createUnknown() : i;
		}
		
		TrackI i = null;
		
		Cursor c = mBansheeDatabase.rawQuery(""
				+ "SELECT t." + DB.ID + ", t." + DB.ARTIST_ID + ", t." + DB.ALBUM_ID
				+ ", t." + DB.TITLE + ", t." + DB.TRACK_NUMBER + ", t." + DB.DURATION
				+ ", t." + DB.YEAR + ", t." + DB.GENRE + ", a." + DB.NAME
				+ ", l." + DB.TITLE + ", l." + DB.ART_ID
				+ " FROM " + DB.TABLE_TRACKS + " AS t"
				+ " JOIN " + DB.TABLE_ARTISTS + " AS a, " + DB.TABLE_ALBUMS + " AS l"
				+ " ON a." + DB.ID + "=t." + DB.ARTIST_ID
				+ " AND l." + DB.ID + "=t." + DB.ALBUM_ID
				+ " WHERE t." + DB.ID + "=" + id,
				null);
		
		if (c.moveToFirst()) {
			i = new TrackI();
			
			String title = cleanString(c, 3);
			String artist = cleanString(c, 8);
			String album = cleanString(c, 9);
			
			i.id = c.getLong(0);
			i.artistId = c.getLong(1);
			i.albumId = c.getLong(2);
			i.title = "".equals(title) ? App.getContext().getString(R.string.unknown_track) : title;
			i.trackNumber = cleanInt(c, 4);
			i.duration = cleanInt(c, 5);
			i.year = cleanInt(c, 6);
			i.genre = cleanString(c, 7);
			
			i.artist = new ArtistI();
			i.artist.id = i.artistId;
			i.artist.name = "".equals(artist)
					? App.getContext().getString(R.string.unknown_artist) : artist;
			
			i.album = new AlbumI();
			i.album.id = i.albumId;
			i.album.artistId = i.artistId;
			i.album.artist = i.artist;
			i.album.title = "".equals(album)
					? App.getContext().getString(R.string.unknown_album) : album;
			i.album.artId = cleanString(c, 10);
		}
		
		c.close();
		
		return i == null ? TrackI.createUnknown() : i;
	}
	
	public static AlbumI [] getOrderedAlbumI(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		List<AlbumI> info = new LinkedList<AlbumI>();
		
		for (AlbumI i : mOrderedAlbumInfo) {
			if (i.getArtistId() == id) {
				info.add(i);
			}
		}
		
		return info.toArray(new AlbumI [0]);
	}
	
	public static AlbumI [] getOrderedAlbumI() {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		return mOrderedAlbumInfo.toArray(new AlbumI [0]);
	}
	
	public static AlbumI getAlbumI(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		AlbumI i = mAlbumInfo.get(id);
		return i == null ? AlbumI.createUnknown() : i;
	}
	
	public static ArtistI [] getOrderedArtistI() {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		return mOrderedArtistInfo.toArray(new ArtistI [0]);
	}
	
	public static ArtistI getArtistI(long id) {
		if (!isOpen()) {
			return null;
		}
		
		setupDbCache();
		
		ArtistI i = mArtistInfo.get(id);
		return i == null ? ArtistI.createUnknown() : i;
	}
	
	public static void setupDbCache() {
		if (!isOpen() || mOrderedTrackInfo != null) {
			return;
		}
		
		mTrackInfo = new TreeMap<Long, TrackI>();
		mAlbumInfo = new TreeMap<Long, AlbumI>();
		mArtistInfo = new TreeMap<Long, ArtistI>();
		
		mOrderedTrackInfo = new TreeSet<TrackI>(new Comparator<TrackI>() {
			@Override
			public int compare(TrackI lhs, TrackI rhs) {
				char lc = lhs.title.charAt(0);
				char rc = rhs.title.charAt(0);
				boolean ra = Character.isLetter(rc);
				int result;
				
				if (Character.isLetter(lc)) {
					result = ra ? lhs.title.compareToIgnoreCase(rhs.title) : 1;
				} else {
					result = ra ? -1 : lhs.title.compareToIgnoreCase(rhs.title);
				}
				
				return result == 0 ? -1 : result;
			}
		});
		
		Cursor c = mBansheeDatabase.query(
				DB.TABLE_TRACKS,
				new String [] {
						DB.ID, DB.ARTIST_ID, DB.ALBUM_ID, DB.TITLE,
						DB.TRACK_NUMBER, DB.DURATION, DB.YEAR, DB.GENRE},
				null, null, null, null, null);
		
		while (c.moveToNext()) {
			String title = cleanString(c, 3);
			TrackI i = new TrackI();
			
			i.id = c.getLong(0);
			i.artistId = c.getLong(1);
			i.albumId = c.getLong(2);
			i.title = "".equals(title) ? App.getContext().getString(R.string.unknown_track) : title;
			i.trackNumber = cleanInt(c, 4);
			i.duration = cleanInt(c, 5);
			i.year = cleanInt(c, 6);
			i.genre = cleanString(c, 7);
			
			mOrderedTrackInfo.add(i);
			mTrackInfo.put(i.id, i);
		}
		
		mOrderedAlbumInfo = new TreeSet<AlbumI>(new Comparator<AlbumI>() {
			@Override
			public int compare(AlbumI lhs, AlbumI rhs) {
				char lc = lhs.title.charAt(0);
				char rc = rhs.title.charAt(0);
				boolean ra = Character.isLetter(rc);
				int result;
				
				if (Character.isLetter(lc)) {
					result = ra ? lhs.title.compareToIgnoreCase(rhs.title) : 1;
				} else {
					result = ra ? -1 : lhs.title.compareToIgnoreCase(rhs.title);
				}
				
				return result == 0 ? -1 : result;
			}
		});
		
		c.close();
		c = mBansheeDatabase.query(
				DB.TABLE_ALBUMS,
				new String [] {DB.ID, DB.ARTIST_ID, DB.TITLE, DB.ART_ID},
				null, null, null, null, null);
		
		while (c.moveToNext()) {
			String title = cleanString(c, 2);
			AlbumI i = new AlbumI();
			
			i.id = c.getLong(0);
			i.artistId = c.getLong(1);
			i.title = "".equals(title) ? App.getContext().getString(R.string.unknown_album) : title;
			i.artId = cleanString(c, 3);
			
			mOrderedAlbumInfo.add(i);
			mAlbumInfo.put(i.id, i);
		}
		
		mOrderedArtistInfo = new TreeSet<ArtistI>(new Comparator<ArtistI>() {
			@Override
			public int compare(ArtistI lhs, ArtistI rhs) {
				char lc = lhs.name.charAt(0);
				char rc = rhs.name.charAt(0);
				boolean ra = Character.isLetter(rc);
				int result;
				
				if (Character.isLetter(lc)) {
					result = ra ? lhs.name.compareToIgnoreCase(rhs.name) : 1;
				} else {
					result = ra ? -1 : lhs.name.compareToIgnoreCase(rhs.name);
				}
				
				return result == 0 ? -1 : result;
			}
		});
		
		c.close();
		c = mBansheeDatabase.query(
				DB.TABLE_ARTISTS,
				new String [] {DB.ID, DB.NAME},
				null, null, null, null, null);
		
		while (c.moveToNext()) {
			String title = cleanString(c, 1);
			ArtistI i = new ArtistI();
			
			i.id = c.getLong(0);
			i.name = "".equals(title) ? App.getContext().getString(R.string.unknown_artist) : title;
			
			mOrderedArtistInfo.add(i);
			mArtistInfo.put(i.id, i);
		}
		
		c.close();
		c = mBansheeDatabase.query(
				DB.TABLE_TRACKS,
				new String [] {DB.ARTIST_ID, "COUNT(*), COUNT(DISTINCT " + DB.ALBUM_ID + ")"},
				null, null, DB.ARTIST_ID, null, null);
		
		while (c.moveToNext()) {
			ArtistI i = mArtistInfo.get(c.getLong(0));
			i.trackCount = c.getInt(1);
			i.albumCount = c.getInt(2);
		}
		
		c.close();
		c = mBansheeDatabase.query(
				DB.TABLE_TRACKS,
				new String [] {DB.ALBUM_ID, "COUNT(" + DB.ID + ")"},
				null, null, DB.ALBUM_ID, null, null);
		
		while (c.moveToNext()) {
			mAlbumInfo.get(c.getLong(0)).trackCount = c.getInt(1);
		}
		
		c.close();
	}
	
	
	/**
	 * Is database up to date?
	 * 
	 * @param server
	 *            banshee server to check
	 * @param timestamp
	 *            timestamp returned by server
	 * 
	 * @return {@code true} if database exists and has the same timestamp
	 */
	public static boolean isDatabaseUpToDate(BansheeServer server, long timestamp) {
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (id > 0 && same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		} else if (id > 0) {
			server = same;
		} else {
			id = server.getId();
		}
		
		if (!new File(App.BANSHEE_PATH + id + App.DB_EXT).exists()) {
			return false;
		} else {
			return timestamp <= server.mDbTimestamp;
		}
	}
	
	/**
	 * Persist a new database for a banshee server.
	 * 
	 * @param server
	 *            banshee server for which will use the database
	 * @param dbData
	 *            database file as raw byte array
	 * @param timestamp
	 *            timestamp of database received from previous request
	 * 
	 * @return {@code true} if database was updated successfully ({@link #open(BansheeServer)} is
	 *         called automatically) otherwise {@code false} and no database is bound anymore (e.g.
	 *         when given data doesn't represent a valid database)
	 */
	public static boolean updateDatabase(BansheeServer server, byte [] dbData, int timestamp) {
		if (server.getId() < 1) {
			throw new IllegalArgumentException("server is not a valid added server");
		}
		
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (id > 0 && same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		} else if (id < 1) {
			id = server.getId();
		} else {
			server = same;
		}
		
		try {
			File file = new File(App.BANSHEE_PATH + id + App.DB_EXT);
			
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
				server.mDbTimestamp = timestamp;
				BansheeServer.updateServer(server);
			} else {
				same.mDbTimestamp = timestamp;
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
		mServer = null;
		
		if (server.getId() < 1) {
			throw new IllegalArgumentException("server is not a valid added server");
		}
		
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (id > 0 && same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
		}
		
		if (id < 1) {
			id = server.getId();
		}
		
		close();
		
		File file = new File(App.BANSHEE_PATH + id + App.DB_EXT);
		
		if (file.exists()) {
			try {
				mBansheeDatabase = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
						SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			} catch (Exception e) {
				mBansheeDatabase = null;
				return false;
			}
		} else {
			return false;
		}
		
		mServer = server;
		
		return true;
	}
	
	/**
	 * Is there an open database?
	 * 
	 * @return {@code true} if a database is open and ready for access
	 */
	public static boolean isOpen() {
		return mBansheeDatabase != null;
	}
	
	/**
	 * Force database close (if open).
	 */
	public static void close() {
		if (isOpen()) {
			mBansheeDatabase.close();
			mBansheeDatabase = null;
			mServer = null;
		}
		
		mOrderedAlbumInfo = null;
		mOrderedArtistInfo = null;
		mOrderedTrackInfo = null;
		mAlbumInfo = null;
		mArtistInfo = null;
		mTrackInfo = null;
	}
	
	/**
	 * Get the banshee server which was given to open the database.
	 * 
	 * @return banshee server or {@code null} when no database is open
	 */
	public static BansheeServer getServer() {
		return mServer;
	}
	
	// PRIVATE ====================================================================================
	
	/**
	 * Get string from cursor.
	 * 
	 * @param c
	 *            cursor
	 * @param index
	 *            index from which the string should be get
	 * 
	 * @return if string is {@code null} you'll get an empty string otherwise the string itself
	 */
	private static String cleanString(Cursor c, int index) {
		return c.isNull(index) ? "" : c.getString(index).trim();
	}
	
	/**
	 * Get integer from cursor.
	 * 
	 * @param c
	 *            cursor
	 * @param index
	 *            index from which the integer should be get
	 * 
	 * @return if integer is {@code null} you'll {@code -1} otherwise the value itself
	 */
	private static int cleanInt(Cursor c, int index) {
		return c.isNull(index) ? -1 : c.getInt(index);
	}
	
	/**
	 * Database (column) constants for the synchronized database.
	 * 
	 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
	 */
	private static class DB {
		public static final String TABLE_TRACKS = "tracks";
		public static final String TABLE_ARTISTS = "artists";
		public static final String TABLE_ALBUMS = "albums";
		
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
