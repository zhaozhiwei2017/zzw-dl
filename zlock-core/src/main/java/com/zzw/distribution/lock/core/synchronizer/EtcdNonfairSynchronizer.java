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

import com.google.common.base.Charsets;
import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;

import java.util.List;

/**
 * Etcd 服务提供的锁
 *
 * @author zhaozhiwei
 * @date 2019/5/29 15:08
 */
public class EtcdNonfairSynchronizer extends AbstractSynchronizer implements ZlockSynchronizer {

    /**
     * 池化管理器
     */
    private final Client client;

    public EtcdNonfairSynchronizer(Client client, String lockName) {
        this.lockName = lockName;
        this.client = client;
    }

    @Override
    public boolean release(int arg) {
        KV kvClient = client.getKVClient();
        String fullLockName = BASE_LOCK_DIR + SEPARATOR + lockName + SEPARATOR + uuid;
        try {
            long count = kvClient.get(getByteSeq(fullLockName)).get().getCount();
            if (count > 0) {
                kvClient.delete(getByteSeq(fullLockName)).get();
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return true;
    }

    @Override
    public boolean tryAcquire(int arg) {
        KV kvClient = client.getKVClient();
        Lease leaseClient = client.getLeaseClient();
        PutResponse res;
        String fullLockName = BASE_LOCK_DIR + SEPARATOR + lockName + SEPARATOR + uuid;
        boolean result = false;
        try {
            long initLeaseId = leaseClient.grant(initTime).get().getID();
            res = kvClient.put(getByteSeq(fullLockName), getByteSeq(uuid),
                    PutOption.newBuilder().withLeaseId(initLeaseId).build()).get();
            long revision = res.getHeader().getRevision();
            List<KeyValue> kvs = kvClient.get(getByteSeq(BASE_LOCK_DIR + SEPARATOR + lockName),
                    GetOption.newBuilder().withPrefix(getByteSeq(BASE_LOCK_DIR + SEPARATOR + lockName))
                            .withSortField(GetOption.SortTarget.CREATE).build())
                    .get().getKvs();
            if (revision == kvs.get(0).getCreateRevision()) {
                result = true;
            } else {
                kvClient.delete(getByteSeq(fullLockName));
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        if (result) {
            addTask(lockName);
        }
        return result;
    }

    @Override
    public void extend() {
        KV kvClient = client.getKVClient();
        Lease leaseClient = client.getLeaseClient();
        String fullLockName = BASE_LOCK_DIR + SEPARATOR + lockName + SEPARATOR + uuid;
        try {
            List<KeyValue> kvs = kvClient.get(getByteSeq(fullLockName)).get().getKvs();
            if (kvs != null && kvs.size() > 0) {
                KeyValue keyValue = kvs.get(0);
                long leaseId = keyValue.getLease();
                long ttl = leaseClient.keepAliveOnce(leaseId).get().getTTL();
                if (ttl == initTime) {
                    long newLeaseId = leaseClient.grant(extendTime).get().getID();
                    kvClient.put(keyValue.getKey(), keyValue.getValue(),
                            PutOption.newBuilder().withLeaseId(newLeaseId).build());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private ByteSequence getByteSeq(String str) {
        return ByteSequence.from(str, Charsets.UTF_8);
    }

}
