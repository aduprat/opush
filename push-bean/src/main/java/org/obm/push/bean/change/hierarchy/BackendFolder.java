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
package org.obm.push.bean.change.hierarchy;

import org.obm.push.bean.FolderType;
import org.obm.push.bean.Stringable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class BackendFolder<T extends Stringable> {
	
	public static <T extends Stringable> Builder<T> builder() {
		return new Builder<T>();
	}
	
	public static class Builder<T extends Stringable> {

		private T backendId;
		private Optional<T> parentId;
		private String displayName;
		private FolderType folderType;
		
		private Builder() {
			super();
		}

		public Builder<T> backendId(T backendId) {
			Preconditions.checkNotNull(backendId);
			this.backendId = backendId;
			return this;
		}

		public Builder<T> displayName(String displayName) {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(displayName));
			this.displayName = displayName;
			return this;
		}

		public Builder<T> folderType(FolderType folderType) {
			Preconditions.checkNotNull(folderType);
			this.folderType = folderType;
			return this;
		}
		
		public Builder<T> parentId(Optional<T> parentId) {
			this.parentId = parentId;
			return this;
		}

		public BackendFolder<T> build() {
			Preconditions.checkNotNull(backendId);
			Preconditions.checkNotNull(parentId);
			Preconditions.checkNotNull(folderType);
			Preconditions.checkArgument(!Strings.isNullOrEmpty(displayName));
			
			return new BackendFolder<T>(backendId, parentId, displayName, folderType);
		}
	}
	
	private final T backendId;
	private final Optional<T> parentId;
	private final String displayName;
	private final FolderType folderType;
	
	public BackendFolder(T backendId, Optional<T> parentId, String displayName, FolderType folderType) {
		this.backendId = backendId;
		this.parentId = parentId;
		this.displayName = displayName;
		this.folderType = folderType;
	}
	
	public T getBackendId() {
		return backendId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public FolderType getFolderType() {
		return folderType;
	}

	public Optional<T> getParentBackendId() {
		return parentId;
	}

	@Override
	public final int hashCode(){
		return Objects.hashCode(backendId, displayName, folderType, parentId);
	}
	
	@Override
	public final boolean equals(Object object){
		if (object instanceof BackendFolder) {
			BackendFolder<?> that = (BackendFolder<?>) object;
			return Objects.equal(this.backendId, that.backendId)
				&& Objects.equal(this.displayName, that.displayName)
				&& Objects.equal(this.folderType, that.folderType)
				&& Objects.equal(this.parentId, that.parentId);
		}
		return false;
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("backendId", backendId)
			.add("displayName", displayName)
			.add("itemType", folderType)
			.add("parentId", parentId)
			.toString();
	}
}