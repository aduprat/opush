/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2013 Linagora
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
package org.obm.provisioning;

import org.obm.provisioning.bean.LdapUser;
import org.obm.provisioning.exception.ConnectionException;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import fr.aliacom.obm.common.user.ObmUser;

public class LdapManagerImpl implements LdapManager {

	private Connection conn;
	private Provider<LdapUser.Builder> userBuilderProvider;

	@Singleton
	public static class Factory implements LdapManager.Factory {
		private Provider<LdapUser.Builder> userBuilderProvider;
		private Connection.Factory connectionFactory;

		@Inject
		public Factory(Provider<LdapUser.Builder> userBuilderProvider,
				Connection.Factory connectionFactory) {
			this.userBuilderProvider = userBuilderProvider;
			this.connectionFactory = connectionFactory;
		}

		@Override
		public LdapManager create() {
			return new LdapManagerImpl(connectionFactory.create(),
					userBuilderProvider);
		}

	}

	public LdapManagerImpl(Connection conn,
			Provider<LdapUser.Builder> userBuilderProvider) {
		this.conn = conn;
		this.userBuilderProvider = userBuilderProvider;
	}

	@Override
	public void createUser(ObmUser obmUser) {
		LdapUser ldapUser = userBuilderProvider.get().fromObmUser(obmUser)
				.build();
		conn.createUser(ldapUser);
	}

	@Override
	public void shutdown() throws ConnectionException {
		conn.shutdown();
	}
}
