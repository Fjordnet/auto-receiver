/*
 * Copyright 2017 FJORD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fjordnet.autoreceiver.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Indicates the desire to automatically create, register, and unregister a broadcast receiver
 * filtered on specific actions.
 * </p><p>
 * Applicable on methods, this annotation marks the broadcast receiver callback that will
 * be invoked when a broadcast occurs with the specified action, assuming it occurs while the
 * broadcast receiver is registered with the system. This can be controlled by modifying the
 * {@link #registerIn()} and {@link #unregisterIn()} attributes.
 * </p><p>
 * The annotated method may have no parameters, one {@code Intent} parameter, which is the
 * intent passed to the broadcast receiver, or two parameters: the {@code Intent} and the
 * {@code BroadcastReceiver} instance itself.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface OnReceiveBroadcast {

    /**
     * One or more actions to be registered with the generated broadcast receiver.
     *
     * @return One or more actions to be registered with the generated broadcast receiver.
     */
    String[] value();

    /**
     * Specifies the method in which the generated broadcast receiver will be registered.
     * The default is {@code onStart}.
     *
     * @return the name of the method in the class in which the generated broadcast receiver
     * will be registered.
     */
    String registerIn() default "onStart";

    /**
     * Specifies the method in which the generated broadcast receiver will be unregistered.
     * The default is {@code onStop}.
     *
     * @return the name of the method in the class in which the generated broadcast receiver
     * will be unregistered.
     */
    String unregisterIn() default "onStop";
}
