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
package org.obm.push.mail;

import org.obm.push.backend.PIMBackend;
import org.obm.push.bean.MSAttachementData;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.ms.UidMSEmail;
import org.obm.push.exception.activesync.AttachementNotFoundException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.exception.activesync.ItemNotFoundException;
import org.obm.push.exception.activesync.ProcessingEmailException;
import org.obm.push.protocol.bean.CollectionId;

public interface MailBackend extends PIMBackend {

	void sendEmail(UserDataRequest udr, byte[] mailContent, boolean saveInSent)
			throws ProcessingEmailException;

	void replyEmail(UserDataRequest udr, byte[] mailContent, boolean saveInSent, CollectionId collectionId, ServerId serverId)
			throws ProcessingEmailException, CollectionNotFoundException, ItemNotFoundException;

	void forwardEmail(UserDataRequest udr, byte[] mailContent,
			boolean saveInSent, CollectionId collectionId, ServerId serverId)
			throws ProcessingEmailException, CollectionNotFoundException;

	UidMSEmail getEmail(UserDataRequest udr, CollectionId collectionId, ServerId serverId)
			throws CollectionNotFoundException, ProcessingEmailException;

	MSAttachementData getAttachment(UserDataRequest udr, String attachmentId)
			throws AttachementNotFoundException, CollectionNotFoundException,
			ProcessingEmailException;

	Long getEmailUidFromServerId(ServerId serverId);

	Object getInvitation(UserDataRequest udr, CollectionId collectionId, ServerId serverId) throws CollectionNotFoundException, ProcessingEmailException;
}
