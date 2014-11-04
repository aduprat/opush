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
package org.obm.opush.command.prov;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.Properties;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.assertj.core.util.Files;
import org.custommonkey.xmlunit.XMLAssert;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.ConfigurationModule.PolicyConfigurationProvider;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.opush.IntegrationTestUtils;
import org.obm.opush.IntegrationUserAccessUtils;
import org.obm.opush.Users;
import org.obm.opush.Users.OpushUser;
import org.obm.opush.env.CassandraServer;
import org.obm.opush.env.DefaultOpushModule;
import org.obm.opush.env.OpushConfigurationFixture;
import org.obm.push.OpushServer;
import org.obm.push.ProtocolVersion;
import org.obm.push.bean.Device;
import org.obm.push.bean.ProvisionPolicyStatus;
import org.obm.push.bean.ProvisionStatus;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.store.DeviceDao;
import org.obm.push.store.DeviceDao.PolicyStatus;
import org.obm.push.utils.DOMUtils;
import org.obm.sync.auth.AuthFault;
import org.obm.sync.push.client.OPClient;
import org.obm.sync.push.client.ProvisionResponse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

@RunWith(GuiceRunner.class)
@GuiceModule(DefaultOpushModule.class)
public class ProvisionHandlerTest {

	@Inject private Users users;
	@Inject private OpushServer opushServer;
	@Inject private IMocksControl mocksControl;
	@Inject private OpushConfigurationFixture configuration;
	@Inject private PolicyConfigurationProvider policyConfigurationProvider;
	@Inject private CassandraServer cassandraServer;
	@Inject private DeviceDao deviceDao;
	@Inject private IntegrationUserAccessUtils userAccessUtils;
	@Inject private IntegrationTestUtils testUtils;

	private CloseableHttpClient httpClient;

	@Before
	public void init() throws Exception {
		httpClient = HttpClientBuilder.create().build();
		cassandraServer.start();
	}
		
	@After
	public void shutdown() throws Exception {
		opushServer.stop();
		cassandraServer.stop();
		Files.delete(configuration.dataDir);
		httpClient.close();
	}

	@Test
	public void testFirstProvisionSendPolicy() throws Exception {
		long nextPolicyKeyGenerated = 115l;
		OpushUser user = users.jaures;
		mockProvisionNeeds(user);

		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();		
		
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		deviceDao.removeUnknownDeviceSyncPerm(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(nextPolicyKeyGenerated).once();
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ProvisionResponse provisionResponse = opClient.provisionStepOne();

		assertOnProvisionResponseSendPolicy(nextPolicyKeyGenerated, provisionResponse);
	}

	@Test
	public void testCheckDefault12Dot1Policy() throws Exception {
		long nextPolicyKeyGenerated = 115l;
		OpushUser user = users.jaures;
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();
		
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		deviceDao.removeUnknownDeviceSyncPerm(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(nextPolicyKeyGenerated).once();
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), ProtocolVersion.V121, httpClient);
		ProvisionResponse provisionResponse = opClient.provisionStepOne();

		XMLAssert.assertXMLEqual(DOMUtils.createDocFromElement(provisionResponse.getPolicyDataEl()), DOMUtils.parse(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<Data>" +
					"<EASProvisionDoc>" +
					"<DevicePasswordEnabled>0</DevicePasswordEnabled>" +
					"<AlphanumericDevicePasswordRequired>0</AlphanumericDevicePasswordRequired>" +
					"<PasswordRecoveryEnabled>0</PasswordRecoveryEnabled>" +
					"<DeviceEncryptionEnabled>0</DeviceEncryptionEnabled>" +
					"<AttachmentsEnabled>1</AttachmentsEnabled>" +
					"<MinDevicePasswordLength>4</MinDevicePasswordLength>" +
					"<MaxInactivityTimeDeviceLock>900</MaxInactivityTimeDeviceLock>" +
					"<MaxDevicePasswordFailedAttempts>8</MaxDevicePasswordFailedAttempts>" +
					"<MaxAttachmentSize/>" +
					"<AllowSimpleDevicePassword>1</AllowSimpleDevicePassword>" +
					"<DevicePasswordExpiration/>" +
					"<DevicePasswordHistory>0</DevicePasswordHistory>" +
					"<AllowStorageCard>1</AllowStorageCard>" +
					"<AllowCamera>1</AllowCamera>" +
					"<RequireDeviceEncryption>0</RequireDeviceEncryption>" +
					"<AllowUnsignedApplications>1</AllowUnsignedApplications>" +
					"<AllowUnsignedInstallationPackages>1</AllowUnsignedInstallationPackages>" +
					"<MinDevicePasswordComplexCharacters>3</MinDevicePasswordComplexCharacters>" +
					"<AllowWiFi>1</AllowWiFi>" +
					"<AllowTextMessaging>1</AllowTextMessaging>" +
					"<AllowPOPIMAPEmail>1</AllowPOPIMAPEmail>" +
					"<AllowBluetooth>2</AllowBluetooth>" +
					"<AllowIrDA>1</AllowIrDA>" +
					"<RequireManualSyncWhenRoaming>0</RequireManualSyncWhenRoaming>" +
					"<AllowDesktopSync>1</AllowDesktopSync>" +
					"<MaxCalendarAgeFilter>0</MaxCalendarAgeFilter>" +
					"<AllowHTMLEmail>1</AllowHTMLEmail>" +
					"<MaxEmailAgeFilter>0</MaxEmailAgeFilter>" +
					"<MaxEmailBodyTruncationSize>-1</MaxEmailBodyTruncationSize>" +
					"<MaxEmailHTMLBodyTruncationSize>-1</MaxEmailHTMLBodyTruncationSize>" +
					"<RequireSignedSMIMEMessages>0</RequireSignedSMIMEMessages>" +
					"<RequireEncryptedSMIMEMessages>0</RequireEncryptedSMIMEMessages>" +
					"<RequireSignedSMIMEAlgorithm>0</RequireSignedSMIMEAlgorithm>" +
					"<RequireEncryptionSMIMEAlgorithm>0</RequireEncryptionSMIMEAlgorithm>" +
					"<AllowSMIMEEncryptionAlgorithmNegotiation>2</AllowSMIMEEncryptionAlgorithmNegotiation>" +
					"<AllowSMIMESoftCerts>1</AllowSMIMESoftCerts>" +
					"<AllowBrowser>1</AllowBrowser>" +
					"<AllowConsumerEmail>1</AllowConsumerEmail>" +
					"<AllowRemoteDesktop>1</AllowRemoteDesktop>" +
					"<AllowInternetSharing>1</AllowInternetSharing>" +
					"<UnapprovedInROMApplicationList/>" +
					"<ApprovedApplicationList/>" +
					"</EASProvisionDoc>" +
					"</Data>"));
	}
	
	@Test
	public void testCheckModified12Dot1Policy() throws Exception {
		long nextPolicyKeyGenerated = 115l;
		OpushUser user = users.jaures;
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn(getClass().getResource("modifiedPolicy.properties").getFile());

		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		deviceDao.removeUnknownDeviceSyncPerm(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(nextPolicyKeyGenerated).once();
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), ProtocolVersion.V121, httpClient);
		ProvisionResponse provisionResponse = opClient.provisionStepOne();

		XMLAssert.assertXMLEqual(DOMUtils.createDocFromElement(provisionResponse.getPolicyDataEl()), DOMUtils.parse(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
						"<Data>" +
						"<EASProvisionDoc>" +
						"<DevicePasswordEnabled>0</DevicePasswordEnabled>" +
						"<AlphanumericDevicePasswordRequired>0</AlphanumericDevicePasswordRequired>" +
						"<PasswordRecoveryEnabled>0</PasswordRecoveryEnabled>" +
						"<DeviceEncryptionEnabled>0</DeviceEncryptionEnabled>" +
						"<AttachmentsEnabled>1</AttachmentsEnabled>" +
						"<MinDevicePasswordLength>4</MinDevicePasswordLength>" +
						"<MaxInactivityTimeDeviceLock>900</MaxInactivityTimeDeviceLock>" +
						"<MaxDevicePasswordFailedAttempts>2</MaxDevicePasswordFailedAttempts>" +
						"<MaxAttachmentSize/>" +
						"<AllowSimpleDevicePassword>1</AllowSimpleDevicePassword>" +
						"<DevicePasswordExpiration/>" +
						"<DevicePasswordHistory>0</DevicePasswordHistory>" +
						"<AllowStorageCard>1</AllowStorageCard>" +
						"<AllowCamera>1</AllowCamera>" +
						"<RequireDeviceEncryption>0</RequireDeviceEncryption>" +
						"<AllowUnsignedApplications>1</AllowUnsignedApplications>" +
						"<AllowUnsignedInstallationPackages>1</AllowUnsignedInstallationPackages>" +
						"<MinDevicePasswordComplexCharacters>3</MinDevicePasswordComplexCharacters>" +
						"<AllowWiFi>1</AllowWiFi>" +
						"<AllowTextMessaging>1</AllowTextMessaging>" +
						"<AllowPOPIMAPEmail>1</AllowPOPIMAPEmail>" +
						"<AllowBluetooth>2</AllowBluetooth>" +
						"<AllowIrDA>1</AllowIrDA>" +
						"<RequireManualSyncWhenRoaming>0</RequireManualSyncWhenRoaming>" +
						"<AllowDesktopSync>1</AllowDesktopSync>" +
						"<MaxCalendarAgeFilter>0</MaxCalendarAgeFilter>" +
						"<AllowHTMLEmail>1</AllowHTMLEmail>" +
						"<MaxEmailAgeFilter>0</MaxEmailAgeFilter>" +
						"<MaxEmailBodyTruncationSize>-1</MaxEmailBodyTruncationSize>" +
						"<MaxEmailHTMLBodyTruncationSize>-1</MaxEmailHTMLBodyTruncationSize>" +
						"<RequireSignedSMIMEMessages>0</RequireSignedSMIMEMessages>" +
						"<RequireEncryptedSMIMEMessages>0</RequireEncryptedSMIMEMessages>" +
						"<RequireSignedSMIMEAlgorithm>0</RequireSignedSMIMEAlgorithm>" +
						"<RequireEncryptionSMIMEAlgorithm>0</RequireEncryptionSMIMEAlgorithm>" +
						"<AllowSMIMEEncryptionAlgorithmNegotiation>2</AllowSMIMEEncryptionAlgorithmNegotiation>" +
						"<AllowSMIMESoftCerts>1</AllowSMIMESoftCerts>" +
						"<AllowBrowser>1</AllowBrowser>" +
						"<AllowConsumerEmail>1</AllowConsumerEmail>" +
						"<AllowRemoteDesktop>1</AllowRemoteDesktop>" +
						"<AllowInternetSharing>1</AllowInternetSharing>" +
						"<UnapprovedInROMApplicationList/>" +
						"<ApprovedApplicationList/>" +
						"</EASProvisionDoc>" +
						"</Data>"));
	}
	
	@Test
	public void testCheckDefault12Dot0Policy() throws Exception {
		long nextPolicyKeyGenerated = 115l;
		OpushUser user = users.jaures;
		user.deviceProtocolVersion = ProtocolVersion.V120;
		user.device = new Device.Factory().create(1, user.deviceType, user.userAgent, user.deviceId, user.deviceProtocolVersion);
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();
		
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		deviceDao.removeUnknownDeviceSyncPerm(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(nextPolicyKeyGenerated).once();
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), ProtocolVersion.V120, httpClient);
		ProvisionResponse provisionResponse = opClient.provisionStepOne();

		XMLAssert.assertXMLEqual(DOMUtils.createDocFromElement(provisionResponse.getPolicyDataEl()), DOMUtils.parse(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<Data>" +
					"<EASProvisionDoc>" +
					"<DevicePasswordEnabled>0</DevicePasswordEnabled>" +
					"<AlphanumericDevicePasswordRequired>0</AlphanumericDevicePasswordRequired>" +
					"<PasswordRecoveryEnabled>0</PasswordRecoveryEnabled>" +
					"<DeviceEncryptionEnabled>0</DeviceEncryptionEnabled>" +
					"<AttachmentsEnabled>1</AttachmentsEnabled>" +
					"<MinDevicePasswordLength>4</MinDevicePasswordLength>" +
					"<MaxInactivityTimeDeviceLock>900</MaxInactivityTimeDeviceLock>" +
					"<MaxDevicePasswordFailedAttempts>8</MaxDevicePasswordFailedAttempts>" +
					"<MaxAttachmentSize/>" +
					"<AllowSimpleDevicePassword>1</AllowSimpleDevicePassword>" +
					"<DevicePasswordExpiration/>" +
					"<DevicePasswordHistory>0</DevicePasswordHistory>" +
					"</EASProvisionDoc>" +
					"</Data>"));
	}

	@Test
	public void testFirstProvisionWithNotAllowedUnknownDevice() throws Exception {
		configuration.syncPerms.allowUnknownDevice = false;
		long nextPolicyKeyGenerated = 115l;
		OpushUser user = users.jaures;
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();
		
		expect(deviceDao.syncAuthorized(user.user, user.deviceId)).andReturn(true);
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		deviceDao.removeUnknownDeviceSyncPerm(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(nextPolicyKeyGenerated).once();
		
		mocksControl.replay();
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ProvisionResponse provisionResponse = opClient.provisionStepOne();

		assertOnProvisionResponseSendPolicy(nextPolicyKeyGenerated, provisionResponse);
	}

	private void assertOnProvisionResponseSendPolicy(long nextPolicyKeyGenerated, ProvisionResponse provisionResponse) {
		assertThat(provisionResponse.getResponse().getStatus()).isEqualTo(ProvisionStatus.SUCCESS);
		assertThat(provisionResponse.getResponse().getPolicyKey()).isEqualTo(nextPolicyKeyGenerated);
		assertThat(provisionResponse.getResponse().getPolicyStatus()).isEqualTo(ProvisionPolicyStatus.SUCCESS);
		assertThat(provisionResponse.getResponse().getPolicyType()).isEqualTo("MS-EAS-Provisioning-WBXML");
		assertThat(provisionResponse.hasPolicyData()).isTrue();
	}
	
	@Test
	public void testFirstProvisionIsIdempotent() throws Exception {
		long nextPolicyKeyGenerated = 115l;
		OpushUser user = users.jaures;
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();
		
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		deviceDao.removeUnknownDeviceSyncPerm(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(nextPolicyKeyGenerated).once();
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(nextPolicyKeyGenerated).once();
		mocksControl.replay();
		
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ProvisionResponse provisionResponse1 = opClient.provisionStepOne();
		ProvisionResponse provisionResponse2 = opClient.provisionStepOne();

		assertThat(provisionResponse1).isNotNull().isEqualTo(provisionResponse2);
		XMLAssert.assertXMLEqual(
				DOMUtils.createDocFromElement(provisionResponse1.getPolicyDataEl()),
				DOMUtils.createDocFromElement(provisionResponse2.getPolicyDataEl()));
	}

	@Test
	public void acknowledgeIsAllowedOnlyOnPendingPolicyKey() throws Exception {
		long pendingPolicyKey = 123l;
		long acknowledgedPolicyKey = 321l;
		OpushUser user = users.jaures;
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();
		
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		deviceDao.removeUnknownDeviceSyncPerm(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(pendingPolicyKey).once();
		deviceDao.removePolicyKey(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(pendingPolicyKey).once();
		deviceDao.removePolicyKey(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.ACCEPTED)).andReturn(acknowledgedPolicyKey).once();
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(null).once();
		mocksControl.replay();
		
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		opClient.provisionStepOne();
		opClient.provisionStepTwo(pendingPolicyKey);
		ProvisionResponse provisionResponse3 = opClient.provisionStepTwo(acknowledgedPolicyKey);

		assertThat(provisionResponse3).isNotNull();
		assertThat(provisionResponse3.getResponse().getPolicyStatus())
			.isEqualTo(ProvisionPolicyStatus.THE_CLIENT_IS_ACKNOWLEDGING_THE_WRONG_POLICY_KEY);
	}
	
	@Test
	public void testSecondProvisionDoesntSendPolicy() throws Exception {
		OpushUser user = users.jaures;
		long userRegistredPolicyKey = 5410l;
		long nextPolicyKeyGenerated = 16510l;
		
		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(userRegistredPolicyKey).once();
		deviceDao.removePolicyKey(user.user, user.device);
		expectLastCall().once();
		expect(deviceDao.allocateNewPolicyKey(user.user, user.deviceId, PolicyStatus.ACCEPTED)).andReturn(nextPolicyKeyGenerated);
		
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();
		
		mocksControl.replay();
		opushServer.start();
  
		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ProvisionResponse provisionResponse = opClient.provisionStepTwo(userRegistredPolicyKey);

		assertThat(provisionResponse.getResponse().getStatus()).isEqualTo(ProvisionStatus.SUCCESS);
		assertThat(provisionResponse.getResponse().getPolicyKey()).isEqualTo(nextPolicyKeyGenerated);
		assertThat(provisionResponse.getResponse().getPolicyStatus()).isEqualTo(ProvisionPolicyStatus.SUCCESS);
		assertThat(provisionResponse.getResponse().getPolicyType()).isEqualTo("MS-EAS-Provisioning-WBXML");
		assertThat(provisionResponse.hasPolicyData()).isFalse();
	}

	@Test
	public void testSecondProvisionSentCorrectStatusWhenNotExpectedPolicyKey() throws Exception {
		OpushUser user = users.jaures;
		long userRegistredPolicyKey = 4015l;
		long acknowledgingPolicyKey = 5410l;

		expect(deviceDao.getPolicyKey(user.user, user.deviceId, PolicyStatus.PENDING)).andReturn(userRegistredPolicyKey).once();
		
		mockProvisionNeeds(user);
		expect(policyConfigurationProvider.get()).andReturn("fakeConfig").anyTimes();

		mocksControl.replay();
		opushServer.start();

		OPClient opClient = testUtils.buildWBXMLOpushClient(user, opushServer.getHttpPort(), httpClient);
		ProvisionResponse provisionResponse = opClient.provisionStepTwo(acknowledgingPolicyKey);

		assertThat(provisionResponse.getResponse().getStatus()).isEqualTo(ProvisionStatus.SUCCESS);
		assertThat(provisionResponse.getResponse().getPolicyKey()).isNull();
		assertThat(provisionResponse.getResponse().getPolicyStatus()).isEqualTo(ProvisionPolicyStatus.THE_CLIENT_IS_ACKNOWLEDGING_THE_WRONG_POLICY_KEY);
		assertThat(provisionResponse.getResponse().getPolicyType()).isEqualTo("MS-EAS-Provisioning-WBXML");
		assertThat(provisionResponse.hasPolicyData()).isFalse();
	}

	private void mockProvisionNeeds(OpushUser user)
			throws DaoException, AuthFault, CollectionNotFoundException {
		
		userAccessUtils.expectUserLoginFromOpush(user);
		
		testUtils.expectUserCollectionsNeverChange(user, Sets.<CollectionId>newHashSet());
		
		expect(deviceDao.getDevice(user.user, 
				user.deviceId, 
				user.userAgent,
				user.deviceProtocolVersion))
			.andReturn(
				new Device(user.device.getDatabaseId(), user.deviceType, user.deviceId, new Properties(), user.deviceProtocolVersion))
			.anyTimes();
	}
}
