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
package org.obm.opush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.obm.DateUtils.date;
import static org.obm.opush.IntegrationPushTestUtils.mockNextGeneratedSyncKey;
import static org.obm.opush.IntegrationTestUtils.buildWBXMLOpushClient;
import static org.obm.opush.IntegrationUserAccessUtils.mockUsersAccess;
import static org.obm.opush.command.sync.SyncTestUtils.assertEqualsWithoutApplicationData;
import static org.obm.opush.command.sync.SyncTestUtils.getCollectionWithId;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Files;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.Configuration;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.configuration.EmailConfiguration;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.opush.Users.OpushUser;
import org.obm.opush.env.CassandraServer;
import org.obm.push.OpushServer;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.SyncCollectionCommand;
import org.obm.push.bean.SyncCollectionResponse;
import org.obm.push.bean.SyncCollectionResponsesResponse;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.SyncStatus;
import org.obm.push.bean.change.SyncCommand;
import org.obm.push.bean.change.item.ItemChange;
import org.obm.push.bean.change.item.ItemDeletion;
import org.obm.push.exception.DaoException;
import org.obm.push.mail.MailboxService;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.protocol.bean.SyncResponse;
import org.obm.push.protocol.data.SyncDecoder;
import org.obm.push.service.DateService;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.ItemTrackingDao;
import org.obm.push.utils.DateUtils;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.sync.push.client.OPClient;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.icegreen.greenmail.imap.ImapHostManager;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;

@RunWith(GuiceRunner.class)
@GuiceModule(MailBackendTestModule.class)
public class MailBackendGetChangedTest {

	@Inject	Users users;
	@Inject	OpushServer opushServer;
	@Inject	ClassToInstanceAgregateView<Object> classToInstanceMap;
	@Inject GreenMail greenMail;
	@Inject ImapConnectionCounter imapConnectionCounter;
	@Inject PendingQueriesLock pendingQueries;
	@Inject IMocksControl mocksControl;
	@Inject Configuration configuration;
	@Inject MailboxService mailboxService;
	@Inject SyncDecoder decoder;
	@Inject PolicyConfigurationProvider policyConfigurationProvider;
	@Inject CassandraServer cassandraServer;
	
	private ItemTrackingDao itemTrackingDao;
	private CollectionDao collectionDao;
	private DateService dateService;

	private GreenMailUser greenMailUser;
	private ImapHostManager imapHostManager;
	private OpushUser user;
	private String mailbox;
	private String inboxCollectionPath;
	private CollectionId inboxCollectionId;
	private String trashCollectionPath;
	private CollectionId trashCollectionId;
	private CloseableHttpClient httpClient;

	@Before
	public void init() throws Exception {
		httpClient = HttpClientBuilder.create().build();
		cassandraServer.start();
		user = users.jaures;
		greenMail.start();
		mailbox = user.user.getLoginAtDomain();
		greenMailUser = greenMail.setUser(mailbox, String.valueOf(user.password));
		imapHostManager = greenMail.getManagers().getImapHostManager();
		imapHostManager.createMailbox(greenMailUser, "Trash");

		inboxCollectionPath = IntegrationTestUtils.buildEmailInboxCollectionPath(user);
		inboxCollectionId = CollectionId.of(1234);
		trashCollectionPath = IntegrationTestUtils.buildEmailTrashCollectionPath(user);
		trashCollectionId = CollectionId.of(1645);
		
		itemTrackingDao = classToInstanceMap.get(ItemTrackingDao.class);
		collectionDao = classToInstanceMap.get(CollectionDao.class);
		dateService = classToInstanceMap.get(DateService.class);

		bindCollectionIdToPath();

		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration");
	}

	private void bindCollectionIdToPath() throws Exception {
		expect(collectionDao.getCollectionPath(inboxCollectionId)).andReturn(inboxCollectionPath).anyTimes();
		expect(collectionDao.getCollectionPath(trashCollectionId)).andReturn(trashCollectionPath).anyTimes();
	}

	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		greenMail.stop();
		httpClient.close();
		Files.delete(configuration.dataDir);
	}

	@Test
	public void testInitialGetChangedWithNoSnapshot() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState allocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(allocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, allocatedState, inboxCollectionId);

		 
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		SyncResponse firstSyncResponse = opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		SyncResponse syncResponse = opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse firstInboxResponse = getCollectionWithId(firstSyncResponse, inboxCollectionId);
		SyncCollectionResponse secondInboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		
		assertThat(firstInboxResponse.getItemChanges()).isEmpty();
		assertEqualsWithoutApplicationData(secondInboxResponse.getItemChanges(), 
			ImmutableList.of(
				ItemChange.builder()
					.serverId(inboxCollectionId.serverId(1))
					.isNew(true)
					.build(),
				ItemChange.builder()
					.serverId(inboxCollectionId.serverId(2))
					.isNew(true)
					.build()));

		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 2);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testInitialGetChangedWithSnapshotNoChanges() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		SyncResponse firstSyncResponse = opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse firstInboxResponse = getCollectionWithId(firstSyncResponse, inboxCollectionId);
		SyncCollectionResponse secondInboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		
		assertThat(firstInboxResponse.getItemChanges()).isEmpty();
		assertThat(secondInboxResponse.getItemChanges()).isEmpty();

		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 2);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testInitialGetChangedWithSnapshotWithChanges() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(currentAllocatedState, inboxCollectionId.serverId(3))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(currentAllocatedState, inboxCollectionId.serverId(4))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		SyncResponse firstSyncResponse = opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		sendTwoEmailsToImapServer();
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse firstInboxResponse = getCollectionWithId(firstSyncResponse, inboxCollectionId);
		SyncCollectionResponse secondInboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		
		assertThat(firstInboxResponse.getItemChanges()).isEmpty();
		assertEqualsWithoutApplicationData(secondInboxResponse.getItemChanges(), 
				ImmutableList.of(
					ItemChange.builder()
						.serverId(inboxCollectionId.serverId(3))
						.isNew(true)
						.build(),
					ItemChange.builder()
						.serverId(inboxCollectionId.serverId(4))
						.isNew(true)
						.build()));
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 4);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testInitialGetChangedNoSnapshotWithMarkAsDeleteMails() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		itemTrackingDao.markAsDeleted(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		greenMail.deleteEmailFromInbox(greenMailUser, 1);
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		SyncResponse syncResponse = opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse secondInboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		assertThat(secondInboxResponse.getItemDeletions()).isEmpty();
		assertEqualsWithoutApplicationData(secondInboxResponse.getItemChanges(), 
				ImmutableList.of(
					ItemChange.builder()
						.serverId(inboxCollectionId.serverId(2))
						.isNew(true)
						.build()));
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 2);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testInitialGetChangedWithSnapshotWithMarkAsDeleteMails() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		itemTrackingDao.markAsDeleted(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		greenMail.deleteEmailFromInbox(greenMailUser, 1);
		
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse secondInboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		assertThat(secondInboxResponse.getItemChanges()).isEmpty();
		assertThat(secondInboxResponse.getItemDeletions()).containsOnly( 
				ItemDeletion.builder()
					.serverId(inboxCollectionId.serverId(1))
					.build());
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 2);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testGetChangedWithFilterTypeChange() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate());
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expect(collectionDao.findItemStateForKey(secondAllocatedSyncKey)).andReturn(currentAllocatedState);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		sendTwoEmailsToImapServer();
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.ONE_DAY_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		
		assertThat(inboxResponse.getStatus()).isEqualTo(SyncStatus.INVALID_SYNC_KEY);
	}
	
	@Test
	public void testGetChangedWithClientDeletionReturnResponseWithDeletion() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		itemTrackingDao.markAsDeleted(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		SyncResponse syncResponse = opClient.deleteEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, inboxCollectionId.serverId(1));
		mocksControl.verify();

		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		assertThat(inboxResponse.getItemChanges()).isEmpty();
		List<SyncCollectionCommand> deletions = inboxResponse.getResponses().getCommandsForType(SyncCommand.DELETE);
		assertThat(deletions).hasSize(1);
		SyncCollectionCommand deletion = deletions.get(0);
		assertThat(deletion.getServerId()).isEqualTo(CollectionId.of(1234).serverId(1));
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 1);
	}
	
	@Test
	public void windowingShouldSupportItemDeletion() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53Z"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26Z"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).times(3);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, inboxCollectionId);
		expect(collectionDao.findItemStateForKey(secondAllocatedSyncKey)).andReturn(secondAllocatedState);
		expect(collectionDao.updateState(user.device, inboxCollectionId, newAllocatedSyncKey, secondAllocatedState.getSyncDate()))
			.andReturn(newAllocatedState);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(3))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(4))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(secondAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendNEmailsToImapServer(4);
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 2);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 2);

		greenMail.deleteEmailFromInbox(greenMailUser, 1);
		greenMail.expungeInbox(greenMailUser);
		
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 2);
		mocksControl.verify();

		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 3);
		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		assertThat(inboxResponse.getItemDeletions()).isEmpty();
		assertEqualsWithoutApplicationData(inboxResponse.getItemChanges(), 
				ImmutableList.of(
					ItemChange.builder()
						.serverId(inboxCollectionId.serverId(2))
						.isNew(true)
						.build()));
	}
	
	@Test
	public void testGetChangedShouldReturnDeleteResponseAskByClient() throws Exception {
		int emailId1 = 1;
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(2))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		itemTrackingDao.markAsDeleted(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		SyncResponse syncResponse = opClient.deleteEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, inboxCollectionId.serverId(1));
		mocksControl.verify();

		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 1);
		List<ServerId> deletions = inboxResponse.getResponses().deletions();
		assertThat(deletions).containsOnly(inboxCollectionId.serverId(emailId1));
	}

	@Test
	public void testGetChangedOnTrashReturnsPreviousClientDeletion() throws Exception {
		int emailId1 = 1;
		int emailId2 = 2;
		int trashEmailId = 1;
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey thirdAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		SyncKey firstAllocatedSyncKeyTrash = new SyncKey("86cc9cc6-db13-4c06-87d7-fa2269c567b5");
		SyncKey secondAllocatedSyncKeyTrash = new SyncKey("c4c558f0-d205-40f4-9292-3ad359e94c2a");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int allocatedStateId3 = 5;
		int allocatedStateId4 = 6;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey,
				thirdAllocatedSyncKey, firstAllocatedSyncKeyTrash, secondAllocatedSyncKeyTrash);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState thirdAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(thirdAllocatedSyncKey)
				.id(allocatedStateId3)
				.build();
		ItemSyncState firstAllocatedStateTrash = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKeyTrash)
				.id(allocatedStateId4)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).anyTimes();
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(thirdAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(firstAllocatedStateTrash.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, secondAllocatedState, thirdAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformInitialSync(firstAllocatedStateTrash, trashCollectionId);
		expect(collectionDao.findItemStateForKey(secondAllocatedSyncKeyTrash)).andReturn(firstAllocatedStateTrash);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(emailId1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(emailId2))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedStateTrash, trashCollectionId.serverId(trashEmailId))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		itemTrackingDao.markAsDeleted(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendTwoEmailsToImapServer();
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.deleteEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, inboxCollectionId.serverId(emailId1));
		opClient.syncEmail(decoder, initialSyncKey, trashCollectionId, FilterType.THREE_DAYS_BACK, 100);
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKeyTrash, trashCollectionId, FilterType.THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, trashCollectionId);
		assertThat(inboxResponse.getItemDeletions()).isEmpty();
		assertEqualsWithoutApplicationData(inboxResponse.getItemChanges(), 
				ImmutableList.of(
					ItemChange.builder()
						.serverId(inboxCollectionId.serverId(trashEmailId))
						.isNew(true)
						.build()));
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 1);
	}
	
	@Test
	public void testGetChangedWithReadFlag() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(1))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(currentAllocatedState, inboxCollectionId.serverId(1))).andReturn(true);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		
		GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject2", "body", greenMail.getSmtp().getServerSetup());
		greenMail.waitForIncomingEmail(1);
		
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);
		
		MailFolder folder = imapHostManager.getFolder(greenMailUser, EmailConfiguration.IMAP_INBOX_NAME);
		folder.setFlags(new Flags(Flag.SEEN), true, 1, null, true);
		
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);
		mocksControl.verify();

		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		assertEqualsWithoutApplicationData(inboxResponse.getItemChanges(), 
				ImmutableList.of(
					ItemChange.builder()
						.serverId(inboxCollectionId.serverId(1))
						.isNew(false)
						.build()));
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 1);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testGetChangedWithDeletedFlag() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:17:26"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate()).times(2);
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		ServerId serverId = inboxCollectionId.serverId(1);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, serverId)).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		itemTrackingDao.markAsDeleted(newAllocatedState, ImmutableSet.of(serverId));
		expectLastCall().once();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		
		GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject2", "body", greenMail.getSmtp().getServerSetup());
		greenMail.waitForIncomingEmail(1);
		
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		
		MailFolder folder = imapHostManager.getFolder(greenMailUser, EmailConfiguration.IMAP_INBOX_NAME);
		folder.setFlags(new Flags(Flag.DELETED), true, 1, null, true);
		
		SyncResponse syncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 100);
		mocksControl.verify();

		SyncCollectionResponse inboxResponse = getCollectionWithId(syncResponse, inboxCollectionId);
		assertThat(inboxResponse.getItemDeletions()).containsOnly(
				ItemDeletion.builder().serverId(serverId).build());
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 1);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testGetChangedWithWindowsSize() throws Exception {
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey thirdAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int allocatedStateId3 = 5;
		int windowSize = 3;
		int numberOfEmails = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, thirdAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();

		ItemSyncState thirdAllocatedState = ItemSyncState.builder()
				.syncDate(secondAllocatedState.getSyncDate())
				.syncKey(thirdAllocatedSyncKey)
				.id(allocatedStateId3)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).times(3);

		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, inboxCollectionId);
		expect(collectionDao.findItemStateForKey(secondAllocatedSyncKey)).andReturn(secondAllocatedState);
		expect(collectionDao.updateState(user.device, inboxCollectionId, thirdAllocatedState.getSyncKey(), thirdAllocatedState.getSyncDate()))
			.andReturn(thirdAllocatedState);

		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();

		expect(itemTrackingDao.isServerIdSynced(eq(firstAllocatedState), anyObject(ServerId.class))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(eq(firstAllocatedState), anyObject(ServerId.class))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(eq(firstAllocatedState), anyObject(ServerId.class))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(eq(secondAllocatedState), anyObject(ServerId.class))).andReturn(false);
		expect(itemTrackingDao.isServerIdSynced(eq(secondAllocatedState), anyObject(ServerId.class))).andReturn(false);
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		sendNEmailsToImapServer(numberOfEmails);
		
		SyncResponse initialSyncResponse = opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, windowSize);
		SyncCollectionResponse initialInboxResponse = getCollectionWithId(initialSyncResponse, inboxCollectionId);
		assertThat(initialInboxResponse.getSyncKey()).isEqualTo(firstAllocatedSyncKey);
		
		SyncResponse firstPartSyncResponse = opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, windowSize);
		SyncCollectionResponse inboxResponse = getCollectionWithId(firstPartSyncResponse, inboxCollectionId);
		assertThat(inboxResponse.getItemChanges()).hasSize(windowSize);
		assertThat(inboxResponse.getSyncKey()).isEqualTo(secondAllocatedSyncKey);
		
		SyncResponse lastPartSyncResponse = opClient.syncEmail(decoder, secondAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, windowSize);
		mocksControl.verify();
		
		SyncCollectionResponse lastInboxResponse = getCollectionWithId(lastPartSyncResponse, inboxCollectionId);
		assertThat(lastInboxResponse.getItemChanges()).hasSize(numberOfEmails - windowSize);
		assertThat(lastInboxResponse.getSyncKey()).isEqualTo(thirdAllocatedSyncKey);
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, numberOfEmails);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testFetchCommandGenerateSyncKey() throws Exception {
		int emailId = 1;
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey newAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int newAllocatedStateId = 5;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, newAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState currentAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState newAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(newAllocatedSyncKey)
				.id(newAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(currentAllocatedState.getSyncDate());
		expect(dateService.getCurrentDate()).andReturn(newAllocatedState.getSyncDate());
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, currentAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, currentAllocatedState, newAllocatedState, inboxCollectionId);

		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, inboxCollectionId.serverId(emailId))).andReturn(false);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		
		GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject2", "body", greenMail.getSmtp().getServerSetup());
		greenMail.waitForIncomingEmail(1);
		
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);

		ServerId serverId = inboxCollectionId.serverId(1);
		SyncResponse syncResponseWithFetch = opClient.syncWithCommand(
				decoder, secondAllocatedSyncKey, inboxCollectionId, SyncCommand.FETCH, serverId);
		
		mocksControl.verify();

		SyncCollectionResponse lastInboxResponse = getCollectionWithId(syncResponseWithFetch, inboxCollectionId);
		assertThat(lastInboxResponse.getSyncKey()).isEqualTo(newAllocatedSyncKey);
		
		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 1);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testFetchShouldReturnFetchErrorOnlyWhenEmailDeleted() throws Exception {
		int emailId = 1;
		SyncKey initialSyncKey = SyncKey.INITIAL_SYNC_KEY;
		SyncKey firstAllocatedSyncKey = new SyncKey("e05fe721-adf6-416d-a2d9-657347096aa1");
		SyncKey secondAllocatedSyncKey = new SyncKey("64dd1fc0-3519-480a-850f-b84c0153855d");
		SyncKey thirdAllocatedSyncKey = new SyncKey("720fc208-1e70-43a1-bfad-112d64548c7b");
		SyncKey lastAllocatedSyncKey = new SyncKey("be61114c-34a6-4453-8847-944cf54bb4ea");
		int allocatedStateId = 3;
		int allocatedStateId2 = 4;
		int allocatedStateId3 = 5;
		int lastAllocatedStateId = 6;
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, firstAllocatedSyncKey, secondAllocatedSyncKey, thirdAllocatedSyncKey, lastAllocatedSyncKey);
		
		Date initialDate = DateUtils.getEpochPlusOneSecondCalendar().getTime();
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(initialDate)
				.syncKey(firstAllocatedSyncKey)
				.id(allocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T16:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(allocatedStateId2)
				.build();
		ItemSyncState thirdAllocatedState = ItemSyncState.builder()
				.syncDate(secondAllocatedState.getSyncDate())
				.syncKey(thirdAllocatedSyncKey)
				.id(allocatedStateId3)
				.build();
		ItemSyncState lastAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-10T18:22:53"))
				.syncKey(lastAllocatedSyncKey)
				.id(lastAllocatedStateId)
				.build();
		expect(dateService.getEpochPlusOneSecondDate()).andReturn(initialDate).once();
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).times(2);
		expect(dateService.getCurrentDate()).andReturn(thirdAllocatedState.getSyncDate());
		expect(dateService.getCurrentDate()).andReturn(lastAllocatedState.getSyncDate());
		expectCollectionDaoPerformInitialSync(firstAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(secondAllocatedSyncKey, secondAllocatedState, thirdAllocatedState, inboxCollectionId);
		expectCollectionDaoPerformSync(thirdAllocatedSyncKey, thirdAllocatedState, lastAllocatedState, inboxCollectionId);
		
		ServerId serverId = inboxCollectionId.serverId(emailId);
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, serverId)).andReturn(false);
		itemTrackingDao.markAsSynced(secondAllocatedState, ImmutableSet.of(serverId));
		expectLastCall().anyTimes();
		itemTrackingDao.markAsDeleted(lastAllocatedState, ImmutableSet.of(serverId));
		expectLastCall().once();
		
		sendNEmailsToImapServer(1);
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		opClient.syncEmail(decoder, initialSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);
		opClient.syncEmail(decoder, firstAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);
		
		greenMail.deleteEmailFromInbox(greenMailUser, 1);
		greenMail.expungeInbox(greenMailUser);
		
		SyncResponse response = opClient.syncWithCommand(decoder, secondAllocatedSyncKey, inboxCollectionId, SyncCommand.FETCH, serverId);
		SyncResponse responseContainingDeletion = opClient.syncEmail(decoder, thirdAllocatedSyncKey, inboxCollectionId, FilterType.THREE_DAYS_BACK, 25);
		
		mocksControl.verify();
		
		assertThat(response.getStatus()).isEqualTo(SyncStatus.OK);
		SyncCollectionResponse inboxResponse = getCollectionWithId(response, inboxCollectionId);
		SyncCollectionResponsesResponse responses = inboxResponse.getResponses();
		assertThat(inboxResponse.isMoreAvailable()).isFalse();
		assertThat(responses.adds()).isEmpty();
		assertThat(responses.updates()).isEmpty();
		assertThat(responses.deletions()).isEmpty();
		assertThat(responses.fetches()).hasSize(1);
		assertThat(responses.getCommandsForType(SyncCommand.FETCH)).containsOnly(SyncCollectionCommand.builder()
				.status(SyncStatus.OBJECT_NOT_FOUND)
				.serverId(serverId)
				.type(SyncCommand.FETCH)
				.build());

		assertThat(responseContainingDeletion.getStatus()).isEqualTo(SyncStatus.OK);
		SyncCollectionResponse inboxResponseDeletion = getCollectionWithId(responseContainingDeletion, inboxCollectionId);
		assertThat(inboxResponseDeletion.isMoreAvailable()).isFalse();
		assertThat(inboxResponseDeletion.getItemDeletions()).containsOnly(
				ItemDeletion.builder().serverId(serverId).build());
	}

	private void expectCollectionDaoPerformSync(SyncKey requestSyncKey,
			ItemSyncState allocatedState, ItemSyncState newItemSyncState, CollectionId collectionId)
					throws DaoException {
		expect(collectionDao.findItemStateForKey(requestSyncKey)).andReturn(allocatedState);
		expect(collectionDao.updateState(user.device, collectionId, newItemSyncState.getSyncKey(), newItemSyncState.getSyncDate()))
				.andReturn(newItemSyncState);
	}

	private void expectCollectionDaoPerformInitialSync(ItemSyncState itemSyncState, CollectionId collectionId)
					throws DaoException {
		
		expect(collectionDao.updateState(user.device, collectionId, itemSyncState.getSyncKey(), itemSyncState.getSyncDate()))
			.andReturn(itemSyncState);
		collectionDao.resetCollection(user.device, collectionId);
		expectLastCall();
	}

	private void sendTwoEmailsToImapServer() throws InterruptedException {
		GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject", "body", greenMail.getSmtp().getServerSetup());
		GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject2", "body", greenMail.getSmtp().getServerSetup());
		greenMail.waitForIncomingEmail(2);
	}

	private void sendNEmailsToImapServer(int numberOfEmails) throws InterruptedException {
		for (int i = 0; i< numberOfEmails; i++) {
			GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject" + i, "body", greenMail.getSmtp().getServerSetup());
		}
		greenMail.waitForIncomingEmail(numberOfEmails);
	}

	private void assertEmailCountInMailbox(String mailbox, Integer expectedNumberOfEmails) {
		MailFolder inboxFolder = imapHostManager.getFolder(greenMailUser, mailbox);
		Assertions.assertThat(inboxFolder.getMessageCount()).isEqualTo(expectedNumberOfEmails);
	}
}
