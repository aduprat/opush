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
package org.obm.push.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.obm.push.configuration.DeliveryStatusNotificationTemplatesImpl;
import org.obm.push.exception.TemplateNotFoundException;

import com.google.common.collect.ImmutableList;

public class TemplateServiceImplTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();
	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Before
	public void setup() throws Exception {
		copy(DeliveryStatusNotificationTemplatesImpl.DELIVERY_RECEIPT_FILE_NAME);
		copy(DeliveryStatusNotificationTemplatesImpl.READ_RECEIPT_FILE_NAME);
	}
	
	private void copy(String deliveryReceiptFileName) throws IOException {
		File file = temporaryFolder.newFile(deliveryReceiptFileName);
		IOUtils.copy(ClassLoader.getSystemResourceAsStream(deliveryReceiptFileName), new FileOutputStream(file));
	}

	@Test
	public void compileDeliveryReceiptTextBodyShouldReturnText() throws Exception {
		TemplateServiceImpl testee = new TemplateServiceImpl(new DeliveryStatusNotificationTemplatesImpl(temporaryFolder.getRoot()));
		
		DeliveryReceiptData deliveryReceiptData = new DeliveryReceiptData("myHost", 
				ImmutableList.of(new DeliveryReceiptData.Recipient("<user1@opush.org>"),
						new DeliveryReceiptData.Recipient("<user2@opush.org>")));
		
		String message = testee.compileDeliveryReceiptTextBody(deliveryReceiptData);
		
		assertThat(message).isEqualTo("This is the mail system at host myHost.\n" +
				"\n" +
				"Your message was successfully delivered to the destination(s)\n" +
				"listed below. If the message was delivered to mailbox you will\n" +
				"receive no further notifications. Otherwise you may still receive\n" +
				"notifications of mail delivery errors from other systems.\n" +
				"\n" +
				"<user1@opush.org>:delivery Ok\n" +
				"<user2@opush.org>:delivery Ok\n");
	}

	@Test
	public void compileDeliveryReceiptTextBodyShouldThrowWhenTemplateNotFound() throws Exception {
		expectedException.expect(TemplateNotFoundException.class);
		TemplateServiceImpl testee = new TemplateServiceImpl(new EmptyDeliveryStatusNotificationTemplates());
		
		DeliveryReceiptData deliveryReceiptData = new DeliveryReceiptData("myHost", 
				ImmutableList.of(new DeliveryReceiptData.Recipient("<user1@opush.org>"),
						new DeliveryReceiptData.Recipient("<user2@opush.org>")));
		
		testee.compileDeliveryReceiptTextBody(deliveryReceiptData);
	}

	@Test
	public void compileReadReceiptTextBodyShouldReturnText() throws Exception {
		TemplateServiceImpl testee = new TemplateServiceImpl(new DeliveryStatusNotificationTemplatesImpl(temporaryFolder.getRoot()));
		
		ReadReceiptData readReceiptData = new ReadReceiptData("<user1@opush.org>");
		
		String message = testee.compileReadReceiptTextBody(readReceiptData);
		
		assertThat(message).isEqualTo("This is a Read Receipt for the mail that you sent to <user1@opush.org>.\n");
	}

	@Test
	public void compileReadReceiptTextBodyShouldThrowWhenTemplateNotFound() throws Exception {
		expectedException.expect(TemplateNotFoundException.class);
		TemplateServiceImpl testee = new TemplateServiceImpl(new EmptyDeliveryStatusNotificationTemplates());
		
		ReadReceiptData readReceiptData = new ReadReceiptData("<user1@opush.org>");
		
		testee.compileReadReceiptTextBody(readReceiptData);
	}
}
