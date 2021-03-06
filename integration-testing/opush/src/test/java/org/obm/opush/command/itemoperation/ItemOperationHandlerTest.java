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
package org.obm.opush.command.itemoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.expect;

import java.util.concurrent.TimeUnit;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.assertj.core.util.Files;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.Configuration;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.configuration.EmailConfiguration;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.opush.ImapConnectionCounter;
import org.obm.opush.IntegrationTestUtils;
import org.obm.opush.IntegrationUserAccessUtils;
import org.obm.opush.MailBackendTestModule;
import org.obm.opush.PendingQueriesLock;
import org.obm.opush.Users;
import org.obm.opush.Users.OpushUser;
import org.obm.opush.env.CassandraServer;
import org.obm.push.OpushServer;
import org.obm.push.bean.FolderType;
import org.obm.push.bean.ItemOperationsStatus;
import org.obm.push.bean.MSEmailBodyType;
import org.obm.push.bean.change.hierarchy.BackendFolder.BackendId;
import org.obm.push.bean.change.hierarchy.Folder;
import org.obm.push.bean.change.hierarchy.FolderSnapshot;
import org.obm.push.bean.change.hierarchy.MailboxPath;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.service.FolderSnapshotDao;
import org.obm.push.state.FolderSyncKey;
import org.obm.push.utils.DOMUtils;
import org.obm.sync.push.client.ItemOperationFetchResponse;
import org.obm.sync.push.client.ItemOperationResponse;
import org.obm.sync.push.client.OPClient;
import org.w3c.dom.Element;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.icegreen.greenmail.imap.ImapHostManager;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;

@RunWith(GuiceRunner.class)
@GuiceModule(MailBackendTestModule.class)
public class ItemOperationHandlerTest {

	@Inject private Users users;
	@Inject private OpushServer opushServer;
	@Inject private GreenMail greenMail;
	@Inject private IMocksControl mocksControl;
	@Inject private ImapConnectionCounter imapConnectionCounter;
	@Inject private Configuration configuration;
	@Inject private PolicyConfigurationProvider policyConfigurationProvider;
	@Inject private PendingQueriesLock pendingQueries;
	@Inject private CassandraServer cassandraServer;
	@Inject private IntegrationTestUtils testUtils;
	@Inject private IntegrationUserAccessUtils userAccessUtils;
	@Inject private FolderSnapshotDao folderSnapshotDao;

	private GreenMailUser greenMailUser;
	private ImapHostManager imapHostManager;
	private OpushUser user;
	private String mailbox;
	private MailboxPath inboxPath;
	private CollectionId inboxCollectionId;
	private Folder inboxFolder;
	private CloseableHttpClient httpClient;

	@Before
	public void init() throws Exception {
		user = users.jaures;
		greenMail.start();
		mailbox = user.user.getLoginAtDomain();
		greenMailUser = greenMail.setUser(mailbox, String.valueOf(user.password));
		imapHostManager = greenMail.getManagers().getImapHostManager();
		imapHostManager.createMailbox(greenMailUser, "Trash");
		httpClient = HttpClientBuilder.create().build();
		cassandraServer.start();

		inboxPath = MailboxPath.of(EmailConfiguration.IMAP_INBOX_NAME);
		inboxCollectionId = CollectionId.of(1234);
		inboxFolder = Folder.builder()
				.backendId(inboxPath)
				.collectionId(inboxCollectionId)
				.parentBackendIdOpt(Optional.<BackendId>absent())
				.displayName("INBOX")
				.folderType(FolderType.DEFAULT_INBOX_FOLDER)
				.build();

		FolderSyncKey syncKey = new FolderSyncKey("4fd6280c-cbaa-46aa-a859-c6aad00f1ef3");
		folderSnapshotDao.create(user.user, user.device, syncKey, 
				FolderSnapshot.nextId(2).folders(ImmutableSet.of(inboxFolder)));
		
		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration");
	}

	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		greenMail.stop();
		Files.delete(configuration.dataDir);
		httpClient.close();
	}

	@Test
	public void testFetchNoOptions() throws Exception {
		userAccessUtils.mockUsersAccess(user);
		
		mocksControl.replay();
		opushServer.start();
		sendEmailsToImapServer("email body data");
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ItemOperationResponse itemOperationFetch = opClient.itemOperationFetch(inboxCollectionId, inboxCollectionId.serverId(1));
		mocksControl.verify();

		ItemOperationFetchResponse fetchResponse = Iterables.getOnlyElement(itemOperationFetch.getFetchResponses());
		assertThat(fetchResponse.getStatus()).isEqualTo(ItemOperationsStatus.SUCCESS);
		assertThat(fetchResponse.getServerId()).isEqualTo(inboxCollectionId.serverId(1));
		Element data = fetchResponse.getData();
		assertThat(DOMUtils.getUniqueElement(data, "Type").getTextContent()).isEqualTo("1");
		assertThat(DOMUtils.getUniqueElement(data, "Data").getTextContent()).contains("email body data");

		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testFetchByFileReference() throws Exception {
		testUtils.appendToINBOX(greenMailUser, "eml/iCSAsAttachment.eml");
		String fileReference = inboxCollectionId.asString() + "_1_2_YXBwbGljYXRpb24vaWNz_QkFTRTY0";
		userAccessUtils.mockUsersAccess(user);
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ItemOperationResponse itemOperationFetch = opClient.itemOperationFetch(fileReference);
		mocksControl.verify();

		ItemOperationFetchResponse fetchResponse = Iterables.getOnlyElement(itemOperationFetch.getFetchResponses());
		assertThat(fetchResponse.getStatus()).isEqualTo(ItemOperationsStatus.SUCCESS);
		assertThat(fetchResponse.getServerId()).isNull();
		Element data = fetchResponse.getData();
		assertThat(DOMUtils.getUniqueElement(data, "ContentType").getTextContent()).isEqualTo("application/ics");
		assertThat(DOMUtils.getUniqueElement(data, "Data").getTextContent()).contains("Weekly Team Feedback");
	}

	@Test
	public void shouldReturnDocumentNotFoundWhenFetchAbsentEmail() throws Exception {
		userAccessUtils.mockUsersAccess(user);
		
		mocksControl.replay();
		opushServer.start();
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ItemOperationResponse itemOperationFetch = opClient.itemOperationFetch(inboxCollectionId, inboxCollectionId.serverId(1));
		mocksControl.verify();

		ItemOperationFetchResponse fetchResponse = Iterables.getOnlyElement(itemOperationFetch.getFetchResponses());
		assertThat(fetchResponse.getStatus()).isEqualTo(ItemOperationsStatus.DOCUMENT_LIBRARY_NOT_FOUND);
		
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	
	@Test
	public void testFetchForHtml() throws Exception {
		
		userAccessUtils.mockUsersAccess(user);
		
		mocksControl.replay();
		opushServer.start();
		sendEmailsToImapServer("email body data");
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ItemOperationResponse itemOperationFetch = opClient.itemOperationFetch(
				inboxCollectionId, MSEmailBodyType.HTML, inboxCollectionId.serverId(1));
		mocksControl.verify();

		ItemOperationFetchResponse fetchResponse = Iterables.getOnlyElement(itemOperationFetch.getFetchResponses());
		assertThat(fetchResponse.getStatus()).isEqualTo(ItemOperationsStatus.SUCCESS);
		assertThat(fetchResponse.getServerId()).isEqualTo(inboxCollectionId.serverId(1));
		Element data = fetchResponse.getData();
		assertThat(DOMUtils.getUniqueElement(data, "Type").getTextContent()).isEqualTo("2");
		assertThat(DOMUtils.getUniqueElement(data, "Data").getTextContent())
			.contains("<html><body>email body data</body></html>");
		
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testFetchForMime() throws Exception {
		userAccessUtils.mockUsersAccess(user);
		
		mocksControl.replay();
		opushServer.start();
		sendEmailsToImapServer("email body data");
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ItemOperationResponse itemOperationFetch = opClient.itemOperationFetch(
				inboxCollectionId, MSEmailBodyType.MIME, inboxCollectionId.serverId(1));
		mocksControl.verify();

		ItemOperationFetchResponse fetchResponse = Iterables.getOnlyElement(itemOperationFetch.getFetchResponses());
		assertThat(fetchResponse.getStatus()).isEqualTo(ItemOperationsStatus.SUCCESS);
		assertThat(fetchResponse.getServerId()).isEqualTo(inboxCollectionId.serverId(1));
		Element data = fetchResponse.getData();
		assertThat(DOMUtils.getUniqueElement(data, "Type").getTextContent()).isEqualTo("4");
		assertThat(DOMUtils.getUniqueElement(data, "Data").getTextContent())
			.contains("Subject: subject")
			.contains("MIME-Version: 1.0")
			.contains("Content-Type: text/plain; charset=us-ascii")
			.contains("Content-Transfer-Encoding: 7bit")
			.contains("To: jaures@sfio.fr")
			.contains("email body data");
		
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(1);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Test
	public void testTwoFetchInDifferentRequest() throws Exception {
		
		userAccessUtils.mockUsersAccess(user);
		
		mocksControl.replay();
		opushServer.start();
		sendEmailsToImapServer("email body data", "email 2 body data");
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		opClient.itemOperationFetch(inboxCollectionId, inboxCollectionId.serverId(1));
		ItemOperationResponse itemOperationFetch = opClient.itemOperationFetch(inboxCollectionId, inboxCollectionId.serverId(2));
		mocksControl.verify();

		ItemOperationFetchResponse fetchResponse = Iterables.getOnlyElement(itemOperationFetch.getFetchResponses());
		assertThat(fetchResponse.getStatus()).isEqualTo(ItemOperationsStatus.SUCCESS);
		assertThat(fetchResponse.getServerId()).isEqualTo(inboxCollectionId.serverId(2));
		Element data = fetchResponse.getData();
		assertThat(DOMUtils.getUniqueElement(data, "Data").getTextContent())
			.contains("email 2 body data");
		
		assertThat(pendingQueries.waitingClose(10, TimeUnit.SECONDS)).isTrue();
		assertThat(imapConnectionCounter.loginCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.closeCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.selectCounter.get()).isEqualTo(2);
		assertThat(imapConnectionCounter.listMailboxesCounter.get()).isEqualTo(0);
	}

	@Ignore("Opush supports only one fetch in an ItemOperation command")
	@Test
	public void testTwoFetchInSameRequest() throws Exception {
		userAccessUtils.mockUsersAccess(user);
		
		mocksControl.replay();
		opushServer.start();
		sendEmailsToImapServer("email body data", "email 2 body data");
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		opClient.itemOperationFetch(inboxCollectionId, inboxCollectionId.serverId(1));
		ItemOperationResponse itemOperationFetch = opClient.itemOperationFetch(inboxCollectionId,
				inboxCollectionId.serverId(1),
				inboxCollectionId.serverId(2));
		mocksControl.verify();

		ItemOperationFetchResponse fetchResponse = Iterables.get(itemOperationFetch.getFetchResponses(), 0);
		assertThat(fetchResponse.getStatus()).isEqualTo(ItemOperationsStatus.SUCCESS);
		assertThat(fetchResponse.getServerId()).isEqualTo(inboxCollectionId.serverId(1));
		Element data = fetchResponse.getData();
		assertThat(DOMUtils.getUniqueElement(data, "Data").getTextContent())
			.contains("email body data");
		
		ItemOperationFetchResponse fetchResponse2 = Iterables.get(itemOperationFetch.getFetchResponses(), 1);
		assertThat(fetchResponse2.getStatus()).isEqualTo(ItemOperationsStatus.SUCCESS);
		assertThat(fetchResponse2.getServerId()).isEqualTo(inboxCollectionId.serverId(2));
		Element data2 = fetchResponse2.getData();
		assertThat(DOMUtils.getUniqueElement(data2, "Data").getTextContent())
			.contains("email 2 body data");
	}

	private void sendEmailsToImapServer(String...bodies) throws InterruptedException {
		for (String body : bodies) {
			GreenMailUtil.sendTextEmail(mailbox, mailbox, "subject", body, greenMail.getSmtp().getServerSetup());
		}
		greenMail.waitForIncomingEmail(bodies.length);
	}
}
