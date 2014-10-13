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
package org.obm.push.protocol.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.Properties;

import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.BodyPreference;
import org.obm.push.bean.Credentials;
import org.obm.push.bean.Device;
import org.obm.push.bean.DeviceId;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.ICollectionPathHelper;
import org.obm.push.bean.MSEmailBodyType;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.Sync;
import org.obm.push.bean.SyncCollectionCommandsResponse;
import org.obm.push.bean.SyncCollectionOptions;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.SyncStatus;
import org.obm.push.bean.User;
import org.obm.push.bean.User.Factory;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.exception.activesync.ASRequestIntegerFieldException;
import org.obm.push.exception.activesync.ASRequestStringFieldException;
import org.obm.push.exception.activesync.PartialException;
import org.obm.push.exception.activesync.ServerErrorException;
import org.obm.push.protocol.bean.SyncRequest;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.SyncedCollectionDao;
import org.obm.push.utils.DOMUtils;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableList;


public class SyncAnalyserTest {
	
	private Device device;
	private UserDataRequest udr;
	private User user;
	private Credentials credentials;
	private String collectionPath;
	private int collectionId;
	
	private IMocksControl mocks;
	private SyncedCollectionDao syncedCollectionDao;
	private CollectionDao collectionDao;
	private ICollectionPathHelper collectionPathHelper;

	private SyncDecoder syncDecoder;
	private SyncAnalyser syncAnalyser;

	@Before
	public void setUp() throws Exception {
		device = new Device(1, "devType", new DeviceId("devId"), new Properties(), null);
		user = Factory.create().createUser("adrien@test.tlse.lngr", "email@test.tlse.lngr", "Adrien");
		credentials = new Credentials(user, "test".toCharArray());
		udr = new UserDataRequest(credentials, "Sync", device);
		collectionPath = "INBOX";
		collectionId = 5;

		mocks = createControl();
		syncedCollectionDao = mocks.createMock(SyncedCollectionDao.class);
		collectionDao = mocks.createMock(CollectionDao.class);
		collectionPathHelper = mocks.createMock(ICollectionPathHelper.class);

		syncDecoder = new SyncDecoder(null);
		syncAnalyser = new SyncAnalyser(syncedCollectionDao, collectionDao, collectionPathHelper, null);
		
		expect(collectionDao.getCollectionPath(collectionId)).andReturn(collectionPath).anyTimes();
		expect(collectionPathHelper.recognizePIMDataType(collectionPath)).andReturn(PIMDataType.EMAIL).anyTimes();
	}
	
	@Test
	public void testRequestOptionsAreStored() throws Exception {
		Document request = buildRequestWithOptions("0", 
				"<Options>" +
					"<FilterType>2</FilterType>" +
					"<Conflict>1</Conflict>" +
					"<MIMESupport>1</MIMESupport>" +
					"<MIMETruncation>100</MIMETruncation>" +
					"<BodyPreference>" +
						"<Type>1</Type>" +
					"</BodyPreference>" +
					"<BodyPreference>" +
						"<Type>2</Type>" +
					"</BodyPreference>" +
					"<BodyPreference>" +
						"<Type>4</Type>" +
						"<TruncationSize>5120</TruncationSize>" +
					"</BodyPreference>" +
				"</Options>");
		
		SyncCollectionOptions requestOptionsToStore = SyncCollectionOptions.builder()
				.filterType(FilterType.THREE_DAYS_BACK)
				.conflict(1)
				.mimeSupport(1)
				.mimeTruncation(100)
				.bodyPreferences(ImmutableList.<BodyPreference> of(
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.PlainText)
						.build(),
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.HTML)
						.build(),
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.MIME)
						.truncationSize(5120)
						.build()
					))
				.build();
		AnalysedSyncCollection requestSyncCollectionToStore = buildRequestCollectionWithOptions(requestOptionsToStore, "0");
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(udr.getUser(), device, requestSyncCollectionToStore);
		expectLastCall().once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		Sync analysed = syncAnalyser.analyseSync(udr, syncRequest);
		mocks.verify();
		
		assertThat(analysed.getCollections()).containsOnly(requestSyncCollectionToStore);
	}
	
	@Test
	public void testRequestWithOnlyFilterTypeOptionsStoreOthersWithDefaultValue() throws Exception {
		Document request = buildRequestWithOptions("0", 
				"<Options>" +
					"<FilterType>2</FilterType>" +
				"</Options>");
		
		SyncCollectionOptions requestOptionsToStore = SyncCollectionOptions.builder()
				.filterType(FilterType.THREE_DAYS_BACK)
				.build();

		AnalysedSyncCollection requestSyncCollectionToStore = buildRequestCollectionWithOptions(requestOptionsToStore, "0");
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(udr.getUser(), device, requestSyncCollectionToStore);
		expectLastCall().once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		Sync analysed = syncAnalyser.analyseSync(udr, syncRequest);
		mocks.verify();
		
		assertThat(analysed.getCollections()).containsOnly(requestSyncCollectionToStore);
	}
	
	@Test
	public void testNoRequestOptionsTakeTheDefaultOneIfNoPrevious() throws Exception {
		SyncCollectionOptions toStoreOptions = SyncCollectionOptions.builder()
				.filterType(FilterType.THREE_DAYS_BACK)
				.conflict(1)
				.mimeSupport(null)
				.mimeTruncation(null)
				.deletesAsMoves(true)
				.build();
		AnalysedSyncCollection toStoreSyncCollection = buildRequestCollectionWithOptions(toStoreOptions, "156");

		Document requestWithoutOptions = buildRequestWithoutOptions("156");
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(udr.getUser(), device, toStoreSyncCollection);
		expectLastCall().once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(requestWithoutOptions);
		Sync analysed = syncAnalyser.analyseSync(udr, syncRequest);
		mocks.verify();
		
		assertThat(analysed.getCollections()).containsOnly(toStoreSyncCollection);
	}
	
	@Test
	public void testNoRequestOptionsTakeThePreviousOne() throws Exception {
		Document firstRequest = buildRequestWithOptions("0", 
				"<Options>" +
					"<FilterType>2</FilterType>" +
					"<Conflict>1</Conflict>" +
					"<MIMESupport>1</MIMESupport>" +
					"<MIMETruncation>100</MIMETruncation>" +
					"<BodyPreference>" +
						"<Type>1</Type>" +
					"</BodyPreference>" +
					"<BodyPreference>" +
						"<Type>2</Type>" +
					"</BodyPreference>" +
					"<BodyPreference>" +
						"<Type>4</Type>" +
						"<TruncationSize>5120</TruncationSize>" +
					"</BodyPreference>" +
				"</Options>");
		Document secondRequest = buildRequestWithoutOptions("156");
		
		SyncCollectionOptions firstRequestOptionsToStore = SyncCollectionOptions.builder()
				.filterType(FilterType.THREE_DAYS_BACK)
				.conflict(1)
				.mimeSupport(1)
				.mimeTruncation(100)
				.bodyPreferences(ImmutableList.<BodyPreference> of(
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.PlainText)
						.build(),
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.HTML)
						.build(),
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.MIME)
						.truncationSize(5120)
						.build()
					))
				.build();
		AnalysedSyncCollection firstSyncCollectionToStore = buildRequestCollectionWithOptions(firstRequestOptionsToStore, "0");
		AnalysedSyncCollection secondSyncCollectionToStore = buildRequestCollectionWithOptions(firstRequestOptionsToStore, "156");
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(udr.getUser(), device, firstSyncCollectionToStore);
		expectLastCall().once();
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(firstSyncCollectionToStore).once();
		syncedCollectionDao.put(udr.getUser(), device, secondSyncCollectionToStore);
		expectLastCall().once();
		
		mocks.replay();
		Sync firstDecodedRequest = syncAnalyser.analyseSync(udr, syncDecoder.decodeSync(firstRequest));
		Sync secondDecodedRequest = syncAnalyser.analyseSync(udr, syncDecoder.decodeSync(secondRequest));
		mocks.verify();
		
		assertThat(firstDecodedRequest.getCollections()).containsOnly(firstSyncCollectionToStore);
		assertThat(secondDecodedRequest.getCollections()).containsOnly(secondSyncCollectionToStore);
	}
	
	@Test
	public void testZeroTruncationSizeMustNotBeInterpreted() throws Exception {
		SyncCollectionOptions syncCollectionOptions = SyncCollectionOptions.builder()
				.filterType(FilterType.THREE_DAYS_BACK)
				.conflict(1)
				.mimeSupport(1)
				.mimeTruncation(100)
				.bodyPreferences(ImmutableList.<BodyPreference> of(
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.PlainText)
						.build()
					))
				.build();
		AnalysedSyncCollection syncCollection = AnalysedSyncCollection.builder()
			.collectionId(collectionId)
			.collectionPath(collectionPath)
			.dataType(PIMDataType.EMAIL)
			.options(syncCollectionOptions)
			.syncKey(SyncKey.INITIAL_FOLDER_SYNC_KEY)
			.status(SyncStatus.OK)
			.commands(SyncCollectionCommandsResponse.builder().build())
			.build();
		
		Document firstDoc = buildRequestWithOptions("0",
				"<Options>" +
					"<FilterType>2</FilterType>" +
					"<Conflict>1</Conflict>" +
					"<MIMESupport>1</MIMESupport>" +
					"<MIMETruncation>100</MIMETruncation>" +
					"<BodyPreference>" +
						"<Type>1</Type>" +
						"<TruncationSize>0</TruncationSize>" +
					"</BodyPreference>" +
				"</Options>");

		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(udr.getUser(), device, syncCollection);
		expectLastCall().once();
		
		mocks.replay();
		Sync analysedRequest = syncAnalyser.analyseSync(udr, syncDecoder.decodeSync(firstDoc));
		mocks.verify();
		
		SyncCollectionOptions options = analysedRequest.getCollection(collectionId).getOptions();
		BodyPreference bodyPreference = options.getBodyPreferences().get(0);
		assertThat(bodyPreference.getTruncationSize()).isNull();
	}
	
	@Test
	public void testTruncationSizeMustBeInterpreted() throws Exception {
		SyncCollectionOptions syncCollectionOptions = SyncCollectionOptions.builder()
				.filterType(FilterType.THREE_DAYS_BACK)
				.conflict(1)
				.mimeSupport(1)
				.mimeTruncation(100)
				.bodyPreferences(ImmutableList.<BodyPreference> of(
					BodyPreference.builder()
						.bodyType(MSEmailBodyType.PlainText)
						.truncationSize(1000)
						.build()
					))
				.build();
		AnalysedSyncCollection syncCollection = AnalysedSyncCollection.builder()
				.collectionId(collectionId)
				.collectionPath(collectionPath)
				.dataType(PIMDataType.EMAIL)
				.options(syncCollectionOptions)
				.syncKey(SyncKey.INITIAL_FOLDER_SYNC_KEY)
				.status(SyncStatus.OK)
				.commands(SyncCollectionCommandsResponse.builder().build())
				.build();
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(udr.getUser(), device, syncCollection);
		expectLastCall().once();
		
		Document firstRequest = buildRequestWithOptions("0",
				"<Options>" +
					"<FilterType>2</FilterType>" +
					"<Conflict>1</Conflict>" +
					"<MIMESupport>1</MIMESupport>" +
					"<MIMETruncation>100</MIMETruncation>" +
					"<BodyPreference>" +
						"<Type>1</Type>" +
						"<TruncationSize>1000</TruncationSize>" +
					"</BodyPreference>" +
				"</Options>");

		mocks.replay();
		Sync analysedRequest = syncAnalyser.analyseSync(udr, syncDecoder.decodeSync(firstRequest));
		mocks.verify();
		
		SyncCollectionOptions options = analysedRequest.getCollection(collectionId).getOptions();
		BodyPreference bodyPreference = options.getBodyPreferences().get(0);
		assertThat(bodyPreference.getTruncationSize()).isEqualTo(1000);
	}
	
	@Test
	public void testRequestWithSameDataClassThanRecognizedDataType() throws Exception {
		AnalysedSyncCollection syncCollection = AnalysedSyncCollection.builder()
				.collectionId(collectionId)
				.collectionPath(collectionPath)
				.dataType(PIMDataType.EMAIL)
				.syncKey(new SyncKey("1234"))
				.status(SyncStatus.OK)
				.commands(SyncCollectionCommandsResponse.builder().build())
				.build();

		Document requestWithoutOptions = DOMUtils.parse(
				"<Sync>" +
						"<Collections>" +
							"<Collection>" +
								"<Class>Email</Class>" +
								"<SyncKey>1234</SyncKey>" +
								"<CollectionId>" + collectionId + "</CollectionId>" +
							"</Collection>" +
						"</Collections>" +
					"</Sync>");
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(udr.getUser(), device, syncCollection);
		expectLastCall().once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(requestWithoutOptions);
		Sync analysed = syncAnalyser.analyseSync(udr, syncRequest);
		
		assertThat(analysed.getCollection(collectionId).getDataClass()).isEqualTo("Email");
		assertThat(analysed.getCollection(collectionId).getDataType()).isEqualTo(PIMDataType.EMAIL);
	}
	
	@Test(expected=ServerErrorException.class)
	public void testRequestWithDifferentDataClassThanRecognizedDataType() throws Exception {
		Document requestWithoutOptions = DOMUtils.parse(
				"<Sync>" +
						"<Collections>" +
							"<Collection>" +
								"<Class>Calendar</Class>" +
								"<SyncKey>1234</SyncKey>" +
								"<CollectionId>" + collectionId + "</CollectionId>" +
							"</Collection>" +
						"</Collections>" +
					"</Sync>");
		
		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(requestWithoutOptions);
		try {
			syncAnalyser.analyseSync(udr, syncRequest);
		} catch (ServerErrorException e) {
			mocks.verify();
			throw e;
		}
	}
	
	private AnalysedSyncCollection buildRequestCollectionWithOptions(SyncCollectionOptions options, String syncKey) {
		return AnalysedSyncCollection.builder()
			.collectionId(collectionId)
			.collectionPath(collectionPath)
			.dataType(PIMDataType.EMAIL)
			.options(options)
			.syncKey(new SyncKey(syncKey))
			.status(SyncStatus.OK)
			.commands(SyncCollectionCommandsResponse.builder().build())
			.build();
	}

	private Document buildRequestWithoutOptions(String syncKey) throws Exception {
		return buildRequestWithOptions(syncKey, "");
	}

	private Document buildRequestWithOptions(String syncKey, String options) throws Exception {
		return DOMUtils.parse(
			"<Sync>" +
				"<Collections>" +
					"<Collection>" +
						"<SyncKey>" + syncKey +"</SyncKey>" +
						"<CollectionId>" + collectionId + "</CollectionId>" +
						options +
					"</Collection>" +
				"</Collections>" +
			"</Sync>");
	}

	@Test(expected=PartialException.class)
	public void testPartialRequest() throws Exception {
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Partial/>" +
					"<Wait>1</Wait>" +
				"</Sync>");

		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		syncAnalyser.analyseSync(udr, syncRequest);
	}

	@Test
	public void windowSizeShouldTookInParentWhenNone() throws Exception {
		String collectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<WindowSize>150</WindowSize>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + collectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +collectionId + "</CollectionId>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(eq(user), eq(device), anyObject(AnalysedSyncCollection.class));
		expectLastCall().once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		Sync sync = syncAnalyser.analyseSync(udr, syncRequest);
		mocks.verify();

		assertThat(sync.getCollection(collectionId).getWindowSize()).isEqualTo(150);
	}

	@Test
	public void windowSizeShouldNotTookInParentWhenOne() throws Exception {
		String collectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<WindowSize>150</WindowSize>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + collectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +collectionId + "</CollectionId>" +
							"<WindowSize>75</WindowSize>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(eq(user), eq(device), anyObject(AnalysedSyncCollection.class));
		expectLastCall().once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		Sync sync = syncAnalyser.analyseSync(udr, syncRequest);
		mocks.verify();

		assertThat(sync.getCollection(collectionId).getWindowSize()).isEqualTo(75);
	}

	@Test
	public void windowSizeShouldBeDefaultWhenNone() throws Exception {
		String collectionSyncKey = "1234-5678";
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>" + collectionSyncKey  + "</SyncKey>" +
							"<CollectionId>" +collectionId + "</CollectionId>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		syncedCollectionDao.put(eq(user), eq(device), anyObject(AnalysedSyncCollection.class));
		expectLastCall().once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		Sync sync = syncAnalyser.analyseSync(udr, syncRequest);
		mocks.verify();

		assertThat(sync.getCollection(collectionId).getWindowSize()).isEqualTo(100);
	}


	@Test(expected=ASRequestStringFieldException.class)
	public void analyzeShouldTriggerExceptionWhenNoSyncKey() throws Exception {
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<CollectionId>" +collectionId + "</CollectionId>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		expect(syncedCollectionDao.get(udr.getCredentials(), device, collectionId)).andReturn(null).once();
		
		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		try {
			syncAnalyser.analyseSync(udr, syncRequest);
		} catch (Exception e) {
			mocks.verify();
			throw e;
		}
	}

	@Test(expected=ASRequestIntegerFieldException.class)
	public void analyzeShouldTriggerExceptionWhenNoCollectionId() throws Exception {
		Document request = DOMUtils.parse(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<Sync>" +
					"<Collections>" +
						"<Collection>" +
							"<SyncKey>1324-1231</SyncKey>" +
						"</Collection>" +
					"</Collections>" +
				"</Sync>");

		mocks.replay();
		SyncRequest syncRequest = syncDecoder.decodeSync(request);
		try {
			syncAnalyser.analyseSync(udr, syncRequest);
		} catch (Exception e) {
			mocks.verify();
			throw e;
		}
	}
}
