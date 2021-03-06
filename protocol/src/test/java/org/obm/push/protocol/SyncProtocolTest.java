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
package org.obm.push.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.guava.api.Assertions.assertThat;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import javax.xml.transform.TransformerException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.guice.GuiceModule;
import org.obm.guice.GuiceRunner;
import org.obm.push.ProtocolVersion;
import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.BodyPreference;
import org.obm.push.bean.Device;
import org.obm.push.bean.DeviceId;
import org.obm.push.bean.EncodedApplicationData;
import org.obm.push.bean.EncodedSyncCollectionCommandRequest;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.IApplicationData;
import org.obm.push.bean.MSContact;
import org.obm.push.bean.MSEmailBodyType;
import org.obm.push.bean.MimeSupport;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.SyncCollectionCommandRequest;
import org.obm.push.bean.SyncCollectionCommandResponse;
import org.obm.push.bean.SyncCollectionCommandsResponse;
import org.obm.push.bean.SyncCollectionOptions;
import org.obm.push.bean.SyncCollectionResponse;
import org.obm.push.bean.SyncCollectionResponsesResponse;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.SyncStatus;
import org.obm.push.bean.change.SyncCommand;
import org.obm.push.bean.change.client.SyncClientCommands;
import org.obm.push.exception.activesync.ASRequestIntegerFieldException;
import org.obm.push.exception.activesync.NoDocumentException;
import org.obm.push.protocol.bean.ClientSyncRequest;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.protocol.bean.SyncCollection;
import org.obm.push.protocol.bean.SyncRequest;
import org.obm.push.protocol.bean.SyncResponse;
import org.obm.push.protocol.data.ContactDecoder;
import org.obm.push.protocol.data.ContactEncoder;
import org.obm.push.protocol.data.DecoderFactory;
import org.obm.push.protocol.data.EncoderFactory;
import org.obm.push.protocol.data.SyncDecoder;
import org.obm.push.protocol.data.SyncEncoder;
import org.obm.push.protocol.data.ms.MSEmailDecoder;
import org.obm.push.utils.DOMUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Provider;

@GuiceModule(ProtocolModuleTest.class)
@RunWith(GuiceRunner.class)
public class SyncProtocolTest {

	@Inject EncoderFactory encoderFactory;

	
	private Device device;
	private SyncProtocol testee;
	
	@Before
	public void setUp() {
		device = new Device.Factory().create(null, "iPhone", "iOs 5", new DeviceId("my phone"), ProtocolVersion.V121);
		testee = new SyncProtocol(new SyncDecoderTest(), new SyncEncoderTest(), new EncoderFactoryTest());
	}

	@Test
	public void testEncodeValidResponseRespectWindowsMobileOrderingExpectation() throws TransformerException {
		CollectionId collectionId = CollectionId.of(515);
		SyncCollectionResponse collectionResponse = newSyncCollectionResponse(collectionId, SyncStatus.OK);

		String endcodedResponse = encodeResponse(collectionResponse);
		
		assertThat(endcodedResponse).isEqualTo(newCollectionNoChangeResponse(collectionId));
	}
	
	@Test
	public void testEncodeResponseCollectionIdError() throws TransformerException {
		CollectionId collectionId = CollectionId.of(515);
		SyncCollectionResponse collectionResponse = newSyncCollectionResponse(collectionId, SyncStatus.OBJECT_NOT_FOUND);

		String endcodedResponse = encodeResponse(collectionResponse);
		
		assertThat(endcodedResponse).isEqualTo(newCollectionNotFoundResponse(collectionId));
	}
	
	@Test
	public void testEncodeResponseSyncKeyError() throws TransformerException {
		CollectionId collectionId = CollectionId.of(515);
		SyncCollectionResponse collectionResponse = newSyncCollectionResponse(collectionId, SyncStatus.INVALID_SYNC_KEY);

		String endcodedResponse = encodeResponse(collectionResponse);
		
		assertThat(endcodedResponse).isEqualTo(newSyncKeyErrorResponse(collectionId));
	}
	
	private String newCollectionNoChangeResponse(CollectionId collectionId) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Email</Class>" +
							"<SyncKey>123456789</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";
	}

	private String newCollectionNotFoundResponse(CollectionId collectionId) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Email</Class>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>8</Status>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";
	}

	private String newSyncKeyErrorResponse(CollectionId collectionId) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Email</Class>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>3</Status>" +
							"<SyncKey>0</SyncKey>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";
	}

	private String encodeResponse(SyncCollectionResponse collectionResponse) throws TransformerException {
		Document endcodedResponse = testee.encodeResponse(device, syncResponse(collectionResponse));
		return DOMUtils.serialize(endcodedResponse);
	}
	
	private SyncResponse syncResponse(SyncCollectionResponse collectionResponse) {
		return SyncResponse.builder()
				.addResponse(collectionResponse)
				.status(SyncStatus.OK)
				.build();
	}

	private SyncCollectionResponse newSyncCollectionResponse(CollectionId collectionId, SyncStatus status) {
		return SyncCollectionResponse.builder()
				.collectionId(collectionId)
				.syncKey(new SyncKey("123456789"))
				.dataType(PIMDataType.EMAIL)
				.status(status)
				.build();
	}
	
	@Test(expected=NoDocumentException.class)
	public void testDecodeRequestWithNullDocument() {
		Document request = null;
		testee.decodeRequest(request);
	}
	
	@Test
	public void testDecodeRequestCallsSyncDecoderWithExpectedDocument() throws Exception {
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>10</Wait>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>1234-5678</SyncKey>" +
							"<CollectionId>2</CollectionId>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncDecoder syncDecoder = createStrictMock(SyncDecoder.class);
		expect(syncDecoder.decodeSync(request)).andReturn(null);
		replay(syncDecoder);
		SyncProtocol syncProtocol = new SyncProtocol(syncDecoder, null, null);
		
		syncProtocol.decodeRequest(request);

		verify(syncDecoder);
	}

	@Test
	public void testGetWaitWhen0() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>0</Wait>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getWaitInSecond()).isEqualTo(0);
	}

	@Test(expected=ASRequestIntegerFieldException.class)
	public void testGetWaitWhen1000() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>1000</Wait>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		testee.decodeRequest(request);
	}

	public void testPartialRequest() throws Exception {
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Partial/>" +
					"<Wait>1</Wait>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);
		assertThat(syncRequest.getWaitInMinute()).isEqualTo(1);
		assertThat(syncRequest.getCollections()).isEmpty();
	}

	@Test
	public void testSyncCollectionNoValues() throws Exception {
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
						"<Sync>" +
						"<Collections>" +
						"<Collection>" +
						"</Collection>" +
						"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).containsOnly(
				SyncCollection.builder()
				.collectionId(null)
				.dataType(null)
				.syncKey(null)
				.windowSize(null)
				.options(null)
				.build());
	}

	@Test
	public void testSyncCollectionWithValues() throws Exception {
		int windowSize = 55;
		CollectionId syncingCollectionId = CollectionId.of(3);
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Email</Class>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" + syncingCollectionId.asString() + "</CollectionId>" +
							"<WindowSize>" + windowSize + "</WindowSize>" +
							"<DeletesAsMoves />" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		SyncCollection expectedSyncCollection = SyncCollection.builder()
			.collectionId(syncingCollectionId)
			.dataType(PIMDataType.EMAIL)
			.syncKey(new SyncKey(syncingCollectionSyncKey))
			.windowSize(windowSize)
			.deletesAsMoves(true)
			.options(null)
			.build();
		assertThat(syncRequest.getCollections()).containsOnly(expectedSyncCollection);
	}

	@Test
	public void testWindowSizeDifferentInSyncAndCollection() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<WindowSize>150</WindowSize>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<WindowSize>75</WindowSize>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = syncRequest.getCollections().iterator().next();
		assertThat(syncCollection.getWindowSize()).isPresent();
		assertThat(syncCollection.getWindowSize().get()).isEqualTo(75);
	}

	@Test
	public void testOptionAsOptional() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Options>" +
							"</Options>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = syncRequest.getCollections().iterator().next();
		SyncCollectionOptions syncCollectionOptions = syncCollection.getOptions();
		assertThat(syncCollectionOptions.getBodyPreferences()).isEmpty();
		assertThat(syncCollectionOptions.getConflict()).isEqualTo(1);
		assertThat(syncCollectionOptions.getFilterType()).isEqualTo(FilterType.ALL_ITEMS);
		assertThat(syncCollectionOptions.getMimeSupport()).isAbsent();
		assertThat(syncCollectionOptions.getMimeTruncation()).isNull();
	}

	@Test
	public void testOptionToZero() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Options>" +
								"<FilterType>0</FilterType>" +
								"<Conflict>0</Conflict>" +
								"<MIMETruncation>0</MIMETruncation>" +
								"<MIMESupport>0</MIMESupport>" +
							"</Options>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = syncRequest.getCollections().iterator().next();
		SyncCollectionOptions syncCollectionOptions = syncCollection.getOptions();
		assertThat(syncCollectionOptions.getBodyPreferences()).isEmpty();
		assertThat(syncCollectionOptions.getConflict()).isEqualTo(0);
		assertThat(syncCollectionOptions.getFilterType()).isEqualTo(FilterType.ALL_ITEMS);
		assertThat(syncCollectionOptions.getMimeSupport()).contains(MimeSupport.NEVER);
		assertThat(syncCollectionOptions.getMimeTruncation()).isEqualTo(0);
	}

	@Test
	public void testOptionToNonZero() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Options>" +
								"<FilterType>5</FilterType>" +
								"<Conflict>1</Conflict>" +
								"<MIMETruncation>8</MIMETruncation>" +
								"<MIMESupport>2</MIMESupport>" +
							"</Options>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = syncRequest.getCollections().iterator().next();
		SyncCollectionOptions syncCollectionOptions = syncCollection.getOptions();
		assertThat(syncCollectionOptions.getBodyPreferences()).isEmpty();
		assertThat(syncCollectionOptions.getConflict()).isEqualTo(1);
		assertThat(syncCollectionOptions.getFilterType()).isEqualTo(FilterType.ONE_MONTHS_BACK);
		assertThat(syncCollectionOptions.getMimeSupport()).contains(MimeSupport.ALWAYS);
		assertThat(syncCollectionOptions.getMimeTruncation()).isEqualTo(8);
	}

	@Test
	public void testOptionsBodyPreferencesMinSpecValues() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Options>" +
								"<BodyPreference>" +
									"<Type>1</Type>" +
									"<TruncationSize>0</TruncationSize>" +
									"<AllOrNone>0</AllOrNone>" +
								"</BodyPreference>" +
							"</Options>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = syncRequest.getCollections().iterator().next();
		SyncCollectionOptions syncCollectionOptions = syncCollection.getOptions();
		assertThat(syncCollectionOptions.getBodyPreferences()).containsOnly(bodyPreference(1, 0, false));
	}

	@Test
	public void testOptionsBodyPreferencesMaxSpecValues() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		int maxSpecTruncationSize = 2147483647;
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Options>" +
								"<BodyPreference>" +
									"<Type>1</Type>" +
									"<TruncationSize>0</TruncationSize>" +
									"<AllOrNone>0</AllOrNone>" +
								"</BodyPreference>" +
								"<BodyPreference>" +
									"<Type>8</Type>" +
									"<TruncationSize>" + maxSpecTruncationSize + "</TruncationSize>" +
									"<AllOrNone>1</AllOrNone>" +
								"</BodyPreference>" +
							"</Options>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = syncRequest.getCollections().iterator().next();
		SyncCollectionOptions syncCollectionOptions = syncCollection.getOptions();
		assertThat(syncCollectionOptions.getBodyPreferences()).containsOnly(
				bodyPreference(1, 0, false),
				bodyPreference(8, maxSpecTruncationSize, true));
	}

	@Test
	public void testOptionsBodyPreferencesTwoEntries() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		int maxSpecTruncationSize = 2147483647;
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Options>" +
								"<BodyPreference>" +
									"<Type>8</Type>" +
									"<TruncationSize>" + maxSpecTruncationSize + "</TruncationSize>" +
									"<AllOrNone>1</AllOrNone>" +
								"</BodyPreference>" +
							"</Options>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);

		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = syncRequest.getCollections().iterator().next();
		SyncCollectionOptions syncCollectionOptions = syncCollection.getOptions();
		assertThat(syncCollectionOptions.getBodyPreferences()).containsOnly(bodyPreference(8, maxSpecTruncationSize, true));
	}

	@Test
	public void testCommandsAddWithoutApplicationData() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Add>" +
									"<ServerId>123</ServerId>" +
									"<ClientId>13579</ClientId>" +
								"</Add>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");


		SyncRequest syncRequest = testee.decodeRequest(request);
		
		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = Iterables.getOnlyElement(syncRequest.getCollections());
		assertThat(Iterables.getOnlyElement(syncCollection.getCommands()).getEncodedApplicationData()).isNull();
	}

	@Test
	public void testCommandsChangeWithoutApplicationData() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Change>" +
									"<ServerId>123</ServerId>" +
								"</Change>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncRequest syncRequest = testee.decodeRequest(request);
		
		assertThat(syncRequest.getCollections()).hasSize(1);
		SyncCollection syncCollection = Iterables.getOnlyElement(syncRequest.getCollections());
		assertThat(Iterables.getOnlyElement(syncCollection.getCommands()).getEncodedApplicationData()).isNull();
	}

	@Test
	public void testCommandsAdd() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Add>" +
									"<ServerId>3:123</ServerId>" +
									"<ClientId>13579</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Add>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		MSContact expectedMSContact = new MSContact();
		expectedMSContact.setEmail1Address("opush@obm.org");
		expectedMSContact.setFileAs("Dobney, JoLynn Julie");
		expectedMSContact.setFirstName("JoLynn");
		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId("13579")
				.type(SyncCommand.ADD)
				.serverId(CollectionId.of(3).serverId(123))
				.applicationData(expectedMSContact)
				.build();

		SyncResponse syncResponse = testee.decodeResponse(request);

		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange);
	}

	@Test
	public void testCommandsTwoAdd() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Add>" +
									"<ServerId>3:123</ServerId>" +
									"<ClientId>13579</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Add>" +
								"<Add>" +
									"<ServerId>3:456</ServerId>" +
									"<ClientId>02468</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush2@obm.org\"&lt;opush2@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney2, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn2</FirstName>" +
									"</ApplicationData>" +
								"</Add>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		MSContact expectedMSContact = new MSContact();
		expectedMSContact.setEmail1Address("opush@obm.org");
		expectedMSContact.setFileAs("Dobney, JoLynn Julie");
		expectedMSContact.setFirstName("JoLynn");
		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId("13579")
				.type(SyncCommand.ADD)
				.serverId(CollectionId.of(3).serverId(123))
				.applicationData(expectedMSContact)
				.build();
		
		MSContact expectedMSContact2 = new MSContact();
		expectedMSContact2.setEmail1Address("opush2@obm.org");
		expectedMSContact2.setFileAs("Dobney2, JoLynn Julie");
		expectedMSContact2.setFirstName("JoLynn2");
		SyncCollectionCommandResponse expectedSyncCollectionChange2 = SyncCollectionCommandResponse.builder()
				.clientId("02468")
				.type(SyncCommand.ADD)
				.serverId(CollectionId.of(3).serverId(456))
				.applicationData(expectedMSContact2)
				.build();

		SyncResponse syncResponse = testee.decodeResponse(request);

		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse collection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(collection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange, expectedSyncCollectionChange2);
	}

	@Test
	public void testCommandsChange() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>10</Wait>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Change>" +
									"<ServerId>3:123</ServerId>" +
									"<ClientId>13579</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Change>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		MSContact expectedMSContact = new MSContact();
		expectedMSContact.setEmail1Address("opush@obm.org");
		expectedMSContact.setFileAs("Dobney, JoLynn Julie");
		expectedMSContact.setFirstName("JoLynn");
		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId("13579")
				.type(SyncCommand.CHANGE)
				.serverId(CollectionId.of(3).serverId(123))
				.applicationData(expectedMSContact)
				.build();

		SyncResponse syncRequest = testee.decodeResponse(request);
		
		assertThat(syncRequest.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncRequest.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange);
	}

	@Test
	public void testCommandsTwoChange() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Change>" +
									"<ServerId>3:123</ServerId>" +
									"<ClientId>13579</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Change>" +
								"<Change>" +
									"<ServerId>3:456</ServerId>" +
									"<ClientId>02468</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush2@obm.org\"&lt;opush2@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney2, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn2</FirstName>" +
									"</ApplicationData>" +
								"</Change>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		MSContact expectedMSContact = new MSContact();
		expectedMSContact.setEmail1Address("opush@obm.org");
		expectedMSContact.setFileAs("Dobney, JoLynn Julie");
		expectedMSContact.setFirstName("JoLynn");
		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId("13579")
				.type(SyncCommand.CHANGE)
				.serverId(CollectionId.of(3).serverId(123))
				.applicationData(expectedMSContact)
				.build();
		
		MSContact expectedMSContact2 = new MSContact();
		expectedMSContact2.setEmail1Address("opush2@obm.org");
		expectedMSContact2.setFileAs("Dobney2, JoLynn Julie");
		expectedMSContact2.setFirstName("JoLynn2");
		SyncCollectionCommandResponse expectedSyncCollectionChange2 = SyncCollectionCommandResponse.builder()
				.clientId("02468")
				.type(SyncCommand.CHANGE)
				.serverId(CollectionId.of(3).serverId(456))
				.applicationData(expectedMSContact2)
				.build();
		

		SyncResponse syncResponse = testee.decodeResponse(request);
		
		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange, expectedSyncCollectionChange2);
	}

	@Test
	public void testCommandsFetch() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" + syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Fetch>" +
									"<ServerId>3:123</ServerId>" +
								"</Fetch>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.FETCH)
				.serverId(CollectionId.of(3).serverId(123))
				.applicationData(null)
				.build();

		SyncResponse syncResponse = testee.decodeResponse(request);

		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange);
		assertThat(syncCollection.getFetchIds()).containsOnly(CollectionId.of(3).serverId(123));
	}

	@Test
	public void testCommandsTwoFetch() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Fetch>" +
									"<ServerId>1:123</ServerId>" +
								"</Fetch>" +
								"<Fetch>" +
									"<ServerId>1:456</ServerId>" +
								"</Fetch>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.FETCH)
				.serverId(CollectionId.of(1).serverId(123))
				.applicationData(null)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionChange2 = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.FETCH)
				.serverId(CollectionId.of(1).serverId(456))
				.applicationData(null)
				.build();

		SyncResponse syncResponse = testee.decodeResponse(request);
		
		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange, expectedSyncCollectionChange2);
		assertThat(syncCollection.getFetchIds()).containsOnly(CollectionId.of(1).serverId(123), CollectionId.of(1).serverId(456));
	}

	@Test
	public void testCommandsDelete() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Delete>" +
									"<ServerId>3:123</ServerId>" +
								"</Delete>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.DELETE)
				.serverId(CollectionId.of(3).serverId(123))
				.applicationData(null)
				.build();

		SyncResponse syncResponse = testee.decodeResponse(request);
		
		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange);
	}

	@Test
	public void testCommandsTwoDelete() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Delete>" +
									"<ServerId>3:123</ServerId>" +
								"</Delete>" +
								"<Delete>" +
									"<ServerId>3:456</ServerId>" +
								"</Delete>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.DELETE)
				.serverId(CollectionId.of(3).serverId(123))
				.applicationData(null)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionChange2 = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.DELETE)
				.serverId(CollectionId.of(3).serverId(456))
				.applicationData(null)
				.build();

		SyncResponse syncResponse = testee.decodeResponse(request);

		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(expectedSyncCollectionChange, expectedSyncCollectionChange2);
	}

	@Test
	public void testCommandsAddChangeFetchDelete() throws Exception {
		int syncingCollectionId = 3;
		String syncingCollectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncingCollectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +syncingCollectionId + "</CollectionId>" +
							"<Commands>" +
								"<Add>" +
									"<ServerId>3:12</ServerId>" +
									"<ClientId>120</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Add>" +
								"<Add>" +
									"<ServerId>3:13</ServerId>" +
									"<ClientId>130</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Add>" +
								"<Change>" +
									"<ServerId>3:34</ServerId>" +
									"<ClientId>340</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Change>" +
								"<Change>" +
									"<ServerId>3:35</ServerId>" +
									"<ClientId>350</ClientId>" +
									"<ApplicationData>" +
										"<Email1Address>\"opush@obm.org\"&lt;opush@obm.org&gt;</Email1Address>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
									"</ApplicationData>" +
								"</Change>" +
								"<Fetch>" +
									"<ServerId>3:56</ServerId>" +
								"</Fetch>" +
								"<Fetch>" +
									"<ServerId>3:57</ServerId>" +
								"</Fetch>" +
								"<Delete>" +
									"<ServerId>3:78</ServerId>" +
								"</Delete>" +
								"<Delete>" +
									"<ServerId>3:79</ServerId>" +
								"</Delete>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");
		MSContact expectedMSContact = new MSContact();
		expectedMSContact.setEmail1Address("opush@obm.org");
		expectedMSContact.setFileAs("Dobney, JoLynn Julie");
		expectedMSContact.setFirstName("JoLynn");

		SyncCollectionCommandResponse expectedSyncCollectionAdd = SyncCollectionCommandResponse.builder()
				.clientId("120")
				.type(SyncCommand.ADD)
				.serverId(CollectionId.of(3).serverId(12))
				.applicationData(expectedMSContact)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionAdd2 = SyncCollectionCommandResponse.builder()
				.clientId("130")
				.type(SyncCommand.ADD)
				.serverId(CollectionId.of(3).serverId(13))
				.applicationData(expectedMSContact)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionChange = SyncCollectionCommandResponse.builder()
				.clientId("340")
				.type(SyncCommand.CHANGE)
				.serverId(CollectionId.of(3).serverId(34))
				.applicationData(expectedMSContact)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionChange2 = SyncCollectionCommandResponse.builder()
				.clientId("350")
				.type(SyncCommand.CHANGE)
				.serverId(CollectionId.of(3).serverId(35))
				.applicationData(expectedMSContact)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionFetch = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.FETCH)
				.serverId(CollectionId.of(3).serverId(56))
				.applicationData(null)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionFetch2 = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.FETCH)
				.serverId(CollectionId.of(3).serverId(57))
				.applicationData(null)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionDelete = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.DELETE)
				.serverId(CollectionId.of(3).serverId(78))
				.applicationData(null)
				.build();
		SyncCollectionCommandResponse expectedSyncCollectionDelete2 = SyncCollectionCommandResponse.builder()
				.clientId(null)
				.type(SyncCommand.DELETE)
				.serverId(CollectionId.of(3).serverId(79))
				.applicationData(null)
				.build();
		
		SyncResponse syncResponse = testee.decodeResponse(request);
		
		assertThat(syncResponse.getCollectionResponses()).hasSize(1);
		SyncCollectionResponse syncCollection = Iterables.getOnlyElement(syncResponse.getCollectionResponses());
		assertThat(syncCollection.getCommands().getCommands()).containsOnly(
				expectedSyncCollectionAdd, expectedSyncCollectionAdd2,
				expectedSyncCollectionChange, expectedSyncCollectionChange2,
				expectedSyncCollectionFetch,  expectedSyncCollectionFetch2,
				expectedSyncCollectionDelete, expectedSyncCollectionDelete2);
		assertThat(syncCollection.getFetchIds()).containsOnly(CollectionId.of(3).serverId(56), CollectionId.of(3).serverId(57));
	}

	@Test
	public void testEncodeForPartialNoCollectionSyncRequest() throws Exception {
		String expected = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Partial>1</Partial>" +
					"<Wait>30</Wait>" +
				"</Sync>";
		
		Document encodedRequest = testee.encodeRequest(ClientSyncRequest.builder().partial(true).waitInMinute(30).build());
		
		assertThat(DOMUtils.serialize(encodedRequest)).isEqualTo(expected);
	}

	@Test
	public void testDecodeForPartialNoCollectionSyncRequest() throws Exception {
		String request = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Partial>1</Partial>" +
					"<Wait>30</Wait>" +
				"</Sync>";
		
		SyncRequest decodedSyncRequest = testee.decodeRequest(DOMUtils.parse(request));
		assertThat(decodedSyncRequest).isEqualTo(SyncRequest.builder().partial(true).waitInMinute(30).build());
	}
	
	@Test
	public void testEncodeSimpleSyncRequest() throws Exception {
		CollectionId syncingCollectionId = CollectionId.of(2);
		String request = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>0</Wait>" +
					"<WindowSize>12</WindowSize>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>1234-5678</SyncKey>" +
							"<CollectionId>" + syncingCollectionId.asString() + "</CollectionId>" +
							"<WindowSize>12</WindowSize>" +
							"<Options><FilterType>2</FilterType><Conflict>1</Conflict></Options>" +
							"<Commands>" +
								"<Delete>" +
									"<ServerId>" + syncingCollectionId.serverId(79).asString() + "</ServerId>" +
								"</Delete>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";
		
		
		Document encodedRequest = testee.encodeRequest(
				ClientSyncRequest.builder().waitInMinute(0).windowSize(12)
					.addCollection(AnalysedSyncCollection.builder()
									.dataType(PIMDataType.CONTACTS)
									.syncKey(new SyncKey("1234-5678"))
									.collectionId(syncingCollectionId)
									.windowSize(12)
									.options(SyncCollectionOptions.builder().filterType(FilterType.THREE_DAYS_BACK).conflict(1).build())
									.command(SyncCollectionCommandRequest.builder().serverId(syncingCollectionId.serverId(79)).type(SyncCommand.DELETE).build())
									.build())
					.build());
		
		assertThat(request).isEqualTo(DOMUtils.serialize(encodedRequest));
	}
	
	@Test
	public void testDecodeLoopForSimpleSyncRequest() throws Exception {
		CollectionId syncingCollectionId = CollectionId.of(2);
		String request = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>0</Wait>" +
					"<WindowSize>12</WindowSize>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>1234-5678</SyncKey>" +
							"<CollectionId>" + syncingCollectionId.asString() + "</CollectionId>" +
							"<WindowSize>12</WindowSize>" +
							"<Options><FilterType>2</FilterType><Conflict>1</Conflict></Options>" +
							"<Commands>" +
								"<Delete>" +
									"<ServerId>" + syncingCollectionId.serverId(79).asString() + "</ServerId>" +
								"</Delete>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";
		
		
		SyncRequest decodedSyncRequest = testee.decodeRequest(DOMUtils.parse(request));
		
		assertThat(decodedSyncRequest).isEqualTo(SyncRequest.builder().waitInMinute(0).windowSize(12)
				.addCollection(SyncCollection.builder()
						.dataType(PIMDataType.CONTACTS)
						.syncKey(new SyncKey("1234-5678"))
						.collectionId(syncingCollectionId)
						.windowSize(12)
						.options(SyncCollectionOptions.builder().filterType(FilterType.THREE_DAYS_BACK).conflict(1).build())
						.command(EncodedSyncCollectionCommandRequest.builder().serverId(syncingCollectionId.serverId(79)).type(SyncCommand.DELETE).build())
						.build())
					.build());
	}

	@Test
	public void testEncodeSyncRequestWithData() throws Exception {
		CollectionId syncingCollectionId = CollectionId.of(2);
		
		String expectedResult = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>0</Wait>" +
					"<WindowSize>12</WindowSize>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>1234-5678</SyncKey>" +
							"<CollectionId>" + syncingCollectionId.asString() + "</CollectionId>" +
							"<WindowSize>12</WindowSize>" +
							"<Options><FilterType>2</FilterType><Conflict>1</Conflict></Options>" +
							"<Commands>" +
								"<Change>" +
									"<ServerId>" + syncingCollectionId.serverId(35).asString() + "</ServerId>" +
									"<ClientId>350</ClientId>" +
									"<ApplicationData>" +
										"<Contacts:FileAs>Dobney, JoLynn Julie</Contacts:FileAs>" +
										"<Contacts:FirstName>JoLynn</Contacts:FirstName>" +
										"<Contacts:Email1Address>opush@obm.org</Contacts:Email1Address>" +
									"</ApplicationData>" +
								"</Change>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";
		
		MSContact msContact = new MSContact();
		msContact.setFileAs("Dobney, JoLynn Julie");
		msContact.setFirstName("JoLynn");
		msContact.setEmail1Address("opush@obm.org");
		
		Document encodedRequest = testee.encodeRequest(
				ClientSyncRequest.builder()
					.waitInMinute(0)
					.windowSize(12)
					.addCollection(
						AnalysedSyncCollection.builder()
							.dataType(PIMDataType.CONTACTS)
							.syncKey(new SyncKey("1234-5678"))
							.collectionId(syncingCollectionId)
							.windowSize(12)
							.options(SyncCollectionOptions.builder().filterType(FilterType.THREE_DAYS_BACK).conflict(1).build())
							.command(SyncCollectionCommandRequest.builder()
									.type(SyncCommand.CHANGE)
									.serverId(syncingCollectionId.serverId(35))
									.clientId("350")
									.applicationData(msContact)
									.build())
							.build())
					.build());
		
		assertThat(DOMUtils.serialize(encodedRequest)).isXmlEqualTo(expectedResult);
	}

	@Test
	public void testDecodeSyncRequestWithData() throws Exception {
		CollectionId syncingCollectionId = CollectionId.of(2);
		String request = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Wait>0</Wait>" +
					"<WindowSize>12</WindowSize>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>1234-5678</SyncKey>" +
							"<CollectionId>" + syncingCollectionId.asString() + "</CollectionId>" +
							"<WindowSize>12</WindowSize>" +
							"<Options><FilterType>2</FilterType><Conflict>1</Conflict></Options>" +
							"<Commands>" +
								"<Change>" +
									"<ServerId>" + syncingCollectionId.serverId(35).asString()+ "</ServerId>" +
									"<ClientId>350</ClientId>" +
									"<ApplicationData>" +
										"<FileAs>Dobney, JoLynn Julie</FileAs>" +
										"<FirstName>JoLynn</FirstName>" +
										"<Email1Address>opush@obm.org</Email1Address>" +
									"</ApplicationData>" +
								"</Change>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";
		
		SyncRequest decodedSyncRequest = testee.decodeRequest(DOMUtils.parse(request));
		
		final MSContact msContact = new MSContact();
		msContact.setFileAs("Dobney, JoLynn Julie");
		msContact.setFirstName("JoLynn");
		msContact.setEmail1Address("opush@obm.org");
		
		SyncRequest expected = SyncRequest.builder()
			.waitInMinute(0)
			.windowSize(12)
			.addCollection(
				SyncCollection.builder()
					.dataType(PIMDataType.CONTACTS)
					.syncKey(new SyncKey("1234-5678"))
					.collectionId(syncingCollectionId)
					.windowSize(12)
					.options(SyncCollectionOptions.builder().filterType(FilterType.THREE_DAYS_BACK).conflict(1).build())
					.command(EncodedSyncCollectionCommandRequest.builder()
							.type(SyncCommand.CHANGE)
							.serverId(syncingCollectionId.serverId(35))
							.clientId("350")
							.applicationData(new EncodedApplicationData(null) {
								@Override
								public IApplicationData decode(Function<Element, IApplicationData> decoder) {
									return msContact;
								}
							})
							.build())
					.build())
			.build();
		assertThat(decodedSyncRequest).isEqualTo(expected);
	}
	
	@Test
	public void testDecodePartialErrorResponse() throws Exception {
		String response = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Status>13</Status>" +
				"</Sync>";

		SyncResponse decodedSyncResponse = testee.decodeResponse(DOMUtils.parse(response));
		
		assertThat(decodedSyncResponse.getStatus()).isEqualTo(SyncStatus.PARTIAL_REQUEST);
	}

	@Test
	public void testDecodeProvisionErrorResponse() throws Exception {
		String response = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Status>11</Status>" +
				"</Sync>";

		SyncResponse decodedSyncResponse = testee.decodeResponse(DOMUtils.parse(response));
		
		assertThat(decodedSyncResponse.getStatus()).isEqualTo(SyncStatus.NOT_YET_PROVISIONNED);
	}

	private BodyPreference bodyPreference(Integer bodyType, Integer truncationSize, Boolean allOrNone) {
		return BodyPreference.builder()
			.bodyType(MSEmailBodyType.getValueOf(bodyType))
			.truncationSize(truncationSize)
			.allOrNone(allOrNone)
			.build();
	}
	
	static class SyncDecoderTest extends SyncDecoder {

		protected SyncDecoderTest() {
			super(new DecoderFactoryTest());
		}
	}
	
	static class SyncEncoderTest extends SyncEncoder {
		public SyncEncoderTest() {
			super(new EncoderFactoryTest());
		}
	}
	
	static class DecoderFactoryTest extends DecoderFactory {

		protected DecoderFactoryTest() {
			super(null,
					new Provider<ContactDecoder>() {
						@Override
						public ContactDecoder get() {
							return new ContactDecoder(null, null);
						}
					},
					null,
					new Provider<MSEmailDecoder>() {
						@Override
						public MSEmailDecoder get() {
							return new MSEmailDecoder(null){};
						}
					});
		}
	}
	
	static class EncoderFactoryTest extends EncoderFactory {

		protected EncoderFactoryTest() {
			super(null,
					new Provider<ContactEncoder>() {
						@Override
						public ContactEncoder get() {
							return new ContactEncoder(){};
						}
					},
					null, null, null);
		}
	}

	@Test
	public void encodeAddCommand() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		MSContact contact = new MSContact();
		contact.setEmail1Address("opush@obm.org");
		contact.setFileAs("Dobney, JoLynn Julie");
		contact.setFirstName("JoLynn");
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.commands(SyncCollectionCommandsResponse.builder()
							.addCommand(SyncCollectionCommandResponse.builder()
									.type(SyncCommand.ADD)
									.serverId(serverId)
									.applicationData(contact)
									.build())
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Commands>" +
								"<Add>" +
									"<ServerId>" + serverId.asString() + "</ServerId>" +
									"<ApplicationData>" +
										"<Contacts:FileAs>Dobney, JoLynn Julie</Contacts:FileAs>" +
										"<Contacts:FirstName>JoLynn</Contacts:FirstName>" +
										"<Contacts:Email1Address>opush@obm.org</Contacts:Email1Address>" +
									"</ApplicationData>" +
								"</Add>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isXmlEqualTo(expectedResponse);
	}

	@Test
	public void encodeDeleteCommand() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		MSContact contact = new MSContact();
		contact.setEmail1Address("opush@obm.org");
		contact.setFileAs("Dobney, JoLynn Julie");
		contact.setFirstName("JoLynn");
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.commands(SyncCollectionCommandsResponse.builder()
							.addCommand(SyncCollectionCommandResponse.builder()
									.type(SyncCommand.DELETE)
									.serverId(serverId)
									.build())
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Commands>" +
								"<Delete>" +
									"<ServerId>" + serverId.asString() + "</ServerId>" +
								"</Delete>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isEqualTo(expectedResponse);
	}

	@Test
	public void encodeChangeCommand() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		String clientId = "123";
		MSContact contact = new MSContact();
		contact.setEmail1Address("opush@obm.org");
		contact.setFileAs("Dobney, JoLynn Julie");
		contact.setFirstName("JoLynn");
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.commands(SyncCollectionCommandsResponse.builder()
							.addCommand(SyncCollectionCommandResponse.builder()
									.type(SyncCommand.CHANGE)
									.serverId(serverId)
									.clientId(clientId)
									.applicationData(contact)
									.build())
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Commands>" +
								"<Change>" +
									"<ServerId>" + serverId.asString() + "</ServerId>" +
									"<ApplicationData>" +
										"<Contacts:FileAs>Dobney, JoLynn Julie</Contacts:FileAs>" +
										"<Contacts:FirstName>JoLynn</Contacts:FirstName>" +
										"<Contacts:Email1Address>opush@obm.org</Contacts:Email1Address>" +
									"</ApplicationData>" +
								"</Change>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isEqualTo(expectedResponse);
	}

	@Test
	public void encodeAddResponse() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		String clientId = "123";
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.responses(SyncCollectionResponsesResponse.builder()
							.adds(ImmutableList.of(new SyncClientCommands.Add(clientId, serverId, SyncStatus.CONVERSATION_ERROR_OR_INVALID_ITEM)))
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Responses>" +
								"<Add>" +
									"<ClientId>" + clientId + "</ClientId>" +
									"<ServerId>" + serverId.asString() + "</ServerId>" +
									"<Status>6</Status>" +
								"</Add>" +
							"</Responses>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isEqualTo(expectedResponse);
	}

	@Test
	public void encodeDeleteResponse() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.responses(SyncCollectionResponsesResponse.builder()
							.deletions(ImmutableList.of(new SyncClientCommands.Deletion(serverId, SyncStatus.CONVERSATION_ERROR_OR_INVALID_ITEM)))
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Responses>" +
								"<Delete>" +
								"<ServerId>" + serverId.asString() + "</ServerId>" +
									"<Status>6</Status>" +
								"</Delete>" +
							"</Responses>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isEqualTo(expectedResponse);
	}

	@Test
	public void encodeChangeResponse() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.responses(SyncCollectionResponsesResponse.builder()
							.updates(ImmutableList.of(new SyncClientCommands.Update(serverId, SyncStatus.CONVERSATION_ERROR_OR_INVALID_ITEM)))
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Responses>" +
								"<Change>" +
									"<ServerId>" + serverId.asString() + "</ServerId>" +
									"<Status>6</Status>" +
								"</Change>" +
							"</Responses>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isEqualTo(expectedResponse);
	}

	@Test
	public void encodeFetchResponse() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		MSContact contact = new MSContact();
		contact.setEmail1Address("opush@obm.org");
		contact.setFileAs("Dobney, JoLynn Julie");
		contact.setFirstName("JoLynn");
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.responses(SyncCollectionResponsesResponse.builder()
							.fetchs(ImmutableList.of(new SyncClientCommands.Fetch(serverId, SyncStatus.CONVERSATION_ERROR_OR_INVALID_ITEM, contact)))
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Responses>" +
								"<Fetch>" +
									"<ServerId>" + serverId.asString() + "</ServerId>" +
									"<Status>6</Status>" +
									"<ApplicationData>" +
										"<Contacts:FileAs>Dobney, JoLynn Julie</Contacts:FileAs>" +
										"<Contacts:FirstName>JoLynn</Contacts:FirstName>" +
										"<Contacts:Email1Address>opush@obm.org</Contacts:Email1Address>" +
									"</ApplicationData>" +
								"</Fetch>" +
							"</Responses>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isEqualTo(expectedResponse);
	}

	@Test
	public void encodeResponseShouldEncodeResponsesThenCommands() throws Exception {
		CollectionId collectionId = CollectionId.of(3);
		SyncKey syncKey = new SyncKey("eb4ff3bb-ef52-4daf-b040-7fb81ec51141");
		ServerId serverId = collectionId.serverId(6);
		String clientId = "123";
		MSContact contact = new MSContact();
		contact.setEmail1Address("opush@obm.org");
		contact.setFileAs("Dobney, JoLynn Julie");
		contact.setFirstName("JoLynn");
		
		SyncResponse syncResponse = SyncResponse.builder()
			.status(SyncStatus.OK)
			.addResponse(SyncCollectionResponse.builder()
					.collectionId(collectionId)
					.status(SyncStatus.OK)
					.syncKey(syncKey)
					.dataType(PIMDataType.CONTACTS)
					.moreAvailable(false)
					.responses(SyncCollectionResponsesResponse.builder()
							.updates(ImmutableList.of(new SyncClientCommands.Update(serverId, SyncStatus.OK)))
							.build())
					.commands(SyncCollectionCommandsResponse.builder()
							.addCommand(SyncCollectionCommandResponse.builder()
									.type(SyncCommand.CHANGE)
									.serverId(serverId)
									.clientId(clientId)
									.applicationData(contact)
									.build())
							.build())
					.build())
			.build();
		
		String expectedResponse = 
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<Class>Contacts</Class>" +
							"<SyncKey>" + syncKey.getSyncKey() + "</SyncKey>" +
							"<CollectionId>" + collectionId.asString() + "</CollectionId>" +
							"<Status>1</Status>" +
							"<Responses>" +
							"<Change>" +
								"<ServerId>" + serverId.asString() + "</ServerId>" +
								"<Status>1</Status>" +
							"</Change>" +
							"</Responses>" +
							"<Commands>" +
								"<Change>" +
									"<ServerId>" + serverId.asString() + "</ServerId>" +
									"<ApplicationData>" +
										"<Contacts:FileAs>Dobney, JoLynn Julie</Contacts:FileAs>" +
										"<Contacts:FirstName>JoLynn</Contacts:FirstName>" +
										"<Contacts:Email1Address>opush@obm.org</Contacts:Email1Address>" +
									"</ApplicationData>" +
								"</Change>" +
							"</Commands>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>";

		Document response = testee.encodeResponse(device, syncResponse);
		assertThat(DOMUtils.serialize(response)).isEqualTo(expectedResponse);
	}
}
