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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DeliveryStatusNotificationTest {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	@Test
	public void buildShouldThrowWhenUserIsNull() {
		expectedException.expect(NullPointerException.class);
		expectedException.expectMessage("'user' is mandatory");
		
		DeliveryStatusNotification.builder()
			.build();
	}
	
	@Test
	public void buildShouldThrowWhenServerIdIsNull() {
		expectedException.expect(NullPointerException.class);
		expectedException.expectMessage("'serverId' is mandatory");
		
		DeliveryStatusNotification.builder()
			.user(User.Factory.create().createUser("user1@opush", "user1@opush.org", "User 1"))
			.build();
	}
	
	@Test
	public void buildShouldSetAllFields() {
		User user = User.Factory.create().createUser("user1@opush", "user1@opush.org", "User 1");
		ServerId serverId = ServerId.of("1:2");
		boolean delivery = true;
		boolean readReceipt = true;
		DeliveryStatusNotification notification = DeliveryStatusNotification.builder()
			.user(user)
			.serverId(serverId)
			.delivery(delivery)
			.readReceipt(readReceipt)
			.build();
		
		assertThat(notification.getUser()).isEqualTo(user);
		assertThat(notification.getServerId()).isEqualTo(serverId);
		assertThat(notification.isDelivery()).isEqualTo(delivery);
		assertThat(notification.isReadReceipt()).isEqualTo(readReceipt);
		
	}
}
