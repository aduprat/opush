package org.obm.push.java.mail.testsuite;

import org.junit.Ignore;
import org.junit.Test;
import org.obm.push.java.mail.MailEnvModule;
import org.obm.test.GuiceModule;

@GuiceModule(MailEnvModule.class)
public class MailboxFetchAPITest extends org.obm.push.mail.imap.testsuite.MailboxFetchAPITest {
	
	@Ignore ("DefaultFolder doesn't parse mailbox with spaces")
	@Test
	public void testFetchUIDNextMailboxInUTF7() {
	}
}
