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
package org.obm.push.helper

import org.obm.push.protocol.bean.SyncResponse
import scala.collection.JavaConversions._
import org.obm.push.bean.PIMDataType._
import org.obm.push.bean.ms.MSEmail
import org.obm.push.bean.msmeetingrequest.MSMeetingRequest
import scala.collection.mutable.MutableList
import org.obm.push.bean.SyncCollectionChange
import org.obm.push.bean.MSEvent

object SyncHelper {
	
	def findChangesWithMeetingRequest(syncResponse: SyncResponse) = {
		for (change <- findChangesWithEmailData(syncResponse);
			 meetingRequest = change.getData().asInstanceOf[MSEmail].getMeetingRequest();
				if meetingRequest != null) yield change
	}
	
	def findChangesWithEmailData(syncResponse: SyncResponse) = {
		for (change <- findChanges(syncResponse);
				if changeHasEmailData(change)) yield change
	}
	
	def findEventChanges(syncResponse: SyncResponse, serverId: String) = {
		for (change <- findChangesWithServerId(syncResponse, serverId);
				if changeHasCalendarData(change))
					yield change.getData().asInstanceOf[MSEvent]
	}
	
	def findChangesWithServerId(syncResponse: SyncResponse, serverId: String) = {
		for (change <- findChanges(syncResponse);
				if change.getServerId().equals(serverId))
					yield change
	}
	
	def findChanges(syncResponse: SyncResponse) = {
		for (collection <- syncResponse.getCollectionResponses();
			 change <- collection.getSyncCollection().getChanges())
				yield change
	}
	
	def changeHasCalendarData(change: SyncCollectionChange) = 
		change.getType() == CALENDAR && change.getData() != null
	
	def changeHasEmailData(change: SyncCollectionChange) = 
		change.getType() == EMAIL && change.getData() != null
}