/*
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.util;

import org.canova.api.conf.Configuration;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * General reflection utils
 */

public class ReflectionUtils {

    private static final Class<?>[] EMPTY_ARRAY = new Class[]{};

    /**
     * Cache of constructors for each class. Pins the classes so they
     * can't be garbage collected until ReflectionUtils can be collected.
     */
    private static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE =
            new ConcurrentHashMap<>();


    /**
     * This code is to support backward compatibility and break the compile
     * time dependency of core on mapred.
     * This should be made deprecated along with the mapred package HADOOP-1230.
     * Should be removed when mapred package is removed.
     */
    private static void setJobConf(Object theObject, Configuration conf) {
        //If JobConf and JobConfigurable are in classpath, AND
        //theObject is of type JobConfigurable AND
        //conf is of type JobConf then
        //invoke configure on theObject
        try {
            Class<?> jobConfClass =
                    conf.getClassByName("org.apache.hadoop.mapred.JobConf");
            Class<?> jobConfigurableClass =
                    conf.getClassByName("org.apache.hadoop.mapred.JobConfigurable");
            if (jobConfClass.isAssignableFrom(conf.getClass()) &&
                    jobConfigurableClass.isAssignableFrom(theObject.getClass())) {
                Method configureMethod =
                        jobConfigurableClass.getMethod("configure", jobConfClass);
                configureMethod.invoke(theObject, conf);
            }
        } catch (ClassNotFoundException e) {
            //JobConf/JobConfigurable not in classpath. no need to configure
        } catch (Exception e) {
            throw new RuntimeException("Error in configuring object", e);
        }
    }

    /** Create an object for the given class and initialize it from conf
     *
     * @param theClass class of which an object is created
     * @param conf Configuration
     * @return a new object
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> theClass, Configuration conf) {
        T result;
        try {
            Constructor<T> meth = (Constructor<T>) CONSTRUCTOR_CACHE.get(theClass);
            if (meth == null) {
                meth = theClass.getDeclaredConstructor(EMPTY_ARRAY);
                meth.setAccessible(true);
                CONSTRUCTOR_CACHE.put(theClass, meth);
            }
            result = meth.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    static private ThreadMXBean threadBean =
            ManagementFactory.getThreadMXBean();

    public static void setContentionTracing(boolean val) {
        threadBean.setThreadContentionMonitoringEnabled(val);
    }

    private static String getTaskName(long id, String name) {
        if (name == null) {
            return Long.toString(id);
        }
        return id + " (" + name + ")";
    }

    /**
     * Print all of the thread's information and stack traces.
     *
     * @param stream the stream to
     * @param title a string title for the stack trace
     */
    public static void printThreadInfo(PrintWriter stream,
                                       String title) {
        final int STACK_DEPTH = 20;
        boolean contention = threadBean.isThreadContentionMonitoringEnabled();
        long[] threadIds = threadBean.getAllThreadIds();
        stream.println("Process Thread Dump: " + title);
        stream.println(threadIds.length + " active threads");
        for (long tid: threadIds) {
            ThreadInfo info = threadBean.getThreadInfo(tid, STACK_DEPTH);
            if (info == null) {
                stream.println("  Inactive");
                continue;
            }
            stream.println("Thread " +
                    getTaskName(info.getThreadId(),
                            info.getThreadName()) + ":");
            Thread.State state = info.getThreadState();
            stream.println("  State: " + state);
            stream.println("  Blocked count: " + info.getBlockedCount());
            stream.println("  Waited count: " + info.getWaitedCount());
            if (contention) {
                stream.println("  Blocked time: " + info.getBlockedTime());
                stream.println("  Waited time: " + info.getWaitedTime());
            }
            if (state == Thread.State.WAITING) {
                stream.println("  Waiting on " + info.getLockName());
            } else  if (state == Thread.State.BLOCKED) {
                stream.println("  Blocked on " + info.getLockName());
                stream.println("  Blocked by " +
                        getTaskName(info.getLockOwnerId(),
                                info.getLockOwnerName()));
            }
            stream.println("  Stack:");
            for (StackTraceElement frame: info.getStackTrace()) {
                stream.println("    " + frame.toString());
            }
        }
        stream.flush();
    }

    private static long previousLogTime = 0;


    /**
     * Return the correctly-typed {@link Class} of the given object.
     *
     * @param o object whose correctly-typed <code>Class</code> is to be obtained
     * @return the correctly typed <code>Class</code> of the given object.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getClass(T o) {
        return (Class<T>)o.getClass();
    }

    // methods to support testing
    static void clearCache() {
        CONSTRUCTOR_CACHE.clear();
    }

    static int getCacheSize() {
        return CONSTRUCTOR_CACHE.size();
    }




}