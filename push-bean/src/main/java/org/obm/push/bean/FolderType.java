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

public enum FolderType {

	USER_FOLDER_GENERIC("1", PIMDataType.UNKNOWN),
	DEFAULT_INBOX_FOLDER("2", PIMDataType.EMAIL),
	DEFAULT_DRAFTS_FOLDER("3", PIMDataType.EMAIL),
	DEFAULT_DELETED_ITEMS_FOLDER("4", PIMDataType.EMAIL),
	DEFAULT_SENT_EMAIL_FOLDER("5", PIMDataType.EMAIL),
	DEFAULT_OUTBOX_FOLDER("6", PIMDataType.EMAIL),
	DEFAULT_TASKS_FOLDER("7", PIMDataType.TASKS),
	DEFAULT_CALENDAR_FOLDER("8", PIMDataType.CALENDAR),
	DEFAULT_CONTACTS_FOLDER("9", PIMDataType.CONTACTS),
	DEFAULT_NOTES_FOLDER("10", PIMDataType.UNKNOWN),
	DEFAULT_JOURNAL_FOLDER("11", PIMDataType.UNKNOWN),
	USER_CREATED_EMAIL_FOLDER("12", PIMDataType.EMAIL),
	USER_CREATED_CALENDAR_FOLDER("13", PIMDataType.CALENDAR),
	USER_CREATED_CONTACTS_FOLDER("14", PIMDataType.CONTACTS),
	USER_CREATED_TASKS_FOLDER("15", PIMDataType.TASKS),
	USER_CREATED_JOURNAL_FOLDER("16", PIMDataType.UNKNOWN),
	USER_CREATED_NOTES_FOLDER("17", PIMDataType.UNKNOWN),
	UNKNOWN_FOLDER_TYPE("18", PIMDataType.UNKNOWN);
	
	private final String specificationValue;
	private final PIMDataType type;
	
	private FolderType(String specificationValue, PIMDataType type) {
		this.specificationValue = specificationValue;
		this.type = type;
	}

	public String asSpecificationValue() {
		return specificationValue;
	}
	
	public PIMDataType getPIMDataType() {
		return type;
	}

	public static FolderType fromSpecificationValue(String specificationValue){
		if (specValueToEnum.containsKey(specificationValue)) {
			return specValueToEnum.get(specificationValue);
		}
		return null;
	}
	
	private static Map<String, FolderType> specValueToEnum;
	
	static {
		Builder<String, FolderType> builder = ImmutableMap.builder();
		for (FolderType folderType : values()) {
			builder.put(folderType.specificationValue, folderType);
		}
		specValueToEnum = builder.build();
	}
	
	public boolean isCalendarFolder() {
		return  this == FolderType.DEFAULT_CALENDAR_FOLDER ||
				this == FolderType.USER_CREATED_CALENDAR_FOLDER;
	}
}
