package org.mozilla.javascript;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a class has well-behaved properties. If a class is annotated with
 * this, it means that the class does not override the "get" and "set" methods in a way that would
 * break the fast property optimization used in compiled mode.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WellBehavedProperties {}
