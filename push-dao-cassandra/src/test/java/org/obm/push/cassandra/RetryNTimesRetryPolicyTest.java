/* ***** BEGIN LICENSE BLOCK *****
 * Copyright (C) 2015  Linagora
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version, provided you comply with the Additional Terms applicable for OBM
 * software by Linagora pursuant to Section 7 of the GNU Affero General Public
 * License, subsections (b), (c), and (e), pursuant to which you must notably (i)
 * retain the displaying by the interactive user interfaces of the “OBM, Free
 * Communication by Linagora” Logo with the “You are using the Open Source and
 * free version of OBM developed and supported by Linagora. Contribute to OBM R&D
 * by subscribing to an Enterprise offer !” infobox, (ii) retain all hypertext
 * links between OBM and obm.org, between Linagora and linagora.com, as well as
 * between the expression “Enterprise offer” and pro.obm.org, and (iii) refrain
 * from infringing Linagora intellectual property rights over its trademarks and
 * commercial brands. Other Additional Terms apply, see
 * <http://www.linagora.com/licenses/> for more details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License and
 * its applicable Additional Terms for OBM along with this program. If not, see
 * <http://www.gnu.org/licenses/> for the GNU Affero General   Public License
 * version 3 and <http://www.linagora.com/licenses/> for the Additional Terms
 * applicable to the OBM software.
 * ***** END LICENSE BLOCK ***** */
package org.obm.push.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;

import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.obm.push.configuration.CassandraConfiguration;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.policies.RetryPolicy.RetryDecision;


public class RetryNTimesRetryPolicyTest {

	private static final int MAX_NUMBER_OF_RETRY = 3;
	
	private CassandraConfiguration cassandraConfiguration;
	private Statement statement;
	
	private IMocksControl control;
	
	@Before
	public void setup() {
		control = createControl();
		cassandraConfiguration = control.createMock(CassandraConfiguration.class);
		expect(cassandraConfiguration.maxRetries())
			.andReturn(MAX_NUMBER_OF_RETRY).anyTimes();
		statement = control.createMock(Statement.class);
	}

	@Test
	public void onReadTimeoutShouldRetryWhenMaxRetriesNotReached() {
		int numberOfRetry = MAX_NUMBER_OF_RETRY - 1;
		
		control.replay();
		RetryNTimesRetryPolicy retryNTimesRetryPolicy = new RetryNTimesRetryPolicy(cassandraConfiguration);
		RetryDecision retryDecision = retryNTimesRetryPolicy.onReadTimeout(statement, ConsistencyLevel.QUORUM, 1, 1, true, numberOfRetry);
		control.verify();
		
		assertThat(retryDecision.getType()).isEqualTo(RetryDecision.Type.RETRY);
		assertThat(retryDecision.getRetryConsistencyLevel()).isEqualTo(ConsistencyLevel.QUORUM);
	}

	@Test
	public void onReadTimeoutShouldRethrowWhenMaxRetriesReached() {
		int numberOfRetry = MAX_NUMBER_OF_RETRY;
		
		control.replay();
		RetryNTimesRetryPolicy retryNTimesRetryPolicy = new RetryNTimesRetryPolicy(cassandraConfiguration);
		RetryDecision retryDecision = retryNTimesRetryPolicy.onReadTimeout(statement, ConsistencyLevel.QUORUM, 1, 1, true, numberOfRetry);
		control.verify();
		
		assertThat(retryDecision.getType()).isEqualTo(RetryDecision.Type.RETHROW);
	}

	@Test
	public void onWriteTimeoutShouldRetryWhenMaxRetriesNotReached() {
		int numberOfRetry = MAX_NUMBER_OF_RETRY - 1;

		control.replay();
		RetryNTimesRetryPolicy retryNTimesRetryPolicy = new RetryNTimesRetryPolicy(cassandraConfiguration);
		RetryDecision retryDecision = retryNTimesRetryPolicy.onWriteTimeout(statement, ConsistencyLevel.QUORUM, WriteType.BATCH, 1, 1, numberOfRetry);
		control.verify();
		
		assertThat(retryDecision.getType()).isEqualTo(RetryDecision.Type.RETRY);
		assertThat(retryDecision.getRetryConsistencyLevel()).isEqualTo(ConsistencyLevel.QUORUM);
	}

	@Test
	public void onWriteTimeoutShouldRethrowWhenMaxRetriesReached() {
		int numberOfRetry = MAX_NUMBER_OF_RETRY;

		control.replay();
		RetryNTimesRetryPolicy retryNTimesRetryPolicy = new RetryNTimesRetryPolicy(cassandraConfiguration);
		RetryDecision retryDecision = retryNTimesRetryPolicy.onWriteTimeout(statement, ConsistencyLevel.QUORUM, WriteType.BATCH, 1, 1, numberOfRetry);
		control.verify();
		
		assertThat(retryDecision.getType()).isEqualTo(RetryDecision.Type.RETHROW);
	}

	@Test
	public void onUnavailableShouldRetryWhenMaxRetriesNotReached() {
		int numberOfRetry = MAX_NUMBER_OF_RETRY - 1;

		control.replay();
		RetryNTimesRetryPolicy retryNTimesRetryPolicy = new RetryNTimesRetryPolicy(cassandraConfiguration);
		RetryDecision retryDecision = retryNTimesRetryPolicy.onUnavailable(statement, ConsistencyLevel.QUORUM, 1, 1, numberOfRetry);
		control.verify();
		
		assertThat(retryDecision.getType()).isEqualTo(RetryDecision.Type.RETRY);
		assertThat(retryDecision.getRetryConsistencyLevel()).isEqualTo(ConsistencyLevel.QUORUM);
	}

	@Test
	public void onUnavailableShouldRethrowWhenMaxRetriesReached() {
		int numberOfRetry = MAX_NUMBER_OF_RETRY;

		control.replay();
		RetryNTimesRetryPolicy retryNTimesRetryPolicy = new RetryNTimesRetryPolicy(cassandraConfiguration);
		RetryDecision retryDecision = retryNTimesRetryPolicy.onUnavailable(statement, ConsistencyLevel.QUORUM, 1, 1, numberOfRetry);
		control.verify();
		
		assertThat(retryDecision.getType()).isEqualTo(RetryDecision.Type.RETHROW);
	}
}
