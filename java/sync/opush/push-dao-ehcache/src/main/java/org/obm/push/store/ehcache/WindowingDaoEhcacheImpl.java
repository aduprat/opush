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
package org.obm.push.store.ehcache;

import java.io.Serializable;
import java.util.Iterator;
import java.util.NoSuchElementException;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

import org.obm.push.bean.SyncKey;
import org.obm.push.mail.EmailChanges;
import org.obm.push.mail.EmailChanges.Builder;
import org.obm.push.mail.EmailChanges.Splitter;
import org.obm.push.mail.bean.WindowingIndexKey;
import org.obm.push.mail.bean.WindowingKey;
import org.obm.push.store.WindowingDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class WindowingDaoEhcacheImpl implements WindowingDao {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final PartitionDao partitionDao;
	
	@Inject
	@VisibleForTesting WindowingDaoEhcacheImpl(PartitionDao partitionDao) {
		this.partitionDao = partitionDao;
	}
	
	@Override
	public EmailChanges popNextPendingElements(WindowingKey key, int maxSize, SyncKey newSyncKey) {
		Preconditions.checkArgument(key != null);
		Preconditions.checkArgument(maxSize > 0);
		Preconditions.checkArgument(newSyncKey != null);

		WindowingIndexKey windowingIndexKey = windowingIndexKey(key);
		logger.info("retrieve a maximum of {} changes for key {}", maxSize, windowingIndexKey);
		
		EmailChanges changes = getEnoughChunks(windowingIndexKey, maxSize);
		Splitter splittedToFitWindowSize = splitToFitWindowSize(changes, maxSize);
		partitionDao.pushNextRequestPendingElements(windowingIndexKey, newSyncKey, splittedToFitWindowSize.getLeft());
		return splittedToFitWindowSize.getFit();
	}

	private Splitter splitToFitWindowSize(EmailChanges changes, int maxSize) {
		Splitter parts = changes.splitToFit(maxSize);

		logger.info("a chunk has been splitted, fit:{} and left:{}", parts.getFit(), parts.getLeft());
		
		return parts;
	}

	private EmailChanges getEnoughChunks(WindowingIndexKey key, int maxSize) {
		Builder builder = EmailChanges.builder();
		for (EmailChanges changes: partitionDao.consumingChunksIterable(key)) {

			logger.info("a chunk is retrieved {}", changes);
			
			builder.merge(changes);
			if (builder.sumOfChanges() >= maxSize) {
				break;
			}
		}
		return builder.build();
	}
	
	@Override
	public void pushPendingElements(WindowingKey key, SyncKey syncKey, EmailChanges changes, int windowSize) {
		WindowingIndexKey windowingIndexKey = windowingIndexKey(key);
		logger.info("pushing windowing elements, key:{}, syncKey:{}, changes:{}, windowSize:{}", 
				windowingIndexKey, syncKey, changes, windowSize);
		
		for (EmailChanges chunk: ImmutableList.copyOf(changes.partition(windowSize)).reverse()) {
			partitionDao.pushPendingElements(windowingIndexKey, syncKey, chunk);
		}
	}

	private WindowingIndexKey windowingIndexKey(WindowingKey key) {
		WindowingIndexKey windowingIndexKey = new WindowingIndexKey(key.getUser(), key.getDeviceId(), key.getCollectionId());
		return windowingIndexKey;
	}

	@Override
	public boolean hasPendingElements(WindowingKey key) {
		WindowingIndexKey windowingIndexKey = windowingIndexKey(key);
		SyncKey syncKey = key.getSyncKey();
		SyncKey windowingSyncKey = partitionDao.getWindowingSyncKey(windowingIndexKey);
		
		if (windowingSyncKey == null) {
			logger.info("no pending windowing for key {}", key);
			return false;
		} else if(!windowingSyncKey.equals(syncKey)) {
			logger.info("reseting a pending windowing for key {} and syncKey {} by a new syncKey {}",
					key, windowingSyncKey, syncKey);
			partitionDao.removePreviousCollectionWindowing(windowingIndexKey);
			return false;
		} else {
			logger.info("there is a pending windowing for key {}, syncKey is {}", key, windowingSyncKey);
			return true;
		}
	}

	public Cache getChunksStore() {
		return partitionDao.chunksStore;
	}

	public Cache getIndexStore() {
		return partitionDao.indexStore;
	}
	
	@Singleton
	public static class PartitionDao {
		
		private final Logger logger = LoggerFactory.getLogger(getClass());
		private final Cache chunksStore;
		private final Cache indexStore;
		
		@Inject
		@VisibleForTesting PartitionDao(StoreManager objectStoreManager, CacheEvictionListener cacheEvictionListener) {
			chunksStore = objectStoreManager.getStore(EhCacheStores.MAIL_WINDOWING_CHUNKS_STORE);
			chunksStore.getCacheEventNotificationService().registerListener(cacheEvictionListener);
			indexStore = objectStoreManager.getStore(EhCacheStores.MAIL_WINDOWING_INDEX_STORE);
			indexStore.getCacheEventNotificationService().registerListener(cacheEvictionListener);
		}
	
		public Iterable<EmailChanges> consumingChunksIterable(final WindowingIndexKey key) {
			return new Iterable<EmailChanges>() {
				@Override
				public Iterator<EmailChanges> iterator() {
					return new Iterator<EmailChanges>() {
	
						@Override
						public boolean hasNext() {
							return getWindowingIndex(key) != null;
						}
	
						@Override
						public EmailChanges next() {
							WindowingIndex index = getWindowingIndex(key);
							if (index == null) {
								throw new NoSuchElementException();
							}
							EmailChanges emailChanges = consumeChunk(key, index);
							udpateIndex(key, index);
							return emailChanges;
						}
	
						private EmailChanges consumeChunk(WindowingIndexKey key, WindowingIndex index) {
							ChunkKey chunkKey = new ChunkKey(key, index.getIndex());
							Element emailChanges = chunksStore.get(chunkKey);
							chunksStore.remove(chunkKey);
							return (EmailChanges) emailChanges.getObjectValue();
						}
	
						private void udpateIndex(final WindowingIndexKey key, WindowingIndex index) {
							if (index.getIndex() == 0) {
								indexStore.remove(key);
							} else {
								indexStore.replace(new Element(key, index.nextToBeRetrieved()));
							}
						}
	
						@Override
						public void remove() {
							throw new UnsupportedOperationException();
						}
					};
				}
			};
		}
		
		public void pushNextRequestPendingElements(WindowingIndexKey indexKey, SyncKey syncKey, EmailChanges partition) {
			WindowingIndex windowingIndex = getWindowingIndex(indexKey);
			if (partition.hasChanges()) {
				pushNextChunk(indexKey, partition, nextToBeStored(indexKey, syncKey));
			} else if (windowingHasDataRemaining(windowingIndex)) {
				indexStore.put(new Element(indexKey, windowingIndex.nextSyncKey(syncKey)));
			}
		}
		
		private boolean windowingHasDataRemaining(WindowingIndex windowingIndex) {
			return windowingIndex != null;
		}
	
		public void pushPendingElements(WindowingIndexKey indexKey, SyncKey syncKey, EmailChanges partition) {
			pushNextChunk(indexKey, partition, nextToBeStored(indexKey, syncKey));
		}
	
		private WindowingIndex nextToBeStored(WindowingIndexKey indexKey, SyncKey syncKey) {
			WindowingIndex windowingIndex = getWindowingIndex(indexKey);
			if (windowingIndex == null) {
				return new WindowingIndex(0, syncKey);
			} else {
				return windowingIndex.nextToBeStored(syncKey);
			}
		}
	
		private void pushNextChunk(WindowingIndexKey indexKey, EmailChanges partition, WindowingIndex nextIndex) {
			logger.debug("put windowing EmailChanges with key {} : {}", indexKey, partition);
			chunksStore.put(new Element(new ChunkKey(indexKey, nextIndex.getIndex()), partition));
			indexStore.put(new Element(indexKey, nextIndex));
		}
		
		private WindowingIndex getWindowingIndex(WindowingIndexKey key) {
			Element indexElement = indexStore.get(key);
			return (WindowingIndex) (indexElement != null ? indexElement.getObjectValue() : null);
		}
	
		public SyncKey getWindowingSyncKey(WindowingIndexKey key) {
			WindowingIndex windowingIndex = getWindowingIndex(key);
			if (windowingIndex != null) {
				return windowingIndex.getSyncKey();
			}
			return null;
		}
	
		public void removePreviousCollectionWindowing(WindowingIndexKey key) {
			WindowingIndex windowingIndex = getWindowingIndex(key);
			if (windowingIndex != null) {
				indexStore.remove(key);
				removePreviousCollectionChunks(key, windowingIndex);
			}
		}
	
		private void removePreviousCollectionChunks(WindowingIndexKey key, WindowingIndex startingIndex) {
			WindowingIndex indexToRemove = startingIndex;
			while (indexToRemove != null) {
				chunksStore.remove(new ChunkKey(key, indexToRemove.getIndex()));
				indexToRemove = indexToRemove.nextToBeRetrieved();
			}
		}
	}

	public static ChunkKey chunkKey(WindowingIndexKey key, int index) {
		return new ChunkKey(key, index);
	}
	@VisibleForTesting static class ChunkKey implements Serializable {
		
		private static final long serialVersionUID = -3718387421101858836L;
		
		private final WindowingIndexKey key;
		private final int index;
		
		public ChunkKey(WindowingIndexKey key, int index) {
			this.key = key;
			this.index = index;
		}
		
		@Override
		public final int hashCode(){
			return Objects.hashCode(index, key);
		}
		
		@Override
		public final boolean equals(Object object){
			if (object instanceof ChunkKey) {
				ChunkKey that = (ChunkKey) object;
				return Objects.equal(this.index, that.index)
					&& Objects.equal(this.key, that.key);
			}
			return false;
		}		
		
	}

	public static WindowingIndex windowingIndex(int index, SyncKey syncKey) {
		return new WindowingIndex(index, syncKey);
	}
	@VisibleForTesting static class WindowingIndex implements Serializable {
		
		private static final long serialVersionUID = -3833313956262343686L;
		
		private final int index;
		private final SyncKey syncKey;
		
		public WindowingIndex(int index, SyncKey syncKey) {
			Preconditions.checkArgument(index >= 0, "illegal index");
			Preconditions.checkArgument(syncKey != null && syncKey != SyncKey.INITIAL_FOLDER_SYNC_KEY, "illegal syncKey");
			this.index = index;
			this.syncKey = syncKey;
		}

		public int getIndex() {
			return index;
		}

		public SyncKey getSyncKey() {
			return syncKey;
		}

		@Override
		public final int hashCode(){
			return Objects.hashCode(index, syncKey);
		}
		
		@Override
		public final boolean equals(Object object){
			if (object instanceof WindowingIndex) {
				WindowingIndex that = (WindowingIndex) object;
				return Objects.equal(this.index, that.index)
					&& Objects.equal(this.syncKey, that.syncKey);
			}
			return false;
		}

		@Override
		public String toString() {
			return Objects.toStringHelper(this)
				.add("index", index)
				.add("syncKey", syncKey)
				.toString();
		}

		public WindowingIndex nextToBeStored(SyncKey nextSyncKey) {
			return new WindowingIndex(index +1, nextSyncKey);
		}
		
		public WindowingIndex nextToBeRetrieved() {
			if (index > 0) {
				return new WindowingIndex(index -1, syncKey);
			} else {
				return null;
			}
		}
		
		public Serializable nextSyncKey(SyncKey newSyncKey) {
			return new WindowingIndex(index , newSyncKey);
		}
	}
}
