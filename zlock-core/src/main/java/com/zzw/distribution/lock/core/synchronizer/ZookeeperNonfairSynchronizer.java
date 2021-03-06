/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zzw.distribution.lock.core.synchronizer;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zookeeper 提供的锁
 *
 * @author zhaozhiwei
 * @date 2019/5/29 15:08
 */
public class ZookeeperNonfairSynchronizer extends AbstractSynchronizer implements ZlockSynchronizer {

    private final CuratorFramework client;
    private static final ConcurrentHashMap<String, String> KEY_CACHE = new ConcurrentHashMap<>();

    public ZookeeperNonfairSynchronizer(CuratorFramework client, String lockName) {
        this.lockName = lockName;
        this.client = client;
        try {
            if (client.checkExists().forPath(BASE_LOCK_DIR) == null) {
                this.client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT)
                        .forPath(BASE_LOCK_DIR);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public boolean release(int arg) {
        String currentLock = KEY_CACHE.get(lockName);
        if (currentLock == null) {
            return true;
        }
        try {
            client.delete().forPath(currentLock);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean tryAcquire(int arg) {
        String fullLockName = BASE_LOCK_DIR + SEPARATOR + lockName + SEPARATOR + lockName;
        try {
            String currentLock = client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                    .forPath(fullLockName, uuid.getBytes());
            List<String> locks = client.getChildren().forPath(BASE_LOCK_DIR + SEPARATOR + lockName);
            if (locks != null && locks.size() > 0) {
                String firstLock = locks.get(0);
                if (Objects.equals(currentLock, firstLock)) {
                    KEY_CACHE.put(lockName, currentLock);
                    return true;
                } else {
                    client.delete().forPath(currentLock);
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public void extend() {

    }
}
