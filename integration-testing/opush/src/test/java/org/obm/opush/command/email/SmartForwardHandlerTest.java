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
package org.obm.opush.command.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.obm.opush.IntegrationTestUtils.appendToINBOX;
import static org.obm.opush.IntegrationTestUtils.buildWBXMLOpushClient;
import static org.obm.opush.IntegrationTestUtils.loadEmail;
import static org.obm.opush.IntegrationUserAccessUtils.mockUsersAccess;

import java.io.InputStreamReader;

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
import org.obm.configuration.EmailConfiguration;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.opush.IntegrationTestUtils;
import org.obm.opush.MailBackendTestModule;
import org.obm.opush.Users;
import org.obm.opush.Users.OpushUser;
import org.obm.opush.env.CassandraServer;
import org.obm.push.OpushServer;
import org.obm.push.bean.MSEvent;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.service.EventService;
import org.obm.push.store.CollectionDao;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.sync.client.user.UserClient;
import org.obm.sync.push.client.OPClient;

import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;

@RunWith(GuiceRunner.class)
@GuiceModule(MailBackendTestModule.class)
public class SmartForwardHandlerTest {

	@Inject Users users;
	@Inject OpushServer opushServer;
	@Inject ClassToInstanceAgregateView<Object> classToInstanceMap;
	@Inject IMocksControl mocksControl;
	@Inject Configuration configuration;
	@Inject GreenMail greenMail;
	@Inject PolicyConfigurationProvider policyConfigurationProvider;
	@Inject CassandraServer cassandraServer;
	
	private OpushUser user;
	private GreenMailUser greenMailUser;
	private String inboxCollectionPath;
	private CollectionId inboxCollectionId;
	private MailFolder inboxFolder;
	private MailFolder sentFolder;
	private ServerId serverId;
	private CloseableHttpClient httpClient;

	@Before
	public void setUp() throws Exception {
		httpClient = HttpClientBuilder.create().build();
		user = users.jaures;
		greenMail.start();
		greenMailUser = greenMail.setUser(user.user.getLoginAtDomain(), String.valueOf(user.password));
		sentFolder = greenMail.getManagers().getImapHostManager().createMailbox(greenMailUser, EmailConfiguration.IMAP_SENT_NAME);
		inboxFolder = greenMail.getManagers().getImapHostManager().getInbox(greenMailUser);
		cassandraServer.start();
		
		inboxCollectionPath = IntegrationTestUtils.buildEmailInboxCollectionPath(user);
		inboxCollectionId = CollectionId.of(1);
		serverId = inboxCollectionId.serverId(1);
		
		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		UserClient userClient = classToInstanceMap.get(UserClient.class);
		mockUsersAccess(classToInstanceMap, Lists.newArrayList(user));
		expect(collectionDao.getCollectionPath(inboxCollectionId)).andReturn(inboxCollectionPath).anyTimes();
		expect(userClient.getUserEmail(user.accessToken)).andReturn(user.user.getLoginAtDomain()).anyTimes();
		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration").anyTimes();
	}
	
	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		httpClient.close();
		Files.delete(configuration.dataDir);
	}

	@Test
	public void testForwardClearTextOnOriginalClearText() throws Exception {
		appendToINBOX(greenMailUser, "eml/textPlain.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textPlain.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.startsWith("Mail content")
			.contains("\r\n> Mail content");
	}

	@Test
	public void testForwardHtmlOnOriginalHtml() throws Exception {
		appendToINBOX(greenMailUser, "eml/textHtml.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textHtml.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("<BODY>\r\n<B>Mail content</B>")
			.contains("<BLOCKQUOTE style=\"border-left:1px solid black; padding-left:1px;\">\r\n<B>Mail content</B>");
	}

	@Test
	public void testForwardClearTextOnOriginalMultipartAlt() throws Exception {
		appendToINBOX(greenMailUser, "eml/multipartAlternative.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textPlain.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("<BODY>Mail content")
			.contains("<BLOCKQUOTE style=\"border-left:1px solid black; padding-left:1px;\">\r\n<B>bodydata</B>");
	}

	@Test
	public void testForwardHtmlOnOriginalMultipartAlt() throws Exception {
		appendToINBOX(greenMailUser, "eml/multipartAlternative.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textHtml.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("<BODY>\r\n<B>Mail content</B>")
			.contains("<BLOCKQUOTE style=\"border-left:1px solid black; padding-left:1px;\">\r\n<B>bodydata</B>");
	}

	@Test
	public void testForwardMultipartAltOnOriginalClearText() throws Exception {
		appendToINBOX(greenMailUser, "eml/textPlain.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/multipartAlternative.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("bodydata")
			.contains("> Mail content")
			.contains("<B>bodydata</B>")
			.contains("<BLOCKQUOTE style=\"border-left:1px solid black; padding-left:1px;\">Mail content");
	}

	@Test
	public void testForwardMultipartAltOnOriginalHtml() throws Exception {
		appendToINBOX(greenMailUser, "eml/textHtml.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/multipartAlternative.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("<B>bodydata</B>")
			.contains("<BLOCKQUOTE style=\"border-left:1px solid black; padding-left:1px;\">\r\n<B>Mail content</B>");
	}
	
	@Test
	public void testForwardMultipartAltRelatedOnOriginalB64Text() throws Exception {
		appendToINBOX(greenMailUser, "eml/multipartAlternativeThenRelated.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textPlainB64.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("<BODY>Fhkktrxcjujjhvvh depuis mon Android<BR>")
			.contains("<BLOCKQUOTE style=\"border-left:1px solid black; padding-left:1px;\">")
			.contains("Heu ?");
	}

	@Test
	public void testForwardedMailWithICSAttachmentShowsAttachment() throws Exception {
		appendToINBOX(greenMailUser, "eml/iCSAsAttachment.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textPlain.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("Mail content")
			.contains("attachment.ics");
	}

	@Test
	public void testForwardedExternalMailWithICSAttachmentShowsAttachment() throws Exception {
		appendToINBOX(greenMailUser, "eml/externalICSAsAttachment.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textPlain.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("Mail content")
			.contains("meeting.ics");
	}
	
	@Test
	public void testForwardedInvitationDoesntShowAttachment() throws Exception {
		appendToINBOX(greenMailUser, "eml/invitation.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		EventService eventService = classToInstanceMap.get(EventService.class);
		expect(eventService.parseEventFromICalendar(anyObject(UserDataRequest.class), anyObject(String.class)))
			.andReturn(new MSEvent()).anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textPlain.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("Mail content")
			.doesNotContain("meeting.ics");
	}
	
	@Test
	public void testForwardedCancelInvitationDoesntShowAttachment() throws Exception {
		appendToINBOX(greenMailUser, "eml/cancelInvitation.eml");
		assertThat(sentFolder.getMessageCount()).isEqualTo(0);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(1);
		EventService eventService = classToInstanceMap.get(EventService.class);
		expect(eventService.parseEventFromICalendar(anyObject(UserDataRequest.class), anyObject(String.class)))
			.andReturn(new MSEvent()).anyTimes();
		
		mocksControl.replay();
		opushServer.start();
		boolean success = opClient().emailForward(loadEmail("eml/textPlain.eml"), inboxCollectionId, serverId);
		mocksControl.verify();
		
		assertThat(success).isTrue();
		assertThat(sentFolder.getMessageCount()).isEqualTo(1);
		assertThat(inboxFolder.getMessages().size()).isEqualTo(2);
		assertThat(CharStreams.toString(new InputStreamReader(
				inboxFolder.getMessages().get(1).getMimeMessage().getInputStream())))
			.contains("Mail content")
			.doesNotContain("meeting.ics");
	}
	
	private OPClient opClient() {
		return buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
	}
}
