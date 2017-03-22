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
package org.obm.push.bean;

import java.util.Locale;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * This enum is serialized, take care of changes done there for older version compatibility
 */
public enum MSMessageClass {
	
	NOTE("ipm.note", false),
	NOTE_RULES_OOFTEMPLATE_MS("ipm.note.rules.ooftemplate.microsoft", false),
	NOTE_SMIME("ipm.note.smime", false),
	NOTE_SMIME_MULTIPART_SIGNED("ipm.note.smime.multipartsigned", false),
	SCHEDULE_MEETING_REQUEST("ipm.schedule.meeting.request", false),
	SCHEDULE_MEETING_CANCELED("ipm.schedule.meeting.canceled", false),
	SCHEDULE_MEETING_RESP_POS("ipm.schedule.meeting.resp.pos", false),
	SCHEDULE_MEETING_RESP_TENT("ipm.schedule.meeting.resp.tent", false),
	SCHEDULE_MEETING_RESP_NEG("ipm.schedule.meeting.resp.neg", false),
	POST("ipm.post", false),

	/*
	 * Reports:
	 * - NDR: non-delivery report
	 * - DR: delivery report
	 * - DELAYED: delivery receipt for a delayed message
	 * - IPNRN: read receipt
	 * - IPNNRN: non-read receipt
	 */
	NOTE_REPORT_NDR("report.ipm.note.ndr", true),
	NOTE_REPORT_DR("report.ipm.note.dr", true),
	NOTE_REPORT_DELAYED("report.ipm.note.delayed", true),
	NOTE_REPORT_IPNRN("report.ipm.note.ipnrn", true),
	NOTE_REPORT_IPNNRN("report.ipm.note.ipnnrn", true),
	SCHEDULE_MEETING_REQUEST_REPORT_NDR("report.ipm.schedule. meeting.request.ndr", true),
	SCHEDULE_MEETING_RESP_POS_REPORT_NDR("report.ipm.schedule.meeting.resp.pos.ndr", true),
	SCHEDULE_MEETING_RESP_TENT_REPORT_NDR("report.ipm.schedule.meeting.resp.tent.ndr", true),
	SCHEDULE_MEETING_CANCELED_REPORT_NDR("report.ipm.schedule.meeting.canceled.ndr", true),
	NOTE_SMIME_REPORT_NDR("report.ipm.note.smime.ndr", true),
	NOTE_SMIME_REPORT_DR("report.ipm.note.smime.dr", true),
	NOTE_SMIME_MULTIPARTSIGNED_NDR("report.ipm.note.smime.multipartsigned.ndr", true),
	NOTE_SMIME_MULTIPARTSIGNED_DR("report.ipm.note.smime.multipartsigned.dr", true);
	
	private final String value;
	private final boolean report;

	private MSMessageClass(String value, boolean report) {
		this.value = value;
		this.report = report;
	}
	
	public String specificationValue() {
		return value;
	}

	public boolean isReport() {
		return report;
	}

	public static MSMessageClass fromSpecificationValue(String specificationValue) {
		if (Strings.isNullOrEmpty(specificationValue)) {
			return null;
		}
    	String lowerCaseSpecificationValue = specificationValue.toLowerCase(Locale.US);
		if (specValueToEnum.containsKey(lowerCaseSpecificationValue)) {
    		return specValueToEnum.get(lowerCaseSpecificationValue);
    	}
		return null;
    }

    private static Map<String, MSMessageClass> specValueToEnum;
    
    static {
    	Builder<String, MSMessageClass> builder = ImmutableMap.builder();
    	for (MSMessageClass enumeration : MSMessageClass.values()) {
    		builder.put(enumeration.specificationValue(), enumeration);
    	}
    	specValueToEnum = builder.build();
    }
}
