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
package com.espertech.esper.common.client.fireandforget;

import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.context.ContextPartitionSelector;

/**
 * Interface for a prepared on-demand query that can be executed multiple times.
 */
public interface EPFireAndForgetPreparedQuery {
    /**
     * Execute the prepared query returning query results.
     *
     * @return query result
     */
    public EPFireAndForgetQueryResult execute();

    /**
     * For use with named windows that have a context declared and that may therefore have multiple context partitions,
     * allows to target context partitions for query execution selectively.
     *
     * @param contextPartitionSelectors selects context partitions to consider
     * @return query result
     */
    public EPFireAndForgetQueryResult execute(ContextPartitionSelector[] contextPartitionSelectors);

    /**
     * Returns the event type, representing the columns of the select-clause.
     *
     * @return event type
     */
    public EventType getEventType();

    /**
     * Releases resources.
     * <p>
     *     Required for use with fire-and-forget queries that use SQL queries i.e. "from SQL:db [query]".
     *     Optional for all other EPL queries.
     * </p>
     */
    public void close();
}
