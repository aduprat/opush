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
package org.obm.push.protocol.data.ms;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.obm.push.bean.MSAddress;
import org.obm.push.bean.MSEmailBodyType;
import org.obm.push.bean.MSEmailHeader;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.bean.ms.MSEmailBody;
import org.obm.push.bean.ms.MSEmailBody.Builder;
import org.obm.push.exception.ConversionException;
import org.obm.push.protocol.data.ASAirs;
import org.obm.push.protocol.data.ASEmail;
import org.obm.push.protocol.data.ActiveSyncDecoder;
import org.obm.push.protocol.data.IDataDecoder;
import org.obm.push.protocol.data.MSEmailEncoder;
import org.obm.push.utils.SerializableInputStream;
import org.w3c.dom.Element;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

public class MSEmailDecoder extends ActiveSyncDecoder implements IDataDecoder {

	private final SimpleDateFormat utcDateFormat;
	
	@Inject
	protected MSEmailDecoder() {
		utcDateFormat = new SimpleDateFormat(MSEmailEncoder.UTC_DATE_PATTERN);
		utcDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	@Override
	public MSEmail decode(Element data) throws ConversionException {
		try {
			return MSEmail.builder()
				.header(MSEmailHeader.builder()
						.from(addresses(uniqueStringFieldValue(data, ASEmail.FROM)))
						.to(addresses(uniqueStringFieldValue(data, ASEmail.TO)))
						.cc(addresses(uniqueStringFieldValue(data, ASEmail.CC)))
						.replyTo(addresses(uniqueStringFieldValue(data, ASEmail.REPLY_TO)))
						.date(date(uniqueStringFieldValue(data, ASEmail.DATE_RECEIVED)))
						.build())
				.body(msEmailBody(data))
				.build();
		} catch (AddressException e) {
			throw new ConversionException("An address field is not valid", e);
		} catch (ParseException e) {
			throw new ConversionException("A date field is not valid", e);
		}
	}

	@VisibleForTesting MSEmailBody msEmailBody(Element data) {
		Builder bodyBuilder = MSEmailBody.builder()
				.bodyType(MSEmailBodyType.getValueOf(uniqueIntegerFieldValue(data, ASAirs.TYPE)));
		
		Integer estimatedDataSize = uniqueIntegerFieldValue(data, ASAirs.ESTIMATED_DATA_SIZE);
		if (estimatedDataSize != null) {
			bodyBuilder.estimatedDataSize(estimatedDataSize);
		}
		
		Boolean truncated = uniqueBooleanFieldValue(data, ASAirs.TRUNCATED);
		if (truncated != null) {
			bodyBuilder.truncated(truncated);
		}
		
		String mimeData = uniqueStringFieldValue(data, ASAirs.DATA);
		if (mimeData != null) {
			bodyBuilder.mimeData(new SerializableInputStream(mimeData));
		}
		
		return bodyBuilder.build();
	}

	@VisibleForTesting Date date(String dateAsString) throws ParseException {
		if (!Strings.isNullOrEmpty(dateAsString)) {
			return utcDateFormat.parse(dateAsString);
		} 
		return null;
	}

	@VisibleForTesting List<MSAddress> addresses(String addressesAsString) throws AddressException {
		if (!Strings.isNullOrEmpty(addressesAsString)) {
			List<MSAddress> addresses = Lists.newArrayList();
			for (InternetAddress address : InternetAddress.parse(addressesAsString)) {
				if (Strings.isNullOrEmpty(address.getAddress()) || !address.getAddress().contains("@")) {
					throw new AddressException("No email address found in : " + address.toUnicodeString());
				}
				addresses.add(new MSAddress(address.getPersonal(), address.getAddress()));
			}
			return addresses;
		}
		return ImmutableList.of();
	}
}
