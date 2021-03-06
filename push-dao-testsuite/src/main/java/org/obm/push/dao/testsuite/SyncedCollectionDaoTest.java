/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2013-2014  Linagora
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
package org.obm.push.dao.testsuite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.guice.GuiceRunner;
import org.obm.push.ProtocolVersion;
import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.Credentials;
import org.obm.push.bean.Device;
import org.obm.push.bean.DeviceId;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.User;
import org.obm.push.bean.User.Factory;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.store.SyncedCollectionDao;

@RunWith(GuiceRunner.class)
public abstract class SyncedCollectionDaoTest {

	protected SyncedCollectionDao syncedCollectionDao;
	
	protected User user;
	protected Device device;
	protected Credentials credentials;

	@Before
	public void setUp() {
		user = Factory.create().createUser("login@domain", "email@domain", "displayName");
		device = new Device(1, "devType", new DeviceId("devId"), new Properties(), ProtocolVersion.V121);
		credentials = new Credentials(user, "password".toCharArray());
	}

	@Test
	public void testGetWhenNoPut() {
		AnalysedSyncCollection syncCollection = syncedCollectionDao.get(credentials, device, CollectionId.of(1));
		assertThat(syncCollection).isNull();
	}
	
	@Test
	public void testGetWhenPutWithOtherDevice() {
		Device otherDevice = new Device(6, "otherType", new DeviceId("otherId"), new Properties(), ProtocolVersion.V121);
		syncedCollectionDao.put(user, otherDevice, buildCollection(CollectionId.of(1), SyncKey.INITIAL_SYNC_KEY));
		
		assertThat(syncedCollectionDao.get(credentials, device, CollectionId.of(1))).isNull();
	}
	
	@Test
	public void testGetWhenPutWithOtherCredentials() {
		User user2 = Factory.create().createUser("login@domain2", "email@domain2", "displayName2");
		syncedCollectionDao.put(user2, device, buildCollection(CollectionId.of(1), SyncKey.INITIAL_SYNC_KEY));

		assertThat(syncedCollectionDao.get(credentials, device, CollectionId.of(1))).isNull();
	}
	
	@Test
	public void testGetWhenPut() {
		syncedCollectionDao.put(user, device, buildCollection(CollectionId.of(1), SyncKey.INITIAL_SYNC_KEY));
		AnalysedSyncCollection syncCollection = syncedCollectionDao.get(credentials, device, CollectionId.of(1));
		assertThat(syncCollection).isNotNull();
		assertThat(syncCollection.getCollectionId()).isEqualTo(CollectionId.of(1));
	}
	
	@Test
	public void testGetWhenOverridingPut() {
		SyncKey expectedSyncKey = new SyncKey("123");
		AnalysedSyncCollection col = buildCollection(CollectionId.of(1), SyncKey.INITIAL_SYNC_KEY);
		syncedCollectionDao.put(user, device, col);
		col = buildCollection(CollectionId.of(1), expectedSyncKey);
		syncedCollectionDao.put(user, device, col);
		
		AnalysedSyncCollection syncCollection = syncedCollectionDao.get(credentials, device, CollectionId.of(1));
		assertThat(syncCollection).isNotNull();
		assertThat(syncCollection.getCollectionId()).isEqualTo(CollectionId.of(1));
		assertThat(syncCollection.getSyncKey()).isEqualTo(expectedSyncKey);
	}

	protected AnalysedSyncCollection buildCollection(CollectionId id, SyncKey syncKey) {
		return AnalysedSyncCollection.builder()
				.collectionId(id)
				.syncKey(syncKey)
				.build();
	}
}
