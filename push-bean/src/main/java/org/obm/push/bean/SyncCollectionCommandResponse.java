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
package org.obm.push.bean;

import org.obm.push.bean.change.SyncCommand;

import com.google.common.base.Objects;

public class SyncCollectionCommandResponse extends SyncCollectionCommand {

	private static final long serialVersionUID = -246587854210988404L;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends SyncCollectionCommand.Builder<SyncCollectionCommandResponse> {
		private IApplicationData applicationData;
		private SyncStatus syncStatus;
		
		private Builder() {
			super();
		}
		
		@Override
		protected Builder applicationDataImpl(Object applicationData) {
			this.applicationData = (IApplicationData) applicationData;
			return this;
		}
		
		public Builder syncStatus(SyncStatus syncStatus) {
			this.syncStatus = syncStatus;
			return this;
		}
		
		@Override
		public SyncCollectionCommandResponse build() {
			return new SyncCollectionCommandResponse(type, serverId, clientId, applicationData, syncStatus);
		}
	}
	
	private final IApplicationData applicationData;
	private final SyncStatus syncStatus;
	
	private SyncCollectionCommandResponse(SyncCommand commandType, String serverId, String clientId, IApplicationData applicationData, SyncStatus syncStatus) {
		super(commandType, serverId, clientId);
		this.applicationData = applicationData;
		this.syncStatus = syncStatus;
	}
	
	public IApplicationData getApplicationData() {
		return applicationData;
	}
	
	public SyncStatus getSyncStatus() {
		return syncStatus;
	}

	@Override
	public int hashCode(){
		return Objects.hashCode(super.hashCode(), applicationData, syncStatus);
	}
	
	@Override
	public boolean equals(Object object){
		if (object instanceof SyncCollectionCommandResponse) {
			SyncCollectionCommandResponse that = (SyncCollectionCommandResponse) object;
			return super.equals(that)
				&& Objects.equal(this.syncStatus, that.syncStatus)
				&& Objects.equal(this.applicationData, that.applicationData);
		}
		return false;
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("super", super.toString())
			.add("syncStatus", syncStatus)
			.add("applicationData", applicationData)
 			.toString();
	}
}
