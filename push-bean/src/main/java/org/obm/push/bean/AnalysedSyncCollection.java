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

import java.io.Serializable;
import java.util.List;

import org.obm.push.bean.change.SyncCommand;
import org.obm.push.exception.activesync.ASRequestIntegerFieldException;
import org.obm.push.exception.activesync.ASRequestStringFieldException;
import org.obm.push.protocol.bean.CollectionId;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;

public class AnalysedSyncCollection implements SyncDefaultValues, Serializable {
	
	private static final long serialVersionUID = 348968178554764052L;

	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		private PIMDataType dataType;
		private SyncKey syncKey;
		private CollectionId collectionId;
		private String collectionPath;
		private Boolean deletesAsMoves;
		private Boolean changes;
		private Integer windowSize;
		private SyncCollectionOptions options;
		private TypedCommandsIndex.Builder<SyncCollectionCommandRequest> commands;
		private SyncStatus status;

		private Builder() {
			commands = TypedCommandsIndex.builder();
		}
		
		public Builder dataType(PIMDataType dataType) {
			this.dataType = dataType;
			return this;
		}
		
		public Builder syncKey(SyncKey syncKey) {
			this.syncKey = syncKey;
			return this;
		}
		
		public Builder collectionId(CollectionId collectionId) {
			this.collectionId = collectionId;
			return this;
		}
		
		public Builder collectionPath(String collectionPath) {
			this.collectionPath = collectionPath;
			return this;
		}

		public Builder deletesAsMoves(Boolean deletesAsMoves) {
			this.deletesAsMoves = deletesAsMoves;
			return this;
		}
		
		public Builder changes(Boolean changes) {
			this.changes = changes;
			return this;
		}
		
		public Builder windowSize(Integer windowSize) {
			this.windowSize = windowSize;
			return this;
		}
		
		public Builder options(SyncCollectionOptions options) {
			this.options = options;
			return this;
		}

		public Builder command(SyncCollectionCommandRequest command) {
			Preconditions.checkNotNull(command);
			this.commands.addCommand(command);
			return this;
		}
		
		public Builder commands(List<SyncCollectionCommandRequest> commands) {
			Preconditions.checkNotNull(commands);
			this.commands.addCommands(commands);
			return this;
		}
		
		public Builder status(SyncStatus status) {
			this.status = status;
			return this;
		}
		
		private void checkSyncCollectionCommonElements() {
			if (collectionId == null) {
				throw new ASRequestIntegerFieldException("Collection id field is required");
			}
			if (syncKey == null) {
				throw new ASRequestStringFieldException("Sync Key field is required");
			}
		}
		
		public AnalysedSyncCollection build() {
			checkSyncCollectionCommonElements();
			return new AnalysedSyncCollection(dataType, syncKey, collectionId, 
					collectionPath, deletesAsMoves, changes, Optional.fromNullable(windowSize), 
					Objects.firstNonNull(options, SyncCollectionOptions.defaultOptions()), 
					commands.build(),
					status);
		}

	}
	
	
	private final PIMDataType dataType;
	private final SyncKey syncKey;
	private final CollectionId collectionId;
	private final TypedCommandsIndex<SyncCollectionCommandRequest> commands;
	private final String collectionPath;
	private final Boolean deletesAsMoves;
	private final Boolean changes;
	private final Optional<Integer> windowSize;
	private final SyncCollectionOptions options;
	private final SyncStatus status;
	
	protected AnalysedSyncCollection(PIMDataType dataType, SyncKey syncKey, CollectionId collectionId,
			String collectionPath, Boolean deletesAsMoves, Boolean changes, Optional<Integer> windowSize, 
			SyncCollectionOptions options, TypedCommandsIndex<SyncCollectionCommandRequest> commands, SyncStatus status) {
		
		this.dataType = dataType;
		this.syncKey = syncKey;
		this.collectionId = collectionId;
		this.collectionPath = collectionPath;
		this.deletesAsMoves = deletesAsMoves;
		this.changes = changes;
		this.windowSize = windowSize;
		this.options = options;
		this.commands = commands;
		this.status = status;
	}

	public Iterable<SyncCollectionCommandRequest> getCommands() {
		return commands;
	}

	public CollectionId getCollectionId() {
		return collectionId;
	}
	
	public String getCollectionPath() {
		return collectionPath;
	}
	
	public PIMDataType getDataType() {
		return dataType;
	}
	
	public Boolean getDeletesAsMoves() {
		return deletesAsMoves;
	}
	
	public List<ServerId> getFetchIds() {
		return FluentIterable.from(
				commands.getCommandsForType(SyncCommand.FETCH))
				.transform(new Function<SyncCollectionCommandRequest, ServerId>() {
					@Override
					public ServerId apply(SyncCollectionCommandRequest input) {
						return input.getServerId();
					}
				}).toList();
	}
	
	public SyncKey getSyncKey() {
		return syncKey;
	}
	
	public Boolean isChanges() {
		return changes;
	}

	public Optional<Integer> getWindowSize() {
		return windowSize;
	}

	public SyncCollectionOptions getOptions() {
		return options;
	}

	public boolean hasOptions() {
		return options != null;
	}

	public SyncStatus getStatus() {
		return status;
	}
	
	public Summary getSummary() {
		return commands.getSummary();
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(dataType, syncKey, collectionId, commands, collectionPath, deletesAsMoves, changes, windowSize, options, status);
	}
	
	@Override
	public boolean equals(Object object) {
		if (object instanceof AnalysedSyncCollection) {
			AnalysedSyncCollection that = (AnalysedSyncCollection) object;
			return Objects.equal(this.dataType, that.dataType)
					&& Objects.equal(this.syncKey, that.syncKey)
					&& Objects.equal(this.collectionId, that.collectionId)
					&& Objects.equal(this.commands, that.commands)
					&& Objects.equal(this.collectionPath, that.collectionPath)
					&& Objects.equal(this.deletesAsMoves, that.deletesAsMoves)
					&& Objects.equal(this.changes, that.changes)
					&& Objects.equal(this.windowSize, that.windowSize)
					&& Objects.equal(this.options, that.options)
					&& Objects.equal(this.status, that.status);
		}
		return false;
	}


	@Override
	public String toString() {
		return Objects.toStringHelper(this)
				.add("dataType", dataType)
				.add("syncKey", syncKey)
				.add("collectionId", collectionId)
				.add("commands", commands)
				.add("collectionPath", collectionPath)
				.add("deletesAsMoves", deletesAsMoves)
				.add("changes", changes)
				.add("windowSize", windowSize)
				.add("options", options)
				.add("status", status)
			.toString();
	}

}
