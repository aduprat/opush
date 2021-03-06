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
package org.obm.push.mail.imap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.push.bean.Credentials;
import org.obm.push.bean.User;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.configuration.OpushEmailConfiguration;
import org.obm.push.exception.MailException;
import org.obm.push.mail.MailEnvModule;
import org.obm.push.mail.MailboxService;
import org.obm.push.mail.bean.MailboxFolder;
import org.obm.push.mail.bean.MailboxFolders;
import org.obm.push.resource.ResourcesHolder;

import com.google.inject.Inject;
import com.icegreen.greenmail.util.GreenMail;

@RunWith(GuiceRunner.class)
@GuiceModule(MailEnvModule.class)
public class MailboxServiceSubscribedFoldersTest {

	@Inject MailboxService mailboxService;

	@Inject GreenMail greenMail;
	@Inject ResourcesHolder resourcesHolder;

	private String mailbox;
	private char[] password;
	private MailboxTestUtils testUtils;
	private Date beforeTest;
	private UserDataRequest udr;

	@Before
	public void setUp() {
		beforeTest = new Date();
		greenMail.start();
		mailbox = "to@localhost.com";
		password = "password".toCharArray();
		greenMail.setUser(mailbox, String.valueOf(password));
		udr = new UserDataRequest(
				new Credentials(User.Factory.create()
						.createUser(mailbox, mailbox, null), password), null, null);
		testUtils = new MailboxTestUtils(mailboxService, udr, mailbox, beforeTest,
				greenMail.getSmtp().getServerSetup());
	}
	
	@After
	public void tearDown() {
		resourcesHolder.close();
		greenMail.stop();
	}

	@Test
	public void testNewFolderIsCreatedUnsubscribed() throws MailException {
		createUnsubscribedFolder("newFolder");
		
		MailboxFolders subscribedFolders = mailboxService.listSubscribedFolders(udr);
		
		assertThat(subscribedFolders).isEmpty();
	}

	@Test
	public void testNoResultWhenNoSubscription() throws MailException {
		MailboxFolders subscribedFolders = mailboxService.listSubscribedFolders(udr);
		
		assertThat(subscribedFolders).isEmpty();
	}

	@Test
	public void testNoResultWhenRegularFoldersExist() throws MailException {
		createUnsubscribedFolder(OpushEmailConfiguration.IMAP_DRAFTS_NAME);
		createUnsubscribedFolder(OpushEmailConfiguration.IMAP_SENT_NAME);
		createUnsubscribedFolder(OpushEmailConfiguration.IMAP_TRASH_NAME);
		
		MailboxFolders subscribedFolders = mailboxService.listSubscribedFolders(udr);
		
		assertThat(subscribedFolders).isEmpty();
	}

	@Test
	public void testNoResultWhenSubfolderExist() throws MailException {
		createUnsubscribedFolder(OpushEmailConfiguration.IMAP_INBOX_NAME + ".SUBFOLDER");
		
		MailboxFolders subscribedFolders = mailboxService.listSubscribedFolders(udr);

		assertThat(subscribedFolders).isEmpty();
	}

	private void createUnsubscribedFolder(String folderName) throws MailException {
		MailboxFolder folder = testUtils.folder(folderName);
		mailboxService.createFolder(udr, folder);
	}
}
