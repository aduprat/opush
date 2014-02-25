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
import static org.obm.opush.IntegrationTestUtils.buildWBXMLOpushClient;
import static org.obm.opush.IntegrationUserAccessUtils.mockUsersAccess;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.assertj.core.api.Assertions;
import org.easymock.IMocksControl;
import org.fest.util.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.Configuration;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.configuration.EmailConfiguration;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.opush.ActiveSyncServletModule.OpushServer;
import org.obm.opush.SingleUserFixture.OpushUser;
import org.obm.push.backend.DataDelta;
import org.obm.push.backend.IContentsExporter;
import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.ICollectionPathHelper;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.MSEmailBodyType;
import org.obm.push.bean.MSEmailHeader;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.client.SyncClientCommands;
import org.obm.push.bean.change.item.ItemChange;
import org.obm.push.bean.change.item.ItemChangesBuilder;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.bean.ms.MSEmailBody;
import org.obm.push.protocol.data.SyncDecoder;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.ItemTrackingDao;
import org.obm.push.utils.DateUtils;
import org.obm.push.utils.SerializableInputStream;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.sync.push.client.OPClient;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.icegreen.greenmail.imap.AuthorizationException;
import com.icegreen.greenmail.imap.ImapHostManager;
import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

@RunWith(GuiceRunner.class)
@GuiceModule(MailBackendHandlerTestModule.class)
public class MailBackendHandlerTest {

	@Inject	SingleUserFixture singleUserFixture;
	@Inject	OpushServer opushServer;
	@Inject	ClassToInstanceAgregateView<Object> classToInstanceMap;
	@Inject GreenMail greenMail;
	@Inject ICollectionPathHelper collectionPathHelper;
	@Inject ImapConnectionCounter imapConnectionCounter;
	@Inject PendingQueriesLock pendingQueries;
	@Inject IMocksControl mocksControl;
	@Inject Configuration configuration;
	@Inject SyncDecoder decoder;
	@Inject PolicyConfigurationProvider policyConfigurationProvider;
	
	private ServerSetup smtpServerSetup;
	private String mailbox;
	private GreenMailUser greenMailUser;
	private ImapHostManager imapHostManager;
	private OpushUser user;
	private CloseableHttpClient httpClient;

	@Before
	public void init() throws AuthorizationException, FolderException {
		httpClient = HttpClientBuilder.create().build();
		user = singleUserFixture.jaures;
		greenMail.start();
		smtpServerSetup = greenMail.getSmtp().getServerSetup();
		mailbox = singleUserFixture.jaures.user.getLoginAtDomain();
		greenMailUser = greenMail.setUser(mailbox, singleUserFixture.jaures.password);
		imapHostManager = greenMail.getManagers().getImapHostManager();
		imapHostManager.createMailbox(greenMailUser, "Trash");

		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration");
	}

	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		greenMail.stop();
		httpClient.close();
		Files.delete(configuration.dataDir);
	}

	@Test
	public void testDeleteMail() throws Exception {
		SyncKey syncEmailSyncKey = new SyncKey("1");
		int serverId = 1234;
		String syncEmailId = ":2";
		SyncKey syncKey = new SyncKey("sync state");
		ItemSyncState syncState = ItemSyncState.builder()
				.syncDate(DateUtils.getCurrentDate())
				.syncKey(syncKey)
				.build();
		DataDelta delta = DataDelta.builder()
			.changes(new ItemChangesBuilder()
				.addItemChange(ItemChange.builder()
					.serverId(serverId + syncEmailId)
					.data(applicationData("text", MSEmailBodyType.PlainText)))
				.build())
			.syncDate(new Date())
			.syncKey(syncState.getSyncKey())
			.build();
		
		mockUsersAccess(classToInstanceMap, Arrays.asList(user));
		mockDao(serverId, syncState);
		
		bindChangedToDelta(delta);
		
		mocksControl.replay();
		opushServer.start();

		GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject", "body", smtpServerSetup);
		GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject2", "body", smtpServerSetup);
		greenMail.waitForIncomingEmail(2);

		OPClient opClient = buildWBXMLOpushClient(singleUserFixture.jaures, opushServer.getPort(), httpClient);
		opClient.deleteEmail(decoder, syncEmailSyncKey, serverId, serverId + syncEmailId);

		assertEmailCountInMailbox(EmailConfiguration.IMAP_INBOX_NAME, 1);
		assertEmailCountInMailbox(EmailConfiguration.IMAP_TRASH_NAME, 1);
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(1);
	}

	private void bindChangedToDelta(DataDelta delta) throws Exception {
		IContentsExporter contentsExporter = classToInstanceMap.get(IContentsExporter.class);
		expect(contentsExporter.getChanged(
				anyObject(UserDataRequest.class),
				anyObject(ItemSyncState.class),
				anyObject(AnalysedSyncCollection.class), 
				anyObject(SyncClientCommands.class),
				anyObject(SyncKey.class)))
			.andReturn(delta).once();
	}

	private void mockDao(int serverId, ItemSyncState syncState) throws Exception {
		mockCollectionDao(serverId, syncState);
		mockItemTrackingDao();
	}
	
	private void mockCollectionDao(int serverId, ItemSyncState syncState) throws Exception {
		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		expect(collectionDao.getCollectionPath(serverId))
			.andReturn(IntegrationTestUtils.buildEmailInboxCollectionPath(singleUserFixture.jaures)).anyTimes();
		
		expect(collectionDao.findItemStateForKey(anyObject(SyncKey.class)))
			.andReturn(syncState).anyTimes();
		
		expect(collectionDao.updateState(eq(user.device), eq(serverId), anyObject(SyncKey.class), anyObject(Date.class)))
			.andReturn(syncState).anyTimes();
		
		expect(collectionDao.getCollectionMapping(eq(user.device), anyObject(String.class)))
			.andReturn(serverId).anyTimes();
		
		IntegrationTestUtils.expectUserCollectionsNeverChange(collectionDao, Sets.newHashSet(singleUserFixture.jaures), ImmutableList.of(serverId));
	}

	private void mockItemTrackingDao() throws Exception {
		ItemTrackingDao itemTrackingDao = classToInstanceMap.get(ItemTrackingDao.class);
		itemTrackingDao.markAsSynced(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		itemTrackingDao.markAsDeleted(anyObject(ItemSyncState.class), anyObject(Set.class));
		expectLastCall().anyTimes();
		
		expect(itemTrackingDao.isServerIdSynced(anyObject(ItemSyncState.class), anyObject(ServerId.class)))
			.andReturn(false).anyTimes();
	}
	
	private void assertEmailCountInMailbox(String mailbox, Integer expectedNumberOfEmails) {
		MailFolder inboxFolder = imapHostManager.getFolder(greenMailUser, mailbox);
		Assertions.assertThat(inboxFolder.getMessageCount()).isEqualTo(expectedNumberOfEmails);
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
}
