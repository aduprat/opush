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
package org.obm.push.cassandra;

import org.cassandraunit.CassandraCQLUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.obm.push.bean.migration.NoTableException;
import org.obm.push.cassandra.dao.CassandraSchemaDao;
import org.obm.push.cassandra.dao.CassandraStructure;
import org.obm.push.cassandra.dao.DaoTestsSchemaProducer;
import org.obm.push.cassandra.dao.OpushCassandraCQLUnit;
import org.obm.push.cassandra.dao.SessionProvider;
import org.obm.push.configuration.CassandraConfiguration;

public class CassandraServiceTest {

	private static final String DAO_SCHEMA = new DaoTestsSchemaProducer().schemaForDAO(CassandraSchemaDao.class);
	@Rule public CassandraCQLUnit cassandraCQLUnit = new OpushCassandraCQLUnit(DAO_SCHEMA);
	
	private CassandraService testee;
	
	@Before
	public void init() {
		SessionProvider sessionProvider = new SessionProvider(cassandraCQLUnit.session);
		CassandraConfiguration configuration = new TestCassandraConfiguration(OpushCassandraCQLUnit.KEYSPACE);
		testee = new CassandraService(sessionProvider, configuration);
	}

	@Test
	public void testErrorIfNoTableWhenTable() {
		testee.errorIfNoTable(CassandraStructure.Schema.TABLE.get());
	}
	
	@Test(expected=NoTableException.class)
	public void testErrorIfNoTableWhenNoTable() {
		testee.errorIfNoTable("notExistingTable");
	}
}
