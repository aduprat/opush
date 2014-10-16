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
package org.obm.push.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.PriorityQueue;

import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

public class ResourcesHolderTest {

	private ResourcesHolder testee;

	@Before
	public void setUp() {
		testee = new ResourcesHolder();
	}
	
	@Test
	public void getShouldReturnNullWhenKeyNoMatch() {
		assertThat(testee.get(TestResource.class)).isNull();
	}
	
	
	@Test
	public void putShouldMakeGetSucceedWhenKeyMatch() {
		TestResource resource = new TestResource();
		testee.put(TestResource.class, resource);
		
		assertThat(testee.get(TestResource.class)).isSameAs(resource);
	}
	
	@Test(expected=IllegalStateException.class)
	public void putShouldCloseOverriddenResource() {
		TestResource resourceOverridden = new TestResource();
		TestResource resource = new TestResource();
		testee.put(TestResource.class, resourceOverridden);
		
		try {
			testee.put(TestResource.class, resource);
		} catch (Exception e) {
			assertThat(resourceOverridden.isClosed).isTrue();
			throw e;
		}
	}

	@Test
	public void closeShouldNoopWhenNoResource() {
		testee.close();
	}
	
	@Test
	public void closeShouldCloseResource() {
		TestResource resource = new TestResource();
		testee.put(TestResource.class, resource);
		
		testee.close();
		
		assertThat(resource.isClosed).isTrue();
	}

	@Test
	public void removeShouldNoopWhenNoResource() {
		testee.remove(TestResource.class);
	}
	
	@Test
	public void removeShouldCloseResource() {
		TestResource resource = new TestResource();
		testee.put(TestResource.class, resource);
		
		testee.remove(TestResource.class);
		
		assertThat(resource.isClosed).isTrue();
	}
	
	@Test
	public void removeShouldFreeKeyForNewResource() {
		TestResource resource = new TestResource();
		testee.put(TestResource.class, resource);
		
		testee.remove(TestResource.class);
		testee.put(TestResource.class, new TestResource());
		
		assertThat(resource.isClosed).isTrue();
	}
	
	@Test
	public void closeShouldCloseEveryResourceEvenWhenException() {
		ResourceType1 first = new ResourceType1(1);
		CloseFailsResource second = new CloseFailsResource(2);
		ResourceType2 third = new ResourceType2(3);
		
		testee.put(ResourceType1.class, first);
		testee.put(CloseFailsResource.class, second);
		testee.put(ResourceType2.class, third);
		testee.close();

		assertThat(first.isClosed).isTrue();
		assertThat(second.isClosed).isTrue();
		assertThat(third.isClosed).isTrue();
	}
	
	@Test
	public void closeShouldCloseResourceUsingComparator() {
		ResourceType1 first = new ResourceType1(5);
		ResourceType2 second = new ResourceType2(6);
		ResourceType3 third = new ResourceType3(7);
		ResourceType4 fourth = new ResourceType4(8);
		
		testee.put(ResourceType3.class, third);
		testee.put(ResourceType4.class, fourth);
		testee.put(ResourceType2.class, second);
		testee.put(ResourceType1.class, first);
		testee.close();

		assertThat(first.closedAt).isLessThan(second.closedAt);
		assertThat(second.closedAt).isLessThan(third.closedAt);
		assertThat(third.closedAt).isLessThan(fourth.closedAt);
	}
	
	@Test
	public void closeShouldCloseBackendResourceUsingComparator() {
		OtherResource other = new OtherResource();
		HttpBackendResource http = new HttpBackendResource();
		AccessTokenBackendResource accessToken = new AccessTokenBackendResource();
		
		testee.put(HttpBackendResource.class, http);
		assertThat(testee.get(HttpBackendResource.class)).isSameAs(http);
		testee.put(AccessTokenBackendResource.class, accessToken);
		assertThat(testee.get(ResourceType1.class)).isNull();
		testee.remove(OtherResource.class);
		testee.put(OtherResource.class, other);
		assertThat(testee.get(AccessTokenBackendResource.class)).isSameAs(accessToken);
		assertThat(testee.get(HttpBackendResource.class)).isSameAs(http);
		testee.close();

		assertThat(accessToken.closedAt).isLessThan(http.closedAt);
	}
	
	@Test
	public void closeShouldCloseAccessTokenBeforeHttpClient() {
		HttpClientResource clientResource = new HttpClientResource(null);
		AccessTokenResource accessTokenResource = new AccessTokenResource.Factory(null).create(null, null);
		
		PriorityQueue<BackendResource> priorityQueue = new PriorityQueue<>(ImmutableList.of(clientResource, accessTokenResource));
		assertThat(priorityQueue.poll()).isSameAs(accessTokenResource);
		assertThat(priorityQueue.poll()).isSameAs(clientResource);
	}
	
	@Test
	public void closeShouldCloseAccessTokenBeforeHttpClient2() {
		HttpClientResource clientResource = new HttpClientResource(null);
		AccessTokenResource accessTokenResource = new AccessTokenResource.Factory(null).create(null, null);
		OtherResource other = new OtherResource();
		
		PriorityQueue<BackendResource> priorityQueue = new PriorityQueue<>(ImmutableList.of(clientResource, other, accessTokenResource));
		assertThat(priorityQueue.poll()).isSameAs(accessTokenResource);
		assertThat(priorityQueue.poll()).isSameAs(clientResource);
		assertThat(priorityQueue.poll()).isSameAs(other);
	}
	
	static class TestResource extends BackendResource {

		int priority;
		boolean isClosed;
		long closedAt;

		public TestResource() {
			this(0);
		}
		
		public TestResource(int priority) {
			this.priority = priority;
			this.isClosed = false;
		}
		
		@Override
		public int compareTo(BackendResource r) {
			Preconditions.checkArgument(r instanceof TestResource);
			if (r instanceof TestResource) {
				return Ints.compare(priority, ((TestResource)r).priority);
			}
			return 0;
		}

		@Override
		public synchronized void close() {
			isClosed = true;
			closedAt = System.nanoTime();
		}
		
	}
	
	static class ResourceType1 extends TestResource {
		
		public ResourceType1(int priority) {
			super(priority);
		}
	}
	
	static class ResourceType2 extends TestResource {
		
		public ResourceType2(int priority) {
			super(priority);
		}
	}
	
	static class ResourceType3 extends TestResource {
		
		public ResourceType3(int priority) {
			super(priority);
		}
	}
	
	static class ResourceType4 extends TestResource {
		
		public ResourceType4(int priority) {
			super(priority);
		}
	}

	static class CloseFailsResource extends TestResource {
		
		public CloseFailsResource(int priority) {
			super(priority);
		}
		
		@Override
		public synchronized void close() {
			super.close();
			throw new RuntimeException("error");
		}
		
	}
	
	static class HttpBackendResource extends BackendResource {

		long closedAt;

		@Override
		public void close() {
			closedAt = System.nanoTime();
		}

		@Override
		protected ResourceCloseOrder getCloseOrder() {
			return ResourceCloseOrder.HTTP_CLIENT;
		}
	}
	
	static class AccessTokenBackendResource extends BackendResource {

		long closedAt;

		@Override
		public void close() {
			closedAt = System.nanoTime();
		}

		@Override
		protected ResourceCloseOrder getCloseOrder() {
			return ResourceCloseOrder.ACCESS_TOKEN;
		}
	}
	
	static class OtherResource extends BackendResource {

		long closedAt;
		
		@Override
		public synchronized void close() {
			closedAt = System.nanoTime();
		}
	}
}
