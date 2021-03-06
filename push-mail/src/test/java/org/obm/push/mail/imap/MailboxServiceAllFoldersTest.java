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
import org.obm.push.bean.change.hierarchy.MailboxPath;
import org.obm.push.mail.MailEnvModule;
import org.obm.push.mail.MailboxService;
import org.obm.push.mail.bean.MailboxFolder;
import org.obm.push.mail.bean.MailboxFolders;
import org.obm.push.resource.ResourcesHolder;

import com.google.inject.Inject;
import com.icegreen.greenmail.imap.ImapHostManager;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;

@RunWith(GuiceRunner.class)
@GuiceModule(MailEnvModule.class)
public class MailboxServiceAllFoldersTest {

	@Inject MailboxService mailboxService;

	@Inject GreenMail greenMail;
	@Inject ResourcesHolder resourcesHolder;

	private String mailbox;
	private char[] password;
	private MailboxTestUtils testUtils;
	private Date beforeTest;
	private UserDataRequest udr;
	private GreenMailUser greenMailUser;

	@Before
	public void setUp() {
		beforeTest = new Date();
		greenMail.start();
		mailbox = "to@localhost.com";
		password = "password".toCharArray();
		greenMailUser = greenMail.setUser(mailbox, String.valueOf(password));
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
	public void testDefaultFolderList() throws Exception {
		MailboxFolders emails = mailboxService.listAllFolders(udr);
		assertThat(emails).containsOnly(inbox());
	}
	

	@Test
	public void testListTwoFolders() throws Exception {
		MailboxFolder newFolder = folder("NEW");
		mailboxService.createFolder(udr, newFolder);
		MailboxFolders after = mailboxService.listAllFolders(udr);
		assertThat(after).containsOnly(
				inbox(),
				newFolder);
	}

	@Test
	public void testListInboxSubfolder() throws Exception {
		MailboxFolder newFolder = folder("INBOX.NEW");
		mailboxService.createFolder(udr, newFolder);
		MailboxFolders after = mailboxService.listAllFolders(udr);
		assertThat(after).containsOnly(
				inbox(),
				newFolder);
	}
	
	@Test
	public void testListInboxDeepSubfolder() throws Exception {
		MailboxFolder newFolder = folder("INBOX.LEVEL1.LEVEL2.LEVEL3.LEVEL4");
		mailboxService.createFolder(udr, newFolder);
		MailboxFolders after = mailboxService.listAllFolders(udr);
		assertThat(after).containsOnly(
				inbox(),
				folder("INBOX.LEVEL1"),
				folder("INBOX.LEVEL1.LEVEL2"),
				folder("INBOX.LEVEL1.LEVEL2.LEVEL3"),
				folder("INBOX.LEVEL1.LEVEL2.LEVEL3.LEVEL4"));
	}

	@Test
	public void testListToplevelFolder() throws Exception {
		MailboxFolder newFolder = folder("TOP");
		mailboxService.createFolder(udr, newFolder);
		MailboxFolders after = mailboxService.listAllFolders(udr);
		assertThat(after).containsOnly(
				inbox(),
				folder("TOP"));
	}
	
	@Test
	public void testListNestedToplevelFolder() throws Exception {
		MailboxFolder newFolder = folder("TOP.LEVEL1");
		mailboxService.createFolder(udr, newFolder);
		MailboxFolders after = mailboxService.listAllFolders(udr);
		assertThat(after).containsOnly(
				inbox(),
				folder("TOP"),
				folder("TOP.LEVEL1"));
	}
	
	@Test
	public void folderExistsShouldReturnTrueIfTheFolderExists() throws Exception {
		MailboxFolder newFolder = folder("TOP.LEVEL1");
		mailboxService.createFolder(udr, newFolder);
		boolean folderExists = mailboxService.folderExists(
				udr, MailboxPath.of(newFolder.getName(), newFolder.getImapSeparator()));
		assertThat(folderExists).isEqualTo(true);
	}
	
	@Test
	public void folderExistsShouldReturnFalseIfTheFolderDoesntExist() throws Exception {
		boolean folderExists = mailboxService.folderExists(udr, MailboxPath.of("ABCDEF"));
		assertThat(folderExists).isEqualTo(false);
	}
	
	@Test
	public void folderExistsShouldReturnFalseIfTheFolderHasNoselectFlag() throws Exception {
		String parentName = "PARENT";
		String childName = "PARENT.CHILD";
		ImapHostManager manager = greenMail.getManagers().getImapHostManager();
		
		manager.createMailbox(greenMailUser, childName);
		MailFolder parentFolder = manager.getFolder(greenMailUser, parentName);
		
		boolean parentFolderExists = mailboxService.folderExists(udr, MailboxPath.of(parentName));
		
		assertThat(parentFolder.isSelectable()).isFalse();
		assertThat(parentFolderExists).isEqualTo(false);
	}
	
	protected MailboxFolder folder(String name) {
		return testUtils.folder(name);
	}
	
	protected MailboxFolder inbox() {
		return testUtils.inbox();
	}
}
