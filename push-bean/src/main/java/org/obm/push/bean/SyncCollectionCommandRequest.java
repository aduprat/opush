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
package org.obm.push.bean;

import org.obm.push.bean.change.SyncCommand;

import com.google.common.base.Objects;

public class SyncCollectionCommandRequest implements TypedCommand {

	public static Builder builder() {
		return new Builder();
	}
	
	public static Builder from(SyncCollectionCommandRequest request) {
		return builder()
			.type(request.type)
			.serverId(request.serverId)
			.clientId(request.clientId)
			.applicationData(request.applicationData);
	}
	
	public static class Builder {
		
		private SyncCommand type;
		private ServerId serverId;
		private String clientId;
		private IApplicationData applicationData;

		protected Builder() {}
		
		public Builder type(SyncCommand commandtype) {
			this.type = commandtype;
			return this;
		}
		
		public Builder serverId(ServerId serverId) {
			this.serverId = serverId;
			return this;
		}

		public Builder clientId(String clientId) {
			this.clientId = clientId;
			return this;
		}

		public Builder applicationData(IApplicationData applicationData) {
			this.applicationData = applicationData;
			return this;
		}

		public SyncCollectionCommandRequest build() {
			return new SyncCollectionCommandRequest(type, serverId, clientId, applicationData);
		}
	}
	
	private final SyncCommand type;
	private final ServerId serverId;
	private final String clientId;
	private final IApplicationData applicationData;
	
	private SyncCollectionCommandRequest(SyncCommand type, ServerId serverId, String clientId, IApplicationData applicationData) {
		this.type = type;
		this.serverId = serverId;
		this.clientId = clientId;
		this.applicationData = applicationData;
	}

	@Override
	public SyncCommand getType() {
		return type;
	}

	@Override
	public ServerId getServerId() {
		return serverId;
	}

	public String getClientId() {
		return clientId;
	}

	public IApplicationData getApplicationData() {
		return applicationData;
	}
	
	@Override
	public final int hashCode(){
		return Objects.hashCode(type, serverId, clientId, applicationData);
	}
	
	@Override
	public final boolean equals(Object object){
		if (object instanceof SyncCollectionCommandRequest) {
			SyncCollectionCommandRequest that = (SyncCollectionCommandRequest) object;
			return Objects.equal(this.type, that.type)
				&& Objects.equal(this.serverId, that.serverId)
				&& Objects.equal(this.clientId, that.clientId)
				&& Objects.equal(this.applicationData, that.applicationData);
		}
		return false;
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("name", type)
			.add("serverId", serverId)
			.add("clientId", clientId)
			.add("applicationData", applicationData)
 			.toString();
	}
}
