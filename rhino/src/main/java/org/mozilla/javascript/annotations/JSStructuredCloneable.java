/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code ScriptableObject} subclass as eligible for structured cloning via the global
 * {@code structuredClone()} function.
 *
 * <p>Eligibility is enforced by {@link
 * org.mozilla.javascript.StructuredCloneContext#clone(Object)}, which walks the value's class
 * hierarchy looking for this annotation. The annotation is deliberately not {@code @Inherited}; the
 * walk visits each superclass explicitly, so the annotation may sit on any class in the hierarchy
 * rather than on the concrete class itself (for example, the typed-array views are covered by the
 * annotation on their common base). It must be placed on a class, not an interface.
 *
 * <p>Only standard types participate in structured cloning. {@code NativeObject} carries the
 * annotation, so plain objects are cloneable out of the box using the plain-object algorithm, and
 * each built-in type ({@code NativeArray}, {@code NativeMap}, ...) annotates itself. A host object
 * (a Java class extending {@code ScriptableObject} directly) is <em>not</em> cloneable unless it is
 * explicitly annotated; attempting to clone one throws a {@code DataCloneError}.
 *
 * <p><b>The annotation and the {@code structuredClone} method form one contract, and a new
 * cloneable type needs both.</b> The annotation decides <em>whether</em> the value is dispatched to
 * {@link org.mozilla.javascript.ScriptableObject#structuredClone(org.mozilla.javascript.Context,
 * org.mozilla.javascript.VarScope, org.mozilla.javascript.StructuredCloneContext)}; it carries no
 * behavior. The method decides <em>how</em> the clone is built. Omitting either half fails in a
 * different way:
 *
 * <ul>
 *   <li>Annotation present but no override: the inherited default implementation runs and copies
 *       only the own enumerable properties into a fresh plain object. For a type whose state lives
 *       in Java fields (a backing map, a time value, a byte buffer, a compiled pattern), the result
 *       is a clone that looks valid but is silently wrong. This is the worse failure mode: no
 *       error, just data loss.
 *   <li>Override present but no annotation anywhere in the hierarchy: the dispatcher throws a
 *       {@code DataCloneError} before the method is ever called, so the override is dead code and
 *       the type is uncloneable despite having a complete implementation.
 * </ul>
 *
 * @see org.mozilla.javascript.ScriptableObject#structuredClone(org.mozilla.javascript.Context,
 *     org.mozilla.javascript.VarScope, org.mozilla.javascript.StructuredCloneContext)
 * @see org.mozilla.javascript.StructuredCloneContext#clone(Object)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JSStructuredCloneable {}
