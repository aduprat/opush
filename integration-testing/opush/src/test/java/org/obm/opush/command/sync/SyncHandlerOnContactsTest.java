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
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.obm.DateUtils.date;
import static org.obm.opush.IntegrationPushTestUtils.mockNextGeneratedSyncKey;
import static org.obm.opush.IntegrationTestUtils.buildWBXMLOpushClient;
import static org.obm.opush.IntegrationUserAccessUtils.mockUsersAccess;
import static org.obm.opush.command.sync.SyncTestUtils.getCollectionWithId;
import static org.obm.opush.command.sync.SyncTestUtils.mockCollectionDaoPerformSync;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.assertj.core.util.Files;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.Configuration;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.opush.IntegrationTestUtils;
import org.obm.opush.Users;
import org.obm.opush.Users.OpushUser;
import org.obm.opush.env.CassandraServer;
import org.obm.push.OpushServer;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.MSContact;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.SyncCollectionCommandResponse;
import org.obm.push.bean.SyncCollectionResponse;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.SyncStatus;
import org.obm.push.bean.change.SyncCommand;
import org.obm.push.protocol.bean.SyncResponse;
import org.obm.push.protocol.data.SyncDecoder;
import org.obm.push.service.DateService;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.ItemTrackingDao;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.sync.base.EmailAddress;
import org.obm.sync.book.AddressBook;
import org.obm.sync.book.Contact;
import org.obm.sync.book.Phone;
import org.obm.sync.client.book.BookClient;
import org.obm.sync.items.ContactChanges;
import org.obm.sync.push.client.WBXMLOPClient;
import org.obm.sync.push.client.beans.Folder;
import org.obm.sync.push.client.commands.SyncWithDataCommand;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

@GuiceModule(SyncHandlerWithBackendTestModule.class)
@RunWith(GuiceRunner.class)
public class SyncHandlerOnContactsTest {

	@Inject	Users users;
	@Inject	OpushServer opushServer;
	@Inject	ClassToInstanceAgregateView<Object> classToInstanceMap;
	@Inject IMocksControl mocksControl;
	@Inject Configuration configuration;
	@Inject SyncDecoder decoder;
	@Inject SyncWithDataCommand.Factory syncWithDataCommandFactory;
	@Inject PolicyConfigurationProvider policyConfigurationProvider;
	@Inject CassandraServer cassandraServer;
	
	private ItemTrackingDao itemTrackingDao;
	private CollectionDao collectionDao;
	private BookClient bookClient;
	private DateService dateService;

	private OpushUser user;
	private String contactCollectionPath;
	private int contactCollectionId;
	private String contactCollectionIdAsString;

	private CloseableHttpClient httpClient;

	@Before
	public void init() throws Exception {
		httpClient = HttpClientBuilder.create().build();
		cassandraServer.start();
		user = users.jaures;

		contactCollectionId = 7891;
		contactCollectionIdAsString = String.valueOf(contactCollectionId);
		contactCollectionPath = IntegrationTestUtils.buildContactCollectionPath(user, contactCollectionIdAsString);
		
		itemTrackingDao = classToInstanceMap.get(ItemTrackingDao.class);
		collectionDao = classToInstanceMap.get(CollectionDao.class);
		bookClient = classToInstanceMap.get(BookClient.class);
		dateService = classToInstanceMap.get(DateService.class);

		expect(collectionDao.getCollectionPath(contactCollectionId)).andReturn(contactCollectionPath).anyTimes();
		
		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration");
	}

	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		httpClient.close();
		Files.delete(configuration.dataDir);
	}

	@Test
	public void newContactOnServerShouldBeSentToClient() throws Exception {
		SyncKey firstAllocatedSyncKey = new SyncKey("4a2c7db8-b532-40a0-92c3-bfebb8da8f00");
		SyncKey secondAllocatedSyncKey = new SyncKey("55df3cf4-b70d-4df2-ac48-d31646994321");
		int firstAllocatedStateId = 3;
		int secondAllocatedStateId = 4;
		
		Date syncDate = date("2012-10-09T16:22:53");
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(firstAllocatedSyncKey)
				.id(firstAllocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(secondAllocatedSyncKey)
				.id(secondAllocatedStateId)
				.build();
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, secondAllocatedSyncKey);
		mockCollectionDaoPerformSync(collectionDao, user.device, firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, contactCollectionId);
		
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).once();
		Contact initialContact = new Contact();
		initialContact.setUid(1);
		initialContact.setFirstname("firstname");
		initialContact.setLastname("lastname");
		initialContact.setEmails(ImmutableMap.of("INTERNET;X-OBM-Ref1", EmailAddress.loginAtDomain("contact@mydomain.org")));
		initialContact.setPhones(ImmutableMap.of("HOME;FAX;X-OBM-Ref1", new Phone("1234")));
		
		ServerId serverId = new ServerId(contactCollectionIdAsString + ":" + initialContact.getUid());
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, serverId))
			.andReturn(false);
		
		itemTrackingDao.markAsSynced(secondAllocatedState, ImmutableSet.of(serverId));
		expectLastCall().once();

		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build()));
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(initialContact),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		mocksControl.replay();
		opushServer.start();

		WBXMLOPClient opushClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		SyncResponse syncResponse = opushClient.sync(decoder, firstAllocatedSyncKey, new Folder(contactCollectionIdAsString));
		
		mocksControl.verify();
		SyncCollectionCommandResponse expectedCommandResponse = SyncCollectionCommandResponse.builder()
				.type(SyncCommand.ADD)
				.serverId(serverId.toString())
				.clientId(null)
				.build();
		
		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		SyncCollectionResponse syncCollectionResponse = getCollectionWithId(syncResponse, contactCollectionIdAsString);
		assertThat(syncCollectionResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		List<SyncCollectionCommandResponse> commands = syncCollectionResponse.getCommands().getCommands();
		assertThat(commands).hasSize(1);
		SyncCollectionCommandResponse syncCollectionCommandResponse = FluentIterable.from(commands).first().get();
		assertThat(syncCollectionCommandResponse).isEqualTo(expectedCommandResponse);
		
		MSContact msContact = (MSContact) syncCollectionCommandResponse.getApplicationData();
		assertThat(msContact.getFirstName()).isEqualTo("firstname");
		assertThat(msContact.getLastName()).isEqualTo("lastname");
		assertThat(msContact.getEmail1Address()).isEqualTo("contact@mydomain.org");
		assertThat(msContact.getHomeFaxNumber()).isEqualTo("1234");
	}

	@Test
	public void newContactOnClientShouldBePopulatedToServer() throws Exception {
		SyncKey firstAllocatedSyncKey = new SyncKey("4a2c7db8-b532-40a0-92c3-bfebb8da8f00");
		SyncKey secondAllocatedSyncKey = new SyncKey("55df3cf4-b70d-4df2-ac48-d31646994321");
		SyncKey thirdAllocatedSyncKey = new SyncKey("4872672c-95b9-4c1c-90ae-ebea7d0e83ed");
		int firstAllocatedStateId = 3;
		int secondAllocatedStateId = 4;
		int thirdAllocatedStateId = 5;
		
		Date syncDate = date("2012-10-09T16:22:53");
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(firstAllocatedSyncKey)
				.id(firstAllocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(secondAllocatedSyncKey)
				.id(secondAllocatedStateId)
				.build();
		ItemSyncState thirdAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(thirdAllocatedSyncKey)
				.id(thirdAllocatedStateId)
				.build();
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, secondAllocatedSyncKey, thirdAllocatedSyncKey);
		mockCollectionDaoPerformSync(collectionDao, user.device, firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, contactCollectionId);
		mockCollectionDaoPerformSync(collectionDao, user.device, secondAllocatedSyncKey, secondAllocatedState, thirdAllocatedState, contactCollectionId);
		
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).once();
		expect(dateService.getCurrentDate()).andReturn(thirdAllocatedState.getSyncDate()).once();
		// first sync
		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build()));
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		// second sync
		String clientId = "123";
		String hashedClientId = "e7cf79a18a015f6b4a97d26b1a07ca70ab6c703a";
		MSContact createdMSContact = new MSContact();
		createdMSContact.setFirstName("firstname");
		createdMSContact.setLastName("lastname");
		createdMSContact.setEmail1Address("contact@mydomain.org");
		createdMSContact.setFileAs("lastname, firstname");
		
		Contact createdContact = new Contact();
		createdContact.setFirstname("firstname");
		createdContact.setLastname("lastname");
		createdContact.setEmails(ImmutableMap.of("INTERNET;X-OBM-Ref1", EmailAddress.loginAtDomain("contact@mydomain.org")));
		
		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build())).times(2);
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId).times(2);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		expect(bookClient.storeContact(user.accessToken, contactCollectionId, createdContact, hashedClientId))
			.andReturn(createdContact);
		
		mocksControl.replay();
		opushServer.start();

		WBXMLOPClient opushClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		opushClient.sync(decoder, firstAllocatedSyncKey, new Folder(contactCollectionIdAsString));
		
		SyncResponse updateSyncResponse = opushClient.syncWithCommand(syncWithDataCommandFactory, user.device, secondAllocatedSyncKey, contactCollectionIdAsString, SyncCommand.ADD, 
				null, clientId, createdMSContact);
		
		mocksControl.verify();
		assertThat(updateSyncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		SyncCollectionResponse syncCollectionResponse = getCollectionWithId(updateSyncResponse, contactCollectionIdAsString);
		assertThat(syncCollectionResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		assertThat(syncCollectionResponse.getCommands().getCommands()).hasSize(0);
	}

	@Test
	public void updatingContactShouldWorkWhenClientModifiesAnAttribute() throws Exception {
		SyncKey firstAllocatedSyncKey = new SyncKey("4a2c7db8-b532-40a0-92c3-bfebb8da8f00");
		SyncKey secondAllocatedSyncKey = new SyncKey("55df3cf4-b70d-4df2-ac48-d31646994321");
		SyncKey thirdAllocatedSyncKey = new SyncKey("4872672c-95b9-4c1c-90ae-ebea7d0e83ed");
		int firstAllocatedStateId = 3;
		int secondAllocatedStateId = 4;
		int thirdAllocatedStateId = 5;
		
		Date syncDate = date("2012-10-09T16:22:53");
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(firstAllocatedSyncKey)
				.id(firstAllocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(secondAllocatedSyncKey)
				.id(secondAllocatedStateId)
				.build();
		ItemSyncState thirdAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(thirdAllocatedSyncKey)
				.id(thirdAllocatedStateId)
				.build();
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, secondAllocatedSyncKey, thirdAllocatedSyncKey);
		mockCollectionDaoPerformSync(collectionDao, user.device, firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, contactCollectionId);
		mockCollectionDaoPerformSync(collectionDao, user.device, secondAllocatedSyncKey, secondAllocatedState, thirdAllocatedState, contactCollectionId);
		
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).once();
		expect(dateService.getCurrentDate()).andReturn(thirdAllocatedState.getSyncDate()).once();
		Contact initialContact = new Contact();
		initialContact.setUid(1);
		initialContact.setFirstname("firstname");
		initialContact.setLastname("lastname");
		initialContact.setEmails(ImmutableMap.of("INTERNET;X-OBM-Ref1", EmailAddress.loginAtDomain("contact@mydomain.org")));
		initialContact.setPhones(ImmutableMap.of("HOME;FAX;X-OBM-Ref1", new Phone("1234")));
		
		ServerId serverId = new ServerId(contactCollectionIdAsString + ":" + initialContact.getUid());
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, serverId))
			.andReturn(false);
		
		itemTrackingDao.markAsSynced(secondAllocatedState, ImmutableSet.of(serverId));
		expectLastCall().once();

		// first sync
		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build()));
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(initialContact),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		// second sync
		MSContact modifiedMSContact = new MSContact();
		modifiedMSContact.setFirstName(initialContact.getFirstname());
		modifiedMSContact.setLastName(initialContact.getLastname());
		modifiedMSContact.setEmail1Address("contact@mydomain.org");
		modifiedMSContact.setHomeFaxNumber("4567");
		modifiedMSContact.setFileAs("lastname, firstname");
		
		Contact modifiedContact = new Contact();
		modifiedContact.setUid(1);
		modifiedContact.setFirstname("firstname");
		modifiedContact.setLastname("lastname");
		modifiedContact.setEmails(ImmutableMap.of("INTERNET;X-OBM-Ref1", EmailAddress.loginAtDomain("contact@mydomain.org")));
		modifiedContact.setPhones(ImmutableMap.of("HOME;FAX;X-OBM-Ref1", new Phone("4567")));
		
		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build())).times(2);
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId).times(2);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		expect(bookClient.storeContact(user.accessToken, contactCollectionId, modifiedContact, null))
			.andReturn(modifiedContact);
		
		mocksControl.replay();
		opushServer.start();

		WBXMLOPClient opushClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		SyncResponse syncResponse = opushClient.sync(decoder, firstAllocatedSyncKey, new Folder(contactCollectionIdAsString));
		String clientId = syncResponse.getProcessedClientIds().get(serverId);
		
		SyncResponse updateSyncResponse = opushClient.syncWithCommand(syncWithDataCommandFactory, user.device, secondAllocatedSyncKey, contactCollectionIdAsString, SyncCommand.CHANGE, 
				serverId.toString(), clientId, modifiedMSContact);
		
		mocksControl.verify();
		assertThat(updateSyncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		SyncCollectionResponse syncCollectionResponse = getCollectionWithId(updateSyncResponse, contactCollectionIdAsString);
		assertThat(syncCollectionResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		assertThat(syncCollectionResponse.getCommands().getCommands()).hasSize(0);
	}

	@Test
	public void updatingContactShouldWorkWhenClientDeletesAnAttribute() throws Exception {
		SyncKey firstAllocatedSyncKey = new SyncKey("4a2c7db8-b532-40a0-92c3-bfebb8da8f00");
		SyncKey secondAllocatedSyncKey = new SyncKey("55df3cf4-b70d-4df2-ac48-d31646994321");
		SyncKey thirdAllocatedSyncKey = new SyncKey("4872672c-95b9-4c1c-90ae-ebea7d0e83ed");
		int firstAllocatedStateId = 3;
		int secondAllocatedStateId = 4;
		int thirdAllocatedStateId = 5;
		
		Date syncDate = date("2012-10-09T16:22:53");
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(firstAllocatedSyncKey)
				.id(firstAllocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(secondAllocatedSyncKey)
				.id(secondAllocatedStateId)
				.build();
		ItemSyncState thirdAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(thirdAllocatedSyncKey)
				.id(thirdAllocatedStateId)
				.build();
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, secondAllocatedSyncKey, thirdAllocatedSyncKey);
		mockCollectionDaoPerformSync(collectionDao, user.device, firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, contactCollectionId);
		mockCollectionDaoPerformSync(collectionDao, user.device, secondAllocatedSyncKey, secondAllocatedState, thirdAllocatedState, contactCollectionId);
		
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).once();
		expect(dateService.getCurrentDate()).andReturn(thirdAllocatedState.getSyncDate()).once();
		Contact initialContact = new Contact();
		initialContact.setUid(1);
		initialContact.setFirstname("firstname");
		initialContact.setLastname("lastname");
		initialContact.setEmails(ImmutableMap.of("INTERNET;X-OBM-Ref1", EmailAddress.loginAtDomain("contact@mydomain.org")));
		initialContact.setPhones(ImmutableMap.of("HOME;FAX;X-OBM-Ref1", new Phone("1234")));
		
		ServerId serverId = new ServerId(contactCollectionIdAsString + ":" + initialContact.getUid());
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, serverId))
			.andReturn(false);
		
		itemTrackingDao.markAsSynced(secondAllocatedState, ImmutableSet.of(serverId));
		expectLastCall().once();

		// first sync
		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build()));
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(initialContact),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		// second sync
		MSContact modifiedMSContact = new MSContact();
		modifiedMSContact.setFirstName(initialContact.getFirstname());
		modifiedMSContact.setLastName(initialContact.getLastname());
		modifiedMSContact.setEmail1Address("contact@mydomain.org");
		modifiedMSContact.setFileAs("lastname, firstname");
		
		Contact modifiedContact = new Contact();
		modifiedContact.setUid(1);
		modifiedContact.setFirstname("firstname");
		modifiedContact.setLastname("lastname");
		modifiedContact.setEmails(ImmutableMap.of("INTERNET;X-OBM-Ref1", EmailAddress.loginAtDomain("contact@mydomain.org")));
		
		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build())).times(2);
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId).times(2);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		expect(bookClient.storeContact(user.accessToken, contactCollectionId, modifiedContact, null))
			.andReturn(modifiedContact);
		
		mocksControl.replay();
		opushServer.start();

		WBXMLOPClient opushClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		SyncResponse syncResponse = opushClient.sync(decoder, firstAllocatedSyncKey, new Folder(contactCollectionIdAsString));
		String clientId = syncResponse.getProcessedClientIds().get(serverId);
		
		SyncResponse updateSyncResponse = opushClient.syncWithCommand(syncWithDataCommandFactory, user.device, secondAllocatedSyncKey, contactCollectionIdAsString, SyncCommand.CHANGE, 
				serverId.toString(), clientId, modifiedMSContact);
		
		mocksControl.verify();
		assertThat(updateSyncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		SyncCollectionResponse syncCollectionResponse = getCollectionWithId(updateSyncResponse, contactCollectionIdAsString);
		assertThat(syncCollectionResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		assertThat(syncCollectionResponse.getCommands().getCommands()).hasSize(0);
	}

	@Test
	public void clientMayAskForAnOldSyncKey() throws Exception {
		SyncKey firstAllocatedSyncKey = new SyncKey("4a2c7db8-b532-40a0-92c3-bfebb8da8f00");
		SyncKey secondAllocatedSyncKey = new SyncKey("55df3cf4-b70d-4df2-ac48-d31646994321");
		SyncKey thirdAllocatedSyncKey = new SyncKey("6214c5ef-e76b-4d71-90ee-1ab92f28181c");
		int firstAllocatedStateId = 3;
		int secondAllocatedStateId = 4;
		int thirdAllocatedStateId = 5;
		
		Date syncDate = date("2012-10-09T16:22:53");
		ItemSyncState firstAllocatedState = ItemSyncState.builder()
				.syncDate(syncDate)
				.syncKey(firstAllocatedSyncKey)
				.id(firstAllocatedStateId)
				.build();
		ItemSyncState secondAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-09T17:22:53"))
				.syncKey(secondAllocatedSyncKey)
				.id(secondAllocatedStateId)
				.build();
		ItemSyncState thirdAllocatedState = ItemSyncState.builder()
				.syncDate(date("2012-10-09T18:22:53"))
				.syncKey(thirdAllocatedSyncKey)
				.id(thirdAllocatedStateId)
				.build();
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockNextGeneratedSyncKey(classToInstanceMap, secondAllocatedSyncKey, thirdAllocatedSyncKey);
		mockCollectionDaoPerformSync(collectionDao, user.device, firstAllocatedSyncKey, firstAllocatedState, secondAllocatedState, contactCollectionId);
		mockCollectionDaoPerformSync(collectionDao, user.device, firstAllocatedSyncKey, firstAllocatedState, thirdAllocatedState, contactCollectionId);
		
		expect(dateService.getCurrentDate()).andReturn(secondAllocatedState.getSyncDate()).once();
		expect(dateService.getCurrentDate()).andReturn(thirdAllocatedState.getSyncDate()).once();
		Contact initialContact = new Contact();
		initialContact.setUid(1);
		initialContact.setFirstname("firstname");
		initialContact.setLastname("lastname");
		initialContact.setEmails(ImmutableMap.of("INTERNET;X-OBM-Ref1", EmailAddress.loginAtDomain("contact@mydomain.org")));
		initialContact.setPhones(ImmutableMap.of("HOME;FAX;X-OBM-Ref1", new Phone("1234")));
		
		ServerId serverId = new ServerId(contactCollectionIdAsString + ":" + initialContact.getUid());
		expect(itemTrackingDao.isServerIdSynced(firstAllocatedState, serverId))
			.andReturn(false)
			.times(2);
		
		itemTrackingDao.markAsSynced(secondAllocatedState, ImmutableSet.of(serverId));
		expectLastCall();
		itemTrackingDao.markAsSynced(thirdAllocatedState, ImmutableSet.of(serverId));
		expectLastCall();

		expect(bookClient.listAllBooks(user.accessToken))
			.andReturn(ImmutableList.<AddressBook> of(AddressBook
					.builder()
					.name(contactCollectionIdAsString)
					.uid(AddressBook.Id.valueOf(contactCollectionId))
					.readOnly(false)
					.build()));
		expect(collectionDao.getCollectionMapping(user.device, contactCollectionPath + ":" + contactCollectionId))
			.andReturn(contactCollectionId);
		
		expect(bookClient.listContactsChanged(user.accessToken, syncDate, contactCollectionId))
			.andReturn(new ContactChanges(ImmutableList.<Contact> of(initialContact),
					ImmutableSet.<Integer> of(),
					syncDate));
		
		mocksControl.replay();
		opushServer.start();

		WBXMLOPClient opushClient = buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		SyncResponse syncResponse = opushClient.sync(decoder, firstAllocatedSyncKey, new Folder(contactCollectionIdAsString));
		SyncResponse sameSyncResponse = opushClient.sync(decoder, firstAllocatedSyncKey, new Folder(contactCollectionIdAsString));
		
		mocksControl.verify();
		SyncCollectionCommandResponse expectedCommandResponse = SyncCollectionCommandResponse.builder()
				.type(SyncCommand.ADD)
				.serverId(serverId.toString())
				.clientId(null)
				.build();
		
		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		SyncCollectionResponse syncCollectionResponse = getCollectionWithId(syncResponse, contactCollectionIdAsString);
		assertThat(syncCollectionResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		List<SyncCollectionCommandResponse> commands = syncCollectionResponse.getCommands().getCommands();
		assertThat(commands).hasSize(1);
		SyncCollectionCommandResponse syncCollectionCommandResponse = FluentIterable.from(commands).first().get();
		assertThat(syncCollectionCommandResponse).isEqualTo(expectedCommandResponse);
		
		MSContact msContact = (MSContact) syncCollectionCommandResponse.getApplicationData();
		assertThat(msContact.getFirstName()).isEqualTo("firstname");
		assertThat(msContact.getLastName()).isEqualTo("lastname");
		assertThat(msContact.getEmail1Address()).isEqualTo("contact@mydomain.org");
		assertThat(msContact.getHomeFaxNumber()).isEqualTo("1234");
		
		assertThat(syncResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		SyncCollectionResponse sameSyncCollectionResponse = getCollectionWithId(sameSyncResponse, contactCollectionIdAsString);
		assertThat(sameSyncCollectionResponse.getStatus()).isEqualTo(SyncStatus.OK);
		
		List<SyncCollectionCommandResponse> sameCommands = sameSyncCollectionResponse.getCommands().getCommands();
		assertThat(sameCommands).hasSize(1);
		SyncCollectionCommandResponse sameSyncCollectionCommandResponse = FluentIterable.from(sameCommands).first().get();
		assertThat(sameSyncCollectionCommandResponse).isEqualTo(expectedCommandResponse);
		
		MSContact sameMSContact = (MSContact) sameSyncCollectionCommandResponse.getApplicationData();
		assertThat(sameMSContact.getFirstName()).isEqualTo("firstname");
		assertThat(sameMSContact.getLastName()).isEqualTo("lastname");
		assertThat(sameMSContact.getEmail1Address()).isEqualTo("contact@mydomain.org");
		assertThat(sameMSContact.getHomeFaxNumber()).isEqualTo("1234");
	}
}