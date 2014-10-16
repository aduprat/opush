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

import org.apache.http.client.HttpClient;
import org.obm.sync.auth.AccessToken;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class OpushResourcesHolder {

	private final Provider<ResourcesHolder> resourcesHolderProvider;
	
	@Inject OpushResourcesHolder(Provider<ResourcesHolder> resourcesHolderProvider) {
		this.resourcesHolderProvider = resourcesHolderProvider;
	}

	public <T extends SortedResource> T get(Class<T> clazz) {
		return getResourcesHolder().get(clazz);
	}
	
	public AccessToken getAccessToken() {
		return getResourcesHolder().get(AccessTokenResource.class).getAccessToken();
	}
	
	public HttpClient getHttpClient() {
		return getResourcesHolder().get(HttpClientResource.class).getHttpClient();
	}
	
	public <T extends SortedResource> void put(Class<T> clazz, T resource) {
		getResourcesHolder().put(clazz, resource);
	}
	
	public void close() {
		getResourcesHolder().close();
	}
	
	public void remove(Class<? extends SortedResource> clazz) {
		getResourcesHolder().remove(clazz);
	}

	private ResourcesHolder getResourcesHolder() {
		return resourcesHolderProvider.get();
	}
}
