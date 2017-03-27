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

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.obm.push.bean.User;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.configuration.DeliveryStatusNotification;
import org.obm.push.exception.TemplateNotFoundException;
import org.obm.push.mail.Mime4jUtils;
import org.obm.push.template.ReadReceiptData;
import org.obm.push.template.TemplateService;
import org.obm.push.utils.MimeContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ReadReceiptMessage {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReadReceiptMessage.class);

	private final TemplateService templateService;
	private final DeliveryStatusNotification deliveryStatusNotification;
	private final Mime4jUtils mime4jUtils;
	private final MimeBoundaryFactory mimeBoundaryFactory;

	@Inject
	@VisibleForTesting ReadReceiptMessage(TemplateService templateService, 
			DeliveryStatusNotification deliveryStatusNotification,
			Mime4jUtils mime4jUtils,
			MimeBoundaryFactory mimeBoundaryFactory) {
		
		this.templateService = templateService;
		this.deliveryStatusNotification = deliveryStatusNotification;
		this.mime4jUtils = mime4jUtils;
		this.mimeBoundaryFactory = mimeBoundaryFactory;
	}
	
	public Optional<Message> from(User from, MSEmail originalMessage) {
		if (deliveryStatusNotification.shouldSendReadReceipt()) {
			Preconditions.checkNotNull(originalMessage, "'originalMessage' is mandatory");
			Optional<String> textBody = textBody(from.getEmail());
			DeliveryStatusNotificationUtils deliveryStatusNotificationUtils = new DeliveryStatusNotificationUtils(originalMessage);
			Optional<Address> to = deliveryStatusNotificationUtils.to();
			if (textBody.isPresent()) {
				try {
					Multipart report = mime4jUtils.createMultipartReport();
					report.addBodyPart(readReport(textBody.get()));
					report.addBodyPart(deliveryStatusNotificationUtils.originalMessage());
					
					Message newMessage = mime4jUtils.createMessage();
					newMessage.setBody(report);
					Header header = new DefaultMessageBuilder().newHeader();
					header.setField(Fields.to(to.get()));
					header.setField(Fields.subject("Read Receipt (displayed)"));
					header.setField(Fields.contentType("multipart/report", 
							ImmutableMap.of("report-type", "delivery-notification",
									ContentTypeField.PARAM_BOUNDARY, mimeBoundaryFactory.random())));
					newMessage.setHeader(header);
					
					return Optional.of(newMessage);
				} catch (UnsupportedEncodingException e) {
				}
			}
		}
		return Optional.absent();
	}

	private BodyPart readReport(String textBody) throws UnsupportedEncodingException {
		return mime4jUtils.createTextPart(textBody, MimeContentType.TEXT_PLAIN.getSubType());
	}

	private Optional<String> textBody(String address) {
		try {
			return Optional.fromNullable(templateService.compileReadReceiptTextBody(new ReadReceiptData(address)));
		} catch (TemplateNotFoundException | IOException e) {
			LOGGER.error("Fail while creating read receipt", e);
			return Optional.absent();
		}
	}
}
