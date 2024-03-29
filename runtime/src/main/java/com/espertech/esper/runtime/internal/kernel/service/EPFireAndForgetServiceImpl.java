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
package com.espertech.esper.runtime.internal.kernel.service;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EPException;
import com.espertech.esper.common.client.context.ContextPartitionSelector;
import com.espertech.esper.common.client.fireandforget.EPFireAndForgetPreparedQuery;
import com.espertech.esper.common.client.fireandforget.EPFireAndForgetPreparedQueryParameterized;
import com.espertech.esper.common.client.fireandforget.EPFireAndForgetQueryResult;
import com.espertech.esper.common.internal.context.query.FAFProvider;
import com.espertech.esper.common.internal.epl.fafquery.querymethod.*;
import com.espertech.esper.runtime.client.EPFireAndForgetService;
import com.espertech.esper.runtime.internal.kernel.faf.EPFireAndForgetPreparedQueryParameterizedImpl;
import com.espertech.esper.runtime.internal.kernel.faf.EPPreparedQueryImpl;
import com.espertech.esper.runtime.internal.kernel.faf.EPQueryResultImpl;
import com.espertech.esper.runtime.internal.kernel.faf.EPRuntimeHelperFAF;

import java.util.concurrent.atomic.AtomicBoolean;

public class EPFireAndForgetServiceImpl implements EPFireAndForgetService {
    private final EPServicesContext services;
    private final AtomicBoolean serviceStatusProvider;

    public EPFireAndForgetServiceImpl(EPServicesContext services, AtomicBoolean serviceStatusProvider) {
        this.services = services;
        this.serviceStatusProvider = serviceStatusProvider;
    }

    public EPFireAndForgetQueryResult executeQuery(EPCompiled compiled) {
        return executeQueryUnprepared(compiled, null);
    }

    public EPFireAndForgetQueryResult executeQuery(EPCompiled compiled, ContextPartitionSelector[] contextPartitionSelectors) {
        if (contextPartitionSelectors == null) {
            throw new IllegalArgumentException("No context partition selectors provided");
        }
        return executeQueryUnprepared(compiled, contextPartitionSelectors);
    }

    public EPFireAndForgetPreparedQuery prepareQuery(EPCompiled compiled) {
        FAFProvider fafProvider = EPRuntimeHelperFAF.queryMethod(compiled, services);
        FAFQueryMethodProvider queryMethodProvider = fafProvider.getQueryMethodProvider();
        EPRuntimeHelperFAF.validateSubstitutionParams(queryMethodProvider);
        FAFQueryMethod queryMethod = queryMethodProvider.getQueryMethod();
        FAFQueryMethodSessionPrepared prepared = queryMethod.readyPrepared(services.getStatementContextRuntimeServices());
        return new EPPreparedQueryImpl(serviceStatusProvider, queryMethodProvider, prepared, services);
    }

    public EPFireAndForgetPreparedQueryParameterized prepareQueryWithParameters(EPCompiled compiled) {
        FAFProvider fafProvider = EPRuntimeHelperFAF.queryMethod(compiled, services);
        FAFQueryMethodProvider queryMethodProvider = fafProvider.getQueryMethodProvider();
        FAFQueryMethod queryMethod = queryMethodProvider.getQueryMethod();
        FAFQueryMethodSessionPrepared prepared = queryMethod.readyPrepared(services.getStatementContextRuntimeServices());
        return new EPFireAndForgetPreparedQueryParameterizedImpl(serviceStatusProvider, queryMethodProvider.getSubstitutionFieldSetter(), prepared, queryMethodProvider.getQueryInformationals());
    }

    public EPFireAndForgetQueryResult executeQuery(EPFireAndForgetPreparedQueryParameterized parameterizedQuery) {
        return executeQueryPrepared(parameterizedQuery, null);
    }

    public EPFireAndForgetQueryResult executeQuery(EPFireAndForgetPreparedQueryParameterized parameterizedQuery, ContextPartitionSelector[] selectors) {
        return executeQueryPrepared(parameterizedQuery, selectors);
    }

    private EPFireAndForgetQueryResult executeQueryPrepared(EPFireAndForgetPreparedQueryParameterized parameterizedQuery, ContextPartitionSelector[] selectors) {
        EPFireAndForgetPreparedQueryParameterizedImpl impl = (EPFireAndForgetPreparedQueryParameterizedImpl) parameterizedQuery;
        EPRuntimeHelperFAF.checkSubstitutionSatisfied(impl);
        if (!impl.getServiceProviderStatus().get()) {
            throw FAFQueryMethodUtil.runtimeDestroyed();
        }
        if (impl.getServiceProviderStatus() != serviceStatusProvider) {
            throw new EPException("Service provider has already been destroyed and reallocated");
        }
        return new EPQueryResultImpl(impl.getPrepared().execute(serviceStatusProvider, impl.getFields(), selectors, services.getContextManagementService()));
    }


    private EPFireAndForgetQueryResult executeQueryUnprepared(EPCompiled compiled, ContextPartitionSelector[] contextPartitionSelectors) {
        FAFProvider fafProvider = EPRuntimeHelperFAF.queryMethod(compiled, services);
        FAFQueryMethodProvider queryMethodProvider = fafProvider.getQueryMethodProvider();
        EPRuntimeHelperFAF.validateSubstitutionParams(queryMethodProvider);
        FAFQueryMethod queryMethod = queryMethodProvider.getQueryMethod();
        FAFQuerySessionUnprepared unprepared = queryMethod.readyUnprepared(services.getStatementContextRuntimeServices());
        EPPreparedQueryResult result = unprepared.execute(serviceStatusProvider, queryMethodProvider.getSubstitutionFieldSetter(), contextPartitionSelectors, services.getContextManagementService());
        return new EPQueryResultImpl(result);
    }
}
