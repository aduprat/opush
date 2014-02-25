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
package org.obm.push.spushnik.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.expect;

import java.io.InputStream;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.easymock.IMocksControl;
import org.fest.util.Files;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.Configuration;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.arquillian.GuiceArquillianRunner;
import org.obm.guice.GuiceModule;
import org.obm.opush.ActiveSyncServletModule.OpushServer;
import org.obm.opush.SingleUserFixture;
import org.obm.opush.env.CassandraServer;
import org.obm.push.spushnik.SpushnikScenarioTestUtils;
import org.obm.push.spushnik.SpushnikTestUtils;
import org.obm.push.spushnik.SpushnikWebArchive;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

@RunWith(GuiceArquillianRunner.class)
@GuiceModule(ScenarioTestModule.class)
public class FolderSyncScenarioTest {

	@Inject SingleUserFixture singleUserFixture;
	@Inject OpushServer opushServer;
	@Inject ClassToInstanceAgregateView<Object> classToInstanceMap;
	@Inject IMocksControl mocksControl;
	@Inject Configuration configuration;
	@Inject PolicyConfigurationProvider policyConfigurationProvider;
	@Inject CassandraServer cassandraServer;
	
	private CloseableHttpClient httpClient;

	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		Files.delete(configuration.dataDir);
		httpClient.close();
	}
	
	@Before
	public void setUp() throws Exception {
		expect(policyConfigurationProvider.get()).andReturn("fakeConfiguration").anyTimes();
		SpushnikScenarioTestUtils.mockWorkingFolderSync(classToInstanceMap, singleUserFixture.jaures);
		mocksControl.replay();
		cassandraServer.start();
		opushServer.start();
		httpClient = HttpClientBuilder.create().build();
	}
	
	@Test
	@RunAsClient
	public void testFolderSyncScenarioWithNiceCertificate(@ArquillianResource URL baseURL) throws Exception {
		HttpResponse httpResponse = folderSyncScenarioWithRequest(baseURL, "request_jaures_certificate_good.txt");
		
		InputStream content = httpResponse.getEntity().getContent();
		assertThat(httpResponse.getStatusLine().getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
		assertThat(IOUtils.toString(content, Charsets.UTF_8)).isEqualTo("{\"status\":0,\"messages\":[]}");
	}
	
	@Test
	@RunAsClient
	public void testFolderSyncScenarioWithNiceCertificateWrongPwd(@ArquillianResource URL baseURL) throws Exception {
		HttpResponse httpResponse = folderSyncScenarioWithRequest(baseURL, "request_jaures_certificate_wrong_pwd.txt");
		
		InputStream content = httpResponse.getEntity().getContent();
		assertThat(httpResponse.getStatusLine().getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
		assertThat(IOUtils.toString(content, Charsets.UTF_8))
			.startsWith("{\"status\":2,\"messages\":[\"org.obm.push.spushnik.service.InvalidCredentialsException: Invalid certificate")
			.endsWith("\"]}");
	}
	
	@Test
	@RunAsClient
	public void testFolderSyncScenarioWithBadCertificate(@ArquillianResource URL baseURL) throws Exception {
		HttpResponse httpResponse = folderSyncScenarioWithRequest(baseURL, "request_jaures_certificate_bad.txt");
		
		InputStream content = httpResponse.getEntity().getContent();
		assertThat(httpResponse.getStatusLine().getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
		String string = IOUtils.toString(content, Charsets.UTF_8);
		assertThat(string)
			.startsWith("{\"status\":2,\"messages\":[\"org.obm.push.spushnik.service.InvalidCredentialsException: Invalid certificate")
			.endsWith("\"]}");
	}
	
	@Test
	@RunAsClient
	public void testFolderSyncScenarioWithNullCertificate(@ArquillianResource URL baseURL) throws Exception {
		HttpResponse httpResponse = folderSyncScenarioWithRequest(baseURL, "request_jaures_certificate_null.txt");
		
		InputStream content = httpResponse.getEntity().getContent();
		assertThat(httpResponse.getStatusLine().getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
		assertThat(IOUtils.toString(content, Charsets.UTF_8)).isEqualTo("{\"status\":0,\"messages\":[]}");
	}
	
	@Test
	@RunAsClient
	public void testFolderSyncScenarioWithoutCertificate(@ArquillianResource URL baseURL) throws Exception {
		HttpResponse httpResponse = folderSyncScenarioWithRequest(baseURL, "request_jaures_certificate_none.txt");
		
		InputStream content = httpResponse.getEntity().getContent();
		assertThat(httpResponse.getStatusLine().getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
		assertThat(IOUtils.toString(content, Charsets.UTF_8)).isEqualTo("{\"status\":0,\"messages\":[]}");
	}

	private HttpResponse folderSyncScenarioWithRequest(URL baseURL, String certificate) throws Exception {
		InputStream requestInputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(certificate);
		byte[] requestContent = ByteStreams.toByteArray(requestInputStream);

		HttpPost httpPost = new HttpPost(buildRequestUrl(baseURL));
		httpPost.setEntity(new ByteArrayEntity(requestContent, ContentType.APPLICATION_JSON));
		return httpClient.execute(httpPost);
	}

	private String buildRequestUrl(URL baseURL) {
		return baseURL.toExternalForm() + "foldersync?serviceUrl=" + SpushnikTestUtils.buildServiceUrl(opushServer.getPort());
	}
	
	@Deployment
	public static WebArchive createDeployment() throws Exception {
		// Deployed once for the whole test class, unexpected data may be remaining between tests
		return SpushnikWebArchive.buildInstance();
	}
}
