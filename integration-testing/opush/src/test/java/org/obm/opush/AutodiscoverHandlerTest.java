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
package org.obm.opush;

import static org.easymock.EasyMock.expect;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

import javax.xml.transform.TransformerException;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Files;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.Configuration;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.guice.GuiceModule;
import org.obm.opush.env.CassandraServer;
import org.obm.opush.env.DefaultOpushModule;
import org.obm.opush.env.OpushGuiceRunner;
import org.obm.push.OpushServer;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.DeviceDao;
import org.obm.push.utils.DOMUtils;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.sync.auth.AuthFault;
import org.obm.sync.client.login.LoginClient;
import org.obm.sync.push.client.OPClient;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

@RunWith(OpushGuiceRunner.class)
@GuiceModule(DefaultOpushModule.class)
public class AutodiscoverHandlerTest {

	@Inject SingleUserFixture singleUserFixture;
	@Inject OpushServer opushServer;
	@Inject ClassToInstanceAgregateView<Object> classToInstanceMap;
	@Inject IMocksControl mocksControl;
	@Inject Configuration configuration;
	@Inject PolicyConfigurationProvider policyConfigurationProvider;
	@Inject CassandraServer cassandraServer;
	private CloseableHttpClient httpClient;
	
	@Before
	public void init() throws Exception {
		httpClient = HttpClientBuilder.create().build();
		cassandraServer.start();
		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration");
	}
	
	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		httpClient.close();
		Files.delete(configuration.dataDir);
	}

	@Test
	public void testAutodiscoverCommand() throws Exception {
		prepareMocks();
		String externalUrl = "https://external-url/Microsoft-Server-ActiveSync";
		configuration.activeSyncServletUrl = externalUrl;
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = IntegrationTestUtils.buildOpushClient(singleUserFixture.jaures, opushServer.getPort(), httpClient);
		
		String emailAddress = singleUserFixture.jaures.user.getEmail();
		Document document = opClient.postXml("Autodiscover", buildAutodiscoverCommand(emailAddress), "Autodiscover", null, false);
		
		checkAutodiscoverResponse(document, externalUrl, formatCultureParameter(Locale.getDefault()));
	}

	private void checkAutodiscoverResponse(Document response, String externalUrl, String culture) throws TransformerException {
		Assertions.assertThat(DOMUtils.serialize(response)).
		isEqualTo( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Autodiscover xmlns:Autodiscover=\"http://schemas.microsoft.com/exchange/autodiscover/responseschema/2006\">" +
				"<Response xmlns=\"http://schemas.microsoft.com/exchange/autodiscover/mobilesync/responseschema/2006\">" +
				"<Culture>" + culture + "</Culture>" +
				"<User>" +
				"<DisplayName>Jean Jaures</DisplayName><EMailAddress>jaures@sfio.fr</EMailAddress>" +
				"</User>" +
				"<Action>" +
				"<Settings>" +
				"<Server><Type>MobileSync</Type><Url>" + externalUrl + "</Url>" +
				"<Name>" + externalUrl + "</Name></Server>" +
				"<Server><Type>CertEnroll</Type>" +
				"<Url>" + externalUrl + "</Url><ServerData>CertEnrollTemplate</ServerData></Server>" +
				"</Settings>" +
				"</Action>" +
				"</Response>" +
				"</Autodiscover>");
	}

	private void prepareMocks() throws CollectionNotFoundException, DaoException, AuthFault {
		mockDeviceDao();
		mockLoginService();
		mockCollectionDaoNoChange();
	}

	private void mockDeviceDao() throws DaoException {
		DeviceDao deviceDao = classToInstanceMap.get(DeviceDao.class);
		IntegrationTestUtils.expectUserDeviceAccess(deviceDao, singleUserFixture.jaures);
	}

	private void mockCollectionDaoNoChange() throws CollectionNotFoundException, DaoException {
		CollectionDao collectionDao = classToInstanceMap.get(CollectionDao.class);
		IntegrationTestUtils.expectUserCollectionsNeverChange(collectionDao, Sets.newHashSet(singleUserFixture.jaures), Collections.<Integer>emptySet());
	}
	
	private void mockLoginService() throws AuthFault {
		LoginClient loginClient = classToInstanceMap.get(LoginClient.class);
		IntegrationTestUtils.expectUserLoginFromOpush(loginClient, singleUserFixture.jaures);
	}
	
	private Document buildAutodiscoverCommand(String emailAddress)
			throws SAXException, IOException {
		return DOMUtils.parse("<Autodiscover xmlns=\"http://schemas.microsoft.com/exchange/autodiscover/mobilesync/requestschema/2006\">"
				+ "<Request>"
				+ "<EMailAddress>"
				+ emailAddress
				+ "</EMailAddress>"
				+ "<AcceptableResponseSchema>"
				+ "http://schemas.microsoft.com/exchange/autodiscover/mobilesync/responseschema/2006"
				+ "</AcceptableResponseSchema>"
				+ "</Request>"
				+ "</Autodiscover>");
	}

	private String formatCultureParameter(Locale locale) {
		return  locale.getLanguage().toLowerCase() + ":" + 
				locale.getCountry().toLowerCase() ;
	}
}
