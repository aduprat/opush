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
package org.obm.push.cassandra.dao;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.obm.push.cassandra.dao.CassandraStructure.DeliveryStatusNotification.TABLE;
import static org.obm.push.cassandra.dao.CassandraStructure.DeliveryStatusNotification.Columns.DELIVERY;
import static org.obm.push.cassandra.dao.CassandraStructure.DeliveryStatusNotification.Columns.READ_RECEIPT;
import static org.obm.push.cassandra.dao.CassandraStructure.DeliveryStatusNotification.Columns.SERVER_ID;
import static org.obm.push.cassandra.dao.CassandraStructure.DeliveryStatusNotification.Columns.USER;

import org.obm.breakdownduration.bean.Watch;
import org.obm.push.bean.BreakdownGroups;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.User;
import org.obm.push.configuration.LoggerModule;
import org.obm.push.exception.DaoException;
import org.obm.push.json.JSONService;
import org.obm.push.store.DeliveryStatusNotificationDao;
import org.slf4j.Logger;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select.Where;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
@Watch(BreakdownGroups.CASSANDRA)
public class DeliveryStatusNotificationDaoImpl extends AbstractCassandraDao implements DeliveryStatusNotificationDao, CassandraStructure, CassandraDao {

	@Inject
	@VisibleForTesting public DeliveryStatusNotificationDaoImpl(Provider<Session> sessionProvider, JSONService jsonService, 
			@Named(LoggerModule.CASSANDRA)Logger logger) {
		super(sessionProvider, jsonService, logger);
	}
	
	@Override
	public void insertStatus(org.obm.push.bean.DeliveryStatusNotification notification) throws DaoException {
		Insert query = insertInto(TABLE.get())
				.value(USER, notification.getUser().getLoginAtDomain())
				.value(SERVER_ID, notification.getServerId().asString())
				.value(DELIVERY, notification.isDelivery())
				.value(READ_RECEIPT, notification.isReadReceipt());
		logger.debug("Inserting dsn {}", query.getQueryString());
		getSession().execute(query);
	}

	@Override
	public boolean hasAlreadyBeenDelivered(User user, ServerId serverId) {
		return retrieveBooleanValueForField(user, serverId, DELIVERY);
	}

	@Override
	public boolean hasAlreadyBeenRead(User user, ServerId serverId) {
		return retrieveBooleanValueForField(user, serverId, READ_RECEIPT);
	}

	private boolean retrieveBooleanValueForField(User user, ServerId serverId, String field) {
		Where statement = select(field).from(TABLE.get())
				.where(eq(USER, user.getLoginAtDomain()))
				.and(eq(SERVER_ID, serverId.asString()));
		logger.debug("Selecting dsn: {}", statement.getQueryString());

		ResultSet results = getSession().execute(statement);
		if (results.isExhausted()) {
			return false;
		}
		return results.one().getBool(field);
	}
}
