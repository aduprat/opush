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
package org.obm.push.mail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.List;

import net.fortuna.ical4j.data.ParserException;

import org.minig.imap.Flag;
import org.minig.imap.UIDEnvelope;
import org.minig.imap.mime.IMimePart;
import org.minig.imap.mime.MimeMessage;
import org.obm.icalendar.ICalendar;
import org.obm.mail.conversation.EmailView;
import org.obm.mail.conversation.EmailView.Builder;
import org.obm.mail.conversation.EmailViewAttachment;
import org.obm.mail.conversation.EmailViewInvitationType;
import org.obm.push.bean.BodyPreference;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.exception.EmailViewBuildException;
import org.obm.push.exception.EmailViewPartsFetcherException;
import org.obm.push.mail.transformer.Transformer;
import org.obm.push.mail.transformer.Transformer.TransformersFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

public class EmailViewPartsFetcherImpl implements EmailViewPartsFetcher {

	private static final Logger logger = LoggerFactory.getLogger(EmailViewPartsFetcherImpl.class);

	private final TransformersFactory transformersFactory;
	private final PrivateMailboxService privateMailboxService;
	private final UserDataRequest udr;
	private final String collectionName;
	private final Integer collectionId;
	private final List<BodyPreference> bodyPreferences;

	public EmailViewPartsFetcherImpl(TransformersFactory transformersFactory, PrivateMailboxService privateMailboxService, 
			List<BodyPreference> bodyPreferences, UserDataRequest udr, String collectionName, Integer collectionId) {
		
		this.transformersFactory = transformersFactory;
		this.privateMailboxService = privateMailboxService;
		this.udr = udr;
		this.collectionName = collectionName;
		this.collectionId = collectionId;
		this.bodyPreferences = bodyPreferences;
	}

	public EmailView fetch(long uid) throws EmailViewPartsFetcherException {
		try {
			Builder emailViewBuilder = EmailView.builder().uid(uid);
			fetchFlags(emailViewBuilder, uid);
			fetchEnvelope(emailViewBuilder, uid);
			
			MimeMessage mimeMessage = getMimeMessage(uid);
			FetchInstruction fetchInstruction = getFetchInstruction(mimeMessage);
			if (fetchInstruction != null) {
				fetchBody(emailViewBuilder, fetchInstruction, uid);
				fetchAttachments(emailViewBuilder, fetchInstruction, uid);
			}
			fetchInvitation(emailViewBuilder, mimeMessage, uid);
			
			return emailViewBuilder.build();
		} catch (MailException e) {
			throw new EmailViewPartsFetcherException(e);
		} catch (IOException e) {
			throw new EmailViewPartsFetcherException(e);
		} catch (ParserException e) {
			throw new EmailViewPartsFetcherException(e);
		} catch (EmailViewBuildException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}


	private void fetchFlags(Builder emailViewBuilder, long uid) throws MailException {
		Collection<Flag> emailFlags = privateMailboxService.fetchFlags(udr, collectionName, uid);
		emailViewBuilder.flags(emailFlags);
	}

	private void fetchEnvelope(Builder emailViewBuilder, long uid)throws MailException {
		UIDEnvelope envelope = privateMailboxService.fetchEnvelope(udr, collectionName, uid);
		emailViewBuilder.envelope(envelope.getEnvelope());
	}
	
	private MimeMessage getMimeMessage(long uid) throws MailException {
		return privateMailboxService.fetchBodyStructure(udr, collectionName, uid);
	}

	private FetchInstruction getFetchInstruction(MimeMessage mimeMessage) {
		return new MimePartSelector().select(bodyPreferences, mimeMessage);
	}
	
	private void fetchBody(Builder emailViewBuilder, FetchInstruction fetchInstruction, 
			long uid) throws MailException, IOException {
		
		InputStream bodyData = fetchBodyData(fetchInstruction, uid);
		
		Transformer transformedMail = transformersFactory.create(fetchInstruction);
		
		IMimePart mimePart = fetchInstruction.getMimePart();
		emailViewBuilder.bodyMimePartData(transformedMail.transform(mimePart.decodeMimeStream(bodyData), transformationCharset(mimePart)));
		emailViewBuilder.bodyType(transformedMail.targetType());
		emailViewBuilder.estimatedDataSize(fetchInstruction.getMimePart().getSize());
		emailViewBuilder.truncated(fetchInstruction.mustTruncate());
		emailViewBuilder.charset(fetchInstruction.getMimePart().getCharset());
	}

	private Charset transformationCharset(IMimePart mimePart) {
		try {
			String charset = mimePart.getCharset();
			if (charset != null) {
				return Charset.forName(charset);
			}
		} catch (IllegalCharsetNameException e) {
			logger.info("mail with illegal charset : " + mimePart.getCharset());
		} catch (UnsupportedCharsetException e) {
			logger.info("mail with unsupported charset : " + mimePart.getCharset());
		}
		return Charsets.UTF_8;
	}

	private InputStream fetchBodyData(FetchInstruction fetchInstruction, long uid) throws MailException {
		InputStream bodyData = null;
		try {
			if (fetchInstruction.hasMimePartAddressDefined()) {
				bodyData = privateMailboxService.fetchMimePartData(udr, collectionName, uid, fetchInstruction);
				if (bodyData != null) {
					return new ByteArrayInputStream(ByteStreams.toByteArray(bodyData));
				} else {
					return null;
				}
			} else {
				return privateMailboxService.fetchMailStream(udr, collectionName, uid);
			}
		} catch (IOException e) {
			throw new MailException(e);
		} finally {
			Closeables.closeQuietly(bodyData);
		}
		
	}
	
	@VisibleForTesting void fetchAttachments(Builder emailViewBuilder, FetchInstruction fetchInstruction, long uid) {
		List<EmailViewAttachment> attachments = Lists.newArrayList();
		IMimePart parentMessage = fetchInstruction.getMimePart().findRootMimePartInTree();
		int nbAttachments = 0;
		for (IMimePart mp : parentMessage.listLeaves(true, true)) {
			if (mp.isAttachment() && !mp.isICSAttachment()) {
				EmailViewAttachment emailViewAttachment = extractEmailViewAttachment(mp, nbAttachments++, uid);
				if (emailViewAttachment != null) {
					attachments.add(emailViewAttachment);
				}
			}
		}
		emailViewBuilder.attachments(attachments);
	}
	
	private EmailViewAttachment extractEmailViewAttachment(IMimePart mp, int nbAttachments, long uid) {
		String id = "at_" + uid + "_" + nbAttachments;
		String fileReference = AttachmentHelper.getAttachmentId(String.valueOf(collectionId), String.valueOf(uid), 
				mp.getAddress().getAddress(), mp.getFullMimeType(), mp.getContentTransfertEncoding());
		
		Optional<String> displayName = selectAttachmentDisplayName(mp);
		if (displayName.isPresent()) {
			return EmailViewAttachment.builder()
					.id(id)
					.displayName(displayName.get())
					.fileReference(fileReference)
					.size(mp.getSize())
					.contentType(mp.getContentType())
					.build();
		}
		return null;
	}

	@VisibleForTesting Optional<String> selectAttachmentDisplayName(IMimePart attachment) {
		String partName = attachment.getName();
		String contentId = attachment.getContentId();
		if (!Strings.isNullOrEmpty(partName) || !Strings.isNullOrEmpty(contentId)) {
			return Optional.of(Objects.firstNonNull(partName, contentId));
		}
		return Optional.absent();
	}
	
	private void fetchInvitation(Builder emailViewBuilder, MimeMessage mimeMessage, long uid) 
			throws MailException, IOException, ParserException {
		
		IMimePart parentMessage = mimeMessage.findRootMimePartInTree();
		for (IMimePart mp : parentMessage.listLeaves(true, true)) {
			if (mp.isInvitation()) {
				fetchICalendar(emailViewBuilder, mp, uid);
				emailViewBuilder.invitationType(EmailViewInvitationType.REQUEST);
			}
			if (mp.isCancelInvitation()) {
				fetchICalendar(emailViewBuilder, mp, uid);
				emailViewBuilder.invitationType(EmailViewInvitationType.CANCELED);
			}
		}
	}

	private void fetchICalendar(Builder emailViewBuilder, IMimePart mp, long uid)
			throws MailException, IOException, ParserException {

		InputStream inputStream = privateMailboxService.findAttachment(udr, collectionName, uid, mp.getAddress());
		ICalendar iCalendar = ICalendar.builder()
			.inputStream(mp.decodeMimeStream(inputStream)).build();
		emailViewBuilder.iCalendar(iCalendar);
	}

}
