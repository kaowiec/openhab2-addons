/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.supla.internal.http;


public interface HttpExecutor extends AutoCloseable {
    Response get(Request request);

    Response post(Request request, Body body);

    Response patch(Request request, Body body);

    @Override
    void close();
}