package com.navercorp.pinpoint.it.plugin.jdbc.template;

import java.sql.SQLException;

public interface TransactionCallback {
     void doInTransaction() throws SQLException;
}
