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

import org.obm.push.SummaryLoggerService;
import org.obm.push.backend.FolderSnapshotService;
import org.obm.push.backend.IContinuation;
import org.obm.push.backend.IHierarchyExporter;
import org.obm.push.bean.FolderSyncStatus;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.hierarchy.BackendFolders;
import org.obm.push.bean.change.hierarchy.FolderSnapshot;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.HierarchyChangesException;
import org.obm.push.exception.UnexpectedObmSyncServerException;
import org.obm.push.exception.activesync.InvalidFolderSyncKeyException;
import org.obm.push.exception.activesync.NoDocumentException;
import org.obm.push.exception.activesync.TimeoutException;
import org.obm.push.impl.DOMDumper;
import org.obm.push.impl.Responder;
import org.obm.push.protocol.FolderSyncProtocol;
import org.obm.push.protocol.bean.FolderSyncRequest;
import org.obm.push.protocol.bean.FolderSyncResponse;
import org.obm.push.protocol.request.ActiveSyncRequest;
import org.obm.push.state.FolderSyncKey;
import org.obm.push.state.FolderSyncKeyFactory;
import org.obm.push.wbxml.WBXMLTools;
import org.w3c.dom.Document;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class FolderSyncHandler extends WbxmlRequestHandler {

	private final IHierarchyExporter hierarchyExporter;
	private final FolderSyncProtocol protocol;
	private final SummaryLoggerService summaryLoggerService;
	private final FolderSyncKeyFactory folderSyncKeyFactory;
	private final FolderSnapshotService folderSnapshotService;
	
	@Inject
	protected FolderSyncHandler(IHierarchyExporter hierarchyExporter, 
			FolderSyncKeyFactory folderSyncKeyFactory,
			FolderSyncProtocol protocol,
			WBXMLTools wbxmlTools, DOMDumper domDumper,
			SummaryLoggerService summaryLoggerService,
			FolderSnapshotService folderSnapshotService) {
		
		super(wbxmlTools, domDumper);
		
		this.hierarchyExporter = hierarchyExporter;
		this.folderSyncKeyFactory = folderSyncKeyFactory;
		this.protocol = protocol;
		this.summaryLoggerService = summaryLoggerService;
		this.folderSnapshotService = folderSnapshotService;
	}

	@Override
	public void process(IContinuation continuation, UserDataRequest udr,
			Document doc, ActiveSyncRequest request, Responder responder) {
		
		try {
			FolderSyncRequest folderSyncRequest = protocol.decodeRequest(doc);
			
			FolderSyncResponse folderSyncResponse = doTheJob(udr, folderSyncRequest);
			summaryLoggerService.logOutgoingFolderSync(folderSyncResponse);
			Document ret = protocol.encodeResponse(udr.getDevice(), folderSyncResponse);
			sendResponse(responder, ret);
			
		} catch (InvalidFolderSyncKeyException e) {
			logger.warn(e.getMessage(), e);
			sendResponse(responder, protocol.encodeErrorResponse(FolderSyncStatus.INVALID_SYNC_KEY));
		} catch (NoDocumentException e) {
			sendError(responder, FolderSyncStatus.INVALID_REQUEST, e);
		} catch (DaoException e) {
			sendError(responder, FolderSyncStatus.SERVER_ERROR, e);
		} catch (UnexpectedObmSyncServerException e) {
			sendError(responder, FolderSyncStatus.SERVER_ERROR, e);
		} catch (HierarchyChangesException e) {
			sendError(responder, FolderSyncStatus.SERVER_ERROR, e);
		} catch (TimeoutException e) {
			sendError(responder, FolderSyncStatus.SERVER_ERROR, e);
		} catch (Exception e) {
			sendError(responder, FolderSyncStatus.SERVER_ERROR, e);
		}
	}

	private void sendResponse(Responder responder, Document ret) {
		responder.sendWBXMLResponse("FolderHierarchy", ret);
	}
	
	private void sendError(Responder responder, FolderSyncStatus status, Exception exception) {
		logger.error(exception.getMessage(), exception);
		sendResponse(responder, protocol.encodeErrorResponse(status));
	}
	
	private FolderSyncResponse doTheJob(UserDataRequest udr, FolderSyncRequest folderSyncRequest)
			throws DaoException, UnexpectedObmSyncServerException {
		
		return getFolderSyncResponse(udr, folderSyncRequest);
	}

	private FolderSyncResponse getFolderSyncResponse(UserDataRequest udr, FolderSyncRequest folderSyncRequest)
			throws DaoException, UnexpectedObmSyncServerException {
		
		FolderSyncKey outgoingSyncKey = folderSyncKeyFactory.randomSyncKey();
		FolderSnapshot knownSnapshot = folderSnapshotService.findFolderSnapshot(udr, folderSyncRequest.getSyncKey());
		BackendFolders backendFolders = hierarchyExporter.getBackendFolders(udr);
		FolderSnapshot currentSnapshot = folderSnapshotService.snapshot(udr, outgoingSyncKey, knownSnapshot, backendFolders);
		return FolderSyncResponse.builder()
			.status(FolderSyncStatus.OK)
			.hierarchyItemsChanges(folderSnapshotService.buildDiff(knownSnapshot, currentSnapshot))
			.newSyncKey(outgoingSyncKey)
			.build();
	}

}