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
package org.obm.push.contacts;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.fest.assertions.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.configuration.ContactConfiguration;
import org.obm.filter.SlowFilterRunner;
import org.obm.push.backend.CollectionPath;
import org.obm.push.backend.CollectionPath.Builder;
import org.obm.push.backend.OpushCollection;
import org.obm.push.backend.PathsToCollections;
import org.obm.push.bean.Credentials;
import org.obm.push.bean.Device;
import org.obm.push.bean.FolderSyncState;
import org.obm.push.bean.FolderType;
import org.obm.push.bean.ItemChange;
import org.obm.push.bean.ItemChangeBuilder;
import org.obm.push.bean.MSContact;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.User;
import org.obm.push.bean.User.Factory;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.service.impl.MappingService;
import org.obm.push.utils.DateUtils;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.auth.AuthFault;
import org.obm.sync.auth.ServerFault;
import org.obm.sync.book.AddressBook;
import org.obm.sync.book.Contact;
import org.obm.sync.book.Folder;
import org.obm.sync.client.book.BookClient;
import org.obm.sync.client.login.LoginService;
import org.obm.sync.items.ContactChanges;
import org.obm.sync.items.FolderChanges;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provider;

@RunWith(SlowFilterRunner.class)
public class ContactsBackendTest {

	private static final String COLLECTION_CONTACT_PREFIX = "obm:\\\\test@test\\contacts\\";
	private static final String DEFAULT_PARENT_BOOK_ID = "0";
	private static final String DEFAULT_PARENT_BOOK_NAME = "contacts";
	
	private User user;
	private Device device;
	private UserDataRequest userDataRequest;
	
	private IMocksControl mocks;
	private MappingService mappingService;
	private BookClient bookClient;
	private LoginService loginService;
	private ContactConfiguration contactConfiguration;
	private Provider<CollectionPath.Builder> collectionPathBuilderProvider;
	private AccessToken token;
	
	@Before
	public void setUp() {
		user = Factory.create().createUser("test@test", "test@domain", "displayName");
		device = new Device.Factory().create(null, "iPhone", "iOs 5", "my phone");
		userDataRequest = new UserDataRequest(new Credentials(user, "password"), "noCommand", device, null);
		token = new AccessToken(0, "OBM");
		
		mocks = createControl();
		mappingService = mocks.createMock(MappingService.class);
		bookClient = mocks.createMock(BookClient.class);
		loginService = mocks.createMock(LoginService.class);
		contactConfiguration = mocks.createMock(ContactConfiguration.class);
		collectionPathBuilderProvider = mocks.createMock(Provider.class);
		expectDefaultAddressAndParentForContactConfiguration();
	}
	
	@Test
	public void sortedByDefaultFolderName() {
		final String defaultFolderName = DEFAULT_PARENT_BOOK_NAME;
		
		Folder f1 = createFolder("users", -1);
		Folder f2 = createFolder("collected_contacts", 2);
		Folder f3 = createFolder(defaultFolderName, 3);
		Folder f4 = createFolder("my address book", 4);
		
		ImmutableList<Folder> immutableList = ImmutableList.of(f1, f2, f3, f4);
		TreeSet<Folder> treeset = new TreeSet<Folder>(
				new ComparatorUsingFolderName(defaultFolderName));
		treeset.addAll(immutableList);
		
		assertThat(treeset).hasSize(4);
		assertThat(treeset).contains(immutableList.toArray());
		assertThat(treeset.first().getName()).isEqualTo(defaultFolderName);
		assertThat(treeset.last().getName()).isEqualTo("users");
	}

	private Folder createFolder(String name, int uid) {
		return Folder.builder().name(name).uid(uid).build();
	}
	
	@Test
	public void testGetPIMDataType() {
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, null);
		assertThat(contactsBackend.getPIMDataType()).isEqualTo(PIMDataType.CONTACTS);
	}

	@Test
	public void testGetItemEstimateSize() throws Exception {
		Date currentDate = DateUtils.getCurrentDate();
		FolderSyncState lastKnownState = new FolderSyncState("1234567890a");
		lastKnownState.setLastSync(currentDate);

		expectLoginBehavior(token);

		int otherContactCollectionUid = 1;
		int targetcontactCollectionUid = 2;
		List<AddressBook> books = ImmutableList.of(
				newAddressBookObject("folder", otherContactCollectionUid, false),
				newAddressBookObject("folder_1", targetcontactCollectionUid, false));
		
		expectListAllBooks(token, books);
		expectBuildCollectionPath("folder", otherContactCollectionUid);
		expectBuildCollectionPath("folder_1", targetcontactCollectionUid);

		int contactChangedUid = 215;
		Contact contactChanged = newContactObject(contactChangedUid);
		ContactChanges contactChanges = new ContactChanges(ImmutableList.<Contact> of(contactChanged), ImmutableSet.<Integer> of(), currentDate);
		expect(bookClient.listContactsChanged(token, currentDate, targetcontactCollectionUid))
			.andReturn(contactChanges).once();
		
		expectMappingServiceCollectionIdBehavior(books);
		
		expect(mappingService.getServerIdFor(targetcontactCollectionUid, String.valueOf(contactChangedUid)))
			.andReturn("1215");
		
		mocks.replay();
		
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, bookClient, loginService, contactConfiguration, collectionPathBuilderProvider);
		int itemEstimateSize = contactsBackend.getItemEstimateSize(userDataRequest, targetcontactCollectionUid, lastKnownState, null);

		mocks.verify();
		
		assertThat(itemEstimateSize).isEqualTo(1);
	}
	
	@Test
	public void testCreateOrUpdate() throws Exception {
		int otherContactCollectionUid = 1;
		int targetcontactCollectionUid = 2;
		int serverId = 215;
		String serverIdAsString = String.valueOf(serverId);
		String clientId = "1";

		List<AddressBook> books = ImmutableList.of(
				newAddressBookObject("folder", otherContactCollectionUid, false),
				newAddressBookObject("folder_1", targetcontactCollectionUid, false));
		
		expectLoginBehavior(token);
		expectListAllBooks(token,books);
		expectBuildCollectionPath("folder", otherContactCollectionUid);
		expectBuildCollectionPath("folder_1", targetcontactCollectionUid);
		
		Contact contact = newContactObject(serverId);
		expect(bookClient.modifyContact(token, targetcontactCollectionUid, contact))
			.andReturn(contact).once();
		
		expectMappingServiceCollectionIdBehavior(books);
		
		expect(mappingService.getItemIdFromServerId(serverIdAsString)).andReturn(serverId).once();
		expect(mappingService.getServerIdFor(targetcontactCollectionUid, serverIdAsString))
			.andReturn(serverIdAsString);

		mocks.replay();
		
		MSContact msContact = new MSContact();
		
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, bookClient, loginService, contactConfiguration, collectionPathBuilderProvider);
		String newServerId = contactsBackend.createOrUpdate(userDataRequest, targetcontactCollectionUid, serverIdAsString, clientId, msContact);
		
		mocks.verify();
		
		assertThat(newServerId).isEqualTo(serverIdAsString);
	}
	
	@Test
	public void testDelete() throws Exception {
		int otherContactCollectionUid = 1;
		int targetcontactCollectionUid = 2;
		int serverId = 2;
		String serverIdAsString = String.valueOf(serverId);
		
		List<AddressBook> books = ImmutableList.of(
				newAddressBookObject("folder", otherContactCollectionUid, false),
				newAddressBookObject("folder_1", targetcontactCollectionUid, false));

		expectLoginBehavior(token);
		expectListAllBooks(token, books);
		expectBuildCollectionPath("folder", otherContactCollectionUid);
		expectBuildCollectionPath("folder_1", targetcontactCollectionUid);
		
		expect(bookClient.removeContact(token, targetcontactCollectionUid, serverId))
			.andReturn(newContactObject(serverId)).once();

		expect(mappingService.getItemIdFromServerId(serverIdAsString)).andReturn(serverId).once();
		expectMappingServiceCollectionIdBehavior(books);

		mocks.replay();
		
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, bookClient, loginService, contactConfiguration, collectionPathBuilderProvider);
		contactsBackend.delete(userDataRequest, serverId, serverIdAsString, true);
		
		mocks.verify();
	}

	@Test
	public void testFetch() throws Exception {
		int otherContactCollectionUid = 1;
		int targetcontactCollectionUid = 2;
		int serverId = 215;
		String serverIdAsString = String.valueOf(serverId);
		
		List<AddressBook> books = ImmutableList.of(
				newAddressBookObject("folder", otherContactCollectionUid, false),
				newAddressBookObject("folder_1", targetcontactCollectionUid, false));

		expectLoginBehavior(token);
		expectListAllBooks(token, books);
		expectBuildCollectionPath("folder", otherContactCollectionUid);
		expectBuildCollectionPath("folder_1", targetcontactCollectionUid);
		
		Contact contact = newContactObject(serverId);
		expect(bookClient.getContactFromId(token, targetcontactCollectionUid, serverId)).andReturn(contact);

		expectMappingServiceCollectionIdBehavior(books);
		expect(mappingService.getItemIdFromServerId(serverIdAsString)).andReturn(serverId);
		expect(mappingService.getCollectionIdFromServerId(serverIdAsString)).andReturn(targetcontactCollectionUid);
		expect(mappingService.getServerIdFor(targetcontactCollectionUid, serverIdAsString)).andReturn(serverIdAsString);
	
		mocks.replay();
		
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, bookClient, loginService, contactConfiguration, collectionPathBuilderProvider);
		List<ItemChange> itemChanges = contactsBackend.fetch(userDataRequest, ImmutableList.of(serverIdAsString), null);
		
		mocks.verify();
		
		ItemChange itemChange = new ItemChange(serverIdAsString, null, null, null, false);
		itemChange.setData(new ContactConverter().convert(contact));
		
		assertThat(itemChanges).hasSize(1);
		assertThat(itemChanges).containsOnly(itemChange);
	}

	private void expectListAllBooks(AccessToken token, List<AddressBook> addressbooks) throws ServerFault {
		expect(bookClient.listAllBooks(token))
			.andReturn(addressbooks)
			.once();
	}

	private Contact newContactObject(int contactUid) {
		Contact contact = new Contact();
		contact.setUid(contactUid);
		return contact;
	}

	private AddressBook newAddressBookObject(String name, Integer uid, boolean readOnly) {
		return new AddressBook(name, uid, readOnly);
	}
	
	private void expectLoginBehavior(AccessToken token) throws AuthFault {
		expect(loginService.login(userDataRequest.getUser().getLoginAtDomain(), userDataRequest.getPassword()))
			.andReturn(token).anyTimes();
		
		loginService.logout(token);
		expectLastCall().anyTimes();
	}

	private void expectDefaultAddressAndParentForContactConfiguration() {
		expect(contactConfiguration.getDefaultAddressBookName())
			.andReturn(DEFAULT_PARENT_BOOK_NAME).anyTimes();
		
		expect(contactConfiguration.getDefaultParentId())
			.andReturn(DEFAULT_PARENT_BOOK_ID).anyTimes();
	}

	private void expectMappingServiceCollectionIdBehavior(List<AddressBook> books) 
			throws CollectionNotFoundException, DaoException {
		
		for (AddressBook book : books) {
			expect(mappingService.getCollectionIdFor(userDataRequest.getDevice(),
					COLLECTION_CONTACT_PREFIX + backendName(book.getName(), book.getUid()))).andReturn(book.getUid()).anyTimes();

			expect(mappingService.collectionIdToString(book.getUid())).andReturn(String.valueOf(book.getUid())).anyTimes();
		}
	}

	private Builder expectBuildCollectionPath(String displayName, int folderUid) {
		CollectionPath collectionPath = new ContactCollectionPath(displayName, folderUid);
		CollectionPath.Builder collectionPathBuilder = expectCollectionPathBuilder(collectionPath, displayName, folderUid);
		expectCollectionPathBuilderPovider(collectionPathBuilder);
		return collectionPathBuilder;
	}

	private CollectionPath.Builder expectCollectionPathBuilder(CollectionPath collectionPath,
			String displayName, int folderUid) {
		
		CollectionPath.Builder collectionPathBuilder = mocks.createMock(CollectionPath.Builder.class);
		expect(collectionPathBuilder.userDataRequest(userDataRequest))
			.andReturn(collectionPathBuilder).once();
		
		expect(collectionPathBuilder.pimType(PIMDataType.CONTACTS))
			.andReturn(collectionPathBuilder).once();
		
		expect(collectionPathBuilder.backendName(backendName(displayName, folderUid)))
			.andReturn(collectionPathBuilder).once();
		
		expect(collectionPathBuilder.build())
			.andReturn(collectionPath).once();
		
		return collectionPathBuilder;
	}

	private void expectCollectionPathBuilderPovider(CollectionPath.Builder collectionPathBuilder) {
			expect(collectionPathBuilderProvider.get())
				.andReturn(collectionPathBuilder).once();
	}
	
	private static class ContactCollectionPath extends CollectionPath {

		public ContactCollectionPath(String displayName, int folderUid) {
			super(String.format("%s%s", COLLECTION_CONTACT_PREFIX, ContactsBackendTest.backendName(displayName, folderUid)),
					PIMDataType.CONTACTS, displayName);
		}
	}

	private static String backendName(String displayName, int folderUid) {
		return String.format("%d-%s", folderUid, displayName);
	}
	
	@Test
	public void changeDisplayNameIsTookFromFolderForAdd() {
		String folder1Name = "f1";
		String folder2Name = "f2";
		FolderChanges changes = FolderChanges.builder().updated(
				createFolder(folder1Name, 1), createFolder(folder2Name, 2)).build();

		expectBuildCollectionPath(folder1Name, 1);
		expectBuildCollectionPath(folder2Name, 2);

		mocks.replay();
		
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, bookClient, loginService, contactConfiguration, collectionPathBuilderProvider);
		Iterable<OpushCollection> actual = contactsBackend.changedCollections(userDataRequest, changes).collections();
		
		mocks.verify();
		
		assertThat(actual).containsOnly(
				OpushCollection.builder()
					.collectionPath(new ContactCollectionPath(folder1Name, 1))
					.displayName(folder1Name)
					.build(),
				OpushCollection.builder()
					.collectionPath(new ContactCollectionPath(folder2Name, 2))
					.displayName(folder2Name)
					.build());
	}
	
	@Test
	public void changeDisplayNameIsTookFromFolderForDelete() {
		String folder1Name = "f1";
		String folder2Name = "f2";
		int folder1Uid = 1;
		int folder2Uid = 2;
		ContactCollectionPath f1CollectionPath = new ContactCollectionPath(folder1Name, folder1Uid);
		ContactCollectionPath f2CollectionPath = new ContactCollectionPath(folder2Name, folder2Uid);
		FolderChanges changes = FolderChanges.builder().removed(
				createFolder(folder1Name, folder1Uid), createFolder(folder2Name, folder2Uid)).build();
		
		PathsToCollections adds = PathsToCollections.builder().build();
		Set<CollectionPath> lastKnown = ImmutableSet.<CollectionPath>of(f1CollectionPath, f2CollectionPath);

		expectBuildCollectionPath(folder1Name, folder1Uid);
		expectBuildCollectionPath(folder2Name, folder2Uid);

		mocks.replay();
		
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, bookClient, loginService, contactConfiguration, collectionPathBuilderProvider);
		Iterable<CollectionPath> actual = contactsBackend.deletedCollections(userDataRequest, changes, lastKnown, adds);
		
		mocks.verify();
		
		assertThat(actual).containsOnly(f1CollectionPath, f2CollectionPath);
	}
	
	@Test
	public void createItemChangeGetsDisplayNameFromOpushCollection() throws Exception {
		AccessToken token = new AccessToken(0, "OBM");
		expectLoginBehavior(token);

		int folderUid = 3;
		expect(mappingService.collectionIdToString(folderUid)).andReturn(String.valueOf(folderUid)).anyTimes();
		expect(mappingService.getCollectionIdFor(userDataRequest.getDevice(), 
				COLLECTION_CONTACT_PREFIX + backendName("technicalName", folderUid)))
			.andReturn(folderUid).anyTimes();

		OpushCollection collection = OpushCollection.builder()
				.collectionPath(new ContactCollectionPath("technicalName", folderUid))
				.displayName("great display name!")
				.build();
		
		mocks.replay();
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, bookClient, loginService, contactConfiguration, collectionPathBuilderProvider);
		ItemChange itemChange = contactsBackend.createItemChange(userDataRequest, collection);
		mocks.verify();
		
		assertThat(itemChange).isEqualTo(new ItemChangeBuilder()
				.displayName("great display name!")
				.parentId("0")
				.serverId("3")
				.itemType(FolderType.USER_CREATED_CONTACTS_FOLDER)
				.build());
	}
	
	@Test
	public void filterUnknownDeletedItemsFromAddressBooksChanged() {
		String folderOneName = "f1";
		String folderTwoName = "f2";
		Folder folder1 = createFolder(folderOneName, 1);
		Folder folder2 = createFolder(folderTwoName, 2);
		FolderChanges changes = FolderChanges.builder().removed(folder1, folder2).build();
		
		ContactCollectionPath f1CollectionPath = new ContactCollectionPath(folderOneName, 1);
		ImmutableSet<CollectionPath> lastKnown = ImmutableSet.<CollectionPath>of(f1CollectionPath);
		PathsToCollections adds = PathsToCollections.builder().build();

		expectBuildCollectionPath(folderOneName, 1);
		expectBuildCollectionPath(folderTwoName, 2);

		mocks.replay();
		
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, collectionPathBuilderProvider);
		Iterable<CollectionPath> actual = contactsBackend.deletedCollections(userDataRequest, changes, lastKnown, adds);
		
		mocks.verify();
		
		assertThat(actual).containsOnly(f1CollectionPath);
	}
	
	@Test
	public void createItemChangeBuildsWithParentIdFromConfiguration() throws Exception {
		int folderUid = 3;
		expect(mappingService.collectionIdToString(folderUid)).andReturn(String.valueOf(folderUid)).anyTimes();
		expect(mappingService.getCollectionIdFor(userDataRequest.getDevice(), 
				COLLECTION_CONTACT_PREFIX + backendName("technicalName", folderUid)))
			.andReturn(folderUid).anyTimes();

		OpushCollection collection = OpushCollection.builder()
				.collectionPath(new ContactCollectionPath("technicalName", folderUid))
				.displayName("displayName")
				.build();
		
		mocks.replay();
		ContactsBackend contactsBackend = new ContactsBackend(mappingService, null, null, contactConfiguration, collectionPathBuilderProvider);
		ItemChange itemChange = contactsBackend.createItemChange(userDataRequest, collection);
		mocks.verify();

		assertThat(itemChange.getParentId()).isEqualTo(DEFAULT_PARENT_BOOK_ID);
	}

	@Test(expected=IllegalArgumentException.class)
	public void builderBackendNameWhenNamePartIsNull() {
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, null);
		contactsBackend.backendNameFromParts(3, null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void builderBackendNameWhenNamePartIsEmpty() {
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, null);
		contactsBackend.backendNameFromParts(3, "");
	}

	@Test
	public void builderBackendName() {
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, null);
		assertThat(contactsBackend.backendNameFromParts(5, "a name")).isEqualTo("5-a name");
	}

	@Test
	public void builderBackendNameWhenNegativeUid() {
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, null);
		assertThat(contactsBackend.backendNameFromParts(-1, "name")).isEqualTo("-1-name");
	}
	
	@Test
	public void testSortingKeepsFolderWithSameNames() {
		Folder folder1 = createFolder("name", 1);
		Folder folder2 = createFolder("name", 2);
		Folder folder3 = createFolder("name", 3);
		FolderChanges changes = FolderChanges.builder().updated(folder1, folder2, folder3).build();
		
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, collectionPathBuilderProvider);
		Iterable<Folder> result = contactsBackend.sortedFolderChangesByDefaultAddressBook(changes, "defaultName");
		
		assertThat(result).hasSize(3);
		assertThat(result).containsOnly(folder1, folder2, folder3);
	}
	
	@Test
	public void testSortingKeepsFolderWithSameNamesAndSameUid() {
		Folder folder1 = createFolder("name", 1);
		Folder folder2 = createFolder("name", 2);
		Folder folder3 = createFolder("name", 2);
		Folder folder4 = createFolder("name", 3);
		Folder folder5 = createFolder("name", 1);
		FolderChanges changes = FolderChanges.builder().updated(folder1, folder2, folder3, folder4, folder5).build();
		
		ContactsBackend contactsBackend = new ContactsBackend(null, null, null, null, collectionPathBuilderProvider);
		Iterable<Folder> result = contactsBackend.sortedFolderChangesByDefaultAddressBook(changes, "defaultName");
		
		assertThat(result).hasSize(3);
		assertThat(result).containsOnly(folder1, folder2, folder4);
	}
}
