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
package org.obm.push.command

import org.obm.push.context.ActiveSyncConfiguration
import org.obm.push.context.User
import org.obm.push.context.UserKey
import org.obm.push.context.http.ActiveSyncHeaders
import org.obm.push.context.http.HttpQueryParams
import io.gatling.core.session.Session
import io.gatling.core.validation.Success
import io.gatling.http.Headers
import io.gatling.http.Predef.http
import io.gatling.http.request.builder.PostHttpRequestBuilder

abstract class AbstractActiveSyncCommand(userKey: UserKey)
		extends ActiveSyncCommand {

	override def buildCommand(): PostHttpRequestBuilder = {
		http(s => Success(commandTitle))
			.post(s => Success(ActiveSyncConfiguration.postUrl))
			.header(Headers.Names.CONTENT_TYPE, s => Success(ActiveSyncConfiguration.wbXmlContentType))
			.header(ActiveSyncHeaders.AS_VERSION, s => Success(ActiveSyncConfiguration.activeSyncVersion.asSpecificationValue()))
			.header(ActiveSyncHeaders.AS_POLICY_KEY, s => Success(userKey.sessionHelper.findPolicyKey(s).toString))
			.basicAuth(s => Success(user(s).userProtocol), s => Success(user(s).password))
			.queryParam(s => Success(HttpQueryParams.USER), s => Success(user(s).userProtocol))
			.queryParam(s => Success(HttpQueryParams.DEVICE_ID), s => Success(user(s).deviceId.getDeviceId()))
			.queryParam(s => Success(HttpQueryParams.DEVICE_TYPE), s => Success(user(s).deviceType))
			.queryParam(s => Success(HttpQueryParams.COMMAND), s => Success(commandName))
	}
	
	def user(session: Session) = session.attributes(userKey.key).asInstanceOf[User]
	
	def getOrNoneIfEmpty[T](response: Option[Array[Byte]], transformMethod: (Array[Byte] => T)) = response match {
		case None => {
			println("No data received for command %s : %s: ".format(commandName, commandTitle))
			Option.empty
		}
		case a: Some[Array[Byte]] => a.get.length match {
			case 0 => {
				println("Empty data received for command %s : %s: ".format(commandName, commandTitle))
				Option.empty
			}
			case _ => Option.apply(transformMethod.apply(a.get))
		}
	}
}
