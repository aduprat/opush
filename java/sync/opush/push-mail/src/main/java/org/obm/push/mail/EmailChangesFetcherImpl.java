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
package org.obm.push.mail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.obm.push.bean.BodyPreference;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.item.MSEmailChanges;
import org.obm.push.bean.change.item.ItemChange;
import org.obm.push.bean.change.item.ItemChangeBuilder;
import org.obm.push.bean.change.item.ItemDeletion;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.EmailViewPartsFetcherException;
import org.obm.push.mail.bean.Email;
import org.obm.push.utils.index.IndexUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class EmailChangesFetcherImpl implements EmailChangesFetcher {

	private final MSEmailFetcher msEmailFetcher;

	@Inject
	@VisibleForTesting EmailChangesFetcherImpl(MSEmailFetcher msEmailFetcher) {
		this.msEmailFetcher = msEmailFetcher;
	}
	
	@Override
	public MSEmailChanges fetch(UserDataRequest udr,
			int collectionId, String collectionPath,
			List<BodyPreference> bodyPreferences, EmailChanges emailChanges) throws EmailViewPartsFetcherException, DaoException {
		Preconditions.checkNotNull(emailChanges, "emailChanges can not be null");
		return MSEmailChanges.builder()
				.deletions(emailDeletions(collectionId, emailChanges.deletions()))
				.changes(emailAdditions(udr, collectionId, collectionPath, bodyPreferences, emailChanges.additions()))
				.changes(emailChanges(udr, collectionId, collectionPath, bodyPreferences, emailChanges.changes()))
				.build();
	}

	private Set<ItemDeletion> emailDeletions(final int collectionId, Set<Email> deletions) {
		return FluentIterable
				.from(deletions)
				.transform(new Function<Email, ItemDeletion>() {

					@Override
					public ItemDeletion apply(Email email) {
						return ItemDeletion.builder()
								.serverId(ServerId.buildServerIdString(collectionId, email.getUid()))
								.build();
					}}
				
				).toImmutableSet();
	}

	private Set<ItemChange> emailAdditions(UserDataRequest udr,
			int collectionId, String collectionPath,
			List<BodyPreference> bodyPreferences, Set<Email> additions)
					throws EmailViewPartsFetcherException, DaoException {
		
		return emailToItemChange(udr, collectionId, collectionPath, bodyPreferences, additions, true);
	}

	private Set<ItemChange> emailChanges(UserDataRequest udr,
			final int collectionId, String collectionPath,
			List<BodyPreference> bodyPreferences, Set<Email> changes)
					throws EmailViewPartsFetcherException, DaoException {
		
		return emailToItemChange(udr, collectionId, collectionPath, bodyPreferences, changes, false);
	}
	
	private Set<ItemChange> emailToItemChange(UserDataRequest udr,
			final int collectionId, String collectionPath,
			List<BodyPreference> bodyPreferences, Set<Email> changes, final boolean withNewTag)
					throws EmailViewPartsFetcherException, DaoException {
		
		final Map<Long, MSEmail> uidToMSEmailMap = fetchMSEmails(udr, collectionId, collectionPath, bodyPreferences, changes); 
		return FluentIterable
				.from(changes)
				.transform(new Function<Email, ItemChange>() {

					@Override
					public ItemChange apply(Email email) {
						
						return new ItemChangeBuilder()
								.serverId(ServerId.buildServerIdString(collectionId, email.getUid()))
								.withApplicationData(uidToMSEmailMap.get(email.getUid()))
								.withNewFlag(withNewTag)
								.build();
					}}
				
				).toImmutableSet();
	}

	private Map<Long, MSEmail> fetchMSEmails(UserDataRequest udr,
			final int collectionId, String collectionPath,
			List<BodyPreference> bodyPreferences, Set<Email> changes)
					throws EmailViewPartsFetcherException, DaoException {
		
		if (changes.isEmpty()) {
			return ImmutableMap.<Long, MSEmail>of();
		}
		List<Long> uids = IndexUtils.listIndexes(changes);
		List<MSEmail> msEmails = msEmailFetcher.fetch(udr, collectionId, collectionPath, uids, bodyPreferences);
		return IndexUtils.mapByIndexes(msEmails);
	}
	
}