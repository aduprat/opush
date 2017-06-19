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
package org.obm.opush.env;

import static org.easymock.EasyMock.createControl;

import org.easymock.IMocksControl;
import org.obm.Configuration;
import org.obm.StaticConfigurationService;
import org.obm.StaticLocatorConfiguration;
import org.obm.configuration.DatabaseConfiguration;
import org.obm.configuration.DatabaseFlavour;
import org.obm.configuration.EmailConfiguration;
import org.obm.configuration.GlobalAppConfiguration;
import org.obm.guice.AbstractOverrideModule;
import org.obm.opush.ActiveSyncServletModule;
import org.obm.push.configuration.OpushConfiguration;
import org.obm.push.configuration.OpushEmailConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.util.Modules;

public abstract class AbstractOpushEnv extends ActiveSyncServletModule {

	protected final Logger logger = LoggerFactory.getLogger(getClass());
	protected final IMocksControl mocksControl;
	protected final OpushConfigurationFixture configuration;
	
	public AbstractOpushEnv() {
		mocksControl = createControl();
		configuration = new OpushConfigurationFixture();
		configuration.dataDir = Files.createTempDir();
		configuration.transaction.timeoutInSeconds = 3600;
	}

	@Provides
	public IMocksControl getMocksControl() {
		return mocksControl;
	}
	
	@Override
	protected Module overrideModule() throws Exception {
		return Modules.combine(dao(),
				cassandra(),
				email(),
				obmSync(),
				backendsModule(),
				configuration());
	}

	protected ObmSyncModule obmSync() {
		return new ObmSyncModule(mocksControl);
	}

	protected BackendsModule backendsModule() {
		return new BackendsModule(mocksControl);
	}
	
	protected Module email() {
		return Modules.combine(new EmailModule(mocksControl), emailConfiguration());
	}

	protected DaoModule dao() {
		return new DaoModule(mocksControl);
	}

	protected AbstractOverrideModule cassandra() {
		return new OpushCassandraModule(mocksControl);
	}

	@Override
	protected GlobalAppConfiguration<org.obm.push.configuration.OpushConfiguration>opushConfiguration() {
		return GlobalAppConfiguration.<OpushConfiguration>builder()
					.mainConfiguration(new OpushStaticConfiguration(configuration))
					.locatorConfiguration(new StaticLocatorConfiguration(configuration.locator))
					.databaseConfiguration(databaseConfiguration())
					.transactionConfiguration(new StaticConfigurationService.Transaction(configuration.transaction))
					.build();
	}
	
	protected DatabaseConfiguration databaseConfiguration() {
		return new DatabaseConfiguration() {

			@Override
			public boolean isPostgresSSLNonValidating() {
				return false;
			}

			@Override
			public boolean isPostgresSSLEnabled() {
				return false;
			}

			@Override
			public DatabaseFlavour getDatabaseSystem() {
				return null;
			}

			@Override
			public String getDatabasePassword() {
				return null;
			}

			@Override
			public String getDatabaseName() {
				return null;
			}

			@Override
			public Integer getDatabaseMaxConnectionPoolSize() {
				return null;
			}

			@Override
			public String getDatabaseLogin() {
				return null;
			}

			@Override
			public String getDatabaseHost() {
				return null;
			}
			
			@Override
			public Integer getDatabaseMinConnectionPoolSize() {
				return null;
			}
			
			@Override
			public String getJdbcOptions() {
				return null;
			}

			@Override
			public Integer getDatabasePort() {
				return null;
			}

			@Override
			public boolean isReadOnly() {
				return false;
			}

			@Override
			public boolean isAutoTruncateEnabled() {
				return false;
			}
		};
	}
	
	protected OpushConfigurationModule configuration() {
		return new OpushConfigurationModule(configuration, mocksControl);
	}

	protected Module emailConfiguration() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				OpushStaticConfiguration.Email opushStaticConfiguration = new OpushStaticConfiguration.Email(configuration.mail);
				bind(OpushEmailConfiguration.class).toInstance(opushStaticConfiguration);
				bind(EmailConfiguration.class).toInstance(opushStaticConfiguration);
			}
		};
	}
	
	@Provides
	public Configuration configurationProvider() {
		return configuration;
	}
	
	@Provides
	public OpushConfigurationFixture opushConfigurationFixtureProvider() {
		return configuration;
	}
}
