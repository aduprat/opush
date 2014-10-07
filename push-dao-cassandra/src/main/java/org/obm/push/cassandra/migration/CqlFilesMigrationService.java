/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2014 Linagora
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
package org.obm.push.cassandra.migration;

import org.obm.push.cassandra.dao.SchemaProducer;
import org.obm.push.cassandra.exception.InstallSchemaNotFoundException;
import org.obm.push.cassandra.migration.CassandraMigrationService.MigrationService;
import org.obm.push.cassandra.schema.SchemaInstaller;
import org.obm.push.cassandra.schema.Version;
import org.obm.push.configuration.LoggerModule;
import org.slf4j.Logger;

import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class CqlFilesMigrationService implements SchemaInstaller, MigrationService {

	private final Logger logger;
	private final SchemaProducer schemaProducer;
	private final Provider<Session> sessionProvider;

	@Inject
	@VisibleForTesting CqlFilesMigrationService(
			@Named(LoggerModule.MIGRATION) Logger logger,
			SchemaProducer schemaProducer,
			Provider<Session> sessionProvider) {
		this.logger = logger;
		this.schemaProducer = schemaProducer;
		this.sessionProvider = sessionProvider;
	}
	
	@Override
	public void install(Version latestVersionUpdate) {
		String schema = schemaProducer.schema(latestVersionUpdate);
		if (Strings.isNullOrEmpty(schema)) {
			throw new InstallSchemaNotFoundException();
		}
		
		executeCQL(schema);
	}

	@Override
	public void migrate(Version currentVersion, Version toVersion) {
		String schema = schemaProducer.schema(currentVersion, toVersion);
		if (Strings.isNullOrEmpty(schema)) {
			logger.info("No CQL migration found from version {} to {}", currentVersion.get(), toVersion.get());
		} else {
			logger.info("Executing CQL migration from version {} to {}", currentVersion.get(), toVersion.get());
			executeCQL(schema);
		}
	}

	private void executeCQL(String cql) {
		Session session = this.sessionProvider.get();
		logger.debug("CQL: {}", cql);
		for (String subQuery : subQueries(cql)) {
			session.execute(subQuery);
		}
	}

	@VisibleForTesting Iterable<String> subQueries(String schema) {
		Iterable<String> subQueries = Splitter.on(";").trimResults().split(schema);
		return Iterables.filter(subQueries, new Predicate<String>() {

			@Override
			public boolean apply(String query) {
				if (Strings.isNullOrEmpty(query) || System.lineSeparator().equals(query)) {
					return false;
				}
				return true;
			}
		});
	}
	
}
