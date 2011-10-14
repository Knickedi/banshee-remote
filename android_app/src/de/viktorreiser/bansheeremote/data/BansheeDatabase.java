package de.viktorreiser.bansheeremote.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.os.Environment;

public class BansheeDatabase {
	
	public static boolean isDatabaseUpToDate(BansheeServer server, long dbSize) {
		long id = server.mSameHostId;
		BansheeServer same = BansheeServer.getServer(id);
		
		if (same == null) {
			// referenced server is dead, update that
			server.mSameHostId = -1;
			BansheeServer.updateServer(server);
			id = server.getId();
		}
		
		if (!new File(Environment.getExternalStorageDirectory(),
				"BansheeRemote/" + id + ".db").exists()) {
			return false;
		} else {
			return dbSize == server.mDbSize;
		}
	}
	
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
			File file = new File(Environment.getExternalStorageDirectory(),
					"BansheeRemote/" + id + ".db");
			
			file.getParentFile().mkdir();
			file.delete();
			
			OutputStream os = new FileOutputStream(file);
			os.write(dbData);
			os.close();
			
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
}
