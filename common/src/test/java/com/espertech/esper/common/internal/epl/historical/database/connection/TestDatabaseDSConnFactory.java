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
package com.espertech.esper.common.internal.epl.historical.database.connection;

import com.espertech.esper.common.client.configuration.common.ConfigurationCommonDBRef;
import com.espertech.esper.common.internal.supportunit.util.SupportInitialContextFactory;
import com.mysql.cj.jdbc.MysqlDataSource;
import junit.framework.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class TestDatabaseDSConnFactory extends TestCase {
    private DatabaseDSConnFactory databaseDSConnFactory;

    public void setUp() {
        MysqlDataSource mySQLDataSource = new MysqlDataSource();
        mySQLDataSource.setUser(SupportDatabaseURL.DBUSER);
        mySQLDataSource.setPassword(SupportDatabaseURL.DBPWD);
        mySQLDataSource.setURL("jdbc:mysql://localhost/test?useSSL=false");

        String envName = "java:comp/env/jdbc/MySQLDB";
        SupportInitialContextFactory.addContextEntry(envName, mySQLDataSource);

        ConfigurationCommonDBRef config = new ConfigurationCommonDBRef();
        Properties properties = SupportDatabaseURL.newProperties();
        properties.put("java.naming.factory.initial", SupportInitialContextFactory.class.getName());
        config.setDataSourceConnection(envName, properties);

        databaseDSConnFactory = new DatabaseDSConnFactory((ConfigurationCommonDBRef.DataSourceConnection) config.getConnectionFactoryDesc(), config.getConnectionSettings());
    }

    public void testGetConnection() throws Exception {
        Connection connection = databaseDSConnFactory.getConnection();
        tryAndCloseConnection(connection);
    }

    private void tryAndCloseConnection(Connection connection) throws Exception {
        Statement stmt = connection.createStatement();
        stmt.execute("select 1 from dual");
        ResultSet result = stmt.getResultSet();
        result.next();
        assertEquals(1, result.getInt(1));
        result.close();
        stmt.close();
        connection.close();
    }

    private final static Logger log = LoggerFactory.getLogger(TestDatabaseDSConnFactory.class);
}
