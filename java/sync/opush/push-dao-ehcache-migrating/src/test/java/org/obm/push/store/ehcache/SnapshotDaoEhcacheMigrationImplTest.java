/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2014  Linagora
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
package org.obm.push.store.ehcache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import net.sf.ehcache.migrating.Element;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.obm.push.bean.DeviceId;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.SnapshotKey;
import org.obm.push.bean.SyncKey;
import org.obm.push.mail.bean.Snapshot;
import org.slf4j.Logger;

public class SnapshotDaoEhcacheMigrationImplTest extends StoreManagerConfigurationTest {

	private MigrationSourceObjectStoreManager objectStoreManagerMigration;
	private SnapshotDaoEhcacheMigrationImpl snapshotDaoEhcacheMigrationImpl;
	private DeviceId deviceId;
	
	@Before
	public void init() throws IOException {
		Logger logger = EasyMock.createNiceMock(Logger.class);
		this.objectStoreManagerMigration = new MigrationSourceObjectStoreManager( super.initOpushConfigurationMock(), logger);
		this.snapshotDaoEhcacheMigrationImpl = new SnapshotDaoEhcacheMigrationImpl(objectStoreManagerMigration);
		this.deviceId = new DeviceId("DevId");
	}
	
	@After
	public void cleanup() throws IllegalStateException, SecurityException {
		objectStoreManagerMigration.shutdown();
	}
	
	@Test
	public void testGetKeys() {
		SnapshotKey key = SnapshotKey.builder()
				.deviceId(deviceId)
				.syncKey(new SyncKey("123"))
				.collectionId(1)
				.build();
		SnapshotKey key2 = SnapshotKey.builder()
				.deviceId(deviceId)
				.syncKey(new SyncKey("456"))
				.collectionId(2)
				.build();
		snapshotDaoEhcacheMigrationImpl.store.put(new Element(
				key,
				Snapshot.builder()
					.deviceId(deviceId)
					.filterType(FilterType.ALL_ITEMS)
					.syncKey(new SyncKey("123"))
					.collectionId(1)
					.build()));
		snapshotDaoEhcacheMigrationImpl.store.put(new Element(
				key2, 
				Snapshot.builder()
					.deviceId(deviceId)
					.filterType(FilterType.ONE_DAY_BACK)
					.syncKey(new SyncKey("456"))
					.collectionId(2)
					.build()));
		
		List<Object> keys = snapshotDaoEhcacheMigrationImpl.getKeys();
		assertThat(keys).containsOnly(key, key2);
	}
	
	@Test
	public void testGet() {
		SnapshotKey key = SnapshotKey.builder()
				.deviceId(deviceId)
				.syncKey(new SyncKey("123"))
				.collectionId(1)
				.build();
		Element element = new Element(
				key, 
				Snapshot.builder()
						.deviceId(deviceId)
						.filterType(FilterType.ALL_ITEMS)
						.syncKey(new SyncKey("123"))
						.collectionId(1)
						.build());
		snapshotDaoEhcacheMigrationImpl.store.put(element);
		
		Element value = snapshotDaoEhcacheMigrationImpl.get(key);
		assertThat(value).isEqualTo(element);
	}
	
	@Test
	public void testRemove() {
		SnapshotKey key = SnapshotKey.builder()
				.deviceId(deviceId)
				.syncKey(new SyncKey("123"))
				.collectionId(1)
				.build();
		Element element = new Element(
				key, 
				Snapshot.builder()
						.deviceId(deviceId)
						.filterType(FilterType.ALL_ITEMS)
						.syncKey(new SyncKey("123"))
						.collectionId(1)
						.build());
		snapshotDaoEhcacheMigrationImpl.store.put(element);
		
		snapshotDaoEhcacheMigrationImpl.remove(key);
		
		Element value = snapshotDaoEhcacheMigrationImpl.get(key);
		assertThat(value).isNull();
	}
}
