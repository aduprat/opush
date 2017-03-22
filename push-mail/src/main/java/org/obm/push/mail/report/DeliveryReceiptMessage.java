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
import java.util.List;

import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.field.address.DefaultAddressParser;
import org.apache.james.mime4j.field.address.ParseException;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.RawField;
import org.obm.push.bean.MSAddress;
import org.obm.push.bean.User;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.configuration.DeliveryStatusNotification;
import org.obm.push.configuration.OpushConfiguration;
import org.obm.push.exception.TemplateNotFoundException;
import org.obm.push.mail.Mime4jUtils;
import org.obm.push.template.DeliveryReceiptData;
import org.obm.push.template.DeliveryReceiptData.Recipient;
import org.obm.push.template.TemplateService;
import org.obm.push.utils.MimeContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DeliveryReceiptMessage {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryReceiptMessage.class);
	private static final String LINE_BREAK = "\r\n";

	private final TemplateService templateService;
	private final DeliveryStatusNotification deliveryStatusNotification;
	private final OpushConfiguration opushConfiguration;
	private final Mime4jUtils mime4jUtils;
	private final MimeBoundaryFactory mimeBoundaryFactory;

	@Inject
	@VisibleForTesting DeliveryReceiptMessage(TemplateService templateService, 
			DeliveryStatusNotification deliveryStatusNotification,
			OpushConfiguration opushConfiguration,
			Mime4jUtils mime4jUtils,
			MimeBoundaryFactory mimeBoundaryFactory) {
		
		this.templateService = templateService;
		this.deliveryStatusNotification = deliveryStatusNotification;
		this.opushConfiguration = opushConfiguration;
		this.mime4jUtils = mime4jUtils;
		this.mimeBoundaryFactory = mimeBoundaryFactory;
	}
	
	public Optional<Message> from(User from, MSEmail originalMessage) {
		if (deliveryStatusNotification.shouldSendDeliveryReceipt()) {
			Preconditions.checkNotNull(originalMessage, "'originalMessage' is mandatory");
			Recipient recipient = recipient(from);
			Optional<String> textBody = textBody(recipient);
			Optional<Address> to = to(originalMessage.getFrom());
			if (textBody.isPresent() && to.isPresent()) {
				try {
					Multipart report = mime4jUtils.createMultipartReport();
					report.addBodyPart(notification(textBody.get()));
					report.addBodyPart(deliveryReport(recipient));
					report.addBodyPart(originalMessage(originalMessage));
					
					Message newMessage = mime4jUtils.createMessage();
					newMessage.setBody(report);
					Header header = new DefaultMessageBuilder().newHeader();
					header.setField(Fields.to(to.get()));
					header.setField(Fields.subject("Successful Mail Delivery Report"));
					header.setField(Fields.contentType("multipart/report", 
							ImmutableMap.of("report-type", "delivery-status",
									ContentTypeField.PARAM_BOUNDARY, mimeBoundaryFactory.random())));
					newMessage.setHeader(header);
					
					return Optional.of(newMessage);
				} catch (UnsupportedEncodingException e) {
				}
			}
		}
		return Optional.absent();
	}

	private BodyPart notification(String textBody) throws UnsupportedEncodingException {
		BodyPart bodyPart = mime4jUtils.createTextPart(textBody, MimeContentType.TEXT_PLAIN.getSubType());
		Header header = new DefaultMessageBuilder().newHeader();
		header.setField(new RawField("Content-Description", "Notification"));
		header.setField(Fields.contentType(MimeContentType.TEXT_PLAIN.getContentType()));
		bodyPart.setHeader(header);
		return bodyPart;
	}

	private Optional<String> textBody(Recipient recipient) {
		try {
			return Optional.fromNullable(templateService.compileDeliveryReceiptTextBody(new DeliveryReceiptData(opushConfiguration.getGlobalDomain(), ImmutableList.of(recipient))));
		} catch (TemplateNotFoundException | IOException e) {
			LOGGER.error("Fail while creating delivery receipt message", e);
			return Optional.absent();
		}
	}

	private Recipient recipient(User from) {
		return new Recipient(from.getEmail());
	}

	private BodyPart deliveryReport(Recipient recipient) throws UnsupportedEncodingException {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Reporting-MTA: dns; ").append(opushConfiguration.getGlobalDomain());
		buffer.append(LINE_BREAK).append(LINE_BREAK);
	
		buffer.append("Original-Recipient: rfc822;").append(recipient.address).append(LINE_BREAK);
		buffer.append("Final-Recipient: rfc822;").append(recipient.address).append(LINE_BREAK);
		buffer.append("Action: delivered").append(LINE_BREAK);
		buffer.append("Status: 2.1.5").append(LINE_BREAK);
		buffer.append(LINE_BREAK);
		
		TextBody body = mime4jUtils.createBody(buffer.toString());
		return mime4jUtils.bodyToBodyPart(body, "message/delivery-status");
	}

	private BodyPart originalMessage(MSEmail originalMessage) throws UnsupportedEncodingException {
		StringBuffer buffer = new StringBuffer();
		for (MSAddress msAddress : originalMessage.getHeader().getTo()) {
			buffer.append("To: ");
			append(buffer, msAddress);
		}
		for (MSAddress msAddress : originalMessage.getHeader().getFrom()) {
			buffer.append("From: ");
			append(buffer, msAddress);
		}
		buffer.append("Subject: ").append(originalMessage.getSubject()).append(LINE_BREAK);
		
		TextBody body = mime4jUtils.createBody(buffer.toString());
		return mime4jUtils.bodyToBodyPart(body, "text/rfc822-headers");
	}

	private void append(StringBuffer buffer, MSAddress msAddress) {
		String displayName = msAddress.getDisplayName();
		if (!Strings.isNullOrEmpty(displayName)) {
			buffer.append(displayName);
		}
		buffer.append(" <").append(msAddress.getMail()).append(">").append(LINE_BREAK);
	}

	private Optional<Address> to(List<MSAddress> from) {
		return FluentIterable.from(from)
			.first()
			.transform(new Function<MSAddress, Address>() {

				@Override
				public Address apply(MSAddress msAddress) {
					try {
						return DefaultAddressParser.DEFAULT.parseAddress(msAddress.getMail());
					} catch (ParseException e) {
						LOGGER.error("Error while parsing address: " + msAddress.getMail(), e);
						return null;
					}
				}
			});
	}
}
