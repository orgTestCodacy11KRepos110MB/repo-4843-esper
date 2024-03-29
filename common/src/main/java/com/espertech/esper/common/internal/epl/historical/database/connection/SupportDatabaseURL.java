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

import java.util.Properties;

public class SupportDatabaseURL {
    public final static String DBUSER = "root";
    public final static String DBPWD = "password";
    public final static String DRIVER = "com.mysql.cj.jdbc.Driver";
    public final static String FULLURL = "jdbc:mysql://localhost/test?user=root&password=password&useSSL=false";
    public final static String PARTURL = "jdbc:mysql://localhost/test?useSSL=false";

    public static Properties newProperties() {
        Properties properties = new Properties();
        properties.put("useSSL", "false");
        return properties;
    }
}
