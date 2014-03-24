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
package org.obm.opush.command.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.obm.DateUtils.date;
import static org.obm.opush.IntegrationPushTestUtils.mockHierarchyChangesOnlyInbox;
import static org.obm.opush.IntegrationTestUtils.buildWBXMLOpushClient;
import static org.obm.opush.IntegrationTestUtils.expectAllocateFolderState;
import static org.obm.opush.IntegrationTestUtils.expectContentExporterFetching;
import static org.obm.opush.IntegrationTestUtils.expectCreateFolderMappingState;
import static org.obm.opush.IntegrationTestUtils.expectUserCollectionsNeverChange;
import static org.obm.opush.IntegrationUserAccessUtils.mockUsersAccess;
import static org.obm.opush.command.sync.EmailSyncTestUtils.checkMailFolderHasAddItems;
import static org.obm.opush.command.sync.EmailSyncTestUtils.checkMailFolderHasDeleteItems;
import static org.obm.opush.command.sync.EmailSyncTestUtils.checkMailFolderHasFetchItems;
import static org.obm.opush.command.sync.EmailSyncTestUtils.checkMailFolderHasItems;
import static org.obm.opush.command.sync.EmailSyncTestUtils.checkMailFolderHasNoChange;
import static org.obm.opush.command.sync.EmailSyncTestUtils.getCollectionWithId;
import static org.obm.opush.command.sync.EmailSyncTestUtils.lookupInbox;
import static org.obm.opush.command.sync.EmailSyncTestUtils.mockCollectionDaoForEmailSync;
import static org.obm.opush.command.sync.EmailSyncTestUtils.mockEmailSyncClasses;
import static org.obm.opush.command.sync.EmailSyncTestUtils.mockItemTrackingDao;
import static org.obm.push.bean.FilterType.THREE_DAYS_BACK;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.naming.NoPermissionException;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.easymock.IMocksControl;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.Configuration;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.guice.GuiceModule;
import org.obm.opush.IntegrationTestUtils;
import org.obm.opush.IntegrationUserAccessUtils;
import org.obm.opush.Users;
import org.obm.opush.Users.OpushUser;
import org.obm.opush.env.CassandraServer;
import org.obm.opush.env.OpushGuiceRunner;
import org.obm.push.OpushServer;
import org.obm.push.backend.DataDelta;
import org.obm.push.backend.IContentsExporter;
import org.obm.push.backend.IContentsImporter;
import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.Device;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.FolderSyncState;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.MSEmailBodyType;
import org.obm.push.bean.MSEmailHeader;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.SyncCollectionOptions;
import org.obm.push.bean.SyncCollectionResponse;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.SyncStatus;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.SyncCommand;
import org.obm.push.bean.change.client.SyncClientCommands;
import org.obm.push.bean.change.hierarchy.CollectionChange;
import org.obm.push.bean.change.item.ItemChange;
import org.obm.push.bean.change.item.ItemChangesBuilder;
import org.obm.push.bean.change.item.ItemDeletion;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.bean.ms.MSEmailBody;
import org.obm.push.exception.ConversionException;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.activesync.HierarchyChangedException;
import org.obm.push.exception.activesync.ItemNotFoundException;
import org.obm.push.exception.activesync.NotAllowedException;
import org.obm.push.mail.exception.FilterTypeChangedException;
import org.obm.push.protocol.bean.FolderSyncResponse;
import org.obm.push.protocol.bean.SyncResponse;
import org.obm.push.protocol.data.SyncDecoder;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.FolderSyncStateBackendMappingDao;
import org.obm.push.store.ItemTrackingDao;
import org.obm.push.utils.DateUtils;
import org.obm.push.utils.SerializableInputStream;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.sync.push.client.OPClient;
import org.obm.sync.push.client.commands.SyncWithDataCommand;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

@GuiceModule(SyncHandlerTestModule.class)
@RunWith(OpushGuiceRunner.class)
public class SyncHandlerTest {

	@Inject Users users;
	@Inject OpushServer opushServer;
	@Inject ClassToInstanceAgregateView<Object> classToInstanceMap;
	@Inject IMocksControl mocksControl;
	@Inject SyncDecoder decoder;
	@Inject Configuration configuration;
	@Inject IContentsImporter contentsImporter;
	@Inject SyncWithDataCommand.Factory syncWithDataCommandFactory;
	@Inject PolicyConfigurationProvider policyConfigurationProvider;
	@Inject CassandraServer cassandraServer;
	
	private List<OpushUser> userAsList;
	private CloseableHttpClient httpClient;

	@Before
	public void init() throws Exception {
		httpClient = HttpClientBuilder.create().build();
		cassandraServer.start();
		userAsList = Arrays.asList(users.jaures);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration");
	}
	
	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		httpClient.close();
	}

	@Test
	public void testSyncDefaultMailFolderUnchange() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("1");
		int syncEmailCollectionId = 4;
		DataDelta delta = DataDelta.builder()
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);

		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, inbox.getCollectionId(), THREE_DAYS_BACK, 150);

		checkMailFolderHasNoChange(syncEmailResponse, inbox.getCollectionId());
	}
	
	@Test
	public void testSyncWithWaitReturnsServerError() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("1");
		int syncEmailCollectionId = 4;
		DataDelta delta = DataDelta.builder()
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);
		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncEmailWithWait(decoder, syncEmailSyncKey, inbox.getCollectionId(), THREE_DAYS_BACK, 150);

		assertThat(syncEmailResponse.getStatus()).isEqualTo(SyncStatus.SERVER_ERROR);
	}
	
	@Test
	public void testSyncOneInboxMail() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("13424");
		int syncEmailCollectionId = 432;

		MSEmail applicationData = applicationData("text", MSEmailBodyType.PlainText);
		DataDelta delta = DataDelta.builder()
			.changes(new ItemChangesBuilder()
					.addItemChange(
						ItemChange.builder().serverId(syncEmailCollectionId + ":0")
							.data(applicationData))
					.build())
			.syncDate(new Date())
			.syncKey(syncEmailSyncKey)
			.build();

		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);
		
		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, inbox.getCollectionId(), THREE_DAYS_BACK, 150);

		checkMailFolderHasAddItems(syncEmailResponse, inbox.getCollectionId(),
				ItemChange.builder()
					.serverId(syncEmailCollectionId + ":" + 0)
					.isNew(true)
					.data(applicationData)
					.build());
	}

	@Test
	public void testSyncTwoMailButOneDisappearing() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("13424");
		int syncEmailCollectionId = 432;

		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expectUserCollectionsNeverChange(collectionDao, users.jaures, ImmutableList.of(syncEmailCollectionId));
		mockCollectionDaoForEmailSync(collectionDao, syncEmailSyncKey, ImmutableList.of(syncEmailCollectionId));
		
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockUsersAccess(classToInstanceMap, userAsList);
		
		IContentsExporter contentsExporter = classToInstanceMap.get(IContentsExporter.class);
		expect(contentsExporter.getChanged(
				anyObject(UserDataRequest.class),
				anyObject(ItemSyncState.class),
				anyObject(AnalysedSyncCollection.class),
				anyObject(SyncClientCommands.class),
				anyObject(SyncKey.class)))
				.andThrow(new ItemNotFoundException());
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);
		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, inbox.getCollectionId(), FilterType.THREE_DAYS_BACK, 100);
		
		assertThat(syncEmailResponse).isNotNull();
		assertThat(syncEmailResponse.getStatus()).isEqualTo(SyncStatus.CONVERSATION_ERROR_OR_INVALID_ITEM);
	}
	
	@Test
	public void testSyncTwoInboxMails() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("13424");
		int syncEmailCollectionId = 432;
		
		MSEmail applicationData = applicationData("text", MSEmailBodyType.PlainText);
		DataDelta delta = DataDelta.builder()
			.changes(new ItemChangesBuilder()
					.addItemChange(
						ItemChange.builder().serverId(syncEmailCollectionId + ":0")
							.data(applicationData))
					.addItemChange(
						ItemChange.builder().serverId(syncEmailCollectionId + ":1")
							.data(applicationData))
					.build())
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();

		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);
		
		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, inbox.getCollectionId(), THREE_DAYS_BACK, 150);

		checkMailFolderHasAddItems(syncEmailResponse, inbox.getCollectionId(), 
				ItemChange.builder()
					.serverId(syncEmailCollectionId + ":" + 0)
					.isNew(true)
					.data(applicationData)
					.build(),
				ItemChange.builder().serverId(syncEmailCollectionId + ":" + 1)
					.isNew(true)
					.data(applicationData)
					.build()); 
	}

	@Test
	public void testSyncOneInboxDeletedMail() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("13424");
		int syncEmailCollectionId = 432;
		
		DataDelta delta = DataDelta.builder()
			.deletions(ImmutableList.of(
					ItemDeletion.builder().serverId(syncEmailCollectionId + ":0").build()))
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();

		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);
		
		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, inbox.getCollectionId(), THREE_DAYS_BACK, 150);

		checkMailFolderHasDeleteItems(syncEmailResponse, inbox.getCollectionId(),
				ItemDeletion.builder().serverId(syncEmailCollectionId + ":" + 0).build());
	}

	@Test
	public void testSyncInboxOneNewOneDeletedMail() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("13424");
		int syncEmailCollectionId = 432;
		MSEmail applicationData = applicationData("text", MSEmailBodyType.PlainText);
		DataDelta delta = DataDelta.builder()
			.changes(new ItemChangesBuilder()
					.addItemChange(
						ItemChange.builder().serverId(syncEmailCollectionId + ":123")
							.data(applicationData))
					.build())
			.deletions(ImmutableList.of(
					ItemDeletion.builder().serverId(syncEmailCollectionId + ":122").build()))
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();

		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);

		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, inbox.getCollectionId(), THREE_DAYS_BACK, 150);

		checkMailFolderHasItems(syncEmailResponse, inbox.getCollectionId(), 
				ImmutableSet.of(ItemChange.builder()
					.serverId(syncEmailCollectionId + ":123")
					.isNew(true)
					.data(applicationData)
					.build()),
				ImmutableSet.of(ItemDeletion.builder().serverId(syncEmailCollectionId + ":122").build()));
	}

	@Test
	public void testSyncInboxFetchIdsNotEmpty() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("13424");
		int syncEmailCollectionId = 432;
		String serverId = syncEmailCollectionId + ":123";
		MSEmail applicationData = applicationData("text", MSEmailBodyType.PlainText);
		List<ItemChange> itemChanges = new ItemChangesBuilder()
				.addItemChange(
					ItemChange.builder().serverId(serverId)
						.data(applicationData))
				.build();
		DataDelta delta = DataDelta.builder()
			.changes(itemChanges)
			.deletions(ImmutableList.of(
					ItemDeletion.builder().serverId(syncEmailCollectionId + ":122").build()))
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();

		UserDataRequest userDataRequest = new UserDataRequest(users.jaures.credentials, 
				"Sync", 
				users.jaures.device);
		
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		expectContentExporterFetching(classToInstanceMap.get(IContentsExporter.class), userDataRequest, itemChanges);
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, ImmutableList.<Integer> of(syncEmailCollectionId), delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);
		
		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		SyncResponse syncEmailResponse = opClient.syncWithCommand(decoder, 
				syncEmailSyncKey, inbox.getCollectionId(), SyncCommand.FETCH, serverId);

		checkMailFolderHasFetchItems(syncEmailResponse, inbox.getCollectionId(),
				ItemChange.builder().serverId(syncEmailCollectionId + ":123")
					.isNew(false)
					.build());
	}
	
	@Test
	public void testSyncWithUnknownSyncKeyReturnsInvalidSyncKeyStatus() throws Exception {
		int collectionId= 1;
		String collectionIdAsString = String.valueOf(collectionId);
		String collectionPath = IntegrationTestUtils.buildEmailInboxCollectionPath(users.jaures); 
		
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey secondSyncKey = new SyncKey("456");
		Date initialUpdateStateDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstItemSyncState = ItemSyncState.builder().syncKey(initialSyncKey).syncDate(initialUpdateStateDate).build();
		
		IntegrationUserAccessUtils.mockUsersAccess(classToInstanceMap, userAsList);
		
		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expect(collectionDao.getCollectionPath(collectionId)).andReturn(collectionPath).times(2);
		expect(collectionDao.findItemStateForKey(initialSyncKey)).andReturn(null);
		expect(collectionDao.findItemStateForKey(secondSyncKey)).andReturn(null).times(2);
		expect(collectionDao.updateState(anyObject(Device.class), anyInt(), anyObject(SyncKey.class), anyObject(Date.class)))
			.andReturn(firstItemSyncState)
			.anyTimes();
		collectionDao.resetCollection(users.jaures.device, collectionId);
		expectLastCall();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		opClient.syncEmail(decoder, initialSyncKey, collectionIdAsString, THREE_DAYS_BACK, 100);
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondSyncKey, collectionIdAsString, THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, collectionIdAsString);
		assertThat(inboxResponse.getStatus()).isEqualTo(SyncStatus.INVALID_SYNC_KEY);
	}

	@Test
	public void testSyncWithoutOptionsAndNoOptionsInCacheTakeThePreviousOne() throws Exception {
		OpushUser user = users.jaures;
		int collectionId = 1;
		String collectionPath = IntegrationTestUtils.buildEmailInboxCollectionPath(user);
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey secondSyncKey = new SyncKey("13424");

		SyncCollectionOptions toStoreOptions = SyncCollectionOptions.builder()
				.filterType(THREE_DAYS_BACK)
				.conflict(1)
				.build();
		ItemSyncState secondRequestSyncState = ItemSyncState.builder()
				.id(4)
				.syncKey(secondSyncKey)
				.syncDate(date("2012-10-10T16:22:53"))
				.build();

		IntegrationUserAccessUtils.mockUsersAccess(classToInstanceMap, userAsList);
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(secondSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		IContentsExporter contentsExporter = classToInstanceMap.get(IContentsExporter.class);
		expect(contentsExporter.getChanged(
				anyObject(UserDataRequest.class),
				anyObject(ItemSyncState.class),
				anyObject(AnalysedSyncCollection.class),
				anyObject(SyncClientCommands.class),
				anyObject(SyncKey.class)))
			.andReturn(DataDelta.newEmptyDelta(secondRequestSyncState.getSyncDate(), secondRequestSyncState.getSyncKey()));
		
		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expect(collectionDao.getCollectionPath(collectionId)).andReturn(collectionPath).anyTimes();
		expect(collectionDao.findItemStateForKey(initialSyncKey)).andReturn(null);
		expect(collectionDao.findItemStateForKey(secondSyncKey)).andReturn(secondRequestSyncState).times(2);
		expect(collectionDao.updateState(anyObject(Device.class), anyInt(),
				anyObject(SyncKey.class), anyObject(Date.class))).andReturn(secondRequestSyncState).times(2);
		collectionDao.resetCollection(user.device, collectionId);
		expectLastCall();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getPort(), httpClient);
		
		FolderSyncResponse folderSyncResponse = opClient.folderSync(initialSyncKey);
		CollectionChange inbox = lookupInbox(folderSyncResponse.getCollectionsAddedAndUpdated());
		
		opClient.syncEmail(decoder, initialSyncKey, inbox.getCollectionId(), toStoreOptions.getFilterType(), 25);
		SyncResponse syncWithoutOptions = opClient.syncWithoutOptions(decoder, secondSyncKey, inbox.getCollectionId());
		mocksControl.verify();

		checkMailFolderHasNoChange(syncWithoutOptions, inbox.getCollectionId());
	}

	private FolderSyncState newSyncState(SyncKey syncEmailSyncKey) {
		return FolderSyncState.builder()
				.syncKey(syncEmailSyncKey)
				.build();
	}
	
	public void testPartialSyncWhenNoPreviousSendError13() throws Exception {
		SyncKey initialFolderSyncKey = new SyncKey("0");
		SyncKey nextFolderSyncKey = new SyncKey("1234");
		
		IntegrationUserAccessUtils.mockUsersAccess(classToInstanceMap, userAsList);
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(nextFolderSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		opClient.folderSync(initialFolderSyncKey);
		SyncResponse partialSyncResponse = opClient.partialSync(decoder);
		
		assertThat(partialSyncResponse.getStatus()).isEqualTo(SyncStatus.PARTIAL_REQUEST);
	}
	
	@Ignore("We don't support partial request yet")
	@Test
	public void testPartialSyncWhenValidPreviousSync() throws Exception {
		SyncKey initialFolderSyncKey = new SyncKey("0");
		SyncKey nextFolderSyncKey = new SyncKey("56789");

		SyncKey initialSyncKey = new SyncKey("1234");
		int syncEmailCollectionId = 12;
		DataDelta emptyDelta = DataDelta.builder()
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();
		
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(nextFolderSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(initialSyncKey, ImmutableSet.of(syncEmailCollectionId), emptyDelta, userAsList, classToInstanceMap);
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		opClient.folderSync(initialFolderSyncKey);
		opClient.syncEmail(decoder, initialSyncKey, syncEmailCollectionId, THREE_DAYS_BACK, 150);
		SyncResponse partialSyncResponse = opClient.partialSync(decoder);
		
		assertThat(partialSyncResponse.getStatus()).isEqualTo(SyncStatus.OK);
	}
	
	private MSEmail applicationData(String message, MSEmailBodyType emailBodyType) {
		return MSEmail.builder()
			.header(MSEmailHeader.builder().build())
			.body(MSEmailBody.builder()
					.mimeData(new SerializableInputStream(new ByteArrayInputStream(message.getBytes())))
					.bodyType(emailBodyType)
					.estimatedDataSize(0)
					.charset(Charsets.UTF_8)
					.truncated(false)
					.build())
			.build();
	}

	@Test
	public void testSyncOnUnexistingCollection() throws Exception {
		SyncKey syncEmailSyncKey = new SyncKey("1");
		java.util.Collection<Integer> existingCollections = Collections.emptySet();
		int syncEmailUnexistingCollectionId = 15105;
		DataDelta delta = DataDelta.builder()
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncEmailSyncKey, existingCollections, delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, syncEmailUnexistingCollectionId, THREE_DAYS_BACK, 25);

		SyncCollectionResponse mailboxResponse = getCollectionWithId(syncEmailResponse, String.valueOf(syncEmailUnexistingCollectionId));
		assertThat(mailboxResponse.getStatus()).isEqualTo(SyncStatus.OBJECT_NOT_FOUND);
	}

	@Test
	public void testSyncDataClassAtCalendarButRecognizedAsEmail() throws Exception {
		SyncKey syncKey = new SyncKey("1");
		int collectionId = 15105;
		List<Integer> existingCollections = ImmutableList.of(collectionId);
		DataDelta delta = DataDelta.builder()
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncKey, existingCollections, delta, userAsList, classToInstanceMap);
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		SyncResponse syncResponse = opClient.sync(decoder, syncKey, collectionId, PIMDataType.CALENDAR);

		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.SERVER_ERROR);
	}

	@Test
	public void testSyncOnHierarchyChangedException() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("1");
		int syncEmailCollectionId = 4;
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockUsersAccess(classToInstanceMap, userAsList);
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncThrowsException(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), 
				new HierarchyChangedException(new NotAllowedException("Not allowed")));
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		opClient.folderSync(initialSyncKey);
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, syncEmailCollectionId, FilterType.THREE_DAYS_BACK, 100);

		assertThat(syncEmailResponse).isNotNull();
		assertThat(syncEmailResponse.getStatus()).isEqualTo(SyncStatus.HIERARCHY_CHANGED);
	}

	@Test
	public void testSyncOnIllegalArgumentException() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_FOLDER_SYNC_KEY;
		SyncKey syncEmailSyncKey = new SyncKey("1");
		int syncEmailCollectionId = 4;
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncEmailSyncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockUsersAccess(classToInstanceMap, userAsList);
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncThrowsException(syncEmailSyncKey, Sets.newHashSet(syncEmailCollectionId), 
				new IllegalArgumentException("Illegal"));
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		opClient.folderSync(initialSyncKey);
		SyncResponse syncEmailResponse = opClient.syncEmail(decoder, syncEmailSyncKey, syncEmailCollectionId, FilterType.THREE_DAYS_BACK, 100);

		assertThat(syncEmailResponse).isNotNull();
		assertThat(syncEmailResponse.getStatus()).isEqualTo(SyncStatus.SERVER_ERROR);
	}

	private void mockEmailSyncThrowsException(SyncKey syncKey, Set<Integer> syncEmailCollectionsIds, Throwable throwable)
			throws DaoException, ConversionException, FilterTypeChangedException {

		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expectUserCollectionsNeverChange(collectionDao, users.jaures, syncEmailCollectionsIds);
		mockCollectionDaoForEmailSync(collectionDao, syncKey, syncEmailCollectionsIds);
		
		ItemTrackingDao itemTrackingDao = classToInstanceMap.get(ItemTrackingDao.class);
		mockItemTrackingDao(itemTrackingDao);
		
		IContentsExporter contentsExporterBackend = classToInstanceMap.get(IContentsExporter.class);
		expect(contentsExporterBackend.getChanged(
				anyObject(UserDataRequest.class), 
				anyObject(ItemSyncState.class),
				anyObject(AnalysedSyncCollection.class),
				anyObject(SyncClientCommands.class),
				anyObject(SyncKey.class)))
				.andThrow(throwable);
	}

	@Test
	public void testSyncWithAddCommandButWithoutApplicationDataGetsProtocolError() throws Exception {
		testSyncWithGivenCommandButWithoutApplicationDataGetsProtocolError(SyncCommand.ADD);
	}

	@Test
	public void testSyncWithChangeCommandButWithoutApplicationDataGetsProtocolError() throws Exception {
		testSyncWithGivenCommandButWithoutApplicationDataGetsProtocolError(SyncCommand.CHANGE);
	}

	private void testSyncWithGivenCommandButWithoutApplicationDataGetsProtocolError(SyncCommand command) throws Exception {
		SyncKey syncKey = new SyncKey("1");
		List<Integer> existingCollections = ImmutableList.of(15);
		DataDelta delta = DataDelta.builder()
			.syncDate(new Date())
			.syncKey(new SyncKey("123"))
			.build();
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncKey, existingCollections, delta, userAsList, classToInstanceMap);

		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		SyncResponse syncResponse = opClient.syncWithCommand(decoder, syncKey, "15", command, "15:51");

		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.PROTOCOL_ERROR);
	}

	@Test
	public void testAddLeadingToNoPermissionExceptionReplyNothing() throws Exception {
		TimeZone defaultTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(DateTimeZone.UTC.toTimeZone());
		
		SyncKey syncKey = new SyncKey("13424");
		int collectionId = 1;
		List<Integer> existingCollections = ImmutableList.of(collectionId);
		String serverId = null;
		String clientId = "156";

		DataDelta serverDataDelta = DataDelta.newEmptyDelta(date("2012-10-10T16:22:53"), syncKey);
		
		MSEmail clientData = MSEmail.builder()
			.header(MSEmailHeader.builder().build())
			.body(MSEmailBody.builder()
					.mimeData(new SerializableInputStream(new ByteArrayInputStream("obm".getBytes())))
					.bodyType(MSEmailBodyType.PlainText)
					.estimatedDataSize(0)
					.charset(Charsets.UTF_8)
					.truncated(false)
					.build())
			.build();
		
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncKey, existingCollections, serverDataDelta, userAsList, classToInstanceMap);
		
		UserDataRequest udr = new UserDataRequest(users.jaures.credentials, "Sync", users.jaures.device);
		expect(contentsImporter.importMessageChange(udr, collectionId, serverId, clientId, clientData))
			.andThrow(new NoPermissionException());
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		SyncResponse syncResponse = opClient.syncWithCommand(syncWithDataCommandFactory, users.jaures.device, 
				syncKey, String.valueOf(collectionId), SyncCommand.ADD, serverId, clientId, clientData);

		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		checkMailFolderHasNoChange(syncResponse, String.valueOf(collectionId));
		TimeZone.setDefault(defaultTimeZone);
	}

	@Test
	public void testChangeLeadingToNoPermissionExceptionReplyNothing() throws Exception {
		TimeZone defaultTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(DateTimeZone.UTC.toTimeZone());
		
		SyncKey syncKey = new SyncKey("13424");
		int collectionId = 1;
		List<Integer> existingCollections = ImmutableList.of(collectionId);
		String serverId = "432:1456";
		String clientId = null;

		DataDelta serverDataDelta = DataDelta.newEmptyDelta(date("2012-10-10T16:22:53"), syncKey);

		MSEmail clientData = MSEmail.builder()
			.header(MSEmailHeader.builder().build())
			.body(MSEmailBody.builder()
					.mimeData(new SerializableInputStream(new ByteArrayInputStream("obm".getBytes())))
					.bodyType(MSEmailBodyType.PlainText)
					.estimatedDataSize(0)
					.charset(Charsets.UTF_8)
					.truncated(false)
					.build())
			.build();
		
		expectAllocateFolderState(classToInstanceMap.get(CollectionDao.class), newSyncState(syncKey));
		expectCreateFolderMappingState(classToInstanceMap.get(FolderSyncStateBackendMappingDao.class));
		mockHierarchyChangesOnlyInbox(classToInstanceMap);
		mockEmailSyncClasses(syncKey, existingCollections, serverDataDelta, userAsList, classToInstanceMap);
		
		UserDataRequest udr = new UserDataRequest(users.jaures.credentials, "Sync", users.jaures.device);
		expect(contentsImporter.importMessageChange(udr, collectionId, serverId, clientId, clientData))
			.andThrow(new NoPermissionException());
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = buildWBXMLOpushClient(users.jaures, opushServer.getPort(), httpClient);
		SyncResponse syncResponse = opClient.syncWithCommand(syncWithDataCommandFactory, users.jaures.device, 
				syncKey, String.valueOf(collectionId), SyncCommand.ADD, serverId, clientId, clientData);

		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		checkMailFolderHasNoChange(syncResponse, String.valueOf(collectionId));
		TimeZone.setDefault(defaultTimeZone);
	}
}
