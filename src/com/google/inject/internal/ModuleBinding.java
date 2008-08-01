/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.spi.DefaultBindTargetVisitor;
import java.lang.annotation.Annotation;

/**
 * Immutable snapshot of a request to bind a value.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class ModuleBinding<T> implements Binding<T> {

  private final Key<?> NULL_KEY = Key.get(Void.class);

  private static final Target<Object> EMPTY_TARGET = new Target<Object>() {
    public <V> V acceptTargetVisitor(TargetVisitor<? super Object, V> visitor) {
      return visitor.visitUntargetted();
    }
  };

  private static final Scoping EMPTY_SCOPING = new AbstractScoping() {
    public <V> V acceptVisitor(ScopingVisitor<V> visitor) {
      return visitor.visitNoScoping();
    }
  };

  private static final TargetVisitor<Object, Boolean> SUPPORTS_SCOPES
      = new DefaultBindTargetVisitor<Object, Boolean>() {
    @Override public Boolean visitInstance(Object instance) {
      return false;
    }

    @Override protected Boolean visitOther() {
      return true;
    }
  };

  private final Object source;
  private Key<T> key;

  @SuppressWarnings("unchecked")
  private Target<T> target = (Target<T>) EMPTY_TARGET;
  private Scoping scoping = EMPTY_SCOPING;

  public ModuleBinding(Object source, Key<T> key) {
    this.source = checkNotNull(source, "source");
    this.key = checkNotNull(key, "key");
  }

  public ModuleBinding(Object source) {
    @SuppressWarnings("unchecked") // unsafe, but we won't ever return this (Key.get fails)
    Key<T> NULL_KEY_OF_T = (Key<T>) NULL_KEY;

    this.source = checkNotNull(source);
    this.key = NULL_KEY_OF_T;
  }

  public Object getSource() {
    return source;
  }

  /**
   * Returns the scoped provider guice uses to fulfill requests for this
   * binding.
   */
  public Provider<T> getProvider() {
    throw new UnsupportedOperationException();
  }

  public <V> V acceptVisitor(Visitor<V> visitor) {
    return visitor.visitBinding(this);
  }

  public Key<T> getKey() {
    return key;
  }

  public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
    return target.acceptTargetVisitor(visitor);
  }

  public <V> V acceptScopingVisitor(ScopingVisitor<V> visitor) {
    return scoping.acceptVisitor(visitor);
  }

  private boolean keyTypeIsSet() {
    return !Void.class.equals(key.getTypeLiteral().getType());
  }

  @Override public String toString() {
    return "bind " + key
        + (target == EMPTY_TARGET ? "" : (" to " + target))
        + (scoping == EMPTY_SCOPING ? "" : (" in " + scoping));
  }

  private static abstract class AbstractScoping implements Scoping {
    public Scope getScope() {
      return null;
    }
    public Class<? extends Annotation> getScopeAnnotation() {
      return null;
    }
  }

  public RegularBuilder regularBuilder(Binder binder) {
    return new RegularBuilder(binder);
  }

  /**
   * Write access to the internal state of this element. Not for use by the public API.
   */
  public class RegularBuilder implements AnnotatedBindingBuilder<T> {
    private final Binder binder;

    RegularBuilder(Binder binder) {
      this.binder = binder.skipSources(RegularBuilder.class);
    }

    public LinkedBindingBuilder<T> annotatedWith(
        Class<? extends Annotation> annotationType) {
      checkNotNull(annotationType, "annotationType");
      checkNotAnnotated();
      key = Key.get(key.getTypeLiteral(), annotationType);
      return this;
    }

    public LinkedBindingBuilder<T> annotatedWith(Annotation annotation) {
      checkNotNull(annotation, "annotation");
      checkNotAnnotated();
      key = Key.get(key.getTypeLiteral(), annotation);
      return this;
    }

    public ScopedBindingBuilder to(final Class<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(
        final TypeLiteral<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(final Key<? extends T> targetKey) {
      checkNotNull(targetKey, "targetKey");
      checkNotTargetted();
      target = new Target<T>() {
        public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
          return visitor.visitKey(targetKey);
        }
        @Override public String toString() {
          return String.valueOf(targetKey);
        }
      };
      return this;
    }

    public void toInstance(final T instance) {
      checkNotTargetted();
      target = new InstanceTarget<T>(instance);
    }

    public ScopedBindingBuilder toProvider(final Provider<? extends T> provider) {
      checkNotNull(provider, "provider");
      checkNotTargetted();
      target = new Target<T>() {
        public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
          return visitor.visitProvider(provider);
        }
      };
      return this;
    }

    public ScopedBindingBuilder toProvider(
        Class<? extends Provider<? extends T>> providerType) {
      return toProvider(Key.get(providerType));
    }

    public ScopedBindingBuilder toProvider(
        final Key<? extends Provider<? extends T>> providerKey) {
      checkNotNull(providerKey, "providerKey");
      checkNotTargetted();
      target = new Target<T>() {
        public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
          return visitor.visitProviderKey(providerKey);
        }
      };
      return this;
    }

    public void in(final Class<? extends Annotation> scopeAnnotation) {
      checkNotNull(scopeAnnotation, "scopeAnnotation");
      checkNotScoped();

      scoping = new AbstractScoping() {
        @Override public Class<? extends Annotation> getScopeAnnotation() {
          return scopeAnnotation;
        }
        public <V> V acceptVisitor(ScopingVisitor<V> visitor) {
          return visitor.visitScopeAnnotation(scopeAnnotation);
        }
        @Override public String toString() {
          return scopeAnnotation.getName();
        }
      };
    }

    public void in(final Scope scope) {
      checkNotNull(scope, "scope");
      checkNotScoped();
      scoping = new AbstractScoping() {
        @Override public Scope getScope() {
          return scope;
        }
        public <V> V acceptVisitor(ScopingVisitor<V> visitor) {
          return visitor.visitScope(scope);
        }
        @Override public String toString() {
          return String.valueOf(scope);
        }
      };
    }

    public void asEagerSingleton() {
      checkNotScoped();
      scoping = new AbstractScoping() {
        public <V> V acceptVisitor(ScopingVisitor<V> visitor) {
          return visitor.visitEagerSingleton();
        }
        @Override public String toString() {
          return "eager singleton";
        }
      };
    }

    static final String IMPLEMENTATION_ALREADY_SET
        = "Implementation is set more than once.";
    static final String SINGLE_INSTANCE_AND_SCOPE = "Setting the scope is not"
        + " permitted when binding to a single instance.";
    static final String SCOPE_ALREADY_SET = "Scope is set more than once.";
    static final String ANNOTATION_ALREADY_SPECIFIED = "More than one annotation"
        + " is specified for this binding.";

    private void checkNotTargetted() {
      if (target != EMPTY_TARGET) {
        binder.addError(IMPLEMENTATION_ALREADY_SET);
      }
    }

    private void checkNotAnnotated() {
      if (ModuleBinding.this.key.getAnnotationType() != null) {
        binder.addError(ANNOTATION_ALREADY_SPECIFIED);
      }
    }

    private void checkNotScoped() {
      @SuppressWarnings("unchecked") TargetVisitor<T,Boolean> supportsScopesOfT
          = (TargetVisitor<T,Boolean>) SUPPORTS_SCOPES;

      // Scoping isn't allowed when we have only one instance.
      if (!target.acceptTargetVisitor(supportsScopesOfT)) {
        binder.addError(SINGLE_INSTANCE_AND_SCOPE);
        return;
      }

      if (scoping != EMPTY_SCOPING) {
        binder.addError(SCOPE_ALREADY_SET);
      }
    }

    @Override public String toString() {
      String type = key.getAnnotationType() == null
          ? "AnnotatedBindingBuilder<"
          : "LinkedBindingBuilder<";
      return type + key.getTypeLiteral() + ">";
    }
  }

  public ConstantBuilder constantBuilder(Binder binder) {
    return new ConstantBuilder(binder);
  }

  /**
   * Package-private write access to the internal state of this element.
   */
  class ConstantBuilder
      implements AnnotatedConstantBindingBuilder, ConstantBindingBuilder {
    private final Binder binder;

    ConstantBuilder(Binder binder) {
      this.binder = binder.skipSources(ConstantBuilder.class);
    }

    public ConstantBindingBuilder annotatedWith(final Class<? extends Annotation> annotationType) {
      checkNotNull(annotationType, "annotationType");
      if (key.getAnnotationType() != null) {
        binder.addError(ANNOTATION_ALREADY_SPECIFIED);
      } else {
        key = Key.get(key.getTypeLiteral(), annotationType);
      }
      return this;
    }

    public ConstantBindingBuilder annotatedWith(final Annotation annotation) {
      checkNotNull(annotation, "annotation");
      if (key.getAnnotationType() != null) {
        binder.addError(ANNOTATION_ALREADY_SPECIFIED);
      } else {
        key = Key.get(key.getTypeLiteral(), annotation);
      }
      return this;
    }

    public void to(final String value) {
      to(String.class, value);
    }

    public void to(final int value) {
      to(Integer.class, value);
    }

    public void to(final long value) {
      to(Long.class, value);
    }

    public void to(final boolean value) {
      to(Boolean.class, value);
    }

    public void to(final double value) {
      to(Double.class, value);
    }

    public void to(final float value) {
      to(Float.class, value);
    }

    public void to(final short value) {
      to(Short.class, value);
    }

    public void to(final char value) {
      to(Character.class, value);
    }

    public void to(final Class<?> value) {
      to(Class.class, value);
    }

    public <E extends Enum<E>> void to(final E value) {
      to(value.getDeclaringClass(), value);
    }

    static final String CONSTANT_VALUE_ALREADY_SET = "Constant value is set more"
        + " than once.";
    static final String ANNOTATION_ALREADY_SPECIFIED = "More than one annotation"
        + " is specified for this binding.";

    private void to(Class<?> type, Object instance) {
      checkNotNull(instance, "instance");

      // this type will define T, so these assignments are safe
      @SuppressWarnings("unchecked")
      Class<T> typeAsClassT = (Class<T>) type;
      @SuppressWarnings("unchecked")
      T instanceAsT = (T) instance;

      if (keyTypeIsSet()) {
        binder.addError(CONSTANT_VALUE_ALREADY_SET);
        return;
      }

      if (key.getAnnotation() != null) {
        key = Key.get(typeAsClassT, key.getAnnotation());
      } else if (key.getAnnotationType() != null) {
        key = Key.get(typeAsClassT, key.getAnnotationType());
      } else {
        key = Key.get(typeAsClassT);
      }

      ModuleBinding.this.target = new InstanceTarget<T>(instanceAsT);
    }

    @Override public String toString() {
      return key.getAnnotationType() == null
          ? "AnnotatedConstantBindingBuilder"
          : "ConstantBindingBuilder";
    }
  }

  /** A binding target, which provides instances from a specific key. */
  private interface Target<T> {
    <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor);
  }

  /** Immutable snapshot of a binding scope. */
  private interface Scoping {
    <V> V acceptVisitor(ScopingVisitor<V> visitor);
  }

  static class InstanceTarget<T> implements Target<T> {
    private final T instance;

    public InstanceTarget(T instance) {
      this.instance = instance;
    }

    public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
      return visitor.visitInstance(instance);
    }
  }
}