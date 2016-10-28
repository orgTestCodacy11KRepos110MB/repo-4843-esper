/*
 * *************************************************************************************
 *  Copyright (C) 2006-2015 EsperTech, Inc. All rights reserved.                       *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.regression.nwtable;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.scopetest.EPAssertionUtil;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.core.service.EPServiceProviderSPI;
import com.espertech.esper.metrics.instrumentation.InstrumentationHelper;
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.bean.SupportBean_S0;
import com.espertech.esper.support.bean.SupportBean_S1;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.support.event.EventTypeAssertionEnum;
import com.espertech.esper.support.event.EventTypeAssertionUtil;
import com.espertech.esper.support.util.SupportModelHelper;
import junit.framework.TestCase;

public class TestInfraEventType extends TestCase
{
    private EPServiceProviderSPI epService;

    public void setUp()
    {
        Configuration config = SupportConfigFactory.getConfiguration();
        epService = (EPServiceProviderSPI) EPServiceProviderManager.getDefaultProvider(config);
        epService.initialize();
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.startTest(epService, this.getClass(), getName());}
    }
    
    protected void tearDown() throws Exception {
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.endTest();}
    }

    public void testEventType() {
        runAssertionType(true);
        runAssertionType(false);
    }

    private void runAssertionType(boolean namedWindow) {
        String eplCreate = namedWindow ?
                "create window MyInfra#keepall() as (c0 int[], c1 int[primitive])" :
                "create table MyInfra (c0 int[], c1 int[primitive])";
        EPStatement stmt = SupportModelHelper.createByCompileOrParse(epService, false, eplCreate);

        Object[][] expectedType = new Object[][]{{"c0", Integer[].class}, {"c1", int[].class}};
        EventTypeAssertionUtil.assertEventTypeProperties(expectedType, stmt.getEventType(), EventTypeAssertionEnum.NAME, EventTypeAssertionEnum.TYPE);

        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("MyInfra", false);
    }
}
