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
package org.obm.push.handler;

import org.obm.push.backend.IContentsExporter;
import org.obm.push.backend.IContinuation;
import org.obm.push.bean.AnalysedSyncCollection;
import org.obm.push.bean.GetItemEstimateStatus;
import org.obm.push.bean.ICollectionPathHelper;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.SyncCollectionResponse;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.SyncStatus;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.WindowingKey;
import org.obm.push.exception.CollectionPathException;
import org.obm.push.exception.ConversionException;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.UnexpectedObmSyncServerException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.exception.activesync.HierarchyChangedException;
import org.obm.push.exception.activesync.InvalidSyncKeyException;
import org.obm.push.exception.activesync.ProcessingEmailException;
import org.obm.push.exception.activesync.TimeoutException;
import org.obm.push.impl.DOMDumper;
import org.obm.push.impl.Responder;
import org.obm.push.mail.exception.FilterTypeChangedException;
import org.obm.push.protocol.GetItemEstimateProtocol;
import org.obm.push.protocol.bean.CollectionId;
import org.obm.push.protocol.bean.Estimate;
import org.obm.push.protocol.bean.GetItemEstimateRequest;
import org.obm.push.protocol.bean.GetItemEstimateResponse;
import org.obm.push.protocol.request.ActiveSyncRequest;
import org.obm.push.state.StateMachine;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.WindowingDao;
import org.obm.push.wbxml.WBXMLTools;
import org.w3c.dom.Document;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetItemEstimateHandler extends WbxmlRequestHandler {

	private final WindowingDao windowingDao;
	private final GetItemEstimateProtocol protocol;
	private final ICollectionPathHelper collectionPathHelper;
	private final IContentsExporter contentsExporter;
	private final StateMachine stMachine;
	private final CollectionDao collectionDao;

	@Inject
	protected GetItemEstimateHandler(
			IContentsExporter contentsExporter, StateMachine stMachine,
			WindowingDao windowingDao, CollectionDao collectionDao,
			GetItemEstimateProtocol protocol, WBXMLTools wbxmlTools, DOMDumper domDumper,
			ICollectionPathHelper collectionPathHelper) {
		
		super(wbxmlTools, domDumper);
		
		this.contentsExporter = contentsExporter;
		this.stMachine = stMachine;
		this.windowingDao = windowingDao;
		this.collectionDao = collectionDao;
		this.protocol = protocol;
		this.collectionPathHelper = collectionPathHelper;
	}

	@Override
	public void process(IContinuation continuation, UserDataRequest udr,
			Document doc, ActiveSyncRequest request, Responder responder) {

		try {
			GetItemEstimateRequest estimateRequest = protocol.decodeRequest(doc);
			GetItemEstimateResponse response = doTheJob(udr, estimateRequest);
			Document document = protocol.encodeResponse(udr.getDevice(), response);
			sendResponse(responder, document);

		} catch (InvalidSyncKeyException e) {
			logger.warn(e.getMessage(), e);
			sendResponse(responder, 
					protocol.buildError(GetItemEstimateStatus.INVALID_SYNC_KEY, e.getCollectionId()));
		} catch (CollectionNotFoundException e) {
			sendErrorResponse(responder, 
					protocol.buildError(GetItemEstimateStatus.INVALID_COLLECTION, e.getCollectionId()), e);
		} catch (HierarchyChangedException e) {
			sendErrorResponse(responder, 
					protocol.buildError(GetItemEstimateStatus.INVALID_COLLECTION, null), e);
		} catch (DaoException e) {
			logger.error(e.getMessage(), e);
		} catch (UnexpectedObmSyncServerException e) {
			logger.error(e.getMessage(), e);
		} catch (ProcessingEmailException e) {
			logger.error(e.getMessage(), e);
		} catch (ConversionException e) {
			logger.error(e.getMessage(), e);
		} catch (TimeoutException e) {
			sendErrorResponse(responder, 
					protocol.buildError(GetItemEstimateStatus.NEED_SYNC, null), e);
		}
	}

	private void sendErrorResponse(Responder responder, Document document, Exception exception) {
		logger.error(exception.getMessage(), exception);
		sendResponse(responder, document);
	}
	
	private void sendResponse(Responder responder, Document document) {
		responder.sendWBXMLResponse("GetItemEstimate", document);
	}

	private GetItemEstimateResponse doTheJob(UserDataRequest udr, GetItemEstimateRequest request) throws InvalidSyncKeyException, DaoException, 
		UnexpectedObmSyncServerException, ProcessingEmailException,
		CollectionNotFoundException, ConversionException, HierarchyChangedException {
		

		GetItemEstimateResponse.Builder getItemEstimateResponseBuilder = GetItemEstimateResponse.builder();
		for (AnalysedSyncCollection syncCollection: request.getSyncCollections()) {
			
			CollectionId collectionId = syncCollection.getCollectionId();
			String collectionPath = collectionDao.getCollectionPath(collectionId);
			try {
				PIMDataType dataType = collectionPathHelper.recognizePIMDataType(collectionPath);
				SyncCollectionResponse.Builder builder = SyncCollectionResponse.builder()
						.collectionId(collectionId)
						.dataType(dataType)
						.status(SyncStatus.OK);
				getItemEstimateResponseBuilder.add(computeEstimate(udr, syncCollection, dataType, builder));
			} catch (CollectionPathException e) {
				throw new CollectionNotFoundException("Collection path {" + collectionPath + "} not found.");
			}			
		}
		
		return getItemEstimateResponseBuilder.build();
	}
	
	@VisibleForTesting Estimate computeEstimate(UserDataRequest udr, AnalysedSyncCollection request, PIMDataType dataType, SyncCollectionResponse.Builder syncCollectionResponseBuilder) 
			throws DaoException, InvalidSyncKeyException, CollectionNotFoundException, ProcessingEmailException, 
				UnexpectedObmSyncServerException, ConversionException, HierarchyChangedException {
		
		SyncKey syncKey = request.getSyncKey();
		ItemSyncState state = stMachine.getItemSyncState(syncKey);
		CollectionId collectionId = request.getCollectionId();
		if (state == null) {
			throw new InvalidSyncKeyException(collectionId, syncKey);
		}
		
		Estimate.Builder estimateBuilder = Estimate.builder();
		try {
			estimateBuilder.incrementEstimate(windowingDao.countPendingChanges(new WindowingKey(udr.getUser(), udr.getDevId(), collectionId, syncKey)));
			estimateBuilder.incrementEstimate(contentsExporter.getItemEstimateSize(udr, dataType, request, state));
		} catch (FilterTypeChangedException e) {
			syncCollectionResponseBuilder.status(SyncStatus.INVALID_SYNC_KEY);
		}
	
		return estimateBuilder
				.collection(syncCollectionResponseBuilder.build())
				.build();
	}
}
