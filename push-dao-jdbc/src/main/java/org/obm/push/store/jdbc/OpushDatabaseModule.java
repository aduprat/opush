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
package org.obm.push.store.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.obm.annotations.transactional.ITransactionAttributeBinder;
import org.obm.dbcp.DatabaseConnectionProvider;
import org.obm.dbcp.DatabaseConnectionProviderImpl;
import org.obm.dbcp.DatabaseModule;
import org.obm.dbcp.PoolingDataSourceDecorator;

import com.google.inject.Inject;
import com.google.inject.Singleton;

public class OpushDatabaseModule extends DatabaseModule {

	
	@Override
	protected void bindDatabaseConnectionProvider() {
		bind(DatabaseConnectionProvider.class).to(TechnicalLogDatabaseConnectionProvider.class);
	}

	@Singleton
	public static class TechnicalLogDatabaseConnectionProvider extends DatabaseConnectionProviderImpl {

		@Inject 
		TechnicalLogDatabaseConnectionProvider(
				ITransactionAttributeBinder transactionAttributeBinder,
				PoolingDataSourceDecorator poolingDataSource) {
			super(transactionAttributeBinder, poolingDataSource);
		}

		@Override
		public Connection getConnection() throws SQLException {
			return TechnicalLoggingConnection.of(super.getConnection());
		}
		
		public static class TechnicalLoggingConnection implements Connection {
			
			public static Connection of(Connection autoclosable) {
				return new TechnicalLoggingConnection(autoclosable);
			}

			private Connection connection;
			
			public TechnicalLoggingConnection(Connection autoclosable) {
				this.connection = autoclosable;
			}
			
			@Override
			public void close() throws SQLException {
				connection.close();
			}

			public <T> T unwrap(Class<T> iface) throws SQLException {
				return connection.unwrap(iface);
			}

			public boolean isWrapperFor(Class<?> iface) throws SQLException {
				return connection.isWrapperFor(iface);
			}

			public Statement createStatement() throws SQLException {
				return connection.createStatement();
			}

			public PreparedStatement prepareStatement(String sql)
					throws SQLException {
				return connection.prepareStatement(sql);
			}

			public CallableStatement prepareCall(String sql)
					throws SQLException {
				return connection.prepareCall(sql);
			}

			public String nativeSQL(String sql) throws SQLException {
				return connection.nativeSQL(sql);
			}

			public void setAutoCommit(boolean autoCommit) throws SQLException {
				connection.setAutoCommit(autoCommit);
			}

			public boolean getAutoCommit() throws SQLException {
				return connection.getAutoCommit();
			}

			public void commit() throws SQLException {
				connection.commit();
			}

			public void rollback() throws SQLException {
				connection.rollback();
			}

			public boolean isClosed() throws SQLException {
				return connection.isClosed();
			}

			public DatabaseMetaData getMetaData() throws SQLException {
				return connection.getMetaData();
			}

			public void setReadOnly(boolean readOnly) throws SQLException {
				connection.setReadOnly(readOnly);
			}

			public boolean isReadOnly() throws SQLException {
				return connection.isReadOnly();
			}

			public void setCatalog(String catalog) throws SQLException {
				connection.setCatalog(catalog);
			}

			public String getCatalog() throws SQLException {
				return connection.getCatalog();
			}

			public void setTransactionIsolation(int level) throws SQLException {
				connection.setTransactionIsolation(level);
			}

			public int getTransactionIsolation() throws SQLException {
				return connection.getTransactionIsolation();
			}

			public SQLWarning getWarnings() throws SQLException {
				return connection.getWarnings();
			}

			public void clearWarnings() throws SQLException {
				connection.clearWarnings();
			}

			public Statement createStatement(int resultSetType,
					int resultSetConcurrency) throws SQLException {
				return connection.createStatement(resultSetType,
						resultSetConcurrency);
			}

			public PreparedStatement prepareStatement(String sql,
					int resultSetType, int resultSetConcurrency)
					throws SQLException {
				return connection.prepareStatement(sql, resultSetType,
						resultSetConcurrency);
			}

			public CallableStatement prepareCall(String sql, int resultSetType,
					int resultSetConcurrency) throws SQLException {
				return connection.prepareCall(sql, resultSetType,
						resultSetConcurrency);
			}

			public Map<String, Class<?>> getTypeMap() throws SQLException {
				return connection.getTypeMap();
			}

			public void setTypeMap(Map<String, Class<?>> map)
					throws SQLException {
				connection.setTypeMap(map);
			}

			public void setHoldability(int holdability) throws SQLException {
				connection.setHoldability(holdability);
			}

			public int getHoldability() throws SQLException {
				return connection.getHoldability();
			}

			public Savepoint setSavepoint() throws SQLException {
				return connection.setSavepoint();
			}

			public Savepoint setSavepoint(String name) throws SQLException {
				return connection.setSavepoint(name);
			}

			public void rollback(Savepoint savepoint) throws SQLException {
				connection.rollback(savepoint);
			}

			public void releaseSavepoint(Savepoint savepoint)
					throws SQLException {
				connection.releaseSavepoint(savepoint);
			}

			public Statement createStatement(int resultSetType,
					int resultSetConcurrency, int resultSetHoldability)
					throws SQLException {
				return connection.createStatement(resultSetType,
						resultSetConcurrency, resultSetHoldability);
			}

			public PreparedStatement prepareStatement(String sql,
					int resultSetType, int resultSetConcurrency,
					int resultSetHoldability) throws SQLException {
				return connection.prepareStatement(sql, resultSetType,
						resultSetConcurrency, resultSetHoldability);
			}

			public CallableStatement prepareCall(String sql, int resultSetType,
					int resultSetConcurrency, int resultSetHoldability)
					throws SQLException {
				return connection.prepareCall(sql, resultSetType,
						resultSetConcurrency, resultSetHoldability);
			}

			public PreparedStatement prepareStatement(String sql,
					int autoGeneratedKeys) throws SQLException {
				return connection.prepareStatement(sql, autoGeneratedKeys);
			}

			public PreparedStatement prepareStatement(String sql,
					int[] columnIndexes) throws SQLException {
				return connection.prepareStatement(sql, columnIndexes);
			}

			public PreparedStatement prepareStatement(String sql,
					String[] columnNames) throws SQLException {
				return connection.prepareStatement(sql, columnNames);
			}

			public Clob createClob() throws SQLException {
				return connection.createClob();
			}

			public Blob createBlob() throws SQLException {
				return connection.createBlob();
			}

			public NClob createNClob() throws SQLException {
				return connection.createNClob();
			}

			public SQLXML createSQLXML() throws SQLException {
				return connection.createSQLXML();
			}

			public boolean isValid(int timeout) throws SQLException {
				return connection.isValid(timeout);
			}

			public void setClientInfo(String name, String value)
					throws SQLClientInfoException {
				connection.setClientInfo(name, value);
			}

			public void setClientInfo(Properties properties)
					throws SQLClientInfoException {
				connection.setClientInfo(properties);
			}

			public String getClientInfo(String name) throws SQLException {
				return connection.getClientInfo(name);
			}

			public Properties getClientInfo() throws SQLException {
				return connection.getClientInfo();
			}

			public Array createArrayOf(String typeName, Object[] elements)
					throws SQLException {
				return connection.createArrayOf(typeName, elements);
			}

			public Struct createStruct(String typeName, Object[] attributes)
					throws SQLException {
				return connection.createStruct(typeName, attributes);
			}

			public void setSchema(String schema) throws SQLException {
				connection.setSchema(schema);
			}

			public String getSchema() throws SQLException {
				return connection.getSchema();
			}

			public void abort(Executor executor) throws SQLException {
				connection.abort(executor);
			}

			public void setNetworkTimeout(Executor executor, int milliseconds)
					throws SQLException {
				connection.setNetworkTimeout(executor, milliseconds);
			}

			public int getNetworkTimeout() throws SQLException {
				return connection.getNetworkTimeout();
			}
			
		}
		
	}
}
