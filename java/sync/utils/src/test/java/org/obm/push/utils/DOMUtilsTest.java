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
package org.obm.push.utils;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.TransformerException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.filter.SlowFilterRunner;
import org.obm.push.utils.DOMUtils.XMLVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Charsets;

@RunWith(SlowFilterRunner.class)
public class DOMUtilsTest {
	
	@Test
	public void testEncodingXmlWithAccents() throws TransformerException, UnsupportedEncodingException{
		Document reply = DOMUtils.createDoc(null, "Sync");
		Element root = reply.getDocumentElement();
		String expectedString = "éàâ";
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		root.setTextContent(expectedString);
		DOMUtils.serialize(reply, out);

		assertThat(new String(out.toByteArray(), "UTF-8")).contains(expectedString);
	}
	
	@Test
	public void testCDataSectionEncoding() throws TransformerException, IOException{
		Document reply = DOMUtils.createDoc(null, "Root");
		Element root = reply.getDocumentElement();

		String expectedString = " \" ' éàâ";
		DOMUtils.createElementAndCDataText(root, 
				"CDataSection",  new ByteArrayInputStream(expectedString.getBytes()), Charsets.UTF_8);
		

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(reply, out);
				
		assertThat(new String(out.toByteArray(), "UTF-8")).contains( 
				"<CDataSection><![CDATA["+ expectedString + "]]></CDataSection>");
	}

	@Test
	public void testSerializeDefaultXmlVersionIsXML10() throws Exception {
		String output = DOMUtils.serialize(createDoc());
		assertThat(output).startsWith("<?xml version=\"1.0\"");
	}

	@Test
	public void testSerializeOutputDefaultXmlVersionIsXML10() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.0\"");
	}

	@Test
	public void testSerializePrettyDefaultXmlVersionIsXML10() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, true);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.0\"");
	}

	@Test
	public void testSerializeNotPrettyDefaultXmlVersionIsXML10() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, false);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.0\"");
	}

	@Test
	public void testSerializeXmlVersionXML10() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, XMLVersion.XML_10);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.0\"");
	}

	@Test
	public void testSerializeXmlVersionXML11() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, XMLVersion.XML_11);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.1\"");
	}

	@Test
	public void testSerializePrettyXmlVersionXML10() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, true, XMLVersion.XML_10);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.0\"");
	}

	@Test
	public void testSerializeNotPrettyXmlVersionXML10() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, false, XMLVersion.XML_10);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.0\"");
	}

	@Test
	public void testSerializePrettyXmlVersionXML11() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, true, XMLVersion.XML_11);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.1\"");
	}

	@Test
	public void testSerializeNotPrettyXmlVersionXML11() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DOMUtils.serialize(createDoc(), out, false, XMLVersion.XML_11);
		assertThat(streamToString(out)).startsWith("<?xml version=\"1.1\"");
	}

	private Document createDoc() throws FactoryConfigurationError {
		return DOMUtils.createDoc(null, "root");
	}
	
	private String streamToString(ByteArrayOutputStream out) {
		return new String(out.toByteArray(), Charsets.UTF_8);
	}
}

