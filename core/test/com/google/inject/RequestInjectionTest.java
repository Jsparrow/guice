/*
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.getDeclaringSourcePart;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Retention;
import junit.framework.TestCase;

/** @author crazybob@google.com (Bob Lee) */
public class RequestInjectionTest extends TestCase {

  @Override
	  protected void setUp() throws Exception {
	    super.setUp();
	    HasInjections.staticField = 0;
	    HasInjections.staticMethod = null;
	  }

	public void testInjectMembers() {
	    final HasInjections hi = new HasInjections();
	
	    Guice.createInjector(
	        new AbstractModule() {
	          @Override
	          protected void configure() {
	            bindConstant().annotatedWith(ForMethod.class).to("test");
	            bindConstant().annotatedWith(ForField.class).to(5);
	            requestInjection(hi);
	          }
	        });
	
	    assertEquals("test", hi.instanceMethod);
	    assertEquals(5, hi.instanceField);
	    assertNull(HasInjections.staticMethod);
	    assertEquals(0, HasInjections.staticField);
	  }

	public void testInjectStatics() {
	    Guice.createInjector(
	        new AbstractModule() {
	          @Override
	          protected void configure() {
	            bindConstant().annotatedWith(ForMethod.class).to("test");
	            bindConstant().annotatedWith(ForField.class).to(5);
	            requestStaticInjection(HasInjections.class);
	          }
	        });
	
	    assertEquals("test", HasInjections.staticMethod);
	    assertEquals(5, HasInjections.staticField);
	  }

	public void testInjectMembersAndStatics() {
	    final HasInjections hi = new HasInjections();
	
	    Guice.createInjector(
	        new AbstractModule() {
	          @Override
	          protected void configure() {
	            bindConstant().annotatedWith(ForMethod.class).to("test");
	            bindConstant().annotatedWith(ForField.class).to(5);
	            requestStaticInjection(HasInjections.class);
	            requestInjection(hi);
	          }
	        });
	
	    assertEquals("test", hi.instanceMethod);
	    assertEquals(5, hi.instanceField);
	    assertEquals("test", HasInjections.staticMethod);
	    assertEquals(5, HasInjections.staticField);
	  }

	public void testValidationErrorOnInjectedMembers() {
	    try {
	      Guice.createInjector(
	          new AbstractModule() {
	            @Override
	            protected void configure() {
	              requestInjection(new NeedsRunnable());
	            }
	          });
	      fail("Expected CreationException");
	    } catch (CreationException expected) {
	      assertContains(
	          expected.getMessage(),
	          "1) No implementation for java.lang.Runnable was bound",
	          "at " + NeedsRunnable.class.getName(),
	          ".runnable(RequestInjectionTest.java:");
	    }
	  }

	public void testInjectionErrorOnInjectedMembers() {
	    try {
	      Guice.createInjector(
	          new AbstractModule() {
	            @Override
	            protected void configure() {
	              bind(Runnable.class)
	                  .toProvider(
	                      new Provider<Runnable>() {
	                        @Override
	                        public Runnable get() {
	                          throw new UnsupportedOperationException();
	                        }
	                      });
	              requestInjection(new NeedsRunnable());
	            }
	          });
	    } catch (CreationException expected) {
	      assertContains(
	          expected.getMessage(),
	          "1) Error in custom provider, java.lang.UnsupportedOperationException",
	          new StringBuilder().append("for field at ").append(NeedsRunnable.class.getName()).append(".runnable(RequestInjectionTest.java:").toString(),
	          "at " + getClass().getName(),
	          getDeclaringSourcePart(getClass()));
	    }
	  }

	public void testUserExceptionWhileInjectingInstance() {
	    try {
	      Guice.createInjector(
	          new AbstractModule() {
	            @Override
	            protected void configure() {
	              requestInjection(new BlowsUpOnInject());
	            }
	          });
	      fail();
	    } catch (CreationException expected) {
	      assertContains(
	          expected.getMessage(),
	          "1) Error injecting method, java.lang.UnsupportedOperationException: Pop",
	          new StringBuilder().append("at ").append(BlowsUpOnInject.class.getName()).append(".injectInstance(RequestInjectionTest.java:").toString());
	    }
	  }

	public void testUserExceptionWhileInjectingStatically() {
	    try {
	      Guice.createInjector(
	          new AbstractModule() {
	            @Override
	            protected void configure() {
	              requestStaticInjection(BlowsUpOnInject.class);
	            }
	          });
	      fail();
	    } catch (CreationException expected) {
	      assertContains(
	          expected.getMessage(),
	          "1) Error injecting method, java.lang.UnsupportedOperationException: Snap",
	          new StringBuilder().append("at ").append(BlowsUpOnInject.class.getName()).append(".injectStatically(RequestInjectionTest.java:").toString());
	    }
	  }

	/*
	   * Tests that initializables of the same instance don't clobber
	   * membersInjectors in InitializableReference, so that they ultimately
	   * can be requested in any order.
	   */
	  public void testEarlyInjectableReferencesWithSameIdentity() {
	    Injector injector =
	        Guice.createInjector(
	            new AbstractModule() {
	              @Override
	              protected void configure() {
	                // Add a listener to trigger all toInstance bindings to get an Initializable.
	                bindListener(
	                    Matchers.any(),
	                    new TypeListener() {
	                      @Override
	                      public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {}
	                    });
	
	                // Bind two different Keys to the IDENTITICAL object
	                // ORDER MATTERS! We want the String binding to push out the Object one
	                String fail = "better not fail!";
	                bind(Object.class).toInstance(fail);
	                bind(String.class).toInstance(fail);
	
	                // Then try to inject those objects in a requestInjection,
	                // letting us get into InjectableReference.get before it has
	                // finished running through all its injections.
	                // Each of these technically has its own InjectableReference internally.
	                // ORDER MATTERS!.. because Object is injected first, that InjectableReference
	                // attempts to process its members injector, but it wasn't initialized,
	                // because String pushed it out of the queue!
	                requestInjection(
	                    new Object() {
	                      @SuppressWarnings("unused")
	                      @Inject
	                      Object obj;
	
	                      @SuppressWarnings("unused")
	                      @Inject
	                      String str;
	                    });
	              }
	            });
	  }

	@Retention(RUNTIME)
	  @BindingAnnotation
	  @interface ForField {}

	@Retention(RUNTIME)
	  @BindingAnnotation
	  @interface ForMethod {}

static class NeedsRunnable {
    @Inject Runnable runnable;
  }

  static class HasInjections {

    @Inject @ForField static int staticField;
	static String staticMethod;
	@Inject @ForField int instanceField;
	String instanceMethod;

	@Inject
    static void setStaticMethod(@ForMethod String staticMethod) {
      HasInjections.staticMethod = staticMethod;
    }

	@Inject
    void setInstanceS(@ForMethod String instanceS) {
      this.instanceMethod = instanceS;
    }
  }

  static class BlowsUpOnInject {
    @Inject
    void injectInstance() {
      throw new UnsupportedOperationException("Pop");
    }

    @Inject
    static void injectStatically() {
      throw new UnsupportedOperationException("Snap");
    }
  }
}
