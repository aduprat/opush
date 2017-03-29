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
package org.obm.push.cassandra.dao;

import static org.easymock.EasyMock.createMock;

import org.cassandraunit.CassandraCQLUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.obm.push.bean.migration.NoTableException;
import org.obm.push.bean.migration.Version;
import org.obm.push.cassandra.PublicCassandraService;
import org.obm.push.cassandra.TestCassandraConfiguration;
import org.obm.push.configuration.CassandraConfiguration;
import org.obm.push.store.SchemaDao;
import org.obm.sync.date.DateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraSchemaDaoNoTableTest {

	@Rule public CassandraCQLUnit cassandraCQLUnit = new OpushCassandraCQLUnit();
	
	private Logger logger = LoggerFactory.getLogger(CassandraSchemaDaoNoTableTest.class);
	
	private SchemaDao schemaDao;
	
	@Before
	public void init() {
		DateProvider dateProvider = createMock(DateProvider.class);

		CassandraConfiguration configuration = new TestCassandraConfiguration(OpushCassandraCQLUnit.KEYSPACE);
		SessionProvider sessionProvider = new SessionProvider(cassandraCQLUnit.session);
		schemaDao = new CassandraSchemaDao(sessionProvider, new PublicJSONService(), logger, 
				new PublicCassandraService(sessionProvider, configuration), dateProvider);
	}

	@Test(expected=NoTableException.class)
	public void getCurrentVersionWhenNoTable() {
		schemaDao.getCurrentVersion();
	}
	
	@Test(expected=NoTableException.class)
	public void getHistoryWhenNoTable() {
		schemaDao.getHistory();
	}
	
	@Test(expected=NoTableException.class)
	public void updateVersionWhenNoTable() {
		schemaDao.updateVersion(Version.of(5));
	}
}
