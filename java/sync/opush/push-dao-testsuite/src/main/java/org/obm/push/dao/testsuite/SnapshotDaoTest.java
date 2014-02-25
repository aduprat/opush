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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.guice.GuiceRunner;
import org.obm.push.bean.DeviceId;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.SyncKey;
import org.obm.push.mail.bean.Email;
import org.obm.push.mail.bean.Snapshot;
import org.obm.push.store.SnapshotDao;
import org.obm.push.utils.DateUtils;

@RunWith(GuiceRunner.class)
public abstract class SnapshotDaoTest {

	protected SnapshotDao snapshotDao;
	
	@Test
	public void getNull() {
		SyncKey syncKey = new SyncKey("synckey");
		DeviceId deviceId = new DeviceId("deviceId");
		Integer connectionId = 1;
		
		Snapshot snapshot = snapshotDao.get(deviceId, syncKey, connectionId);
		
		assertThat(snapshot).isNull();
	}
	
	@Test
	public void put() {
		SyncKey syncKey = new SyncKey("synckey");
		DeviceId deviceId = new DeviceId("deviceId");
		Integer collectionId = 1;
		int uidNext = 2;
		Email email = Email.builder()
				.uid(3)
				.read(false)
				.date(DateUtils.getCurrentDate())
				.build();
		
		Snapshot snapshot = Snapshot.builder()
				.deviceId(deviceId)
				.filterType(FilterType.THREE_DAYS_BACK)
				.syncKey(syncKey)
				.collectionId(collectionId)
				.uidNext(uidNext)
				.addEmail(email)
				.build();
		
		snapshotDao.put(snapshot);
	}
	
	@Test
	public void get() {
		SyncKey syncKey = new SyncKey("synckey");
		DeviceId deviceId = new DeviceId("deviceId");
		Integer collectionId = 1;
		int uidNext = 2;
		Email email = Email.builder()
				.uid(3)
				.read(false)
				.date(DateUtils.getCurrentDate())
				.build();
		
		Snapshot expectedSnapshot = Snapshot.builder()
				.deviceId(deviceId)
				.filterType(FilterType.THREE_DAYS_BACK)
				.syncKey(syncKey)
				.collectionId(collectionId)
				.uidNext(uidNext)
				.addEmail(email)
				.build();
		
		snapshotDao.put(expectedSnapshot);
		Snapshot snapshot = snapshotDao.get(deviceId, syncKey, collectionId);
		
		assertThat(snapshot).isEqualTo(expectedSnapshot);
	}
}
