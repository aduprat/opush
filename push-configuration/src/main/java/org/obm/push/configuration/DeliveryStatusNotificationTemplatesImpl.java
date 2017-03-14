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
package org.obm.push.configuration;

import java.io.File;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class DeliveryStatusNotificationTemplatesImpl implements DeliveryStatusNotificationTemplates {

	private static final String MUSTACHE_EXTENSION = ".mustache";
	public static final String DELIVERY_RECEIPT_FILE_NAME = "delivery_receipt" + MUSTACHE_EXTENSION;
	public static final String READ_RECEIPT_FILE_NAME = "read_receipt" + MUSTACHE_EXTENSION;
	
	private final Optional<Mustache> deliveryReceipt;
	private final Optional<Mustache> readReceipt;
	
	@Inject
	@VisibleForTesting public DeliveryStatusNotificationTemplatesImpl(@Named("mustacheTemplatesRoot") File mustacheTemplatesRoot) {
		deliveryReceipt = loadTemplate(mustacheTemplatesRoot, DELIVERY_RECEIPT_FILE_NAME);
		readReceipt = loadTemplate(mustacheTemplatesRoot, READ_RECEIPT_FILE_NAME);
	}

	private Optional<Mustache> loadTemplate(File mustacheTemplatesRoot, String templateName) {
		try {
			DefaultMustacheFactory mustacheFactory = new DefaultMustacheFactory(mustacheTemplatesRoot);
			return Optional.of(mustacheFactory.compile(templateName));
		} catch (MustacheException e) {
			return Optional.absent();
		}
	}

	@Override
	public Optional<Mustache> deliveryReceipt() {
		return deliveryReceipt;
	}

	@Override
	public Optional<Mustache> readReceipt() {
		return readReceipt;
	}
}
