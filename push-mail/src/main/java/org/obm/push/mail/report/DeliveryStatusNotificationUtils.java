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

import java.io.UnsupportedEncodingException;

import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.field.address.DefaultAddressParser;
import org.apache.james.mime4j.field.address.ParseException;
import org.apache.james.mime4j.message.BodyPart;
import org.obm.push.bean.MSAddress;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.mail.Mime4jUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;

public class DeliveryStatusNotificationUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryStatusNotificationUtils.class);
	private static final String LINE_BREAK = "\r\n";

	private final MSEmail originalMessage;

	public DeliveryStatusNotificationUtils(MSEmail originalMessage) {
		this.originalMessage = originalMessage;
	}

	public Optional<Address> to() {
		return FluentIterable.from(originalMessage.getFrom())
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

	public BodyPart originalMessage() throws UnsupportedEncodingException {
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
		
		Mime4jUtils mime4jUtils = new Mime4jUtils();
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
}
