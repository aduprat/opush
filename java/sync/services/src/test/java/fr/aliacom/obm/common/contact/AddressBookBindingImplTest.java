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
package fr.aliacom.obm.common.contact;

import static org.easymock.EasyMock.expect;
import static org.fest.assertions.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.obm.configuration.ContactConfiguration;
import org.obm.push.utils.DateUtils;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.auth.ServerFault;
import org.obm.sync.book.Contact;
import org.obm.sync.book.Folder;
import org.obm.sync.items.AddressBookChangesResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import fr.aliacom.obm.services.constant.ObmSyncConfigurationService;
import fr.aliacom.obm.utils.ObmHelper;

import org.obm.filter.SlowFilterRunner;

@RunWith(SlowFilterRunner.class)
public class AddressBookBindingImplTest {
	
	private ObmHelper mockHelper() throws SQLException {
		ObmHelper helper = EasyMock.createMock(ObmHelper.class);
		expect(helper.getConnection()).andReturn(null);
		helper.cleanup(null, null, null);
		expect(helper.selectNow(null)).andReturn(new Date());
		return helper;
	}

	/**
	 * Tests that getSync() returns the updated contacts and address books,
	 * including the full list of users from the user DAO, when
	 * AddressBookBindingImpl.GLOBAL_ADDRESS_BOOK_SYNC is set to true.
	 */
	@Test
	public void testGetSyncGlobalAddressBookSync() throws ServerFault, SQLException {
		Date timestamp = DateUtils.getEpochCalendar().getTime();

		AccessToken token = new AccessToken(1, "");

		Contact newContact = new Contact();
		newContact.setLastname("newContact");

		List<Contact> updatedContacts = ImmutableList.of(newContact);

		Set<Integer> archivedContactIds = ImmutableSet.of(1, 2);

		ContactUpdates contactUpdates = new ContactUpdates();
		contactUpdates.setContacts(updatedContacts);
		contactUpdates.setArchived(archivedContactIds);

		Set<Integer> removalCandidates = ImmutableSet.of(3);

		Contact newUser = new Contact();
		newUser.setLastname("obmuser");

		List<Contact> updatedUsers = ImmutableList.of(newUser);

		Set<Integer> archivedUserIds = ImmutableSet.of(5, 7, 8);

		ContactUpdates userUpdates = new ContactUpdates();
		userUpdates.setContacts(updatedUsers);
		userUpdates.setArchived(archivedUserIds);

		Set<Contact> allUpdatedContacts = new HashSet<Contact>();
		allUpdatedContacts.addAll(updatedContacts);
		allUpdatedContacts.addAll(updatedUsers);

		Folder updatedContactFolder1 = Folder.builder().uid(1).name("updatedContactFolder1").build();
		Folder updatedContactFolder2 = Folder.builder().uid(2).name("updatedContactFolder2").build();

		Set<Folder> updatedContactFolders = new HashSet<Folder>();
		updatedContactFolders.add(updatedContactFolder1);
		updatedContactFolders.add(updatedContactFolder2);

		int defautUsersIdFolder = -1;
		String defaultUsersNameFolder = "users";
		Folder updatedUserFolder = Folder.builder().uid(defautUsersIdFolder).name(defaultUsersNameFolder).build();

		Folder removedContactFolder1 = Folder.builder().uid(10).name("removedContactFolder1").build();
		Folder removedContactFolder2 = Folder.builder().uid(11).name("removedContactFolder2").build();
		Set<Folder> removedContactFolders =  Sets.newHashSet(removedContactFolder1, removedContactFolder2);
		
		ObmHelper helper = mockHelper();

		ContactDao contactDao = EasyMock.createMock(ContactDao.class);
		expect(contactDao.findUpdatedContacts(timestamp, token)).andReturn(contactUpdates).once();
		expect(contactDao.findRemovalCandidates(timestamp, token)).andReturn(removalCandidates).once();
		
		expect(helper.getConnection()).andReturn(null);
		helper.cleanup(null, null, null);
		expect(helper.selectNow(null)).andReturn(new Date());
		
		expect(contactDao.findUpdatedFolders(timestamp, token)).andReturn(updatedContactFolders).once();
		expect(contactDao.findRemovedFolders(timestamp, token)).andReturn(removedContactFolders).once();

		expect(helper.getConnection()).andReturn(null);
		helper.cleanup(null, null, null);
		expect(helper.selectNow(null)).andReturn(new Date());
		
		UserDao userDao = EasyMock.createMock(UserDao.class);
		expect(userDao.findUpdatedUsers(timestamp, token)).andReturn(userUpdates).once();

		ObmSyncConfigurationService configuration = EasyMock.createMock(ObmSyncConfigurationService.class);
		expect(configuration.syncUsersAsAddressBook()).andReturn(true).atLeastOnce();

		ContactConfiguration contactConfiguration = EasyMock.createMock(ContactConfiguration.class);
		expect(contactConfiguration.getAddressBookUserId()).andReturn(-1);
		expect(contactConfiguration.getAddressBookUsersName()).andReturn("users");
		
		Object[] mocks = { helper, contactDao, userDao, configuration, contactConfiguration };
		EasyMock.replay(mocks);

		AddressBookBindingImpl binding = new AddressBookBindingImpl(contactDao, userDao, null, helper, 
				configuration, contactConfiguration);
		AddressBookChangesResponse changes = binding.getAddressBookSync(token, timestamp);

		EasyMock.verify(mocks);

		assertThat(changes.getContactChanges().getUpdated()).containsOnly(newContact, newUser);
		assertThat(changes.getRemovedContacts()).containsOnly(1, 2, 3, 5, 7, 8);
		assertThat(changes.getUpdatedAddressBooks()).containsOnly(updatedContactFolder1, updatedContactFolder2, updatedUserFolder);
		assertThat(changes.getRemovedAddressBooks()).containsOnly(removedContactFolder1, removedContactFolder2);
	}

	/**
	 * Tests that getSync() returns the updated contacts and address book, but
	 * not the full list of users from the user DAO, when
	 * AddressBookBindingImpl.GLOBAL_ADDRESS_BOOK_SYNC is set to false.
	 */
	@Test
	public void testGetSyncNoGlobalAddressBookSync() throws ServerFault, SQLException {
		Date timestamp = new Date();

		AccessToken token = new AccessToken(1, "");

		Contact newContact = new Contact();
		newContact.setLastname("newContact");

		List<Contact> updatedContacts = ImmutableList.of(newContact);

		Set<Integer> archivedContactIds = ImmutableSet.of(1, 2);

		ContactUpdates contactUpdates = new ContactUpdates();
		contactUpdates.setContacts(updatedContacts);
		contactUpdates.setArchived(archivedContactIds);

		Set<Integer> removalCandidates = ImmutableSet.of(3);

		Set<Contact> allUpdatedContacts = new HashSet<Contact>();
		allUpdatedContacts.addAll(updatedContacts);

		Folder updatedContactFolder1 = Folder.builder().uid(1).name("updatedContactFolder1").build();
		Folder updatedContactFolder2 = Folder.builder().uid(2).name("updatedContactFolder2").build();
		Set<Folder> updatedContactFolders = Sets.newHashSet(updatedContactFolder1, updatedContactFolder2);

		Folder removedContactFolder1 = Folder.builder().uid(10).name("removedContactFolder1").build();
		Folder removedContactFolder2 = Folder.builder().uid(11).name("removedContactFolder2").build();
		Set<Folder> removedContactFolders =  Sets.newHashSet(removedContactFolder1, removedContactFolder2);
		
		removedContactFolders.add(removedContactFolder1);
		removedContactFolders.add(removedContactFolder2);

		ObmHelper helper = mockHelper();

		ContactDao contactDao = EasyMock.createMock(ContactDao.class);
		
		expect(contactDao.findUpdatedContacts(timestamp, token)).andReturn(contactUpdates).once();
		expect(contactDao.findRemovalCandidates(timestamp, token)).andReturn(removalCandidates).once();
		expect(helper.getConnection()).andReturn(null);
		helper.cleanup(null, null, null);
		expect(helper.selectNow(null)).andReturn(new Date());

		expect(contactDao.findUpdatedFolders(timestamp, token)).andReturn(updatedContactFolders).once();
		expect(contactDao.findRemovedFolders(timestamp, token)).andReturn(removedContactFolders).once();
		expect(helper.getConnection()).andReturn(null);
		helper.cleanup(null, null, null);
		expect(helper.selectNow(null)).andReturn(new Date());
		
		ObmSyncConfigurationService configuration = EasyMock.createMock(ObmSyncConfigurationService.class);
		expect(
				configuration.syncUsersAsAddressBook()).andReturn(false).atLeastOnce();

		Object[] mocks = { helper, contactDao, configuration };
		EasyMock.replay(mocks);

		AddressBookBindingImpl binding = new AddressBookBindingImpl(contactDao, null, null, helper,
				configuration, null);
		AddressBookChangesResponse changes = binding.getAddressBookSync(token, timestamp);

		EasyMock.verify(mocks);

		assertThat(changes.getContactChanges().getUpdated()).containsOnly(newContact);
		assertThat(changes.getRemovedContacts()).containsOnly(1, 2, 3);
		assertThat(changes.getUpdatedAddressBooks()).containsOnly(updatedContactFolder1, updatedContactFolder2);
		assertThat(changes.getRemovedAddressBooks()).containsOnly(removedContactFolder1, removedContactFolder2);
	}
}