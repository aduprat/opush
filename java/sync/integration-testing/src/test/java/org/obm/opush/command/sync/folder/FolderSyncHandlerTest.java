/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2012  Linagora
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
package org.obm.opush.command.sync.folder;

import static org.easymock.EasyMock.expect;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.obm.opush.IntegrationPushTestUtils.mockHierarchyChangesForMailboxes;
import static org.obm.opush.IntegrationPushTestUtils.mockHierarchyChangesOnlyInbox;
import static org.obm.opush.IntegrationPushTestUtils.mockNextGeneratedSyncKey;
import static org.obm.opush.IntegrationTestUtils.buildWBXMLOpushClient;
import static org.obm.opush.IntegrationTestUtils.replayMocks;
import static org.obm.opush.IntegrationTestUtils.verifyMocks;
import static org.obm.opush.IntegrationUserAccessUtils.mockUsersAccess;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.DateUtils;
import org.obm.filter.Slow;
import org.obm.filter.SlowFilterRunner;
import org.obm.opush.ActiveSyncServletModule.OpushServer;
import org.obm.opush.PortNumber;
import org.obm.opush.SingleUserFixture;
import org.obm.opush.SingleUserFixture.OpushUser;
import org.obm.opush.env.JUnitGuiceRule;
import org.obm.push.bean.FolderSyncState;
import org.obm.push.bean.FolderSyncStatus;
import org.obm.push.bean.HierarchyItemsChanges;
import org.obm.push.bean.ItemChange;
import org.obm.push.exception.DaoException;
import org.obm.push.store.CollectionDao;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.sync.push.client.Folder;
import org.obm.sync.push.client.FolderSyncResponse;
import org.obm.sync.push.client.FolderType;
import org.obm.sync.push.client.OPClient;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

@RunWith(SlowFilterRunner.class) @Slow
public class FolderSyncHandlerTest {

	@Rule
	public JUnitGuiceRule guiceBerry = new JUnitGuiceRule(FolderSyncHandlerTestModule.class);

	@Inject @PortNumber int port;
	@Inject SingleUserFixture singleUserFixture;
	@Inject OpushServer opushServer;
	@Inject ClassToInstanceAgregateView<Object> classToInstanceMap;

	private List<OpushUser> userAsList;

	private OpushUser user;

	@Before
	public void init() {
		user = singleUserFixture.jaures;
		userAsList = Arrays.asList(user);
	}
	
	@After
	public void shutdown() throws Exception {
		opushServer.stop();
	}

	@Test
	public void testInitialFolderSyncContainsINBOX() throws Exception {
		String initialSyncKey = "0";
		String newGeneratedSyncKey = "d58ea559-d1b8-4091-8ba5-860e6fa54875";
		Date newSyncDate = DateUtils.date("2012-12-14T21:39:45");
		FolderSyncState newMappingSyncState = new FolderSyncState(newGeneratedSyncKey);
		
		mockUsersAccess(classToInstanceMap, userAsList);
		mockHierarchyChangesOnlyInbox(classToInstanceMap, newSyncDate);
		mockNextGeneratedSyncKey(classToInstanceMap, newGeneratedSyncKey);
		
		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expectCollectionDaoAllocateNewFolderSyncState(collectionDao, newGeneratedSyncKey);
		expectCollectionDaoHasntMapping(collectionDao, newMappingSyncState);
		
		replayMocks(classToInstanceMap);
		
		opushServer.start();
		
		OPClient opClient = buildWBXMLOpushClient(user, port);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);

		verifyMocks(classToInstanceMap);
		
		assertThat(folderSyncResponse.getReturnedSyncKey()).isEqualTo(newGeneratedSyncKey);
		assertThat(folderSyncResponse.getStatusAsString()).isEqualTo(FolderSyncStatus.OK.asXmlValue());
		assertThat(folderSyncResponse.getCount()).isEqualTo(1);
		assertThat(folderSyncResponse.getFolders()).hasSize(1);
		Folder inbox = Iterables.getOnlyElement(folderSyncResponse.getFolders().values());
		assertThat(inbox.getName()).isEqualTo("INBOX");
		assertThat(inbox.getType()).isEqualTo(FolderType.DEFAULT_INBOX_FOLDER);
	}

	@Test
	public void testFolderSyncHasNoChange() throws Exception {
		String currentSyncKey = "12341234-1234-1234-1234-123456123456";
		Date currentSyncDate = DateUtils.date("2012-12-14T21:39:45");
		int currentSyncStateId = 123;
		FolderSyncState currentSyncState = createFolderSyncState(currentSyncKey, currentSyncDate, currentSyncStateId);

		String newGeneratedSyncKey = "d58ea559-d1b8-4091-8ba5-860e6fa54875";
		Date newSyncDate = DateUtils.date("2012-12-15T21:39:45");
		int newSyncStateId = 1156;
		FolderSyncState newSyncState = createFolderSyncState(newGeneratedSyncKey, newSyncDate, newSyncStateId);
		
		int collectionId = 4;
		
		mockUsersAccess(classToInstanceMap, userAsList);
		mockHierarchyChangesForMailboxes(classToInstanceMap, buildHierarchyItemsChangeEmpty(newSyncDate));
		mockNextGeneratedSyncKey(classToInstanceMap, newGeneratedSyncKey);

		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expectCollectionDaoHasSyncStateForKey(collectionDao, currentSyncState);
		expectCollectionDaoUpdateSyncState(collectionDao, newSyncState);
		expectCollectionDaoHasMapping(collectionDao, collectionId);
		
		replayMocks(classToInstanceMap);
		
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(user, port);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(currentSyncKey);

		verifyMocks(classToInstanceMap);

		assertThat(folderSyncResponse.getReturnedSyncKey()).isEqualTo(newGeneratedSyncKey);
		assertThat(folderSyncResponse.getStatusAsString()).isEqualTo(FolderSyncStatus.OK.asXmlValue());
		assertThat(folderSyncResponse.getCount()).isEqualTo(0);
		assertThat(folderSyncResponse.getFolders()).isEmpty();
	}

	@Test
	public void testFolderSyncHasChanges() throws Exception {
		String currentSyncKey = "12341234-1234-1234-1234-123456123456";
		Date currentSyncDate = DateUtils.date("2012-12-14T21:39:45");
		int currentSyncStateId = 123;
		FolderSyncState currentSyncState = createFolderSyncState(currentSyncKey, currentSyncDate, currentSyncStateId);

		String newGeneratedSyncKey = "d58ea559-d1b8-4091-8ba5-860e6fa54875";
		Date newSyncDate = DateUtils.date("2012-12-14T21:39:45");
		int newSyncStateId = 1156;
		FolderSyncState newSyncState = createFolderSyncState(newGeneratedSyncKey, newSyncDate, newSyncStateId);
		
		int collectionId = 4;
		String serverId = "4:1";
		String parentId = "23";
		
		org.obm.push.bean.FolderType itemChangeType = org.obm.push.bean.FolderType.USER_CREATED_EMAIL_FOLDER;
		HierarchyItemsChanges mailboxChanges = new HierarchyItemsChanges.Builder()
			.lastSync(currentSyncDate)
			.changes(Lists.newArrayList(
					new ItemChange(serverId, parentId, "aNewImapFolder", itemChangeType, true)))
			.build();
		
		mockUsersAccess(classToInstanceMap, userAsList);
		mockHierarchyChangesForMailboxes(classToInstanceMap, mailboxChanges);
		mockNextGeneratedSyncKey(classToInstanceMap, newGeneratedSyncKey);

		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expectCollectionDaoHasSyncStateForKey(collectionDao, currentSyncState);
		expectCollectionDaoUpdateSyncState(collectionDao, newSyncState);
		expectCollectionDaoHasMapping(collectionDao, collectionId);
		
		replayMocks(classToInstanceMap);
		
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(user, port);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(currentSyncKey);

		verifyMocks(classToInstanceMap);

		assertThat(folderSyncResponse.getReturnedSyncKey()).isEqualTo(newGeneratedSyncKey);
		assertThat(folderSyncResponse.getStatusAsString()).isEqualTo(FolderSyncStatus.OK.asXmlValue());
		assertThat(folderSyncResponse.getCount()).isEqualTo(1);
		assertThat(folderSyncResponse.getFolders()).hasSize(1);
		Folder inbox = Iterables.getOnlyElement(folderSyncResponse.getFolders().values());
		assertThat(inbox.getName()).isEqualTo("aNewImapFolder");
		assertThat(inbox.getType()).isEqualTo(FolderType.USER_CREATED_EMAIL_FOLDER);
	}

	@Test
	public void testFolderSyncHasDeletions() throws Exception {
		String currentSyncKey = "12341234-1234-1234-1234-123456123456";
		Date currentSyncDate = DateUtils.date("2012-12-14T21:39:45");
		int currentSyncStateId = 123;
		FolderSyncState currentSyncState = createFolderSyncState(currentSyncKey, currentSyncDate, currentSyncStateId);

		String newGeneratedSyncKey = "d58ea559-d1b8-4091-8ba5-860e6fa54875";
		Date newSyncDate = DateUtils.date("2012-12-14T21:39:45");
		int newSyncStateId = 1156;
		FolderSyncState newSyncState = createFolderSyncState(newGeneratedSyncKey, newSyncDate, newSyncStateId);

		int collectionId = 4;
		String serverId = "4:1";
		String parentId = "23";
		
		org.obm.push.bean.FolderType itemChangeType = org.obm.push.bean.FolderType.USER_CREATED_EMAIL_FOLDER;
		HierarchyItemsChanges mailboxChanges = new HierarchyItemsChanges.Builder()
			.lastSync(currentSyncDate)
			.deletions(Lists.newArrayList(
					new ItemChange(serverId, parentId, "aNewImapFolder", itemChangeType, true)))
			.build();
		
		mockUsersAccess(classToInstanceMap, userAsList);
		mockHierarchyChangesForMailboxes(classToInstanceMap, mailboxChanges);
		mockNextGeneratedSyncKey(classToInstanceMap, newGeneratedSyncKey);

		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expectCollectionDaoHasSyncStateForKey(collectionDao, currentSyncState);
		expectCollectionDaoUpdateSyncState(collectionDao, newSyncState);
		expectCollectionDaoHasMapping(collectionDao, collectionId);
		
		replayMocks(classToInstanceMap);
		
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(user, port);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(currentSyncKey);

		verifyMocks(classToInstanceMap);

		assertThat(folderSyncResponse.getReturnedSyncKey()).isEqualTo(newGeneratedSyncKey);
		assertThat(folderSyncResponse.getStatusAsString()).isEqualTo(FolderSyncStatus.OK.asXmlValue());
		assertThat(folderSyncResponse.getCount()).isEqualTo(1);
		assertThat(folderSyncResponse.getFolders()).hasSize(1);
		Folder inbox = Iterables.getOnlyElement(folderSyncResponse.getFolders().values());
		assertThat(inbox.getServerId()).isEqualTo(serverId);
	}

	private HierarchyItemsChanges buildHierarchyItemsChangeEmpty(Date lastSync) {
		return new HierarchyItemsChanges.Builder().lastSync(lastSync).build();
	}

	private FolderSyncState createFolderSyncState(String syncKey, Date syncDate, int id) {
		FolderSyncState folderSyncState = new FolderSyncState(syncKey, syncDate);
		folderSyncState.setId(id);
		return folderSyncState;
	}
	
	private void expectCollectionDaoAllocateNewFolderSyncState(CollectionDao collectionDao, String nextSyncKey) throws DaoException {
		expect(collectionDao.allocateNewFolderSyncState(user.device, nextSyncKey))
			.andReturn(new FolderSyncState(nextSyncKey));
	}
	
	private void expectCollectionDaoHasMapping(CollectionDao collectionDao, int collectionId) throws DaoException {
		expect(collectionDao.getCollectionMapping(user.device, user.rootCollectionPath))
				.andReturn(collectionId).anyTimes();
	}
	
	private void expectCollectionDaoHasntMapping(CollectionDao collectionDao, FolderSyncState newMappingSyncState)
			throws DaoException {
		
		expect(collectionDao.getCollectionMapping(user.device, user.rootCollectionPath))
			.andReturn(null).once();
		expect(collectionDao.addCollectionMapping(user.device, user.rootCollectionPath, newMappingSyncState))
			.andReturn(newMappingSyncState.getId()+1).once();
	}
	
	private void expectCollectionDaoHasSyncStateForKey(CollectionDao collectionDao,
			FolderSyncState currentSyncState) throws DaoException {

		expect(collectionDao.findFolderStateForKey(currentSyncState.getKey())).andReturn(currentSyncState).once();
	}

	private void expectCollectionDaoUpdateSyncState(CollectionDao collectionDao,
			FolderSyncState newSyncState) throws DaoException {
		
		expect(collectionDao.allocateNewFolderSyncState(user.device, newSyncState.getKey())).andReturn(newSyncState);
	}
}
