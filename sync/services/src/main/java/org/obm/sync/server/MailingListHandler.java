/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (c) 1997-2008 Aliasource - Groupe LINAGORA
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation; either version 2 of the
 *  License, (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 * 
 *  http://www.obm.org/                                              
 * 
 * ***** END LICENSE BLOCK ***** */
package org.obm.sync.server;

import java.util.List;

import org.obm.sync.auth.AccessToken;
import org.obm.sync.mailingList.MLEmail;
import org.obm.sync.mailingList.MailingList;
import org.obm.sync.mailingList.MailingListItemsParser;
import org.obm.sync.server.handler.SecureSyncHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import fr.aliacom.obm.common.mailingList.MailingListBindingImpl;
import fr.aliacom.obm.common.session.SessionManagement;

@Singleton
public class MailingListHandler extends SecureSyncHandler {

	private MailingListBindingImpl binding;
	private MailingListItemsParser parser;

	@Inject
	private MailingListHandler(SessionManagement sessionManagement, MailingListBindingImpl mailingListBindingImpl, MailingListItemsParser mailingListItemsParser) {
		super(sessionManagement);
		binding = mailingListBindingImpl;
		parser = mailingListItemsParser;
	}

	@Override
	public void handle(String method, ParametersSource params,
			XmlResponder responder) throws Exception {
		AccessToken at = getCheckedToken(params);
		if ("getMailingListFromId".equals(method)) {
			getMailingListFromId(at, params, responder);
		} else if ("listAllMailingList".equals(method)) {
			listAllMailingList(at, responder);
		} else if ("createMailingList".equals(method)) {
			createMailingList(at, params, responder);
		} else if ("modifyMailingList".equals(method)) {
			modifyMailingList(at, params, responder);
		} else if ("removeMailingList".equals(method)) {
			removeMailingList(at, params, responder);
		} else if ("addEmails".equals(method)) {
			addEmails(at, params, responder);
		} else if ("removeEmail".equals(method)) {
			removeEmail(at, params, responder);
		} else {
			responder.sendError("Cannot handle method '" + method + "'");
		}
	}

	private void removeEmail(AccessToken token, ParametersSource params, XmlResponder responder) {
		try {
			String mailingListId = p(params, "mailingListId");
			String mailingListEmailId = p(params, "mailingListEmailId");
			binding.removeEmail(token, Integer.parseInt(mailingListId),
					Integer.parseInt(mailingListEmailId));
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responder.sendError(e);
		}
	}

	private void addEmails(AccessToken token, ParametersSource params, XmlResponder responder) {
		try {
			String mailingListId = p(params, "mailingListId");
			String mailingListEmails = p(params, "mailingListEmails");
			List<MLEmail> ret = binding.addEmails(token, Integer.parseInt(mailingListId),
					parser.parseMailingListEmails(mailingListEmails));
			responder.sendListMailingListEmails(ret);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responder.sendError(e);
		}
	}

	private void getMailingListFromId(AccessToken token, ParametersSource params,
			XmlResponder responder) {
		try {
			String id = p(params, "id");

			MailingList ret = binding.getMailingListFromId(token,
					Integer.parseInt(id));
			if (ret != null) {
				responder.sendMailingList(ret);
			} else {
				responder.sendError("mailing list with id " + id
						+ " not found.");
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responder.sendError(e);
		}

	}

	private void listAllMailingList(AccessToken token,
			XmlResponder responder) {
		try {
			List<MailingList> ret = binding.listAllMailingList(token);
			responder.sendListMailingLists(ret);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responder.sendError(e);
		}
	}

	private void createMailingList(AccessToken token, ParametersSource params,
			XmlResponder responder) {
		try {
			MailingList ret = binding.createMailingList(token,
					parser.parseMailingList(p(params, "mailingList")));
			responder.sendMailingList(ret);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responder.sendError(e);
		}
	}

	private void modifyMailingList(AccessToken token, ParametersSource params,
			XmlResponder responder) {
		try {
			String ct = p(params, "mailingList");
			MailingList ret = binding.modifyMailingList(token,
					parser.parseMailingList(ct));
			responder.sendMailingList(ret);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responder.sendError(e);
		}
	}

	private void removeMailingList(AccessToken token, ParametersSource params,
			XmlResponder responder) {
		try {
			String id = p(params, "id");
			binding.removeMailingList(token, Integer.parseInt(id));
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responder.sendError(e);
		}
	}

}
