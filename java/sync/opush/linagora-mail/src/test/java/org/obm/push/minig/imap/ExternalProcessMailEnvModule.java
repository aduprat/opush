package org.obm.push.minig.imap;

import org.obm.push.LinagoraImapModule;

import com.google.inject.AbstractModule;

public class ExternalProcessMailEnvModule extends AbstractModule {
	
	@Override
	protected void configure() {
		install(new LinagoraImapModule());
		install(new org.obm.push.mail.MailEnvModule());
		
	}
	
}