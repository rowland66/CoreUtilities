package org.rowland.jinix.coreutilities.jsh;

import java.util.*;

public class JobMap {

    private LinkedHashMap<Integer, Jsh.Job> jobIdToJobMap = new LinkedHashMap<>(16);
    private HashMap<Integer, Jsh.Job> processGroupIdToJobMap = new HashMap<>(16);
    private HashMap<Integer, Jsh.Job> processIdToJobMap = new HashMap<>(32);
    private Integer lastKey = null;

    int add(Jsh.Job job) {
        if (lastKey == null) {
            lastKey = new Integer(1);
        } else {
            lastKey = lastKey + 1;
        }
        processGroupIdToJobMap.put(job.processGroupId, job);
        jobIdToJobMap.put(lastKey, job);
        for (Integer pid : job.pidList) {
            processIdToJobMap.put(pid, job);
        }

        return lastKey;
    }

    Jsh.Job getByProcessGroupId(int processGroupId) {
        return processGroupIdToJobMap.get(processGroupId);
    }

    Jsh.Job getByJobId(int jobId) {
        return jobIdToJobMap.get(jobId);
    }

    Jsh.Job getByProcessId(int pid) {
        return processIdToJobMap.get(pid);
    }

    Jsh.Job getByCmdLine(String cmdLine) {
        for (Jsh.Job j : jobIdToJobMap.values()) {
            if (j.cmdLine().startsWith(cmdLine)) {
                return j;
            }
        }
        return null;
    }

    Jsh.Job removeByProcessGroupId(int processGroupId) {

        if (!processGroupIdToJobMap.containsKey(processGroupId)) {
            return null;
        }

        Jsh.Job rtrnValue = processGroupIdToJobMap.remove(processGroupId);

        Integer jobId = null;
        for (Map.Entry<Integer,Jsh.Job> entry : jobIdToJobMap.entrySet()) {
            if (entry.getValue() == rtrnValue) {
                jobId = entry.getKey();
                break;
            }
        }

        jobIdToJobMap.remove(jobId);

        for (Integer pid : rtrnValue.pidList) {
            processIdToJobMap.remove(pid);
        }

        if (jobIdToJobMap.isEmpty()) {
            lastKey = null;
        } else {
            int maxKey = 0;
            for (Integer i : jobIdToJobMap.keySet()) {
                if (i > maxKey) maxKey = i;
            }
            lastKey = maxKey;
        }
        return rtrnValue;
    }

    Collection<Jsh.Job> jobList() {
        return jobIdToJobMap.values();
    }
}
