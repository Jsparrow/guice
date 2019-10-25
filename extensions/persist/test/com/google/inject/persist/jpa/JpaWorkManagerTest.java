/*
 * Copyright (C) 2010 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.persist.jpa;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import junit.framework.TestCase;
import org.hibernate.HibernateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Dhanji R. Prasanna (dhanji@gmail.com) */

public class JpaWorkManagerTest extends TestCase {
  private static final Logger logger = LoggerFactory.getLogger(JpaWorkManagerTest.class);
private static final String UNIQUE_TEXT_3 =
      new StringBuilder().append(JpaWorkManagerTest.class.getSimpleName()).append("CONSTRAINT_VIOLATING some other unique text").append(new Date()).toString();
private Injector injector;

@Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    //startup persistence
    injector.getInstance(PersistService.class).start();
  }

@Override
  public void tearDown() {
    try {
      injector.getInstance(EntityManagerFactory.class).close();
    } catch (HibernateException ex) {
		logger.error(ex.getMessage(), ex);
      // Expected if the persist service has already been stopped.
    }
  }

public void testWorkManagerInSession() {
    injector.getInstance(UnitOfWork.class).begin();
    try {
      injector.getInstance(TransactionalObject.class).runOperationInTxn();
    } finally {
      injector.getInstance(UnitOfWork.class).end();
    }

    injector.getInstance(UnitOfWork.class).begin();
    injector.getInstance(EntityManager.class).getTransaction().begin();
    try {
      final Query query =
          injector
              .getInstance(EntityManager.class)
              .createQuery("select e from JpaTestEntity as e where text = :text");

      query.setParameter("text", UNIQUE_TEXT_3);
      final Object o = query.getSingleResult();

      assertNotNull("no result!!", o);
      assertTrue("Unknown type returned " + o.getClass(), o instanceof JpaTestEntity);
      JpaTestEntity ent = (JpaTestEntity) o;

      assertEquals(
          "Incorrect result returned or not persisted properly" + ent.getText(),
          UNIQUE_TEXT_3,
          ent.getText());

    } finally {
      injector.getInstance(EntityManager.class).getTransaction().commit();
      injector.getInstance(UnitOfWork.class).end();
    }
  }

public void testCloseMoreThanOnce() {
    injector.getInstance(PersistService.class).stop();

    try {
      injector.getInstance(PersistService.class).stop();
      fail();
    } catch (IllegalStateException e) {
		logger.error(e.getMessage(), e);
      // Ignored.
    }
  }
public static class TransactionalObject {
    @Inject EntityManager em;

    @Transactional
    public void runOperationInTxn() {
      JpaTestEntity testEntity = new JpaTestEntity();

      testEntity.setText(UNIQUE_TEXT_3);
      em.persist(testEntity);
    }

    @Transactional
    public void runOperationInTxnError() {

      JpaTestEntity testEntity = new JpaTestEntity();

      testEntity.setText(new StringBuilder().append(UNIQUE_TEXT_3).append("transient never in db!").append(hashCode()).toString());
      em.persist(testEntity);
    }
  }
}
