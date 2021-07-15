/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

/**
 * MemoryPollingThread is triggered by (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)
 * This thread checks to see if any of the MemoryPoolMXBeans threshold(s) are 
 * exceeded. If still exceed, it continues to block instruments from running. 
 * This will prevent unwanted reporting side-effects and coordinated omissions 
 * data. When the threshold(s) are no longer exceeded, then the loop exits, 
 * releasing the lock on instruments, permitting them to continue. We may want 
 * to look into (MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)
 *
 */
public class MemoryPollingThread extends Thread {
    private int pollMemoryThresholdMillis = 6000;

    public MemoryPollingThread(int pollMemoryThresholdMillis) {
        this.setDaemon(true);
        this.setName("MemoryPollingThread");
        this.setPriority(Thread.MAX_PRIORITY);
        this.pollMemoryThresholdMillis = pollMemoryThresholdMillis;
    }

    public void run() {
        System.out.println("Fermata: PAUSE ALL OBSERVABILITY THREADS");
        //poll indefinitely every (default) 6 seconds to determine if all memory pools are freed up
        while (isMemoryPoolThresholdExceeded()) {
            try {
                System.out.println("indefinitely looping every " + pollMemoryThresholdMillis + " millis!");
                Thread.sleep(pollMemoryThresholdMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Tremolo: exited polling loop, CONTINUE ALL OBSERVABILITY THREADS!");
    }

    private boolean isMemoryPoolThresholdExceeded() {
        for (MemoryPoolMXBean membean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (membean.isUsageThresholdSupported()) {
                System.out.println("name: " + membean.getName());
                if (membean.isUsageThresholdExceeded()) {
                    return true;
                }
            }
        }
        return false;
    }
}