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
package org.obm.push.store.ehcache;

import static org.easymock.EasyMock.createMock;

import java.io.IOException;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.obm.annotations.transactional.TransactionProvider;
import org.obm.push.configuration.OpushConfiguration;
import org.obm.push.dao.testsuite.WindowingDaoTest;
import org.obm.push.store.ehcache.WindowingDaoEhcacheImpl.PartitionDao;
import org.obm.transaction.TransactionManagerRule;
import org.slf4j.Logger;

public class WindowingDaoEhcacheImplInterfaceMethodsTest extends WindowingDaoTest {

	@Rule public TemporaryFolder tempFolder =  new TemporaryFolder();
	@Rule public TransactionManagerRule transactionManagerRule = new TransactionManagerRule();

	private ObjectStoreManager objectStoreManager;

	@Before
	public void init() throws IOException, NotSupportedException, SystemException {
		Logger logger = EasyMock.createNiceMock(Logger.class);
		TransactionProvider transactionProvider = EasyMock.createNiceMock(TransactionProvider.class);
		OpushConfiguration opushConfiguration = new EhCacheOpushConfiguration().mock(tempFolder);

		TestingEhCacheConfiguration config = new TestingEhCacheConfiguration();
		objectStoreManager = new ObjectStoreManager(opushConfiguration, config, logger, transactionProvider);
		CacheEvictionListener cacheEvictionListener = createMock(CacheEvictionListener.class);
		testee = new WindowingDaoEhcacheImpl(new PartitionDao(objectStoreManager, cacheEvictionListener));
		
		transactionManagerRule.getTransactionManager().begin();
	}
	
	@After
	public void cleanup() throws IllegalStateException, SecurityException, SystemException {
		transactionManagerRule.getTransactionManager().rollback();
		objectStoreManager.shutdown();
	}
	
	@Ignore("EHCache implementation does not support back in history")
	@Override
	public void hasPendingElementsWhenAskingWithPreviousSyncKey() {
		// not supported
	}
}
