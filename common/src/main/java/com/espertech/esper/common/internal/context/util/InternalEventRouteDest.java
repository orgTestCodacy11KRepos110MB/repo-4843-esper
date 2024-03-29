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
package com.espertech.esper.common.internal.context.util;

import com.espertech.esper.common.client.EventBean;

/**
 * Interface for a service that routes events within the runtimefor further processing.
 */
public interface InternalEventRouteDest {
    /**
     * Route the event such that the event is processed as required.
     *  @param theEvent        to route
     * @param statementHandle provides statement resources
     * @param addToFront      whether to add to the front of the work queue
     * @param precedence event precedence
     */
    public void route(EventBean theEvent, EPStatementHandle statementHandle, boolean addToFront, int precedence);

    public void setInternalEventRouter(InternalEventRouter internalEventRouter);

    public void processThreadWorkQueue();

    public void dispatch();

    public String getRuntimeURI();
}