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

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * This enum is serialized, take care of changes done there for older version compatibility
 */
public enum SyncStatus {

	OK("1"),
	PROTOCOL_VERSION_MISMATCH("2"),
	INVALID_SYNC_KEY("3"),
	PROTOCOL_ERROR("4"),
	SERVER_ERROR("5"),
	CONVERSATION_ERROR_OR_INVALID_ITEM("6"),
	CONFLICT("7"),
	OBJECT_NOT_FOUND("8"),
	OUT_OF_DISK_SPACE("9"),
	NOTIFICATION_GUID_ERROR("10"),
	NOT_YET_PROVISIONNED("11"),
	HIERARCHY_CHANGED("12"),
	PARTIAL_REQUEST("13"),
	WAIT_INTERVAL_OUT_OF_RANGE("14"),
	TO_MUCH_FOLDER_TO_MONITOR("15"),
	NEED_RETRY("16");

	private final String specificationValue;

	private SyncStatus(String asSpecificationValue) {
		this.specificationValue = asSpecificationValue;
	}
	
	public String asSpecificationValue() {
		return specificationValue;
	}
	
	public static SyncStatus fromSpecificationValue(String specificationValue) {
		if (specValueToEnum.containsKey(specificationValue)) {
			return specValueToEnum.get(specificationValue);
		}
		throw new IllegalArgumentException("No SyncStatus for '" + specificationValue + "'");
	}

	private static Map<String, SyncStatus> specValueToEnum;
	
	static {
		Builder<String, SyncStatus> builder = ImmutableMap.builder();
		for (SyncStatus syncStatus : values()) {
			builder.put(syncStatus.specificationValue, syncStatus);
		}
		specValueToEnum = builder.build();
	}
}
