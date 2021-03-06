/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2013  Linagora
 *
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Affero General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version, provided you comply 
 * with the Additional Terms applicable for OBM connector by Linagora 
 * pursuant to Section 7 of the GNU Affero General Public License, 
 * subsections (b), (c), and (e), pursuant to which you must notably (i) retain 
 * the “Message sent thanks to OBM, Free Communication by Linagora” 
 * signature notice appended to any and all outbound messages 
 * (notably e-mail and meeting requests), (ii) retain all hypertext links between 
 * OBM and obm.org, as well as between Linagora and linagora.com, and (iii) refrain 
 * from infringing Linagora intellectual property rights over its trademarks 
 * and commercial brands. Other Additional Terms apply, 
 * see <http://www.linagora.com/licenses/> for more details. 
 *
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details. 
 *
 * You should have received a copy of the GNU Affero General Public License 
 * and its applicable Additional Terms for OBM along with this program. If not, 
 * see <http://www.gnu.org/licenses/> for the GNU Affero General Public License version 3 
 * and <http://www.linagora.com/licenses/> for the Additional Terms applicable to 
 * OBM connectors. 
 * 
 * ***** END LICENSE BLOCK ***** */
package org.obm.push.cassandra.dao;


public interface CassandraStructure {
	
	interface ContactCreation {
		Table TABLE = Table.of("contact_creation");
		String[] PK = { Columns.USER, Columns.DEVICE_ID, Columns.COLLECTION_ID, Columns.HASH };
		
		interface Columns {
			String USER = "user";
			String DEVICE_ID = "device_id";
			String COLLECTION_ID = "collection_id";
			String HASH = "hash";
			String SERVER_ID = "server_id";
		}
	}
	
	interface Schema {
		Table TABLE = Table.of("schema_version");
		String[] PK = { Columns.ID, Columns.VERSION }; 
		
		interface Columns {
			String ID = "id";
			String VERSION = "version";
			String DATE = "date";
		}
	}

	interface SyncedCollection {
		Table TABLE = Table.of("synced_collection_v2");
		String[] PK = { Columns.USER, Columns.DEVICE, Columns.COLLECTION_ID }; 
		
		interface Columns {
			String USER = "user";
			String DEVICE = "device";
			String COLLECTION_ID = "collection_id";
			String ANALYSED_SYNC_COLLECTION = "analysed_sync_collection";
		}
	}
	
	interface MonitoredCollection {
		Table TABLE = Table.of("monitored_collection_v2");
		String[] PK = { Columns.USER, Columns.DEVICE };
		
		interface Columns {
			String USER = "user";
			String DEVICE = "device";
			String ANALYSED_SYNC_COLLECTIONS = "analysed_sync_collections";
		}
	}
	
	interface Windowing {
		Table TABLE = Table.of("windowing");
		String[] PK = { Columns.ID, Columns.CHANGE_INDEX };
		
		interface Columns {
			String ID = "id";
			String CHANGE_INDEX = "change_index";
			String CHANGE_TYPE = "change_type";
			String CHANGE_VALUE = "change_value";
		}
	}
	
	interface WindowingIndex {
		Table TABLE = Table.of("windowing_index");
		String[] PK = { Columns.USER, Columns.DEVICE_ID, Columns.COLLECTION_ID, Columns.SYNC_KEY };
		
		interface Columns {
			String USER = "user";
			String DEVICE_ID = "device_id";
			String COLLECTION_ID = "collection_id";
			String SYNC_KEY = "sync_key";
			String WINDOWING_ID = "windowing_id";
			String WINDOWING_KIND = "windowing_kind";
			String WINDOWING_INDEX = "windowing_index";
		}
	}
	
	interface SnapshotTable {
		Table TABLE = Table.of("snapshot");
		String[] PK = { Columns.ID };
		
		interface Columns {
			String ID = "id";
			String SNAPSHOT = "snapshot";
		}
	}
	
	interface SnapshotIndex {
		Table TABLE = Table.of("snapshot_index");
		String[] PK = { Columns.DEVICE_ID, Columns.COLLECTION_ID, Columns.SYNC_KEY };
		
		interface Columns {
			String DEVICE_ID = "device_id";
			String COLLECTION_ID = "collection_id";
			String SYNC_KEY = "sync_key";
			String SNAPSHOT_ID = "snapshot_id";
		}
	}
	
	interface FolderSnapshot {
		Table TABLE = Table.of("folder_snapshot");
		String[] PK = { Columns.USER, Columns.DEVICE_ID, Columns.SYNC_KEY };
		
		interface Columns {
			String USER = "user";
			String DEVICE_ID = "device_id";
			String SYNC_KEY = "sync_key";
			String FOLDERS = "folders";
			String NEXT_COLLECTION_ID = "next_collection_id";
		}
	}
	
	interface FolderMapping {
		Table TABLE = Table.of("folder_mapping");
		String[] PK = { Columns.USER, Columns.DEVICE_ID, Columns.COLLECTION_ID };
		
		interface Columns {
			String USER = "user";
			String DEVICE_ID = "device_id";
			String COLLECTION_ID = "collection_id";
			String BACKEND_ID = "backend_id";
			String DATA_TYPE = "data_type";
			String FOLDER = "folder";
		}
	}
	
	interface WindowingToSnapshot {
		Table TABLE = Table.of("windowing_to_snapshot");
		String[] PK = { Columns.DEVICE_ID, Columns.COLLECTION_ID, Columns.SYNC_KEY };
		
		interface Columns {
			String DEVICE_ID = "device_id";
			String COLLECTION_ID = "collection_id";
			String SYNC_KEY = "sync_key";
			String SNAPSHOT_ID = "snapshot_id";
		}
	}
	
	interface V1 {

		interface SyncedCollection {
			Table TABLE = Table.of("synced_collection");
			String[] PK = { Columns.CREDENTIALS, Columns.DEVICE, Columns.COLLECTION_ID }; 
			
			interface Columns {
				String CREDENTIALS = "credentials";
				String DEVICE = "device";
				String COLLECTION_ID = "collection_id";
				String ANALYSED_SYNC_COLLECTION = "analysed_sync_collection";
			}
		}
		
		interface MonitoredCollection {
			Table TABLE = Table.of("monitored_collection");
			String[] PK = { Columns.CREDENTIALS, Columns.DEVICE };
			
			interface Columns {
				String CREDENTIALS = "credentials";
				String DEVICE = "device";
				String ANALYSED_SYNC_COLLECTIONS = "analysed_sync_collections";
			}
		}
	}
	
	interface DeliveryStatusNotification {
		Table TABLE = Table.of("delivery_status_notification");
		String[] PK = { Columns.USER, Columns.SERVER_ID };
		
		interface Columns {
			String USER = "user";
			String SERVER_ID = "server_id";
			String DELIVERY = "delivery";
			String READ_RECEIPT = "read_receipt";
		}
		
	}
}
