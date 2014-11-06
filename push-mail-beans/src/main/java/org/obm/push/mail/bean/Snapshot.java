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
package org.obm.push.mail.bean;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.obm.push.bean.FilterType;
import org.obm.push.bean.ServerId;
import org.obm.push.exception.activesync.InvalidServerId;
import org.obm.push.exception.activesync.ProtocolException;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class Snapshot implements Serializable {
	
	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		
		private FilterType filterType;
		private Long uidNext;
		private Collection<Email> emails;
		
		private Builder() {
			emails = Lists.newArrayList();
		}
		
		public Builder filterType(FilterType filterType) {
			this.filterType = filterType;
			return this;
		}
		
		public Builder uidNext(long uidNext) {
			this.uidNext = uidNext;
			return this;
		}
		
		public Builder emails(Collection<Email> emails) {
			this.emails = ImmutableList.copyOf(emails);
			return this;
		}
		
		public Builder addEmail(Email email) {
			emails.add(email);
			return this;
		}
		
		public Snapshot build() {
			Preconditions.checkState(filterType != null, "filterType can't be null or empty");
			return new Snapshot(
					filterType, 
					Optional.fromNullable(uidNext), 
					emails);
		}
	}
	
	private static final long serialVersionUID = -8674207692296869251L;
	
	private final FilterType filterType;
	private final Optional<Long> uidNext;
	private final Collection<Email> emails;
	private final MessageSet messageSet;
	
	protected Snapshot(FilterType filterType, Optional<Long> uidNext, Collection<Email> emails) {
		this.filterType = filterType;
		this.uidNext = uidNext;
		this.emails = emails;
		this.messageSet = generateMessageSet();
	}

	protected MessageSet generateMessageSet() {
		MessageSet.Builder builder = MessageSet.builder();
		for (Email email : emails) {
			builder.add(email.getUid());
		}
		return builder.build();
	}

	public boolean containsAllIds(List<ServerId> serverIds) throws InvalidServerId {
		Preconditions.checkNotNull(serverIds);
		for (ServerId serverId: serverIds) {
			Integer mailUid = serverId.getItemId();
			if (mailUid == null) {
				throw new ProtocolException(String.format("ServerId '%s' must reference an Item", serverId));
			}
			if (!messageSet.contains(mailUid)) {
				return false;
			}
		}
		return true;
	}

	public FilterType getFilterType() {
		return filterType;
	}

	public Optional<Long> getUidNext() {
		return uidNext;
	}

	public Collection<Email> getEmails() {
		return emails;
	}

	public MessageSet getMessageSet() {
		return messageSet;
	}
	
	@Override
	public final int hashCode(){
		return Objects.hashCode(filterType, uidNext, emails, messageSet);
	}
	
	@Override
	public final boolean equals(Object object){
		if (object instanceof Snapshot) {
			Snapshot that = (Snapshot) object;
			return  
				Objects.equal(this.filterType, that.filterType) && 
				Objects.equal(this.uidNext, that.uidNext) && 
				Objects.equal(this.emails, that.emails) &&  
				Objects.equal(this.messageSet, that.messageSet); 
		}
		return false;
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("filterType", filterType)
			.add("uidNext", uidNext)
			.add("emails", emails)
			.add("messageSet", messageSet)
			.toString();
	}
}
