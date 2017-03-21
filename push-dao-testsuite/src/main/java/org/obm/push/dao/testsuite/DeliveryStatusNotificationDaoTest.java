/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2013-2014  Linagora
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
package org.obm.push.dao.testsuite;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.guice.GuiceRunner;
import org.obm.push.bean.DeliveryStatusNotification;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.User;
import org.obm.push.store.DeliveryStatusNotificationDao;

@RunWith(GuiceRunner.class)
public abstract class DeliveryStatusNotificationDaoTest {

	protected DeliveryStatusNotificationDao testee;
	
	private User user;
	private ServerId serverId;

	@Before
	public void setUp() {
		user = User.builder().login("login").domain("domain").email("user@domain").build();
		serverId = ServerId.of("5:12");
	}
	
	@Test(expected=NullPointerException.class)
	public void insertStatusShouldThrowWhenNotificationIsNull() {
		testee.insertStatus(null);
	}

	@Test
	public void hasAlreadyBeenDeliveredShouldBeTrueWhenMatchingAndTrue() {
		DeliveryStatusNotification notification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.delivery(true)
				.build();
		testee.insertStatus(notification);
		
		assertThat(testee.hasAlreadyBeenDelivered(user, serverId)).isTrue();
	}

	@Test
	public void hasAlreadyBeenDeliveredShouldBeFalseWhenMatchingAndFalse() {
		DeliveryStatusNotification notification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.delivery(false)
				.build();
		testee.insertStatus(notification);
		
		assertThat(testee.hasAlreadyBeenDelivered(user, serverId)).isFalse();
	}

	@Test
	public void hasAlreadyBeenDeliveredShouldBeFalseWhenNotMatching() {
		assertThat(testee.hasAlreadyBeenDelivered(user, serverId)).isFalse();
	}

	@Test
	public void hasAlreadyBeenDeliveredShouldBeOverridenWhenMultipleInserts() {
		DeliveryStatusNotification notification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.delivery(true)
				.build();
		testee.insertStatus(notification);
		DeliveryStatusNotification secondNotification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.delivery(false)
				.build();
		testee.insertStatus(secondNotification);
		
		assertThat(testee.hasAlreadyBeenDelivered(user, serverId)).isFalse();
	}

	@Test
	public void hasAlreadyBeenReadShouldBeTrueWhenMatchingAndTrue() {
		DeliveryStatusNotification notification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.readReceipt(true)
				.build();
		testee.insertStatus(notification);
		
		assertThat(testee.hasAlreadyBeenRead(user, serverId)).isTrue();
	}

	@Test
	public void hasAlreadyBeenReadShouldBeFalseWhenMatchingAndFalse() {
		DeliveryStatusNotification notification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.readReceipt(false)
				.build();
		testee.insertStatus(notification);
		
		assertThat(testee.hasAlreadyBeenRead(user, serverId)).isFalse();
	}

	@Test
	public void hasAlreadyBeenReadShouldBeFalseWhenNotMatching() {
		assertThat(testee.hasAlreadyBeenRead(user, serverId)).isFalse();
	}

	@Test
	public void hasAlreadyBeenReadShouldBeOverridenWhenMultipleInserts() {
		DeliveryStatusNotification notification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.readReceipt(true)
				.build();
		testee.insertStatus(notification);
		DeliveryStatusNotification secondNotification = DeliveryStatusNotification.builder()
				.user(user)
				.serverId(serverId)
				.readReceipt(false)
				.build();
		testee.insertStatus(secondNotification);
		
		assertThat(testee.hasAlreadyBeenRead(user, serverId)).isFalse();
	}
}