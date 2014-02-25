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

import net.sf.ehcache.Element;

import org.obm.push.bean.SnapshotKey;
import org.obm.push.bean.SyncKey;
import org.obm.push.exception.DaoException;
import org.obm.push.mail.bean.Snapshot;
import org.obm.push.store.SnapshotDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SnapshotDaoEhcacheImpl extends AbstractEhcacheDao implements SnapshotDao {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Inject  SnapshotDaoEhcacheImpl(StoreManager objectStoreManager, CacheEvictionListener cacheEvictionListener) {
		super(objectStoreManager, cacheEvictionListener);
	}
	
	@Override
	protected String getStoreName() {
		return EhCacheStores.MAIL_SNAPSHOT_STORE;
	}

	@Override
	public Snapshot get(SnapshotKey key) {
		Element element = store.get(key);
		logger.debug("Get snapshot with key {} : {}", key, element);
		if (element != null) {
			return (Snapshot) element.getObjectValue();
		}
		return null;
	}

	@Override
	public void put(SnapshotKey key, Snapshot snapshot) {
		Preconditions.checkNotNull(key);
		Preconditions.checkNotNull(snapshot);
		logger.debug("put snapshot with key {} : {}", key, snapshot);
		store.put(new Element(key, snapshot));
	}

	@Override
	public void linkSyncKeyToSnapshot(SyncKey synckey, SnapshotKey snapshotKey) throws DaoException {
		Snapshot snapshot = get(snapshotKey);
		if (snapshot == null) {
			throw new DaoException("Not snapshot found for snapshot key " + snapshotKey);
		}
		
		SnapshotKey newSnapshotKey = SnapshotKey.builder()
				.collectionId(snapshotKey.getCollectionId())
				.deviceId(snapshotKey.getDeviceId())
				.syncKey(synckey)
				.build();
		put(newSnapshotKey, snapshot);
	}

}
