/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2012  Linagora
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
package org.obm.sync.push.client.commands;

import java.util.List;

import org.obm.push.bean.MoveItemsStatus;
import org.obm.push.utils.DOMUtils;
import org.obm.sync.push.client.AccountInfos;
import org.obm.sync.push.client.MoveItemsResponse;
import org.obm.sync.push.client.MoveItemsResponse.MoveResult;
import org.obm.sync.push.client.OPClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.collect.Lists;

public class MoveItemsCommand extends TemplateBasedCommand<MoveItemsResponse> {

	public static class Move {
		
		public final String serverId;
		public final int sourceCollectionId;
		public final int destCollectionId;
		
		public Move(String serverId, int sourceCollectionId, int destCollectionId) {
			this.serverId = serverId;
			this.sourceCollectionId = sourceCollectionId;
			this.destCollectionId = destCollectionId;
		}
	}
	
	private final Move[] moves;
	
	public MoveItemsCommand(Move...moves) {
		super(NS.Move, "MoveItems", "MoveItemsRequest.xml");
		this.moves = moves;
	}
	
	@Override
	protected void customizeTemplate(AccountInfos ai, OPClient opc) {
		Element documentElement = tpl.getDocumentElement();
		
		for (Move move : moves) {
			Element fetchElement = DOMUtils.createElement(documentElement, "Move");
			DOMUtils.createElementAndText(fetchElement, "SrcMsgId", move.serverId);
			DOMUtils.createElementAndText(fetchElement, "SrcFldId", String.valueOf(move.sourceCollectionId));
			DOMUtils.createElementAndText(fetchElement, "DstFldId", String.valueOf(move.destCollectionId));
		}
	}

	@Override
	protected MoveItemsResponse parseResponse(Element root) {
		NodeList responses = root.getElementsByTagName("Response");
		
		List<MoveResult> moveResults = Lists.newArrayList();
		for (int i = 0 ; i < responses.getLength(); i++) {
			Element moveResult = (Element) responses.item(i);
			String statusAsString = DOMUtils.getElementText(moveResult, "Status");
			String srcMsgId = DOMUtils.getElementText(moveResult, "SrcMsgId");
			String dstMsgId = DOMUtils.getElementText(moveResult, "DstMsgId");
			
			MoveItemsStatus status = MoveItemsStatus.fromSpecificationValue(statusAsString);
			moveResults.add(new MoveResult(srcMsgId, dstMsgId, status));
		}
		return new MoveItemsResponse(moveResults);
	}

}