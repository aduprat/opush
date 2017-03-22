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
package org.obm.push.mail.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.guava.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.obm.push.bean.MSAddress;
import org.obm.push.bean.MSAttachement;
import org.obm.push.bean.MSEmailHeader;
import org.obm.push.bean.User;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.bean.ms.MSEmailBody;
import org.obm.push.mail.Mime4jUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class DeliveryReceiptMessageTest {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	private User user;

	@Before
	public void setup() {
		user = User.builder()
			.displayName("User Display")
			.email("user@obm.org")
			.domain("obm.org")
			.build();
	}
	
	@Test
	public void fromShouldReturnAbsentWhenDisabledInConfiguration() {
		DeliveryReceiptMessage testee = new DeliveryReceiptMessage(new MyTemplateService(), 
				new DisabledDeliveryStatusNotification(),
				new MyOpushConfiguration(),
				new Mime4jUtils(),
				new FixedMimeBoundaryFactory());

		assertThat(testee.from(user, null)).isAbsent();
	}

	@Test
	public void fromShouldThrowWhenOriginalMessageIsNull() {
		expectedException.expect(NullPointerException.class);
		DeliveryReceiptMessage testee = new DeliveryReceiptMessage(new MyTemplateService(), 
				new EnabledDeliveryStatusNotification(),
				new MyOpushConfiguration(),
				new Mime4jUtils(),
				new FixedMimeBoundaryFactory());

		testee.from(user, null);
	}

	@Test
	public void fromShouldWork() throws IOException {
		DeliveryReceiptMessage testee = new DeliveryReceiptMessage(new MyTemplateService(), 
				new EnabledDeliveryStatusNotification(),
				new MyOpushConfiguration(),
				new Mime4jUtils(),
				new FixedMimeBoundaryFactory());
		
		Optional<Message> deliveryReceipt = testee.from(user, MSEmail.builder()
				.header(MSEmailHeader.builder()
						.from(new MSAddress("user@opush.org"))
						.to(ImmutableList.of(new MSAddress("To1", "to1@opush.org"), new MSAddress("To2", "to2@opush.org"), new MSAddress("To3", "to3@opush.org")))
						.cc(ImmutableList.of(new MSAddress("Cc1", "cc1@opush.org"), new MSAddress("cc2@opush.org")))
						.build())
				.body(MSEmailBody.builder()
						.build())
				.attachements(ImmutableSet.<MSAttachement> of())
				.subject("my subject")
				.build());
		
		String expectedMessage = "To: user@opush.org\r\n" + 
			"Subject: Successful Mail Delivery Report\r\n" +
			"Content-Type: multipart/report; report-type=delivery-status;\r\n" +
			" boundary=\"" + FixedMimeBoundaryFactory.FIXED_BOUNDARY + "\"\r\n" +
			"\r\n" +
			"--" + FixedMimeBoundaryFactory.FIXED_BOUNDARY + "\r\n" +
			"Content-Description: Notification\r\n" +
			"Content-Type: text/plain\r\n" +
			"\r\n" +
			"delivery receipt body\r\n" +
			"--" + FixedMimeBoundaryFactory.FIXED_BOUNDARY + "\r\n" +
			"Content-Type: message/delivery-status\r\n" +
			"\r\n" +
			"Reporting-MTA: dns; mydomain.org\r\n" +
			"\r\n" +
			"Original-Recipient: rfc822;user@obm.org\r\n" +
			"Final-Recipient: rfc822;user@obm.org\r\n" +
			"Action: delivered\r\n" +
			"Status: 2.1.5\r\n" +
			"\r\n" +
			"\r\n" +
			"--" + FixedMimeBoundaryFactory.FIXED_BOUNDARY + "\r\n" +
			"Content-Type: text/rfc822-headers\r\n" +
			"\r\n" +
			"To: To1 <to1@opush.org>\r\n" +
			"To: To2 <to2@opush.org>\r\n" +
			"To: To3 <to3@opush.org>\r\n" +
			"From:  <user@opush.org>\r\n" +
			"Subject: my subject\r\n" +
			"\r\n" +
			"--" + FixedMimeBoundaryFactory.FIXED_BOUNDARY + "--\r\n";

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		new DefaultMessageWriter().writeMessage(deliveryReceipt.get(), outputStream);
		String mimeMessageAsString = new String(outputStream.toByteArray());
		assertThat(mimeMessageAsString).isEqualTo(expectedMessage);
	}
}
