package org.mozilla.javascript;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation enables certain optimizations that assume that a particular class stores all its
 * properties in a standard SlotMap. A class must only include this annotation if it:
 *
 * <ul>
 *   <li>Is a subclass of ScriptableObject (although it will be ignored if it's not)
 *   <li>Does not override "put", "get", or "has".
 *   <li>Does not do anything else to store properties outside the slot map.
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WellBehavedProperties {}
