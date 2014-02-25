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
package org.obm.push.contacts;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.naming.NoPermissionException;

import org.obm.breakdownduration.bean.Watch;
import org.obm.configuration.ContactConfiguration;
import org.obm.push.backend.CollectionPath;
import org.obm.push.backend.DataDelta;
import org.obm.push.backend.OpushCollection;
import org.obm.push.backend.PIMBackend;
import org.obm.push.backend.PathsToCollections;
import org.obm.push.backend.PathsToCollections.Builder;
import org.obm.push.backend.WindowingContact;
import org.obm.push.backend.WindowingContactChanges;
import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.BreakdownGroups;
import org.obm.push.bean.FolderSyncState;
import org.obm.push.bean.FolderType;
import org.obm.push.bean.IApplicationData;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.MSContact;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.SyncCollectionOptions;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.WindowingKey;
import org.obm.push.bean.change.client.SyncClientCommands;
import org.obm.push.bean.change.hierarchy.CollectionChange;
import org.obm.push.bean.change.hierarchy.CollectionDeletion;
import org.obm.push.bean.change.hierarchy.HierarchyCollectionChanges;
import org.obm.push.bean.change.item.ItemChange;
import org.obm.push.bean.change.item.ItemDeletion;
import org.obm.push.exception.ConversionException;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.HierarchyChangesException;
import org.obm.push.exception.UnexpectedObmSyncServerException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.exception.activesync.InvalidSyncKeyException;
import org.obm.push.exception.activesync.ItemNotFoundException;
import org.obm.push.exception.activesync.NotAllowedException;
import org.obm.push.exception.activesync.ProcessingEmailException;
import org.obm.push.impl.ObmSyncBackend;
import org.obm.push.resource.ResourcesUtils;
import org.obm.push.service.ClientIdService;
import org.obm.push.service.impl.MappingService;
import org.obm.push.store.WindowingDao;
import org.obm.push.utils.DateUtils;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.auth.ServerFault;
import org.obm.sync.book.AddressBook;
import org.obm.sync.book.Contact;
import org.obm.sync.book.Folder;
import org.obm.sync.client.book.BookClient;
import org.obm.sync.exception.ContactNotFoundException;
import org.obm.sync.items.ContactChanges;
import org.obm.sync.items.FolderChanges;
import org.obm.sync.services.IAddressBook;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
@Watch(BreakdownGroups.CONTACTS)
public class ContactsBackend extends ObmSyncBackend implements PIMBackend {
	
	private final ContactConfiguration contactConfiguration;
	private final BookClient.Factory bookClientFactory;
	private final WindowingDao windowingDao;
	private final ClientIdService clientIdService;
	private final ContactConverter contactConverter;
	@Inject
	@VisibleForTesting ContactsBackend(MappingService mappingService, 
			BookClient.Factory bookClientFactory, 
			ContactConfiguration contactConfiguration,
			Provider<CollectionPath.Builder> collectionPathBuilderProvider,
			WindowingDao windowingDao,
			ClientIdService clientIdService,
			ContactConverter contactConverter) {
		
		super(mappingService, collectionPathBuilderProvider);
		this.bookClientFactory = bookClientFactory;
		this.contactConfiguration = contactConfiguration;
		this.windowingDao = windowingDao;
		this.clientIdService = clientIdService;
		this.contactConverter = contactConverter;
	}

	@Override
	public PIMDataType getPIMDataType() {
		return PIMDataType.CONTACTS;
	}
	
	@Override
	public HierarchyCollectionChanges getHierarchyChanges(UserDataRequest udr, 
			FolderSyncState lastKnownState, FolderSyncState outgoingSyncState)
			throws DaoException, InvalidSyncKeyException {

		try {
			FolderChanges folderChanges = listAddressBooksChanged(udr, lastKnownState);
			Set<CollectionPath> lastKnownCollections = lastKnownCollectionPath(udr, lastKnownState, getPIMDataType());
			
			PathsToCollections changedCollections = changedCollections(udr, folderChanges);
			Set<CollectionPath> deletedCollections = deletedCollections(udr, folderChanges, lastKnownCollections, changedCollections);
			Iterable<OpushCollection> addCollections = addedCollections(lastKnownCollections, changedCollections);
			snapshotHierarchy(udr, lastKnownCollections, changedCollections, deletedCollections, outgoingSyncState);

			return buildHierarchyItemsChanges(udr, addCollections, deletedCollections);
		} catch (CollectionNotFoundException e) {
			throw new HierarchyChangesException(e);
		}
	}

	private Date backendLastSyncDate(FolderSyncState lastKnownState) throws DaoException, InvalidSyncKeyException {

		if (lastKnownState.isInitialFolderSync()) {
			return DateUtils.getEpochCalendar().getTime();
		} else {
			return getLastSyncDateFromSyncState(lastKnownState);
		}
	}

	private Date getLastSyncDateFromSyncState(FolderSyncState lastKnownState)
			throws InvalidSyncKeyException, DaoException {
		
		Date lastSyncDate = mappingService.getLastBackendMapping(getPIMDataType(), lastKnownState);
		if (lastSyncDate != null) {
			return lastSyncDate;
		}
		throw new InvalidSyncKeyException(lastKnownState.getSyncKey());
	}

	private void snapshotHierarchy(UserDataRequest udr, Set<CollectionPath> lastKnownCollections,
			PathsToCollections changedCollections, Set<CollectionPath> deletedCollections,
			FolderSyncState outgoingSyncState) throws DaoException {

		Set<CollectionPath> remainingKnownCollections = Sets.difference(lastKnownCollections, deletedCollections);
		Set<CollectionPath> currentCollections = Sets.union(remainingKnownCollections, changedCollections.pathKeys());
		snapshotHierarchy(udr, currentCollections, outgoingSyncState);
	}

	@Override
	protected CollectionChange createCollectionChange(UserDataRequest udr, OpushCollection collection)
			throws DaoException, CollectionNotFoundException {
		
		CollectionPath collectionPath = collection.collectionPath();
		return CollectionChange.builder()
				.collectionId(getCollectionIdFromCollectionPath(udr, collectionPath.collectionPath()))
				.parentCollectionId(contactConfiguration.getDefaultParentId())
				.folderType(getFolderType(udr, collection))
				.displayName(collection.displayName())
				.isNew(true)
				.build();
	}

	@Override
	protected CollectionDeletion createCollectionDeletion(UserDataRequest udr, CollectionPath collectionPath)
			throws CollectionNotFoundException, DaoException {
		
		return CollectionDeletion.builder()
				.collectionId(getCollectionIdFromCollectionPath(udr, collectionPath.collectionPath()))
				.build();
	}

	@VisibleForTesting Set<CollectionPath> deletedCollections(UserDataRequest udr, FolderChanges folderChanges, 
			Set<CollectionPath> lastKnownCollections, PathsToCollections changedCollections) {
		
		PathsToCollections removedCollections = foldersToCollection(udr, folderChanges.getRemoved());
		return FluentIterable
				.from(removedCollections.pathKeys())
				.filter(Predicates.in(lastKnownCollections))
				.filter(Predicates.not(Predicates.in(changedCollections.pathKeys())))
				.toSet();
	}

	@VisibleForTesting PathsToCollections changedCollections(UserDataRequest udr, FolderChanges folderChanges) {
		Iterable<Folder> folderChangesSorted = 
				sortedFolderChangesByDefaultAddressBook(folderChanges, contactConfiguration.getDefaultAddressBookName());
		return foldersToCollection(udr, folderChangesSorted);
	}

	private PathsToCollections foldersToCollection(final UserDataRequest udr, Iterable<Folder> folders) {
		Builder builder = PathsToCollections.builder();
		for (Folder folder : folders) {
			OpushCollection collection = collectionFromFolder(udr, folder);
			builder.put(collection.collectionPath(), collection);
		}
		return builder.build();
	}

	protected OpushCollection collectionFromFolder(UserDataRequest udr, Folder folder) {
		String backendName = ContactCollectionPath.backendName(folder);
		return OpushCollection.builder()
				.collectionPath(collectionPathBuilderProvider.get()
						.userDataRequest(udr)
						.pimType(getPIMDataType())
						.backendName(backendName)
						.build())
				.ownerLoginAtDomain(folder.getOwnerLoginAtDomain())
				.displayName(folder.getName())
				.build();
	}

	@VisibleForTesting Iterable<Folder> sortedFolderChangesByDefaultAddressBook(FolderChanges folderChanges, String defaultAddressBookName) {
		return ImmutableSortedSet
				.orderedBy(new ComparatorUsingFolderName(defaultAddressBookName))
				.addAll(folderChanges.getUpdated())
				.build();
	}

	private FolderChanges listAddressBooksChanged(UserDataRequest udr, FolderSyncState lastKnownState)
			throws UnexpectedObmSyncServerException, DaoException, InvalidSyncKeyException {
		
		AccessToken token = getAccessToken(udr);
		Date lastSyncDate = backendLastSyncDate(lastKnownState);
		try {
			return getBookClient(udr).listAddressBooksChanged(token, lastSyncDate);
		} catch (ServerFault e) {
			throw new UnexpectedObmSyncServerException(e);
		}
	}
	
	private String getCollectionIdFromCollectionPath(UserDataRequest udr, String collectionPath)
			throws DaoException, CollectionNotFoundException {
		
		Integer collectionId = mappingService.getCollectionIdFor(udr.getDevice(), collectionPath);
		return mappingService.collectionIdToString(collectionId);
	}
	
	private FolderType getFolderType(UserDataRequest udr, OpushCollection collection) {
		if (isDefaultFolder(udr, collection)) {
			return FolderType.DEFAULT_CONTACTS_FOLDER;
		} else {
			return FolderType.USER_CREATED_CONTACTS_FOLDER;
		}
	}
	
	@VisibleForTesting boolean isDefaultFolder(UserDataRequest udr, OpushCollection collection) {
		String folderName = ContactCollectionPath.folderName(collection.collectionPath());
		boolean isOwner = udr.getUser().getLoginAtDomain().equalsIgnoreCase(collection.getOwnerLoginAtDomain());
		boolean isDefaultAddressBookName = folderName.equalsIgnoreCase(contactConfiguration.getDefaultAddressBookName());
		return isOwner && isDefaultAddressBookName;
	}
	
	@Override
	public int getItemEstimateSize(UserDataRequest udr, ItemSyncState state, Integer collectionId, 
		SyncCollectionOptions syncCollectionOptions) throws CollectionNotFoundException, 
		DaoException, UnexpectedObmSyncServerException {
	
		WindowingContactChanges.Builder builder = WindowingContactChanges.builder();
		Date lastSync = getAllChanges(udr, state, collectionId, builder);
		DataDelta dataDelta = convertToDataDelta(builder.build(), collectionId, lastSync, state.getSyncKey());
		return dataDelta.getItemEstimateSize();
	}
	
	@Override
	public DataDelta getChanged(UserDataRequest udr, ItemSyncState itemSyncState, AnalysedSyncCollection syncCollection, 
			SyncClientCommands clientCommands, SyncKey newSyncKey)
		throws UnexpectedObmSyncServerException, DaoException, CollectionNotFoundException {

		SyncKey requestSyncKey = syncCollection.getSyncKey();
		WindowingKey key = new WindowingKey(udr.getUser(), udr.getDevId(), syncCollection.getCollectionId(), requestSyncKey);
		
		if (windowingDao.hasPendingChanges(key)) {
			return continueWindowing(syncCollection, key, newSyncKey, itemSyncState.getSyncDate());
		} else {
			return startWindowing(udr, itemSyncState, syncCollection, key, newSyncKey);
		}
	}

	private DataDelta startWindowing(UserDataRequest udr, ItemSyncState syncState, AnalysedSyncCollection collection, WindowingKey key, SyncKey newSyncKey) {
		
		final Integer collectionId = collection.getCollectionId();
		WindowingContactChanges.Builder builder = WindowingContactChanges.builder();
		Date lastSync = getAllChanges(udr, syncState, collectionId, builder);
		
		WindowingContactChanges windowingContactChanges = builder.build();
		if (collection.getWindowSize() >= windowingContactChanges.sumOfChanges()) {
			return convertToDataDelta(windowingContactChanges, collectionId, lastSync, newSyncKey);
		} else {
			windowingDao.pushPendingChanges(key, newSyncKey, windowingContactChanges, PIMDataType.CONTACTS, collection.getWindowSize());
			return continueWindowing(collection, key, newSyncKey, lastSync);
		}
	}

	@VisibleForTesting DataDelta convertToDataDelta(WindowingContactChanges contactChanges, final Integer collectionId, Date syncDate, SyncKey newSyncKey) {
		return DataDelta.builder()
				.changes(FluentIterable.from(Iterables.concat(contactChanges.additions(),contactChanges.changes()))
						.transform(new Function<WindowingContact, ItemChange>() {
			
							@Override
							public ItemChange apply(WindowingContact windowingContact) {
								return ItemChange.builder()
										.serverId(mappingService.getServerIdFor(collectionId, String.valueOf(windowingContact.getUid())))
										.data(windowingContact.getMsContact())
										.build();
							}
						}).toList())
				.deletions(FluentIterable.from(contactChanges.deletions())
						.transform(new Function<WindowingContact, ItemDeletion>() {
			
							@Override
							public ItemDeletion apply(WindowingContact windowingContact) {
								return ItemDeletion.builder()
										.serverId(mappingService.getServerIdFor(collectionId, String.valueOf(windowingContact.getUid())))
										.build();
							}
						}).toList())
				.syncDate(syncDate)
				.syncKey(newSyncKey)
				.build();
	}

	private DataDelta continueWindowing(AnalysedSyncCollection collection, WindowingKey key, SyncKey syncKey, Date lastSync)
		throws DaoException {
		
		WindowingContactChanges pendingChanges = windowingDao.popNextChanges(key, collection.getWindowSize(), syncKey, WindowingContactChanges.builder()).build();
		return convertToDataDelta(pendingChanges, collection.getCollectionId(), lastSync, syncKey);
	}
	

	@VisibleForTesting Date getAllChanges(UserDataRequest udr, ItemSyncState state, Integer collectionId, WindowingContactChanges.Builder builder) {
		
		Integer addressBookId = findAddressBookIdFromCollectionId(udr, collectionId);
		ContactChanges contactChanges = listContactsChanged(udr, state, addressBookId);
		
		for (Contact contact : contactChanges.getUpdated()) {
			builder.change(WindowingContact.builder()
					.uid(contact.getUid())
					.msContact(contactConverter.convert(contact))
					.build());
		}
		
		for (Integer remove : contactChanges.getRemoved()) {
			builder.deletion(WindowingContact.builder()
					.uid(remove)
					.build());
		}
		
		return contactChanges.getLastSync();
	}
	
	private Integer findAddressBookIdFromCollectionId(UserDataRequest udr, Integer collectionId) 
			throws UnexpectedObmSyncServerException, DaoException, CollectionNotFoundException {
		
		List<AddressBook> addressBooks = listAddressBooks(udr);
		for (AddressBook addressBook: addressBooks) {
			String backendName = ContactCollectionPath.backendName(addressBook);
			String collectionPath = collectionPathBuilderProvider.get()
					.userDataRequest(udr)
					.pimType(getPIMDataType())
					.backendName(backendName)
					.build()
					.collectionPath();
			try {
				Integer addressBookCollectionId = mappingService.getCollectionIdFor(udr.getDevice(), collectionPath);
				if (addressBookCollectionId.intValue() == collectionId.intValue()) {
					return addressBook.getUid().getId();
				}
			} catch (CollectionNotFoundException e) {
				logger.warn(e.getMessage());
			}
		}
		throw new CollectionNotFoundException(collectionId);
	}
	
	private List<AddressBook> listAddressBooks(UserDataRequest udr) throws UnexpectedObmSyncServerException {
		AccessToken token = getAccessToken(udr);
		try {
			return getBookClient(udr).listAllBooks(token);
		} catch (ServerFault e) {
			throw new UnexpectedObmSyncServerException(e);
		}
	}

	private ContactChanges listContactsChanged(UserDataRequest udr, ItemSyncState state, Integer addressBookId) throws UnexpectedObmSyncServerException {
		AccessToken token = getAccessToken(udr);
		try {
			if (state.isInitial()) {
				return getBookClient(udr).firstListContactsChanged(token, state.getSyncDate(), addressBookId);
			}
			return getBookClient(udr).listContactsChanged(token, state.getSyncDate(), addressBookId);
		} catch (ServerFault e) {
			throw new UnexpectedObmSyncServerException(e);
		}
	}
	
	private ItemChange convertContactToItemChange(Integer collectionId, Contact contact) {
		return ItemChange.builder()
			.serverId( mappingService.getServerIdFor(collectionId, String.valueOf(contact.getUid())))
			.data(contactConverter.convert(contact))
			.build();
	}

	@Override
	public String createOrUpdate(UserDataRequest udr, Integer collectionId,
			String serverId, String clientId, IApplicationData data)
			throws CollectionNotFoundException, ProcessingEmailException,
			DaoException, UnexpectedObmSyncServerException,
			ItemNotFoundException, NoPermissionException {

		MSContact contact = (MSContact) data;
		Integer contactId = mappingService.getItemIdFromServerId(serverId);
		Integer addressBookId = findAddressBookIdFromCollectionId(udr, collectionId);
		try {

			if (serverId != null) {
				Contact convertedContact = contactConverter.contact(contact);
				convertedContact.setUid(contactId);
				modifyContact(udr, addressBookId, convertedContact);
			} else {
				Contact createdContact = createContact(udr, addressBookId, contactConverter.contact(contact), clientId);
				contactId = createdContact.getUid();
			}

		} catch (ContactNotFoundException e) {
			throw new ItemNotFoundException(e);
		}
		
		return mappingService.getServerIdFor(collectionId, String.valueOf(contactId));
	}

	private Contact modifyContact(UserDataRequest udr, Integer addressBookId, Contact contact) 
			throws UnexpectedObmSyncServerException, NoPermissionException, ContactNotFoundException {
		
		AccessToken token = getAccessToken(udr);
		try {
			return getBookClient(udr).modifyContact(token, addressBookId, contact);
		} catch (ServerFault e) {
			throw new UnexpectedObmSyncServerException(e);
		}
	}
	
	private Contact createContact(UserDataRequest udr, Integer addressBookId, Contact contact, String clientId) 
			throws UnexpectedObmSyncServerException, NoPermissionException {
		
		AccessToken token = getAccessToken(udr);
		try {
			return getBookClient(udr).createContact(token, addressBookId, contact, clientIdService.hash(udr, clientId));
		} catch (ServerFault e) {
			throw new UnexpectedObmSyncServerException(e);
		}
	}

	@Override
	public void delete(UserDataRequest udr, Integer collectionId, String serverId, Boolean moveToTrash)
			throws CollectionNotFoundException, DaoException,
			UnexpectedObmSyncServerException, ItemNotFoundException {
		
		Integer contactId = mappingService.getItemIdFromServerId(serverId);
		Integer addressBookId = findAddressBookIdFromCollectionId(udr, collectionId);
		try {
			removeContact(udr, addressBookId, contactId);
		} catch (NoPermissionException e) {
			logger.warn(e.getMessage());
		} catch (ContactNotFoundException e) {
			logger.warn(e.getMessage());
		}
	}

	private Contact removeContact(UserDataRequest udr, Integer addressBookId, Integer contactId) 
			throws UnexpectedObmSyncServerException, NoPermissionException, ContactNotFoundException {
		
		AccessToken token = getAccessToken(udr);
		try {
			return getBookClient(udr).removeContact(token, addressBookId, contactId);
		} catch (ServerFault e) {
			throw new UnexpectedObmSyncServerException(e);
		}
	}

	@Override
	public List<ItemChange> fetch(UserDataRequest udr, int collectionId, List<String> fetchServerIds, SyncCollectionOptions syncCollectionOptions,
				ItemSyncState previousItemSyncState)
			throws DaoException, UnexpectedObmSyncServerException, ConversionException {
	
		return fetch(udr, collectionId, fetchServerIds, syncCollectionOptions);
	}
	
	@Override
	public List<ItemChange> fetch(UserDataRequest udr, int collectionId, List<String> fetchServerIds, SyncCollectionOptions syncCollectionOptions)
			throws CollectionNotFoundException, DaoException, UnexpectedObmSyncServerException {
		
		List<ItemChange> ret = new LinkedList<ItemChange>();
		for (String serverId: fetchServerIds) {
			try {

				Integer contactId = mappingService.getItemIdFromServerId(serverId);
				Integer addressBookId = findAddressBookIdFromCollectionId(udr, collectionId);
				
				Contact contact = getContactFromId(udr, addressBookId, contactId);
				ret.add( convertContactToItemChange(collectionId, contact) );
				
			} catch (ContactNotFoundException e) {
				logger.error(e.getMessage());
			}
		}
		return ret;
	}

	private Contact getContactFromId(UserDataRequest udr, Integer addressBookId, Integer contactId) 
			throws UnexpectedObmSyncServerException, ContactNotFoundException {
		
		AccessToken token = getAccessToken(udr);
		try {
			return getBookClient(udr).getContactFromId(token, addressBookId, contactId);
		} catch (ServerFault e) {
			throw new UnexpectedObmSyncServerException(e);
		}
	}

	private IAddressBook getBookClient(UserDataRequest udr) {
		return bookClientFactory.create(ResourcesUtils.getHttpClient(udr));
	}
	
	@Override
	public String move(UserDataRequest udr, String srcFolder, String dstFolder,
			String messageId) throws CollectionNotFoundException,
			ProcessingEmailException {
		return null;
	}

	@Override
	public void emptyFolderContent(UserDataRequest udr, String collectionPath,
			boolean deleteSubFolder) throws NotAllowedException {
		throw new NotAllowedException(
				"emptyFolderContent is only supported for emails, collection was "
						+ collectionPath);
	}
	
	
}
