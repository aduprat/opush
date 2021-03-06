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

import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.EncodedSyncCollectionCommandRequest;
import org.obm.push.bean.IApplicationData;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.Sync;
import org.obm.push.bean.SyncCollectionCommandRequest;
import org.obm.push.bean.SyncCollectionOptions;
import org.obm.push.bean.SyncStatus;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.hierarchy.Folder;
import org.obm.push.configuration.OpushConfiguration;
import org.obm.push.exception.CollectionPathException;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.activesync.ASRequestIntegerFieldException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.exception.activesync.InvalidServerId;
import org.obm.push.exception.activesync.PartialException;
import org.obm.push.exception.activesync.ProtocolException;
import org.obm.push.exception.activesync.ServerErrorException;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.protocol.bean.SyncCollection;
import org.obm.push.protocol.bean.SyncRequest;
import org.obm.push.service.FolderSnapshotDao;
import org.obm.push.store.SyncedCollectionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SyncAnalyser {

	private static final Logger logger = LoggerFactory.getLogger(SyncAnalyser.class);
	
	private final SyncedCollectionDao syncedCollectionStoreService;
	private final FolderSnapshotDao folderSnapshotDao;
	private final DecoderFactory decoderFactory;
	private final OpushConfiguration configuration;

	@Inject
	protected SyncAnalyser(SyncedCollectionDao syncedCollectionStoreService,
			FolderSnapshotDao folderSnapshotDao,
			DecoderFactory decoderFactory,
			OpushConfiguration configuration) {
		this.syncedCollectionStoreService = syncedCollectionStoreService;
		this.folderSnapshotDao = folderSnapshotDao;
		this.decoderFactory = decoderFactory;
		this.configuration = configuration;
	}

	public Sync analyseSync(UserDataRequest udr, SyncRequest syncRequest) 
			throws PartialException, ProtocolException, DaoException, CollectionPathException {
		assertNotPartialRequest(syncRequest);

		Sync.Builder builder = Sync.builder()
				.waitInMinutes(syncRequest.getWaitInMinute());
	
		for (SyncCollection syncCollectionRequest : syncRequest.getCollections()) {
			builder.addCollection(getCollection(syncRequest, udr, syncCollectionRequest, false));
		}
		return builder.build();
	}

	private void assertNotPartialRequest(SyncRequest syncRequest) {
		if (syncRequest.isPartial() != null && syncRequest.isPartial()) {
			throw new PartialException();
		}
	}
	
	private AnalysedSyncCollection getCollection(SyncRequest syncRequest, UserDataRequest udr, SyncCollection collectionRequest, boolean isPartial)
			throws PartialException, ProtocolException, DaoException, CollectionPathException{
		
		AnalysedSyncCollection.Builder builder = AnalysedSyncCollection.builder();
		CollectionId collectionId = getCollectionId(collectionRequest);
		builder
			.collectionId(collectionId)
			.syncKey(collectionRequest.getSyncKey());
		
		
		try {
			Folder folder = folderSnapshotDao.get(udr.getUser(), udr.getDevice(), collectionId);
			PIMDataType checkedCollectionType = checkedCollectionType(collectionRequest.getDataType(), folder);
			builder
				.dataType(checkedCollectionType)
				.windowSize(getWindowSize(syncRequest, collectionRequest))
				.options(getUpdatedOptions(
						findLastSyncedCollectionOptions(udr, isPartial, collectionId), collectionRequest))
				.status(SyncStatus.OK);
			
			
			for (EncodedSyncCollectionCommandRequest command: collectionRequest.getCommands()) {
				try {
					checkServerId(command, collectionId);
					builder.command(
							SyncCollectionCommandRequest.builder()
								.clientId(command.getClientId())
								.serverId(command.getServerId())
								.type(command.getType())
								.applicationData(decodeApplicationData(command, checkedCollectionType))
								.build());
				} catch (InvalidServerId e) {
					logger.warn("Error with a command", e);
				}
			}
			AnalysedSyncCollection analysed = builder.build();
			syncedCollectionStoreService.put(udr.getUser(), udr.getDevice(), analysed);
			return analysed;
		} catch (CollectionNotFoundException e) {
			return builder.status(SyncStatus.OBJECT_NOT_FOUND).build();
		}
		// TODO sync supported
		// TODO sync <getchanges/>

	}

	private IApplicationData decodeApplicationData(EncodedSyncCollectionCommandRequest command, final PIMDataType dataType) {
		if (command.getEncodedApplicationData() == null) {
			if (command.getType().requireApplicationData()) {
				throw new ProtocolException("No decodable " + command.getType() + " data");
			}
			return null;
		} else {
			return command.getEncodedApplicationData().decode(new Function<Element, IApplicationData>() {
				@Override
				public IApplicationData apply(Element input) {
					return decoderFactory.decode(input, dataType);
				}
			});
		}
	}
	
	private void checkServerId(EncodedSyncCollectionCommandRequest command, CollectionId collectionId) throws InvalidServerId {
		ServerId serverId = command.getServerId();
		if (serverId != null) {
			if (!serverId.isItem() || serverId.getItemId() < 1) {
				throw new InvalidServerId("item " + serverId.toString() + " must have an Id greater than O");
			}
			if (!serverId.belongsTo(collectionId)) {
				throw new InvalidServerId("item " + serverId.toString() + " doesn't belong to collection " + collectionId);
			}
		}
	}

	private PIMDataType checkedCollectionType(PIMDataType dataClass, Folder folder) {
		if (dataClass != null && !Objects.equal(dataClass, folder.getFolderType().getPIMDataType())) {
			String msg = "The type of the collection found:{%s} is not the same than received in DataClass:{%s}";
			throw new ServerErrorException(String.format(msg, folder.getFolderType().getPIMDataType() , dataClass));
		}
		return folder.getFolderType().getPIMDataType();
	}

	private CollectionId getCollectionId(SyncCollection collectionRequest) {
		CollectionId collectionId = collectionRequest.getCollectionId();
		if (collectionId == null) {
			throw new ASRequestIntegerFieldException("Collection id field is required");
		}
		return collectionId;
	}

	private Integer getWindowSize(SyncRequest syncRequest, SyncCollection collectionRequest) {
		Optional<Integer> collectionWindowSize = collectionRequest.getWindowSize();
		if (collectionWindowSize.isPresent()) {
			return limitMaximalWindowSize(collectionWindowSize.get());
		}
		
		Optional<Integer> requestWindowSize = syncRequest.getWindowSize();
		if (requestWindowSize.isPresent()) {
			return limitMaximalWindowSize(requestWindowSize.get());
		}
		
		int defaultWindowSize = configuration.defaultWindowSize();
		return limitMaximalWindowSize(defaultWindowSize);
	}
	
	private int limitMaximalWindowSize(int windowSize) {
		Optional<Integer> maxWindowSize = configuration.maxWindowSize();
		if (maxWindowSize.isPresent()) {
			return Math.min(maxWindowSize.get(), windowSize);
		}
		return windowSize;
	}

	private AnalysedSyncCollection findLastSyncedCollectionOptions(UserDataRequest udr, boolean isPartial, CollectionId collectionId) {
		AnalysedSyncCollection lastSyncCollection = 
				syncedCollectionStoreService.get(udr.getCredentials(), udr.getDevice(), collectionId);
		if (isPartial && lastSyncCollection == null) {
			throw new PartialException();
		}
		return lastSyncCollection;
	}

	private SyncCollectionOptions getUpdatedOptions(AnalysedSyncCollection lastSyncCollection, SyncCollection requestCollection) {
		SyncCollectionOptions lastUsedOptions = null;
		if (lastSyncCollection != null) {
			lastUsedOptions = lastSyncCollection.getOptions();
		}
		
		if (!requestCollection.hasOptions() && lastUsedOptions != null) {
			return lastUsedOptions;
		} else if (requestCollection.hasOptions()) {
			return SyncCollectionOptions.cloneOnlyByExistingFields(requestCollection.getOptions());
		}
		return SyncCollectionOptions.defaultOptions();
	}

	protected IApplicationData decode(Element data, PIMDataType dataType) {
		return decoderFactory.decode(data, dataType);
	}

}
