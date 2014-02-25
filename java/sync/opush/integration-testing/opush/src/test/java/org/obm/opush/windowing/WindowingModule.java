/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2014  Linagora
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
package org.obm.opush.windowing;

import java.util.concurrent.TimeUnit;

import org.obm.StaticConfigurationService;
import org.obm.annotations.transactional.LazyTransactionProvider;
import org.obm.annotations.transactional.TransactionProvider;
import org.obm.configuration.TransactionConfiguration;
import org.obm.configuration.module.LoggerModule;
import org.obm.opush.env.OpushConfigurationFixture;
import org.obm.opush.env.OpushStaticConfiguration;
import org.obm.opush.env.OpushStaticConfiguration.EhCache;
import org.obm.push.configuration.OpushConfiguration;
import org.obm.push.store.WindowingDao;
import org.obm.push.store.ehcache.CacheEvictionListener;
import org.obm.push.store.ehcache.CacheEvictionListenerImpl;
import org.obm.push.store.ehcache.EhCacheConfiguration;
import org.obm.push.store.ehcache.ObjectStoreManager;
import org.obm.push.store.ehcache.StoreManager;
import org.obm.push.store.ehcache.WindowingDaoEhcacheImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class WindowingModule extends AbstractModule {

	private final Logger configurationLogger;

	public WindowingModule() {
		configurationLogger = LoggerFactory.getLogger(getClass());
	}
	
	@Override
	protected void configure() {
		OpushConfigurationFixture configuration = configuration();
		configuration.transaction.timeoutInSeconds = 3600;
		bind(OpushConfiguration.class).toInstance(new OpushStaticConfiguration(configuration));
		bind(TransactionConfiguration.class).toInstance(new StaticConfigurationService.Transaction(configuration.transaction));
		bind(TransactionProvider.class).to(LazyTransactionProvider.class);
		bind(WindowingDao.class).to(WindowingDaoEhcacheImpl.class);
		bind(Logger.class).annotatedWith(Names.named(LoggerModule.CONFIGURATION)).toInstance(configurationLogger);
		bind(EhCacheConfiguration.class).toInstance(new EhCache(configuration.ehCache));
		bind(StoreManager.class).to(ObjectStoreManager.class);
		bind(CacheEvictionListener.class).to(CacheEvictionListenerImpl.class);
	}		

	protected OpushConfigurationFixture configuration() {
		OpushConfigurationFixture configuration = new OpushConfigurationFixture();
		configuration.transaction.timeoutInSeconds = Ints.checkedCast(TimeUnit.MINUTES.toSeconds(10));
		configuration.dataDir = Files.createTempDir();
		return configuration;
	}

}
