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
package com.espertech.esper.common.internal.epl.fafquery.querymethod;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.internal.epl.fafquery.processor.FireAndForgetInstance;
import com.espertech.esper.common.internal.epl.resultset.select.core.SelectExprProcessor;

/**
 * Starts and provides the stop method for EPL statements.
 */
public class FAFQueryMethodIUDInsertInto extends FAFQueryMethodIUDBase {
    public final static EPTypeClass EPTYPE = new EPTypeClass(FAFQueryMethodIUDInsertInto.class);

    private SelectExprProcessor[] insertHelpers;

    public void setInsertHelpers(SelectExprProcessor[] insertHelpers) {
        this.insertHelpers = insertHelpers;
    }

    protected EventBean[] execute(FireAndForgetInstance fireAndForgetProcessorInstance) {
        return fireAndForgetProcessorInstance.processInsert(this);
    }

    public SelectExprProcessor[] getInsertHelpers() {
        return insertHelpers;
    }
}
