/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.common.internal.metrics.stmtmetrics;

import com.espertech.esper.common.client.metric.EPMetricsStatement;
import com.espertech.esper.common.client.metric.StatementMetric;
import com.espertech.esper.common.internal.util.DeploymentIdNamePair;
import com.espertech.esper.common.internal.util.ManagedReadWriteLock;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Holder for statement group's statement metrics.
 * <p>
 * Changes to StatementMetric instances must be done in a read-lock:
 * <pre>
 * getRwLock.readLock.lock()
 * metric = getAddMetric(index)
 * metric.accountFor(cpu, wall, etc)
 * getRwLock.readLock.unlock()
 * </pre>
 * <p>
 * All other changes are done under write lock for this class.
 * <p>
 * This is a collection backed by an array that grows by 50% each time expanded, maintains a free/busy list of statement names,
 * maintains an element number of last used element.
 * <p>
 * The flush operaton copies the complete array, thereby keeping array size. Statement names are only removed on the next flush.
 */
public class StatementMetricArray {
    private final String runtimeURI;

    // Lock
    //  Read lock applies to each current transaction on a StatementMetric instance
    //  Write lock applies to flush and to add a new statement
    private final ManagedReadWriteLock rwLock;
    private final String name;
    private final boolean isReportInactive;

    // Active statements
    private DeploymentIdNamePair[] statementNames;

    // Count of active statements
    private int currentLastElement;

    // Flushed metric per statement
    private volatile StatementMetric[] metrics;

    // Statements ids to remove with the next flush
    private Set<DeploymentIdNamePair> removedStatementNames;

    /**
     * Ctor.
     *
     * @param runtimeURI       runtime URI
     * @param name             name of statement group
     * @param initialSize      initial size of array
     * @param isReportInactive true to indicate to report on inactive statements
     */
    public StatementMetricArray(String runtimeURI, String name, int initialSize, boolean isReportInactive) {
        this.runtimeURI = runtimeURI;
        this.isReportInactive = isReportInactive;
        this.name = name;

        metrics = new StatementMetric[initialSize];
        statementNames = new DeploymentIdNamePair[initialSize];
        currentLastElement = -1;
        rwLock = new ManagedReadWriteLock("StatementMetricArray-" + name, true);
        removedStatementNames = new HashSet<>();
    }

    /**
     * Remove a statement.
     * <p>
     * Next flush actually frees the slot that this statement occupied.
     *
     * @param statement to remove
     */
    public void removeStatement(DeploymentIdNamePair statement) {
        rwLock.acquireWriteLock();
        try {
            removedStatementNames.add(statement);

            if (removedStatementNames.size() > 1000) {
                for (int i = 0; i <= currentLastElement; i++) {
                    if (removedStatementNames.contains(statementNames[i])) {
                        statementNames[i] = null;
                    }
                }
                removedStatementNames.clear();
            }
        } finally {
            rwLock.releaseWriteLock();
        }
    }

    /**
     * Adds a statement and returns the index added at.
     * <p>
     * May reuse an empty slot, grow the underlying array, or append to the end.
     *
     * @param statement deployment-id and name pair
     * @return index added to
     */
    public int addStatementGetIndex(DeploymentIdNamePair statement) {
        rwLock.acquireWriteLock();
        try {
            // see if there is room
            if ((currentLastElement + 1) < metrics.length) {
                currentLastElement++;
                statementNames[currentLastElement] = statement;
                return currentLastElement;
            }

            // no room, try to use an existing slot of a removed statement
            for (int i = 0; i < statementNames.length; i++) {
                if (statementNames[i] == null) {
                    statementNames[i] = statement;
                    if ((i + 1) > currentLastElement) {
                        currentLastElement = i;
                    }
                    return i;
                }
            }

            // still no room, expand storage by 50%
            int newSize = (int) (metrics.length * 1.5);
            DeploymentIdNamePair[] newStatementNames = new DeploymentIdNamePair[newSize];
            StatementMetric[] newMetrics = new StatementMetric[newSize];
            System.arraycopy(statementNames, 0, newStatementNames, 0, statementNames.length);
            System.arraycopy(metrics, 0, newMetrics, 0, metrics.length);

            statementNames = newStatementNames;
            metrics = newMetrics;

            currentLastElement++;
            statementNames[currentLastElement] = statement;

            return currentLastElement;
        } finally {
            rwLock.releaseWriteLock();
        }
    }

    /**
     * Flushes the existing metrics via array copy and swap.
     * <p>
     * May report all statements (empty and non-empty slots) and thereby null values.
     * <p>
     * Returns null to indicate no reports to do.
     *
     * @return metrics
     */
    public StatementMetric[] flushMetrics() {
        rwLock.acquireWriteLock();
        try {
            boolean isEmpty = currentLastElement == -1;

            housekeeping();

            if (isEmpty) {
                return null;    // no copies made if empty collection
            }

            // perform flush
            StatementMetric[] newMetrics = new StatementMetric[metrics.length];
            StatementMetric[] oldMetrics = metrics;
            metrics = newMetrics;
            return oldMetrics;
        } finally {
            rwLock.releaseWriteLock();
        }
    }

    /**
     * Returns the read-write lock, for read-lock when modifications are made.
     *
     * @return lock
     */
    public ManagedReadWriteLock getRwLock() {
        return rwLock;
    }

    /**
     * Returns an existing or creates a new statement metric for the index.
     *
     * @param index of statement
     * @return metric to modify under read lock
     */
    public StatementMetric getAddMetric(int index) {
        StatementMetric metric = metrics[index];
        if (metric == null) {
            metric = new StatementMetric(runtimeURI, statementNames[index].getDeploymentId(), statementNames[index].getName());
            metrics[index] = metric;
        }
        return metric;
    }

    /**
     * Returns maximum collection size (last used element), which may not truely reflect the number
     * of actual statements held as some slots may empty up when statements are removed.
     *
     * @return known maximum size
     */
    public int sizeLastElement() {
        return currentLastElement + 1;
    }

    public String getName() {
        return name;
    }

    public void iterate(Consumer<EPMetricsStatement> consumer) {
        rwLock.acquireReadLock();
        try {
            housekeeping();
            for (StatementMetric metric : metrics) {
                if (metric != null) {
                    consumer.accept(new EPMetricsStatement(metric));
                }
            }
        } finally {
            rwLock.releaseReadLock();
        }
    }

    private void housekeeping() {
        // first fill in the blanks if there are no reports and we report inactive statements
        if (isReportInactive) {
            for (int i = 0; i <= currentLastElement; i++) {
                if (statementNames[i] != null) {
                    metrics[i] = new StatementMetric(runtimeURI, statementNames[i].getDeploymentId(), statementNames[i].getName());
                }
            }
        }

        // remove statement ids that disappeared during the interval
        if ((currentLastElement > -1) && (!removedStatementNames.isEmpty())) {
            for (int i = 0; i <= currentLastElement; i++) {
                if (removedStatementNames.contains(statementNames[i])) {
                    statementNames[i] = null;
                }
            }
        }

        // adjust last used element
        while ((currentLastElement != -1) && (statementNames[currentLastElement] == null)) {
            currentLastElement--;
        }
    }

    public boolean isReportInactive() {
        return isReportInactive;
    }
}
