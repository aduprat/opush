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
package org.obm.push.mail.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;


public class TextToHtmlTest {

	private Transformer testee;

	@Before
	public void setup() {
		testee = new TextToHtml.Factory().create(null);
	}
	
	@Test(expected=NullPointerException.class)
	public void transformNull() throws IOException {
		testee.transform(null, Charsets.UTF_8);
	}

	@Test(expected=NullPointerException.class)
	public void transformNullCharset() throws IOException {
		testee.transform(new ByteArrayInputStream(new byte[] {0x22}), null);
	}
	
	@Test
	public void transformSimpleString() throws IOException {
		ByteArrayInputStream inputStream = stringToInputStream("simple string");
		InputStream actual = testee.transform(inputStream, Charsets.UTF_8);
		assertThat(actual).hasContentEqualTo(stringToInputStream("<html><body>simple string</body></html>"));
	}

	@Test
	public void transformMultilineString() throws IOException {
		ByteArrayInputStream inputStream = stringToInputStream("line one\r\nline two\r\n");
		InputStream actual = testee.transform(inputStream, Charsets.UTF_8);
		assertThat(actual).hasContentEqualTo(stringToInputStream("<html><body>line one<br/>line two</body></html>"));
	}
	
	private ByteArrayInputStream stringToInputStream(String content) {
		return new ByteArrayInputStream(content.getBytes(Charsets.UTF_8));
	}
}
