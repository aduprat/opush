/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2015  Linagora
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
package org.obm.push;

import java.util.Iterator;
import java.util.List;

import org.obm.push.backend.IHierarchyExporter;
import org.obm.push.backend.PIMBackend;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.hierarchy.BackendFolder;
import org.obm.push.bean.change.hierarchy.BackendFolder.BackendId;
import org.obm.push.bean.change.hierarchy.BackendFolders;
import org.obm.push.bean.change.hierarchy.FolderCreateRequest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class HierarchyExporter implements IHierarchyExporter {

	private final Backends backends;

	@Inject
	@VisibleForTesting HierarchyExporter(Backends backends) {
		this.backends = backends;
	}

	@Override
	public BackendFolders getBackendFolders(UserDataRequest udr) {
		
		final List<BackendFolders> allFoldersIterables = Lists.newArrayList();
		for (PIMBackend backend: backends) {
			allFoldersIterables.add(backend.getBackendFolders(udr));
			
		}
		return new BackendFolders() {

			@Override
			public Iterator<BackendFolder> iterator() {
				return Iterables.concat(allFoldersIterables).iterator();
			}
		};
	}
	
	@Override
	public BackendId createFolder(UserDataRequest udr, FolderCreateRequest folderCreateRequest,
			Optional<BackendId> parentBackendId) {
		return backends.getBackend(folderCreateRequest.getFolderType().getPIMDataType())
			.createFolder(udr, folderCreateRequest, parentBackendId);
	}
}
