/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2017  Linagora
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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class DeliveryStatusNotification {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		
		private User user;
		private ServerId serverId;
		private boolean delivery;
		private boolean readReceipt;

		private Builder() {
		}

		public Builder user(User user) {
			this.user = user;
			return this;
		}

		public Builder serverId(ServerId serverId) {
			this.serverId = serverId;
			return this;
		}

		public Builder delivery(boolean delivery) {
			this.delivery = delivery;
			return this;
		}

		public Builder readReceipt(boolean readReceipt) {
			this.readReceipt = readReceipt;
			return this;
		}

		public DeliveryStatusNotification build() {
			Preconditions.checkNotNull(user, "'user' is mandatory");
			Preconditions.checkNotNull(serverId, "'serverId' is mandatory");
			return new DeliveryStatusNotification(user, serverId, delivery, readReceipt);
		}
	}

	private final User user;
	private final ServerId serverId;
	private final boolean delivery;
	private final boolean readReceipt;
	
	private DeliveryStatusNotification(User user, ServerId serverId, boolean delivery, boolean readReceipt) {
		this.user = user;
		this.serverId = serverId;
		this.delivery = delivery;
		this.readReceipt = readReceipt;
	}

	public User getUser() {
		return user;
	}

	public ServerId getServerId() {
		return serverId;
	}

	public boolean isDelivery() {
		return delivery;
	}

	public boolean isReadReceipt() {
		return readReceipt;
	}

	@Override
	public final int hashCode(){
		return Objects.hashCode(user, serverId, delivery, readReceipt);
	}
	
	@Override
	public final boolean equals(Object object){
		if (object instanceof DeliveryStatusNotification) {
			DeliveryStatusNotification that = (DeliveryStatusNotification) object;
			return Objects.equal(this.user, that.user)
				&& Objects.equal(this.serverId, that.serverId)
				&& Objects.equal(this.delivery, that.delivery)
				&& Objects.equal(this.readReceipt, that.readReceipt);
		}
		return false;
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("user", user)
			.add("serverId", serverId)
			.add("delivery", delivery)
			.add("readReceipt", readReceipt)
			.toString();
	}
}
