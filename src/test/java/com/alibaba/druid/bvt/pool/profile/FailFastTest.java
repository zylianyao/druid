package com.alibaba.druid.bvt.pool.profile;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;

import com.alibaba.druid.pool.DruidDataSource;

import junit.framework.TestCase;

public class FailFastTest extends TestCase {

    private DruidDataSource dataSource;
    
    private CountDownLatch latch = new CountDownLatch(1);

    @SuppressWarnings("serial")
    protected void setUp() throws Exception {
        dataSource = new DruidDataSource() {
            public PhysicalConnectionInfo createPhysicalConnection() throws SQLException {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                SQLException ex = new SQLException();
                setCreateError(ex);
                throw ex;
            }
        };
        
        dataSource.setUrl("jdbc:mock:xxx");
        dataSource.setMaxWait(1000 * 1000);
        
        Properties properties = new Properties();
        properties.setProperty("druid.failFast", "true");
        dataSource.configFromPropety(properties);
    }

    protected void tearDown() throws Exception {
        dataSource.close();
    }

    public void testDefault() throws Exception {
        Assert.assertTrue(dataSource.isFailFast());

        final AtomicReference<SQLException> errorHolder = new AtomicReference<SQLException>(null);
        final CountDownLatch connectStartLatch = new CountDownLatch(1);
        final CountDownLatch connectEndLatch = new CountDownLatch(1);
        Thread connectThread = new Thread() {
            public void run() {
                connectStartLatch.countDown();
                try {
                    dataSource.getConnection();
                } catch (SQLException e) {
                    errorHolder.set(e);
                } finally {
                    connectEndLatch.countDown();
                }
            }
        };
        connectThread.setName("ConnectThread");
        connectThread.start();
        
        connectStartLatch.await();
        
        latch.countDown();
        
        connectEndLatch.await(3, TimeUnit.SECONDS);
    }

}
